package com.loom.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp

/** 1px 发线：用细线代替边框与卡片堆叠。 */
@Composable
fun Hairline(color: Color = Loom.colors.hairline, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 编辑式页头：等宽小标 + 衬线大标题 + 一句说明 + 右侧动作。 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    sub: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Loom.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = eyebrow,
                style = LoomType.eyebrow,
                color = c.accent,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        Spacer(Modifier.height(Sp.x2))
        Text(title, style = LoomType.display, color = c.ink)
        if (sub != null) {
            Spacer(Modifier.height(Sp.x2))
            Text(sub, style = LoomType.small, color = c.muted)
        }
    }
}

/** 分组标号：01 / 装配清单 —— 编号用铜色等宽体。 */
@Composable
fun SectionHeader(
    index: String?,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Loom.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) {
            Text(index, style = LoomType.eyebrow, color = c.accent)
            Spacer(Modifier.width(Sp.x3))
        }
        Text(title, style = LoomType.h2, color = c.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Footnote(text: String, color: Color = Loom.colors.faint, modifier: Modifier = Modifier) {
    Text(text, style = LoomType.micro, color = color, modifier = modifier)
}

enum class BtnStyle { Primary, Secondary, Ghost, Danger, DangerOutline }

/** 按钮：最小 52dp 高、圆角 12、按下有涟漪（clickable 自带 indication）。 */
@Composable
fun LoomButton(
    text: String,
    style: BtnStyle = BtnStyle.Primary,
    icon: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Loom.colors
    val bg = when (style) {
        BtnStyle.Primary -> c.ink
        BtnStyle.Secondary -> c.surface
        BtnStyle.Ghost -> Color.Transparent
        BtnStyle.Danger -> c.danger
        BtnStyle.DangerOutline -> Color.Transparent
    }
    val fg = when (style) {
        BtnStyle.Primary, BtnStyle.Danger -> c.inkInverse
        BtnStyle.Secondary, BtnStyle.Ghost -> c.ink
        BtnStyle.DangerOutline -> c.danger
    }
    val borderColor = when (style) {
        BtnStyle.Secondary -> c.hairStrong
        BtnStyle.DangerOutline -> c.danger
        BtnStyle.Ghost -> Color.Transparent
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(R.r3)
    var m = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(shape)
        .background(bg)
    if (borderColor != Color.Transparent) m = m.border(1.dp, borderColor, shape)
    Row(
        modifier = m
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = Sp.x4, vertical = Sp.x3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LoomIcon(icon, fg, 18.dp)
            Spacer(Modifier.width(Sp.x2))
        }
        Text(
            text = text,
            style = LoomType.title,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图标按钮：48dp 触控盒。 */
@Composable
fun IconBtn(
    icon: String,
    description: String,
    tint: Color = Loom.colors.ink,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(Sp.touch)
            .clip(RoundedCornerShape(R.pill))
            .clickable { onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        LoomIcon(icon, tint, 21.dp)
    }
}

@Composable
fun LoomChip(
    text: String,
    fg: Color,
    bg: Color,
    icon: String? = null,
    border: Color? = null,
) {
    val shape = RoundedCornerShape(R.pill)
    var m = Modifier.clip(shape).background(bg)
    if (border != null) m = m.border(1.dp, border, shape)
    Row(
        m.padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LoomIcon(icon, fg, 13.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = LoomType.mono.copy(fontSize = 11.sp), color = fg)
    }
}

/** 插件四态：颜色 + 图标 + 文字三重编码（不只靠颜色）。 */
@Composable
fun StatusChip(code: String) {
    val icon = when (code) {
        "ACTIVE" -> "check"
        "PENDING" -> "clock"
        "ERROR" -> "alert"
        else -> "pause"
    }
    LoomChip(code, Loom.statusColor(code), Loom.statusSoft(code), icon)
}

@Composable
fun TagChip(text: String) {
    LoomChip(text, Loom.colors.muted, Loom.colors.surfaceAlt)
}

/** 数据条：等宽数字 + 竖细线。 */
@Composable
fun StatStrip(items: List<Pair<String, String>>) {
    val c = Loom.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, (value, label) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = LoomType.monoLarge, color = c.ink, maxLines = 1)
                Spacer(Modifier.height(Sp.x1))
                Text(label, style = LoomType.eyebrow, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (index != items.lastIndex) {
                Box(
                    Modifier
                        .padding(horizontal = Sp.x2)
                        .width(1.dp)
                        .height(30.dp)
                        .background(c.hairline)
                )
            }
        }
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable () -> Unit,
) {
    val c = Loom.colors
    Column(
        modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(R.r4))
            .clip(RoundedCornerShape(R.r4))
            .background(c.surface)
            .padding(padding.dp)
    ) { content() }
}

/** 标准行：最小 64dp，整行可点。 */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    mono: String? = null,
    leadingIcon: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Loom.colors
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
    Row(
        modifier = (if (onClick != null) base.clickable { onClick() } else base)
            .padding(vertical = Sp.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            LoomIcon(leadingIcon, c.muted, 20.dp)
            Spacer(Modifier.width(Sp.x3))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LoomType.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = LoomType.small, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (mono != null) {
                Spacer(Modifier.height(Sp.x1))
                Text(mono, style = LoomType.mono.copy(fontSize = 11.sp), color = c.faint)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Sp.x3))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(Sp.x2))
            LoomIcon("chevron", c.faint, 16.dp)
        }
    }
}

/** 空状态：必须有引导，不能白屏。 */
@Composable
fun EmptyBlock(
    icon: String,
    title: String,
    body: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: (@Composable () -> Unit)? = null,
) {
    val c = Loom.colors
    Column(
        Modifier.fillMaxWidth().padding(top = Sp.x6, bottom = Sp.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(R.pill))
                .background(c.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            LoomIcon(icon, c.accent, 26.dp)
        }
        Spacer(Modifier.height(Sp.x5))
        Text(title, style = LoomType.h1, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Sp.x3))
        Text(
            body,
            style = LoomType.body,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(Sp.x6))
            LoomButton(primaryLabel, BtnStyle.Primary, "plus", onClick = onPrimary)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(Sp.x2))
            LoomButton(secondaryLabel, BtnStyle.Secondary, "import", onClick = onSecondary)
        }
        if (tertiary != null) {
            Spacer(Modifier.height(Sp.x4))
            tertiary()
        }
    }
}

/** 表单：标签在上（不能只用 Placeholder），错误提示贴着输入框。 */
@Composable
fun LoomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    error: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Loom.colors
    Column(modifier.fillMaxWidth()) {
        Text(label, style = LoomType.eyebrow, color = c.muted)
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(R.r2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LoomType.title.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(c.surfaceAlt)
                        .border(if (error != null) 1.5.dp else 1.dp, if (error != null) c.danger else c.hairStrong, shape)
                        .padding(horizontal = Sp.x3, vertical = Sp.x3),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(hint, style = LoomType.title, color = c.faint)
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(5.dp))
            Text(error, style = LoomType.micro, color = c.danger)
        }
    }
}

/** 分段控件：容器整体 48dp，避免触控目标不达标。 */
@Composable
fun Segmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Loom.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.r2))
            .background(c.surfaceAlt)
            .padding(2.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(R.r1))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (on) LoomType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else LoomType.small,
                    color = if (on) c.ink else c.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LoomSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String? = null) {
    val c = Loom.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.accent,
            checkedTrackColor = c.accentSoft,
            uncheckedThumbColor = c.faint,
            uncheckedTrackColor = c.surfaceAlt,
            uncheckedBorderColor = c.hairStrong,
        ),
        modifier = if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
    )
}

/** 提示条：颜色 + 图标 + 文案，用于说明边界与机制。 */
@Composable
fun Notice(
    icon: String,
    text: String,
    fg: Color,
    bg: Color,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.r3))
            .background(bg)
            .padding(horizontal = Sp.x4, vertical = Sp.x3),
        verticalAlignment = Alignment.Top,
    ) {
        LoomIcon(icon, fg, 17.dp)
        Spacer(Modifier.width(Sp.x2))
        Column(Modifier.weight(1f)) {
            Text(text, style = LoomType.small, color = fg)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Sp.x2))
            trailing()
        }
    }
}

/** 占满剩余空间的空白（用于空状态垂直居中）。 */
@Composable
fun FillSpacer() {
    Spacer(Modifier.fillMaxSize())
}

/** 一等标签。 */
@Composable
fun Eyebrow(text: String, color: Color = Loom.colors.faint) {
    Text(text, style = LoomType.eyebrow, color = color)
}

@Composable
fun VSpace(dp: Int) {
    Spacer(Modifier.height(dp.dp))
}

@Composable
fun VSpace(dp: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(dp))
}


@Composable
fun HSpace(dp: Int) {
    Spacer(Modifier.width(dp.dp))
}

