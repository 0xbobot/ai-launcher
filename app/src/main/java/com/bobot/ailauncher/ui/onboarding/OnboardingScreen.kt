@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import com.bobot.ailauncher.util.rebindListener
import com.bobot.ailauncher.ui.island.IslandToasts

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

/**
 * v0.68.98（F-12）：随生命周期刷新的状态值——初值组合期读一次，之后每次
 * ON_RESUME 重读。引导步要跳系统设置页再返回，此前状态行在组合期直读、
 * 返回后无重组触发就一直说「尚未…」，用户明明设好了还被状态行说谎。
 * 做法与 PetZone 的 resumeTick 同款（只在用户看得见时刷新，省电原则）。
 */
@Composable
private fun rememberResumeRefreshed(check: () -> Boolean): Boolean {
    var value by remember { mutableStateOf(check()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = check()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    return value
}

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
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 刘海避让（Bob 2026-10-25 全页检查）：横屏下两侧挖孔区；两侧吃
            // displayCutout inset——竖屏侧边 inset 为 0，零变化；顶部 32dp 本就
            // 高于标准状态栏，不另加。
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Start + WindowInsetsSides.End
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // F-10：引导页点按补 tick（走 Haptics 两档门面，下同）
            TextButton(onClick = { view.tickHaptic(); onSkip() }) {
                Text("跳过", color = AILauncherColors.current.Hint)
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
    // 批B ③：颜色在组合上下文先读出再带进 Canvas（DrawScope 非组合上下文）
    val dotColor = if (active) AILauncherColors.current.Accent else AILauncherColors.current.Divider
    androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
        drawCircle(
            color = dotColor,
            radius = size.minDimension / 2
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val view = LocalView.current
    Button(
        // F-10：引导主按钮（开始/去设置/去开启/进入）统一 confirm 档
        onClick = { view.confirmHaptic(); onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.current.Title)
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
            // 体检批D ⑨：装饰（下方标题已表达）标装饰语义
            contentDescription = null,
            tint = AILauncherColors.current.Accent,
            modifier = Modifier
                .size(64.dp)
                .semantics { invisibleToUser() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "把手机变成会干活的空间",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.current.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "说出你想干嘛，不用找 App",
            fontSize = 16.sp,
            color = AILauncherColors.current.Body,
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
    val view = LocalView.current
    // F-12：状态行随 ON_RESUME 重读——从系统设置返回即翻新，不再说谎
    val defaultDone = rememberResumeRefreshed { isDefaultLauncher(context) }
    StepScaffold(step = 1, onSkip = onSkip) {
        Text(
            text = "设为默认桌面",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.current.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "把 AI桌面 设为默认，才能替代系统桌面，接管你的 Home 键。",
            fontSize = 16.sp,
            color = AILauncherColors.current.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        StatusRow(
            done = defaultDone,
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
            view.tickHaptic()
            if (isDefaultLauncher(context)) {
                onNext()
            } else {
                IslandToasts.show("还没检测到，请先在设置中完成")
            }
        }) {
            Text("下一步", color = AILauncherColors.current.Title)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "从设置返回后，上面的状态会自动更新",
            fontSize = 12.sp,
            color = AILauncherColors.current.Hint
        )
    }
}

/** Step 3：通知读取权限 */
@Composable
private fun StepNotificationAccess(onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // F-12：状态行随 ON_RESUME 重读——从系统设置返回即翻新，不再说谎
    val accessDone = rememberResumeRefreshed { isNotificationAccessGranted(context) }
    StepScaffold(step = 2, onSkip = onSkip) {
        Text(
            text = "开启通知读取",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.current.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            // F-11：旧文案提的「正在进行时」不是现行表面名（已下线）——
            // 通知的真实去向是首页 TODAY 简报（待回复消息/提醒都从通知来）
            text = "开启后，七仔能读到新消息和提醒，把要紧的事整理进首页的 TODAY 简报，重要的事不漏掉。",
            fontSize = 16.sp,
            color = AILauncherColors.current.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        StatusRow(
            done = accessDone,
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
            view.tickHaptic()
            if (isNotificationAccessGranted(context)) {
                // 授权发生在 App 运行中，强制重绑让 onListenerConnected 立刻回调
                rebindListener(context)
                onNext()
            } else {
                IslandToasts.show("还没检测到，请先在设置中开启")
            }
        }) {
            Text("下一步", color = AILauncherColors.current.Title)
        }
    }
}

/** Step 4：完成 */
@Composable
private fun StepDone(onFinish: () -> Unit) {
    StepScaffold(step = 3, onSkip = onFinish) {
        Icon(
            Icons.Filled.Check,
            // 体检批D ⑨：装饰（下方标题「一切就绪」已表达）标装饰语义
            contentDescription = null,
            tint = AILauncherColors.current.Success,
            modifier = Modifier
                .size(64.dp)
                .semantics { invisibleToUser() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "一切就绪",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.current.Title,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            // F-11：旧文案宣称「能力视图都已准备好」——能力页早在 v0.14 单页化时
            // 已移除，引导在介绍不存在的功能；按现行表面重写（审计定案口径：
            // 意图入口 + TODAY 简报 + 应用中心）
            text = "意图入口、TODAY 简报、应用中心都已准备好。\n按 Home 键，随时回到你的 AI 空间。",
            fontSize = 16.sp,
            color = AILauncherColors.current.Body,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        PrimaryButton(text = "进入 AI桌面", onClick = onFinish)
    }
}

@Composable
private fun StatusRow(done: Boolean, doneText: String, todoText: String) {
    // F-12：状态行是活区——从设置返回、状态翻新时 TalkBack 礼貌播报新状态
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Icon(
            Icons.Filled.Check,
            // 体检批D ⑨：状态含义由同行文字（完成/待办文案不同）表达，图标标装饰语义
            contentDescription = null,
            tint = if (done) AILauncherColors.current.Success else AILauncherColors.current.Hint,
            modifier = Modifier
                .size(20.dp)
                .semantics { invisibleToUser() }
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (done) doneText else todoText,
            fontSize = 14.sp,
            color = if (done) AILauncherColors.current.Success else AILauncherColors.current.Hint
        )
    }
}
