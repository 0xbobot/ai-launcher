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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.bobot.ailauncher.data.SmartCardAction
import com.bobot.ailauncher.data.SmartCardActions
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.visibleGuideCards
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.settings.OnGrantedTransition
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.settings.isSmsGranted
import com.bobot.ailauncher.ui.settings.openNotificationSettings
import com.bobot.ailauncher.ui.settings.rememberPermissionState
import com.bobot.ailauncher.ui.settings.rememberSmsPermissionOpener
import com.bobot.ailauncher.util.rebindListener
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
 *
 * 引导卡片（设计稿 §4.7，448）：真卡片为空、且引导还没看过时，叠层位置
 * 出现一叠引导卡片（而不是空着），只出现一次。点 × 整叠消失、不再出现；
 * 一张张滑过去看完也记一次；点“连接”走 §5 流程（消息先显著披露再弹
 * 系统框，通知跳系统设置页）；接上来源后对应引导卡消失，有真事项时真
 * 卡片顶掉整叠引导卡。
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
    // 引导卡片要用的权限状态与 opener：必须放在下面的提前返回之前——
    // remember 调用在重组间顺序要稳定，不能只在真卡片为空的分支里才跑。
    val smsGranted = rememberPermissionState { isSmsGranted(context) }
    val notificationGranted =
        rememberPermissionState { isNotificationAccessGranted(context) }
    val openSms = rememberSmsPermissionOpener(smsGranted)
    // 消息刚接上就立即重读短信（不然真卡片要等下次回桌面才出现、顶不掉引导卡）
    OnGrantedTransition(smsGranted.value) {
        SmartCardRepository.refreshSms(context, force = true)
    }
    // 应用运行中去设置里开通知读取权限时系统不回调，在这里重绑监听
    OnGrantedTransition(notificationGranted.value) { rebindListener(context) }
    if (cards.isNotEmpty()) {
        SmartCardStack(
            cards = cards,
            onAction = { card ->
                val opened = SmartCardActions.performAction(context, card)
                if (!opened) IslandToasts.show("没能打开")
                opened
            },
            onSkip = { card -> SmartCardRepository.skip(context, card) }
        )
        return
    }
    // 真卡片为空时才轮到引导卡片（而不是空着）。
    var guideSeen by remember {
        mutableStateOf(SmartCardRepository.isGuideSeen(context))
    }
    var swipedGuideIds by remember { mutableStateOf(setOf<String>()) }
    var guideDisclosure by remember { mutableStateOf(false) }
    val guides = visibleGuideCards(
        messageGranted = smsGranted.value,
        notificationGranted = notificationGranted.value,
        swipedIds = swipedGuideIds
    )
    // 没什么可引导的（来源都已接上，或全部滑完了）即记一次，不再弹
    LaunchedEffect(guides.isEmpty(), guideSeen) {
        if (guides.isEmpty() && !guideSeen) {
            SmartCardRepository.markGuideSeen(context)
            guideSeen = true
        }
    }
    if (guideSeen || guides.isEmpty()) return
    SmartCardStack(
        cards = guides,
        onAction = { card ->
            when ((card.action as? SmartCardAction.Connect)?.connectorKey) {
                UiPrefs.CONNECTOR_MESSAGE -> guideDisclosure = true
                UiPrefs.CONNECTOR_NOTIFICATION -> openNotificationSettings(context)
                else -> Unit
            }
            true
        },
        onSkip = { card -> swipedGuideIds += card.id },
        onClose = {
            SmartCardRepository.markGuideSeen(context)
            guideSeen = true
        }
    )
    if (guideDisclosure) {
        // 显著披露（§5.4，同设置页“接上消息”原文）：先讲清要什么、为什么，
        // 用户点“同意”后再弹系统框；不要另起一套。
        AlertDialog(
            onDismissRequest = { guideDisclosure = false },
            title = {
                Text(
                    text = "接上消息",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "要读短信里的账单和体检预约提醒。只读近 7 天的短信，只解析账单、预约这两类，解析完不存原文、不发到别处。",
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = { guideDisclosure = false; openSms() }) {
                    Text(
                        text = "同意",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { guideDisclosure = false }) {
                    Text(
                        text = "取消",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Hint
                    )
                }
            }
        )
    }
}

/**
 * 卡片叠层：最上面一张完整显示，下面两张露个边（8dp 一层）。
 * 点动作立即执行（不拖 250ms 动画等开应用）；横滑跳过播离场动画再沉底。
 * onClose 非空时（引导卡片模式）在叠层上方放一个 ×：点一下整叠消失，
 * 不再出现；仍在叠层自己的槽位里，不另开浮层。
 */
@Composable
fun SmartCardStack(
    cards: List<SmartCard>,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
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
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AILauncherColors.current.Hint
                    )
                }
            }
        } else {
            // 与简报会行同款段间距（32dp，大于简报内部 24dp 段间距，8dp 网格）：
            // 卡片作为独立一段站得住。
            Spacer(modifier = Modifier.height(32.dp))
        }
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
