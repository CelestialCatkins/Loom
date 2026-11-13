package com.loom.app.api

import androidx.compose.runtime.Composable

/**
 * DEX 插件的 Compose 界面贡献点。
 *
 * 注意：`@Composable` 的调用 ABI 随 Compose 编译器版本变化，因此这条路径是**版本锁定**的：
 * 插件必须与宿主使用同一 Compose BOM（见 [LoomApi.COMPOSE_ABI]），并在 plugin.json 里声明
 * `"composeAbi"`。宿主装载时会校验，不匹配就判 ERROR，不会带着错误的 ABI 跑起来。
 *
 * 想要「任何 Compose 版本都能用」的界面，请改用 [PluginContext.viewScreen] 返回原生 View。
 */
interface ComposeScreen {
    @Composable
    fun Content()
}
