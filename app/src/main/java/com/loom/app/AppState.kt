package com.loom.app

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.loom.app.kernel.Kernel

/** 一条内联提示：失败不静默，总是带原因。 */
data class BannerMsg(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val key: Long = System.nanoTime(),
)

/** 需要由 Activity 层处理的导出请求（SAF）。 */
class ExportRequest(
    val mime: String,
    val fileName: String,
    val write: (java.io.OutputStream) -> Unit,
)

/** 需要由 Activity 层处理的导入请求。 */
enum class ImportKind { Project, Plugin, Icon }

/**
 * 界面状态（不是业务状态）：当前页签、推入的运行台、提示条。
 * 内核状态变化时 revision +1，所有读取 revision 的界面会自动重组。
 */
class AppState(val kernel: Kernel) {

    var tab by mutableStateOf(0)
    var revision by mutableStateOf(0)
    var runOpen by mutableStateOf(false)
    var banner by mutableStateOf<BannerMsg?>(null)
    var themeMode by mutableStateOf(kernel.prefs.getInt("theme", 2))

    /** 由 Activity 层注入的 SAF 动作。 */
    var exportAction: (ExportRequest) -> Unit = {}
    var importAction: (ImportKind) -> Unit = {}

    fun refresh() {
        revision++
    }

    fun chooseTheme(mode: Int) {
        themeMode = mode
        kernel.prefs.edit().putInt("theme", mode).apply()
    }

    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        banner = BannerMsg(text, actionLabel, action)
    }

    fun startExport(req: ExportRequest) = exportAction(req)

    fun startImport(kind: ImportKind) = importAction(kind)
}
