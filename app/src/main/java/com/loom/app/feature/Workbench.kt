package com.loom.app.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loom.app.AppState
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.Status
import com.loom.app.kernel.ProjectFs
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.Footnote
import com.loom.app.ui.Hairline
import com.loom.app.ui.IconBtn
import com.loom.app.ui.LoomButton
import com.loom.app.ui.LoomChip
import com.loom.app.ui.LoomIcon
import com.loom.app.ui.Notice
import com.loom.app.ui.PromptPanel
import com.loom.app.ui.ScreenHeader
import com.loom.app.ui.SectionHeader
import com.loom.app.ui.StatStrip
import com.loom.app.ui.SurfaceCard
import com.loom.app.ui.VSpace
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp

/** 区域一：项目工作台（首屏）。 */
@Composable
fun WorkbenchScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        PromptPanel(
            eyebrow = "NEW PROJECT / 新建",
            title = "创建一个装配单元",
            label = "项目名",
            hint = "例如：夜里的计数器",
            confirmLabel = "创建项目",
            validate = { if (it.isEmpty()) "项目名不能为空" else null },
            onDismiss = { creating = false },
            onConfirm = { name ->
                runCatching { kernel.fs.create(name) }
                    .onSuccess { p ->
                        kernel.openProject(p)
                        state.show("项目「${p.name}」已创建，下一步：导入第一个插件")
                        state.tab = 2
                    }
                    .onFailure { state.show("创建失败：" + (it.message ?: "未知错误")) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sp.page)
            .padding(top = Sp.x3, bottom = Sp.x7)
    ) {
        ScreenHeader(
            eyebrow = "LOOM / 织机 · 装配运行时",
            title = "工作台",
            sub = "宿主只提供织机。项目里的插件决定这个应用能做什么。",
            action = { IconBtn("plus", "新建项目") { creating = true } },
        )

        if (kernel.safetyMode) {
            VSpace(20)
            Notice(
                icon = "shield",
                text = if (kernel.safetyReason.isBlank())
                    "安全模式已开启：所有插件均未装载，项目数据保持不变。"
                else kernel.safetyReason + " 所有插件均未装载。",
                fg = Loom.colors.warn,
                bg = Loom.colors.warnSoft,
            )
            VSpace(Sp.x2)
            LoomButton("退出安全模式并重新装载", BtnStyle.Secondary, "power") {
                kernel.setSafeMode(false, "")
                state.show("已退出安全模式，插件重新解析并装载")
            }
        }

        if (kernel.projects().isEmpty()) {
            VSpace(26)
            Hero(state)
        } else {
            VSpace(26)
            Recent(state) { creating = true }
        }

        VSpace(30)
        Overview(state)
        VSpace(26)
        Hairline()
        VSpace(Sp.x4)
        Footnote(
            "Loom 不内置任何业务功能：没有计算器、没有笔记、没有待办。" +
                "你看到的一切都来自项目里装配的插件。插件之间不直接引用，只经服务总线与事件总线通信。"
        )
    }
}

/** 织机纹样：五经四纬 + 一处铜色交织，纯绘制。 */
@Composable
private fun LoomPattern() {
    val c = Loom.colors
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val pad = 18.dp.toPx()
        val left = pad
        val top = pad
        val right = size.width - pad
        val bottom = size.height - pad * 1.6f
        val cols = 7
        val rows = 4
        val cw = (right - left) / cols
        val ch = (bottom - top) / rows

        for (i in 0 until cols step 2) {
            for (j in 0 until rows step 2) {
                drawRect(
                    color = c.surfaceAlt,
                    topLeft = Offset(left + i * cw, top + j * ch),
                    size = Size(cw, ch),
                )
            }
        }
        for (i in 0..cols) {
            drawLine(c.hairline, Offset(left + i * cw, top), Offset(left + i * cw, bottom), strokeWidth = 1f)
        }
        for (j in 0..rows) {
            drawLine(c.hairline, Offset(left, top + j * ch), Offset(right, top + j * ch), strokeWidth = 1f)
        }
        drawRect(
            color = c.accent,
            topLeft = Offset(left + 3 * cw, top + ch),
            size = Size(cw, ch),
        )
        // 梭线
        drawLine(
            color = c.accent,
            start = Offset(left + cw * 1.2f, bottom + 10.dp.toPx()),
            end = Offset(left + cw * 5.2f, bottom + 10.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
private fun Hero(state: AppState) {
    val c = Loom.colors
    LoomPattern()
    VSpace(Sp.x5)
    Text("这里没有功能，只有织机。", style = LoomType.h1, color = c.ink)
    VSpace(Sp.x2)
    Text(
        "创建第一个项目，把插件织进同一个运行时。项目是自包含的：配置、插件副本、数据都在里面，可以整个导出带走。",
        style = LoomType.body,
        color = c.muted,
    )
    VSpace(Sp.x6)
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PromptPanel(
            eyebrow = "NEW PROJECT / 新建",
            title = "创建一个装配单元",
            label = "项目名",
            hint = "例如：夜里的计数器",
            confirmLabel = "创建项目",
            validate = { if (it.isEmpty()) "项目名不能为空" else null },
            onDismiss = { creating = false },
            onConfirm = { name ->
                runCatching { state.kernel.fs.create(name) }
                    .onSuccess { p ->
                        state.kernel.openProject(p)
                        state.show("项目「${p.name}」已创建，下一步：导入第一个插件")
                        state.tab = 2
                    }
                    .onFailure { state.show("创建失败：" + (it.message ?: "未知错误")) }
            },
        )
    }
    LoomButton("创建项目", BtnStyle.Primary, "plus") { creating = true }
    VSpace(Sp.x2)
    LoomButton("导入 .loomproj", BtnStyle.Secondary, "import") {
        state.startImport(com.loom.app.ImportKind.Project)
    }
    VSpace(Sp.x5)
    Row(verticalAlignment = Alignment.Top) {
        LoomIcon("info", c.faint, 15.dp)
        Spacer(Modifier.width(Sp.x2))
        Footnote("还没有插件？插件是独立文件：一个含 plugin.json 的 zip。导入后才会进入项目。")
    }
}

@Composable
private fun Recent(state: AppState, onNew: () -> Unit) {
    val c = Loom.colors
    val kernel = state.kernel
    SectionHeader("01", "最近装配") {
        Text(
            "新建",
            style = LoomType.small,
            color = c.accent,
            modifier = Modifier
                .clickable { onNew() }
                .padding(horizontal = Sp.x2, vertical = Sp.x3),
        )
    }
    VSpace(Sp.x1)
    kernel.projects().forEach { p ->
        ProjectRow(state, p)
        Hairline()
    }
    VSpace(Sp.x4)
    LoomButton("导入 .loomproj", BtnStyle.Secondary, "import") {
        state.startImport(com.loom.app.ImportKind.Project)
    }
}

@Composable
private fun ProjectRow(state: AppState, p: ProjectFs.Project) {
    val c = Loom.colors
    val kernel = state.kernel
    val isCurrent = kernel.current?.id == p.id
    val plugins = if (isCurrent) kernel.pluginCount() else kernel.fs.pluginCount(p)
    val active = if (isCurrent) kernel.count(Status.ACTIVE) else 0
    val pending = if (isCurrent) kernel.count(Status.PENDING) else 0
    val errors = if (isCurrent) kernel.count(Status.ERROR) else 0

    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (kernel.current?.id != p.id) kernel.openProject(p)
                state.tab = 1
                state.refresh()
            }
            .padding(vertical = Sp.x4)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                style = LoomType.h1.copy(fontSize = LoomType.h1.fontSize * 0.82f),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) LoomChip("当前", c.accent, c.accentSoft, "dot")
            if (pending > 0) {
                Spacer(Modifier.width(6.dp))
                LoomChip("$pending 等待", c.warn, c.warnSoft, "clock")
            }
            if (errors > 0) {
                Spacer(Modifier.width(6.dp))
                LoomChip("$errors 失败", c.danger, c.dangerSoft, "alert")
            }
        }
        VSpace(6)
        Text(
            "$plugins 插件 · $active 活跃 · ${com.loom.app.ui.Fmt.ago(p.updated)}",
            style = LoomType.mono.copy(fontSize = LoomType.micro.fontSize),
            color = c.faint,
        )
        VSpace(Sp.x3)
        Row {
            LoomButton(
                if (isCurrent) "查看项目" else "打开",
                BtnStyle.Secondary,
                "project",
                modifier = Modifier.weight(1f),
            ) {
                if (kernel.current?.id != p.id) kernel.openProject(p)
                state.tab = 1
                state.refresh()
            }
            Spacer(Modifier.width(Sp.x2))
            val canRun = isCurrent && kernel.uiScreens().isNotEmpty()
            LoomButton("运行", BtnStyle.Primary, "play", enabled = canRun, modifier = Modifier.weight(1f)) {
                if (kernel.current?.id != p.id) kernel.openProject(p)
                if (kernel.uiScreens().isEmpty()) {
                    state.show("这个项目还没有可运行的界面插件")
                } else {
                    state.runOpen = true
                }
            }
        }
    }
}

@Composable
private fun Overview(state: AppState) {
    val kernel = state.kernel
    SectionHeader("02", "内核状态")
    VSpace(Sp.x3)
    SurfaceCard {
        StatStrip(
            listOf(
                kernel.projects().size.toString() to "PROJECTS / 项目",
                kernel.pluginCount().toString() to "PLUGINS / 插件",
            )
        )
        VSpace(Sp.x4)
        Hairline()
        VSpace(Sp.x4)
        StatStrip(
            listOf(
                kernel.services.entries().size.toString() to "SERVICES / 服务",
                kernel.events.subscriberCount().toString() to "SUBS / 订阅",
            )
        )
        VSpace(Sp.x3)
        Hairline()
        VSpace(Sp.x3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Footnote("追踪记录 ${kernel.events.size()} 条 · 契约版本 v1", modifier = Modifier.weight(1f))
            Text(
                "看图谱",
                style = LoomType.small,
                color = Loom.colors.accent,
                modifier = Modifier
                    .clickable {
                        state.tab = 3
                        state.refresh()
                    }
                    .padding(horizontal = Sp.x2, vertical = Sp.x2),
            )
        }
    }
}
