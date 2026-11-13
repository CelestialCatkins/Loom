package com.loom.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.BannerMsg
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp
import kotlinx.coroutines.delay

val TABS = listOf(
    Triple("工作台", "workbench", 0),
    Triple("项目", "project", 1),
    Triple("插件", "plug", 2),
    Triple("图谱", "graph", 3),
    Triple("设置", "settings", 4),
)

/** 底部导航：等宽小字 + 铜色指示线，五个区域始终可达。 */
@Composable
fun LoomBottomBar(current: Int, onSelect: (Int) -> Unit) {
    val c = Loom.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.paper)
            .navigationBarsPadding()
    ) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            TABS.forEach { (label, icon, index) ->
                val on = index == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) }
                        .semantics { contentDescription = if (on) "$label，当前页" else label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (on) c.accent else Color.Transparent)
                    )
                    Spacer(Modifier.height(6.dp))
                    LoomIcon(icon, if (on) c.ink else c.faint, 21.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = LoomType.small.copy(fontSize = 11.sp),
                        color = if (on) c.ink else c.faint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 内联提示：底部浮起，失败信息带原因与可选操作。 */
@Composable
fun BannerHost(banner: BannerMsg?, onDismiss: () -> Unit) {
    val c = Loom.colors
    var visible by remember(banner?.key) { mutableStateOf(banner != null) }
    LaunchedEffect(banner?.key) {
        if (banner != null) {
            visible = true
            delay(if (banner.actionLabel != null) 6000 else 3600)
            visible = false
            delay(200)
            onDismiss()
        }
    }
    Box(Modifier.fillMaxSize().padding(Sp.x4), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && banner != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
        ) {
            val msg = banner ?: return@AnimatedVisibility
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(R.r3))
                    .background(c.ink)
                    .padding(start = Sp.x4, end = if (msg.actionLabel != null) Sp.x2 else Sp.x4, top = Sp.x3, bottom = Sp.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    msg.text,
                    style = LoomType.small,
                    color = c.inkInverse,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (msg.actionLabel != null) {
                    Text(
                        msg.actionLabel,
                        style = LoomType.small,
                        color = c.accent,
                        modifier = Modifier
                            .clickable {
                                visible = false
                                msg.action?.invoke()
                            }
                            .padding(horizontal = Sp.x3, vertical = Sp.x2),
                    )
                }
            }
        }
    }
}

/** 推入页面的返回页头（运行台用）。 */
@Composable
fun BackHeader(title: String, sub: String?, onBack: () -> Unit) {
    val c = Loom.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Sp.x2), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Sp.touch)
                .clickable { onBack() }
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            LoomIcon("back", c.ink, 20.dp)
        }
        Spacer(Modifier.width(Sp.x1))
        Column(Modifier.weight(1f)) {
            Text(title, style = LoomType.h1, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub, style = LoomType.micro, color = c.muted)
            }
        }
    }
}
