package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import kotlinx.coroutines.delay

/**
 * 体检批 C 从 PetZone.kt 拆出：七仔说话气泡。
 *
 * v0.68.96（F-01 退役）：同文件的宠物呈现卡（PetPresentedCard 及其操作行）在
 * 本批前已无任何调用点，随退役一并删除。本文件只保留 SpeechBubble——它在
 * .94 玻璃试点批刚换上强玻璃，是七仔说话的统一呈现面；其唯一调用点位于
 * 已删除的非封面分支，当前暂无调用方，是否在封面形态补回气泡属产品决策，
 * 留待后续拍板，不在本批删除范围。
 */

/**
 * v0.47.0：七仔说话气泡——Mii 风（干净圆润白气泡+小尾巴）+ 游戏化（弹性弹出+打字机）。
 * 七仔有什么话要说，都走这里统一呈现。
 */
@Composable
internal fun SpeechBubble(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    // v0.64.5：编辑模式下点气泡退出编辑模式
    onClick: (() -> Unit)? = null,
    // 气泡子项：尾巴朝向。false = 尾巴在上、指向上方宠物（气泡在宠物下方，
    // 原口径）；true = 尾巴在下、指向下方宠物（气泡在宠物上方，宿主默认）。
    tailDown: Boolean = false
) {
    // 打字机：一字一字蹦出来（RPG 对话感）
    var shownChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shownChars = 0
        for (i in 1..text.length) {
            delay(40)
            shownChars = i
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
        ) + fadeIn(),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 尾巴：朝上（气泡在宠物下方，原口径）或朝下（气泡在宠物上方）。
            // 颜色在组合作用域先读好，Canvas 绘制 lambda 内不能读 CompositionLocal。
            val tailColor = AILauncherColors.current.Card
            @Composable
            fun Tail(down: Boolean) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(18.dp, 10.dp)
                ) {
                    val tailPath = Path().apply {
                        if (down) {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                        } else {
                            moveTo(0f, size.height)
                            lineTo(size.width, size.height)
                            lineTo(size.width / 2f, 0f)
                        }
                        close()
                    }
                    drawPath(tailPath, tailColor)
                }
            }
            if (!tailDown) Tail(down = false)
            // 玻璃试点批：气泡体改强玻璃——Box + 手动柔和阴影（与 TODAY 卡同手法，
            // v0.63.1 口径不用 Material3 Card）；模拟档 fallback 回原 Card 实色。
            // 形状（18dp）、最大宽 240dp、内边距与文字一律不动，只换材质。
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .glassSurface(
                        kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                        cornerRadius = 18.dp,
                        tintColor = AILauncherColors.current.Card,
                        fallbackColor = AILauncherColors.current.Card
                    )
                    .then(if (onClick != null) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() } else Modifier)
            ) {
                Text(
                    text = text.take(shownChars),
                    fontSize = AILauncherType.Footnote.fontSize,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            if (tailDown) Tail(down = true)
        }
    }
}

/**
 * 宠物交互重构·气泡子项：宠物的嘴——非模态气泡宿主。
 *
 * 单一驱动源：只订阅 PetRepository.speech / speechAction / glanceLanding，
 * 不分来源——任何来源的 say()（天气突变、早晨简报、会议提醒、重要通知、
 * 拖拽玩笑、扇形播报……）都经同一 speech 状态发布，宿主一律渲染。
 * 优先级：say 气泡 > 普通通知瞥视落点（say 发布时仓库已清掉落点）。
 *
 * 呈现：一件事 + 一个下一步动作（宠物不做列表、不做弹窗）。
 * 气泡本体复用 SpeechBubble（强玻璃 + 18dp 圆角 + 打字机）；本体下方挂
 * 一个动作钮（触控高保底 48dp）：有动作时显示动作文案（加入会议 / 打开
 * 会话 / 看看 / 忽略），无动作的 say 显示“知道了”仅关闭。点气泡本体也
 * 关闭（不执行动作）；点动作钮执行后关闭。
 * 展示时长沿用现有口径（字数×40ms + 2600ms + 350ms，见 speechDisplayMs）：
 * 到期宿主自动清掉 speech；用户先点动作/点气泡则立即关闭（下一步动作
 * 优先于等到期）。瞥视落点无到期时间，留到用户点掉或被新 say 取代。
 * 注视：气泡出现时宠物的注视方向由 say() 自身发布（先定向 250ms 再出
 * 气泡，现有机制），宿主不另造注视通道、不驱动注视。
 *
 * 单挂载点契约（供入口手势子项集成）：全 App 同一时刻只挂一个本宿主。
 * 家里宿主挂在 PullUpDock 外层 Box（与 Dock 宠物同一 Box、同一中轴线，
 * 贴头锚定＋拖拽跟头；气泡在 Dock 宠物上方，故 tailDown = true）；
 * 意图宿主挂在 IntentEntry 覆盖层（卡片宠物上方，同款锚定＋跟头＋滑入）。
 * PetZone 列尾挂载点已于 2026-10-27 删除，禁止两处同挂（否则同一条
 * say 出现两个气泡）；气泡在宠物上方时保持 tailDown = true，若改放
 * 宠物下方，改传 tailDown = false。
 */
@Composable
internal fun PetSpeechBubbleHost(
    modifier: Modifier = Modifier,
    tailDown: Boolean = true
) {
    val context = LocalContext.current
    val speech by PetRepository.speech.collectAsState()
    val speechAction by PetRepository.speechAction.collectAsState()
    val landing by PetRepository.glanceLanding.collectAsState()

    // say 气泡优先；无 say 时显示瞥视落点。
    val displayText = speech?.text ?: landing?.text
    val displayAction = if (speech != null) speechAction else landing?.action
    val visible = displayText != null

    // say 气泡到期自动关闭（与 speechDisplayMs 同口径）；瞥视落点不设到期。
    LaunchedEffect(speech?.id) {
        val current = speech ?: return@LaunchedEffect
        delay(speechDisplayMs(current.text))
        if (PetRepository.speech.value?.id == current.id) {
            PetRepository.clearSpeech()
        }
    }

    fun dismiss() {
        if (PetRepository.speech.value != null) PetRepository.clearSpeech()
        else PetRepository.clearGlanceLanding()
    }

    fun execute(action: PetRepository.SpeechAction) {
        when (action.kind) {
            PetRepository.SpeechActionKind.JOIN_MEETING -> {
                action.meetingEvent?.let { joinCalendarEvent(context, it) }
            }
            PetRepository.SpeechActionKind.OPEN_SESSION -> {
                val sent = try {
                    action.sessionPendingIntent?.send()
                    action.sessionPendingIntent != null
                } catch (_: Exception) {
                    false
                }
                if (!sent && action.appPackage != null) {
                    try {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage(action.appPackage)
                            ?.addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        if (launch != null) context.startActivity(launch)
                    } catch (_: Exception) { /* 打不开则只关闭气泡，不报错 */ }
                }
            }
            PetRepository.SpeechActionKind.DISMISS -> Unit
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpeechBubble(
            text = displayText ?: "",
            visible = visible,
            tailDown = tailDown,
            onClick = { dismiss() }
        )
        if (visible) {
            Spacer(modifier = Modifier.height(8.dp))
            // 唯一动作钮：触控高保底 48dp；无动作的 say 显示“知道了”仅关闭。
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(99.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(99.dp))
                    .glassSurface(
                        kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                        cornerRadius = 99.dp,
                        tintColor = AILauncherColors.current.Card,
                        fallbackColor = AILauncherColors.current.Card
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        displayAction?.let { execute(it) }
                        dismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayAction?.label ?: "知道了",
                    fontSize = AILauncherType.Footnote.fontSize,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
