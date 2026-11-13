package com.loom.app.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.loom.app.kernel.EventBus
import com.loom.app.kernel.Kernel
import com.loom.app.kernel.Layer
import com.loom.app.kernel.LoadedPlugin
import com.loom.app.kernel.Status

/** 可视化用色板（由 Compose Token 转成 android.graphics 颜色，供 Canvas 与 PNG 导出共用）。 */
data class VizPalette(
    val paper: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val faint: Int,
    val hair: Int,
    val accent: Int,
    val ok: Int,
    val warn: Int,
    val danger: Int,
    val surfaceAlt: Int,
)

class GraphNode(
    val id: String,
    val label: String,
    val layer: String,
    val status: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

class GraphEdge(
    val from: GraphNode,
    val to: GraphNode,
    val label: String,
    val dashed: Boolean,
    val violation: Boolean,
)

class GraphBand(val label: String, val y: Float, val h: Float)

class GraphModel(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val bands: List<GraphBand>,
    val busLabel: String,
    val busY: Float,
    val width: Float,
    val height: Float,
)

/**
 * 关系图：全部由注册表推导，不手绘。
 * 分层：UI 层在上、逻辑层居中、能力层在下；服务依赖为实线（消费者 → 提供者），
 * 事件订阅为虚线连到底部事件总线。
 */
object GraphLayout {

    private const val NODE_W = 128f
    private const val NODE_H = 46f
    private const val GAP_X = 14f
    private const val GAP_Y = 26f
    private const val MARGIN = 20f
    private const val BAND_GAP = 78f
    private const val LABEL_COL = 74f

    fun build(kernel: Kernel): GraphModel {
        val plugins = kernel.list()
        val bands = ArrayList<GraphBand>()
        val nodes = ArrayList<GraphNode>()

        val groups = listOf(
            Triple("UI 层", Layer.UI, "#1"),
            Triple("逻辑层", Layer.LOGIC, "#2"),
            Triple("能力层", Layer.LOGIC, "#3"),
        )

        var y = 66f
        var contentWidth = 480f
        groups.forEachIndexed { gi, (label, layer, tag) ->
            val members = when (gi) {
                0 -> plugins.filter { it.manifest.layer == Layer.UI }
                1 -> plugins.filter { it.manifest.layer == Layer.LOGIC && !it.manifest.isCapability }
                else -> plugins.filter { it.manifest.isCapability }
            }
            val bandH = if (members.isEmpty()) 52f else {
                val rows = ((members.size - 1) / 3) + 1
                rows * (NODE_H + GAP_Y) - GAP_Y + 40f
            }
            bands.add(GraphBand(label, y, bandH))
            val startX = MARGIN + LABEL_COL
            val perRow = 3
            members.forEachIndexed { i, lp ->
                val col = i % perRow
                val row = i / perRow
                val x = startX + col * (NODE_W + GAP_X)
                val ny = y + 30f + row * (NODE_H + GAP_Y)
                nodes.add(
                    GraphNode(
                        id = lp.id,
                        label = lp.name,
                        layer = tag,
                        status = lp.status.code,
                        kind = lp.manifest.kind?.key ?: "ui",
                        x = x,
                        y = ny,
                        w = NODE_W,
                        h = NODE_H,
                    )
                )
                contentWidth = maxOf(contentWidth, x + NODE_W + MARGIN)
            }
            y += bandH + BAND_GAP
        }

        val busY = y - BAND_GAP + 30f
        val byId = nodes.associateBy { it.id }
        val edges = ArrayList<GraphEdge>()

        // 服务依赖：消费者 → 提供者
        plugins.forEach { lp ->
            val consumer = byId[lp.id] ?: return@forEach
            lp.manifest.inject.forEach { service ->
                val owner = kernel.services.ownerOf(service)
                val provider = owner?.let { byId[it] }
                if (provider != null && provider.id != consumer.id) {
                    val violation = lp.manifest.layer == Layer.LOGIC &&
                        plugins.firstOrNull { it.id == provider.id }?.manifest?.layer == Layer.UI
                    edges.add(GraphEdge(consumer, provider, service, false, violation))
                }
            }
        }

        // 事件订阅：订阅者 ⇢ 事件总线
        val subs = kernel.events.subscriptions()
        subs.forEach { (owner, ev) ->
            val node = byId[owner] ?: return@forEach
            edges.add(GraphEdge(node, node, ev, true, false))
        }

        val height = busY + 46f
        return GraphModel(
            nodes = nodes,
            edges = edges,
            bands = bands,
            busLabel = "事件总线 · ${subs.map { it.second }.distinct().size} 个事件 / ${subs.size} 条订阅",
            busY = busY,
            width = maxOf(contentWidth, 420f),
            height = height,
        )
    }

}

/** 关系图渲染：Compose Canvas 与 PNG 导出复用同一段绘制代码。 */
class GraphRenderer(private val pal: VizPalette) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(7f, 6f), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = 14f
    }
    private val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        textSize = 10.5f
    }
    private val textMono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        textSize = 11.5f
    }

    private fun statusColor(code: String): Int = when (code) {
        "ACTIVE" -> pal.ok
        "PENDING" -> pal.warn
        "ERROR" -> pal.danger
        else -> pal.muted
    }

    fun draw(canvas: Canvas, model: GraphModel) {
        canvas.drawColor(pal.paper)

        // 分层带
        model.bands.forEach { band ->
            fill.color = pal.surfaceAlt
            fill.alpha = 40
            canvas.drawRoundRect(
                MARGIN_LEFT, band.y, model.width - 16f, band.y + band.h, 14f, 14f, fill
            )
            fill.alpha = 255
            textSmall.color = pal.faint
            canvas.drawText(band.label, 18f, band.y + 20f, textSmall)
            stroke.color = pal.hair
            stroke.strokeWidth = 1f
            canvas.drawLine(18f, band.y + band.h, model.width - 16f, band.y + band.h, stroke)
        }

        // 事件总线
        fill.color = pal.accent
        fill.alpha = 26
        canvas.drawRoundRect(16f, model.busY - 22f, model.width - 16f, model.busY + 22f, 12f, 12f, fill)
        fill.alpha = 255
        textSmall.color = pal.accent
        canvas.drawText(model.busLabel, 26f, model.busY + 4f, textSmall)

        // 边
        model.edges.forEach { e ->
            val paint = if (e.dashed) dash else stroke
            paint.color = when {
                e.violation -> pal.danger
                e.dashed -> pal.faint
                else -> pal.muted
            }
            if (e.dashed) {
                // 订阅：从节点底部到底部总线
                val x = e.from.x + e.from.w / 2f
                canvas.drawLine(x, e.from.y + e.from.h, x, model.busY - 22f, paint)
                textSmall.color = pal.faint
                canvas.drawText(e.label, x + 5f, e.from.y + e.from.h + 16f, textSmall)
            } else {
                val sx = e.from.x + e.from.w / 2f
                val sy = e.from.y
                val tx = e.to.x + e.to.w / 2f
                val ty = e.to.y + e.to.h
                val midY = (sy + ty) / 2f
                val path = Path()
                path.moveTo(sx, sy)
                path.cubicTo(sx, midY, tx, midY, tx, ty)
                canvas.drawPath(path, paint)
                // 箭头
                val head = Path()
                head.moveTo(tx, ty + 8f)
                head.lineTo(tx - 4.5f, ty + 1f)
                head.lineTo(tx + 4.5f, ty + 1f)
                head.close()
                fill.color = paint.color
                canvas.drawPath(head, fill)
                textSmall.color = pal.muted
                val lw = textSmall.measureText(e.label)
                canvas.drawText(e.label, (sx + tx) / 2f - lw / 2f, midY - 4f, textSmall)
            }
        }

        // 节点
        model.nodes.forEach { n ->
            val r = 10f
            fill.color = pal.surface
            canvas.drawRoundRect(n.x, n.y, n.x + n.w, n.y + n.h, r, r, fill)
            stroke.color = if (n.status == "ERROR") pal.danger else pal.hair
            stroke.strokeWidth = if (n.status == "ERROR") 2f else 1.2f
            canvas.drawRoundRect(n.x, n.y, n.x + n.w, n.y + n.h, r, r, stroke)

            fill.color = statusColor(n.status)
            canvas.drawCircle(n.x + 14f, n.y + n.h / 2f, 4f, fill)

            text.color = pal.ink
            val label = ellipsize(n.label, text, n.w - 40f)
            canvas.drawText(label, n.x + 26f, n.y + n.h / 2f + 5f, text)

            textSmall.color = pal.faint
            val sub = ellipsize(n.id, textSmall, n.w - 40f)
            canvas.drawText(sub, n.x + 26f, n.y + n.h - 8f, textSmall)
        }
    }

    private fun ellipsize(s: String, paint: Paint, max: Float): String {
        if (paint.measureText(s) <= max) return s
        var v = s
        while (v.length > 1 && paint.measureText("$v…") > max) v = v.dropLast(1)
        return "$v…"
    }

    fun mermaid(model: GraphModel): String {
        val sb = StringBuilder()
        sb.append("graph TD\n")
        sb.append("  classDef ui fill:#F1DDD0,stroke:#B4512A,color:#1A1B17;\n")
        sb.append("  classDef logic fill:#DCEAE1,stroke:#2C6B49,color:#1A1B17;\n")
        sb.append("  classDef cap fill:#E8E4D8,stroke:#6C6A60,color:#1A1B17;\n")
        sb.append("  classDef bad fill:#F3DAD4,stroke:#A32B1E,color:#1A1B17;\n")
        model.nodes.forEach { n ->
            val id = n.id.replace(".", "_")
            val cls = when {
                n.status == "ERROR" -> "bad"
                n.layer == "#1" -> "ui"
                n.layer == "#3" -> "cap"
                else -> "logic"
            }
            sb.append("  $id[\"${n.label}\\n${n.id}\\n${n.status}\"]:::$cls\n")
        }
        model.edges.forEach { e ->
            if (e.dashed) {
                sb.append("  ${e.from.id.replace(".", "_")} -. \"${e.label}\" .-> bus[(事件总线)]\n")
            } else {
                sb.append(
                    "  ${e.from.id.replace(".", "_")} -->|\"${e.label}\"| ${e.to.id.replace(".", "_")}\n"
                )
            }
        }
        return sb.toString()
    }

    fun dot(model: GraphModel): String {
        val sb = StringBuilder()
        sb.append("digraph loom {\n  rankdir=TB;\n  node [shape=box, style=\"rounded,filled\", fontname=\"sans-serif\", fontsize=11];\n")
        model.nodes.forEach { n ->
            val color = when {
                n.status == "ERROR" -> "#F3DAD4"
                n.layer == "#1" -> "#F1DDD0"
                n.layer == "#3" -> "#E8E4D8"
                else -> "#DCEAE1"
            }
            sb.append("  \"${n.id}\" [label=\"${n.label}\\n${n.status}\", fillcolor=\"$color\"];\n")
        }
        model.edges.forEach { e ->
            if (e.dashed) {
                sb.append("  \"${e.from.id}\" -> \"eventbus_${e.label.replace(".", "_")}\" [style=dashed, label=\"${e.label}\"];\n")
            } else {
                sb.append("  \"${e.from.id}\" -> \"${e.to.id}\" [label=\"${e.label}\"];\n")
            }
        }
        sb.append("}\n")
        return sb.toString()
    }

    companion object {
        const val MARGIN_LEFT = 12f
    }
}

// ------------------------------------------------------------------ 运行链条

class TraceRow(
    val fromLane: Int,
    val toLane: Int,
    val label: String,
    val kind: String,
    val traceEvent: String,
    val error: Boolean,
)

class TraceModel(
    val lanes: List<String>,
    val rows: List<TraceRow>,
    val laneOffsets: List<Float>,
    val width: Float,
    val height: Float,
)

object TraceLayout {
    private const val LANE_W = 132f
    private const val ROW_H = 30f
    private const val HEADER = 46f
    private const val LEFT = 12f

    fun build(events: EventBus): TraceModel {
        val traces = events.traceList()
        val lanes = LinkedHashMap<String, Int>()
        fun lane(name: String): Int = lanes.getOrPut(name) { lanes.size }
        lane("bus")
        traces.forEach { t ->
            lane(t.from)
            t.steps.forEach {
                lane(it.from)
                lane(it.to)
            }
        }
        val rows = ArrayList<TraceRow>()
        traces.forEach { t ->
            t.steps.forEach { s ->
                if (s.kind == "emit") {
                    rows.add(TraceRow(lane(s.from), lane(s.to), s.label, s.kind, t.event, t.error))
                } else {
                    rows.add(TraceRow(lane(s.from), lane(s.to), s.label, s.kind, t.event, t.error))
                }
            }
        }
        val laneList = lanes.keys.toList()
        val offsets = laneList.indices.map { LEFT + it * LANE_W }
        return TraceModel(
            lanes = laneList,
            rows = rows,
            laneOffsets = offsets,
            width = LEFT + laneList.size * LANE_W + 20f,
            height = HEADER + rows.size * ROW_H + 30f,
        )
    }
}

class TraceRenderer(private val pal: VizPalette) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 8f), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        textSize = 11f
    }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 11.5f
    }

    fun draw(canvas: Canvas, model: TraceModel) {
        canvas.drawColor(pal.paper)
        // 生命线
        model.lanes.forEachIndexed { i, name ->
            val x = model.laneOffsets[i]
            dash.color = pal.hair
            canvas.drawLine(x, 46f, x, model.height - 10f, dash)
            fill.color = pal.surfaceAlt
            canvas.drawRoundRect(x - 46f, 8f, x + 46f, 38f, 8f, 8f, fill)
            text.color = pal.ink
            val label = ellipsize(name, text, 88f)
            canvas.drawText(label, x - text.measureText(label) / 2f, 27f, text)
        }
        // 行
        model.rows.forEachIndexed { i, row ->
            val y = 46f + i * 30f
            val x1 = model.laneOffsets[row.fromLane]
            val x2 = model.laneOffsets[row.toLane]
            stroke.color = when {
                row.error -> pal.danger
                row.kind == "emit" -> pal.accent
                row.kind == "call" -> pal.ok
                else -> pal.muted
            }
            stroke.strokeWidth = 1.6f
            if (x1 != x2) {
                canvas.drawLine(x1, y, x2, y, stroke)
                val dir = if (x2 > x1) 1f else -1f
                val p = Path()
                p.moveTo(x2, y)
                p.lineTo(x2 - 7f * dir, y - 4f)
                p.lineTo(x2 - 7f * dir, y + 4f)
                p.close()
                fill.color = stroke.color
                canvas.drawPath(p, fill)
                text.color = pal.muted
                val lw = text.measureText(row.label)
                canvas.drawText(row.label, minOf(x1, x2) + (kotlin.math.abs(x2 - x1) - lw) / 2f, y - 5f, text)
            } else {
                fill.color = pal.surfaceAlt
                canvas.drawCircle(x1, y, 3f, fill)
                text.color = pal.muted
                canvas.drawText(row.label, x1 + 8f, y + 4f, text)
            }
        }
    }

    private fun ellipsize(s: String, paint: Paint, max: Float): String {
        if (paint.measureText(s) <= max) return s
        var v = s
        while (v.length > 1 && paint.measureText("$v…") > max) v = v.dropLast(1)
        return "$v…"
    }
}

/** 追踪日志 JSON（结构化导出）。 */
object TraceJson {
    fun of(events: EventBus): String {
        val arr = org.json.JSONArray()
        events.traceList().forEach { arr.put(it.toJson()) }
        return org.json.JSONObject().put("traces", arr).toString(2)
    }
}
