package com.loom.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.AppState
import com.loom.app.ImportKind
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.Layer
import com.loom.app.kernel.Status
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.ConfirmPanel
import com.loom.app.ui.EmptyBlock
import com.loom.app.ui.Footnote
import com.loom.app.ui.Hairline
import com.loom.app.ui.InfoPanel
import com.loom.app.ui.LoomButton
import com.loom.app.ui.LoomChip
import com.loom.app.ui.LoomSwitch
import com.loom.app.ui.ScreenHeader
import com.loom.app.ui.SectionHeader
import com.loom.app.ui.Segmented
import com.loom.app.ui.StatusChip
import com.loom.app.ui.TagChip
import com.loom.app.ui.VSpace
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp

private val FILTERS = listOf("全部", "UI 层", "逻辑层", "能力")

/** 区域三：插件管理器。 */
@Composable
fun PluginsScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    val project = kernel.current
    var filter by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }
    var sourceOf by remember { mutableStateOf<String?>(null) }

    if (project == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Sp.page, vertical = Sp.x3)
        ) {
            ScreenHeader("PLUGINS / 插件", "插件管理器", "插件只能装在项目里。")
            VSpace(24)
            EmptyBlock(
                icon = "plug",
                title = "还没有打开的项目",
                body = "插件属于项目。先在工作台打开或新建一个项目，再导入插件。",
                primaryLabel = "去工作台",
                onPrimary = {
                    state.tab = 0
                    state.refresh()
                },
            )
        }
        return
    }

    val all = kernel.list()
    val filtered = all.filter {
        when (filter) {
            1 -> it.manifest.layer == Layer.UI
            2 -> it.manifest.layer == Layer.LOGIC
            3 -> it.manifest.isCapability
            else -> true
        }
    }

    detail?.let { id ->
        val lp = kernel.plugins[id]
        if (lp != null) {
            PluginDetail(
                state = state,
                lp = lp,
                onDismiss = { detail = null },
                onUninstall = {
                    detail = null
                    confirmUninstall = id
                },
                onSource = {
                    detail = null
                    sourceOf = id
                },
            )
        }
    }
    confirmUninstall?.let { id ->
        val lp = kernel.plugins[id]
        if (lp != null) {
            ConfirmPanel(
                eyebrow = "DANGER / 卸载",
                title = "卸载「${lp.name}」？",
                body = "卸载会逆序撤销它注册的所有服务与订阅，并删除插件目录与项目内状态，不可撤销。依赖它的插件会转入 PENDING。",
                confirmLabel = "卸载插件",
                danger = true,
                onDismiss = { confirmUninstall = null },
                onConfirm = {
                    kernel.uninstall(id)
                    state.show("已卸载 ${lp.name}，注册表已清理")
                },
            )
        }
    }
    sourceOf?.let { id ->
        val lp = kernel.plugins[id]
        if (lp != null) {
            SourcePanel(lp, onDismiss = { sourceOf = null })
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Sp.page).padding(top = Sp.x3)) {
            ScreenHeader(
                eyebrow = "PLUGINS / 插件 · ${project.name}",
                title = "插件管理器",
                sub = "层级、状态、权限、依赖都在这里看清楚。",
            )
            VSpace(Sp.x4)
            Segmented(FILTERS, filter, { filter = it })
            VSpace(Sp.x3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Footnote(
                    "${Status.ACTIVE.code} ${kernel.count(Status.ACTIVE)} · " +
                        "${Status.PENDING.code} ${kernel.count(Status.PENDING)} · " +
                        "${Status.ERROR.code} ${kernel.count(Status.ERROR)} · " +
                        "${Status.DISABLED.code} ${kernel.count(Status.DISABLED)}",
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "刷新",
                    style = LoomType.small,
                    color = Loom.colors.accent,
                    modifier = Modifier
                        .clickable {
                            kernel.reload()
                            state.refresh()
                        }
                        .padding(horizontal = Sp.x2, vertical = Sp.x2),
                )
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Sp.page, end = Sp.page, top = Sp.x3, bottom = Sp.x6,
        )) {
            if (filtered.isEmpty()) {
                item {
                    EmptyBlock(
                        icon = "plug",
                        title = if (all.isEmpty()) "这个项目还没有插件" else "这个筛选下没有插件",
                        body = if (all.isEmpty())
                            "插件是独立文件：一个含 plugin.json 的 zip 包。导入后才会出现在这里。"
                        else "换个筛选看看。",
                        primaryLabel = "导入插件包",
                        onPrimary = { state.startImport(ImportKind.Plugin) },
                    )
                }
            }
            items(filtered, key = { it.id }) { lp ->
                PluginRow(
                    state = state,
                    lp = lp,
                    onOpen = { detail = lp.id },
                )
                Hairline()
            }
            item {
                VSpace(Sp.x5)
                LoomButton("导入插件包", BtnStyle.Secondary, "import") {
                    state.startImport(ImportKind.Plugin)
                }
                VSpace(Sp.x2)
                Footnote("包内必须有 plugin.json。装在 plugins/ui/ 还是 plugins/logic/ 由 manifest 的 layer 决定，目录不一致会被判为 ERROR。")
            }
        }
    }
}

@Composable
private fun PluginRow(state: AppState, lp: LoadedPlugin, onOpen: () -> Unit) {
    val c = Loom.colors
    val kernel = state.kernel
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = Sp.x3)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lp.name, style = LoomType.title, color = c.ink, maxLines = 1)
                    Spacer(Modifier.width(Sp.x2))
                    if (lp.manifest.isCapability) TagChip("能力") else TagChip(if (lp.manifest.isUi) "UI" else "逻辑")
                }
                VSpace(3)
                Text(
                    "${lp.id} · ${lp.manifest.layerLabel} · v${lp.manifest.version}",
                    style = LoomType.mono.copy(fontSize = 11.sp),
                    color = c.faint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Sp.x2))
            StatusChip(lp.status.code)
        }

        if (lp.manifest.permissions.isNotEmpty()) {
            VSpace(Sp.x2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lp.manifest.permissions.take(4).forEach { p ->
                    LoomChip(p, c.muted, c.surfaceAlt, null, c.hairline)
                }
            }
        }
        if (lp.missing.isNotEmpty()) {
            VSpace(Sp.x2)
            Text(
                "缺少依赖：" + lp.missing.joinToString("、") + "（提供者未装载）",
                style = LoomType.micro,
                color = c.warn,
            )
        }
        if (!lp.error.isNullOrBlank()) {
            VSpace(Sp.x2)
            Text(lp.error ?: "", style = LoomType.micro, color = c.danger)
        }
        VSpace(Sp.x2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Footnote(
                if (kernel.isEnabled(lp.id)) "启用中 · 效果 ${lp.effects.size} 条" else "已停用 · 不装载任何代码",
                modifier = Modifier.weight(1f),
            )
            LoomSwitch(
                checked = kernel.isEnabled(lp.id),
                onCheckedChange = { on ->
                    kernel.toggleEnabled(lp.id, on)
                    state.show(if (on) "已启用 ${lp.name}" else "已停用 ${lp.name}，依赖它的插件会转入 PENDING")
                },
                label = "启用 ${lp.name}",
            )
        }
    }
}

@Composable
private fun PluginDetail(
    state: AppState,
    lp: LoadedPlugin,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit,
    onSource: () -> Unit,
) {
    val c = Loom.colors
    val kernel = state.kernel
    InfoPanel(
        eyebrow = "PLUGIN / 插件 · ${lp.id}",
        title = lp.name,
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(lp.status.code)
                Spacer(Modifier.width(Sp.x2))
                Text(
                    "${lp.manifest.layerLabel} · v${lp.manifest.version}",
                    style = LoomType.mono,
                    color = c.muted,
                )
            }
            if (!lp.manifest.description.isBlank()) {
                VSpace(Sp.x3)
                Text(lp.manifest.description, style = LoomType.body, color = c.muted)
            }
            if (!lp.error.isNullOrBlank()) {
                VSpace(Sp.x3)
                ToneBox(c.warnSoft) {
                    Text(lp.error ?: "", style = LoomType.small, color = c.danger)
                }
            }

            VSpace(Sp.x4)
            SectionHeader(null, "依赖")
            VSpace(Sp.x2)
            if (lp.manifest.inject.isEmpty()) {
                Text("无（不依赖任何服务）", style = LoomType.small, color = c.muted)
            } else {
                lp.manifest.inject.forEach { service ->
                    val owner = kernel.services.ownerOf(service)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(service, style = LoomType.mono, color = c.ink, modifier = Modifier.weight(1f))
                        Text(
                            if (owner != null) "← $owner" else "缺失",
                            style = LoomType.mono,
                            color = if (owner != null) c.ok else c.warn,
                        )
                    }
                }
            }

            VSpace(Sp.x3)
            SectionHeader(null, "提供")
            VSpace(Sp.x2)
            if (lp.manifest.provides.isEmpty()) {
                Text("不提供服务（只贡献界面或订阅事件）", style = LoomType.small, color = c.muted)
            } else {
                lp.manifest.provides.forEach { service ->
                    val calls = kernel.services.lookup(service)?.calls ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(service, style = LoomType.mono, color = c.ink, modifier = Modifier.weight(1f))
                        Text("调用 $calls 次", style = LoomType.mono, color = c.muted)
                    }
                }
            }

            VSpace(Sp.x3)
            SectionHeader(null, "权限声明")
            VSpace(Sp.x2)
            if (lp.manifest.permissions.isEmpty()) {
                Text("未声明任何能力", style = LoomType.small, color = c.muted)
            } else {
                lp.manifest.permissions.forEach { p ->
                    Text("· ${permissionText(p)}", style = LoomType.small, color = c.muted)
                }
            }

            VSpace(Sp.x3)
            SectionHeader(null, "实现")
            VSpace(Sp.x2)
            val screenKind = when {
                lp.declScreen != null -> "声明式（screen.lua）"
                lp.viewFactory != null -> "原生 View（AndroidView）"
                lp.composeScreen != null -> "Compose（ABI 锁定）"
                else -> "无"
            }
            Text(
                "运行时：${if (lp.manifest.runtime == "dex") "DEX（同进程，非沙箱）" else "Lua 沙箱（无 io/os/require）"}\n" +
                    "入口：${lp.manifest.entry}\n" +
                    "界面贡献点：$screenKind\n" +
                    "可逆效果：${lp.effects.size} 条",
                style = LoomType.mono.copy(fontSize = 11.sp),
                color = c.muted,
            )

            if (lp.manifest.runtime == "dex") {
                val dex = java.io.File(lp.dir, lp.manifest.entry)
                val hash = if (dex.exists()) kernel.sha256(dex) else ""
                VSpace(Sp.x3)
                ToneBox(c.dangerSoft) {
                    Text(
                        "DEX 插件与宿主同进程加载，没有沙箱隔离，等同宿主权限。\nsha256 $hash",
                        style = LoomType.micro,
                        color = c.danger,
                    )
                }
                if (hash.isNotEmpty() && !kernel.isTrustedDex(hash)) {
                    VSpace(Sp.x2)
                    LoomButton("信任这个来源并重新装载", BtnStyle.Secondary, "shield") {
                        kernel.trustDex(hash)
                        kernel.reload()
                        state.show("已信任该 DEX 来源，重新装载完成")
                        onDismiss()
                    }
                }
            }
        }

        VSpace(Sp.x4)
        Row {
            when (lp.status) {
                Status.ACTIVE -> LoomButton("停用", BtnStyle.Secondary, "pause", modifier = Modifier.weight(1f)) {
                    kernel.toggleEnabled(lp.id, false)
                    state.show("已停用 ${lp.name}")
                    onDismiss()
                }
                else -> LoomButton("启用", BtnStyle.Primary, "play", modifier = Modifier.weight(1f)) {
                    kernel.toggleEnabled(lp.id, true)
                    state.show("已启用 ${lp.name}")
                    onDismiss()
                }
            }
            Spacer(Modifier.width(Sp.x2))
            LoomButton("源码", BtnStyle.Secondary, "code", modifier = Modifier.weight(1f), onClick = onSource)
        }
        VSpace(Sp.x2)
        if (lp.manifest.isUi && lp.status == Status.ACTIVE) {
            LoomButton("打开界面", BtnStyle.Primary, "play") {
                onDismiss()
                state.runOpen = true
            }
            VSpace(Sp.x2)
        }
        LoomButton("卸载（不可撤销）", BtnStyle.DangerOutline, "trash", onClick = onUninstall)
    }
}

@Composable
private fun SourcePanel(lp: LoadedPlugin, onDismiss: () -> Unit) {
    val c = Loom.colors
    val entry = java.io.File(lp.dir, lp.manifest.entry)
    val screen = java.io.File(lp.dir, lp.manifest.screenEntry ?: "source/screen.lua")
    val text = buildString {
        append("// plugin.json\n")
        append(runCatching { java.io.File(lp.dir, "plugin.json").readText() }.getOrDefault("（缺失）"))
        append("\n\n// ${lp.manifest.entry}\n")
        append(if (entry.exists()) runCatching { entry.readText() }.getOrDefault("（读取失败）") else "（缺失）")
        if (screen.exists() && screen.absolutePath != entry.absolutePath) {
            append("\n\n// ${lp.manifest.screenEntry ?: "source/screen.lua"}\n")
            append(runCatching { screen.readText() }.getOrDefault("（读取失败）"))
        }
    }
    InfoPanel(eyebrow = "SOURCE / 源码", title = lp.name, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text(
                text,
                style = LoomType.mono.copy(fontSize = 11.sp),
                color = c.ink,
            )
        }
    }
}

private fun permissionText(p: String): String = when (p) {
    "storage" -> "storage：读写项目 data/ 目录"
    "network" -> "network：发起网络请求"
    "camera" -> "camera：调用相机"
    "location" -> "location：读取定位"
    "sensors" -> "sensors：读取传感器"
    "clipboard" -> "clipboard：读写剪贴板"
    else -> p
}

@Composable
private fun ToneBox(bg: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.r2))
            .background(bg)
            .padding(Sp.x3)
    ) { content() }
}
