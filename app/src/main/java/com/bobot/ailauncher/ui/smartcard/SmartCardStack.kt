package com.bobot.ailauncher.ui.smartcard

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.CONNECTED_FEEDBACK_MS
import com.bobot.ailauncher.data.ConnectedFeedback
import com.bobot.ailauncher.data.GUIDE_VERSION
import com.bobot.ailauncher.data.SMART_CARD_DISMISS_LABEL
import com.bobot.ailauncher.data.SmartCardKind
import com.bobot.ailauncher.data.buildConnectorStates
import com.bobot.ailauncher.data.celebrationLevelText
import com.bobot.ailauncher.data.connectedFeedback
import com.bobot.ailauncher.data.connectedSourceCount
import com.bobot.ailauncher.data.connectorKeyForGuideId
import com.bobot.ailauncher.data.createCalendarReminder
import com.bobot.ailauncher.data.guideCards
import com.bobot.ailauncher.data.guideIdsForConnector
import com.bobot.ailauncher.data.isGuideConnectorGranted
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SmartCard
import com.bobot.ailauncher.data.SmartCardAction
import com.bobot.ailauncher.data.SmartCardActions
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.currentGuideCard
import com.bobot.ailauncher.data.shouldShowGuideVersion
import com.bobot.ailauncher.data.visibleGuideCards
import com.bobot.ailauncher.ui.components.AppBadge
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.BackdropKind
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.LocalGlassBackdrop
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.settings.OnGrantedTransition
import com.bobot.ailauncher.ui.settings.isCalendarGranted
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.settings.isSmsGranted
import com.bobot.ailauncher.ui.settings.isWriteCalendarGranted
import com.bobot.ailauncher.ui.settings.openNotificationSettings
import com.bobot.ailauncher.ui.settings.rememberCalendarPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberPermissionState
import com.bobot.ailauncher.ui.settings.rememberSmsPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberWriteCalendarPermissionOpener
import com.bobot.ailauncher.util.rebindListener
import kotlinx.coroutines.delay
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 智能卡片叠层宿主（第一阶段，447/v1.2.0，设计稿 §4）：
 * 收集卡片流、回桌面与分钟翻页时重算；没有卡时什么都不渲染、不留占位。
 *
 * 叠层交互：多张卡叠成一摞，最上面一张完整显示、下面露个边（最多露两张）；
 * 点下一步动作算处理完一张、卡片消失——除了第十九批的有时间段提醒
 * （日历/预约）：时间段内点动作不摘卡、卡片留在摞里，只有「我知道了」
 * 或过了截止点才摘；横滑跳过播离场动画——用户的事项
 * 提醒（账单/预约/日历/未接来电/快递航班）沉到摞底，看完其他卡就轮到它，
 * 只有点「我知道了」才丢（第十四批，Bob 2026-11-10 23:43 定，取代 482
 * 「本轮收起来」）；应用推的提醒划过就丢。全部处理完或跳过后区域清空、
 * 不留占位条。
 *
 * 每张卡两个响应（设计稿 §4.2，449/v1.2.2，Bob 2026-11-07 晚真机 verdict；
 * 第十一批 Bob 2026-11-09 定文案）：我知道了（卡片消失、不再出现、不追问）
 * 与下一步（按卡片定义的下一步跟进）。引导卡的下一步＝去连接对应的来源
 * （走 §5 已有流程）；真卡片的下一步＝卡片定义的下一步动作（去还款 / 回拨 /
 * 看预约 / 查看，走已有的深链/启动路径直接执行；预约卡是“帮我建提醒”，
 * 先拿 WRITE_CALENDAR 授权再写系统日历）。我知道了真卡片即记处理完
 * （截止点前不再出现）；我知道了引导卡即被拒一次（放队尾，第十四批
 * 轮换推），不是永久记下。
 *
 * 引导卡片（设计稿 §4.7，448；v3 按权限重做，455；换版重看，456）：
 * 真卡片为空、且本版引导还没看过时，叠层位置出现引导卡片
 * （而不是空着），每版引导只出现一次、不是终身一次——引导内容带
 * 版本号，换版后自动重看一次（452 记下的旧版 guide_seen 视为已看 v2，
 * 不挡 v3）。
 * 按权限分四张（消息、通知、日历、电话拨号器；第十四批删联系人）；点 ×
 * 整叠消失、不再出现；单卡提醒、轮换推（第十四批，Bob 2026-11-10 23:07
 * 定）：一次只推一张，接上一个来源后继续推下一张；点「我知道了」或横滑
 * ＝被拒一次，放队尾、其他来源推完后再回来一次，再拒就本版不再推（等引
 * 导换版）；点“连接”走 §5 流程（消息先显著披露再弹系统框，日历直接弹，
 * 通知跳系统设置页）；接上某个权限后对应卡先显示“已连接”＋接下来会发生
 * 什么（3 秒）再消失，其他引导卡留着；有真事项时真卡片顶掉整叠引导卡。
 * 文案 v3：每张讲三件事——这是什么、为什么要连、连上之后会发生什么。
 */
@Composable
fun SmartCardStackHost() {
    val context = LocalContext.current
    val cards by SmartCardRepository.cards.collectAsState()
    val prepById by SmartCardRepository.prepById.collectAsState()
    val skipEpoch by SmartCardRepository.skipEpoch.collectAsState()
    val notifications by NotificationRepository.notifications.collectAsState()
    val nowMillis by rememberNowMillis()
    // 冷启动就绪门（第二十五批，问题 1）：卡片叠层不在空桌面上单独先蹦——
    // 与 505 的 Dock 门同一信号（AppCatalog 快照到位才算首页首帧就绪）。
    // 505 的 Dock 只在 catalog != null 时画；卡片比时钟还早（1.75 秒先蹦、
    // 时钟 2.0 秒、Dock 2.75 秒才齐），就是因为叠层没有任何就绪门、仓库
    // 第一次 refresh 回来就直接蹦出来。门只挡冷启动首帧：快照只在首建时
    // 为 null，重建是换值不断流，到位后不再回落，门开后不再关。
    // 白闪（1.25 秒）与空壁纸（1.5 秒）那两段是启动窗口过渡：主题已是
    // 透明 windowBackground＋windowShowWallpaper（v0.37.2 刻意让系统壁纸
    // 直接透出），白闪是系统在透明窗口后补的启动窗口底色，应用侧改不了
    // 主题、不动它；门解决的是“卡片在空桌面上单独先蹦”这一段。
    val catalog by AppCatalog.snapshot.collectAsState()
    val homeReady = catalog != null
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
    // 准备清单（第十二批）：可见卡片变化时，为需要大模型的那两类卡
    // （预约/日程）请求一次准备清单；仓库内做本地过滤＋脱敏＋每卡一次，
    // 结果回来经 prepById 填上，失败静默、卡片先按事实＋动作显示。
    LaunchedEffect(cards) {
        for (card in cards) SmartCardRepository.requestPrep(context, card)
    }
    // 引导卡片要用的权限状态与 opener：必须放在下面的提前返回之前——
    // remember 调用在重组间顺序要稳定，不能只在真卡片为空的分支里才跑。
    val smsGranted = rememberPermissionState { isSmsGranted(context) }
    val notificationGranted =
        rememberPermissionState { isNotificationAccessGranted(context) }
    val calendarGranted = rememberPermissionState { isCalendarGranted(context) }
    val openSms = rememberSmsPermissionOpener(smsGranted)
    val openCalendar = rememberCalendarPermissionOpener(calendarGranted)
    // 消息刚接上就立即重读短信（不然真卡片要等下次回桌面才出现、顶不掉引导卡）
    OnGrantedTransition(smsGranted.value) {
        SmartCardRepository.refreshSms(context, force = true)
    }
    // 应用运行中去设置里开通知读取权限时系统不回调，在这里重绑监听
    OnGrantedTransition(notificationGranted.value) { rebindListener(context) }
    // 预约卡“帮我建提醒”的 WRITE_CALENDAR 授权（第十一批）：状态与 opener
    // 必须放在下面的提前返回之前——remember 调用在重组间顺序要稳定。
    // 等授权结果的卡（pendingReminderCard）：授权框里点了允许就建提醒，
    // 拒绝就退回看预约（打开短信会话）；去设置里开完回来也一样（ON_RESUME
    // 重读状态翻转为已开时 OnGrantedTransition 补建）。
    val writeCalendarGranted = rememberPermissionState { isWriteCalendarGranted(context) }
    var pendingReminderCard by remember { mutableStateOf<SmartCard?>(null) }
    var reminderDisclosureCard by remember { mutableStateOf<SmartCard?>(null) }
    fun createReminderFor(card: SmartCard) {
        val action = card.action as? SmartCardAction.CreateReminder ?: return
        val created = createCalendarReminder(context, action)
        if (created) {
            SmartCardRepository.markReminderCreated(context, card)
        } else {
            IslandToasts.show("没能建提醒")
        }
    }
    fun viewAppointment(card: SmartCard) {
        // 看预约原文：建提醒前的退回动作（短信会话或通知自己的跳转）；
        // 已建过提醒的卡，动作本身就是看预约，直接执行。
        val fallback = (card.action as? SmartCardAction.CreateReminder)?.fallback
        val opened = if (fallback != null) {
            SmartCardActions.performAction(context, card.copy(action = fallback))
        } else {
            SmartCardActions.performAction(context, card)
        }
        if (!opened) IslandToasts.show("没能打开")
    }
    val openWriteCalendar = rememberWriteCalendarPermissionOpener(
        writeCalendarGranted,
        onResult = { granted ->
            val pending = pendingReminderCard
            pendingReminderCard = null
            if (pending != null) {
                if (granted) createReminderFor(pending) else viewAppointment(pending)
            }
        }
    )
    OnGrantedTransition(writeCalendarGranted.value) {
        // 去设置里开完 WRITE_CALENDAR 回来：等授权结果的卡还在，就补建提醒。
        pendingReminderCard?.let {
            pendingReminderCard = null
            createReminderFor(it)
        }
    }
    // 建提醒的显著披露（§5.4 同款）：先讲清要什么、为什么，用户点“同意”
    // 后再弹系统框；点“取消”或划掉＝这次不建，卡片留在叠层里（pending 清掉，
    // 不退回看预约——退回只在系统框里拒绝授权时发生）。
    // 放在提前返回之前：真卡片在场时也要能弹（引导卡模式同理）。
    if (reminderDisclosureCard != null) {
        val card = reminderDisclosureCard!!
        AlertDialog(
            onDismissRequest = {
                reminderDisclosureCard = null
                pendingReminderCard = null
            },
            title = {
                Text(
                    text = "建个提醒",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "要在系统日历里为这次预约建一个提醒，到点提醒你。提醒写进系统日历，删改在日历应用里做。",
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    reminderDisclosureCard = null
                    openWriteCalendar()
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
                    reminderDisclosureCard = null
                    pendingReminderCard = null
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
    if (cards.isNotEmpty()) {
        // 就绪门（第二十五批，问题 1）：首帧没齐之前不画叠层——有真卡也
        // 先等着，不在空桌面上单独先蹦；门开后（catalog 到位）与 Dock
        // 一起出现。return 照常：真卡在场时不落到引导卡分支。
        if (homeReady) {
            SmartCardStack(
                cards = cards,
                prepById = prepById,
                skipEpoch = skipEpoch,
                onAction = { card ->
                    if (card.action is SmartCardAction.CreateReminder) {
                        // 预约卡“帮我建提醒”：已授权直接建；没授权先说明（显著披露）
                        // 再弹系统框；拒绝退回看预约（onResult 里做）。
                        if (writeCalendarGranted.value) {
                            createReminderFor(card)
                        } else {
                            pendingReminderCard = card
                            reminderDisclosureCard = card
                        }
                        true
                    } else {
                        val opened = SmartCardActions.performAction(context, card)
                        if (!opened) IslandToasts.show("没能打开")
                        opened
                    }
                },
                onSkip = { card -> SmartCardRepository.skip(context, card) },
                onIgnore = { card -> SmartCardRepository.markDone(context, card) },
                onBodyClick = { card ->
                    // 点预约卡正文（除按钮外）看原文：建提醒前是短信会话/
                    // 通知跳转，建完后按钮本身就是看预约、这里同动作。
                    if (card.kind == SmartCardKind.APPOINTMENT) viewAppointment(card)
                    // 点日历卡正文：与右按钮同一动作（加入会议 / 看日程）。
                    // 第十九批：时间段内点动作不算处理完、卡片不摘（performAction
                    // 内按 kind＋deadlineMs 判），只有「我知道了」或过了截止点才摘。
                    if (card.kind == SmartCardKind.CALENDAR) {
                        val opened = SmartCardActions.performAction(context, card)
                        if (!opened) IslandToasts.show("没能打开")
                    }
                }
            )
        }
        return
    }
    // 真卡片为空时才轮到引导卡片（而不是空着）。
    // 引导内容带版本号（设计稿 §4.7，456）：存的版本比当前旧（或无记录）
    // 就出现；看完/关掉记当前版本，同一版本不再重复出现。
    var seenGuideVersion by remember {
        mutableStateOf(SmartCardRepository.guideSeenVersion(context))
    }
    val guideSeen = !shouldShowGuideVersion(seenGuideVersion)
    // 轮换推的代次（第十四批）：点「我知道了」/横滑一张引导卡时仓库改队列
    // （放队尾或出局），SharedPreferences 本身不可观测，靠这个代次触发
    // 重组、重读队列。队列与被拒次数存在仓库里（按 GUIDE_VERSION 存），
    // 进程重启不丢。
    var guideTick by remember { mutableIntStateOf(0) }
    var dismissedGuideIds by remember {
        mutableStateOf(SmartCardRepository.dismissedGuideIds(context))
    }
    // 显著披露要针对哪个连接器（消息先讲清再弹系统框，日历直接弹、
    // 通知/电话拨号器跳系统设置页）；null＝没在弹。
    var disclosureConnector by remember { mutableStateOf<String?>(null) }
    // 用户在叠层里点了右按钮（连接＋来源名）的卡（等授权结果）；接上后先显示“已连接”
    // 反馈再离开。只对在叠层里点了右按钮的卡给反馈；在设置里接上
    // 的权限，其卡片直接消失、不补反馈。
    var pendingConnectIds by remember { mutableStateOf(setOf<String>()) }
    // 用户点右按钮那一刻的真实已接源个数（457 的 connectedSourceCount，
    // 同一处计数）：接上后拿它跟新个数一减，就是本次智能涨了多少。
    var pendingConnectLevels by remember { mutableStateOf(mapOf<String, Int>()) }
    // 正在显示“已连接”庆祝反馈的卡（3 秒后离开）：状态里带动效触发标志
    // celebrate 与等级增量 levelDelta（见 ConnectedFeedback），不是光一个 id。
    var connectedFeedbacks by remember {
        mutableStateOf(mapOf<String, ConnectedFeedback>())
    }
    val connectedIds: Set<String> = connectedFeedbacks.keys
    // 接上某个权限后，对应引导卡先显示“已连接”＋接下来会发生什么（3 秒），
    // 再消失——用户点了连接不能是懵的（设计稿 §4.7 v3）。
    LaunchedEffect(
        smsGranted.value, notificationGranted.value,
        calendarGranted.value
    ) {
        val newly = pendingConnectIds.filter { id ->
            val key = connectorKeyForGuideId(id) ?: return@filter false
            isGuideConnectorGranted(
                key,
                smsGranted.value, notificationGranted.value,
                calendarGranted.value
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
    // 单卡提醒、轮换推（第十四批，Bob 2026-11-10 23:07 定）：只展示队列里
    // 第一张仍符合出现条件的引导卡（currentGuideCard），不是整叠——接上
    // 一个来源后继续推下一张；被拒那张在队列尾，推完其他来源再回来一次。
    // 队列按 GUIDE_VERSION 存在仓库里（guideTick 变化时重读）；符合条件的
    // 集合（visibleGuideCards：没接上、没处理完）每帧重算。
    val queue = remember(guideTick) { SmartCardRepository.guideQueue(context) }
    val guides = visibleGuideCards(
        messageGranted = smsGranted.value,
        notificationGranted = notificationGranted.value,
        calendarGranted = calendarGranted.value,
        dismissedIds = dismissedGuideIds
    )
    val currentGuide = currentGuideCard(queue, guides)
    // 反馈卡排在最前面（刚接上的事立刻看得见），其余只轮到当前这一张。
    val displayed = connectedCards + listOfNotNull(currentGuide)
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
    // 就绪门（第二十五批，问题 1）：引导卡也一样——首帧没齐之前不画，
    // 不在空桌面上单独先蹦；门开后与 Dock 一起出现。
    if (homeReady) {
        SmartCardStack(
            cards = displayed,
            skipEpoch = skipEpoch,
            connectedFeedbacks = connectedFeedbacks,
            onAction = { card ->
                val key = (card.action as? SmartCardAction.Connect)?.connectorKey
                    ?: return@SmartCardStack true
                pendingConnectIds += card.id
                // 点右按钮这一刻的真实等级：接上后拿它算增量（见上）。
                pendingConnectLevels +=
                    card.id to connectedSourceCount(buildConnectorStates(context))
                when (key) {
                    UiPrefs.CONNECTOR_MESSAGE ->
                        disclosureConnector = UiPrefs.CONNECTOR_MESSAGE
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
                    // 横滑一张引导卡＝被拒一次：放队尾（第十四批轮换推），
                    // 其他来源推完后再回来一次，再拒就本版不再推。
                    SmartCardRepository.rejectGuideCard(context, card)
                    guideTick++
                }
            },
            onIgnore = { card ->
                // 点「我知道了」＝被拒一次（第十四批轮换推）：放队尾，不是
                // 永久记下——旧口径“这张不用了、不再出现”作废一半（不追问是
                // 对的，但不是永久不出现）。
                SmartCardRepository.rejectGuideCard(context, card)
                guideTick++
            },
            onClose = {
                SmartCardRepository.markGuideSeen(context)
                seenGuideVersion = GUIDE_VERSION
            }
        )
    }
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
}

/**
 * 卡片叠层：最上面一张完整显示，下面两张露个边（8dp 一层）。
 * 重设计 §2.5：露边卡只有边、没有字——内容只在顶卡上组合，滑动时
 * 下一张升上来成为顶卡、内容才画上去；玻璃透出来也不会几张字混在一起。
 * 点动作立即执行（不拖 250ms 动画等开应用）；横滑跳过播离场动画——
 * 用户的事项提醒沉到摞底（第十四批）、应用推的提醒划过就丢。
 * 每张卡两个响应：我知道了（消失、不再出现）与右按钮（卡片定义的下一步意图）。
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
 * 重设计起顶卡固定 192dp（SmartCardHeight，第十六批由 168dp 抬高），量出来
 * 就是 192dp，摞高确定；松约束的量法不变（仍防内容换卡时按钮被裁）。
 */
/**
 * 叠层卡片两侧边距（第十批，设计稿 §4.7，2026-11-08 Bob 真机 verdict：
 * “卡片是全宽，和桌面风格不协调”）。
 * 取值来源——设置页卡片（桌面现成的浮动玻璃卡语言）：
 * SettingsScreen 全页同一值，`padding(horizontal = 24.dp)` ＋
 * `RoundedCornerShape(20.dp)`；智能卡片圆角原本就是 20dp
 * （SmartCardView），内边距 16dp、行距 8dp 网格也不变，所以只收
 * 边距这一处：两侧各 24dp，不贴边、不满宽。
 * 简报 hero/会行是 32dp 文字缩进（不是卡片），Dock 是竖屏满宽底板
 * （28dp 圆角），都不是浮动卡片的边距——不取那两处。
 * 顶卡、露边卡、“已连接”反馈卡都走同一处（同一 SubcomposeLayout
 * 约束下量），角标压在收边后的顶卡角上。
 */
val SmartCardStackSideMargin = 24.dp

/**
 * 叠层与 hero 的间距（重设计 §2.7）：16dp——叠层顶缘锚在 hero 底部（讲述句）
 * 下方 16dp，吃掉 486 拿掉会行腾出的约 56dp。8dp 网格。
 */
val SmartCardStackTopGap = 16.dp

/**
 * 露边卡形态（纯函数，单测锁死）：第 index 张露边卡相对顶卡的缩放、
 * 透明度与下移量。露边只露 8dp 一格（8dp 网格），顶卡之下最多露两张。
 * 宽度与顶卡相同——露边卡与顶卡用同一组 constraints 量，不另缩宽度；
 * 这里的 scale 只是视觉上的轻微缩小（绕中心），不是改宽度。
 */
data class PeekCardTransform(val scale: Float, val alpha: Float, val yOffset: Dp)

fun peekCardTransform(index: Int): PeekCardTransform = PeekCardTransform(
    scale = 0.97f - 0.03f * index,
    alpha = 0.65f - 0.25f * index,
    yOffset = 8.dp * (index + 1)
)

/**
 * 卡片固定高度（重设计 §2.6）：192dp，不随正文长短变；所有卡一样高，
 * 摞起来齐整。8dp 网格（192 % 8 == 0）。487 是 168dp；Bob 2026-11-11
 * 09:47 真机 verdict“卡太小了”（有小标的卡事实区只剩约 32dp、约 1.3
 * 行），第十六批抬到 192dp。
 */
val SmartCardHeight = 192.dp

/** 卡片内容内边距（重设计 §2.5）：14dp；正文区算式与布局同源（见下）。 */
val SmartCardContentPadding = 14.dp

/**
 * 标题行最小高度（重设计 §2.6 算术）：20dp——来源标签 Caption 12sp／
 * 18sp 行高与 16dp 小标图标取大者约 18dp，min 20dp 让标题行高确定、
 * 正文区算式不依赖 token 行高。引导卡的 48dp × 表头不在此口径内。
 */
val SmartCardTitleRowHeight = 20.dp

/** 按钮行高度（重设计 §2.6 算术）：两个动作按钮都是 48dp 触控高。 */
val SmartCardActionRowMinHeight = 48.dp

/** 卡内行距（重设计 §2.5）：标题行／事实区／按钮行之间各 8dp。 */
val SmartCardRowGap = 8.dp

/**
 * 应用小标右端内收（重设计 §2.8，Bob 2026-11-11 真机 verdict）：
 * 小标搬到标题行右上角后，右端再向内收 16dp，留出右上角待办数角标
 * （AppBadge 20dp 高、贴角偏移 x +4dp／y −4dp；1 位数 pill 宽约 20dp、
 * 2 位数约 24dp，左缘落在卡右缘内约 16–20dp）的位置，不顶到角标。
 * 小标右缘最终距卡右缘 14dp 内边距＋16dp 内收＝30dp，2 位数角标留
 * 约 10dp 空；“99+”（100 张以上）是摞的理论上限，实际不出现。
 */
val SmartCardSubtitleEndInset = 16.dp

/**
 * 正文区可用高度（纯算式，单测锁死）——卡高固定时，正文区＝卡高－上下
 * 内边距－标题行－按钮行－行距（标题行与事实区之间、事实区与按钮行
 * 之间两段）。实卡口径：192－28－20－48－16＝80dp（Body 15sp／24sp
 * 行高，3.3 行）≥72dp（3 行下限）；超了在卡内滚动，不是截断。
 * 布局上正文区就是 weight(1f) 撑满这段（不另加 min/max 钳制——min
 * 会顶穿引导卡的 48dp × 表头、max 会把按钮顶离底边留白），算式与
 * 布局同源（内边距／标题行／按钮行／行距都是布局里用的同一组常量）。
 * 引导卡表头是 48dp ×，算式不套引导卡（引导卡事实短，2 行多就够）。
 */
fun smartCardFactAreaHeight(): Dp =
    SmartCardHeight - SmartCardContentPadding * 2 - SmartCardTitleRowHeight -
        SmartCardActionRowMinHeight - SmartCardRowGap * 2

/**
 * 呼吸描边渐变色（重设计 §2.2）：暖金 #D4A24E → 柔玫 → 冷青 → 暖金闭环。
 * 金是 Bob 点名的锚点；玫/青低饱和、贴浅暖灰主题；首尾同色，绕一圈不断色。
 * 第二十六批（Bob 2026-11-14：光晕太淡感受不到）：玫/青各加深一档
 * （#D9A5A0→#D99B90、#8FB8B5→#6FA8A0），饱和度仍在 0.35 上限内
 * （玫 0.336、青 0.339，见 SmartCardRedesignTest），金不动。
 */
fun smartCardBorderColors(): List<Color> = listOf(
    Color(0xFFD4A24E),
    Color(0xFFD99B90),
    Color(0xFF6FA8A0),
    Color(0xFFD4A24E)
)

/**
 * 光晕描边宽度（重设计 §2.2，第二十六批：Bob 2026-11-14 说光晕太淡、
 * 感受不到——2dp→3dp，只加存在感，不改卡片结构与配色体系）。
 */
val SmartCardHaloStroke = 3.dp

/**
 * 光晕呼吸下限（重设计 §2.2，第二十六批：0.5→0.8——描边不再长时间
 * 躲在半透明里，呼吸只在 0.8↔1 之间，不做刺眼的跑马灯）。
 */
const val SmartCardHaloMinAlpha = 0.8f

/**
 * 光晕一圈时长（重设计 §2.2，第二十六批：6000ms→4000ms——渐变移动与
 * 呼吸都略加快，呼吸半程 2000ms；减动效时仍静止）。
 */
const val SmartCardHaloPeriodMs = 4000L

/** 横滑切卡的手势日志 tag（第二十六批：松手判定现打一行日志，见 TopSmartCard）。 */
private const val TAG = "SmartCardStack"

/**
 * 暖灰半透明衬底的透明度（重设计 §2.1）：下限 0.55；低对比环境回到 0.75 兜底。
 * 低对比＝壁纸平（GlassPolicy 判 FLAT，低亮度方差／低饱和，衬底与壁纸分不开）
 * 或玻璃状态没提供（无模糊可读，衬底是唯一可读层），都走安全档 0.75；
 * 有真实纹理的壁纸（TEXTURED），玻璃模糊已做分隔，衬底只留 0.55。
 */
fun smartCardTintAlpha(backdropKind: BackdropKind?): Float = when (backdropKind) {
    BackdropKind.TEXTURED -> 0.55f
    else -> 0.75f
}

@Composable
fun SmartCardStack(
    cards: List<SmartCard>,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    onIgnore: (SmartCard) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    // 点卡片正文（除按钮外）——第十一批：预约卡看原文（短信会话/通知跳转）；
    // 其他卡没有正文动作，默认空实现。
    onBodyClick: (SmartCard) -> Unit = {},
    // 正在显示“已连接”庆祝反馈的卡（第八批）：不画动作行（没有“连接”可点），
    // 只显示“已连接”＋接下来会发生什么，外加庆祝形态（对勾弹入＋金色等级行
    // ＋整卡轻弹，见 SmartCardView）。反馈卡顶掉的是它自己的引导卡，不多占
    // 一张待办，角标计数不变（cards.size 本就是替换后的张数）。
    connectedFeedbacks: Map<String, ConnectedFeedback> = emptyMap(),
    // 大模型准备清单（第十二批）：按卡片 id 的异步结果，覆盖卡片构建时
    // 的 prep（构建时为 null）。没回来就是没有，不贴模板。
    prepById: Map<String, String> = emptyMap(),
    // 顶卡划走动画复位代次（第十二批）：仓库 skip() 时加一；
    // TopSmartCard 的组合键挂上它，同一张卡以新代次重新呈现时动画状态
    // （offsetX/alpha）重建，不会再出现“数着卡、看不见卡”。
    skipEpoch: Int = 0
) {
    if (cards.isEmpty()) return
    // 准备清单覆盖：大模型回来了就显示，没回来显示构建时的 prep（null
    // 即没有，不贴模板）。copy 只换 prep，不动 id/动作/排序键。
    val top = cards[0].let { it.copy(prep = prepById[it.id] ?: it.prep) }
    val connectedIds: Set<String> = connectedFeedbacks.keys
    // 下一张卡（第二十六批，揭示过渡）：拖动顶卡时它的内容要跟着露出来，
    // 不是等松手切过去后才突然出现。内容组合在露边槽里、盖在空玻璃面
    // （PeekCardFace）之上，透明度/缩放/上浮都跟揭示进度走（见下）；
    // 静止时进度为 0、内容全透明，外观与以前的空露边一模一样。
    val next = cards.getOrNull(1)?.let { it.copy(prep = prepById[it.id] ?: it.prep) }
    // 揭示进度（第二十六批）：0＝静止（下一张内容全透明）、1＝顶卡已拖过
    // 切卡阈值（下一张内容完整在位）。以顶卡 id 为键：顶卡换了就重建回 0，
    // 新的下一张从全透明开始；切卡离场那一刻进度停在 1，新顶卡挂载时
    // 内容已在位、不跳变。TopSmartCard 在手势里写它（跟手 snapTo，
    // 松手弹回时动画回 0），露边内容在 graphicsLayer 里读它（只重绘
    // 图层，不触发整卡重组）。
    val revealAnim = remember(top.id) { Animatable(0f) }
    // 露边卡只有边、没有字（重设计 §2.5）：只留张数与 id 作键，内容不组合；
    // 升上来成为顶卡时，它的内容才在顶卡位置组合（见 top）。
    // 第二十六批补一句：下一张卡的内容在拖动揭示时提前组合在露边槽里
    // （见 next／revealAnim），静止时透明度为 0、仍是不画字的边。
    val peekIds = cards.drop(1).take(2).map { it.id }
    // 第十批：卡片按桌面卡片语言收边——两侧各留 SmartCardStackSideMargin，
    // 不贴边、不满宽。收边放在最外层 Column：顶卡、露边卡、角标一起收，
    // 摞高量测（SubcomposeLayout）与手势带（cardStackBandPx 的上下缘）
    // 都按收边后的宽度走，不另起一套。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SmartCardStackSideMargin)
    ) {
        // 叠层与 hero 的间距（重设计 §2.7，Bob 2026-11-11 批准）：16dp——
        // 叠层顶缘锚在 hero 底部（讲述句）下方 16dp，吃掉 486 拿掉会行腾出的
        // 约 56dp（32dp 留白＋会行），不再留会行那段空位。× 不再占这一行
        // （448 的 × 行在卡外、悬在壁纸上）；× 进卡内表头（见 SmartCardView），
        // 这里两模式统一只留段间距。
        Spacer(modifier = Modifier.height(SmartCardStackTopGap))
        // 待办数角标（Bob 2026-11-07 晚定）：叠层右上角、压住顶卡角，显示
        // 还剩几张待办卡片；与 Dock/应用中心同一套 AppBadge（红 pill 白字，
        // 1–99 显示数字、≥100 显示 99+，0 张不画）。计数＝当前叠层卡片张数
        // （真卡或可见引导卡），随处理/跳过/新增实时变；0 张时本函数已提前
        // 返回、区域清空，角标不显示。纯视觉覆盖层，不消费触摸（× 在卡内
        // 16dp 处，角标贴角不重叠；即使贴到也不拦点按）。
        Box(modifier = Modifier.fillMaxWidth()) {
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val topPlaceables = subcompose(SmartCardStackSlot.Top) {
                    // 组合键挂上 skipEpoch（第十二批）：同一张卡以新代次重新
                    // 呈现时 TopSmartCard 重建，划走动画的 offsetX/alpha
                    // 回到 0/1，不会再出现“数着卡、看不见卡”。
                    key(top.id, skipEpoch) {
                        TopSmartCard(
                            card = top,
                            onAction = onAction,
                            onSkip = onSkip,
                            onIgnore = onIgnore,
                            onClose = onClose,
                            onBodyClick = onBodyClick,
                            hideActionRow = top.id in connectedIds,
                            celebration = connectedFeedbacks[top.id],
                            revealAnim = revealAnim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("smart_card_top")
                        )
                    }
                }.map { it.measure(constraints.copy(maxHeight = Constraints.Infinity)) }
                val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
                val peekPlaceables = peekIds.mapIndexed { index, id ->
                    val transform = peekCardTransform(index)
                    subcompose(SmartCardStackSlot.Peek(index)) {
                        key(id) {
                            PeekCardFace(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = transform.scale
                                        scaleY = transform.scale
                                        alpha = transform.alpha
                                    }
                                    .testTag("smart_card_peek")
                            )
                        }
                    }.map { it.measure(constraints.copy(maxHeight = topHeight)) }
                }
                // 下一张卡的揭示内容（第二十六批）：只给第一张露边卡（index 0）。
                // 与顶卡同一张 SmartCardView、同一尺寸——进度到 1 时与顶卡
                // 位置像素一致（缩放 1.0、上浮 0、透明度 1），松手切过去后
                // 新顶卡挂载、内容已在位，不跳变。放在 (0, 0)：静止时靠
                // translationY 下沉 8dp、缩 0.97，与下面的空玻璃面对齐；
                // 进度上升时上浮到 0、放大到 1.0。内容不可点：外层 pointerInput
                // 吃掉落点（点按/滑动都归顶卡），避免手指落在已露出的下一张
                // 卡上时误触它的按钮/正文；呼吸描边不画（光晕只属于顶卡，
                // 升上来成为顶卡后自带）。
                val nextContentPlaceables = if (next != null) {
                    subcompose(SmartCardStackSlot.PeekContent) {
                        key(next.id) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val p = revealAnim.value
                                        alpha = p
                                        scaleX = peekCardTransform(0).scale +
                                            (1f - peekCardTransform(0).scale) * p
                                        scaleY = peekCardTransform(0).scale +
                                            (1f - peekCardTransform(0).scale) * p
                                        translationY = (1f - p) *
                                            peekCardTransform(0).yOffset.toPx()
                                    }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            // 只在揭示进行中（下一张内容已露出来）才
                                            // 吃掉落点；静止时进度为 0、内容全透明，
                                            // 落点照常落到叠层下层，不改原有行为。
                                            if (revealAnim.value > 0f) down.consume()
                                        }
                                    }
                                    .testTag("smart_card_peek_content")
                            ) {
                                SmartCardView(
                                    card = next,
                                    onActionClick = {},
                                    onIgnoreClick = {},
                                    onCloseClick = null,
                                    onBodyClick = null,
                                    hideActionRow = next.id in connectedIds,
                                    celebration = connectedFeedbacks[next.id],
                                    showBreathingBorder = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }.map { it.measure(constraints.copy(maxHeight = topHeight)) }
                } else emptyList()
                val stackHeight = topHeight + 8.dp.roundToPx() * peekIds.size
                layout(constraints.maxWidth, stackHeight) {
                    // 下面几张先放（在后面），露边在顶卡底边下方
                    peekPlaceables.forEachIndexed { index, placeables ->
                        placeables.forEach {
                            it.place(0, peekCardTransform(index).yOffset.roundToPx())
                        }
                    }
                    // 揭示内容盖在第一张露边卡的空玻璃面上、顶卡之下。
                    nextContentPlaceables.forEach { it.place(0, 0) }
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

/** SubcomposeLayout 槽位：顶卡一个，露边卡按序号，下一张揭示内容一个。 */
private enum class SmartCardStackSlot {
    Top, Peek0, Peek1, PeekContent;

    companion object {
        fun Peek(index: Int): SmartCardStackSlot =
            if (index == 0) Peek0 else Peek1
    }
}

/** 横滑切卡的位移阈值（dp）：慢拖过线就切卡（第二十三批由 120dp 放宽）。 */
internal const val SMART_CARD_SWIPE_DISPLACEMENT_DP = 96f

/** 横滑切卡的速度阈值（dp/s）：快速轻扫即使位移不到阈值也切卡。 */
internal const val SMART_CARD_SWIPE_FLING_DP = 1000f

/**
 * 横滑切卡判定（纯函数，第二十三批单测锁死）：
 * 位移或速度任一过线就切卡。位移阈值 96dp（由 120dp 放宽，慢拖也好切）；
 * 速度阈值 1000dp/s（快速轻扫即使位移不到阈值也切）。
 * 输入用 dp（与密度无关），调用处把 px / density 换算进来。
 * 第二十六批：这个谓词判的是“某一个位移值”——松手判定（见
 * resolveSwipeRelease）给它喂的是峰值位移，不是松手瞬间净位移。
 */
internal fun shouldSkipCard(
    offsetXDp: Float,
    velocityXDps: Float,
    displacementThresholdDp: Float = SMART_CARD_SWIPE_DISPLACEMENT_DP,
    flingThresholdDps: Float = SMART_CARD_SWIPE_FLING_DP
): Boolean {
    return abs(offsetXDp) > displacementThresholdDp ||
        abs(velocityXDps) > flingThresholdDps
}

/**
 * 拖动揭示进度（纯函数，第二十六批单测锁死）：下一张卡内容显现的程度，
 * 0＝静止（全透明）、1＝顶卡已拖过切卡阈值（内容完整在位）。按拖动位移
 * 的绝对值占阈值的比例算，方向不进判定（左右拖都揭示）。
 */
internal fun swipeRevealProgress(offsetXPx: Float, thresholdPx: Float): Float {
    if (thresholdPx <= 0f) return 0f
    return (abs(offsetXPx) / thresholdPx).coerceIn(0f, 1f)
}

/**
 * 横滑松手后的统一判定（纯函数，第二十五批单测锁死、第二十六批改口径）：
 * 手势怎么结束（正常抬起 / 指针从事件里消失）不影响判定——只看
 * 认领与位移/速度。501 的 pointerGone 分支无条件弹回是错的：
 * Bob 2026-11-14 录屏里顶卡向右拖到近乎出屏（实测峰值位移约 889px
 * ≈ 300dp，远超 96dp 阈值）却两次都弹回——手指拖到屏边被系统
 * 取消（指针消失）时，已拖够的位移是用户明确的划走意图，不该被
 * 取消否决；小位移被取消时 shouldSkipCard 本来就返回 false，照常弹回。
 * 第二十六批再改一处（Bob 2026-11-14 10:41 录屏：513 上还是划不动）：
 * 501／512 判的都是松手瞬间的**净位移**——慢拖、探一下再往回带的手势，
 * 松手时净位移很容易回到 96dp 以内（峰值明明拖出去 300dp），就被判成
 * 弹回。松手判定改看**峰值位移**（整条手势里 |totalX| 的最大值）：拖出去
 * 多远就是用户的意图，松手前带回来一点不算反悔。另加第二信号渲染位移
 * （offsetX.value，真正在屏上画出来的位置）：手势循环里累计的 totalX
 * 万一漏了事件、与渲染对不上，卡片 visibly 已拖过阈值时也不能弹回。
 * 净位移不再单独进判定（峰值恒 ≥ |净位移|，已被峰值包含）。
 * 方向不进判定（abs）：左划右划都是“划走这张看下一张”，不是
 * 上一张/下一张翻页器（双向划走，Bob 2026-11-14 定）。
 */
internal enum class SwipeRelease {
    /** 没认领或已在离场：本次手势什么都不做。 */
    Ignore,
    /** 位移或速度过线：离场、划走顶卡。 */
    Skip,
    /** 没过线：弹回原位。 */
    SnapBack
}

internal fun resolveSwipeRelease(
    claimed: Boolean,
    skipping: Boolean,
    peakOffsetXDp: Float,
    renderedOffsetXDp: Float,
    velocityXDps: Float
): SwipeRelease {
    if (!claimed || skipping) return SwipeRelease.Ignore
    return if (shouldSkipCard(peakOffsetXDp, velocityXDps) ||
        abs(renderedOffsetXDp) > SMART_CARD_SWIPE_DISPLACEMENT_DP
    ) SwipeRelease.Skip else SwipeRelease.SnapBack
}

/** 顶卡：横滑跳过（位移或速度过线离场、沉到摞底，不够回弹）；点动作立即执行 */
@Composable
private fun TopSmartCard(
    card: SmartCard,
    onAction: (SmartCard) -> Boolean,
    onSkip: (SmartCard) -> Unit,
    onIgnore: (SmartCard) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBodyClick: (SmartCard) -> Unit = {},
    hideActionRow: Boolean = false,
    celebration: ConnectedFeedback? = null,
    // 下一张卡的揭示进度（第二十六批）：手势里跟手写，露边内容读它渐显。
    revealAnim: Animatable<Float, AnimationVector1D>
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val offsetX = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var skipping by remember { mutableStateOf(false) }

    SmartCardView(
        card = card,
        onActionClick = { onAction(card) },
        onIgnoreClick = { onIgnore(card) },
        onCloseClick = onClose,
        onBodyClick = { onBodyClick(card) },
        hideActionRow = hideActionRow,
        celebration = celebration,
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                this.alpha = alpha.value
            }
            .pointerInput(card.id, skipping) {
                // 决定性横滑（第二十三批，Bob 2026-11-13 横滑切卡 bug 的根因修法）：
                // 旧实现 detectHorizontalDragGestures 是协作式检测器——它的
                // drag 循环见到一条已被别的检测器消费过的 move 就取消
                // （onDragCancel → 弹回原位）。正文区的 verticalScroll（竖向
                // DragGestureNode）在同一条命中路径上更内层、每条 move 先过它；
                // 手指斜着划时竖向漂移一过 slop 它就消费那条 move，卡片的横滑
                // 就中途被取消——卡跟手拖很远（f_021 136dp、f_029 近乎出屏）
                // 又在 0.12 秒内弹回，同一张卡仍在顶、角标仍 2，4–5 次都这样。
                // 根因不是父级盲操（449 仲裁已把卡片区排除、录屏里扇形／应用
                // 中心一个没弹），不是 onDragEnd 读到小位移（f_029 渲染值就是
                // offsetX），不是 onSkip 没顶上下一张（onSkip 根本没被调到，
                // 卡没有飞出淡出）。SmartCardView 里「横滑切卡与正文竖翻是
                // 两个方向，不冲突」的注释是错的假设——Compose 的消费是按
                // change 计的单一位，竖向检测器消费一条 move 就能取消横向拖拽。
                // 修法：卡片自己做轴向仲裁——横向占优且过 slop 就认领整条手势
                // （每条 move 都 consume，叠层自己吃掉横向滑动；读
                // positionChangeIgnoreConsumed，不怕被别的检测器先消费）；
                // 竖向占优就让路（不消费、不动卡），正文照常竖翻。位移同步
                // 累计在 totalX 里（松手判定用它，不读可能滞后的 offsetX.value）；
                // 视觉跟手仍走 scope.launch 异步 snapTo（awaitEachGesture 是
                // 受限作用域，不能直接调挂起的 snapTo）。松手加速度判断
                // （快速轻扫即使位移不到阈值也切，见 shouldSkipCard）。
                // 划走语义不动：onSkip 仍接 skip()，用户的事项卡沉底、
                // 应用推的提醒丢、引导卡走轮换推。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var totalX = 0f
                    var totalY = 0f
                    // 峰值位移（第二十六批）：整条手势里 |totalX| 的最大值——
                    // 慢拖、探一下再往回带的手势，松手时净位移会回到阈值内，
                    // 但峰值才是用户的划走意图，松手判定看它（见 resolveSwipeRelease）。
                    var peakX = 0f
                    var claimed = false
                    val touchSlop = viewConfiguration.touchSlop
                    val revealThresholdPx =
                        with(density) { SMART_CARD_SWIPE_DISPLACEMENT_DP.dp.toPx() }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            // 指针从事件里消失（系统取消，见 awaitDragOrUp 的
                            // null 分支）：手势到此结束，但已累计的位移仍参与
                            // 下面的松手判定（第二十五批：不再无条件弹回）。
                            break
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                        val delta = change.positionChangeIgnoreConsumed()
                        totalX += delta.x
                        totalY += delta.y
                        peakX = peakX.coerceAtLeast(abs(totalX))
                        velocityTracker.addPosition(
                            change.uptimeMillis, change.position
                        )
                        if (!claimed) {
                            when {
                                abs(totalX) > touchSlop && abs(totalX) >= abs(totalY) -> {
                                    claimed = true
                                }
                                abs(totalY) > touchSlop && abs(totalY) > abs(totalX) -> {
                                    // 竖向占优：让路给正文竖翻，卡片不动、不消费。
                                    break
                                }
                                else -> continue
                            }
                        }
                        // 已认领：吃掉这条 move（父级盲操见了已消费就退出）。
                        // 位移同步跟手：awaitEachGesture 是受限作用域，不能
                        // 直接调 Animatable.snapTo（挂起函数），改走 scope.launch
                        // 异步跟手（与旧实现同款）；松手判定用同步累计的 totalX，
                        // 不是可能滞后的 offsetX.value。
                        change.consume()
                        val x = totalX
                        scope.launch { offsetX.snapTo(x) }
                        // 揭示进度跟手写（第二十六批）：下一张卡的内容随拖动
                        // 渐显，拖过阈值就完整在位；与 offsetX 同款异步 snapTo。
                        val progress = swipeRevealProgress(x, revealThresholdPx)
                        scope.launch { revealAnim.snapTo(progress) }
                    }
                    // 松手判定（第二十六批）：正常抬起与指针消失（系统取消）
                    // 走同一判定——resolveSwipeRelease 只看认领、峰值位移、
                    // 渲染位移与速度。拖够阈值就切卡，不过线才弹回。判定的
                    // 输入现打一行日志：再有“拖出去还弹回”，logcat 里直接
                    // 看是哪一项没过线（只记数值，不记卡片内容）。
                    val velocity = velocityTracker.calculateVelocity().x
                    val densityScale = density.density
                    val netDp = totalX / densityScale
                    val peakDp = peakX / densityScale
                    val renderedDp = offsetX.value / densityScale
                    val release = resolveSwipeRelease(
                        claimed = claimed,
                        skipping = skipping,
                        peakOffsetXDp = peakDp,
                        renderedOffsetXDp = renderedDp,
                        velocityXDps = velocity / densityScale
                    )
                    Log.d(
                        TAG,
                        "swipe release: card=${card.id} claimed=$claimed " +
                            "skipping=$skipping net=${netDp}dp peak=${peakDp}dp " +
                            "rendered=${renderedDp}dp " +
                            "velocity=${velocity / densityScale}dp/s -> $release"
                    )
                    when (release) {
                        SwipeRelease.Ignore -> return@awaitEachGesture
                        SwipeRelease.SnapBack -> {
                            scope.launch {
                                offsetX.animateTo(0f, tween(MotionTokens.Fast))
                            }
                            // 揭示内容跟着收回去（与弹回同一节奏）。
                            scope.launch {
                                revealAnim.animateTo(0f, tween(MotionTokens.Fast))
                            }
                        }
                        SwipeRelease.Skip -> {
                            skipping = true
                            val dir = if (totalX >= 0) 1f else -1f
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
                        }
                    }
                }
            }
    )
}

/**
 * 单张卡片（只画顶卡——露边卡是光面 PeekCardFace，不画字）：
 * 来源标签＋一句话事实（含截止点）＋（可选）一行准备清单＋
 * 一行两个响应：我知道了（左）与右按钮（右，第十二批 Bob 定：显示下一步
 * 的意图，去还款 / 帮我建提醒 / 看预约 / 回拨 / 查看 / 加入会议 / 看日程 /
 * 连接＋来源名；动作含义由事实文案＋右下方应用小标补充）。
 * 右下方应用小标（第十一批）：只画在真卡上，独立一行贴右下角——16dp
 * 应用图标＋应用名（Caption 12sp，次要色；字阶最小档就是 12sp，11sp
 * 中文可读性不足、token 规则禁用的下限，取 Bob 给的 11–12sp 的上限），
 * 无底色、无边框，不抢按钮视觉；名字过长截断；只做展示、不可点。
 * 没有标题党、没有解释行。
 *
 * 视觉（重设计，Bob 2026-11-11 批准）：
 * - 背景：拿掉 449 加的 0.88 实色衬底（GlassCardStrong，当时为盖住露边卡
 *   的文字），改回玻璃 STRONG 本色＋一层暖灰半透明（0.55；低对比环境
 *   0.75 兜底，见 smartCardTintAlpha）。露边卡不再画字，玻璃透出来也不混。
 * - 边缘呼吸：顶卡 3dp 渐变描边（暖金→柔玫→冷青，4 秒一圈，透明度 0.8↔1
 *   呼吸；减动效时静止）。第二十六批加粗加亮（Bob 2026-11-14：太淡感受不到）。
 * - 正文：Body 15sp Medium；不截断（maxLines=3＋Ellipsis 已删），全文在
 *   卡内滚动；卡高固定 192dp（第十六批由 168dp 抬高），正文区至少 3 行高
 *   （80dp，3.3 行，见 smartCardFactAreaHeight）。
 * - 内边距 14dp；圆角 20dp、两侧收边 24dp、按钮、金色庆祝不动；应用小标
 *   不再独占一行，搬到标题行右上角（§2.8）。
 * SIMULATED 档／无玻璃环境退回半透明暖灰 GlassCard（glassSurface 自带）。
 */
@Composable
private fun SmartCardView(
    card: SmartCard,
    onActionClick: () -> Unit,
    onIgnoreClick: () -> Unit,
    onCloseClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // 点卡片正文（除按钮外）——第十一批：预约卡看原文；null＝没有正文动作。
    onBodyClick: (() -> Unit)? = null,
    // “已连接”反馈卡：不画动作行（忽略/连接两个按钮与露边提示都不画），
    // 只留来源标签＋“已连接”＋接下来会发生什么。
    hideActionRow: Boolean = false,
    // “已连接”庆祝反馈（第八批，设计稿 §4.7）：非空时卡片换成庆祝形态——
    // 金色对勾弹入＋金色等级行（“智能 +1 · 已接 3/4 个源”）＋整卡轻弹。
    // 颜色只用已有的主操作金 Accent / AccentSoft，不引新色；图标是线性
    // Check，不用 emoji；尺寸走 8dp 网格（40dp 圆、8dp 间距）；减动效时
    // 直接落到终态，不播动画。文案仍按 Bob 的两条写。
    celebration: ConnectedFeedback? = null,
    // 呼吸描边（第二十六批）：只画在顶卡。露边揭示内容传 false——
    // 升上来成为顶卡后自带光晕，揭示过程中不抢顶卡的光。
    showBreathingBorder: Boolean = true
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
    // 暖灰半透明衬底（重设计 §2.1）：见 smartCardTintAlpha——有纹理壁纸 0.55，
    // 低对比环境 0.75 兜底；颜色取主题暖灰底（浅色 #F4F2EE／深色 #1A1815）。
    val tint = smartCardTint()
    // 正文滚动：事实＋准备清单＋已建提醒一起在卡内滚动；翻到底渐隐消失。
    val factScrollState = rememberScrollState()
    val factFade = if (factScrollState.canScrollForward) {
        Modifier.drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    0.75f to Color.Black,
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }
    } else Modifier
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
                color = tint,
                shape = shape
            )
            .then(
                if (showBreathingBorder) breathingBorderModifier(reduceMotion)
                else Modifier
            )
            .height(SmartCardHeight)
            .padding(SmartCardContentPadding),
        verticalArrangement = Arrangement.spacedBy(SmartCardRowGap)
    ) {
        // 表头（重设计 §2.8，第十六批）：来源标签在左上角；应用小标
        // （16dp 图标＋12sp 应用名）搬到右上角、与来源标签同一行——
        // 小标本身不变（次要色、无底色无边框、只展示不可点、最宽 160dp），
        // 只是换位置、不丢信息；右端再向内收 SmartCardSubtitleEndInset，
        // 留出右上角待办数角标的位置。引导卡没有小标（下一步是去连接
        // 来源、不是打开某个应用），只有卡内 ×；“已连接”反馈卡也不画
        // （hideActionRow 时不画，见下）。× 落在卡片内部、48dp 触控；
        // 448 的 × 是卡外单独一行、悬在壁纸上（实拍右上角卡外），改掉。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SmartCardTitleRowHeight),
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
            } else if (!hideActionRow && card.kind != SmartCardKind.GUIDE) {
                card.targetApp?.let { target ->
                    AppSubscript(
                        targetApp = target,
                        modifier = Modifier.padding(end = SmartCardSubtitleEndInset)
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
        // 正文区（重设计 §2.6，第十六批）：事实＋准备清单＋已建提醒一起在
        // 卡内滚动；至少 3 行高（80dp，3.3 行，见 smartCardFactAreaHeight），
        // 来源行／按钮行固定不动（小标已搬到标题行，不再占一行）。全文都在，
        // 不是截断——maxLines=3＋Ellipsis 已删；翻到底渐隐消失（见 factFade）。
        // 高度就是 weight(1f) 撑满卡高减去其余行（不另加 min/max 钳制——
        // min 会顶穿引导卡的 48dp × 表头、max 会把按钮顶离底边留白）。
        // 横滑切卡与正文竖翻靠轴向仲裁共存（第二十三批）：TopSmartCard
        // 的横滑处理横向占优认领、竖向占优让路——不是「两个方向，不冲突」
        // （旧注释是错的假设：消费按 change 计单一位，竖向检测器消费一条
        // move 就能取消横向拖拽，2026-11-13 横滑切卡 bug 的根因）；点正文
        // 仍是看原文（预约／日历卡）。
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(factScrollState)
                .then(factFade)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = card.fact,
                    fontSize = AILauncherType.Body.fontSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = AILauncherType.Body.lineHeight,
                    color = AILauncherColors.current.Title,
                    modifier = if (onBodyClick != null) {
                        Modifier.clickable(onClick = onBodyClick)
                    } else Modifier
                )
                card.prep?.let { prep ->
                    Text(
                        text = prep,
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Body,
                        lineHeight = AILauncherType.Footnote.lineHeight,
                        modifier = if (onBodyClick != null) {
                            Modifier.clickable(onClick = onBodyClick)
                        } else Modifier
                    )
                }
                // “已建提醒”（第十一批）：预约卡建完提醒后的一行小字，告诉用户
                // 提醒已在系统日历里了、不用重复建；动作已换回看预约。
                if (card.reminderCreated) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = AILauncherColors.current.Hint,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "已建提醒",
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Hint
                        )
                    }
                }
            }
        }
        if (!hideActionRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 我知道了：卡片消失、不再出现、不追问（设计稿 §4.2）。
                TextButton(
                    onClick = onIgnoreClick,
                    modifier = Modifier.heightIn(min = SmartCardActionRowMinHeight)
                ) {
                    Text(
                        text = SMART_CARD_DISMISS_LABEL,
                        fontSize = AILauncherType.Callout.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Hint
                    )
                }
                // 右按钮（第十二批 Bob 定）：显示下一步的意图（actionLabel：
                // 去还款 / 帮我建提醒 / 看预约 / 回拨 / 查看 / 加入会议 /
                // 看日程 / 连接＋来源名），不再写“下一步”几个字。
                // 金色只留主操作（去 AI 味四条）。
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.heightIn(min = SmartCardActionRowMinHeight)
                ) {
                    Text(
                        text = card.actionLabel,
                        fontSize = AILauncherType.Callout.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Accent
                    )
                }
            }
        }
    }
}

/**
 * 暖灰半透明衬底（重设计 §2.1）：玻璃之上那一层；低对比环境 0.75 兜底。
 * 颜色取主题暖灰底（浅色 #F4F2EE／深色 #1A1815），透明度见 smartCardTintAlpha。
 */
@Composable
private fun smartCardTint(): Color {
    val glassState = LocalGlassBackdrop.current
    return AILauncherColors.current.Background.copy(
        alpha = smartCardTintAlpha(glassState?.backdropKind)
    )
}

/**
 * 顶卡渐变呼吸描边（重设计 §2.2）：只画顶卡。
 * 渐变沿卡边缓慢移动、描边透明度呼吸；减动效时静止（不播动画）。
 * 实现：线性渐变的方向角绕卡旋转——矩形本身不转，只有渐变色在走；
 * 描边向内收半个线宽，整条描边都落在 20dp 圆角裁剪内。
 * 第二十六批（Bob 2026-11-14：光晕太淡感受不到）：描边 2dp→3dp、
 * 呼吸下限 0.5→0.8、一圈 6 秒→4 秒（呼吸半程 2 秒）、玫/青加深一档
 * （见 smartCardBorderColors），仍在浅暖灰主题与色彩克制口径内，
 * 不做刺眼的跑马灯；参数见 SmartCardHaloStroke／SmartCardHaloMinAlpha／
 * SmartCardHaloPeriodMs（单测锁死）。
 */
@Composable
private fun breathingBorderModifier(reduceMotion: Boolean): Modifier {
    val angle = remember { Animatable(0f) }
    val borderAlpha = remember { Animatable(1f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            angle.snapTo(0f)
            borderAlpha.snapTo(1f)
        } else {
            launch {
                while (true) {
                    angle.animateTo(
                        360f,
                        tween(
                            durationMillis = SmartCardHaloPeriodMs.toInt(),
                            easing = LinearEasing
                        )
                    )
                    angle.snapTo(0f)
                }
            }
            launch {
                while (true) {
                    borderAlpha.animateTo(
                        SmartCardHaloMinAlpha,
                        tween(durationMillis = (SmartCardHaloPeriodMs / 2).toInt())
                    )
                    borderAlpha.animateTo(
                        1f,
                        tween(durationMillis = (SmartCardHaloPeriodMs / 2).toInt())
                    )
                }
            }
        }
    }
    val density = LocalDensity.current
    val strokePx = with(density) { SmartCardHaloStroke.toPx() }
    val radiusPx = with(density) { 19.dp.toPx() }
    val insetPx = with(density) { 1.dp.toPx() }
    val colors = smartCardBorderColors()
    return Modifier.drawWithContent {
        drawContent()
        val rad = Math.toRadians(angle.value.toDouble())
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dx = cos(rad).toFloat() * (size.width / 2f)
        val dy = sin(rad).toFloat() * (size.height / 2f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy)
            ),
            topLeft = Offset(insetPx, insetPx),
            size = Size(size.width - 2 * insetPx, size.height - 2 * insetPx),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
            alpha = borderAlpha.value
        )
    }
}

/**
 * 露边卡面（重设计 §2.5）：只是一张卡的边——玻璃＋暖灰衬底＋1dp 细线，
 * 不画任何文字内容。第二十六批：拖动揭示时，下一张卡的内容另有一层
 * 盖在这张空玻璃面上渐显（见 SmartCardStack 的 PeekContent 槽），
 * 静止时那层透明度为 0、这里仍是不画字的边；升上来成为顶卡时，
 * 它的内容在顶卡位置组合。
 * 高度与顶卡同高（SmartCardHeight）；按 peekCardTransform 的位置／缩放／
 * 透明度放，只露底边 8dp 一格。
 */
@Composable
private fun PeekCardFace(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .height(SmartCardHeight)
            .clip(shape)
            .glassSurface(
                kind = GlassSurfaceKind.STRONG,
                cornerRadius = 20.dp,
                tintColor = AILauncherColors.current.Card,
                fallbackColor = AILauncherColors.current.GlassCard
            )
            .background(color = smartCardTint(), shape = shape)
            .border(1.dp, AILauncherColors.current.GlassBorder, shape)
    )
}

/**
 * 应用小标（第十一批，Bob 定“要小、要优雅”）：只画在真卡上——引导卡的
 * 下一步是去连接来源、不是打开某个应用，不画；“已连接”反馈卡也不画
 * （hideActionRow）。16dp 应用图标＋应用名（Caption 12sp，次要色），
 * 无底色、无边框，只做展示、不可点（没有 clickable，点右按钮才是动作）；
 * 名字过长截断（最多 160dp）。图标按包名取真实应用图标；包被卸了、
 * 图标读不到时只显示名字。
 * 第十六批：从右下角独占行搬到标题行右上角（§2.8）——本函数只包内容、
 * 不占整行宽，右对齐由标题行的 SpaceBetween 负责，右端内收由调用方
 * 传 SmartCardSubtitleEndInset。
 */
@Composable
private fun AppSubscript(
    targetApp: com.bobot.ailauncher.data.TargetApp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 小标可以没有应用（第二十五批，问题 3：日历卡小标是事件所在的日历
    // 账户名，如“豆包日历”，不是用来打开的日历应用——没有包名可取图标，
    // 只画文字）。packageName 为 null 时不取图标，只画 label。
    val drawable = remember(targetApp.packageName) {
        val pkg = targetApp.packageName ?: return@remember null
        try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (_: Exception) {
            null
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (drawable != null) {
            AppIconImage(
                drawable = drawable,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = targetApp.label,
            fontSize = AILauncherType.Caption.fontSize,
            color = AILauncherColors.current.Hint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}
