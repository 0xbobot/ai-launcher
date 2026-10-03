package com.bobot.ailauncher.ui.battery

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.28.0：低电量温和提醒弹窗。
 * 原则：只提醒、不干预、不制造焦虑。
 * - 居中卡片 + 半透明背景（不全屏压迫）
 * - 暖色系，不用警告红
 * - 文案关心口吻，一键关闭
 * - 「去开省电模式」只跳系统设置页，开关由用户自己点
 */
class BatteryGuardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PCT = "pct"
        const val EXTRA_CRITICAL = "critical"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pct = intent.getIntExtra(EXTRA_PCT, 10)
        val critical = intent.getBooleanExtra(EXTRA_CRITICAL, false)
        setContent {
            BatteryGuardDialog(
                pct = pct,
                critical = critical,
                onOpenSaver = {
                    // 只引导：跳系统省电设置，开关用户自己点
                    try {
                        val i = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        // 部分机型没有这个 action，兜底到电池设置
                        if (i.resolveActivity(packageManager) == null) {
                            startActivity(Intent(Settings.ACTION_BATTERY_SETTINGS))
                        } else {
                            startActivity(i)
                        }
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) { }
                    }
                    finish()
                },
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
private fun BatteryGuardDialog(
    pct: Int,
    critical: Boolean,
    onOpenSaver: () -> Unit,
    onDismiss: () -> Unit
) {
    // 暖色系：低电量用暖橙，紧急用深橙（不用刺眼的警告红）
    val accent = if (critical) Color(0xFFE8734A) else Color(0xFFF0A24A)
    val title = if (critical) "掉到 $pct% 了，还没充？"
    else "电量到 $pct% 了"
    val message = if (critical) "再不插电真要关机了。也可以开省电模式多撑一会儿"
    else "充个电休息一下？也可以开省电模式多撑会儿"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 电池图标（Canvas 手绘，暖色）
                BatteryIcon(pct = pct, color = accent)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = AILauncherColors.Body,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                // 主按钮：引导去开省电模式（友好文案）
                Button(
                    onClick = onOpenSaver,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "去开省电模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 次按钮：一键关闭，毫无压力
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "好，知道了",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint
                    )
                }
            }
        }
    }
}

/** 手绘电池图标：圆角外框 + 暖色电量填充 */
@Composable
private fun BatteryIcon(pct: Int, color: Color) {
    Canvas(modifier = Modifier.size(72.dp, 40.dp)) {
        val w = size.width
        val h = size.height
        val capW = w * 0.08f
        val bodyW = w - capW - 4.dp.toPx()
        // 外框
        drawRoundRect(
            color = Color(0xFFD8D2C8),
            topLeft = Offset.Zero,
            size = Size(bodyW, h),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        // 电池帽
        drawRoundRect(
            color = Color(0xFFD8D2C8),
            topLeft = Offset(bodyW + 2.dp.toPx(), h * 0.3f),
            size = Size(capW, h * 0.4f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // 电量填充
        val pad = 4.dp.toPx()
        val fillW = (bodyW - pad * 2) * (pct.coerceIn(0, 100) / 100f)
        if (fillW > 0) {
            drawRoundRect(
                color = color,
                topLeft = Offset(pad, pad),
                size = Size(fillW, h - pad * 2),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )
        }
    }
}
