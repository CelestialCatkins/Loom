package com.loom.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.loom.app.kernel.Kernel

/**
 * 应用入口：建立内核、判定安全模式、记录「这次启动是否完成」。
 *
 * 连续崩溃自动进入安全模式的实现就在这里：每次启动把计数 +1，
 * 界面画出首帧后清零；若上次启动没来得及清零（>=2），说明连续两次没起来。
 */
class LoomApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("loom", Context.MODE_PRIVATE)
        val pending = p.getInt("boot_pending", 0)
        val manual = p.getBoolean("safety", false)
        val auto = pending >= 2

        val k = kernel(this)
        val reason = when {
            auto -> "检测到连续 $pending 次启动未完成，已自动进入安全模式。"
            manual -> p.getString("safety_reason", "") ?: ""
            else -> ""
        }
        k.initSafety(manual || auto, reason)
        if (auto) {
            p.edit().putBoolean("safety", true).putString("safety_reason", reason).apply()
        }
        p.edit().putInt("boot_pending", pending + 1).apply()
    }

    companion object {
        @Volatile
        private var instance: Kernel? = null

        fun kernel(context: Context): Kernel =
            instance ?: synchronized(this) {
                instance ?: Kernel(context.applicationContext).also { instance = it }
            }

        /** 界面首帧完成后调用：清空启动计数。 */
        fun markBootOk(kernel: Kernel) {
            kernel.prefs.edit().putInt("boot_pending", 0).apply()
        }

        fun themeMode(context: Context): Int =
            context.getSharedPreferences("loom", Context.MODE_PRIVATE).getInt("theme", 2)

        fun isDark(context: Context): Boolean {
            return when (themeMode(context)) {
                0 -> false
                1 -> true
                else -> {
                    val ui = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    ui == Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
    }
}
