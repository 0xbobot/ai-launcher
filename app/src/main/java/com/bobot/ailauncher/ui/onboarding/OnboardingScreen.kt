package com.bobot.ailauncher.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.util.rebindListener

/** 检测本应用是否已被设为默认桌面 */
fun isDefaultLauncher(context: Context): Boolean {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return ri?.activityInfo?.packageName == context.packageName
}

/** 检测通知读取权限是否已开启 */
fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@Composable
fun OnboardingNav(onFinish: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    when (step) {
        0 -> StepWelcome(onNext = { step = 1 }, onSkip = onFinish)
        1 -> StepDefaultLauncher(onNext = { step = 2 }, onSkip = onFinish)
        2 -> StepNotificationAccess(onNext = { step = 3 }, onSkip = onFinish)
        else -> StepDone(onFinish = onFinish)
    }
}

@Composable
private fun StepScaffold(
    step: Int,
    totalSteps: Int = 4,
    onSkip: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSkip) {
                Text("跳过", color = AILauncherColors.Hint)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        content()
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSteps) { i ->
                ProgressDot(active = i == step)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProgressDot(active: Boolean) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
        drawCircle(
            color = if (active) AILauncherColors.Accent else AILauncherColors.Divider,
            radius = size.minDimension / 2
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.Title)
    ) {
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
        )
    }
}

/** Step 1：欢迎 */
@Composable
private fun StepWelcome(onNext: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(step = 0, onSkip = onSkip) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = AILauncherColors.Accent,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "把手机变成会干活的空间",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "说出你想干嘛，不用找 App",
            fontSize = 16.sp,
            color = AILauncherColors.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        PrimaryButton(text = "开始", onClick = onNext)
    }
}

/** Step 2：设为默认桌面 */
@Composable
private fun StepDefaultLauncher(onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    StepScaffold(step = 1, onSkip = onSkip) {
        Text(
            text = "设为默认桌面",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "把 AI桌面 设为默认，才能替代系统桌面，接管你的 Home 键。",
            fontSize = 15.sp,
            color = AILauncherColors.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        StatusRow(
            done = isDefaultLauncher(context),
            doneText = "已设为默认桌面",
            todoText = "尚未设为默认"
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "去设置", onClick = {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            if (isDefaultLauncher(context)) {
                onNext()
            } else {
                Toast.makeText(context, "还没检测到，请先在设置中完成", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("下一步", color = AILauncherColors.Title)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "从设置返回后，点「下一步」会自动检测",
            fontSize = 12.sp,
            color = AILauncherColors.Hint
        )
    }
}

/** Step 3：通知读取权限 */
@Composable
private fun StepNotificationAccess(onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    StepScaffold(step = 2, onSkip = onSkip) {
        Text(
            text = "开启通知读取",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "「正在进行时」需要读取通知，才能把会议、快递、消息主动浮上来。",
            fontSize = 15.sp,
            color = AILauncherColors.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        StatusRow(
            done = isNotificationAccessGranted(context),
            doneText = "通知读取已开启",
            todoText = "尚未开启"
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "去开启", onClick = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            if (isNotificationAccessGranted(context)) {
                // 授权发生在 App 运行中，强制重绑让 onListenerConnected 立刻回调
                rebindListener(context)
                onNext()
            } else {
                Toast.makeText(context, "还没检测到，请先在设置中开启", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("下一步", color = AILauncherColors.Title)
        }
    }
}

/** Step 4：完成 */
@Composable
private fun StepDone(onFinish: () -> Unit) {
    StepScaffold(step = 3, onSkip = onFinish) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = AILauncherColors.Success,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "一切就绪",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "意图入口、正在进行时、能力视图都已准备好。\n按 Home 键，随时回到你的 AI 空间。",
            fontSize = 15.sp,
            color = AILauncherColors.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        PrimaryButton(text = "进入 AI桌面", onClick = onFinish)
    }
}

@Composable
private fun StatusRow(done: Boolean, doneText: String, todoText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = if (done) AILauncherColors.Success else AILauncherColors.Hint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (done) doneText else todoText,
            fontSize = 14.sp,
            color = if (done) AILauncherColors.Success else AILauncherColors.Hint
        )
    }
}
