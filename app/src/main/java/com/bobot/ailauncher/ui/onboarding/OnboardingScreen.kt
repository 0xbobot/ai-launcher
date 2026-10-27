@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.pet.HybridPet
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import com.bobot.ailauncher.util.rebindListener

// 引导批（2026-10-27，Bob 定稿）：3 屏——欢迎／权限组／完成＋默认选择。
// 默认桌面不放第 2 屏（旧 4 屏结构作废），挪到最后一页二选一；
// 权限组日历＋通知读取合一屏；“下一步”一律不门控（旧“还没检测到”
// Toast 墙全部删除）；使用介绍穿插进各屏好处句后，不单开教学页。

/** 欢迎／完成屏卡片上沿的七仔宽度（与 Dock 上沿同一身体、同一 HybridPet） */
private val GuidePetWidth = 96.dp

/** 七仔压入卡片顶缘（Dock 同款 5dp，前面图层、不被卡片盖住下半身） */
private val PetPress = 5.dp

/** 权限组标题旁的缩小七仔（只作向导，不抢内容） */
private val GuidePetSmall = 48.dp

@Composable
fun OnboardingNav(onFinish: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    when (step) {
        0 -> StepWelcome(onNext = { step = 1 }, onSkip = onFinish)
        1 -> StepPermissions(onNext = { step = 2 }, onSkip = onFinish)
        else -> StepFinish(onFinish = onFinish)
    }
}

@Composable
private fun StepScaffold(
    step: Int,
    totalSteps: Int = 3,
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
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
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
    Canvas(modifier = Modifier.size(8.dp)) {
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
        // F-10：引导主按钮统一 confirm 档；金色底用深色字（Theme.kt 口径，
        // 白字压金底可读性不够）
        onClick = { view.confirmHaptic(); onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.current.Accent)
    ) {
        Text(
            text = text,
            color = Color(0xFF1F1D1A),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
        )
    }
}

/**
 * 简报杂志风卡片（暖纸底 Card 色、宋体大标题、金色 accent、28dp 圆角）：
 * petWidth 非空时七仔坐卡片上沿——卡片下推（盒高 − 压入量），七仔后画、
 * 前面图层，脚底压进顶缘 5dp（Dock 同款“坐在上面”画法，不被盖住下半身）。
 * 引导语即卡片内容（七仔气泡样式，不另起一套视觉）；引导期的七仔只作
 * 向导、不接点按动作（语义清为空点可读“七仔”，不承诺点按行为）。
 */
@Composable
private fun GuideCard(
    petWidth: Dp?,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (petWidth != null) petWidth * (1318f / 1043f) - PetPress else 0.dp
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
        if (petWidth != null) {
            HybridPet(
                petWidth = petWidth,
                onPetTap = {},
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clearAndSetSemantics { contentDescription = "七仔" }
            )
        }
    }
}

/** 好处行：金点＋一句好处（含跟在后面的用法，不单开教学页） */
@Composable
private fun BenefitLine(text: String) {
    // 批B ③同款：颜色在组合上下文先读出再带进 Canvas（DrawScope 非组合上下文，
    // 里面读 AILauncherColors.current 会编不过）
    val dotColor = AILauncherColors.current.Accent
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(modifier = Modifier.padding(top = 8.dp).size(8.dp)) {
            drawCircle(
                color = dotColor,
                radius = size.minDimension / 2
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            color = AILauncherColors.current.Body
        )
    }
}

/** 第 1 屏：欢迎——只讲这个启动器独特的优点（Bob 23:11 方向），常规项不写 */
@Composable
private fun StepWelcome(onNext: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(step = 0, onSkip = onSkip) {
        GuideCard(petWidth = GuidePetWidth) {
            Text(
                text = "说句话，手机就帮你干活",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.current.Title,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 每行好处后面直接跟一句怎么用（Bob 23:42 方向：穿插使用介绍，
            // 不单开一页教使用；“今天要干的事”有工作感，描述要更通用，23:14）
            BenefitLine("七仔就坐在桌面上，会看你、眨眼、跟你说话，点一下就能直接问它。")
            Spacer(modifier = Modifier.height(12.dp))
            BenefitLine("想干什么，点一下首页胶囊、说一声就到，不用翻应用找图标。")
            Spacer(modifier = Modifier.height(12.dp))
            BenefitLine("首页只有你想看的，每件都能一键接着干——消息点一下直接回，会议点一下直接加入。")
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(text = "开始", onClick = onNext)
        }
    }
}

/** 第 2 屏：权限组——日历＋通知读取合一屏，各自独立、不捆绑 */
@Composable
private fun StepPermissions(onNext: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val calendarGranted = rememberPermissionState { isCalendarGranted(context) }
    val notificationGranted = rememberPermissionState { isNotificationAccessGranted(context) }
    val openCalendar = rememberCalendarPermissionOpener(calendarGranted)
    // 通知读取授权后强制重绑，让 onListenerConnected 立刻回调（旧第 3 屏做法保留）
    OnGrantedTransition(notificationGranted.value) { rebindListener(context) }
    StepScaffold(step = 1, onSkip = onSkip) {
        GuideCard(petWidth = null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HybridPet(
                    petWidth = GuidePetSmall,
                    onPetTap = {},
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "七仔" }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "让首页看到你的日程和消息",
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.current.Title
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            PermissionItem(
                benefit = "日历：首页能显示今天的会，开会前提醒你，点一下就能加入。",
                granted = calendarGranted.value,
                doneText = "日历已开启",
                todoText = "尚未开启",
                onOpen = openCalendar
            )
            Spacer(modifier = Modifier.height(20.dp))
            PermissionItem(
                benefit = "消息提醒：要回的消息整理到首页，点一下就能直接回，重要的事不漏掉。",
                granted = notificationGranted.value,
                doneText = "消息提醒已开启",
                todoText = "尚未开启",
                onOpen = { openNotificationSettings(context) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            // “下一步”无论开没开都能前进——旧门控与“还没检测到”Toast 已删，
            // 拒绝后对应内容优雅缺失（不报错、不空白卡、不反复弹）
            PrimaryButton(text = "下一步", onClick = onNext)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { view.tickHaptic(); onNext() }) {
                Text("以后再说", color = AILauncherColors.current.Title)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "不开也能继续用，只是首页少了对应的内容。",
                fontSize = 12.sp,
                color = AILauncherColors.current.Hint,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionItem(
    benefit: String,
    granted: Boolean,
    doneText: String,
    todoText: String,
    onOpen: () -> Unit
) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = benefit,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            color = AILauncherColors.current.Body
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusRow(
                done = granted,
                doneText = doneText,
                todoText = todoText
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { view.tickHaptic(); onOpen() }) {
                Text("去开启", color = AILauncherColors.current.Accent)
            }
        }
    }
}

/** 第 3 屏：完成＋默认选择（最后一页）——二选一，不强制 */
@Composable
private fun StepFinish(onFinish: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val defaultGranted = rememberPermissionState { isDefaultLauncher(context) }
    var wentToSettings by remember { mutableStateOf(false) }
    // 选完回来进桌面：从设置返回、状态翻为已设为默认时自动完成；
    // 未选返回则留在本屏，可再点“直接启动体验”。
    LaunchedEffect(defaultGranted.value) {
        if (wentToSettings && defaultGranted.value) onFinish()
    }
    StepScaffold(step = 2, onSkip = onFinish) {
        GuideCard(petWidth = GuidePetWidth) {
            Text(
                text = "一切就绪",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.current.Title,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "说句话就能找应用、办事——点胶囊或点七仔，说出你想干嘛。",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = AILauncherColors.current.Body,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "首页只有你想看的，每件都能一键接着干。",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = AILauncherColors.current.Body,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "以后改主意，随时去设置 → 权限里开关。",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = AILauncherColors.current.Body,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(text = "直接启动体验", onClick = onFinish)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = {
                view.tickHaptic()
                // 已是默认就没什么可设的，直接完成；否则去系统设置页选
                if (isDefaultLauncher(context)) {
                    onFinish()
                } else {
                    wentToSettings = true
                    openHomeSettings(context)
                }
            }) {
                Text("设置为默认", color = AILauncherColors.current.Title)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                // 诚实点：这步要在系统设置里选一下，不承诺 App 内一点就好
                text = "要在系统设置里选一下，选完回来就行",
                fontSize = 12.sp,
                color = AILauncherColors.current.Hint,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                // 收尾气泡（可选）：“按 Home 键随时回到这里”只在设为默认后
                // 成立，未设默认的路径上不得写，改说“随时回来看看”
                text = "随时回来看看。",
                fontSize = 13.sp,
                color = AILauncherColors.current.Hint,
                textAlign = TextAlign.Center
            )
        }
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
