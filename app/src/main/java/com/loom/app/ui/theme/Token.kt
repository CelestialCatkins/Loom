package com.loom.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计 Token —— 唯一来源。
 *
 * 方向：工业编辑风。纸墨底色 + 单一铜色（线/强调），状态色只做语义提示。
 * 全部界面只能引用这里的颜色、字号、间距、圆角，禁止就地写死值。
 */
@Immutable
data class LoomColors(
    val paper: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkInverse: Color,
    val muted: Color,
    val faint: Color,
    val hairline: Color,
    val hairStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val scrim: Color,
)

/** 浅色：稿纸。 */
val PaperColors = LoomColors(
    paper = Color(0xFFF2EFE7),
    surface = Color(0xFFFBF9F3),
    surfaceAlt = Color(0xFFE8E4D8),
    ink = Color(0xFF1A1B17),
    inkInverse = Color(0xFFF7F4EA),
    muted = Color(0xFF6C6A60),
    faint = Color(0xFF9C9889),
    hairline = Color(0xFFD8D3C5),
    hairStrong = Color(0xFFB7B1A0),
    accent = Color(0xFFB4512A),
    accentSoft = Color(0xFFF1DDD0),
    ok = Color(0xFF2C6B49),
    okSoft = Color(0xFFDCEAE1),
    warn = Color(0xFF85610F),
    warnSoft = Color(0xFFF1E5C7),
    danger = Color(0xFFA32B1E),
    dangerSoft = Color(0xFFF3DAD4),
    scrim = Color(0x991A1B17),
)

/** 深色：夜织。 */
val NightColors = LoomColors(
    paper = Color(0xFF171713),
    surface = Color(0xFF1F201A),
    surfaceAlt = Color(0xFF2A2B23),
    ink = Color(0xFFEDE9DC),
    inkInverse = Color(0xFF14150F),
    muted = Color(0xFFA6A192),
    faint = Color(0xFF7C7869),
    hairline = Color(0xFF34352B),
    hairStrong = Color(0xFF4C4D3E),
    accent = Color(0xFFDD8155),
    accentSoft = Color(0xFF3A2A20),
    ok = Color(0xFF84BC98),
    okSoft = Color(0xFF20352A),
    warn = Color(0xFFCFA752),
    warnSoft = Color(0xFF3A2F19),
    danger = Color(0xFFE68A72),
    dangerSoft = Color(0xFF3E2520),
    scrim = Color(0xCC000000),
)

/** 间距尺度：4 的倍数。 */
object Sp {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x7 = 32.dp
    val x8 = 40.dp
    val page = 20.dp
    val touch = 48.dp
}

/** 圆角：同心规则，外层 = 内层 + 内边距。 */
object R {
    val r1 = 4.dp
    val r2 = 8.dp
    val r3 = 12.dp
    val r4 = 16.dp
    val r5 = 20.dp
    val pill = 100.dp
}

/**
 * 三音色字体系统：
 *  - Serif   标题（编辑感，中文走系统 Noto Serif CJK）
 *  - Sans    正文（长文阅读舒适）
 *  - Mono    一切标识/计数/时间/代码（等宽数字，天然对齐）
 * 不引入任何外部字体文件，首屏零字体阻塞。
 */
object LoomType {
    val display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    )
    val h1 = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    )
    val h2 = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    )
    val small = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    val micro = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val eyebrow = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val monoLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val monoHuge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 46.sp,
        lineHeight = 56.sp,
    )
}
