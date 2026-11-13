package com.loom.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.LoomButton
import com.loom.app.ui.LoomChip
import com.loom.app.ui.Hairline
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * 声明式界面渲染器：UI 插件用 Lua 返回一棵「界面表」，宿主负责画。
 *
 * 支持节点：column / row / eyebrow / headline / text / metric / button / ghost /
 * divider / space / note / tag / kv。
 * 每次重组都会调用插件的 screen.lua 重新求值，因此界面永远显示当下状态。
 */
@Composable
fun RenderNode(node: JSONObject, modifier: Modifier = Modifier, onEmit: (String) -> Unit) {
    val c = Loom.colors
    val type = node.optString("type", "text")
    when (type) {
        "column" -> {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(node.optInt("gap", 12).dp)) {
                children(node).forEach { child -> RenderNode(child, Modifier, onEmit) }
            }
        }
        "row" -> {
            Row(
                modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(node.optInt("gap", 10).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                children(node).forEach { child ->
                    val t = child.optString("type")
                    val weighted = t == "button" || t == "ghost"
                    RenderNode(child, if (weighted) Modifier.weight(1f) else Modifier, onEmit)
                }
            }
        }
        "eyebrow" -> Text(node.optString("text"), style = LoomType.eyebrow, color = c.accent)
        "headline" -> Text(node.optString("text"), style = LoomType.h1, color = c.ink)
        "text" -> {
            val mono = node.optBoolean("mono", false)
            val muted = node.optBoolean("muted", false)
            Text(
                node.optString("text"),
                style = if (mono) LoomType.mono.copy(fontSize = 13.sp) else LoomType.body,
                color = if (muted) c.muted else c.ink,
            )
        }
        "metric" -> {
            Column(modifier.fillMaxWidth()) {
                val label = node.optString("label")
                if (label.isNotEmpty()) Text(label, style = LoomType.eyebrow, color = c.faint)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(node.opt("value")), style = LoomType.monoHuge, color = c.ink)
                    val unit = node.optString("unit")
                    if (unit.isNotEmpty()) {
                        Spacer(Modifier.width(Sp.x2))
                        Text(
                            unit,
                            style = LoomType.eyebrow,
                            color = c.faint,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
        "button" -> {
            LoomButton(
                text = node.optString("text", "执行"),
                style = BtnStyle.Primary,
                modifier = modifier.fillMaxWidth(),
                onClick = { node.optString("emit").takeIf { it.isNotEmpty() }?.let(onEmit) },
            )
        }
        "ghost" -> {
            LoomButton(
                text = node.optString("text", "执行"),
                style = BtnStyle.Secondary,
                modifier = modifier.fillMaxWidth(),
                onClick = { node.optString("emit").takeIf { it.isNotEmpty() }?.let(onEmit) },
            )
        }
        "divider" -> Hairline()
        "space" -> Spacer(Modifier.height(node.optInt("h", 12).dp))
        "note" -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(R.r2))
                    .background(c.surfaceAlt)
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
                Text(
                    node.optString("text"),
                    style = LoomType.small,
                    color = c.ink,
                    modifier = Modifier.padding(Sp.x3),
                )
            }
        }
        "tag" -> LoomChip(node.optString("text"), c.muted, c.surfaceAlt)
        "kv" -> {
            Row(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(node.optString("label"), style = LoomType.small, color = c.muted, modifier = Modifier.weight(1f))
                Text(fmt(node.opt("value")), style = LoomType.mono, color = c.ink)
            }
        }
        else -> Text(fmt(node.opt("text")), style = LoomType.body, color = c.ink)
    }
}

private fun children(node: JSONObject): List<JSONObject> {
    val arr: JSONArray = node.optJSONArray("children") ?: return emptyList()
    val out = ArrayList<JSONObject>(arr.length())
    for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.let { out.add(it) }
    }
    return out
}

fun fmt(v: Any?): String = when (v) {
    null, JSONObject.NULL -> "—"
    is Number -> {
        val d = v.toDouble()
        if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }
    is Boolean -> if (v) "true" else "false"
    is String -> v
    else -> v.toString()
}
