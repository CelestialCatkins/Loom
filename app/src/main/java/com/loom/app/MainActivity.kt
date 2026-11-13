package com.loom.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.loom.app.feature.GraphScreen
import com.loom.app.feature.PluginsScreen
import com.loom.app.feature.ProjectScreen
import com.loom.app.feature.RunScreen
import com.loom.app.feature.SettingsScreen
import com.loom.app.feature.WorkbenchScreen
import com.loom.app.kernel.Kernel
import com.loom.app.ui.BannerHost
import com.loom.app.ui.LoomBottomBar
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kernel = LoomApp.kernel(this)
        kernel.restoreProject()
        setContent { LoomRoot(kernel) }
    }

    override fun onResume() {
        super.onResume()
        LoomApp.kernel(this).notifyChanged()
    }
}

private fun readUri(ctx: android.content.Context, uri: Uri, max: Int = 64 * 1024 * 1024): ByteArray {
    val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("无法读取这个文件")
    input.use {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = it.read(buf)
            if (n <= 0) break
            total += n
            if (total > max) throw IOException("文件超过 ${max / 1024 / 1024}MB 上限")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

@Composable
fun LoomRoot(kernel: Kernel) {
    val context = LocalContext.current
    val state = remember { AppState(kernel) }
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        0 -> false
        1 -> true
        else -> systemDark
    }

    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingImport by remember { mutableStateOf<ImportKind?>(null) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        val req = pendingExport
        pendingExport = null
        if (uri != null && req != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { req.write(it) }
                            ?: throw IOException("无法写入目标文件")
                    }
                }
                state.show(
                    if (result.isSuccess) "已导出 ${req.fileName}"
                    else "导出失败：" + (result.exceptionOrNull()?.message ?: "未知错误")
                )
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = pendingImport
        pendingImport = null
        if (uri != null && kind != null) {
            scope.launch {
                val outcome = runCatching {
                    when (kind) {
                        ImportKind.Project -> {
                            val bytes = withContext(Dispatchers.IO) { readUri(context, uri) }
                            val project = withContext(Dispatchers.IO) {
                                kernel.fs.importZip(ByteArrayInputStream(bytes))
                            }
                            kernel.openProject(project)
                            "已导入「${project.name}」"
                        }
                        ImportKind.Plugin -> {
                            val project = kernel.current ?: throw IOException("请先打开一个项目")
                            val bytes = withContext(Dispatchers.IO) { readUri(context, uri) }
                            val ids = withContext(Dispatchers.IO) {
                                kernel.fs.installPluginZip(project, ByteArrayInputStream(bytes))
                            }
                            kernel.reload()
                            "已导入 ${ids.size} 个插件：" + ids.joinToString("、")
                        }
                        ImportKind.Icon -> {
                            val bytes = withContext(Dispatchers.IO) { readUri(context, uri, 8 * 1024 * 1024) }
                            withContext(Dispatchers.IO) {
                                val f = File(context.filesDir, "icon/custom.png")
                                f.parentFile?.mkdirs()
                                f.writeBytes(bytes)
                            }
                            kernel.notifyChanged()
                            "图标已存入项目文件系统；作为预置配置，随构建版本生效"
                        }
                    }
                }
                state.show(outcome.getOrElse { "操作失败：" + (it.message ?: it.javaClass.simpleName) })
            }
        }
    }

    state.exportAction = { req ->
        pendingExport = req
        createDocument.launch(req.fileName)
    }
    state.importAction = { kind ->
        pendingImport = kind
        when (kind) {
            ImportKind.Icon -> openDocument.launch(arrayOf("image/*"))
            else -> openDocument.launch(
                arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed", "*/*")
            )
        }
    }

    DisposableEffect(kernel) {
        val listener: () -> Unit = { state.refresh() }
        kernel.addListener(listener)
        onDispose { kernel.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        delay(900)
        LoomApp.markBootOk(kernel)
    }

    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }

    LoomTheme(dark = dark) {
        Surface(Modifier.fillMaxSize(), color = Loom.colors.paper) {
            Scaffold(
                containerColor = Loom.colors.paper,
                bottomBar = {
                    if (!state.runOpen) {
                        LoomBottomBar(state.tab) { index ->
                            state.tab = index
                            state.refresh()
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (state.runOpen) {
                        RunScreen(state)
                    } else {
                        when (state.tab) {
                            0 -> WorkbenchScreen(state)
                            1 -> ProjectScreen(state)
                            2 -> PluginsScreen(state)
                            3 -> GraphScreen(state)
                            else -> SettingsScreen(state)
                        }
                    }
                    BannerHost(state.banner) { state.banner = null }
                }
            }
        }
    }

    BackHandler(enabled = state.runOpen) { state.runOpen = false }
    BackHandler(enabled = !state.runOpen && state.tab != 0) {
        state.tab = 0
        state.refresh()
    }
}
