package com.loom.app.kernel.lua

import android.util.Log
import com.loom.app.kernel.EventBus
import com.loom.app.kernel.Json
import com.loom.app.kernel.Kernel
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.ServiceBus
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import java.io.File

/**
 * 脚本插件的沙箱（Lua 5.2 语义，LuaJ 纯 Java 实现）。
 *
 * 能力白名单：
 *  - 只用 Base / Table / String / Math / Coroutine 库；
 *  - 不加载 io / os / package / debug，也不加载 luajava 绑定；
 *  - dofile / loadfile / load / require / print 全部被替换或移除；
 *  - 脚本只能经 ctx 表访问内核：服务、事件、项目存储、自己的 state。
 *
 * 已知边界（必须诚实）：没有指令级超时。死循环脚本会让主线程卡住，
 * 这属于「脚本插件风险可控但不是零」的现实；界面在导入时如实说明。
 */
class LuaHost(
    private val kernel: Kernel,
    private val plugin: LoadedPlugin,
) {

    private lateinit var globals: Globals
    private lateinit var ctx: LuaTable

    fun boot() {
        globals = newGlobals()
        ctx = ctxTable()
        val entry = File(plugin.dir, plugin.manifest.entry)
        if (entry.exists()) {
            val chunk = globals.load(entry.readText(), "@" + plugin.manifest.entry)
            chunk.call(ctx)
        } else if (!plugin.manifest.isUi) {
            throw IllegalStateException("缺少入口文件 " + plugin.manifest.entry)
        }

        if (plugin.manifest.isUi) {
            val screenPath = plugin.manifest.screenEntry ?: "source/screen.lua"
            val sf = File(plugin.dir, screenPath)
            if (!sf.exists()) throw IllegalStateException("UI 插件缺少 " + screenPath)
            val chunk = globals.load(sf.readText(), "@" + screenPath)
            val table = chunk.call(ctx)
            if (!table.istable()) throw IllegalStateException(screenPath + " 必须 return 一个界面表")
            plugin.screenFn = chunk
            val json = tableToJson(table.checktable())
            plugin.declScreen = json
            plugin.screenTitle = json.optString("title").ifBlank { plugin.name }
            plugin.screenIcon = json.optString("icon").ifBlank { "grid" }
        }
        plugin.luaGlobals = globals
    }

    /** 每次渲染都重新求值：界面永远读当下真实状态，不做缓存。 */
    fun renderScreen(): JSONObject? {
        val fn = plugin.screenFn ?: return null
        val t = fn.call(ctx)
        return if (t.istable()) tableToJson(t.checktable()) else null
    }

    // ------------------------------------------------------------ 沙箱

    private fun newGlobals(): Globals {
        val g = Globals()
        g.load(BaseLib())
        g.load(TableLib())
        g.load(StringLib())
        g.load(MathLib())
        g.load(CoroutineLib())
        g.set("dofile", LuaValue.NIL)
        g.set("loadfile", LuaValue.NIL)
        g.set("load", LuaValue.NIL)
        g.set("require", LuaValue.NIL)
        g.set("package", LuaValue.NIL)
        g.set("print", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                Log.d("LoomLua", plugin.id + ": " + arg.tojstring())
                return LuaValue.NIL
            }
        })
        return g
    }

    private fun ctxTable(): LuaTable {
        val t = LuaTable()
        t.set("id", LuaValue.valueOf(plugin.id))
        t.set("name", LuaValue.valueOf(plugin.name))
        t.set("layer", LuaValue.valueOf(plugin.manifest.layer.key))
        t.set("config", jsonToLua(kernel.projectConfig()))

        t.set("log", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                Log.d("LoomLua", plugin.id + ": " + arg.tojstring())
                return LuaValue.NIL
            }
        })

        t.set("emit", object : TwoArgFunction() {
            override fun call(a: LuaValue, b: LuaValue): LuaValue {
                val event = a.tojstring()
                val payload = if (b.istable()) tableToJson(b.checktable()) else JSONObject()
                return LuaValue.valueOf(kernel.emitEvent(event, plugin.id, payload))
            }
        })

        t.set("on", object : TwoArgFunction() {
            override fun call(a: LuaValue, fn: LuaValue): LuaValue {
                val event = a.tojstring()
                check(fn.isfunction()) { "ctx.on 的第二个参数必须是函数" }
                val listener = EventBus.Listener { e -> runGuarded("handler " + e.name) { fn.call(jsonToLua(e.payload)) } }
                kernel.events.on(event, plugin.id, listener)
                plugin.add { kernel.events.off(event, plugin.id) }
                return LuaValue.NIL
            }
        })

        t.set("provide", object : TwoArgFunction() {
            override fun call(a: LuaValue, b: LuaValue): LuaValue {
                val name = a.tojstring()
                val table = b.checktable()
                val handler = ServiceBus.Handler { method, args ->
                    val fn = table.get(method)
                    if (!fn.isfunction()) throw IllegalStateException("服务 $name 没有方法 $method")
                    val result = fn.call(jsonToLua(args))
                    luaToAny(result)
                }
                if (!kernel.services.provide(name, plugin.id, plugin.manifest.layer.key, handler)) {
                    throw IllegalStateException("服务名已被占用：$name")
                }
                plugin.add { kernel.services.withdraw(name, plugin.id) }
                return LuaValue.NIL
            }
        })

        t.set("call", object : ThreeArgFunction() {
            override fun call(a: LuaValue, b: LuaValue, c: LuaValue): LuaValue {
                val service = a.tojstring()
                val method = b.tojstring()
                val args = if (c.istable()) tableToJson(c.checktable()) else JSONObject()
                kernel.callService(service, method, args, plugin.id)
                return LuaValue.NIL
            }
        })

        t.set("svc", object : ThreeArgFunction() {
            override fun call(a: LuaValue, b: LuaValue, c: LuaValue): LuaValue {
                val service = a.tojstring()
                val method = b.tojstring()
                val args = if (c.istable()) tableToJson(c.checktable()) else JSONObject()
                return anyToLua(kernel.callService(service, method, args, plugin.id))
            }
        })

        val stateTable = LuaTable()
        stateTable.set("get", object : TwoArgFunction() {
            override fun call(k: LuaValue, def: LuaValue): LuaValue {
                val key = k.tojstring()
                return if (plugin.state.has(key) && !plugin.state.isNull(key))
                    anyToLua(plugin.state.opt(key)) else def
            }
        })
        stateTable.set("set", object : TwoArgFunction() {
            override fun call(k: LuaValue, v: LuaValue): LuaValue {
                plugin.state.put(k.tojstring(), luaToAny(v) ?: JSONObject.NULL)
                kernel.persistState(plugin)
                return LuaValue.NIL
            }
        })
        stateTable.set("all", object : OneArgFunction() {
            override fun call(ignored: LuaValue): LuaValue = jsonToLua(Json.copy(plugin.state))
        })
        t.set("state", stateTable)

        val storeTable = LuaTable()
        storeTable.set("get", object : OneArgFunction() {
            override fun call(k: LuaValue): LuaValue = anyToLua(kernel.storeGet(k.tojstring()))
        })
        storeTable.set("set", object : TwoArgFunction() {
            override fun call(k: LuaValue, v: LuaValue): LuaValue {
                kernel.storePut(k.tojstring(), luaToAny(v))
                return LuaValue.NIL
            }
        })
        t.set("store", storeTable)

        return t
    }

    private inline fun runGuarded(what: String, block: () -> Unit) {
        try {
            block()
            kernel.persistState(plugin)
        } catch (t: Throwable) {
            plugin.error = what + " 失败：" + (t.message ?: t.javaClass.simpleName)
            Log.w("LoomLua", plugin.id + " " + what, t)
            kernel.notifyChanged()
        }
    }

    // ------------------------------------------------------------ 值转换

    fun anyToLua(v: Any?): LuaValue = when (v) {
        null, JSONObject.NULL -> LuaValue.NIL
        is LuaValue -> v
        is Boolean -> LuaValue.valueOf(v)
        is Int -> LuaValue.valueOf(v)
        is Long -> LuaValue.valueOf(v.toDouble())
        is Double -> LuaValue.valueOf(v)
        is Float -> LuaValue.valueOf(v.toDouble())
        is Number -> LuaValue.valueOf(v.toDouble())
        is String -> LuaValue.valueOf(v)
        is JSONObject -> jsonToLua(v)
        is JSONArray -> jsonToLua(v)
        is List<*> -> {
            val t = LuaTable()
            v.forEachIndexed { i, item -> t.set(i + 1, anyToLua(item)) }
            t
        }
        is Map<*, *> -> {
            val t = LuaTable()
            v.forEach { (k, value) -> t.set(k.toString(), anyToLua(value)) }
            t
        }
        else -> LuaValue.valueOf(v.toString())
    }

    fun jsonToLua(o: JSONObject?): LuaTable {
        val t = LuaTable()
        if (o == null) return t
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.opt(k)
            if (v != null && v != JSONObject.NULL) t.set(k, anyToLua(v))
        }
        return t
    }

    fun jsonToLua(a: JSONArray?): LuaTable {
        val t = LuaTable()
        if (a == null) return t
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            if (v != null && v != JSONObject.NULL) t.set(i + 1, anyToLua(v))
        }
        return t
    }

    fun luaToAny(v: LuaValue?): Any? {
        if (v == null || v.isnil()) return null
        return when {
            v.isboolean() -> v.toboolean()
            v.isinttype() -> v.toint()
            v.isnumber() -> v.todouble()
            v.isstring() -> v.tojstring()
            v.istable() -> tableToAny(v.checktable())
            else -> v.tojstring()
        }
    }

    /** 表 → JSON：整数且连续的键转数组，其余转对象。 */
    fun tableToAny(t: LuaTable): Any {
        val keys = t.keys()
        if (keys.isEmpty()) return JSONObject()
        var allInt = true
        val ints = HashSet<Int>()
        for (k in keys) {
            if (!k.isinttype()) {
                allInt = false
                break
            }
            ints.add(k.toint())
        }
        if (allInt && ints.size == keys.size) {
            var contiguous = true
            for (i in 1..keys.size) if (!ints.contains(i)) contiguous = false
            if (contiguous) {
                val arr = JSONArray()
                for (i in 1..keys.size) arr.put(luaToAny(t.get(i)))
                return arr
            }
        }
        val o = JSONObject()
        for (k in keys) {
            val value = luaToAny(t.get(k))
            o.put(k.tojstring(), value ?: JSONObject.NULL)
        }
        return o
    }

    fun tableToJson(t: LuaTable): JSONObject {
        val any = tableToAny(t)
        return if (any is JSONObject) any else JSONObject()
    }
}
