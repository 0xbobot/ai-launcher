package com.bobot.ailauncher.ui.smartcard

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.CONNECTED_FEEDBACK_MS
import com.bobot.ailauncher.data.ConnectedFeedback
import com.bobot.ailauncher.data.GUIDE_VERSION
import com.bobot.ailauncher.data.SmartCardKind
import com.bobot.ailauncher.data.buildConnectorStates
import com.bobot.ailauncher.data.celebrationLevelText
import com.bobot.ailauncher.data.connectedFeedback
import com.bobot.ailauncher.data.connectedSourceCount
import com.bobot.ailauncher.data.connectorKeyForGuideId
import com.bobot.ailauncher.data.createCalendarReminder
import com.bobot.ailauncher.data.guideCards
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.data.calendarEventDetailLines
import com.bobot.ailauncher.data.guideIdsForConnector
import com.bobot.ailauncher.data.isGuideConnectorGranted
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.SmartCard
import com.bobot.ailauncher.data.SmartCardAction
import com.bobot.ailauncher.data.SmartCardActions
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.overlayUpdateCardDisplay
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.currentGuideCard
import com.bobot.ailauncher.data.shouldShowGuideVersion
import com.bobot.ailauncher.data.visibleGuideCards
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.BackdropKind
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.LocalGlassBackdrop
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.home.TrackedText
import com.bobot.ailauncher.ui.home.isQuietHero
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
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 智能卡片叠层宿主（第一阶段，447/v1.2.0，设计稿 §4）：
 * 收集卡片流、回桌面与分钟翻页时重算；没有卡时什么都不渲染、不留占位。
 *
 * 叠层交互（第三十三批，Bob 2026-11-14 22:44 定：单卡槽位自动轮播，
 * 无划走——划卡线（501/512/516/518/520/521/522/523 的划走、飞出、
 * 揭示、轮播切卡）全部作废）：
 * 单卡槽位：一次只显示一张卡（当前最紧急的那张），不叠放、不露边、
 * 不画下一张的边。自动定时轮换（5 秒一档，≥2 张才轮，1 张不轮）；
 * 切卡只用淡入淡出（300ms，减动效 120ms），不飞出、不揭示（点圆点／
 * 自动轮换；手动滑动一张接一张见下）；
 * 卡片下方圆点指示（当前张实心金、其余空心淡，点圆点跳到那一张，
 * 点后自动轮换暂停 8 秒再恢复）；卡片上挂手动滑动（第三十七批回来、
 * 第三十八批改跟手、第三十九批改一张接一张）：拖动位移跟手指走，松手
 * 位移超 1/4 卡宽或速度够就换卡（出卡继续滑出、进卡从反方向滑入，一张
 * 接一张，300ms 同一条时间线）、否则回弹；滑动只切卡、不摘卡。
 * 扇形仲裁不受影响：HomeScreen 的盲操触发区按叠层实测 bounds 排除
 * （纵向 fallback 同样排除），卡片区的手势本来就不走盲操／应用中心。
 * 只有点「我知道了」才摘卡（真卡 markDone／引导卡轮换推，走原有口径），
 * 摘完自动进下一张、圆点更新。点下一步动作算处理完一张、卡片消失——
 * 除了第十九批的有时间段提醒（日历/预约）：时间段内点动作不摘卡、
 * 卡片留在槽里，只有「我知道了」或过了截止点才摘。全部处理完后区域
 * 清空、不留占位条。
 * 旧口径一并作废：第十四批划走按卡种分（事项卡划过沉底、应用推划过
 * 就丢、引导卡划过放队尾）、第三十二批轮播切卡（左右滑手动切卡、
 * 飞出＋揭示）——卡片上没有划了，沉底／丢／轮换只由「我知道了」
 * 触发；仓库 skip() 自三十二批起已无 UI 调用方（旧版已写的
 * skipped_／skipAt_ 键仍由 liveIdList／orderSkippedIds 读来排沉底
 * 顺序，见 SmartCardRepository.skip）。
 *
 * 每张卡两个响应（设计稿 §4.2，449/v1.2.2，Bob 2026-11-07 晚真机 verdict；
 * 第十一批 Bob 2026-11-09 定文案）：我知道了（卡片消失、不再出现、不追问）
 * 与下一步（按卡片定义的下一步跟进）。引导卡的下一步＝去连接对应的来源
 * （走 §5 已有流程）；真卡片的下一步＝卡片定义的下一步动作（去还款 / 回拨 /
 * 看预约 / 打开看看，走已有的深链/启动路径直接执行；预约卡是“帮我建提醒”，
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
 * 定）：一次只推一张，接上一个来源后继续推下一张；点「我知道了」
 * ＝被拒一次（横滑已取消，第三十三批），放队尾、其他来源推完后再回来一次，再拒就本版不再推（等引
 * 导换版）；点“连接”走 §5 流程（消息先显著披露再弹系统框，日历直接弹，
 * 通知跳系统设置页）；接上某个权限后对应卡先显示“已经连上了”＋接下来会发生
 * 什么（3 秒）再消失，其他引导卡留着；有真事项时真卡片顶掉整叠引导卡。
 * 文案 v3：每张讲三件事——这是什么、为什么要连、连上之后会发生什么。
 */
@Composable
fun SmartCardStackHost(
    // Stageshow 桌面优化 3.1（Bob 2026-11-21 定）：hero 安静／完整上报——
    // 叠层里有一览卡时安静，没有时完整。
    // 2026-11-24（轮播跳动根因修）：只报列表派生的安静与否，不报轮播
    // 瞬时下标对应的顶卡——下标每 5 秒一变，hero 跟着翻高度会单帧跳变。
    onQuietHeroChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val cards by SmartCardRepository.cards.collectAsState()
    val prepById by SmartCardRepository.prepById.collectAsState()
    val notifications by NotificationRepository.notifications.collectAsState()
    // 升级卡下载状态（第四十五批）：订阅下载器状态，状态变了就按同一口径
    // 覆盖升级卡的事实／按钮／进度条（见 overlayUpdateCardDisplay）——
    // 只重组、不触发仓库 refresh（下载进度每 200ms 推一次，refresh 一次
    // 要重查日历／短信缓存／复盘卡，太贵）。必须放在提前返回之前——
    // collectAsState 调用在重组间顺序要稳定。
    val dlState by OtaDownloader.state.collectAsState()
    // 升级卡显示覆盖（第四十五批）：仓库建卡时的下载状态是快照，这里按
    // 实时下载状态重算事实／按钮／进度条；其余卡原样。id/kind/sourceKey/
    // sortMs/deadlineMs/action 不动（身份与排序只认仓库那张卡）。
    // remember 键挂 cards 与 dlState：下载进度每 200ms 推一次才重算，
    // 其余重组（分钟 ticker、通知变化）不重复读 prefs／文件。
    val displayCards = remember(cards, dlState) {
        cards.map { card ->
            if (card.kind != SmartCardKind.UPDATE) {
                card
            } else {
                val info = (card.action as? SmartCardAction.Update)?.info
                if (info == null) {
                    card
                } else {
                    overlayUpdateCardDisplay(
                        card,
                        dlState,
                        OtaUpdater.downloadTargetVersion(context),
                        OtaUpdater.isDownloadedVersion(context, info.versionCode)
                    )
                }
            }
        }
    }
    // 轮播跳动根因修（2026-11-24）：hero 安静只认叠层卡片列表的组成——
    // displayCards 是真卡列表（引导卡不含一览卡、空栈也是空列表，两种
    // 情况 isQuietHero 都是 false，hero 完整）。LaunchedEffect 只在安静
    // 与否翻转时上报，不跟轮播下标走。
    val quietHero = isQuietHero(displayCards)
    LaunchedEffect(quietHero) {
        onQuietHeroChange(quietHero)
    }
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
    // 本地日程详情 Dialog 的状态（第四十批，「看日程」退路）：机上没有能
    // 打开事件详情的日历应用时，在这里看日程（标题＋时间＋地点＋说明＋
    // 日历名）；null＝不弹。
    var localEventDetail by remember { mutableStateOf<CalEvent?>(null) }
    fun createReminderFor(card: SmartCard) {
        val action = card.action as? SmartCardAction.CreateReminder ?: return
        val created = createCalendarReminder(context, action)
        if (created) {
            SmartCardRepository.markReminderCreated(context, card)
        } else {
            IslandToasts.show("这次没建上提醒，稍后再试试吧")
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
        if (!opened) IslandToasts.show("这次没打开，稍后再试试吧")
    }
    // 「看日程」退路（第四十批）：performAction 返回 false（机上没有能
    // 打开事件详情的日历应用：包名解析与无包意图都解析不到，Bob 机上
    // 就是这种情况——只有谷歌 APP、没有事件详情 Activity），不弹死胡同
    // toast，改按事件 id 从系统日历 provider 查一场、在本地 Dialog 里
    // 看日程（标题＋时间＋地点＋说明＋日历名；用户主动触发，合规）。
    // 查不到事件（已被删）才退回原 toast。卡片不摘（performAction 失败
    // 本来就不记 markDone）。
    fun showLocalEventDetail(card: SmartCard) {
        val eventId = (card.action as? SmartCardAction.CalendarEvent)?.eventId
            ?: return
        val event = CalendarRepository.loadEventById(context, eventId)
        if (event != null) {
            localEventDetail = event
        } else {
            IslandToasts.show("这次没打开，稍后再试试吧")
        }
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
    // 本地日程详情 Dialog（第四十批，「看日程」退路）：机上没有能打开事件
    // 详情的日历应用时，在这里看日程。标题＝事件标题（空标题时退回
    // 「日程详情」），正文＝时间＋地点＋说明＋日历名
    // （calendarEventDetailLines）；点「知道了」或划掉关掉，卡片不摘。
    if (localEventDetail != null) {
        val event = localEventDetail!!
        AlertDialog(
            onDismissRequest = { localEventDetail = null },
            title = {
                Text(
                    text = event.title.ifBlank { "日程详情" },
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    calendarEventDetailLines(event, System.currentTimeMillis())
                        .forEach { line ->
                            Text(
                                text = line,
                                fontSize = AILauncherType.Body.fontSize,
                                color = AILauncherColors.current.Body
                            )
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { localEventDetail = null }) {
                    Text(
                        text = "知道了",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium
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
                cards = displayCards,
                prepById = prepById,
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
                        // 第四十批：日历卡「看日程」打不开时（机上没有能打开事件
                        // 详情的日历应用），弹本地日程详情 Dialog，不弹死胡同
                        // toast；其他卡仍弹原 toast。
                        if (!opened) {
                            if (card.action is SmartCardAction.CalendarEvent) {
                                showLocalEventDetail(card)
                            } else {
                                IslandToasts.show("这次没打开，稍后再试试吧")
                            }
                        }
                        opened
                    }
                },
                onIgnore = { card -> SmartCardRepository.markDone(context, card) },
                onBodyClick = { card ->
                    // 点预约卡正文（除按钮外）看原文：建提醒前是短信会话/
                    // 通知跳转，建完后按钮本身就是看预约、这里同动作。
                    if (card.kind == SmartCardKind.APPOINTMENT) viewAppointment(card)
                    // 点日历卡正文：与右按钮同一动作（加入会议 / 看日程）。
                    // 第十九批：时间段内点动作不算处理完、卡片不摘（performAction
                    // 内按 kind＋deadlineMs 判），只有「我知道了」或过了截止点才摘。
                    // 点总览卡正文：与右按钮同一动作（加入会议 / 看日程 / 打开看看）。
                    // 总览卡不是时间段卡（windowStartMs=0），点动作成功即摘卡。
                    if (card.kind == SmartCardKind.CALENDAR ||
                        card.kind == SmartCardKind.DAY_OVERVIEW
                    ) {
                        val opened = SmartCardActions.performAction(context, card)
                        // 第四十批：日历卡「看日程」打不开时弹本地日程详情
                        // Dialog（见 onAction 同款注释）；总览卡不是
                        // CalendarEvent 动作，仍弹原 toast。
                        if (!opened) {
                            if (card.action is SmartCardAction.CalendarEvent) {
                                showLocalEventDetail(card)
                            } else {
                                IslandToasts.show("这次没打开，稍后再试试吧")
                            }
                        }
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
    // 轮换推的代次（第十四批）：点「我知道了」一张引导卡时仓库改队列
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
    // 用户在叠层里点了右按钮（按来源给动作起名）的卡（等授权结果）；接上后先显示“已经连上了”
    // 反馈再离开。只对在叠层里点了右按钮的卡给反馈；在设置里接上
    // 的权限，其卡片直接消失、不补反馈。
    var pendingConnectIds by remember { mutableStateOf(setOf<String>()) }
    // 用户点右按钮那一刻的真实已接源个数（457 的 connectedSourceCount，
    // 同一处计数）：接上后拿它跟新个数一减，就是本次智能涨了多少。
    var pendingConnectLevels by remember { mutableStateOf(mapOf<String, Int>()) }
    // 正在显示“已经连上了”庆祝反馈的卡（3 秒后离开）：状态里带动效触发标志
    // celebrate 与等级增量 levelDelta（见 ConnectedFeedback），不是光一个 id。
    var connectedFeedbacks by remember {
        mutableStateOf(mapOf<String, ConnectedFeedback>())
    }
    val connectedIds: Set<String> = connectedFeedbacks.keys
    // 接上某个权限后，对应引导卡先显示“已经连上了”＋接下来会发生什么（3 秒），
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
    // “已经连上了”反馈停留 3 秒后离开（记永久忽略，不再出现）。
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
    // 对应卡片留在叠层里（pending 清掉，不给“已经连上了”反馈）。
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
                    text = "让我读读你的短信",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "我要读短信里的账单和体检预约提醒。只看最近 7 天的短信，只找账单和预约这两类，看完不留原文，也不会发到别处。",
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
 * 单卡槽位自动轮播（第三十三批，Bob 2026-11-14 22:44 定：单卡槽位
 * 自动轮播，无划走——划卡线全部作废）：
 * 一次只显示一张卡（当前最紧急的那张），不叠放、不露边、不画下一张
 * 的边。自动定时轮换（5 秒一档，≥2 张才轮，1 张不轮、0 张不画）；
 * 切卡一律一张接一张滑动（第四十批：点圆点／自动轮换也走 startSlideSwitch，
 * 与手动滑动同一条 300ms 时间线，减动效 120ms）——Crossfade 已撤：
 * 淡入淡出会在同一位置重叠两张相似卡的文字（Bob 2026-11-15 录屏：
 * 自动轮换的 Crossfade 在滑动前 300ms 重影，滑动时又闪），滑动不重叠、
 * 不重影。卡片下方圆点指示（当前张实心金、其余空心淡，
 * 点圆点跳到那一张，点后自动轮换暂停 8 秒再恢复）。
 * 卡片上挂手动滑动。点「我知道了」才摘卡（真卡 markDone／引导卡
 * 轮换推），摘完自动进下一张、圆点更新；摘完 1 张停轮、无圆点；
 * 摘完 0 张区域清空。0 张时提前返回（见函数头）。
 *
 * 切卡目标态只取卡 id（不是整张卡）：准备清单异步回来只改 prep，
 * 不触发一次切换；摘卡／列表变化时内容直接换（单帧，无重影）。
 * 圆点行与卡片同宽（两侧各 24dp 收边内），圆点居中。
 * 待办数角标已删（第二十七批）——叠层右上角不再画红 pill 计数。
 *
 * 每张卡两个响应：我知道了（消失、不再出现）与右按钮（卡片定义的下一步意图）。
 * onClose 非空时（引导卡片模式）× 在顶卡表头右上角、卡片内部：点一下
 * 整叠消失，不再出现；仍在叠层自己的槽位里，不另开浮层。
 * 摞高就是卡高（固定 192dp，SmartCardHeight）：单卡槽位不再量摞高，
 * SubcomposeLayout 的松约束量法随叠层一并退役（内容换卡时按钮被裁
 * 的自反馈死循环已不存在——每次只画一张卡，卡高固定）。
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
 * 卡片与圆点行都走同一处收边。
 */
val SmartCardStackSideMargin = 24.dp

/**
 * 叠层与 hero 的间距（重设计 §2.7）：8dp——Stageshow 桌面优化 3.5
 * （Bob 2026-11-21 定）：hero 与卡片收紧成一组，16dp→8dp；卡片两侧
 * 24dp 不动。8dp 网格。
 */
val SmartCardStackTopGap = 8.dp

/**
 * 卡片抬起阴影（Stageshow 桌面优化 3.2，Bob 2026-11-21 定）：呼吸边拿掉后，
 * 卡片靠面与阴影立住——柔和抬起阴影 8.dp（设计没给数字，Bob 可再调）。
 */
val SmartCardLiftShadow = 8.dp

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
 * 小标搬到标题行右上角后，右端再向内收 16dp；小标右缘最终距卡右缘
 * 14dp 内边距＋16dp 内收＝30dp。
 * （第二十七批：待办数角标已删，内收不再为留角标位置，只作小标
 * 右端留白；数值不动，小标位置不动。）
 */
val SmartCardSubtitleEndInset = 16.dp

/**
 * 应用小标画不画（第四十九批，Bob 2026-11-21 定审计 A2a）：未接来电卡不画小标——
 * 按钮“回拨”必然开拨号盘，小标“电话”不增加信息；其他真卡照常画（小标是右按钮
 * 动作的落点：去哪家应用）。引导卡本来就不画（下一步是去连接来源、不是打开
 * 某个应用）；“已经连上了”反馈卡也不画（hideActionRow）。纯函数，单测锁死
 * （SmartCardSubscriptTest）。
 */
fun shouldShowAppSubscript(card: SmartCard, hideActionRow: Boolean): Boolean =
    !hideActionRow && card.kind != SmartCardKind.GUIDE && card.kind != SmartCardKind.MISSED_CALL

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

// Stageshow 桌面优化 3.2（Bob 2026-11-21 定）：呼吸边整条拿掉——光晕常量
// （SmartCardHaloColor／SmartCardHaloStroke／SmartCardHaloInset／
// SmartCardHaloCornerRadius／SmartCardHaloMinAlpha／SmartCardHaloMaxAlpha／
// SmartCardHaloPeriodMs）、haloStrokeClearance 与 breathingBorderModifier
// 已删；卡片靠玻璃面（全档 0.75）＋抬起阴影立住，轮换间隙完全静止。

/**
 * 暖灰半透明衬底的透明度（重设计 §2.1）：全档 0.75——Stageshow 桌面优化 3.2
 * （Bob 2026-11-21 定）：呼吸边拿掉后卡片靠面立住，玻璃由约 0.55 抬到 0.75，
 * 有纹理壁纸也不再留 0.55（低对比环境的 0.75 兜底档即现行全档）。
 */
fun smartCardTintAlpha(backdropKind: BackdropKind?): Float = when (backdropKind) {
    BackdropKind.TEXTURED -> 0.75f
    else -> 0.75f
}

@Composable
fun SmartCardStack(
    cards: List<SmartCard>,
    onAction: (SmartCard) -> Boolean,
    onIgnore: (SmartCard) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    // 点卡片正文（除按钮外）——第十一批：预约卡看原文（短信会话/通知跳转）；
    // 其他卡没有正文动作，默认空实现。
    onBodyClick: (SmartCard) -> Unit = {},
    // 正在显示“已经连上了”庆祝反馈的卡（第八批）：不画动作行（没有“连接”可点），
    // 只显示“已经连上了”＋接下来会发生什么，外加庆祝形态（对勾弹入＋金色等级行
    // ＋整卡轻弹，见 SmartCardView）。反馈卡顶掉的是它自己的引导卡，不多占
    // 一张待办（cards.size 本就是替换后的张数）。
    connectedFeedbacks: Map<String, ConnectedFeedback> = emptyMap(),
    // 大模型准备清单（第十二批）：按卡片 id 的异步结果，覆盖卡片构建时
    // 的 prep（构建时为 null）。没回来就是没有，不贴模板。
    prepById: Map<String, String> = emptyMap(),
) {
    if (cards.isEmpty()) return
    // 轮播下标（第三十三批）：当前卡在轮播里的位置。点圆点跳卡改下标；
    // 点「我知道了」摘卡后列表缩短，下标可能越界——carouselClampIndex
    // 钳回末尾；摘掉当前卡后下标不动，下一张自然顶上来（“摘卡后自动进
    // 到下一张”）。
    var currentIndex by remember { mutableIntStateOf(0) }
    val index = carouselClampIndex(currentIndex, cards.size)
    // 点圆点后暂停自动轮换到这个时刻（第三十二批定 8 秒，第三十三批沿用）：
    // 点圆点后安静 8 秒再恢复 5 秒一档。
    var resumeAtMillis by remember { mutableLongStateOf(0L) }
    val reduceMotion = rememberReduceMotion()
    // 跟手拖动状态（第三十八批）：拖动位移（Animatable，纯视觉，跟手指走）、
    // 卡宽（松手判定用 1/4 卡宽）、拖动中（自动轮换暂停）、收尾任务。
    // 位移只走这一个 Animatable：拖动时 snapTo（1:1 跟手）、松手回弹
    // 时 animateTo 回 0（弹簧；减动效短 tween），不另设 Float 状态抢写——
    // 顶层 animate(初值, 终值, …) 在本工程 Compose 版本重载解析失败
    // （CI 实证），不用。换卡不再 tween 回 0（见 startSlideSwitch）。
    val dragScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // 一张接一张的滑动切换状态（第三十九批）：出卡 id（null＝没在切换）、
    // 进卡下标、滑动方向（＋1 左滑下一张、−1 右滑上一张）、松手时位移、
    // 切换进度（0→1，300ms／减动效 120ms，与淡入淡出同口径）、切换任务。
    // 第四十批：suppressCrossfade 已删——Crossfade 已撤，切卡收尾不再
    // 需要压住它（见 startSlideSwitch）。
    val slideProgress = remember { Animatable(0f) }
    var slideOutgoingId by remember { mutableStateOf<String?>(null) }
    var slideIncomingIndex by remember { mutableIntStateOf(0) }
    var slideDir by remember { mutableIntStateOf(0) }
    var slideReleaseOffset by remember { mutableFloatStateOf(0f) }
    var slideJob by remember { mutableStateOf<Job?>(null) }
    val isSliding = slideOutgoingId != null
    // 松手回弹（第三十九批）：只留给没过线的拖动——弹簧回弹到 0
    // （减动效时短 tween、不做弹簧过冲）。换卡不再走这里（见
    // startSlideSwitch：一张接一张滑出／滑入）。唯一写 dragOffsetX
    // 的收尾动画入口——先取消上一次收尾，保证同一时刻只有一个写者
    // （手势中途被取消、pointerInput 重启时 finally 里也会调这里，
    // 不会与拖动打架）。
    fun settleDragOffsetToZero() {
        settleJob?.cancel()
        settleJob = dragScope.launch {
            if (!reduceMotion) {
                dragOffsetX.animateTo(
                    0f,
                    spring(
                        dampingRatio = SMART_CARD_DRAG_SPRING_DAMPING,
                        stiffness = SMART_CARD_DRAG_SPRING_STIFFNESS
                    )
                )
            } else {
                dragOffsetX.animateTo(0f, tween(SMART_CARD_DRAG_SETTLE_MS))
            }
        }
    }
    // 点圆点／手动滑动跳卡（第三十三批点圆点、第三十七批手动滑动）：
    // 直接改下标，顺带把自动轮换暂停 8 秒。第四十批：自动轮换也走
    // startSlideSwitch（见下面的自动轮换计时），这里只留给点圆点／
    // 手动滑动的收尾（startSlideSwitch 里 manual=true 时调它）。
    fun jumpTo(target: Int) {
        currentIndex = target
        resumeAtMillis =
            System.currentTimeMillis() + SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS
    }
    // 准备清单覆盖：大模型回来了就显示，没回来显示构建时的 prep（null
    // 即没有，不贴模板）。copy 只换 prep，不动 id/动作/排序键。
    val connectedIds: Set<String> = connectedFeedbacks.keys
    // 一张接一张的滑动切换（第三十九批，Bob 2026-11-15：不要直接弹回来，
    // 要一个接一个的效果——536 换卡是位移 150ms 回 0，新卡从拖动位置沉回
    // 原位，用户看来就是卡直接弹回来）。松手判定换卡后：出卡不回 0，继续
    // 顺滑动方向滑到 ±1 卡宽外（轻微淡出），进卡从反方向滑入到中心；两张
    // 同一条 300ms 时间线（减动效 120ms，与淡入淡出同口径）。不回 521 的
    // 1600px 飞出。收尾才提交下标。
    // 第四十批：切卡一律走这里——点圆点／自动轮换也不再直接改下标
    // （直接改下标会触发 Crossfade；Crossfade 已撤，见文件头 KDoc：
    // 淡入淡出会在同一位置重叠两张相似卡的文字，自动轮换时重影、滑动时
    // 又闪）。manual=true（点圆点／手动滑动）时收尾走 jumpTo（改下标＋
    // 暂停自动轮换 8 秒）；manual=false（自动轮换）时收尾只改下标、不碰
    // resumeAtMillis（自动轮换保持 5 秒一档，不被 8 秒暂停拖慢）。
    // 收尾时滑入的卡已在中心、满不透明，直接换内容即可（单帧，无重影），
    // 不再需要压住 Crossfade（suppressCrossfade 已删）。
    fun startSlideSwitch(
        dir: Int,
        releaseOffset: Float,
        fromIndex: Int,
        targetIndex: Int,
        manual: Boolean
    ) {
        slideOutgoingId = cards[fromIndex].id
        slideIncomingIndex = targetIndex
        slideDir = dir
        slideReleaseOffset = releaseOffset
        slideJob?.cancel()
        slideJob = dragScope.launch {
            slideProgress.snapTo(0f)
            slideProgress.animateTo(
                1f,
                tween(durationMillis = crossfadeDurationMs(reduceMotion))
            )
            if (manual) jumpTo(targetIndex) else currentIndex = targetIndex
            slideOutgoingId = null
            dragOffsetX.snapTo(0f)
            slideProgress.snapTo(0f)
        }
    }
    // 自动轮换计时（第三十三批）：下标／张数／恢复时刻任一变就重计时；
    // 只有 ≥2 张才轮（carouselShouldAutoRotate），1 张不轮、0 张不画。
    // 第三十八批：isDragging 也进键——拖动一开始计时就重算，5 秒档
    // 不会半路切卡、把用户手里的卡换掉；拖完（回弹）重计时满 5 秒，
    // 拖完（换卡）走 jumpTo 的 8 秒暂停。
    // 第三十九批：isSliding 也进键——一张接一张滑出／滑入的 300ms 里
    // 不自动切卡；滑完 jumpTo 的 8 秒暂停接手。
    // 第四十批：到时也走 startSlideSwitch（manual=false：只改下标，
    // 不碰 resumeAtMillis，自动轮换保持 5 秒一档）——不再直接改下标
    // 触发 Crossfade（Crossfade 已撤，见文件头 KDoc）。
    LaunchedEffect(index, cards.size, resumeAtMillis, isDragging, isSliding) {
        if (isSliding) return@LaunchedEffect
        // 第四十五批：强更升级卡钉住时不自动轮走（它一直钉在叠层最上面，
        // 直到装上新版）；用户手动点圆点／拖动仍可去看别的卡（不是全屏阻塞）。
        if (!carouselShouldAutoRotate(cards, index)) return@LaunchedEffect
        delay(
            carouselWaitMs(
                SMART_CARD_CAROUSEL_INTERVAL_MS,
                resumeAtMillis,
                System.currentTimeMillis()
            )
        )
        startSlideSwitch(
            +1, 0f, index, carouselNextIndex(index, cards.size, +1), false
        )
    }
    // 手动滑动切卡（第三十八批跟手、第三十九批一张接一张，Bob 2026-11-15：
    // 536 的位移 150ms 回 0 像直接弹回来，要一个接一个的效果）。
    // 跟手：拖动过程中顶卡横向位移跟手指走（1:1，夹到半张卡宽内）；
    // 松手判定（纯函数 carouselReleaseDecision）：位移超 1/4 卡宽或松手
    // 速度够就换卡（出卡继续滑出、进卡从反方向滑入，一张接一张，见
    // startSlideSwitch），否则弹簧回弹到 0。
    // 不要回 521 的飞出（1600px 大位移）、不要回 523 的左右滑切卡整套；
    // 跟手只做拖动跟随＋松手判定。位移是纯视觉的：拖动不改下标、不调仓库，
    // 换卡只在松手判定后走 startSlideSwitch（收尾 jumpTo，与点圆点同一条
    // 链：改下标＋暂停自动轮换 8 秒）。点按与竖翻由 Compose 手势仲裁保证
    // 互不抢占：横向先过 slop 才认领（点按不触发，按钮照常可点）；竖向先过
    // slop 就被卡内 verticalScroll 认领，本次手势直接返回
    // （awaitHorizontalTouchSlopOrCancellation 返回 null），卡片原地不动。
    // 滑动切换进行中（300ms）不认领新手势，等这一张滑完再滑。≥2 张才挂，
    // 1 张不触发。
    val flingThresholdPx = with(LocalDensity.current) {
        SMART_CARD_FLING_VELOCITY_DP_PER_SEC.dp.toPx()
    }
    val dragModifier = if (cards.size >= 2) {
        Modifier.pointerInput(cards.size, index) {
            try {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 横向先过 slop 才认领；竖向先过 slop／点按则返回 null。
                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    } ?: return@awaitEachGesture
                    // 滑动切换进行中不认领新手势（300ms，等这一张滑完再滑）。
                    if (slideOutgoingId != null) return@awaitEachGesture
                    // 上一次收尾没跑完就再拖：先停掉它，从当前位置跟手。
                    settleJob?.cancel()
                    isDragging = true
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    tracker.addPosition(slopChange.uptimeMillis, slopChange.position)
                    try {
                        horizontalDrag(down.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // 增量＝当前位置减上一位置（positionChange() 在本工程
                            // Compose 版本未解析，改用属性相减，等价）。
                            val dx = change.position.x - change.previousPosition.x
                            val target = carouselDragOffsetClamp(
                                dragOffsetX.value + dx,
                                cardWidthPx
                            )
                            dragScope.launch { dragOffsetX.snapTo(target) }
                            change.consume()
                        }
                    } finally {
                        isDragging = false
                    }
                    val velocity = tracker.calculateVelocity().x
                    val dir = carouselReleaseDecision(
                        dragOffsetX.value, cardWidthPx, velocity, flingThresholdPx
                    )
                    if (dir != 0) {
                        // 一张接一张（第三十九批）：出卡继续滑出、进卡从反
                        // 方向滑入，不 tween 回 0、不直接弹回来。
                        startSlideSwitch(
                            dir,
                            dragOffsetX.value,
                            index,
                            carouselNextIndex(index, cards.size, dir),
                            true
                        )
                    } else {
                        settleDragOffsetToZero()
                    }
                }
            } finally {
                // 手势中途被取消（如下标／张数变了、pointerInput 重启）：
                // 位移可能停在半路，收尾回 0，不留一张歪卡；已在收尾的
                // 不重复启动（settleDragOffsetToZero 自己会取消上一个）。
                // 滑动切换进行中不管（拖动位移冻在松手位，滑完 snapTo 0）。
                isDragging = false
                if (slideOutgoingId == null && dragOffsetX.value != 0f &&
                    settleJob?.isActive != true
                ) {
                    settleDragOffsetToZero()
                }
            }
        }
    } else Modifier
    // 第十批：卡片按桌面卡片语言收边——两侧各留 SmartCardStackSideMargin，
    // 不贴边、不满宽。收边放在最外层 Column：卡片与圆点行一起收。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SmartCardStackSideMargin)
    ) {
        // 叠层与 hero 的间距（重设计 §2.7，Bob 2026-11-11 批准）：16dp——
        // 叠层顶缘锚在 hero 底部（讲述句）下方 16dp，吃掉 486 拿掉会行腾出的
        // 约 56dp。× 不再占这一行（448 的 × 行在卡外、悬在壁纸上）；× 进卡内表头（见 SmartCardView），
        // 这里两模式统一只留段间距。
        Spacer(modifier = Modifier.height(SmartCardStackTopGap))
        // 单卡槽位（第三十三批）：只画当前下标那一张。第四十批：Crossfade
        // 已撤——非滑动时也只直画当前卡，不再淡入淡出（Crossfade 在同一
        // 位置以互补透明度同画两张相似卡，文字重叠成鬼影，见文件头 KDoc）。
        // 摘卡／列表变化时内容直接换（单帧，无重影）。
        // 第三十八批：手动滑动改跟手（拖动位移跟手指走、松手判定换卡／回弹，
        // 见 dragModifier）；第三十七批“只做手势触发、不做动画”就此作废。
        // 第三十九批：手动滑动松手换卡改一张接一张——出卡继续顺滑动方向
        // 滑出（到 ±1 卡宽外、轻微淡出），进卡从反方向滑入到中心，同一条
        // 300ms 时间线（见 slide layers）；拖动跟手与松手判定不动；536
        // 「换卡位移 150ms 回 0」就此作废。第四十批：点圆点／自动轮换也
        // 走 startSlideSwitch（同一条滑动时间线），不再直接改下标。
        // 手势与量宽挂在外层 Box 上（不在切换层上）：切换层互换时 Box 不动，
        // pointerInput 不重启，触发滑动的那条手势协程不会被滑动状态的
        // 变更取消。第四十批：外层 Box 加 clipToBounds——出卡滑到 ±1 卡宽
        // 时右缘还剩 24dp 侧边距的一条边露在屏上，滑完切回单卡渲染时这条
        // 边一帧消失（小 pop）；在槽位边裁掉，p=1 时出卡完全不可见。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .then(dragModifier)
        ) {
            if (slideOutgoingId != null) {
                // 一张接一张（第三十九批）：出卡与进卡各一层，同向走——
                // 出卡从松手位移继续滑到 ±1 卡宽外（轻微淡出），进卡从反方向
                // ±1 卡宽外滑入到中心；两张不重叠（同向差一整张卡宽）。
                // 第四十一批：进卡起点带上松手位移（见 carouselSlideInOffset），
                // 否则手动滑动时两张在起点拉开 |松手位移| 的蓝底缝（538 闪）。
                // 摘卡／过期让某张中途没了就空着那一层（find／getOrNull 兜底），
                // 收尾 jumpTo 会钳制下标，不崩。
                val progress = slideProgress.value
                val outRaw = cards.find { it.id == slideOutgoingId }
                val inRaw = cards.getOrNull(
                    carouselClampIndex(slideIncomingIndex, cards.size)
                )
                if (outRaw != null) {
                    val outCard = outRaw.copy(prep = prepById[outRaw.id] ?: outRaw.prep)
                    SmartCardView(
                        card = outCard,
                        onActionClick = { onAction(outCard) },
                        onIgnoreClick = { onIgnore(outCard) },
                        onCloseClick = onClose,
                        onBodyClick = { onBodyClick(outCard) },
                        hideActionRow = outCard.id in connectedIds,
                        celebration = connectedFeedbacks[outCard.id],
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = carouselSlideOutOffset(
                                    progress, slideReleaseOffset, slideDir, cardWidthPx
                                )
                                alpha = carouselSlideOutAlpha(progress)
                            }
                    )
                }
                if (inRaw != null) {
                    val inCard = inRaw.copy(prep = prepById[inRaw.id] ?: inRaw.prep)
                    SmartCardView(
                        card = inCard,
                        onActionClick = { onAction(inCard) },
                        onIgnoreClick = { onIgnore(inCard) },
                        onCloseClick = onClose,
                        onBodyClick = { onBodyClick(inCard) },
                        hideActionRow = inCard.id in connectedIds,
                        celebration = connectedFeedbacks[inCard.id],
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = carouselSlideInOffset(
                                    progress, slideDir, cardWidthPx, slideReleaseOffset
                                )
                            }
                    )
                }
            } else {
                // 第四十批：直画当前卡——准备清单异步回来只改 prep，不触发
                // 一次切换；摘卡／列表变化时内容直接换（单帧，无重影）。
                // 跟手位移仍挂在这里（拖动时整张卡跟手指走）。
                // 第四十二批：拖动时垫一张在下面——只画当前一张时，拖开
                // 就露蓝底（539 闪的根因）；垫卡是拖动方向上的下一张／
                // 上一张，位移＝拖动位移＋dir×卡宽（carouselDragUnderOffset），
                // 与当前卡全程恰好差一整张卡宽，拖动露出的是下一张、不
                // 是蓝底；松手换卡时滑入起点与垫卡位置一致，拖动到滑动
                // 无跳变。只在拖动位移不为 0 时画（静止时垫卡在 ±卡宽外、
                // 被 clipToBounds 裁掉，不多画一张）；1 张时不垫。垫卡先
                // 画、在当前卡下面。
                val dragOffset = dragOffsetX.value
                if (cards.size >= 2 && dragOffset != 0f && cardWidthPx > 0f) {
                    val dragDir = if (dragOffset < 0f) +1 else -1
                    val underRaw = cards.getOrNull(
                        carouselNextIndex(index, cards.size, dragDir)
                    )
                    if (underRaw != null) {
                        val underCard =
                            underRaw.copy(prep = prepById[underRaw.id] ?: underRaw.prep)
                        SmartCardView(
                            card = underCard,
                            onActionClick = { onAction(underCard) },
                            onIgnoreClick = { onIgnore(underCard) },
                            onCloseClick = onClose,
                            onBodyClick = { onBodyClick(underCard) },
                            hideActionRow = underCard.id in connectedIds,
                            celebration = connectedFeedbacks[underCard.id],
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = carouselDragUnderOffset(
                                        dragOffset, dragDir, cardWidthPx
                                    )
                                }
                        )
                    }
                }
                val raw = cards[index]
                val card = raw.copy(prep = prepById[raw.id] ?: raw.prep)
                SmartCardView(
                    card = card,
                    onActionClick = { onAction(card) },
                    onIgnoreClick = { onIgnore(card) },
                    onCloseClick = onClose,
                    onBodyClick = { onBodyClick(card) },
                    hideActionRow = card.id in connectedIds,
                    celebration = connectedFeedbacks[card.id],
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = dragOffsetX.value }
                )
            }
        }
        // 圆点指示（第三十二批，第三十三批沿用样式与点按）：≥2 张才画——
        // 当前张实心金、其余空心淡；点圆点跳到那一张（点后自动轮换暂停
        // 8 秒）。1 张不画圆点、也不自动轮换。
        if (cards.size >= 2) {
            Spacer(modifier = Modifier.height(SmartCardCarouselDotGap))
            CarouselDots(
                count = cards.size,
                current = index,
                onDotClick = { target ->
                    // 滑动切换进行中不响应点圆点（300ms，等这一张滑完）。
                    // 第四十批：点圆点也走 startSlideSwitch（一张接一张滑入），
                    // 不再直接 jumpTo（直接改下标会触发 Crossfade；Crossfade
                    // 已撤）。方向取最短环向（见 carouselDotTapDir）。
                    if (slideOutgoingId == null && target != index) {
                        startSlideSwitch(
                            carouselDotTapDir(index, target, cards.size),
                            0f,
                            index,
                            target,
                            true
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 划卡诊断行已拆（第三十一批，Bob 2026-11-14 18:46 亲口）：
        // 519 立的临时出口使命已完成（证明了手势通、沉底通、飞出通），
        // 叠层下方不再留调试小字。
    }
}

/**
 * 轮播圆点指示（第三十二批）：当前张实心深灰、其余空心淡；点圆点跳到那一张。
 * Hero 无现成圆点可抄（CoverHero 无圆点），样式取主题 token：当前张 Title
 * 深灰（Stageshow 桌面优化 3.3，Bob 2026-11-21 定：金色只留主操作——
 * 右按钮与时间冒号；圆点当前态的金色收回）、其余 Hint 40% 透明度；
 * 尺寸走 8dp 网格（6dp 点、8dp 间距）。
 * 点按：整行一个 pointerInput，按落点 x 换算到最近的圆点（不给每个 6dp
 * 点单独挂 clickable——点太小，触控区会重叠、点到相邻的点）。行高 32dp，
 * 圆点居中；落点消费掉，不落到叠层下层。pointerInput 的键挂上 count 与
 * current——下标变了就用新的 onDotClick（旧闭包里的 index 会过期）。
 */
/**
 * 轮播圆点颜色（纯函数，单测锁死；Stageshow 桌面优化 3.3）：当前张实心
 * Title 深灰、其余 Hint 40% 透明度。金色只留主操作（右按钮与时间冒号），
 * 圆点当前态不用金。
 */
internal fun carouselDotColor(selected: Boolean, palette: AILauncherColors.Palette): Color =
    if (selected) palette.Title else palette.Hint.copy(alpha = 0.4f)

@Composable
private fun CarouselDots(
    count: Int,
    current: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .height(32.dp)
            .pointerInput(count, current) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pitch = with(density) {
                        (SmartCardCarouselDotSize + SmartCardCarouselDotGap).toPx()
                    }
                    val dot = with(density) { SmartCardCarouselDotSize.toPx() }
                    val rowWidth = count * dot + (count - 1) * (pitch - dot)
                    val startX = (size.width - rowWidth) / 2f
                    val idx = ((down.position.x - startX + pitch / 2f) / pitch)
                        .toInt().coerceIn(0, count - 1)
                    onDotClick(idx)
                    down.consume()
                }
            }
            .testTag("smart_card_carousel_dots"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SmartCardCarouselDotGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until count) {
                val selected = i == current
                Box(
                    modifier = Modifier
                        .size(SmartCardCarouselDotSize)
                        .clip(CircleShape)
                        .background(carouselDotColor(selected, AILauncherColors.current))
                )
            }
        }
    }
}

/**
 * 切卡滑动时长（毫秒，第三十三批）：点圆点／自动轮换切卡只用淡入淡出
 * （Crossfade），不飞出、不揭示——动效只有透明度一路。第三十九批起
 * 一张接一张的滑动切换也用同一档（300ms／减动效 120ms）。
 * 第四十批：Crossfade 已撤，切卡一律一张接一张滑动，300ms／120ms
 * 现为滑动时长；函数名与常量名沿用（不改名、不动测试 import）。
 * 300ms——看得见切换、又不拖沓；减动效时 120ms，缩短但不硬切。
 * 纯函数 crossfadeDurationMs 选档，单测锁死。
 */
internal const val SMART_CARD_CROSSFADE_MS = 300
internal const val SMART_CARD_CROSSFADE_REDUCED_MS = 120

/** 淡入淡出时长选档（纯函数，单测锁死）：正常 300ms，减动效 120ms。第四十批：
 * Crossfade 已撤，现为切卡滑动时长（函数名与常量名沿用）。 */
internal fun crossfadeDurationMs(reduceMotion: Boolean): Int =
    if (reduceMotion) SMART_CARD_CROSSFADE_REDUCED_MS else SMART_CARD_CROSSFADE_MS

/**
 * 轮播自动轮换间隔（毫秒，第三十二批）：Hero 无现成间隔可抄——
 * CoverHero 已收敛到 GIANT_TIME 一个模板（2026-11-21，Stageshow 桌面
 * 优化：三模板轮换已删），无定时器、无圆点、无手动切。取 5 秒，
 * 常见轮播档；Bob 可再定。
 */
internal const val SMART_CARD_CAROUSEL_INTERVAL_MS = 5000L

/**
 * 点圆点跳卡后暂停自动轮换的时长（毫秒，第三十二批定 8 秒，第三十三批沿用）：
 * 点圆点后安静 8 秒再恢复 5 秒一档。
 */
internal const val SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS = 8000L

/**
 * 跟手拖动松手判定的距离线（第三十八批立 1/3，第三十九批放宽到 1/4）：
 * 松手时 |位移| 超 1/4 卡宽就换卡（方向＝位移方向：左滑下一张、右滑上一张）。
 * 放宽原因（第三十九批复核，Bob 2026-11-15：正常一滑要可靠触发换卡，
 * 不要滑了还弹回来）：卡宽约 363dp 时 1/3 ≈ 121dp 是一记很长的拖动，
 * 正常一滑容易没过线就回弹；1/4 ≈ 91dp 是一记正常滑动的距离。只许往
 * 更灵敏调。速度线 500dp/s 不动（轻扫的速度远超此线，距离线才是决定项）。
 * 纯函数 carouselReleaseDecision 的一部分，单测锁死。
 */
internal const val SMART_CARD_DRAG_RELEASE_FRACTION = 1f / 4f

/**
 * 跟手拖动位移的夹子（第三十八批）：位移跟手指 1:1 走，但夹到半张卡宽内——
 * 卡不飞出屏（不要回 521 的 1600px 飞出），拖过半张再拖手指只走、卡不动
 * （到墙了）。纯函数 carouselDragOffsetClamp 的一部分，单测锁死。
 */
internal const val SMART_CARD_DRAG_MAX_FRACTION = 0.5f

/**
 * 跟手拖动松手判定的速度线（第三十八批）：松手速度（dp/s）够快也换卡——
 * 轻扫一下不用拖满 1/4 卡宽。500dp/s 是一记有意的轻扫，不是慢拖；
 * 慢拖靠距离线判定。纯函数 carouselReleaseDecision 的一部分，单测锁死。
 */
internal const val SMART_CARD_FLING_VELOCITY_DP_PER_SEC = 500

/**
 * 跟手拖动松手回弹的减动效时长（毫秒，第三十八批）：150ms——减动效时
 * 不用弹簧、改短 tween 回 0。第三十九批起换卡不再 tween 回 0（改一张
 * 接一张滑出／滑入），这个值只留给减动效时的回弹。
 */
internal const val SMART_CARD_DRAG_SETTLE_MS = 150

/**
 * 跟手拖动松手回弹的弹簧参数（第三十八批）：没过线时弹簧回弹到 0——
 * 阻尼 0.8（轻回弹、不来回晃）、刚度 800（约 200ms 内回到位）。
 * 减动效时不用弹簧、改 150ms 短 tween（见 settleDragOffsetToZero）。
 */
internal const val SMART_CARD_DRAG_SPRING_DAMPING = 0.8f
internal const val SMART_CARD_DRAG_SPRING_STIFFNESS = 800f

/** 轮播圆点直径（第三十二批）：6dp；圆点间距 8dp（8dp 网格）。 */
val SmartCardCarouselDotSize = 6.dp
val SmartCardCarouselDotGap = 8.dp

/**
 * 轮播下一张下标（纯函数，第三十二批单测锁死）：dir +1＝下一张、
 * -1＝上一张，首尾相接循环；只有 0／1 张时下标恒为 0（没有下一张可切）。
 */
internal fun carouselNextIndex(current: Int, size: Int, dir: Int): Int {
    if (size <= 1) return 0
    return ((current + dir) % size + size) % size
}

/**
 * 点圆点跳卡的滑动方向（纯函数，第四十批，单测锁死）：点的那张是循环
 * 下一张就 +1（左滑入），否则 −1（右滑入）——取最短环向。2 张时点的
 * 那张一定是循环下一张（+1）；3 张以上点非相邻的圆点时按环向最短的
 * 一边滑（不跨两张）。size<2 时返回 0（没有下一张可切，调用方不调）。
 */
internal fun carouselDotTapDir(current: Int, target: Int, size: Int): Int {
    if (size < 2) return 0
    return if (target == carouselNextIndex(current, size, +1)) +1 else -1
}

/**
 * 跟手拖动位移夹子（纯函数，单测锁死）：位移夹到 ±半张卡宽内；
 * 卡宽没量到（≤0）时返回 0——量不到就不拖。
 */
internal fun carouselDragOffsetClamp(offsetPx: Float, cardWidthPx: Float): Float {
    if (cardWidthPx <= 0f) return 0f
    val max = cardWidthPx * SMART_CARD_DRAG_MAX_FRACTION
    return offsetPx.coerceIn(-max, max)
}

/**
 * 跟手拖动松手判定（纯函数，单测锁死）：位移 offsetPx（左滑为负）、
 * 卡宽 cardWidthPx、松手速度 velocityPxPerSec（左为负）、速度线
 * velocityThresholdPxPerSec。返回 +1 下一张、−1 上一张、0 回弹。
 * 距离优先：|位移| 超 1/4 卡宽就按位移方向换卡；位移没过线再看速度，
 * 速度过线按速度方向换卡；都没过线回弹。501–523 的教训是判定耦合了
 * 仓库沉底与飞出动画、修一处漏一处；本批判定只看手势物理量，换卡
 * 只走 startSlideSwitch（收尾 jumpTo），两边不耦合——位移是纯视觉，
 * 拖动不改下标、不调仓库。
 */
internal fun carouselReleaseDecision(
    offsetPx: Float,
    cardWidthPx: Float,
    velocityPxPerSec: Float,
    velocityThresholdPxPerSec: Float
): Int {
    if (cardWidthPx <= 0f) return 0
    val distanceDir = when {
        offsetPx <= -cardWidthPx * SMART_CARD_DRAG_RELEASE_FRACTION -> +1
        offsetPx >= cardWidthPx * SMART_CARD_DRAG_RELEASE_FRACTION -> -1
        else -> 0
    }
    if (distanceDir != 0) return distanceDir
    return when {
        velocityPxPerSec <= -velocityThresholdPxPerSec -> +1
        velocityPxPerSec >= velocityThresholdPxPerSec -> -1
        else -> 0
    }
}

/**
 * 一张接一张滑出位移（纯函数，第三十九批，单测锁死）：出卡从松手时的位移
 * releaseOffsetPx 出发，沿滑动方向继续滑到 ±1 张卡宽外。dir +1＝左滑
 * （下一张，出卡向左、终点 −卡宽）、−1＝右滑（上一张，出卡向右、终点
 * ＋卡宽）。progress 0→1 与 300ms 动画同步（减动效 120ms）。卡宽没量到
 * （≤0）时返回松手位移（不动，等收尾 snapTo 0）。
 */
internal fun carouselSlideOutOffset(
    progress: Float,
    releaseOffsetPx: Float,
    dir: Int,
    cardWidthPx: Float
): Float {
    if (cardWidthPx <= 0f) return releaseOffsetPx
    val end = -dir * cardWidthPx
    return releaseOffsetPx + (end - releaseOffsetPx) * progress
}

/**
 * 一张接一张滑入位移（纯函数，第三十九批，单测锁死）：进卡从反方向
 * ±1 张卡宽外滑入到中心。dir +1＝左滑（进卡从右侧＋卡宽处进来）、
 * −1＝右滑（进卡从左侧 −卡宽处进来）。progress 0→1。卡宽没量到
 * （≤0）时返回 0。
 * 第四十一批：进卡起点要带上松手位移 releaseOffsetPx——出卡从松手位移
 * 出发（见 carouselSlideOutOffset），进卡若仍从 ±卡宽起步，两张在
 * 滑动起点就拉开 |releaseOffset| 的蓝底缝（538 闪的根因：Bob 录屏里
 * 手动滑动时出卡与进卡之间闪过 250–306px 蓝底带，缝宽＝松手位移量，
 * 随进度收到 0）；起点改成 dir×卡宽＋松手位移，两张全程恰好差一整张
 * 卡宽、无缝无重叠（单测 slideOffsets_withReleaseOffset_stayExactlyOneWidthApart
 * 锁死）。自动轮换／点圆点松手位移为 0，行为与原来一致。
 */
internal fun carouselSlideInOffset(
    progress: Float,
    dir: Int,
    cardWidthPx: Float,
    releaseOffsetPx: Float = 0f
): Float {
    if (cardWidthPx <= 0f) return 0f
    val start = dir * cardWidthPx + releaseOffsetPx
    return start + (0f - start) * progress
}

/**
 * 拖动时垫在下面的卡（纯函数，第四十二批，单测锁死）：手动拖动时当前卡
 * 跟手指走，下面那张（拖动方向上的下一张／上一张）也要跟着走——拖动
 * 只画当前一张时，拖开就露蓝底（539 闪的根因：Bob 录屏里手动拖动
 * 时卡后露出 100–420px 蓝底带，0.2 秒内从 0 长到 420px）。dir
 * +1＝向左拖（下一张在右侧）、−1＝向右拖（上一张在左侧）。垫卡位移
 * ＝拖动位移＋dir×卡宽，与当前卡全程恰好差一整张卡宽、无缝无重叠；
 * 松手换卡时滑入起点与垫卡位置一致（见 carouselSlideInOffset，
 * p＝0 时滑入位移＝dir×卡宽＋松手位移），拖动到滑动无跳变。卡宽没
 * 量到（≤0）时返回拖动位移。
 */
internal fun carouselDragUnderOffset(
    dragOffsetPx: Float,
    dir: Int,
    cardWidthPx: Float
): Float {
    if (cardWidthPx <= 0f) return dragOffsetPx
    return dragOffsetPx + dir * cardWidthPx
}

/**
 * 一张接一张滑出透明度（纯函数，第三十九批，单测锁死）：出卡滑出时
 * 轻微淡出，1→0.6；淡出是配角、位移是主角。进卡不淡出（纯滑入，
 * 全程不透明）。
 */
internal fun carouselSlideOutAlpha(progress: Float): Float =
    (1f - 0.4f * progress).coerceIn(0f, 1f)

/**
 * 轮播是否自动轮换（纯函数，第三十二批单测锁死）：≥2 张才轮换，
 * 1 张不轮、0 张不画叠层。
 */
internal fun carouselShouldAutoRotate(size: Int): Boolean = size >= 2

/**
 * 轮播是否自动轮换（列表版，第四十五批，单测锁死）：张数口径与上面
 * 同一条；外加强更升级卡钉住时不轮——当前这张是 pinned（强更）就停住，
 * 直到装上新版卡片消失、或用户手动点圆点／拖动离开；手动离开后恢复
 * 正常轮换（轮回来时到强更卡又停住）。手动切换本身不禁（不是全屏阻塞，
 * 用户还能用桌面、还能去看别的卡）。
 */
internal fun carouselShouldAutoRotate(cards: List<SmartCard>, index: Int): Boolean =
    carouselShouldAutoRotate(cards.size) &&
        cards.getOrNull(index)?.pinned != true

/**
 * 轮播下标钳制（纯函数，第三十二批单测锁死）：摘卡后列表缩短，下标
 * 可能越界，钳回末尾；空列表为 0。
 */
internal fun carouselClampIndex(index: Int, size: Int): Int =
    if (size <= 0) 0 else index.coerceIn(0, size - 1)

/**
 * 自动轮换等待时长（纯函数，第三十二批单测锁死）：正常一档间隔；
 * 点圆点后恢复时刻未到就等到恢复时刻（点圆点后安静 8 秒再恢复）。
 * resumeAtMillis 已过时等一档间隔（coerceAtLeast 兜底，不等 0、不立即连转）。
 * 注：kotlin.math.maxOf 在此 Kotlin 版本不存在（516 实证），用 coerceAtLeast。
 */
internal fun carouselWaitMs(intervalMs: Long, resumeAtMillis: Long, nowMillis: Long): Long =
    intervalMs.coerceAtLeast(resumeAtMillis - nowMillis)

/**
 * 单张卡片（第三十三批：单卡槽位，一次只画一张卡）：
 * 来源标签＋一句话事实（含截止点）＋（可选）一行准备清单＋
 * 一行两个响应：我知道了（左）与右按钮（右，第十二批 Bob 定：显示下一步
 * 的意图，去还款 / 帮我建提醒 / 看预约 / 回拨 / 打开看看 / 加入会议 / 看日程 /
 * 按来源给动作起名；动作含义由事实文案＋右下方应用小标补充）。
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
    // “已经连上了”反馈卡：不画动作行（忽略/连接两个按钮与露边提示都不画），
    // 只留来源标签＋“已经连上了”＋接下来会发生什么。
    hideActionRow: Boolean = false,
    // “已经连上了”庆祝反馈（第八批，设计稿 §4.7）：非空时卡片换成庆祝形态——
    // 金色对勾弹入＋金色等级行（“已接上 3/4 个来源”）＋整卡轻弹。
    // 颜色只用已有的主操作金 Accent / AccentSoft，不引新色；图标是线性
    // Check，不用 emoji；尺寸走 8dp 网格（40dp 圆、8dp 间距）；减动效时
    // 直接落到终态，不播动画。文案仍按 Bob 的两条写。
    celebration: ConnectedFeedback? = null,
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
    // 正文滚动：事实＋准备清单＋已建提醒一起在卡内滚动。
    // 第四十三批（2026-11-15）：正文底部渐隐（factFade）已删——它用
    // BlendMode.DstIn 在正文区底部 25% 画垂直渐变，DstIn 会把同一画布上
    // 已画的卡片衬底也擦掉；切卡收尾换分支那一帧它瞬时激活一次，衬底被擦
    // 掉就透出蓝天壁纸，成一道横着的蓝底条（闪的根因）。长文改底部裁切，
    // 竖翻照常。
    val factScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .shadow(elevation = SmartCardLiftShadow, shape = shape)
            .clip(shape)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .glassSurface(
                kind = GlassSurfaceKind.STRONG,
                cornerRadius = 20.dp,
                tintColor = AILauncherColors.current.Card,
                fallbackColor = AILauncherColors.current.GlassCard,
                // 均匀玻璃（第五十四批，Bob 2026-11-26 定）：跳过强玻璃锐利
                // 折射层，整卡只剩模糊层 + 叠色，不再有 30dp 边缘带分界
                // （分界线曾压在表头文字下沿，读成卡内一个更透的方框）；
                // Dock 等其他玻璃面不传此参数，一字不动。
                uniformGlass = true
            )
            .background(
                color = tint,
                shape = shape
            )
            .height(SmartCardHeight)
            .padding(SmartCardContentPadding),
        verticalArrangement = Arrangement.spacedBy(SmartCardRowGap)
    ) {
        // 表头（重设计 §2.8，第十六批）：来源标签在左上角；应用小标
        // （16dp 图标＋12sp 应用名）搬到右上角、与来源标签同一行——
        // 小标本身不变（次要色、无底色无边框、只展示不可点、最宽 160dp），
        // 只是换位置、不丢信息；右端再向内收 SmartCardSubtitleEndInset
        // （第二十七批：角标已删，内收只作右端留白）。引导卡没有小标
        // （下一步是去连接来源、不是打开某个应用），只有卡内 ×；“已经连上了”反馈卡也不画
        // （hideActionRow 时不画，见下）；未接来电卡也不画小标（第四十九批，
        // Bob 2026-11-21 定审计 A2a：按钮“回拨”已隐含拨号盘）。× 落在卡片内部、48dp 触控；
        // 448 的 × 是卡外单独一行、悬在壁纸上（实拍右上角卡外），改掉。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SmartCardTitleRowHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stageshow 桌面优化 3.4：来源标签与 hero 刊头同一体（小字、tracking、
            // Title 深灰——金色只留主操作，见 3.3；视觉稿里标签的金色是稿件
            // 不准确处，以设计文档文字为准）。
            TrackedText(
                text = card.sourceLabel,
                fontSize = AILauncherType.Caption.fontSize,
                tracking = 4.dp,
                color = AILauncherColors.current.Title
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
            } else if (shouldShowAppSubscript(card, hideActionRow)) {
                card.targetApp?.let { target ->
                    AppSubscript(
                        targetApp = target,
                        modifier = Modifier.padding(end = SmartCardSubtitleEndInset)
                    )
                }
            }
        }
        // 庆祝行（第八批）：金色对勾＋等级变化。只在“已经连上了”反馈卡上画，
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
                        contentDescription = "已经连上了",
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
        // 不是截断——maxLines=3＋Ellipsis 已删；长文底部直接裁切（第四十三批
        // 删了 factFade 渐隐，见上）。
        // 高度就是 weight(1f) 撑满卡高减去其余行（不另加 min/max 钳制——
        // min 会顶穿引导卡的 48dp × 表头、max 会把按钮顶离底边留白）。
        // 正文竖翻（第三十三批：卡片上不再有横滑手势，竖向滚动不再与
        // 横向仲裁竞争——2026-11-13 横滑切卡 bug 的根因（竖向检测器消费
        // 一条 move 就能取消横向拖拽）随横滑一并作废）；点正文仍是看原文
        // （预约／日历卡）。
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(factScrollState)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 结论句加粗（第四十八批「今天一览」重设计）：leadBold 时 fact
                // 第一行（结论句）画 SemiBold、其余行 Medium；其余卡照常全 Medium。
                val factText: AnnotatedString = if (card.leadBold) {
                    buildAnnotatedString {
                        val firstBreak = card.fact.indexOf('\n')
                        val leadEnd = if (firstBreak < 0) card.fact.length else firstBreak
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(card.fact.substring(0, leadEnd))
                        }
                        if (firstBreak >= 0) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                                append(card.fact.substring(firstBreak))
                            }
                        }
                    }
                } else {
                    AnnotatedString(card.fact)
                }
                Text(
                    text = factText,
                    fontSize = AILauncherType.Body.fontSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = AILauncherType.Body.lineHeight,
                    color = AILauncherColors.current.Title,
                    modifier = if (onBodyClick != null) {
                        Modifier.clickable(onClick = onBodyClick)
                    } else Modifier
                )
                // 下载进度条（第四十五批）：升级卡下载中才有（card.progress
                // 0..1），画在事实下方；事实本身已带「（xx%）」，这里只画条。
                card.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                // 左按钮文案按卡种：默认「我知道了」，设默认卡是「取消」
                // （card.dismissLabel，Bob 2026-11-14 07:33 亲令）。
                // 第四十五批：强更升级卡不出左按钮（hideDismissButton，
                // 只剩「去更新」）；左按钮藏起时右按钮仍贴右（Spacer 顶住）。
                if (!card.hideDismissButton) {
                    TextButton(
                        onClick = onIgnoreClick,
                        modifier = Modifier.heightIn(min = SmartCardActionRowMinHeight)
                    ) {
                        Text(
                            text = card.dismissLabel,
                            fontSize = AILauncherType.Callout.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Hint
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                // 右按钮（第十二批 Bob 定）：显示下一步的意图（actionLabel：
                // 去还款 / 帮我建提醒 / 看预约 / 回拨 / 打开看看 / 加入会议 /
                // 看日程 / 按来源给动作起名），不再写“下一步”几个字。
                // 金色只留主操作（去 AI 味四条）。
                // 第四十五批：升级卡下载中右按钮先收起来（hideActionButton，
                // 下载中没什么可点的）；下完变「安装」，失败变「重试」。
                if (!card.hideActionButton) {
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

// Stageshow 桌面优化 3.2：breathingBorderModifier 已删（见上文光晕常量删除注）。

/**
 * 应用小标（第十一批，Bob 定“要小、要优雅”）：只画在真卡上——引导卡的
 * 下一步是去连接来源、不是打开某个应用，不画；“已经连上了”反馈卡也不画
 * （hideActionRow）；未接来电卡也不画（第四十九批，Bob 2026-11-21 定审计
 * A2a：按钮“回拨”已隐含拨号盘）。16dp 应用图标＋应用名（Caption 12sp，次要色），
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
