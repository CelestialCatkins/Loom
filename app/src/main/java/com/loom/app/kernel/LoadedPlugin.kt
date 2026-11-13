package com.loom.app.kernel

import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import java.io.File

/**
 * 一个已装载的插件实例：状态机 + 可逆副作用栈 + 私有状态。
 * 脚本插件与 DEX 插件共用这套结构（PRD 6.3）。
 */
class LoadedPlugin(
    val manifest: PluginManifest,
    val dir: File,
) {
    fun interface Effect {
        fun undo()
    }

    var status: Status = Status.PENDING
    var error: String? = null
    val missing = ArrayList<String>()
    val effects = ArrayList<Effect>()
    val state = JSONObject()

    /** UI 层界面贡献点之一（三种形态互斥，按注册方式决定）。 */
    var screenTitle: String? = null
    var screenIcon: String = "grid"
    /** 声明式界面（Lua 插件的 screen.lua，每次渲染重新求值）。 */
    var declScreen: JSONObject? = null
    /** 原生 View 界面（DEX 插件，无 Compose 版本耦合）。 */
    var viewFactory: com.loom.app.api.ScreenFactory? = null
    /** Compose 界面（DEX 插件，与宿主 Compose ABI 锁定）。 */
    var composeScreen: com.loom.app.api.ComposeScreen? = null

    var luaGlobals: Globals? = null
    var screenFn: LuaValue? = null
    var dexInstance: com.loom.app.api.Plugin? = null
    var activatedAt: Long = 0L

    val id: String get() = manifest.id
    val name: String get() = manifest.name

    fun add(effect: Effect) {
        effects.add(effect)
    }

    /** 逆序撤销全部副作用；任何一条失败都不影响其余。 */
    fun rollback(): Int {
        var n = 0
        for (i in effects.indices.reversed()) {
            try {
                effects[i].undo()
                n++
            } catch (ignored: Throwable) {
            }
        }
        effects.clear()
        declScreen = null
        screenFn = null
        viewFactory = null
        composeScreen = null
        screenTitle = null
        dexInstance = null
        luaGlobals = null
        return n
    }

    fun effectLabels(): List<String> = effects.map { "effect" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("version", manifest.version)
        .put("layer", manifest.layer.key)
        .put("kind", manifest.kind?.key ?: "")
        .put("status", status.code)
        .put("error", error ?: "")
        .put("missing", Json.array(missing))
        .put("inject", Json.array(manifest.inject))
        .put("provides", Json.array(manifest.provides))
        .put("permissions", Json.array(manifest.permissions))
}
