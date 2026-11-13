package com.loom.app.kernel

import org.json.JSONObject

enum class Layer(val key: String, val cn: String) {
    UI("ui", "UI 层"),
    LOGIC("logic", "逻辑层"),
}

enum class Kind(val key: String, val cn: String) {
    BUSINESS("business", "业务"),
    CAPABILITY("capability", "能力"),
}

enum class Status(val code: String, val cn: String) {
    ACTIVE("ACTIVE", "活跃"),
    PENDING("PENDING", "等待依赖"),
    ERROR("ERROR", "加载失败"),
    DISABLED("DISABLED", "已停用"),
}

/**
 * plugin.json 的强类型视图。
 * 必填字段（PRD 8.2）：id / name / version / layer / inject / provides / permissions / entry。
 */
class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val layer: Layer,
    val kind: Kind?,
    val author: String,
    val description: String,
    val runtime: String,
    val entry: String,
    val screenEntry: String?,
    val mainClass: String?,
    val inject: List<String>,
    val provides: List<String>,
    val permissions: List<String>,
    val raw: JSONObject,
) {
    val isUi: Boolean get() = layer == Layer.UI
    val isCapability: Boolean get() = layer == Layer.LOGIC && kind == Kind.CAPABILITY
    val layerLabel: String
        get() = when {
            isUi -> "UI 层"
            isCapability -> "逻辑 · 能力"
            else -> "逻辑 · 业务"
        }

    companion object {
        fun parse(o: JSONObject): PluginManifest {
            val id = Json.str(o, "id").trim()
            require(id.isNotEmpty()) { "plugin.json 缺少必填字段：id" }
            require(!id.contains("/") && !id.contains("..")) { "插件 id 不能包含路径分隔符" }
            val name = Json.str(o, "name", id)
            val layerKey = Json.str(o, "layer")
            val layer = Layer.entries.firstOrNull { it.key == layerKey }
                ?: throw IllegalArgumentException("layer 必须是 ui 或 logic，当前为「$layerKey」")
            val kind = if (layer == Layer.LOGIC) {
                val k = Json.str(o, "kind")
                Kind.entries.firstOrNull { it.key == k }
                    ?: throw IllegalArgumentException("逻辑层插件必须声明 kind=business|capability")
            } else {
                null
            }
            val runtime = Json.str(o, "runtime", if (Json.str(o, "entry").endsWith(".dex")) "dex" else "lua")
            val entry = Json.str(o, "entry", if (runtime == "dex") "source/plugin.dex" else "source/main.lua")
            return PluginManifest(
                id = id,
                name = name,
                version = Json.str(o, "version", "1.0.0"),
                layer = layer,
                kind = kind,
                author = Json.str(o, "author"),
                description = Json.str(o, "description"),
                runtime = runtime,
                entry = entry,
                screenEntry = Json.str(o, "screen").ifBlank { null },
                mainClass = Json.str(o, "mainClass").ifBlank { null },
                inject = Json.strList(o, "inject"),
                provides = Json.strList(o, "provides"),
                permissions = Json.strList(o, "permissions"),
                raw = o,
            )
        }
    }
}
