package com.loom.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文案格式化：所有时间/计数都在这里变成字符串，展示时用等宽字体。 */
object Fmt {

    fun ago(ts: Long): String {
        if (ts <= 0) return "—"
        val d = System.currentTimeMillis() - ts
        return when {
            d < 60_000L -> "刚刚"
            d < 3_600_000L -> "${d / 60_000L} 分钟前"
            d < 86_400_000L -> "${d / 3_600_000L} 小时前"
            d < 172_800_000L -> "昨天 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
            d < 7 * 86_400_000L -> "${d / 86_400_000L} 天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))
        }
    }

    fun date(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ts))

    fun clock(ts: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA).format(Date(ts))

    fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.CHINA, "%.1f KB", b / 1024.0)
        else -> String.format(Locale.CHINA, "%.1f MB", b / 1024.0 / 1024.0)
    }
}
