package com.loom.app.feature

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.loom.app.AppState
import com.loom.app.ExportRequest
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.EmptyBlock
import com.loom.app.ui.Footnote
import com.loom.app.ui.LoomButton
import com.loom.app.ui.ScreenHeader
import com.loom.app.ui.Segmented
import com.loom.app.ui.VSpace
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp
import com.loom.app.viz.GraphLayout
import com.loom.app.viz.GraphModel
import com.loom.app.viz.GraphRenderer
import com.loom.app.viz.TraceJson
import com.loom.app.viz.TraceLayout
import com.loom.app.viz.TraceModel
import com.loom.app.viz.TraceRenderer
import com.loom.app.viz.VizPalette
import java.io.ByteArrayOutputStream

@Composable
private fun loomPalette(): VizPalette {
    val c = Loom.colors
    return VizPalette(
        paper = c.paper.toArgb(),
        surface = c.surface.toArgb(),
        ink = c.ink.toArgb(),
        muted = c.muted.toArgb(),
        faint = c.faint.toArgb(),
        hair = c.hairline.toArgb(),
        accent = c.accent.toArgb(),
        ok = c.ok.toArgb(),
        warn = c.warn.toArgb(),
        danger = c.danger.toArgb(),
        surfaceAlt = c.surfaceAlt.toArgb(),
    )
}

/** 区域四：可视化面板（关系图 / 运行链条），数据全部来自注册表与事件追踪。 */
@Composable
fun GraphScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    var mode by remember { mutableStateOf(0) }
    val density = LocalDensity.current.density

    if (kernel.current == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Sp.page, vertical = Sp.x3)
        ) {
            ScreenHeader("GRAPH / 图谱", "可视化面板", "关系图与运行链条都由内核注册表与事件追踪自动生成。")
            VSpace(24)
            EmptyBlock(
                icon = "graph",
                title = "还没有打开的项目",
                body = "图谱属于项目：先打开一个装过插件的项目。",
                primaryLabel = "去工作台",
                onPrimary = {
                    state.tab = 0
                    state.refresh()
                },
            )
        }
        return
    }

    val pal = loomPalette()
    val graphModel = remember(state.revision) { GraphLayout.build(kernel) }
    val traceModel = remember(state.revision) { TraceLayout.build(kernel.events) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Sp.page)
            .padding(top = Sp.x3, bottom = Sp.x4)
    ) {
        ScreenHeader(
            eyebrow = "GRAPH / 图谱 · ${kernel.current?.name ?: ""}",
            title = "可视化面板",
            sub = if (mode == 0)
                "节点=插件，实线=服务依赖，虚线=事件订阅。数据来自注册表，不手绘。"
            else "一次事件从发出到所有下游订阅与服务调用的完整因果链。",
        )
        VSpace(Sp.x4)
        Segmented(listOf("关系图", "运行链条"), mode, { mode = it })
        VSpace(Sp.x3)

        if (mode == 0) {
            GraphCanvas(graphModel, pal, Modifier.weight(1f))
            VSpace(Sp.x3)
            GraphLegend(graphModel)
            VSpace(Sp.x3)
            Row {
                ExportButton("PNG", "image") {
                    exportBitmap(state, "loom-graph.png", graphModel.width, graphModel.height, density) { cv ->
                        GraphRenderer(pal).draw(cv, graphModel)
                    }
                }
                Spacer(Modifier.width(Sp.x2))
                ExportButton("Mermaid", "code") {
                    state.startExport(
                        ExportRequest("text/plain", "loom-graph.mmd") { out ->
                            out.write(GraphRenderer(pal).mermaid(graphModel).toByteArray())
                        }
                    )
                }
                Spacer(Modifier.width(Sp.x2))
                ExportButton("DOT", "code") {
                    state.startExport(
                        ExportRequest("text/plain", "loom-graph.dot") { out ->
                            out.write(GraphRenderer(pal).dot(graphModel).toByteArray())
                        }
                    )
                }
            }
        } else {
            if (kernel.events.size() == 0) {
                EmptyBlock(
                    icon = "graph",
                    title = if (kernel.events.tracing) "还没有追踪记录" else "追踪已关闭",
                    body = if (kernel.events.tracing)
                        "去运行台点几下插件按钮，事件会在这里长成时序图。"
                    else "在项目配置里打开「运行追踪」，之后的事件才会被记录。",
                    primaryLabel = "去运行台",
                    onPrimary = { state.runOpen = true },
                )
            } else {
                TraceCanvas(traceModel, pal, Modifier.weight(1f))
                VSpace(Sp.x3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Footnote(
                        "${kernel.events.size()} 条追踪 · 缓冲上限 ${kernel.events.limit} 条",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "清空日志",
                        style = LoomType.small,
                        color = Loom.colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(R.r2))
                            .background(Loom.colors.surfaceAlt)
                            .clickable {
                                kernel.events.clear()
                                state.refresh()
                            }
                            .padding(horizontal = Sp.x3, vertical = Sp.x2),
                    )
                }
                VSpace(Sp.x3)
                Row {
                    ExportButton("PNG", "image") {
                        exportBitmap(state, "loom-trace.png", traceModel.width, traceModel.height, density) { cv ->
                            TraceRenderer(pal).draw(cv, traceModel)
                        }
                    }
                    Spacer(Modifier.width(Sp.x2))
                    ExportButton("JSON", "code") {
                        state.startExport(
                            ExportRequest("application/json", "loom-trace.json") { out ->
                                out.write(TraceJson.of(kernel.events).toByteArray())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphCanvas(model: GraphModel, pal: VizPalette, modifier: Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    var initialised by remember(model) { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.r4))
            .background(Loom.colors.surface)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewW = constraints.maxWidth.toFloat()
            LaunchedEffect(model) {
                if (!initialised && viewW > 0f) {
                    scale = (viewW / (model.width * density)).coerceIn(0.45f, 1.4f)
                    initialised = true
                }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.35f, 4f)
                            offset += pan
                        }
                    }
            ) {
                drawIntoCanvas { canvas ->
                    val cv = canvas.nativeCanvas
                    cv.save()
                    cv.translate(offset.x, offset.y)
                    cv.scale(density * scale, density * scale)
                    GraphRenderer(pal).draw(cv, model)
                    cv.restore()
                }
            }
        }
        if (model.nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这个项目还没有插件，关系图是空的。", style = LoomType.small, color = Loom.colors.faint)
            }
        }
    }
}

@Composable
private fun TraceCanvas(model: TraceModel, pal: VizPalette, modifier: Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    var initialised by remember(model) { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.r4))
            .background(Loom.colors.surface)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewW = constraints.maxWidth.toFloat()
            LaunchedEffect(model) {
                if (!initialised && viewW > 0f) {
                    scale = (viewW / (model.width * density)).coerceIn(0.45f, 1.4f)
                    initialised = true
                }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.35f, 4f)
                            offset += pan
                        }
                    }
            ) {
                drawIntoCanvas { canvas ->
                    val cv = canvas.nativeCanvas
                    cv.save()
                    cv.translate(offset.x, offset.y)
                    cv.scale(density * scale, density * scale)
                    TraceRenderer(pal).draw(cv, model)
                    cv.restore()
                }
            }
        }
    }
}

@Composable
private fun GraphLegend(model: GraphModel) {
    val c = Loom.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(c.accent, "UI 层")
            Spacer(Modifier.width(Sp.x3))
            LegendDot(c.ok, "逻辑层")
            Spacer(Modifier.width(Sp.x3))
            LegendDot(c.muted, "能力")
            Spacer(Modifier.width(Sp.x3))
            LegendDot(c.danger, "违规/失败")
        }
        VSpace(Sp.x2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(1.dp).background(c.muted))
            Spacer(Modifier.width(6.dp))
            Footnote("服务依赖（消费者 → 提供者）")
        }
        VSpace(4)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(1.dp).background(c.faint))
            Spacer(Modifier.width(6.dp))
            Footnote("事件订阅 → 总线，可双指缩放、拖动")
        }
        VSpace(Sp.x2)
        Footnote("${model.nodes.size} 个节点 · ${model.edges.size} 条边")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(R.pill)).background(color))
        Spacer(Modifier.width(5.dp))
        Footnote(label)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ExportButton(text: String, icon: String, onClick: () -> Unit) {
    LoomButton(text, BtnStyle.Secondary, icon, onClick = onClick, modifier = Modifier.weight(1f))
}

/** 把可视化渲染成 Bitmap 再交给 SAF 保存（2 倍密度，导出图可读）。 */
private fun exportBitmap(
    state: AppState,
    fileName: String,
    modelW: Float,
    modelH: Float,
    density: Float,
    draw: (android.graphics.Canvas) -> Unit,
) {
    state.startExport(
        ExportRequest("image/png", fileName) { out ->
            val sf = 2f * density
            val w = (modelW * sf).toInt().coerceIn(320, 8000)
            val h = (modelH * sf).toInt().coerceIn(240, 12000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.scale(sf, sf)
            draw(cv)
            val bytes = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            out.write(bytes.toByteArray())
        }
    )
}
