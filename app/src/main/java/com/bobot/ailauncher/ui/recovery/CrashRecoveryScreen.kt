package com.bobot.ailauncher.ui.recovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.CrashRecord
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃恢复屏（核心版）：连续崩两次后出现，代替正常首页。
 *
 * 只用最基础的 Material3 组件（Column/Text/Button/TextButton），
 * 不用动画、不用应用中心、不用智能卡片、不用角标、不用自定义组件，
 * 保证它自己不崩。崩溃记录只读展示，不改设置页的数据。
 */
@Composable
fun CrashRecoveryScreen(
    latestRecord: CrashRecord?,
    onSwitchToSystemLauncher: () -> Unit,
    onRetry: () -> Unit,
    onShareRecord: (CrashRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRecord by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (!showRecord) {
            Text(
                text = "桌面遇到了问题",
                fontSize = AILauncherType.Title.fontSize,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.current.Title
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "上次打开时闪退了。为了不影响你用手机，你可以选择：",
                fontSize = AILauncherType.Body.fontSize,
                color = AILauncherColors.current.Body
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSwitchToSystemLauncher,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "切回系统桌面",
                    fontSize = AILauncherType.BodyLarge.fontSize
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "回到系统桌面，手机立刻能正常用；修好了再从系统设置里切回来。",
                fontSize = AILauncherType.Footnote.fontSize,
                color = AILauncherColors.current.Hint
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "重试",
                    fontSize = AILauncherType.BodyLarge.fontSize
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showRecord = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "查看崩溃记录",
                    fontSize = AILauncherType.BodyLarge.fontSize
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "不用进设置，这里就能看到崩溃记录、发给开发者。",
                fontSize = AILauncherType.Footnote.fontSize,
                color = AILauncherColors.current.Hint
            )
        } else {
            Text(
                text = "崩溃记录",
                fontSize = AILauncherType.Title.fontSize,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.current.Title
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (latestRecord == null) {
                Text(
                    text = "暂无崩溃记录",
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Body
                )
            } else {
                Text(
                    text = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(Date(latestRecord.epochMillis)),
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Hint
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = latestRecord.title,
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Title
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = latestRecord.content,
                    fontSize = AILauncherType.Caption.fontSize,
                    fontFamily = FontFamily.Monospace,
                    color = AILauncherColors.current.Body
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onShareRecord(latestRecord) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "分享这条记录",
                        fontSize = AILauncherType.BodyLarge.fontSize
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showRecord = false }) {
                Text(
                    text = "返回",
                    fontSize = AILauncherType.BodyLarge.fontSize
                )
            }
        }
    }
}
