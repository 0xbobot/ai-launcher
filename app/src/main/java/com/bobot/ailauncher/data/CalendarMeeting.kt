package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 日历事件的会议链接与打开链路（第十二批）：
 * 原在 ui/pet/PetZone.kt 的 extractMeetingUrl / joinCalendarEvent，
 * 下沉到 data 层——日历智能卡片（仓库）与简报会行（PetZone）共用同一条
 * 链路，不另写一套。域名白名单与三级兜底原样照搬。防多发守卫
 * （JoinGuard）第十六批也下沉到本文件：简报会行已随 486 拿掉，加入的
 * 唯一入口是日历卡「加入会议」（performAction 的 CalendarEvent 分支），
 * 卡片动作与会行路径（joinCalendarEvent，暂无调用点、定义保留）各用
 * 一个实例、同一窗口（2 秒）；卡片动作在成功发出后才记（没发出不记，
 * 失败可重试、失败不摘卡）。
 *
 * 会议类判定：现有代码没有“标题含会议词”这类判定，就用“有会议链接即
 * 会议类”（提案口径），不另造宽泛词表。
 */
/**
 * v0.68.101（D-5）：入会防多发守卫（纯逻辑，单测锁死）——同一事件在
 * 窗口内重复触发（连点/重组竞态）只放行第一次。真机录屏里「加入」后
 * 浏览器与主页来回闪烁，代码侧先把「本应用多次发放意图」这一路堵死。
 * 原在 ui/pet/PetZone.kt（只拦简报会行的连点）；486 拿掉会行后，加入
 * 的唯一入口是日历卡「加入会议」，第十六批移到 data 层。
 * 只查不记是第十六批的拆分：wasFired 只判断、record 只记录——
 * performAction 在成功发出后才记（没发出不记，失败可重试、失败不摘卡）；
 * shouldFire 是一次性查＋记，给会行路径（joinCalendarEvent）用。
 */
internal class JoinGuard(private val windowMs: Long = 2_000L) {
    private var lastKey: String? = null
    private var lastMs: Long = Long.MIN_VALUE

    /** 窗口内是否已放行过（只查不记；查完不影响后续判断）。 */
    @Synchronized
    fun wasFired(key: String, nowMs: Long): Boolean =
        key == lastKey && nowMs - lastMs < windowMs

    /** 记录一次放行（成功发出后才记；没发出不记，失败可重试）。 */
    @Synchronized
    fun record(key: String, nowMs: Long) {
        lastKey = key
        lastMs = nowMs
    }

    /** 查＋记一次（会行路径用）：窗口内重复触发只放行第一次。 */
    @Synchronized
    fun shouldFire(key: String, nowMs: Long): Boolean {
        if (wasFired(key, nowMs)) return false
        record(key, nowMs)
        return true
    }
}

private val MEETING_DOMAINS = listOf(
    "meeting.tencent.com", "wemeet", "voovmeeting.com",
    "zoom.us", "zoom.com",
    "teams.microsoft.com", "teams.live.com",
    "meeting.feishu.cn", "feishu.cn",
    "webex.com",
    "dingtalk.com"
)
private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

/** 从日历事件的 location/description/title 提取在线会议链接；没有返回 null */
fun extractMeetingUrl(event: CalEvent): String? {
    val combined = "${event.location}\n${event.description}\n${event.title}"
    return URL_REGEX.findAll(combined)
        .map { it.value.trimEnd('.', ',', ')', ']', '!', '；', '。') }
        .firstOrNull { url ->
            val lower = url.lowercase()
            MEETING_DOMAINS.any { lower.contains(it) }
        }
}

/** 是否会议类事件：有会议链接即会议类（没有其他判定，不另造词表） */
fun isMeetingEvent(event: CalEvent): Boolean = extractMeetingUrl(event) != null

/**
 * 日历卡出现窗口（纯函数，第十二批）：会议类开始前 30 分钟、非会议日程
 * 开始前 2 小时出现；过了结束时间消失（deadline＝结束时间）。
 * 返回 (appearFromMs, deadlineMs)。
 */
fun calendarCardWindow(event: CalEvent): Pair<Long, Long> {
    val leadMs = if (isMeetingEvent(event)) 30L * 60 * 1000 else 2L * 60 * 60 * 1000
    return (event.begin - leadMs) to event.end
}

/** 日历卡一句话事实（纯函数）：时间＋标题＋地点（有就写，没有不硬凑） */
fun calendarCardFact(event: CalEvent, nowMs: Long): String {
    val dayStart = startOfDayMs(event.begin)
    val startMin = ((event.begin - dayStart) / 60_000L).toInt()
    val endMin = ((event.end - dayStart) / 60_000L).toInt()
    return buildString {
        append(relativeDayLabel(dayStart, nowMs))
        append(" ")
        append(formatTimeRange(startMin, endMin))
        append(" ")
        append(event.title.trim())
        val place = event.location.trim()
        if (place.isNotEmpty()) append("，$place")
    }
}

/**
 * 日历卡去重键（纯函数）：与短信预约卡同一口径（提案：同一件事短信和
 * 日历里都有，只留一张，以短信那条为准）——
 * `appt|<当天 00:00>|<开始分钟>|<地点 hash>`，与 SmartCardRepository 里
 * 短信预约卡的 key 同格式；候选顺序短信在前，去重后短信版优先。
 * 地点写法不一致（短信启发式提取 vs 日历地点原文）时键对不上、两张并存，
 * 是已知局限，不硬凑。
 */
fun calendarCardSourceKey(event: CalEvent): String {
    val dayStart = startOfDayMs(event.begin)
    val startMin = ((event.begin - dayStart) / 60_000L).toInt()
    val place = event.location.trim().ifBlank { null }
    return "appt|$dayStart|$startMin|${place?.hashCode() ?: 0}"
}

/**
 * 打开日历事件（PetZone.joinCalendarEvent 同一条链，原样照搬）：
 * 1) 有会议链接 → 直开链接（会议 App/浏览器接管）；
 * 2) 无链接 → 打开日历 App；
 * 3) 再兜底按 content://com.android.calendar/events/{eventId} 打开事件详情；
 * 全失败返回 false（调用方提示，不在这里弹）。
 */
fun openCalendarEvent(context: Context, eventId: Long, meetingUrl: String?): Boolean {
    if (meetingUrl != null) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(meetingUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (_: Exception) { /* 链接打开失败 → 落日历兜底 */ }
    }
    try {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        }
    } catch (_: Exception) { }
    if (eventId > 0) {
        try {
            val uri = Uri.parse("content://com.android.calendar/events/$eventId")
            val viewIntent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                return true
            }
        } catch (_: Exception) { }
    }
    return false
}
