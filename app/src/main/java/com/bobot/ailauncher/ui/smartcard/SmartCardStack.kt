package com.bobot.ailauncher.ui.smartcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SmartCard
import com.bobot.ailauncher.data.SmartCardActions
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 智能卡片叠层宿主（第一阶段，447/v1.2.0，设计稿 §4）：
 * 收集卡片流、回桌面与分钟翻页时重算；没有卡时什么都不渲染、不留占位。
 *
 * 叠层交互：多张卡叠成一摞，最上面一张完整显示、下面露个边（最多露两张）；
 * 点下一步动作算处理完一张、卡片消失；横滑跳过沉到摞底、轮到最后再出现；
 * 全部处理完或跳过后区域清空、不留占位条。
 */
@Composable
fun SmartCardStackHost() {
    val context = LocalContext.current
    val cards by SmartCardRepository.cards.collectAsState()
    val notifications by NotificationRepository.notifications.collectAsState()
    val nowMillis by rememberNowMillis()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // 回桌面：重读短信（节流 5 分钟）＋重算可见卡片
    LaunchedEffect(resumeTick) {
        SmartCardRepository.refreshSms(context)
        SmartCardRepository.refresh(context, notifications, System.currentTimeMillis())
    }
    // 通知变化 / 分钟翻页：重算窗口与顺序（过期卡片最迟 1 分钟内摘掉）
    LaunchedEffect(notifications, nowMillis) {
        SmartCardRepository.refresh(context, notifications, nowMillis)
    }
    if (cards.isEmpty()) return
    SmartCardStack(
        cards = cards,
        onAction = { card ->
            val opened = SmartCardActions.performAction(context, card)
            if (!opened) IslandToasts.show("没能打开")
            opened
        },
        onSkip = { card -> SmartCardRepository.skip(context, card) }
    )
}

/**
 * 卡片叠层：最上面一张完整显示，下面两张露个边（8dp 一层）。
 * 点动作立即执行（不拖 250ms 动画等开应用）；横滑跳过播离场动画再沉底。
 */
@Composable
fun SmartCardStack(
    cards: List<SmartCard>,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    val top = cards[0]
    val peeks = cards.drop(1).take(2)
    val density = LocalDensity.current
    var topHeightPx by remember { mutableIntStateOf(0) }
    val topHeightDp = with(density) {
        if (topHeightPx > 0) topHeightPx.toDp() else 160.dp
    }
    Column(modifier = modifier.fillMaxWidth()) {
        // 与简报会行同款段间距（32dp，大于简报内部 24dp 段间距，8dp 网格）：
        // 卡片作为独立一段站得住。
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 摞高＝顶卡高＋露边（8dp 一层）；clipToBounds 保证露边恒为 8dp，
                // 不受下面卡片自身高度影响。
                .height(topHeightDp + 8.dp * peeks.size)
                .clipToBounds()
        ) {
            // 下面几张先画（在后面），露边在顶卡底边下方
            peeks.forEachIndexed { index, card ->
                val layer = index + 1
                SmartCardView(
                    card = card,
                    showAction = false,
                    onActionClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (8 * layer).dp)
                        .graphicsLayer {
                            val scale = 0.97f - 0.03f * index
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.65f - 0.25f * index
                        }
                        .testTag("smart_card_peek")
                )
            }
            key(top.id) {
                TopSmartCard(
                    card = top,
                    onAction = onAction,
                    onSkip = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { topHeightPx = it.size.height }
                        .testTag("smart_card_top")
                )
            }
        }
    }
}

/** 顶卡：横滑跳过（过 120dp 阈值离场沉底，不够回弹）；点动作立即执行 */
@Composable
private fun TopSmartCard(
    card: SmartCard,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val offsetX = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var skipping by remember { mutableStateOf(false) }
    val thresholdPx = with(density) { 120.dp.toPx() }

    SmartCardView(
        card = card,
        showAction = true,
        onActionClick = { onAction(card) },
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                this.alpha = alpha.value
            }
            .pointerInput(card.id, skipping) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (skipping) return@detectHorizontalDragGestures
                        val x = offsetX.value
                        if (abs(x) > thresholdPx) {
                            skipping = true
                            val dir = if (x > 0) 1f else -1f
                            if (reduceMotion) {
                                onSkip(card)
                            } else {
                                scope.launch {
                                    launch {
                                        offsetX.animateTo(
                                            dir * 1200f,
                                            tween(MotionTokens.Medium)
                                        )
                                    }
                                    launch {
                                        alpha.animateTo(
                                            0f, tween(MotionTokens.Medium)
                                        )
                                    }
                                    onSkip(card)
                                }
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, tween(MotionTokens.Fast))
                            }
                        }
                    },
                    onDragCancel = {
                        if (!skipping) {
                            scope.launch {
                                offsetX.animateTo(0f, tween(MotionTokens.Fast))
                            }
                        }
                    }
                ) { _, dragAmount ->
                    if (skipping) return@detectHorizontalDragGestures
                    // detectHorizontalDragGestures 的 dragAmount 本身就是横向位移（Float）
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                }
            }
    )
}

/**
 * 单张卡片：来源标签＋一句话事实（含截止点）＋（可选）一行准备清单＋
 * 一个下一步动作按钮。没有标题党、没有解释行。
 * 视觉走 App 实际主题：白卡片＋20dp 圆角＋柔和阴影（与设置页卡片同款）。
 */
@Composable
private fun SmartCardView(
    card: SmartCard,
    showAction: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .shadow(
                8.dp, RoundedCornerShape(20.dp),
                spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = card.sourceLabel,
                fontSize = AILauncherType.Caption.fontSize,
                color = AILauncherColors.current.Hint
            )
            Text(
                text = card.fact,
                fontSize = AILauncherType.Headline.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.current.Title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            card.prep?.let { prep ->
                Text(
                    text = prep,
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Body,
                    lineHeight = AILauncherType.Footnote.lineHeight
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAction) {
                    TextButton(
                        onClick = onActionClick,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = card.actionLabel,
                            fontSize = AILauncherType.Callout.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Accent
                        )
                    }
                } else {
                    Text(
                        text = card.actionLabel,
                        fontSize = AILauncherType.Callout.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Accent,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
