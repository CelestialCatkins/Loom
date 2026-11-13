package com.loom.app.kernel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.loom.app.api.ComposeScreen
import com.loom.app.api.LoomApi
import com.loom.app.api.PluginContext
import com.loom.app.api.ScreenFactory
import com.loom.app.kernel.lua.LuaHost
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 内核：只负责插件生命周期、服务注册、项目文件系统、事件总线与安全模式。
 *
 * 它不含任何业务功能 —— 界面与能力全部来自插件。
 * 所有注册都要经 effect 包裹，卸载时逆序撤销（PRD FR-6.2）。
 */
class Kernel(private val app: Context) {

    val fs = ProjectFs(app.filesDir)
    val services = ServiceBus()
    val events = EventBus()
    val prefs: SharedPreferences = app.getSharedPreferences("loom", Context.MODE_PRIVATE)

    var current: ProjectFs.Project? = null
        private set

    val plugins = LinkedHashMap<String, LoadedPlugin>()

    var safetyMode: Boolean = false
        private set

    var safetyReason: String = ""
        private set

    private val listeners = ArrayList<() -> Unit>()
    private val luaHosts = HashMap<String, LuaHost>()
    private val dexDir = File(app.cacheDir, "dex").apply { mkdirs() }

    // ------------------------------------------------------------ 监听

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        listeners.toList().forEach {
            runCatching { it() }
        }
    }

    // ------------------------------------------------------------ 项目

    fun projects(): List<ProjectFs.Project> = fs.list()

    fun restoreProject() {
        if (current != null) return
        val id = prefs.getString("current", "")
        val p = fs.open(id)
            ?: projects().firstOrNull()
        if (p != null) openProject(p)
    }

    fun openProject(p: ProjectFs.Project?) {
        unloadAll()
        current = p
        prefs.edit().putString("current", p?.id ?: "").apply()
        if (p != null) resolveAndActivate()
        notifyChanged()
    }

    fun closeProject() {
        unloadAll()
        current = null
        prefs.edit().putString("current", "").apply()
        notifyChanged()
    }

    fun reload() {
        unloadAll()
        resolveAndActivate()
        notifyChanged()
    }

    fun projectsCount(): Int = projects().size

    // ------------------------------------------------------------ 启停

    private fun disabledIds(): MutableSet<String> {
        val p = current ?: return HashSet()
        return Json.strList(p.meta, "disabled").toMutableSet()
    }

    private fun saveDisabled(ids: Set<String>) {
        val p = current ?: return
        p.meta.put("disabled", Json.array(ids))
        p.meta.put("enabled", Json.array(plugins.values.filter { it.status == Status.ACTIVE }.map { it.id }))
        fs.save(p)
    }

    fun isEnabled(pluginId: String): Boolean = !disabledIds().contains(pluginId)

    fun toggleEnabled(pluginId: String, enabled: Boolean) {
        if (current == null) return
        val ids = disabledIds()
        if (enabled) ids.remove(pluginId) else ids.add(pluginId)
        saveDisabled(ids)
        reload()
    }

    fun uninstall(pluginId: String) {
        val lp = plugins[pluginId]
        lp?.rollback()
        val p = current ?: return
        val ids = disabledIds().apply { remove(pluginId) }
        saveDisabled(ids)
        val dir = lp?.dir ?: File(File(p.dir, "plugins"), pluginId)
        dir.deleteRecursively()
        File(fs.dataDir(p), "state-$pluginId.json").delete()
        luaHosts.remove(pluginId)
        reload()
    }

    @Throws(Exception::class)
    fun installZip(p: ProjectFs.Project, input: InputStream): List<String> {
        val ids = fs.installPluginZip(p, input)
        if (current?.id == p.id) reload()
        return ids
    }

    // ------------------------------------------------------------ 装载

    private fun unloadAll() {
        plugins.values.forEach {
            persistState(it)
            it.rollback()
        }
        plugins.clear()
        luaHosts.clear()
        services.clear()
        events.clearSubscribers()
    }

    private fun resolveAndActivate() {
        val p = current ?: return
        plugins.clear()
        luaHosts.clear()

        for (dir in fs.pluginDirs(p)) {
            val mf = File(dir, "plugin.json")
            if (!mf.exists()) continue
            try {
                val m = PluginManifest.parse(Json.obj(fs.readText(mf)))
                val parent = dir.parentFile?.name ?: ""
                if (parent != m.layer.key) {
                    throw IllegalArgumentException(
                        "目录层级与 layer 不一致：plugins/$parent/ 里放了 layer=${m.layer.key} 的插件"
                    )
                }
                val lp = LoadedPlugin(m, dir)
                val saved = fs.pluginState(p, m.id)
                Json.keys(saved).forEach { k -> lp.state.put(k, saved.opt(k)) }
                plugins[m.id] = lp
            } catch (t: Throwable) {
                val broken = brokenPlugin(dir, t.message ?: "plugin.json 解析失败")
                plugins[broken.id] = broken
            }
        }

        val providerLayer = HashMap<String, String>()
        plugins.values.forEach { lp ->
            lp.manifest.provides.forEach { s -> providerLayer[s] = lp.manifest.layer.key }
        }

        val disabled = disabledIds()
        val pending = ArrayList<LoadedPlugin>()

        for (lp in plugins.values) {
            if (safetyMode) {
                lp.status = Status.DISABLED
                lp.error = "安全模式：所有插件均未装载"
                continue
            }
            if (disabled.contains(lp.id)) {
                lp.status = Status.DISABLED
                continue
            }
            if (lp.status == Status.ERROR) continue
            if (lp.manifest.layer == Layer.LOGIC) {
                val bad = lp.manifest.inject.firstOrNull { providerLayer[it] == Layer.UI.key }
                if (bad != null) {
                    lp.status = Status.ERROR
                    lp.error = "架构违规：逻辑层插件注入了 UI 层提供的服务 $bad（逻辑层不得依赖 UI 层）"
                    continue
                }
            }
            pending.add(lp)
        }

        var guard = 0
        while (pending.isNotEmpty() && guard++ < 32) {
            var progressed = false
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val lp = iterator.next()
                val missing = lp.manifest.inject.filter { !services.has(it) }
                if (missing.isNotEmpty()) continue
                iterator.remove()
                progressed = true
                try {
                    activate(lp)
                    lp.status = Status.ACTIVE
                    lp.activatedAt = System.currentTimeMillis()
                } catch (t: Throwable) {
                    lp.rollback()
                    lp.status = Status.ERROR
                    lp.error = short(t)
                    Log.w("Loom", "activate ${lp.id} failed", t)
                }
            }
            if (!progressed) break
        }
        pending.forEach { lp ->
            lp.status = Status.PENDING
            lp.missing.clear()
            lp.missing.addAll(lp.manifest.inject.filter { !services.has(it) })
        }
    }

    private fun brokenPlugin(dir: File, message: String): LoadedPlugin {
        val layerKey = dir.parentFile?.name ?: "logic"
        val layer = Layer.entries.firstOrNull { it.key == layerKey } ?: Layer.LOGIC
        val raw = JSONObject().put("id", dir.name).put("name", dir.name).put("layer", layer.key)
        val m = PluginManifest(
            id = dir.name,
            name = dir.name,
            version = "?",
            layer = layer,
            kind = if (layer == Layer.LOGIC) Kind.BUSINESS else null,
            author = "",
            description = "",
            runtime = "lua",
            entry = "source/main.lua",
            screenEntry = null,
            mainClass = null,
            inject = emptyList(),
            provides = emptyList(),
            permissions = emptyList(),
            raw = raw,
        )
        return LoadedPlugin(m, dir).apply {
            status = Status.ERROR
            error = message
        }
    }

    private fun activate(lp: LoadedPlugin) {
        if (lp.manifest.runtime == "dex") {
            activateDex(lp)
        } else {
            val host = LuaHost(this, lp)
            host.boot()
            luaHosts[lp.id] = host
        }
    }

    private fun activateDex(lp: LoadedPlugin) {
        val dex = File(lp.dir, lp.manifest.entry)
        if (!dex.exists()) throw IllegalStateException("缺少 DEX 文件 " + lp.manifest.entry)
        val hash = sha256(dex)
        if (!isTrustedDex(hash)) {
            throw IllegalStateException(
                "DEX 来源未受信（sha256 " + hash.take(12) + "…）：请先在插件页确认信任后再启用"
            )
        }
        val mainClass = lp.manifest.mainClass
            ?: throw IllegalStateException("DEX 插件必须在 plugin.json 里声明 mainClass")
        val loader = dalvik.system.DexClassLoader(
            dex.absolutePath,
            dexDir.absolutePath,
            null,
            Kernel::class.java.classLoader,
        )
        val clazz = Class.forName(mainClass, true, loader)
        val instance = clazz.getDeclaredConstructor().newInstance() as com.loom.app.api.Plugin
        lp.dexInstance = instance
        instance.activate(DexContext(lp))
        lp.add {
            runCatching { instance.deactivate() }
        }
    }

    /** DEX 插件的上下文：每个动作都登记成可撤销的 effect。 */
    private inner class DexContext(private val lp: LoadedPlugin) : PluginContext {

        override fun pluginId(): String = lp.id

        override fun config(): JSONObject = Json.copy(current?.meta)

        override fun provide(service: String, serviceImpl: PluginContext.Service) {
            val ok = services.provide(service, lp.id, lp.manifest.layer.key) { method, args ->
                serviceImpl.call(method, args)
            }
            if (!ok) throw IllegalStateException("服务名已被占用：$service")
            lp.add { services.withdraw(service, lp.id) }
        }

        override fun call(service: String, method: String, args: JSONObject?): Any? =
            callService(service, method, args ?: JSONObject(), lp.id)

        override fun emit(event: String, payload: JSONObject?) {
            emitEvent(event, lp.id, payload ?: JSONObject())
        }

        override fun subscribe(event: String, listener: PluginContext.EventListener) {
            events.on(event, lp.id) { e -> listener.onEvent(e.name, e.payload) }
            lp.add { events.off(event, lp.id) }
        }

        override fun viewScreen(title: String?, icon: String?, factory: ScreenFactory?) {
            require(lp.manifest.isUi) { "只有 layer=ui 的插件可以注册界面" }
            val f = factory ?: throw IllegalStateException("viewScreen 缺少 ScreenFactory")
            lp.screenTitle = title?.ifBlank { lp.name } ?: lp.name
            lp.screenIcon = icon?.ifBlank { "grid" } ?: "grid"
            lp.viewFactory = f
            lp.add {
                lp.viewFactory = null
            }
        }

        override fun composeScreen(title: String?, icon: String?, screen: ComposeScreen?) {
            require(lp.manifest.isUi) { "只有 layer=ui 的插件可以注册界面" }
            val sc = screen ?: throw IllegalStateException("composeScreen 缺少 ComposeScreen")
            val declared = Json.str(lp.manifest.raw, "composeAbi")
            if (declared != LoomApi.COMPOSE_ABI) {
                throw IllegalStateException(
                    "Compose 界面是版本锁定的：plugin.json 需要声明 \"composeAbi\": \"" +
                        LoomApi.COMPOSE_ABI + "\"，当前为「" + declared + "」"
                )
            }
            val hasAbi = runCatching {
                sc.javaClass.methods.any {
                    it.name == "Content" && it.parameterCount >= 1 &&
                        it.parameterTypes[0].name == "androidx.compose.runtime.Composer"
                }
            }.getOrDefault(false)
            if (!hasAbi) {
                throw IllegalStateException("Compose 界面 ABI 不匹配：找不到 Content(Composer, int)")
            }
            lp.screenTitle = title?.ifBlank { lp.name } ?: lp.name
            lp.screenIcon = icon?.ifBlank { "grid" } ?: "grid"
            lp.composeScreen = sc
            lp.add {
                lp.composeScreen = null
            }
        }

        override fun effect(undo: Runnable, label: String?) {
            lp.add { undo.run() }
        }
    }

    private fun short(t: Throwable): String {
        val m = t.message ?: t.javaClass.simpleName
        return if (m.length > 220) m.take(220) + "…" else m
    }

    // ------------------------------------------------------------ 运行期

    @Throws(Exception::class)
    fun callService(name: String, method: String, args: JSONObject, caller: String): Any? {
        events.recordCall(events.currentTraceId, caller, services.ownerOf(name), name, method)
        return services.call(name, method, args)
    }

    fun emitEvent(event: String, from: String, payload: JSONObject): String =
        events.emit(event, from, payload, events.currentTraceId).id

    fun projectConfig(): JSONObject = current?.meta?.optJSONObject("config") ?: JSONObject()

    fun storeGet(key: String): Any? {
        val p = current ?: return null
        val store = fs.store(p)
        return if (store.has(key) && !store.isNull(key)) store.opt(key) else null
    }

    fun storePut(key: String, value: Any?) {
        val p = current ?: return
        val store = fs.store(p)
        store.put(key, value ?: JSONObject.NULL)
        fs.saveStore(p, store)
    }

    fun persistState(lp: LoadedPlugin) {
        val p = current ?: return
        fs.savePluginState(p, lp.id, lp.state)
    }

    fun renderScreen(lp: LoadedPlugin): JSONObject? = luaHosts[lp.id]?.renderScreen()

    // ------------------------------------------------------------ 状态与查询

    fun list(): List<LoadedPlugin> = plugins.values.sortedWith(
        compareBy(
            { when {
                it.manifest.isUi -> 0
                it.manifest.isCapability -> 2
                else -> 1
            } },
            { it.name },
        )
    )

    fun uiScreens(): List<LoadedPlugin> = list().filter {
        it.status == Status.ACTIVE && it.manifest.isUi &&
            (it.declScreen != null || it.viewFactory != null || it.composeScreen != null)
    }

    fun count(status: Status): Int = plugins.values.count { it.status == status }

    fun pluginCount(): Int = plugins.size

    /** 应用启动时由 LoomApp 注入（启动计数 / 手动标记）。 */
    fun initSafety(on: Boolean, reason: String) {
        safetyMode = on
        safetyReason = reason
    }

    fun setSafeMode(on: Boolean, reason: String) {
        safetyMode = on
        safetyReason = reason
        prefs.edit().putBoolean("safety", on).putString("safety_reason", reason).apply()
        reload()
    }

    // ------------------------------------------------------------ 安全模式 / DEX 信任

    fun isTrustedDex(hash: String): Boolean {
        val raw = prefs.getString("trusted_dex", "") ?: ""
        return raw.split('\n').any { it.trim() == hash }
    }

    fun trustDex(hash: String) {
        if (isTrustedDex(hash)) return
        val raw = prefs.getString("trusted_dex", "") ?: ""
        prefs.edit().putString("trusted_dex", raw + hash + "\n").apply()
    }

    fun sha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                md.update(buffer, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        ""
    }
}
