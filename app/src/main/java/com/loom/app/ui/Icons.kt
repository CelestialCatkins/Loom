package com.loom.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一图标系统：24×24 网格上的线稿路径，全部由代码绘制。
 * 不使用 Emoji、不引外部图标库，也没有位图资源 —— 任何功能图标都必须来自这里。
 */
object IconPaths {

    private val cache = HashMap<String, Path>()

    private fun Path.rr(l: Float, t: Float, r: Float, b: Float, rad: Float): Path {
        addRoundRect(
            RoundRect(
                rect = Rect(l, t, r, b),
                topLeft = CornerRadius(rad),
                topRight = CornerRadius(rad),
                bottomRight = CornerRadius(rad),
                bottomLeft = CornerRadius(rad),
            )
        )
        return this
    }

    private fun Path.circle(cx: Float, cy: Float, r: Float): Path {
        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        return this
    }

    fun of(name: String): Path = cache.getOrPut(name) {
        val p = Path()
        when (name) {
            "workbench", "grid" -> {
                p.rr(3.5f, 3.5f, 10.5f, 10.5f, 1.6f)
                p.rr(13.5f, 3.5f, 20.5f, 10.5f, 1.6f)
                p.rr(3.5f, 13.5f, 10.5f, 20.5f, 1.6f)
                p.rr(13.5f, 13.5f, 20.5f, 20.5f, 1.6f)
            }
            "project", "stack" -> {
                p.rr(5f, 3f, 19f, 9f, 2f)
                p.moveTo(4f, 13f); p.lineTo(20f, 13f)
                p.moveTo(4f, 18.5f); p.lineTo(20f, 18.5f)
            }
            "plug" -> {
                p.rr(8.5f, 7.5f, 15.5f, 16.5f, 2.2f)
                p.moveTo(10.5f, 3.5f); p.lineTo(10.5f, 7.5f)
                p.moveTo(13.5f, 3.5f); p.lineTo(13.5f, 7.5f)
                p.moveTo(12f, 16.5f); p.lineTo(12f, 21f)
            }
            "graph" -> {
                p.circle(6f, 6.5f, 2.6f)
                p.circle(18f, 7.5f, 2.6f)
                p.circle(12f, 17.5f, 2.6f)
                p.moveTo(8.4f, 7.3f); p.lineTo(10.2f, 15.3f)
                p.moveTo(15.8f, 8f); p.lineTo(13.8f, 15.5f)
                p.moveTo(8.6f, 6.8f); p.lineTo(15.4f, 7.3f)
            }
            "settings", "sliders" -> {
                p.moveTo(3f, 6f); p.lineTo(21f, 6f)
                p.moveTo(3f, 12f); p.lineTo(21f, 12f)
                p.moveTo(3f, 18f); p.lineTo(21f, 18f)
                p.circle(8.5f, 6f, 2.3f)
                p.circle(15.5f, 12f, 2.3f)
                p.circle(7f, 18f, 2.3f)
            }
            "plus" -> {
                p.moveTo(12f, 4.5f); p.lineTo(12f, 19.5f)
                p.moveTo(4.5f, 12f); p.lineTo(19.5f, 12f)
            }
            "import" -> {
                p.moveTo(12f, 3.5f); p.lineTo(12f, 14f)
                p.moveTo(7.5f, 9.8f); p.lineTo(12f, 14.2f)
                p.moveTo(16.5f, 9.8f); p.lineTo(12f, 14.2f)
                p.moveTo(4f, 16.5f); p.lineTo(4f, 20f)
                p.lineTo(20f, 20f)
                p.lineTo(20f, 16.5f)
            }
            "export" -> {
                p.moveTo(12f, 14.5f); p.lineTo(12f, 3.8f)
                p.moveTo(7.5f, 8.2f); p.lineTo(12f, 3.8f)
                p.moveTo(16.5f, 8.2f); p.lineTo(12f, 3.8f)
                p.moveTo(4f, 16.5f); p.lineTo(4f, 20f)
                p.lineTo(20f, 20f)
                p.lineTo(20f, 16.5f)
            }
            "trash" -> {
                p.moveTo(3.5f, 7f); p.lineTo(20.5f, 7f)
                p.moveTo(7f, 7f); p.lineTo(7.8f, 20.5f)
                p.lineTo(16.2f, 20.5f)
                p.lineTo(17f, 7f)
                p.moveTo(9.5f, 4f); p.lineTo(14.5f, 4f)
                p.moveTo(10.5f, 10.5f); p.lineTo(10.8f, 17f)
                p.moveTo(13.5f, 10.5f); p.lineTo(13.2f, 17f)
            }
            "back" -> {
                p.moveTo(15f, 4.5f); p.lineTo(7.5f, 12f); p.lineTo(15f, 19.5f)
            }
            "chevron" -> {
                p.moveTo(9.5f, 4.5f); p.lineTo(17f, 12f); p.lineTo(9.5f, 19.5f)
            }
            "chevronDown" -> {
                p.moveTo(4.5f, 9.5f); p.lineTo(12f, 17f); p.lineTo(19.5f, 9.5f)
            }
            "check" -> {
                p.moveTo(4.5f, 12.5f); p.lineTo(9.5f, 17.5f); p.lineTo(19.5f, 6f)
            }
            "clock" -> {
                p.circle(12f, 12f, 8f)
                p.moveTo(12f, 7.2f); p.lineTo(12f, 12.3f); p.lineTo(16f, 13.8f)
            }
            "alert" -> {
                p.moveTo(12f, 3.5f); p.lineTo(21f, 20f); p.lineTo(3f, 20f); p.close()
                p.moveTo(12f, 9f); p.lineTo(12f, 14f)
                p.moveTo(12f, 16.8f); p.lineTo(12f, 17.2f)
            }
            "pause" -> {
                p.moveTo(9f, 5.5f); p.lineTo(9f, 18.5f)
                p.moveTo(15f, 5.5f); p.lineTo(15f, 18.5f)
            }
            "code" -> {
                p.moveTo(9f, 6.5f); p.lineTo(3.5f, 12f); p.lineTo(9f, 17.5f)
                p.moveTo(15f, 6.5f); p.lineTo(20.5f, 12f); p.lineTo(15f, 17.5f)
            }
            "image" -> {
                p.rr(3f, 4.5f, 21f, 19.5f, 2f)
                p.moveTo(4.5f, 17f); p.lineTo(9.5f, 11f); p.lineTo(14f, 16f)
                p.lineTo(17f, 12.5f); p.lineTo(20.5f, 17f)
                p.circle(15.5f, 8.5f, 1.4f)
            }
            "close" -> {
                p.moveTo(5.5f, 5.5f); p.lineTo(18.5f, 18.5f)
                p.moveTo(18.5f, 5.5f); p.lineTo(5.5f, 18.5f)
            }
            "shield" -> {
                p.moveTo(12f, 3f); p.lineTo(20f, 6.5f); p.lineTo(18.8f, 15f)
                p.lineTo(12f, 21f); p.lineTo(5.2f, 15f); p.lineTo(4f, 6.5f); p.close()
            }
            "play" -> {
                p.moveTo(7.5f, 4.5f); p.lineTo(19f, 12f); p.lineTo(7.5f, 19.5f); p.close()
            }
            "save" -> {
                p.moveTo(12f, 3.5f); p.lineTo(12f, 13.5f)
                p.moveTo(7.5f, 9.2f); p.lineTo(12f, 13.7f)
                p.moveTo(16.5f, 9.2f); p.lineTo(12f, 13.7f)
                p.moveTo(5f, 20f); p.lineTo(19f, 20f)
            }
            "info" -> {
                p.circle(12f, 12f, 8.5f)
                p.moveTo(12f, 11f); p.lineTo(12f, 16.5f)
                p.moveTo(12f, 7.6f); p.lineTo(12f, 8f)
            }
            "power" -> {
                p.moveTo(12f, 3f); p.lineTo(12f, 10f)
                p.circle(12f, 13.2f, 8.4f)
            }
            "search" -> {
                p.circle(10.5f, 10.5f, 6.2f)
                p.moveTo(15.2f, 15.2f); p.lineTo(20.5f, 20.5f)
            }
            "weave", "logo" -> {
                p.moveTo(6.5f, 3.5f); p.lineTo(6.5f, 20.5f)
                p.moveTo(12f, 3.5f); p.lineTo(12f, 20.5f)
                p.moveTo(17.5f, 3.5f); p.lineTo(17.5f, 20.5f)
                p.moveTo(3.5f, 6.5f); p.lineTo(20.5f, 6.5f)
                p.moveTo(3.5f, 12f); p.lineTo(20.5f, 12f)
                p.moveTo(3.5f, 17.5f); p.lineTo(20.5f, 17.5f)
            }
            "refresh" -> {
                p.circle(12f, 12f, 7.4f)
                p.moveTo(12f, 4.6f); p.lineTo(12f, 8.2f)
                p.moveTo(18.2f, 8.4f); p.lineTo(15.4f, 9.6f)
            }
            "note" -> {
                p.rr(4f, 3.5f, 20f, 20.5f, 2f)
                p.moveTo(7.5f, 8f); p.lineTo(16.5f, 8f)
                p.moveTo(7.5f, 12f); p.lineTo(16.5f, 12f)
                p.moveTo(7.5f, 16f); p.lineTo(13f, 16f)
            }
            "dot" -> p.circle(12f, 12f, 3.2f)
            else -> p.circle(12f, 12f, 8f)
        }
        p
    }
}

/** 图标组件：统一线宽与圆头线帽，跟随语义色。 */
@Composable
fun LoomIcon(
    name: String,
    tint: Color,
    size: Dp = 20.dp,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    val m = if (description != null) modifier.semantics { contentDescription = description } else modifier
    Canvas(m.size(size)) {
        val s = this.size.minDimension / 24f
        val dx = (this.size.width - 24f * s) / 2f
        val dy = (this.size.height - 24f * s) / 2f
        translate(dx, dy) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(
                    path = IconPaths.of(name),
                    color = tint,
                    style = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** 状态点：实心小圆（永远与文字同时出现，不单独承载语义）。 */
@Composable
fun Dot(color: Color, diameter: Dp = 6.dp) {
    Canvas(Modifier.size(diameter)) {
        drawCircle(color = color, radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f))
    }
}
