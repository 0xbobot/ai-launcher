package com.bobot.ailauncher.ui.smartcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
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
 * 每张卡两个响应（设计稿 §4.2，449/v1.2.2，Bob 2026-11-07 晚真机 verdict）：
 * 忽略（我知道了，卡片消失、不再出现、不追问）与继续（按卡片定义的
 * 下一步跟进）。引导卡的继续＝去连接对应的来源（走 §5 已有流程）；
 * 真卡片的继续＝卡片定义的下一步动作（去还款 / 回拨 / 看预约 / 查看，
 * 走已有的深链/启动路径直接执行）。忽略真卡片即记处理完（截止点前不再
 * 出现）；忽略引导卡即永久记下这张（不是滑走，滑走只是本轮沉底）。
 *
 * 引导卡片（设计稿 §4.7，448）：真卡片为空、且引导还没看过时，叠层位置
 * 出现一叠引导卡片（而不是空着），只出现一次。点 × 整叠消失、不再出现；
 * 一张张滑过去看完也记一次；点“连接”走 §5 流程（消息先显著披露再弹
 * 系统框，通知跳系统设置页）；接上来源后对应引导卡消失，有真事项时真
 * 卡片顶掉整叠引导卡。文案 v2（449）：先讲为什么，再讲要连接哪个来源。
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
            onSkip = { card -> SmartCardRepository.skip(context, card) },
            onIgnore = { card -> SmartCardRepository.markDone(context, card) }
        )
        return
    }
    // 真卡片为空时才轮到引导卡片（而不是空着）。
    var guideSeen by remember {
        mutableStateOf(SmartCardRepository.isGuideSeen(context))
    }
    var swipedGuideIds by remember { mutableStateOf(setOf<String>()) }
    var dismissedGuideIds by remember {
        mutableStateOf(SmartCardRepository.dismissedGuideIds(context))
    }
    var guideDisclosure by remember { mutableStateOf(false) }
    val guides = visibleGuideCards(
        messageGranted = smsGranted.value,
        notificationGranted = notificationGranted.value,
        swipedIds = swipedGuideIds,
        dismissedIds = dismissedGuideIds
    )
    // 没什么可引导的（来源都已接上，或全部滑完了/忽略完了）即记一次，不再弹
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
        onIgnore = { card ->
            SmartCardRepository.dismissGuideCard(context, card)
            dismissedGuideIds += card.id
        },
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
 * 每张卡两个响应：忽略（消失、不再出现）与继续（卡片定义的下一步）。
 * onClose 非空时（引导卡片模式）× 在顶卡表头右上角、卡片内部：点一下
 * 整叠消失，不再出现；仍在叠层自己的槽位里，不另开浮层。
 *
 * 摞高怎么定（449/v1.2.2，修 448 实拍“按钮贴底被裁”）：
 * 旧实现用 onGloballyPositioned 量顶卡高 → Box 定高 → clipToBounds。
 * 量到的高度是在摞高自己的旧约束下裁过的高度：顶卡换成更高一张时，
 * Box 仍按旧矮高度约束新卡，新卡量出来还是矮值——自反馈死循环，
 * 顶卡永远长不回真实高度，排在最后的按钮行被裁（实拍：快递航班卡
 * “连接”只剩上半截，像素实测卡高 194dp、内容要 212dp）。
 * 新实现用 SubcomposeLayout：顶卡先以松约束（maxHeight = Infinity）
 * 量真实高度，摞高＝顶卡真实高＋露边，顶卡再也不受摞高约束；
 * 露边卡按顶卡真实高量（maxHeight = 顶卡高），不裁顶卡。
 */
@Composable
fun SmartCardStack(
    cards: List<SmartCard>,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    onIgnore: (SmartCard) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    if (cards.isEmpty()) return
    val top = cards[0]
    val peeks = cards.drop(1).take(2)
    Column(modifier = modifier.fillMaxWidth()) {
        // 与简报会行同款段间距（32dp，大于简报内部 24dp 段间距，8dp 网格）：
        // 卡片作为独立一段站得住。× 不再占这一行（448 的 × 行在卡外、
        // 悬在壁纸上）；× 进卡内表头（见 SmartCardView），这里两模式
        // 统一只留段间距。
        Spacer(modifier = Modifier.height(32.dp))
        SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
            val topPlaceables = subcompose(SmartCardStackSlot.Top) {
                key(top.id) {
                    TopSmartCard(
                        card = top,
                        onAction = onAction,
                        onSkip = onSkip,
                        onIgnore = onIgnore,
                        onClose = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_card_top")
                    )
                }
            }.map { it.measure(constraints.copy(maxHeight = Constraints.Infinity)) }
            val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
            val peekPlaceables = peeks.mapIndexed { index, card ->
                subcompose(SmartCardStackSlot.Peek(index)) {
                    key(card.id) {
                        SmartCardView(
                            card = card,
                            showAction = false,
                            onActionClick = {},
                            onIgnoreClick = {},
                            onCloseClick = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val scale = 0.97f - 0.03f * index
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 0.65f - 0.25f * index
                                }
                                .testTag("smart_card_peek")
                        )
                    }
                }.map { it.measure(constraints.copy(maxHeight = topHeight)) }
            }
            val stackHeight = topHeight + 8.dp.roundToPx() * peeks.size
            layout(constraints.maxWidth, stackHeight) {
                // 下面几张先放（在后面），露边在顶卡底边下方
                peekPlaceables.forEachIndexed { index, placeables ->
                    placeables.forEach {
                        it.place(0, 8.dp.roundToPx() * (index + 1))
                    }
                }
                topPlaceables.forEach { it.place(0, 0) }
            }
        }
    }
}

/** SubcomposeLayout 槽位：顶卡一个，露边卡按序号。 */
private enum class SmartCardStackSlot {
    Top, Peek0, Peek1;

    companion object {
        fun Peek(index: Int): SmartCardStackSlot =
            if (index == 0) Peek0 else Peek1
    }
}

/** 顶卡：横滑跳过（过 120dp 阈值离场沉底，不够回弹）；点动作立即执行 */
@Composable
private fun TopSmartCard(
    card: SmartCard,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    onIgnore: (SmartCard) -> Unit,
    onClose: (() -> Unit)?,
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
        onIgnoreClick = { onIgnore(card) },
        onCloseClick = onClose,
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
 * 一行两个响应：忽略（左）与继续（右，卡片定义的下一步动作）。
 * 没有标题党、没有解释行。
 * 视觉走启动器同一套玻璃卡片语言（与 Dock 面板、意图面板同款
 * glassSurface STRONG，圆角 20dp；SIMULATED 档/无玻璃环境退化为
 * 半透明暖灰 GlassCard）：448 的纯白 Material 卡片在壁纸上是一块
 * 白底大空卡，与家里其他玻璃面不协调，改掉。
 */
@Composable
private fun SmartCardView(
    card: SmartCard,
    showAction: Boolean,
    onActionClick: () -> Unit,
    onIgnoreClick: () -> Unit,
    onCloseClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .glassSurface(
                kind = GlassSurfaceKind.STRONG,
                cornerRadius = 20.dp,
                tintColor = AILauncherColors.current.Card,
                fallbackColor = AILauncherColors.current.GlassCard
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 表头：来源标签＋（引导卡）卡内 ×。× 落在卡片内部（16dp 内边距
        // 内）、48dp 触控；真卡片没有 ×，表头就是一行标签，不增高。
        // 448 的 × 是卡外单独一行、悬在壁纸上（实拍右上角卡外），改掉。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = card.sourceLabel,
                fontSize = AILauncherType.Caption.fontSize,
                color = AILauncherColors.current.Hint
            )
            if (onCloseClick != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCloseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AILauncherColors.current.Hint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAction) {
                // 忽略：我知道了，卡片消失、不再出现、不追问（设计稿 §4.2）
                TextButton(
                    onClick = onIgnoreClick,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "忽略",
                        fontSize = AILauncherType.Callout.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Hint
                    )
                }
                // 继续：卡片定义的下一步动作（去还款 / 回拨 / 看预约 /
                // 查看；引导卡是“连接”）。金色只留主操作（去 AI 味四条）。
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
                // 露边卡：按钮够不着，只留动作名作下一张的提示（与 448 同款）。
                // 露边只露 8dp，这行基本看不见，不占布局。
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
