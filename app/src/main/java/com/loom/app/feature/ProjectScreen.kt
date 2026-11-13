package com.loom.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.AppState
import com.loom.app.ExportRequest
import com.loom.app.ImportKind
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.Status
import com.loom.app.kernel.Layer
import com.loom.app.kernel.ProjectFs
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.ConfirmPanel
import com.loom.app.ui.Footnote
import com.loom.app.ui.Hairline
import com.loom.app.ui.IconBtn
import com.loom.app.ui.ListRow
import com.loom.app.ui.LoomButton
import com.loom.app.ui.LoomIcon
import com.loom.app.ui.LoomSwitch
import com.loom.app.ui.PromptPanel
import com.loom.app.ui.ScreenHeader
import com.loom.app.ui.SectionHeader
import com.loom.app.ui.StatStrip
import com.loom.app.ui.SurfaceCard
import com.loom.app.ui.VSpace
import com.loom.app.ui.Dot
import com.loom.app.ui.Fmt
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.Sp

/** 区域二：项目详情（装配清单、配置、导出、危险区）。 */
@Composable
fun ProjectScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    val project = kernel.current

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (project == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Sp.page, vertical = Sp.x3)
        ) {
            ScreenHeader("PROJECT / 项目", "项目", "项目是自包含的运行时单元。")
            VSpace(24)
            com.loom.app.ui.EmptyBlock(
                icon = "stack",
                title = "还没有打开的项目",
                body = "插件只装在项目里。先从工作台打开或新建一个项目。",
                primaryLabel = "去工作台",
                onPrimary = {
                    state.tab = 0
                    state.refresh()
                },
            )
        }
        return
    }

    if (renaming) {
        PromptPanel(
            eyebrow = "RENAME / 重命名",
            title = "改项目名",
            label = "项目名",
            hint = "新的名字",
            initial = project.name,
            confirmLabel = "保存",
            validate = { if (it.isEmpty()) "项目名不能为空" else null },
            onDismiss = { renaming = false },
            onConfirm = { name ->
                project.name = name
                kernel.fs.save(project)
                state.show("项目已重命名")
            },
        )
    }
    if (deleting) {
        ConfirmPanel(
            eyebrow = "DANGER / 删除",
            title = "删除「${project.name}」？",
            body = "会连同项目里的插件副本、项目数据与导出快照一起删除，不可撤销。建议先导出 .loomproj。",
            confirmLabel = "删除项目",
            danger = true,
            onDismiss = { deleting = false },
            onConfirm = {
                kernel.fs.delete(project)
                kernel.closeProject()
                state.show("项目已删除")
                state.tab = 0
            },
        )
    }

    val narrow = LocalConfiguration.current.screenWidthDp < 360

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sp.page)
            .padding(top = Sp.x3, bottom = Sp.x7)
    ) {
        ScreenHeader(
            eyebrow = "PROJECT / 项目",
            title = project.name,
            sub = "plugins/ui 与 plugins/logic 分栏装载；导出后可在另一台设备复原。",
            action = { IconBtn("export", "导出项目") { exportProject(state, project) } },
        )
        VSpace(Sp.x2)
        Text(
            "${project.id} · 建于 ${Fmt.date(project.created)}",
            style = LoomType.mono.copy(fontSize = LoomType.micro.fontSize),
            color = Loom.colors.faint,
        )

        VSpace(22)
        SurfaceCard {
            StatStrip(
                listOf(
                    kernel.count(Status.ACTIVE).toString() to "ACTIVE / 活跃",
                    kernel.count(Status.PENDING).toString() to "PENDING / 等待",
                )
            )
            VSpace(Sp.x4)
            Hairline()
            VSpace(Sp.x4)
            StatStrip(
                listOf(
                    kernel.services.entries().size.toString() to "SERVICES / 服务",
                    Fmt.bytes(kernel.fs.sizeOf(project)) to "SIZE / 占用",
                )
            )
        }

        VSpace(20)
        RunBlock(state)

        VSpace(30)
        Inventory(state, narrow)

        VSpace(30)
        Services(state)

        VSpace(30)
        Config(state, project) { renaming = true }

        VSpace(30)
        Transfer(state)

        VSpace(34)
        DangerZone { deleting = true }
    }

}

@Composable
private fun RunBlock(state: AppState) {
    val kernel = state.kernel
    val screens = kernel.uiScreens()
    if (screens.isNotEmpty()) {
        LoomButton("运行项目（${screens.size} 个界面）", BtnStyle.Primary, "play") {
            state.runOpen = true
        }
        VSpace(Sp.x2)
        Footnote("运行台只渲染 UI 层插件贡献的界面；按钮发意图事件，逻辑层订阅后执行。")
    } else {
        LoomButton("暂无可运行界面", BtnStyle.Secondary, "play", enabled = false) {}
        VSpace(Sp.x2)
        Footnote("UI 层插件决定这里能不能运行。去插件页导入一个含 screen.lua 的 UI 插件。")
    }
}

@Composable
private fun Inventory(state: AppState, narrow: Boolean) {
    val kernel = state.kernel
    SectionHeader("01", "装配清单") {
        Text(
            "管理",
            style = LoomType.small,
            color = Loom.colors.accent,
            modifier = Modifier
                .clickable {
                    state.tab = 2
                    state.refresh()
                }
                .padding(horizontal = Sp.x2, vertical = Sp.x3),
        )
    }
    VSpace(Sp.x3)
    val ui = kernel.list().filter { it.manifest.layer == Layer.UI }
    val logic = kernel.list().filter { it.manifest.layer == Layer.LOGIC }
    if (narrow) {
        LayerColumn("UI 层", "plugins/ui", ui)
        VSpace(Sp.x4)
        LayerColumn("逻辑层", "plugins/logic", logic)
    } else {
        Row {
            Box(Modifier.weight(1f)) { LayerColumn("UI 层", "plugins/ui", ui) }
            Spacer(Modifier.width(Sp.x2))
            Box(Modifier.weight(1f)) { LayerColumn("逻辑层", "plugins/logic", logic) }
        }
    }
}

@Composable
private fun LayerColumn(title: String, path: String, list: List<LoadedPlugin>) {
    val c = Loom.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = LoomType.title, color = c.ink, modifier = Modifier.weight(1f))
            Text(list.size.toString(), style = LoomType.mono, color = c.faint)
        }
        VSpace(Sp.x1)
        Text(path, style = LoomType.mono.copy(fontSize = LoomType.micro.fontSize), color = c.faint)
        VSpace(Sp.x3)
        if (list.isEmpty()) {
            Text("空", style = LoomType.small, color = c.faint)
        } else {
            list.forEach { lp ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(Loom.statusColor(lp.status.code), 6.dp)
                    Spacer(Modifier.width(Sp.x2))
                    Text(
                        lp.name,
                        style = LoomType.small,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(lp.status.code, style = LoomType.mono.copy(fontSize = 10.sp), color = Loom.statusColor(lp.status.code))
                }
            }
        }
    }
}

@Composable
private fun Services(state: AppState) {
    val kernel = state.kernel
    SectionHeader("02", "服务注册表")
    VSpace(Sp.x2)
    Footnote("所有插件间调用都发生在这里。服务名被占用时提供者装载失败，不会静默覆盖。")
    VSpace(Sp.x3)
    val entries = kernel.services.entries()
    if (entries.isEmpty()) {
        Text("暂无注册服务", style = LoomType.small, color = Loom.colors.faint)
        return
    }
    entries.forEach { e ->
        ListRow(
            title = e.name,
            subtitle = "提供者 ${e.owner} · ${e.layer} 层",
            mono = "调用 ${e.calls} 次",
            leadingIcon = "plug",
        )
        Hairline()
    }
}

@Composable
private fun Config(state: AppState, project: ProjectFs.Project, onRename: () -> Unit) {
    val kernel = state.kernel
    var contract by remember { mutableStateOf(false) }
    SectionHeader("03", "项目配置")
    VSpace(Sp.x3)
    ListRow("项目名", project.name, onClick = onRename)
    Hairline()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Sp.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("运行追踪", style = LoomType.title, color = Loom.colors.ink)
            VSpace(2)
            Text(
                if (kernel.events.tracing) "记录因果链，供运行链条视图消费" else "已关闭，不产生追踪开销",
                style = LoomType.small,
                color = Loom.colors.muted,
            )
        }
        LoomSwitch(kernel.events.tracing, { on ->
            kernel.events.tracing = on
            kernel.prefs.edit().putBoolean("tracing", on).apply()
            state.refresh()
        }, label = "运行追踪")
    }
    Hairline()
    ListRow(
        title = "插件契约",
        subtitle = "plugin.json 必填字段与 Lua 插件 API",
        mono = "v1",
        leadingIcon = "code",
        onClick = { contract = true },
    )
    Hairline()
    ListRow(
        title = "导出快照",
        subtitle = "每次导出会往 exports/ 写一份 snapshot.json",
        mono = Fmt.bytes(
            (kernel.fs.exportsDir(project).listFiles() ?: emptyArray()).sumOf { if (it.isFile) it.length() else 0L }
        ),
        leadingIcon = "export",
    )
    if (contract) ContractSheet(onDismiss = { contract = false })
}

@Composable
private fun Transfer(state: AppState) {
    val project = state.kernel.current ?: return
    SectionHeader("04", "导出与导入")
    VSpace(Sp.x3)
    LoomButton("导出 .loomproj", BtnStyle.Primary, "export") { exportProject(state, project) }
    VSpace(Sp.x2)
    LoomButton("导出插件源码包", BtnStyle.Secondary, "code") {
        state.startExport(
            ExportRequest("application/zip", safeName(project.name) + "-plugins.zip") { out ->
                state.kernel.fs.exportPluginsZip(project, out)
            }
        )
    }
    VSpace(Sp.x2)
    LoomButton("导入插件包", BtnStyle.Secondary, "import") {
        state.startImport(ImportKind.Plugin)
    }
}

@Composable
private fun DangerZone(onDelete: () -> Unit) {
    val c = Loom.colors
    Hairline(color = c.danger.copy(alpha = 0.45f))
    VSpace(Sp.x4)
    Text("DANGER / 危险操作", style = LoomType.eyebrow, color = c.danger)
    VSpace(Sp.x2)
    Footnote("以下操作不可撤销，且与上方常规操作分开。")
    VSpace(Sp.x3)
    LoomButton("删除这个项目", BtnStyle.DangerOutline, "trash", onClick = onDelete)
}

private fun safeName(n: String): String = n.replace(Regex("[\\\\/:*?\"<>|]"), "_")

private fun exportProject(state: AppState, project: ProjectFs.Project) {
    state.startExport(
        ExportRequest("application/zip", safeName(project.name) + ".loomproj") { out ->
            state.kernel.fs.exportZip(project, out)
        }
    )
}
