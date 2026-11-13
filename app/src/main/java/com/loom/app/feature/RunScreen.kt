package com.loom.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.loom.app.AppState
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.ui.BackHeader
import com.loom.app.ui.EmptyBlock
import com.loom.app.ui.Footnote
import com.loom.app.ui.Hairline
import com.loom.app.ui.LoomIcon
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp
import org.json.JSONObject

/**
 * 运行台：唯一会出现「业务界面」的地方。
 * 界面内容全部来自 UI 层插件的 screen.lua，宿主只负责渲染与转发意图事件。
 */
@Composable
fun RunScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    val screens = kernel.uiScreens()
    var selected by remember { mutableStateOf(0) }
    if (selected >= screens.size) selected = 0

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.page)) {
        BackHeader(
            title = "运行台",
            sub = (kernel.current?.name ?: "") + " · 界面来自 UI 层插件",
            onBack = { state.runOpen = false },
        )
        Hairline()
        if (screens.isEmpty()) {
            EmptyBlock(
                icon = "grid",
                title = "没有可运行的界面",
                body = "这个项目里没有 ACTIVE 状态的 UI 层插件。去插件页启用或导入一个带 screen.lua 的插件。",
                primaryLabel = "去插件页",
                onPrimary = {
                    state.runOpen = false
                    state.tab = 2
                },
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = Sp.x3),
            horizontalArrangement = Arrangement.spacedBy(Sp.x2),
        ) {
            screens.forEachIndexed { index, lp ->
                val on = index == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(R.pill))
                        .background(if (on) Loom.colors.ink else Loom.colors.surfaceAlt)
                        .clickable { selected = index }
                        .padding(horizontal = Sp.x3, vertical = Sp.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoomIcon(
                        iconOf(lp),
                        if (on) Loom.colors.inkInverse else Loom.colors.muted,
                        15.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        titleOf(lp),
                        style = LoomType.small,
                        color = if (on) Loom.colors.inkInverse else Loom.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Hairline()

        val lp = screens[selected]
        Box(Modifier.weight(1f)) {
            when {
                lp.declScreen != null -> DeclarativeScreen(state, lp)
                lp.viewFactory != null -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> lp.viewFactory?.create(ctx) ?: android.view.View(ctx) },
                )
                lp.composeScreen != null -> ComposeScreenHost(lp)
                else -> Text(
                    "这个插件注册的界面无法渲染",
                    style = LoomType.small,
                    color = Loom.colors.danger,
                    modifier = Modifier.padding(top = Sp.x5),
                )
            }
        }
        Hairline()
        val traces = kernel.events.traceList()
        val last = traces.lastOrNull()
        Row(Modifier.fillMaxWidth().padding(vertical = Sp.x2), verticalAlignment = Alignment.CenterVertically) {
            Footnote(
                if (last == null) "还没有产生事件。点一下界面里的按钮，运行链条会开始生长。"
                else "追踪 ${traces.size} 条 · 最近事件 ${last.event} · 经过 ${last.steps.size} 步",
                modifier = Modifier.weight(1f),
            )
            Text(
                "看链条",
                style = LoomType.small,
                color = Loom.colors.accent,
                modifier = Modifier
                    .clickable {
                        state.runOpen = false
                        state.tab = 3
                    }
                    .padding(horizontal = Sp.x2, vertical = Sp.x2),
            )
        }
    }
}

@Composable
private fun DeclarativeScreen(state: AppState, lp: LoadedPlugin) {
    val kernel = state.kernel
    val screen = remember(state.revision, lp.id) {
        runCatching { kernel.renderScreen(lp) }.getOrNull()
    }
    if (screen == null) {
        Column(Modifier.fillMaxSize().padding(top = Sp.x5)) {
            Text("界面渲染失败", style = LoomType.h2, color = Loom.colors.danger)
            Spacer(Modifier.height(Sp.x2))
            Footnote("检查 ${lp.id} 的 source/screen.lua 是否 return 了一个界面表；错误原因会出现在插件页。")
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Sp.x5)
    ) {
        val sub = screen.optString("sub")
        if (sub.isNotEmpty()) {
            Text(sub, style = LoomType.eyebrow, color = Loom.colors.faint)
            Spacer(Modifier.height(Sp.x4))
        }
        val root = screen.optJSONObject("root")
        if (root != null) {
            RenderNode(root, onEmit = { event ->
                kernel.emitEvent(event, lp.id, JSONObject())
                state.refresh()
            })
        }
        Spacer(Modifier.height(Sp.x6))
        Footnote("界面由 ${lp.id} 的声明式 UI 提供。按钮只发意图事件，不直接改状态。")
    }
}

/** Compose 界面贡献点：ABI 已在装载时校验，这里直接进入插件的内容。 */
@Composable
private fun ComposeScreenHost(lp: LoadedPlugin) {
    val screen = lp.composeScreen
    Box(Modifier.fillMaxSize().background(Loom.colors.paper)) {
        if (screen != null) screen.Content()
    }
}

private fun titleOf(lp: LoadedPlugin): String = lp.screenTitle ?: lp.name

private fun iconOf(lp: LoadedPlugin): String = lp.screenIcon.ifBlank { "grid" }
