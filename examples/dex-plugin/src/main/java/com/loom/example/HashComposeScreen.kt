package com.loom.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loom.app.api.ComposeScreen

/**
 * Compose 界面贡献点示例（版本锁定）：
 * 必须在 plugin.json 里写 "composeAbi": "2024.09.03"，与宿主 Compose BOM 一致。
 * 注册方式：ctx.composeScreen("摘要台", "code", HashComposeScreen())
 */
class HashComposeScreen : ComposeScreen {

    @Composable
    override fun Content() {
        Column(Modifier.background(androidx.compose.ui.graphics.Color(0xFFF2EFE7)).padding(20.dp)) {
            Text("DEX 插件 · Compose 界面", fontSize = 20.sp, color = androidx.compose.ui.graphics.Color(0xFF1A1B17))
            Text(
                "这条路径与宿主的 Compose 编译器版本绑定；版本不一致时内核会在装载阶段判 ERROR。",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = androidx.compose.ui.graphics.Color(0xFF6C6A60),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
