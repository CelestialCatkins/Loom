package com.loom.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalLoomColors = staticCompositionLocalOf { PaperColors }

/** 主题入口：只决定这一棵树用哪套 Token，并且把 Material3 的默认组件也拉回同一套色。 */
object Loom {

    val colors: LoomColors
        @Composable get() = LocalLoomColors.current

    @Composable
    fun statusColor(code: String): Color {
        val c = colors
        return when (code) {
            "ACTIVE" -> c.ok
            "PENDING" -> c.warn
            "ERROR" -> c.danger
            else -> c.muted
        }
    }

    @Composable
    fun statusSoft(code: String): Color {
        val c = colors
        return when (code) {
            "ACTIVE" -> c.okSoft
            "PENDING" -> c.warnSoft
            "ERROR" -> c.dangerSoft
            else -> c.surfaceAlt
        }
    }
}

@Composable
fun LoomTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) NightColors else PaperColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.inkInverse,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.hairStrong,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.inkInverse,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.muted,
            outline = colors.hairStrong,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalLoomColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
