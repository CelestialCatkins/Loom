package com.loom.app.feature

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.loom.app.AppState
import com.loom.app.ImportKind
import com.loom.app.LoomApp
import com.loom.app.ui.BtnStyle
import com.loom.app.ui.ConfirmPanel
import com.loom.app.ui.Footnote
import com.loom.app.ui.Hairline
import com.loom.app.ui.LoomButton
import com.loom.app.ui.LoomIcon
import com.loom.app.ui.LoomSwitch
import com.loom.app.ui.Notice
import com.loom.app.ui.ScreenHeader
import com.loom.app.ui.SectionHeader
import com.loom.app.ui.Segmented
import com.loom.app.ui.VSpace
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp
import com.loom.app.ui.Fmt
import java.io.File

private val PRESETS = listOf(
    Triple("Loom", "墨底铜线 · 默认预置", "AliasLoom"),
    Triple("织机", "纸底墨线 · 中文名", "AliasWeave"),
    Triple("Loom Lab", "苔绿底 · 实验台", "AliasLab"),
)

/** 区域五：设置（外观、图标与名称、追踪、安全模式、数据、危险区）。 */
@Composable
fun SettingsScreen(state: AppState) {
    @Suppress("UNUSED_VARIABLE") val rev = state.revision
    val kernel = state.kernel
    val context = LocalContext.current
    var confirmSafety by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf<Int?>(null) }
    var showContract by remember { mutableStateOf(false) }

    if (showContract) ContractSheet(onDismiss = { showContract = false })
    if (confirmSafety) {
        ConfirmPanel(
            eyebrow = "SAFETY / 安全模式",
            title = "以安全模式重启？",
            body = "安全模式会禁用所有插件的装载，只保留内核与界面。用于排查某个插件把启动弄坏的情况。",
            confirmLabel = "进入安全模式",
            danger = true,
            onDismiss = { confirmSafety = false },
            onConfirm = {
                kernel.setSafeMode(true, "手动进入安全模式。")
                state.show("已进入安全模式：所有插件未装载")
            },
        )
    }
    if (confirmWipe) {
        ConfirmPanel(
            eyebrow = "DANGER / 清空",
            title = "清空所有项目数据？",
            body = "会删除全部项目（含插件副本、项目数据与导出快照），不可撤销。应用设置与已信任的 DEX 来源会保留。",
            confirmLabel = "全部清空",
            danger = true,
            onDismiss = { confirmWipe = false },
            onConfirm = {
                kernel.projects().forEach { kernel.fs.delete(it) }
                kernel.closeProject()
                kernel.events.clear()
                state.show("项目数据已清空")
            },
        )
    }
    confirmPreset?.let { index ->
        ConfirmPanel(
            eyebrow = "ICON / 图标与名称",
            title = "切换到「${PRESETS[index].first}」？",
            body = "这会启用另一套预置组合（activity-alias），并重启应用。桌面上的图标与名称会变成这一套 —— 它是预置的组合，不是任意替换。",
            confirmLabel = "切换并重启",
            onDismiss = { confirmPreset = null },
            onConfirm = {
                applyPreset(context, index)
                state.show("正在重启应用…")
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sp.page)
            .padding(top = Sp.x3, bottom = Sp.x7)
    ) {
        ScreenHeader("SETTINGS / 设置", "设置", "外观、身份、追踪、安全与数据。")

        // 外观
        VSpace(26)
        SectionHeader("01", "外观")
        VSpace(Sp.x3)
        Segmented(listOf("浅色", "深色", "跟随系统"), state.themeMode, { mode ->
            state.chooseTheme(mode)
        })
        VSpace(Sp.x2)
        Footnote("纸墨底 + 单一铜色；状态色只用于语义，不承担装饰。")

        // 图标与名称
        VSpace(26)
        SectionHeader("02", "图标与名称")
        VSpace(Sp.x3)
        Notice(
            icon = "info",
            text = "这里是「预置组合切换」，不是运行时任意替换。Android 不允许运行时改已安装应用的图标与名称，切换会导致应用重启。",
            fg = Loom.colors.muted,
            bg = Loom.colors.surfaceAlt,
        )
        VSpace(Sp.x2)
        val currentPreset = currentPreset(context)
        PRESETS.forEachIndexed { index, preset ->
            val on = index == currentPreset
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { if (!on) confirmPreset = index }
                    .padding(vertical = Sp.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoomIcon("weave", if (on) Loom.colors.accent else Loom.colors.faint, 22.dp)
                Spacer(Modifier.width(Sp.x3))
                Column(Modifier.weight(1f)) {
                    Text(preset.first, style = LoomType.title, color = Loom.colors.ink)
                    VSpace(2)
                    Text(preset.second, style = LoomType.small, color = Loom.colors.muted)
                }
                if (on) {
                    Text("当前", style = LoomType.mono, color = Loom.colors.accent)
                } else {
                    Text("切换", style = LoomType.mono, color = Loom.colors.muted)
                }
            }
            Hairline()
        }
        VSpace(Sp.x3)
        LoomButton("上传图标图片", BtnStyle.Secondary, "image") {
            state.startImport(ImportKind.Icon)
        }
        VSpace(Sp.x2)
        val custom = File(context.filesDir, "icon/custom.png")
        if (custom.exists()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(custom.lastModified()) { BitmapFactory.decodeFile(custom.absolutePath) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "已上传的图标",
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(R.r2)),
                    )
                    Spacer(Modifier.width(Sp.x3))
                }
                Footnote("已保存到项目文件系统 · ${Fmt.bytes(custom.length())}\n说明：上传的图片进入预置配置，随构建版本生效，不能即时替换桌面图标。")
            }
        } else {
            Footnote("还没有上传自定义图标。上传的图片会进入预置配置，随构建版本生效。")
        }

        // 追踪
        VSpace(26)
        SectionHeader("03", "运行追踪")
        VSpace(Sp.x3)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("记录因果链", style = LoomType.title, color = Loom.colors.ink)
                VSpace(2)
                Text(
                    "当前 ${kernel.events.size()} 条，缓冲上限 ${kernel.events.limit} 条（环形，超出丢最旧）",
                    style = LoomType.small,
                    color = Loom.colors.muted,
                )
            }
            LoomSwitch(kernel.events.tracing, { on ->
                kernel.events.tracing = on
                kernel.prefs.edit().putBoolean("tracing", on).apply()
                state.refresh()
            }, label = "运行追踪")
        }
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Footnote("关闭后不记录、不产生开销；已记录的条目保留。", modifier = Modifier.weight(1f))
            Text(
                "清空",
                style = LoomType.small,
                color = Loom.colors.accent,
                modifier = Modifier
                    .clickable {
                        kernel.events.clear()
                        state.show("追踪日志已清空")
                    }
                    .padding(horizontal = Sp.x2, vertical = Sp.x3),
            )
        }

        // 安全模式
        VSpace(26)
        SectionHeader("04", "安全模式")
        VSpace(Sp.x3)
        Notice(
            icon = "shield",
            text = if (kernel.safetyMode) "当前处于安全模式：所有插件未装载。" else "当前正常启动，插件按依赖顺序装载。",
            fg = if (kernel.safetyMode) Loom.colors.warn else Loom.colors.muted,
            bg = if (kernel.safetyMode) Loom.colors.warnSoft else Loom.colors.surfaceAlt,
        )
        VSpace(Sp.x2)
        Footnote("自动机制：连续 2 次启动未完成（首帧前崩溃或被系统杀掉）时，下次启动自动进入安全模式，并在这里显示原因。")
        VSpace(Sp.x3)
        if (kernel.safetyMode) {
            LoomButton("退出安全模式", BtnStyle.Primary, "power") {
                kernel.setSafeMode(false, "")
                state.show("已退出安全模式")
            }
        } else {
            LoomButton("进入安全模式", BtnStyle.Secondary, "shield") { confirmSafety = true }
        }

        // 数据
        VSpace(26)
        SectionHeader("05", "数据")
        VSpace(Sp.x3)
        val total = kernel.projects().sumOf { kernel.fs.sizeOf(it) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("项目数据占用", style = LoomType.title, color = Loom.colors.ink, modifier = Modifier.weight(1f))
            Text(Fmt.bytes(total), style = LoomType.mono, color = Loom.colors.muted)
        }
        Hairline()
        Footnote("导出单元是项目：在项目详情里导出 .loomproj。归档只含项目自身内容，不含宿主密钥或用户凭证。")
        VSpace(Sp.x3)
        LoomButton("导入 .loomproj", BtnStyle.Secondary, "import") {
            state.startImport(ImportKind.Project)
        }

        // 关于
        VSpace(26)
        SectionHeader("06", "关于")
        VSpace(Sp.x3)
        Text(
            "Loom 1.0.0 · 内核 v1 · 插件契约 v1\n" +
                "宿主不含业务功能；脚本插件跑在 Lua 沙箱（无 io/os/require）；" +
                "DEX 插件同进程加载，非沙箱，等同宿主权限，来源需显式信任。",
            style = LoomType.small,
            color = Loom.colors.muted,
        )
        VSpace(Sp.x2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Footnote("LuaJ 3.0.1 · 纯 Java，无 NDK 依赖", modifier = Modifier.weight(1f))
            Text(
                "插件契约",
                style = LoomType.small,
                color = Loom.colors.accent,
                modifier = Modifier
                    .clickable { showContract = true }
                    .padding(horizontal = Sp.x2, vertical = Sp.x3),
            )
        }

        // 危险区（置底、单独分组）
        VSpace(34)
        Hairline(color = Loom.colors.danger.copy(alpha = 0.45f))
        VSpace(Sp.x4)
        Text("DANGER / 危险操作", style = LoomType.eyebrow, color = Loom.colors.danger)
        VSpace(Sp.x2)
        Footnote("以下操作不可撤销，与上方设置分开。")
        VSpace(Sp.x3)
        LoomButton("清空所有项目数据", BtnStyle.DangerOutline, "trash") { confirmWipe = true }
    }
}

/** 当前启用的 activity-alias 序号（用于预置组合的选中态）。 */
private fun currentPreset(context: Context): Int {
    val pm = context.packageManager
    PRESETS.forEachIndexed { index, preset ->
        val cn = ComponentName(context.packageName, context.packageName + "." + preset.third)
        val state = pm.getComponentEnabledSetting(cn)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return index
    }
    return 0
}

/**
 * 切换预置组合：启用目标 alias、禁用其余，然后重启应用。
 * 已启用的 alias 不能删除，否则设备启动会崩溃 —— 这里只改 enabled 状态。
 */
private fun applyPreset(context: Context, index: Int) {
    val pm = context.packageManager
    PRESETS.forEachIndexed { i, preset ->
        val cn = ComponentName(context.packageName, context.packageName + "." + preset.third)
        pm.setComponentEnabledSetting(
            cn,
            if (i == index) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
    val target = ComponentName(context.packageName, context.packageName + "." + PRESETS[index].third)
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = target
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    (context as? Activity)?.finishAffinity()
    context.startActivity(intent)
}
