package com.loom.app.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.ui.Hairline
import com.loom.app.ui.InfoPanel
import com.loom.app.ui.VSpace
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.Sp

private val CONTRACT = listOf(
    "一、plugin.json（必填字段）" to
        "id            唯一标识，不能含路径分隔符\n" +
        "name          显示名\n" +
        "version       语义化版本\n" +
        "layer         ui | logic\n" +
        "kind          仅逻辑层：business | capability\n" +
        "inject        依赖的服务名数组\n" +
        "provides      提供的服务名数组\n" +
        "permissions   能力声明数组\n" +
        "entry         入口：source/main.lua 或 source/plugin.dex\n" +
        "screen        仅 UI 层：source/screen.lua\n" +
        "mainClass     仅 DEX：实现 com.loom.app.api.Plugin 的类名",
    "二、目录规约" to
        "plugins/ui/{id}/      layer=ui 必须放这里\n" +
        "plugins/logic/{id}/   layer=logic 必须放这里\n" +
        "目录层级与 layer 不一致 → 插件直接 ERROR。",
    "三、Lua 插件 API（main.lua）" to
        "local ctx = ...\n\n" +
        "ctx.provide(\"count\", {\n" +
        "  inc  = function(args) ... return n end,\n" +
        "  get  = function() return n end,\n" +
        "})\n" +
        "ctx.on(\"count.inc\", function(payload) ... end)\n" +
        "ctx.emit(\"count.changed\", { value = 1 })\n" +
        "ctx.call(\"count\", \"get\")     -- 无返回值版\n" +
        "ctx.svc(\"count\", \"get\")      -- 返回值版\n" +
        "ctx.state.get(\"n\", 0) / ctx.state.set(\"n\", 1)\n" +
        "ctx.store.get(\"k\") / ctx.store.set(\"k\", v)\n" +
        "ctx.log(\"对调试器可见的日志\")",
    "四、screen.lua（UI 插件）" to
        "local ctx = ...\n" +
        "return {\n" +
        "  title = \"计数面板\", icon = \"grid\", sub = \"一句说明\",\n" +
        "  root = { type = \"column\", children = {\n" +
        "    { type = \"eyebrow\", text = \"COUNTER\" },\n" +
        "    { type = \"metric\", label = \"当前计数\", value = ctx.svc(\"count\",\"get\"), unit = \"STITCH\" },\n" +
        "    { type = \"button\", text = \"+1\", emit = \"count.inc\" },\n" +
        "    { type = \"note\", text = \"说明文字\" },\n" +
        "  }},\n" +
        "}",
    "五、可用节点" to
        "column / row / eyebrow / headline / text / metric /\n" +
        "button / ghost / divider / space / note / tag / kv",
    "六、沙箱边界" to
        "脚本只用 Base / Table / String / Math / Coroutine；\n" +
        "没有 io、os、package、debug，也没有 Java 反射。\n" +
        "dofile / loadfile / load / require 已被移除。\n" +
        "已知限制：没有指令级超时，死循环会让主线程卡住。",
    "七、DEX 插件" to
        "实现 com.loom.app.api.Plugin，经 PluginContext 注册服务 / 订阅事件 / emit。\n" +
        "同进程加载，没有沙箱，等同宿主权限；装载前必须显式信任 sha256。\n" +
        "界面贡献点两种：\n" +
        "  ctx.viewScreen(title, icon, ctx -> View)   任意 Compose 版本可用\n" +
        "  ctx.composeScreen(title, icon, ComposeScreen())  需声明 composeAbi\n" +
        "Compose 界面与宿主 Compose BOM 版本锁定，装载时会校验，不匹配即 ERROR。",
    "八、可逆卸载" to
        "所有注册都包在 effect 里：服务撤销、事件退订、DEX deactivate。\n" +
        "卸载时逆序执行，并清掉项目内 state 文件。",
)

/** 插件契约说明（在项目详情与设置里都能打开）。 */
@Composable
fun ContractSheet(onDismiss: () -> Unit) {
    InfoPanel(eyebrow = "CONTRACT / 插件契约 v1", title = "怎么写一个插件", onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            CONTRACT.forEachIndexed { index, (title, body) ->
                if (index > 0) {
                    VSpace(Sp.x3)
                    Hairline()
                    VSpace(Sp.x3)
                }
                Text(title, style = LoomType.h2, color = Loom.colors.ink)
                VSpace(Sp.x1)
                Text(body, style = LoomType.mono.copy(fontSize = 11.sp), color = Loom.colors.muted)
            }
        }
    }
}
