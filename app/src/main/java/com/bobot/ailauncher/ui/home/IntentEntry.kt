package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.intent.IntentCoordinator
import com.bobot.ailauncher.core.intent.IntentUiState
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.delay

/**
 * R2 UI（文档二 P0）：首页意图入口胶囊——封面与 TODAY 紧凑条之间。
 *
 * 呈现规则（Bob 2026-10-20 拍板）：
 * - 只有 NeedsConfirmation（中/高风险动作确认，安全门）用 AlertDialog，全 App 意图链路仅此一种弹窗；
 *   其余状态（回复/错误/缺权限）一律非模态：七仔气泡（协调器已 say）+ 胶囊下方短时小条/说明卡。
 * - 胶囊右侧只有提交按钮，不做麦克风（语音胶囊已于 10-18 全部清除）。
 * - 外观沿用封面体系：Card 88% 底 + 柔和阴影 + 大圆角，与 NextUpStrip 同语言，不新增渐变/重阴影。
 *
 * 状态由 [IntentCoordinator] 驱动，本组件只读渲染 + 转发用户动作，不碰 LLM/执行细节。
 */
@Composable
fun IntentEntry(
    state: IntentUiState,
    onSubmit: (String) -> Unit,
    onConfirm: (ActionRequest) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissionSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val planning = state is IntentUiState.Planning
    val canSubmit = text.isNotBlank() && !planning

    fun submitIfReady() {
        if (canSubmit) onSubmit(text)
    }

    // 回复小条短时呈现：5 秒后自动收起（协调器 dismiss 回编辑态，原话不丢）
    LaunchedEffect(state) {
        if (state is IntentUiState.Reply) {
            delay(5_000)
            onDismiss()
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // ---- 胶囊本体 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = AILauncherColors.Title.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(28.dp))
                .background(AILauncherColors.Card.copy(alpha = 0.88f))
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = AILauncherColors.Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                enabled = !planning,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = AILauncherColors.Title
                ),
                cursorBrush = SolidColor(AILauncherColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitIfReady() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                text = "想做什么？",
                                fontSize = 15.sp,
                                color = AILauncherColors.Hint
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            // 提交区：Planning 时换轻量进度（文字保留、不可重复提交）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSubmit || planning) AILauncherColors.Accent
                        else AILauncherColors.Accent.copy(alpha = 0.35f)
                    )
                    .clickable(enabled = canSubmit) { submitIfReady() },
                contentAlignment = Alignment.Center
            ) {
                if (planning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "提交",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ---- 状态小条 / 说明卡（非模态） ----
        when (val s = state) {
            is IntentUiState.Reply -> {
                StatusStrip(
                    text = s.text,
                    onClose = onDismiss
                )
            }
            is IntentUiState.Error -> {
                val noKey = s.message == IntentCoordinator.NO_KEY_MESSAGE
                StatusStrip(
                    text = s.message,
                    actionLabel = if (noKey) "去设置" else null,
                    onAction = if (noKey) onOpenSettings else null,
                    onClose = onDismiss
                )
            }
            is IntentUiState.NeedsPermission -> {
                PermissionExplainCard(
                    message = s.message,
                    onGoSettings = {
                        onOpenPermissionSettings(s.permission)
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            }
            else -> Unit
        }
    }

    // ---- 唯一弹窗：中/高风险动作确认（安全门，Bob 拍板归类） ----
    if (state is IntentUiState.NeedsConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "确认执行？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
            },
            text = {
                Text(
                    text = state.message,
                    fontSize = 14.sp,
                    color = AILauncherColors.Body
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(state.request) }) {
                    Text(
                        text = "执行",
                        color = AILauncherColors.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消", color = AILauncherColors.Hint)
                }
            }
        )
    }
}

/** 胶囊下方短时小条：回复/错误共用，可带一个动作（如"去设置"），可关。 */
@Composable
private fun StatusStrip(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = AILauncherColors.Body,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = AILauncherColors.Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = AILauncherColors.Hint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 缺权限说明卡（非模态）：与 HomeScreen 无障碍引导卡同风格——先讲清用途，再给"去开启"。 */
@Composable
private fun PermissionExplainCard(
    message: String,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "需要开启一个权限",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AILauncherColors.Title
        )
        Text(
            text = message,
            fontSize = 13.sp,
            color = AILauncherColors.Body
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = "暂不", color = AILauncherColors.Hint, fontSize = 13.sp)
            }
            TextButton(onClick = onGoSettings) {
                Text(
                    text = "去开启",
                    color = AILauncherColors.Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * 按权限类型跳对应系统设置页（参考 ActionEngine 可能给出的权限标识做尽力映射，
 * 无法识别时落到本应用详情页，不静默失败）。当前 capabilities.json 尚无能力声明
 * permission，此路径未在真实触发下验证过。
 */
internal fun openPermissionSettings(context: Context, permission: String) {
    val p = permission.lowercase()
    val intent = when {
        p.contains("notification") -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        p.contains("accessibility") || p.contains("bind_accessibility") ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        p.contains("usage") -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        p.contains("overlay") || p.contains("alert_window") -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        else -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // 个别 ROM 没有对应设置页：落到应用详情，不让用户点空
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }
}
