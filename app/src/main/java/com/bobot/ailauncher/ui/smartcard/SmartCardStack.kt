package com.bobot.ailauncher.ui.smartcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.bobot.ailauncher.data.CONNECTED_FEEDBACK_MS
import com.bobot.ailauncher.data.ConnectedFeedback
import com.bobot.ailauncher.data.GUIDE_VERSION
import com.bobot.ailauncher.data.buildConnectorStates
import com.bobot.ailauncher.data.celebrationLevelText
import com.bobot.ailauncher.data.connectedFeedback
import com.bobot.ailauncher.data.connectedSourceCount
import com.bobot.ailauncher.data.connectorKeyForGuideId
import com.bobot.ailauncher.data.guideCards
import com.bobot.ailauncher.data.guideIdsForConnector
import com.bobot.ailauncher.data.isGuideConnectorGranted
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SmartCard
import com.bobot.ailauncher.data.SmartCardAction
import com.bobot.ailauncher.data.SmartCardActions
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.shouldShowGuideVersion
import com.bobot.ailauncher.data.visibleGuideCards
import com.bobot.ailauncher.ui.components.AppBadge
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.settings.OnGrantedTransition
import com.bobot.ailauncher.ui.settings.isCalendarGranted
import com.bobot.ailauncher.ui.settings.isContactsGranted
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.settings.isSmsGranted
import com.bobot.ailauncher.ui.settings.openNotificationSettings
import com.bobot.ailauncher.ui.settings.rememberCalendarPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberContactsPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberPermissionState
import com.bobot.ailauncher.ui.settings.rememberSmsPermissionOpener
import com.bobot.ailauncher.util.rebindListener
import kotlinx.coroutines.delay
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
 * 引导卡片（设计稿 §4.7，448；v3 按权限重做，455；换版重看，456）：
 * 真卡片为空、且本版引导还没看过时，叠层位置出现一叠引导卡片
 * （而不是空着），每版引导只出现一次、不是终身一次——引导内容带
 * 版本号，换版后自动重看一次（452 记下的旧版 guide_seen 视为已看 v2，
 * 不挡 v3）。
 * 按权限分五张（消息、通知、日历、联系人、电话拨号器）；点 × 整叠消失、
 * 不再出现；一张张滑过去看完也记一次；点“连接”走 §5 流程（消息/联系人
 * 先显著披露再弹系统框，日历直接弹，通知跳系统设置页）；接上某个权限后
 * 对应卡先显示“已连接”＋接下来会发生什么（3 秒）再消失，其他引导卡
 * 留着；有真事项时真卡片顶掉整叠引导卡。文案 v3：每张讲三件事——这是
 * 什么、为什么要连、连上之后会发生什么。
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
    val calendarGranted = rememberPermissionState { isCalendarGranted(context) }
    val contactsGranted = rememberPermissionState { isContactsGranted(context) }
    val openSms = rememberSmsPermissionOpener(smsGranted)
    val openCalendar = rememberCalendarPermissionOpener(calendarGranted)
    val openContacts = rememberContactsPermissionOpener(contactsGranted)
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
    // 引导内容带版本号（设计稿 §4.7，456）：存的版本比当前旧（或无记录）
    // 就出现；看完/关掉记当前版本，同一版本不再重复出现。
    var seenGuideVersion by remember {
        mutableStateOf(SmartCardRepository.guideSeenVersion(context))
    }
    val guideSeen = !shouldShowGuideVersion(seenGuideVersion)
    var swipedGuideIds by remember { mutableStateOf(setOf<String>()) }
    var dismissedGuideIds by remember {
        mutableStateOf(SmartCardRepository.dismissedGuideIds(context))
    }
    // 显著披露要针对哪个连接器（消息/联系人先讲清再弹系统框，日历直接弹、
    // 通知/电话拨号器跳系统设置页）；null＝没在弹。
    var disclosureConnector by remember { mutableStateOf<String?>(null) }
    // 用户在叠层里点了“继续”的卡（等授权结果）；接上后先显示“已连接”
    // 反馈再离开。只对“用户在叠层里点了继续”的卡给反馈；在设置里接上
    // 的权限，其卡片直接消失、不补反馈。
    var pendingConnectIds by remember { mutableStateOf(setOf<String>()) }
    // 用户点“继续”那一刻的真实已接源个数（457 的 connectedSourceCount，
    // 同一处计数）：接上后拿它跟新个数一减，就是本次智能涨了多少。
    var pendingConnectLevels by remember { mutableStateOf(mapOf<String, Int>()) }
    // 正在显示“已连接”庆祝反馈的卡（3 秒后离开）：状态里带动效触发标志
    // celebrate 与等级增量 levelDelta（见 ConnectedFeedback），不是光一个 id。
    var connectedFeedbacks by remember {
        mutableStateOf(mapOf<String, ConnectedFeedback>())
    }
    val connectedIds: Set<String> = connectedFeedbacks.keys
    val guides = visibleGuideCards(
        messageGranted = smsGranted.value,
        notificationGranted = notificationGranted.value,
        calendarGranted = calendarGranted.value,
        contactsGranted = contactsGranted.value,
        swipedIds = swipedGuideIds,
        dismissedIds = dismissedGuideIds
    )
    // 接上某个权限后，对应引导卡先显示“已连接”＋接下来会发生什么（3 秒），
    // 再消失——用户点了连接不能是懵的（设计稿 §4.7 v3）。
    LaunchedEffect(
        smsGranted.value, notificationGranted.value,
        calendarGranted.value, contactsGranted.value
    ) {
        val newly = pendingConnectIds.filter { id ->
            val key = connectorKeyForGuideId(id) ?: return@filter false
            isGuideConnectorGranted(
                key,
                smsGranted.value, notificationGranted.value,
                calendarGranted.value, contactsGranted.value
            )
        }
        if (newly.isNotEmpty()) {
            // 等级数读真实已接源个数（457 的 connectedSourceCount，同一处
            // 计数）：接通知时电话拨号器开关已开，等级一次涨 2，增量如实算，
            // 不写死 +1。
            val levelAfter = connectedSourceCount(buildConnectorStates(context))
            pendingConnectIds -= newly.toSet()
            connectedFeedbacks += newly.associateWith { id ->
                val before = pendingConnectLevels[id] ?: (levelAfter - 1)
                val card = guideCards().find { it.id == id }!!
                connectedFeedback(card, levelAfter, before)
            }
            pendingConnectLevels -= newly.toSet()
        }
    }
    // “已连接”反馈停留 3 秒后离开（记永久忽略，不再出现）。
    LaunchedEffect(connectedIds) {
        if (connectedIds.isNotEmpty()) {
            delay(CONNECTED_FEEDBACK_MS)
            val leaving = connectedIds
            leaving.forEach { id ->
                guideCards().find { it.id == id }?.let {
                    SmartCardRepository.dismissGuideCard(context, it)
                }
            }
            dismissedGuideIds += leaving
            connectedFeedbacks = emptyMap()
        }
    }
    val connectedCards = connectedFeedbacks.values.map { it.card }
    // 反馈卡排在最前面（刚接上的事立刻看得见），其余引导卡留着。
    val displayed = connectedCards + guides
    // 叠层空下来才记当前版本（看完、忽略完、或全部接上且反馈已给完）：
    // 每版引导只出现一次。注意不要按 guides.isEmpty() 记——接上最后一个
    // 权限时 guides 先空、反馈卡还在显示，那时记下会把反馈也吞掉（452 的
    // 问题：连一个权限整叠全消失，根因有二：①已接上的权限被静默过滤——
    // Bob 设备上通知在 452 测试前已接上，首屏只剩两张消息卡，连上消息即
    // 清空；②这行 latch 在引导列表一空就记 guide_seen，全程没有任何反馈，
    // 用户点了连接是懵的）。
    LaunchedEffect(displayed.isEmpty(), guideSeen) {
        if (displayed.isEmpty() && !guideSeen) {
            SmartCardRepository.markGuideSeen(context)
            seenGuideVersion = GUIDE_VERSION
        }
    }
    if (guideSeen || displayed.isEmpty()) return
    SmartCardStack(
        cards = displayed,
        connectedFeedbacks = connectedFeedbacks,
        onAction = { card ->
            val key = (card.action as? SmartCardAction.Connect)?.connectorKey
                ?: return@SmartCardStack true
            pendingConnectIds += card.id
            // 点“继续”这一刻的真实等级：接上后拿它算增量（见上）。
            pendingConnectLevels +=
                card.id to connectedSourceCount(buildConnectorStates(context))
            when (key) {
                UiPrefs.CONNECTOR_MESSAGE ->
                    disclosureConnector = UiPrefs.CONNECTOR_MESSAGE
                UiPrefs.CONNECTOR_CONTACTS ->
                    disclosureConnector = UiPrefs.CONNECTOR_CONTACTS
                UiPrefs.CONNECTOR_CALENDAR -> openCalendar()
                UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE ->
                    openNotificationSettings(context)
                else -> Unit
            }
            true
        },
        onSkip = { card ->
            if (card.id in connectedIds) {
                // 滑走“已连接”反馈卡＝直接记永久忽略，不再出现
                SmartCardRepository.dismissGuideCard(context, card)
                dismissedGuideIds += card.id
                connectedFeedbacks -= card.id
                pendingConnectLevels -= card.id
            } else {
                swipedGuideIds += card.id
            }
        },
        onIgnore = { card ->
            SmartCardRepository.dismissGuideCard(context, card)
            dismissedGuideIds += card.id
        },
        onClose = {
            SmartCardRepository.markGuideSeen(context)
            seenGuideVersion = GUIDE_VERSION
        }
    )
    // 显著披露（§5.4，同设置页原文）：先讲清要什么、为什么，用户点
    // “同意”后再弹系统框；不要另起一套。点“取消”或划掉＝这次不连，
    // 对应卡片留在叠层里（pending 清掉，不给“已连接”反馈）。
    fun cancelDisclosure(key: String) {
        disclosureConnector = null
        val ids = guideIdsForConnector(key).toSet()
        pendingConnectIds -= ids
        pendingConnectLevels -= ids
    }
    if (disclosureConnector == UiPrefs.CONNECTOR_MESSAGE) {
        AlertDialog(
            onDismissRequest = { cancelDisclosure(UiPrefs.CONNECTOR_MESSAGE) },
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
                TextButton(onClick = {
                    disclosureConnector = null
                    openSms()
                }) {
                    Text(
                        text = "同意",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    cancelDisclosure(UiPrefs.CONNECTOR_MESSAGE)
                }) {
                    Text(
                        text = "取消",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Hint
                    )
                }
            }
        )
    }
    if (disclosureConnector == UiPrefs.CONNECTOR_CONTACTS) {
        AlertDialog(
            onDismissRequest = { cancelDisclosure(UiPrefs.CONNECTOR_CONTACTS) },
            title = {
                Text(
                    text = "接上联系人",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "要把未接来电的号码显示成名字。只按号码查姓名，不读其他联系人信息。",
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosureConnector = null
                    openContacts()
                }) {
                    Text(
                        text = "同意",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    cancelDisclosure(UiPrefs.CONNECTOR_CONTACTS)
                }) {
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
 * 待办数角标（本批，Bob 2026-11-07 晚定）：叠层右上角压住顶卡角，
 * 显示还剩几张待办卡片（当前叠层张数），与 Dock/应用中心同一套
 * AppBadge；0 张时区域清空、角标不显示。
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
    onClose: (() -> Unit)? = null,
    // 正在显示“已连接”庆祝反馈的卡（第八批）：不画动作行（没有“连接”可点），
    // 只显示“已连接”＋接下来会发生什么，外加庆祝形态（对勾弹入＋金色等级行
    // ＋整卡轻弹，见 SmartCardView）。反馈卡顶掉的是它自己的引导卡，不多占
    // 一张待办，角标计数不变（cards.size 本就是替换后的张数）。
    connectedFeedbacks: Map<String, ConnectedFeedback> = emptyMap()
) {
    if (cards.isEmpty()) return
    val top = cards[0]
    val connectedIds: Set<String> = connectedFeedbacks.keys
    val peeks = cards.drop(1).take(2)
    Column(modifier = modifier.fillMaxWidth()) {
        // 与简报会行同款段间距（32dp，大于简报内部 24dp 段间距，8dp 网格）：
        // 卡片作为独立一段站得住。× 不再占这一行（448 的 × 行在卡外、
        // 悬在壁纸上）；× 进卡内表头（见 SmartCardView），这里两模式
        // 统一只留段间距。
        Spacer(modifier = Modifier.height(32.dp))
        // 待办数角标（Bob 2026-11-07 晚定）：叠层右上角、压住顶卡角，显示
        // 还剩几张待办卡片；与 Dock/应用中心同一套 AppBadge（红 pill 白字，
        // 1–99 显示数字、≥100 显示 99+，0 张不画）。计数＝当前叠层卡片张数
        // （真卡或可见引导卡），随处理/跳过/新增实时变；0 张时本函数已提前
        // 返回、区域清空，角标不显示。纯视觉覆盖层，不消费触摸（× 在卡内
        // 16dp 处，角标贴角不重叠；即使贴到也不拦点按）。
        Box(modifier = Modifier.fillMaxWidth()) {
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val topPlaceables = subcompose(SmartCardStackSlot.Top) {
                    key(top.id) {
                        TopSmartCard(
                            card = top,
                            onAction = onAction,
                            onSkip = onSkip,
                            onIgnore = onIgnore,
                            onClose = onClose,
                            hideActionRow = top.id in connectedIds,
                            celebration = connectedFeedbacks[top.id],
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
                                hideActionRow = card.id in connectedIds,
                                celebration = connectedFeedbacks[card.id],
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
            // 待办数角标：与 Dock 图标角标同款贴法（右上角、微微压住角），
            // 高度 20dp（与 D1 同档）。AppBadge 自身在 count<=0 时不画。
            AppBadge(
                count = cards.size,
                height = 20.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
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
    modifier: Modifier = Modifier,
    hideActionRow: Boolean = false,
    celebration: ConnectedFeedback? = null
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
        hideActionRow = hideActionRow,
        celebration = celebration,
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
 * 449 实拍 verdict（Bob 2026-11-07 晚）：玻璃太透、山体壁纸与露边卡
 * 的字都透过顶卡，看着乱——字先保证可读，再谈透。本批在 glassSurface
 * 之上加一层实色衬底（GlassCardStrong，0.88 不透明度，同一 20dp 圆角），
 * 盖住玻璃补丁与身后露边卡的文字、落在文字之下；全局 GlassPolicy 不动，
 * 露边卡 alpha（0.65/0.40）也不动——顶卡变实后露边字即被挡住。
 */
@Composable
private fun SmartCardView(
    card: SmartCard,
    showAction: Boolean,
    onActionClick: () -> Unit,
    onIgnoreClick: () -> Unit,
    onCloseClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // “已连接”反馈卡：不画动作行（忽略/连接两个按钮与露边提示都不画），
    // 只留来源标签＋“已连接”＋接下来会发生什么。
    hideActionRow: Boolean = false,
    // “已连接”庆祝反馈（第八批，设计稿 §4.7）：非空时卡片换成庆祝形态——
    // 金色对勾弹入＋金色等级行（“智能 +1 · 已接 3/5 个源”）＋整卡轻弹。
    // 颜色只用已有的主操作金 Accent / AccentSoft，不引新色；图标是线性
    // Check，不用 emoji；尺寸走 8dp 网格（40dp 圆、8dp 间距）；减动效时
    // 直接落到终态，不播动画。文案仍按 Bob 的两条写。
    celebration: ConnectedFeedback? = null
) {
    val shape = RoundedCornerShape(20.dp)
    // 庆祝动效：整卡从 0.94 轻弹到 1（带回弹的弹簧），对勾从 0 弹入到 1。
    // remember 以 celebration 为键：反馈出现是同一张卡换形态（id 不变，
    // 不会重挂载），不 key 住就拿不到 0.94 的初值、动画播不出来。
    val reduceMotion = rememberReduceMotion()
    val pop = remember(celebration) {
        Animatable(if (celebration == null) 1f else 0.94f)
    }
    val checkScale = remember(celebration) {
        Animatable(if (celebration == null) 1f else 0f)
    }
    LaunchedEffect(celebration) {
        if (celebration?.celebrate == true && !reduceMotion) {
            launch {
                pop.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                delay(60)
                checkScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            pop.snapTo(1f)
            checkScale.snapTo(1f)
        }
    }
    Column(
        modifier = modifier
            .clip(shape)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .glassSurface(
                kind = GlassSurfaceKind.STRONG,
                cornerRadius = 20.dp,
                tintColor = AILauncherColors.current.Card,
                fallbackColor = AILauncherColors.current.GlassCard
            )
            .background(
                color = AILauncherColors.current.GlassCardStrong,
                shape = shape
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
        // 庆祝行（第八批）：金色对勾＋等级变化。只在“已连接”反馈卡上画，
        // 引导卡与真卡片一律没有——一眼就能看出这张不一样。
        if (celebration != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = checkScale.value
                            scaleY = checkScale.value
                        }
                        .clip(CircleShape)
                        .background(color = AILauncherColors.current.AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已连接",
                        tint = AILauncherColors.current.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = celebrationLevelText(
                        celebration.level, celebration.previousLevel
                    ),
                    fontSize = AILauncherType.Callout.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Accent
                )
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
        if (!hideActionRow) {
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
}
