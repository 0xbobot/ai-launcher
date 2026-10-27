package com.bobot.ailauncher.ui.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bobot.ailauncher.data.AiInsight
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.isWithinWindow
import com.bobot.ailauncher.ui.rememberNowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * TODAY 数据管线（封面模式专用）：只跑数据 effect、向上抛简报标题与下个会议，
 * 不渲染任何 UI。
 *
 * v0.68.96（F-01 退役）：全量 TODAY 列表 UI（消息/待办行、天气 pill、编辑模式、
 * 区块显隐）随非封面分支一并删除——该分支自 v0.68.44 杂志封面改版起无入口可达，
 * Bob 2026-10-22 拍板定性退役。本文件只保留封面 hero 与 NextUp 紧凑条共用的
 * 数据来源：日历加载/边界刷新/会议提醒调度、消息 3 小时窗与需回复计数、简报
 * 标题合成。行为与删除前 dataOnly=true 路径逐项平价（含一个既有事实：原 UI 版
 * 里位于 dataOnly return 之后的 onResume 重查日历在封面模式下从未生效，退役后
 * 维持原样，不在此顺手改变刷新策略）。
 */
@Composable
internal fun TodaySection(
    // 会议提醒回调（省电原则：一次性任务；实际提醒逻辑在 PetZone 的 remindMeeting）
    onMeetingRemind: (CalEvent, String) -> Unit = { _, _ -> },
    // 简报标题/下个会议上抛（封面 hero / NextUp 紧凑条用）
    onSummaryChange: (String) -> Unit = {},
    onNextEventChange: (CalEvent?) -> Unit = {},
) {
    val context = LocalContext.current
    // v0.59.0 AI-2：保留今日所有事件，用于消息关联日历与提醒调度
    var allEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var hasCalPermission by remember { mutableStateOf(CalendarRepository.hasCalendarPermission(context)) }

    // v0.68.164（过期日程修复 A）：选会改为分钟 ticker 派生值——此前 nextEvent
    // 是 LaunchedEffect 里一次性算出的 state，只靠边界 delay 链刷新，delay 被
    // 打断就会冻住已结束的会（2026-10-27 实拍：16:00–17:00 的会 17:30 还在屏上）。
    // 派生后可见性只由 ticker 驱动，已结束的会最迟 1 分钟内消失，不再依赖 effect。
    // v0.68.95（F-05）：now 从分钟 ticker 派生（仅 RESUMED tick，回桌面立即刷新）。
    val nowMillis by rememberNowMillis()
    val nextEvent = remember(allEvents, nowMillis) {
        selectNextEvent(allEvents, nowMillis)
    }

    LaunchedEffect(hasCalPermission) {
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
            allEvents = events.filter { !it.allDay }
        }
    }
    // v0.67.5 边界刷新（v0.68.164 后仅剩数据重查的作用，保留作后备）：
    // 到达当前会边界时重查一次 allEvents（新加／改期的日程在边界点被捡回），
    // 可见性不再由它决定——即使本 effect 被打断，ticker 派生仍会在 1 分钟内
    // 摘掉已结束的会。key 为派生 nextEvent：选会变化时重启，同一场会内分钟
    // tick 不重启（选中的仍是同一 CalEvent 实例）。
    LaunchedEffect(nextEvent) {
        val e = nextEvent ?: return@LaunchedEffect
        val effectiveEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
        val now = System.currentTimeMillis()
        if (e.begin > now) delay(e.begin - now)
        val waitEnd = effectiveEnd - System.currentTimeMillis()
        if (waitEnd > 0) delay(waitEnd)
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
            allEvents = events.filter { !it.allDay }
        }
    }
    // v0.67.6：会议提醒一次性任务（省电原则：不轮询）
    // 日历数据变化（加载/边界刷新）时取消旧任务重算；
    // 在"开会前30分钟"和"前15分钟"各提醒一次；已进入 30 分钟窗口则立即提醒
    // 实际提醒走 onMeetingRemind（PetZone 的 remindMeeting：七仔说话 + 去重）
    LaunchedEffect(allEvents) {
        val now = System.currentTimeMillis()
        val upcoming = allEvents.firstOrNull { !it.allDay && it.begin > now }
            ?: return@LaunchedEffect
        // 30 分钟前提醒
        val d30 = upcoming.begin - 30 * 60 * 1000L - System.currentTimeMillis()
        if (d30 > 0) delay(d30)
        if (System.currentTimeMillis() >= upcoming.begin - 30 * 60 * 1000L) {
            onMeetingRemind(upcoming, "30")
        }
        // 15 分钟前提醒
        val d15 = upcoming.begin - 15 * 60 * 1000L - System.currentTimeMillis()
        if (d15 > 0) delay(d15)
        if (System.currentTimeMillis() >= upcoming.begin - 15 * 60 * 1000L) {
            onMeetingRemind(upcoming, "15")
        }
    }

    // 消息数据（简报标题的需回复计数来源）：3 小时窗 + 行动优先级排序
    val filed by PetRepository.filed.collectAsState()
    // v0.68.95（F-05）：3 小时窗的 now 从分钟 ticker 派生并纳入 remember
    // key——此前 now 在组合期一次性捕获，旧消息会滞留在已超时的窗口里。
    // nowMillis 在本函数顶部声明（选会派生与消息窗共用同一 ticker）。
    val recentMsgs = remember(filed, nowMillis) {
        filed.values.flatten()
            .filter { !it.isCalendar }
            .filter { isWithinWindow(it.time, nowMillis, 3 * 60 * 60 * 1000L) }
            .sortedWith(
                compareByDescending<PetItem> {
                    when (it.cat) {
                        PetCat.IMP -> 3
                        PetCat.WORK -> 2
                        else -> 1
                    }
                }.thenByDescending { it.time }
            )
    }
    // AI 简报式：P0 需回复消息计数进简报标题
    val replyMsgs = remember(recentMsgs) {
        recentMsgs.filter { AiInsight.analyzeReplyNeed(it.title, it.text).needsReply }
    }
    // v0.64.5：首次出现 P0 需回复消息 → 七仔引导（只一次）。
    // 气泡子项：P0 回复配下一步动作“打开会话”（contentIntent 直达会话，
    // 兜底按包名开应用），点后气泡关闭；文案本身不改。
    LaunchedEffect(replyMsgs.isNotEmpty()) {
        if (replyMsgs.isNotEmpty() && GuideManager.tryShow(context, GuideManager.Id.P0_REPLY)) {
            val first = replyMsgs.first()
            PetRepository.say(
                "红色的是要回复的，点进去看看",
                action = PetRepository.SpeechAction(
                    PetRepository.SpeechActionKind.OPEN_SESSION, "打开会话",
                    sessionPendingIntent = first.pendingIntent,
                    appPackage = first.packageName
                )
            )
        }
    }
    // v0.67.5：已结束的会议不计入，避免 stale "1 个会要开"。
    // v0.68.164：nextEvent 已是 ticker 派生（end > nowMillis 才入选），
    // 入选即计入，不再用一次性 System.currentTimeMillis() 二次判断。
    // F-01 退役后区块显隐设置随编辑模式一并下线，下个会议恒计入简报。
    val meetingCount = if (hasCalPermission && nextEvent != null) 1 else 0
    val summaryText = AiInsight.buildSummary(replyMsgs.size, meetingCount)
    // v0.68.44：上抛给封面 hero
    LaunchedEffect(summaryText) { onSummaryChange(summaryText) }
    LaunchedEffect(nextEvent) { onNextEventChange(nextEvent) }
}

/**
 * 选会纯函数（v0.68.164 过期日程修复 A，供单测）：
 * 输入为当日非全天事件（仓库层已按 BEGIN 升序返回；此处再按 begin 排序一次，
 * 使函数不依赖调用方顺序）；返回第一条“还没结束”的会（end > now），
 * 即“今天还没结束的第一场会”。已结束的会（end <= now）永远不入选。
 */
internal fun selectNextEvent(events: List<CalEvent>, now: Long): CalEvent? =
    events.sortedBy { it.begin }.firstOrNull { it.end > now }

/** 会议行状态（v0.68.164，供单测与 BriefMeetingRow 标签分支）。 */
internal enum class MeetingStatus { UPCOMING, ONGOING, ENDED }

/**
 * 会议状态纯函数（v0.68.164，供单测）：
 * begin <= now < end 为进行中；now < begin 为未来；now >= end 为已结束。
 * 边界语义与选会一致：now == end 即已结束（选会 end > now 不入选），
 * now == begin 即进行中。
 */
internal fun meetingStatus(begin: Long, end: Long, now: Long): MeetingStatus =
    when {
        now < begin -> MeetingStatus.UPCOMING
        now < end -> MeetingStatus.ONGOING
        else -> MeetingStatus.ENDED
    }
