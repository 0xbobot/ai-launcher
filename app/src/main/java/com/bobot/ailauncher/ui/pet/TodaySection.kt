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
    var nextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // v0.59.0 AI-2：保留今日所有事件，用于消息关联日历与提醒调度
    var allEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var hasCalPermission by remember { mutableStateOf(CalendarRepository.hasCalendarPermission(context)) }

    LaunchedEffect(hasCalPermission) {
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
            val now = System.currentTimeMillis()
            allEvents = events.filter { !it.allDay }
            // v0.67.2：过滤已结束的会议（end <= now 的不显示，避免"还有 -1240 分钟"）
            nextEvent = allEvents.firstOrNull { it.end > now }
        }
    }
    // v0.67.5：会议开始/结束时间点一次性刷新（省电原则：边界点一次性任务，不轮询）
    LaunchedEffect(nextEvent) {
        val e = nextEvent ?: return@LaunchedEffect
        val effectiveEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
        val now = System.currentTimeMillis()
        // 先等到开始（"还有X分钟"→"进行中"），再等到结束（切下一场/消失）
        if (e.begin > now) delay(e.begin - now)
        val waitEnd = effectiveEnd - System.currentTimeMillis()
        if (waitEnd > 0) delay(waitEnd)
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
            val now2 = System.currentTimeMillis()
            allEvents = events.filter { !it.allDay }
            nextEvent = allEvents.firstOrNull { it.end > now2 }
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
    val nowMillis by rememberNowMillis()
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
    // v0.67.5：已结束的会议不计入（无结束时间默认按1小时算），避免 stale "1 个会要开"
    // F-01 退役后区块显隐设置随编辑模式一并下线，下个会议恒计入简报。
    val meetingCount = if (hasCalPermission && nextEvent != null) {
        val e = nextEvent!!
        val nowM = System.currentTimeMillis()
        val effEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
        if (nowM < effEnd) 1 else 0
    } else 0
    val summaryText = AiInsight.buildSummary(replyMsgs.size, meetingCount)
    // v0.68.44：上抛给封面 hero
    LaunchedEffect(summaryText) { onSummaryChange(summaryText) }
    LaunchedEffect(nextEvent) { onNextEventChange(nextEvent) }
}
