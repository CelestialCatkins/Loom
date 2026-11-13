package com.loom.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loom.app.ui.theme.Loom
import com.loom.app.ui.theme.LoomType
import com.loom.app.ui.theme.R
import com.loom.app.ui.theme.Sp

/**
 * 底部面板：所有「需要更多信息」的操作都收在这里，
 * 保证高风险动作永远单独成组、并且要点两下才会发生。
 */
@Composable
fun BottomPanel(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Loom.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = R.r5, topEnd = R.r5))
                    .background(c.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = Sp.x5, end = Sp.x5, top = Sp.x3, bottom = Sp.x5)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(R.pill)).background(c.hairStrong))
                Spacer(Modifier.height(Sp.x4))
                content()
            }
        }
    }
}

@Composable
fun ConfirmPanel(
    eyebrow: String,
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Loom.colors
    BottomPanel(onDismiss) {
        Text(eyebrow, style = LoomType.eyebrow, color = if (danger) c.danger else c.accent)
        Spacer(Modifier.height(Sp.x2))
        Text(title, style = LoomType.h1, color = c.ink)
        Spacer(Modifier.height(Sp.x3))
        Text(body, style = LoomType.body, color = c.muted)
        Spacer(Modifier.height(Sp.x5))
        Row {
            LoomButton("取消", BtnStyle.Secondary, modifier = Modifier.weight(1f), onClick = onDismiss)
            Spacer(Modifier.width(Sp.x2))
            LoomButton(
                confirmLabel,
                if (danger) BtnStyle.Danger else BtnStyle.Primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            )
        }
    }
}

@Composable
fun PromptPanel(
    eyebrow: String,
    title: String,
    label: String,
    hint: String,
    initial: String = "",
    confirmLabel: String,
    validate: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = Loom.colors
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    BottomPanel(onDismiss) {
        Text(eyebrow, style = LoomType.eyebrow, color = c.accent)
        Spacer(Modifier.height(Sp.x2))
        Text(title, style = LoomType.h1, color = c.ink)
        Spacer(Modifier.height(Sp.x4))
        LoomTextField(
            value = value,
            onValueChange = {
                value = it
                error = null
            },
            label = label,
            hint = hint,
            error = error,
        )
        Spacer(Modifier.height(Sp.x4))
        Row {
            LoomButton("取消", BtnStyle.Secondary, modifier = Modifier.weight(1f), onClick = onDismiss)
            Spacer(Modifier.width(Sp.x2))
            LoomButton(
                confirmLabel,
                BtnStyle.Primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    val trimmed = value.trim()
                    val err = validate(trimmed)
                    if (err != null) {
                        error = err
                    } else {
                        onDismiss()
                        onConfirm(trimmed)
                    }
                },
            )
        }
    }
}

/** 长内容面板（插件详情、契约文档）：内容自己负责滚动。 */
@Composable
fun InfoPanel(
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Loom.colors
    BottomPanel(onDismiss) {
        Text(eyebrow, style = LoomType.eyebrow, color = c.accent)
        Spacer(Modifier.height(Sp.x2))
        Text(title, style = LoomType.h1, color = c.ink)
        Spacer(Modifier.height(Sp.x4))
        content()
        if (footer != null) {
            Spacer(Modifier.height(Sp.x4))
            footer()
        }
    }
}
