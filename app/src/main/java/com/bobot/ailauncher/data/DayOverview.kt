package com.bobot.ailauncher.data

import android.content.Context
import java.util.Calendar
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * 节点一「今天 / 明天一览」（方案 ac-missing-nodes-plan.md 第一批）：
 * 起床窗出「今天一览」、睡前窗（21:30 锚点）出「明天一览」，进 524 单卡槽轮换。
 *
 * 第四十八批（Bob 2026-11-20 18:07 拍板 Key 分流）重设计：不再是三行事实
 * 并列，卡片回答「今天什么最值得我注意？」——结论是正文第一句（lead，
 * 加粗），按跨来源优先级取第一条适用的（雨＋出门 ＞ 同来源 ＞ 空闲 × 未读
 * ＞ 好天气 × 大段空闲 ＞ 日子形状回落）；结论里用到的来源不再重复出现
 * （去重规则见 buildDayOverviewCard）。
 *
 * Key 分流（设计 smart-card-day-overview-shape-design.md §6）：配了 Key
 * 时用 DeepSeek（deepseek-flash）合成结论，21:30 复盘时顺手算好、存本地
 * （OverviewConclusionStore），早上直接显示存好的；没配 Key／离线／调用
 * 失败时静默回落到规则版跨来源结论（dayOverviewConclusion 纯函数），
 * 一览卡照常出、不报错、不贴假清单。
 *
 * 纯组装——所有 Android 依赖（查日历、读天气缓存、读角标、解析日历
 * 应用）都在 SmartCardRepository.dayOverviewCard 里做，这里只认
 * 已取到的数据。
 *
 * 降级（写死）：没日历权限 → 不算结论、回落 533 三行并列（看不到 ≠ 没有，
 * 不能写「今天没有会」）；缺哪行就不显示哪行；三个源全缺不出卡；没有可点
 * 的动作（没会、没未读、也没能打开的日历应用）也不出卡——不出没有
 * 下一步的卡。
 */

/** 未读来源：只记包名、显示名与条数，不读正文、不展示预览。 */
data class UnreadSource(
    val packageName: String,
    val label: String,
    val count: Int
)

/** 跨来源结论的类型（设计 §3 的五条优先级；SHAPE 是回落）。 */
enum class DayOverviewConclusionType {
    RAIN_OUT,
    SAME_SOURCE,
    FREE_TIME_UNREAD,
    GOOD_WEATHER_FREE,
    SHAPE
}

/** 结论的动作目标（仓库按它解析成具体动作，目标不可用时顺延）。 */
enum class DayOverviewAction {
    EVENT,
    UNREAD,
    CALENDAR
}

/** 一条一览结论：正文第一句（lead）＋类型＋动作目标。 */
data class DayOverviewConclusion(
    val text: String,
    val type: DayOverviewConclusionType,
    val action: DayOverviewAction
)

/** 总览卡的组装输入（全是已取到的纯数据）。 */
data class DayOverviewInput(
    /** 0 = 今天一览，1 = 明天一览。 */
    val dayOffset: Int,
    /** 所示那天的 yyyyMMdd（拼卡 id 用，按日抑制靠它）。 */
    val dateKey: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    /** 天气缓存（WeatherState.full 的当前值；null = 拉取失败）。 */
    val weather: WeatherRepository.WeatherInfo?,
    /** 当天/明天第一场相关会议（已按 firstRelevantEvent 挑过；null = 没有）。 */
    val firstEvent: CalEvent?,
    /** 该会议的入会链接（extractMeetingUrl 的结果；null = 没有）。 */
    val meetingUrl: String?,
    /** 会议的小标（日历账户名或解析出的日历应用；null = 不画小标）。 */
    val meetingTargetApp: TargetApp?,
    /** 未读来源（已按条数倒序；空 = 没有未读）。 */
    val unread: List<UnreadSource>,
    /** 能打开的日历应用包名（无会、无未读时的兜底动作；null = 没有）。 */
    val calendarAppPackage: String?,
    /** 日历应用的小标（null = 不画小标）。 */
    val calendarAppTargetApp: TargetApp?,
    /**
     * 当天全部日历事件（含已结束的计时事件与全天事件；结论引擎内部再按
     * nowMs 挑剩余——形状句要区分「没有会」与「都开完了」，只传剩余事件
     * 区分不出来）。仓库里 events 本来就有，直接传进来。
     */
    val events: List<CalEvent> = emptyList(),
    /** 当前时刻（毫秒；算空闲窗口、进行中用）。 */
    val nowMs: Long = 0L,
    /** 有无日历权限：没权限不算结论（看不到 ≠ 没有），回落 533 三行并列。 */
    val hasCalendarPermission: Boolean = true,
    /** 21:30 复盘时算好存本地的模型结论（可空；有 Key 且 dateKey 对上时才传）。 */
    val modelConclusion: DayOverviewConclusion? = null
)

/** 毫秒 → 当天 yyyyMMdd（拼总览卡 id 用）。 */
fun dateKeyOf(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d%02d%02d".format(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )
}

/** 毫秒 → HH:mm。 */
fun formatClock(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/**
 * 当天/明天第一场相关会议：第一场非全天、还没结束的（进行中或未开始）；
 * 全结束了返回 null。标题为空的也算（显示时写「有日程」，不编标题）。
 */
fun firstRelevantEvent(events: List<CalEvent>, nowMs: Long): CalEvent? =
    events.filter { !it.allDay && it.end > it.begin && it.end > nowMs }
        .minByOrNull { it.begin }

/** 当天/明天还没结束的计时事件（按开始升序；全天事件与已结束的不进）。 */
fun remainingEvents(events: List<CalEvent>, nowMs: Long): List<CalEvent> =
    events.filter { !it.allDay && it.end > it.begin && it.end > nowMs }
        .sortedBy { it.begin }

/** 是否要出门：地点非空且无入会链接（有入会链接即线上会；没地点不知道要不要出门，不瞎猜）。 */
fun isOutingEvent(event: CalEvent): Boolean =
    event.location.isNotBlank() && extractMeetingUrl(event) == null

/** 恶劣天气：天气描述含雨／雪／雷／雹（WeatherInfo 没存 weather_code，描述文字是唯一依据）。 */
fun isBadWeather(desc: String): Boolean =
    desc.contains("雨") || desc.contains("雪") || desc.contains("雷") || desc.contains("雹")

/** 好天气：晴／多云且非恶劣天气。 */
fun isGoodWeather(desc: String): Boolean =
    (desc.contains("晴") || desc.contains("多云")) && !isBadWeather(desc)

/**
 * 会议的来源应用（纯函数）：入会链接域名 → 应用名（feishu.cn→飞书、
 * dingtalk.com→钉钉、zoom.us→Zoom、teams→Teams、webex.com→Webex、
 * meeting.tencent.com/wemeet/voovmeeting.com→腾讯会议）；无入会链接时
 * 退到日历账户名（calendarName，如「飞书日历」）；都认不出来返回 null。
 * 同来源判定由调用方拿它和未读来源比对（账户名含应用名、或应用名含
 * 账户名都算同一应用）。
 */
fun meetingSourceApp(event: CalEvent): String? {
    val url = extractMeetingUrl(event)
    if (url != null) {
        val host = url.lowercase().substringAfter("://").substringBefore("/")
            .substringBefore("?").substringBefore("#")
        return when {
            host.contains("feishu") -> "飞书"
            host.contains("dingtalk") -> "钉钉"
            host.contains("zoom") -> "Zoom"
            host.contains("teams") -> "Teams"
            host.contains("webex") -> "Webex"
            host.contains("tencent") || host.contains("wemeet") || host.contains("voov") -> "腾讯会议"
            else -> null
        }
    }
    return event.calendarName.trim().ifBlank { null }
}

/**
 * 同来源判定（纯函数）：第一场会的来源，和未读最多的来源是同一个应用。
 * 返回未读来源的显示名（结论文案里写它，如「飞书」——账户名「飞书日历」
 * 匹配上时文案也写「飞书」，不写账户名）；对不上返回 null。
 */
fun sameSourceLabel(event: CalEvent, unread: List<UnreadSource>): String? {
    val top = unread.maxByOrNull { it.count } ?: return null
    val source = meetingSourceApp(event) ?: return null
    return if (source == top.label || source.contains(top.label) || top.label.contains(source)) {
        top.label
    } else {
        null
    }
}

/**
 * 窗口／间隔按半小时取整写「X 小时」（1 / 1.5 / 2 / 3），不足 1 小时写分钟
 * （5 分钟取整，如「40 分钟」）。纯函数。
 */
fun formatFreeWindow(windowMs: Long): String {
    val halfHours = (windowMs.toDouble() / (30 * 60 * 1000)).roundToInt()
    if (halfHours >= 2) {
        val hours = halfHours / 2.0
        val text = if (hours == kotlin.math.floor(hours)) hours.toInt().toString() else hours.toString()
        return "$text 小时"
    }
    val minutes = ((windowMs / 60000.0).roundToInt() / 5 * 5).coerceAtLeast(5)
    return "$minutes 分钟"
}

/** 目标那天的天气描述（今天用 desc，明天用 tomorrowDesc；取不到返回 null）。 */
private fun overviewWeatherDesc(weather: WeatherRepository.WeatherInfo?, isToday: Boolean): String? {
    if (weather == null) return null
    return if (isToday) weather.desc.ifBlank { null } else weather.tomorrowDesc.ifBlank { null }
}

/** 目标那天的最高温（今天用 high，明天用 tomorrowHigh；取不到返回 null）。 */
private fun overviewWeatherHigh(weather: WeatherRepository.WeatherInfo?, isToday: Boolean): Int? {
    if (weather == null) return null
    return if (isToday) weather.high else weather.tomorrowHigh
}

/**
 * 最后一场会结束后到当天 24:00 的空闲（毫秒）；没有剩余事件返回
 * Long.MAX_VALUE（全天空着）。纯函数。
 */
private fun freeAfterLastEventMs(input: DayOverviewInput, nowMs: Long, remaining: List<CalEvent>): Long {
    if (remaining.isEmpty()) return Long.MAX_VALUE
    val lastEnd = remaining.maxOf { it.end }
    val (_, dayEnd) = CalendarRepository.dayWindow(nowMs, input.dayOffset)
    return dayEnd - lastEnd
}

/**
 * 空闲 × 未读的结论句（设计 §3.3，纯函数）：未读总数 ≥10 条才当 lead。
 * 今天的窗口 = 第一场会开始 − 现在；明天的窗口 = 第一场会开始 − 08:00
 * （名义起床时间，Bob 待定项默认值）。
 * 长窗口（≥1 小时，按半小时取整）说「现在看」／「明早看」；短窗口
 * （＜1 小时）说「开完会再看」／「今晚看」；没有会说「有空慢慢看」。
 * 注意：设计只写了长 ≥2 小时、短 ＜1 小时，1–2 小时这档没写——这里按
 * 「现在看」句式处理（1 小时以上已有可观时间预算，短句式只用于 ＜1 小时）。
 * 第一场会进行中（会前窗口已过）时 3.3 不触发，往下走。
 */
private fun freeTimeUnreadText(
    input: DayOverviewInput,
    nowMs: Long,
    remaining: List<CalEvent>,
    first: CalEvent?,
    topUnread: UnreadSource,
    isToday: Boolean
): String? {
    return if (isToday) {
        if (first == null) {
            "今天没有会，${topUnread.label} ${topUnread.count} 条未读有空慢慢看"
        } else {
            val windowMs = first.begin - nowMs
            if (windowMs <= 0) return null
            if (windowMs < 60 * 60 * 1000) {
                "${formatClock(first.begin)}开会，只剩 ${formatFreeWindow(windowMs)}，${topUnread.count} 条未读开完会再看"
            } else {
                "${formatClock(first.begin)}开会前还有 ${formatFreeWindow(windowMs)}，${topUnread.label} ${topUnread.count} 条未读现在看"
            }
        }
    } else {
        val (dayStart, _) = CalendarRepository.dayWindow(nowMs, 1)
        val morningMs = dayStart + 8 * 60 * 60 * 1000
        if (first == null) {
            "明天没有会，${topUnread.label} ${topUnread.count} 条未读有空慢慢看"
        } else {
            val windowMs = first.begin - morningMs
            if (windowMs < 60 * 60 * 1000) {
                "明天 ${formatClock(first.begin)} 就开会，${topUnread.count} 条未读今晚看"
            } else {
                "明天 ${formatClock(first.begin)} 开会，明早有 ${formatFreeWindow(windowMs)}，${topUnread.label} ${topUnread.count} 条未读明早看"
            }
        }
    }
}

/**
 * 好天气 × 大段空闲的结论句（设计 §3.4，纯函数）：天气晴／多云、最高温
 * ≤33°、最后一场会结束后 ≥3 小时空闲（或没有会，全天空着）。
 * 今天的「之后全天空着／之后空着」按 14:00 分界（与形状句同一条数字规则）；
 * 明天一律写「之后空着」。
 */
private fun goodWeatherFreeText(
    input: DayOverviewInput,
    nowMs: Long,
    remaining: List<CalEvent>,
    desc: String,
    isToday: Boolean
): String {
    val lastEnd = remaining.maxOfOrNull { it.end }
    return if (isToday) {
        if (lastEnd == null) {
            "今天没有会，今天${desc}，适合出去走走"
        } else {
            val (dayStart, _) = CalendarRepository.dayWindow(nowMs, 0)
            val freeAfter = if (lastEnd <= dayStart + 14 * 60 * 60 * 1000) "之后全天空着" else "之后空着"
            "${formatClock(lastEnd)}$freeAfter，今天${desc}，适合出去走走"
        }
    } else {
        if (lastEnd == null) {
            "明天没有会，天${desc}，适合出去走走"
        } else {
            "明天 ${formatClock(lastEnd)}之后空着，天${desc}，适合出去走走"
        }
    }
}

/**
 * 日子形状句 v1（设计 §3.5 的回落，逐字规则，纯函数）：
 * 没有会「今天没有会」；没有会但有全天事件「今天没有会，全天「{标题}」」；
 * 会都开完了「今天的会都开完了」；只剩 1 场未开始「今天只有 1 个会，
 * 10:00 团队周会，11:00 之后全天空着」；只剩 1 场进行中「团队周会进行中，
 * 11:00 结束，之后全天空着」；N≥2、最大间隔＜1 小时「今天 3 个会连轴转，
 * 第一场 10:00 团队周会」；N≥2、最大间隔≥1 小时「今天 3 个会，第一场
 * 10:00 团队周会，中间最长空 2 小时」；N≥2、第一场进行中「团队周会进行中，
 * 11:00 结束，下一场 13:00」。
 * 数字规则：最后一场结束 ≤14:00 写「之后全天空着」，＞14:00 写「之后空着」；
 * 间隔按半小时取整；标题为空写「有日程」；多场只写第一场标题；
 * 「进行中」不写个数。明天同一套，不会出现进行中／都开完了。
 */
fun dayShapeSentence(events: List<CalEvent>, nowMs: Long, isToday: Boolean): String {
    val day = if (isToday) "今天" else "明天"
    val timed = events.filter { !it.allDay && it.end > it.begin }.sortedBy { it.begin }
    val remaining = timed.filter { it.end > nowMs }
    if (remaining.isEmpty()) {
        // 今天：计时事件都在过去了 → 都开完了；明天不会出现这句（明天的事件都还没开始）。
        if (isToday && timed.isNotEmpty()) return "今天的会都开完了"
        val allDay = events.filter { it.allDay }
        if (allDay.isNotEmpty()) {
            val title = allDay.minByOrNull { it.begin }?.title?.ifBlank { "有日程" } ?: "有日程"
            return "${day}没有会，全天「${title}」"
        }
        return "${day}没有会"
    }
    val first = remaining.first()
    val firstTitle = first.title.ifBlank { "有日程" }
    val (dayStart, _) = CalendarRepository.dayWindow(nowMs, if (isToday) 0 else 1)
    val freeAfter = if (first.end <= dayStart + 14 * 60 * 60 * 1000) "之后全天空着" else "之后空着"
    if (remaining.size == 1) {
        return if (first.begin <= nowMs) {
            "${firstTitle}进行中，${formatClock(first.end)}结束，$freeAfter"
        } else {
            "${day}只有 1 个会，${formatClock(first.begin)} $firstTitle，${formatClock(first.end)} $freeAfter"
        }
    }
    if (first.begin <= nowMs) {
        val next = remaining[1]
        return "${firstTitle}进行中，${formatClock(first.end)}结束，下一场 ${formatClock(next.begin)}"
    }
    val maxGap = remaining.zipWithNext { a, b -> b.begin - a.end }.maxOrNull() ?: 0L
    return if (maxGap < 60 * 60 * 1000) {
        "$day ${remaining.size} 个会连轴转，第一场 ${formatClock(first.begin)} $firstTitle"
    } else {
        "$day ${remaining.size} 个会，第一场 ${formatClock(first.begin)} $firstTitle，中间最长空 ${formatFreeWindow(maxGap)}"
    }
}

/**
 * 模型结论的轻量时效校验（纯函数）：类型前提对不上当前数据就回落规则
 * （如 RAIN_OUT 但现在已不下雨）。只校验类型的前提，不逐字核对结论里的
 * 时间／条数——21:30 算好、早上显示，一夜之间的小出入不影响判断。
 */
private fun modelConclusionPremiseHolds(
    conclusion: DayOverviewConclusion,
    input: DayOverviewInput,
    nowMs: Long
): Boolean {
    val isToday = input.dayOffset == 0
    val remaining = remainingEvents(input.events, nowMs)
    val first = remaining.firstOrNull()
    val desc = overviewWeatherDesc(input.weather, isToday).orEmpty()
    val high = overviewWeatherHigh(input.weather, isToday)
    val topUnread = input.unread.maxByOrNull { it.count }
    val totalUnread = input.unread.sumOf { it.count }
    return when (conclusion.type) {
        DayOverviewConclusionType.RAIN_OUT ->
            isBadWeather(desc) && remaining.any { isOutingEvent(it) }
        DayOverviewConclusionType.SAME_SOURCE ->
            first != null && topUnread != null && sameSourceLabel(first, input.unread) != null
        DayOverviewConclusionType.FREE_TIME_UNREAD ->
            totalUnread >= 10
        DayOverviewConclusionType.GOOD_WEATHER_FREE ->
            isGoodWeather(desc) && high != null && high <= 33 &&
                freeAfterLastEventMs(input, nowMs, remaining) >= 3 * 60 * 60 * 1000
        DayOverviewConclusionType.SHAPE -> true
    }
}

/**
 * 跨来源结论引擎（纯函数，设计 §3 的优先级写死）：
 * 雨＋出门 ＞ 同来源 ＞ 空闲 × 未读 ＞ 好天气 × 大段空闲 ＞ 日子形状。
 * 没日历权限返回 null（调用方回落 533 三行并列）。
 * 存好的模型结论优先：前提还对得上当前数据就直接用（模型按同一套
 * 优先级选的结论，早上只复核前提）；前提对不上了静默回落规则。
 */
fun dayOverviewConclusion(input: DayOverviewInput, nowMs: Long): DayOverviewConclusion? {
    if (!input.hasCalendarPermission) return null
    input.modelConclusion?.let { stored ->
        if (modelConclusionPremiseHolds(stored, input, nowMs)) return stored
    }
    val isToday = input.dayOffset == 0
    val remaining = remainingEvents(input.events, nowMs)
    val first = remaining.firstOrNull()
    val desc = overviewWeatherDesc(input.weather, isToday).orEmpty()
    val high = overviewWeatherHigh(input.weather, isToday)
    val topUnread = input.unread.maxByOrNull { it.count }
    val totalUnread = input.unread.sumOf { it.count }

    // 3.1 雨＋出门：取最早一场要出门的事件（线上会、没地点的都不算）。
    if (isBadWeather(desc)) {
        val outing = remaining.firstOrNull { isOutingEvent(it) }
        if (outing != null) {
            val title = outing.title.ifBlank { "有日程" }
            val text = if (isToday) {
                "今天有雨，${formatClock(outing.begin)} ${title}要出门，带伞、早点出门"
            } else {
                "明天下雨，${formatClock(outing.begin)} ${title}要出门，记得带伞"
            }
            return DayOverviewConclusion(text, DayOverviewConclusionType.RAIN_OUT, DayOverviewAction.EVENT)
        }
    }
    // 3.2 同来源：第一场会的来源应用里还有未读（只讲共现＋「会前看一眼」，不写没根据的话）。
    if (first != null && topUnread != null) {
        val source = sameSourceLabel(first, input.unread)
        if (source != null) {
            val title = first.title.ifBlank { "有日程" }
            val text = if (isToday) {
                "${formatClock(first.begin)} ${source}的${title}，${source}还有 ${topUnread.count} 条未读，会前看一眼"
            } else {
                "明天 ${formatClock(first.begin)} ${source}的${title}，${source}还有 ${topUnread.count} 条未读"
            }
            return DayOverviewConclusion(text, DayOverviewConclusionType.SAME_SOURCE, DayOverviewAction.UNREAD)
        }
    }
    // 3.3 空闲 × 未读：未读总数 ≥10 条才当 lead。
    if (totalUnread >= 10 && topUnread != null) {
        val text = freeTimeUnreadText(input, nowMs, remaining, first, topUnread, isToday)
        if (text != null) {
            return DayOverviewConclusion(text, DayOverviewConclusionType.FREE_TIME_UNREAD, DayOverviewAction.UNREAD)
        }
    }
    // 3.4 好天气 × 大段空闲：最高温 ≤33°、最后一场会结束后 ≥3 小时空闲（或没有会）。
    if (isGoodWeather(desc) && high != null && high <= 33 &&
        freeAfterLastEventMs(input, nowMs, remaining) >= 3 * 60 * 60 * 1000
    ) {
        val text = goodWeatherFreeText(input, nowMs, remaining, desc, isToday)
        return DayOverviewConclusion(text, DayOverviewConclusionType.GOOD_WEATHER_FREE, DayOverviewAction.CALENDAR)
    }
    // 3.5 回落：日子形状。
    val shapeAction = when {
        first != null -> DayOverviewAction.EVENT
        topUnread != null -> DayOverviewAction.UNREAD
        else -> DayOverviewAction.CALENDAR
    }
    return DayOverviewConclusion(
        dayShapeSentence(input.events, nowMs, isToday),
        DayOverviewConclusionType.SHAPE,
        shapeAction
    )
}

// ---------- 大模型结论：提示词与解析校验 ----------

/**
 * 一览结论的系统提示词：稳定、每天字节级一模一样（不插值、不放时间戳、
 * 不放相对时间词——同一份结论晚上当「明天一览」显示、早上当「今天一览」
 * 显示，模型文本里不能有相对时间词，见 parseDayOverviewConclusion 的禁令）。
 */
val DAY_OVERVIEW_SYSTEM_PROMPT = """
你是手机桌面「一览」卡片的结论助手。根据下面提供的日历、天气、未读信息，选出目标日期最值得用户注意的一条结论。
按以下优先级选第一条适用的：
1. 天气恶劣（雨、雪、雷、雹）且目标日期有要出门的事项；
2. 第一场会议的来源应用里还有未读消息；
3. 未读总数达到 10 条以上，且开会前有一段空闲；
4. 天气好（晴或多云、最高温不超过 33 度）且最后一场会结束后有 3 小时以上空闲；
5. 以上都不适用时，用一句话概括当天的日程形状（有几个会、第一场几点、中间最长空多久、是否已开完）。
结论一句话、不超过 100 字，讲清「什么＋为什么值得注意」；只按提供的信息写，不编造时间、地点、条数，不知道的不要写。
结论里不要出现「今天」「明天」「明早」「今晚」等相对时间词——同一份结论晚上当「明天一览」显示、早上当「今天一览」显示。
只返回 JSON：{"conclusion":"...","type":"rain_out|same_source|free_time_unread|good_weather_free|shape","action":"event|unread|calendar"}，不要解释、不要多余文字。
""".trimIndent()

/** 模型结论里禁用的相对时间词：同一份结论服务两次（晚上「明天一览」＋早上「今天一览」），文本里不能有它们。 */
private val OVERVIEW_RELATIVE_DAY_WORDS = listOf(
    "今天", "明天", "明早", "明晚", "今晚", "今早", "昨晚", "昨日", "明日", "后天"
)

/** yyyyMMdd → 2026年11月21日星期六（纯函数；星期几帮模型认清目标日期）。 */
private fun formatOverviewDate(dateKey: String): String {
    if (dateKey.length != 8) return dateKey
    val year = dateKey.substring(0, 4).toIntOrNull() ?: return dateKey
    val month = dateKey.substring(4, 6).toIntOrNull() ?: return dateKey
    val day = dateKey.substring(6, 8).toIntOrNull() ?: return dateKey
    val c = Calendar.getInstance().apply { set(year, month - 1, day, 0, 0, 0) }
    val weekday = listOf("日", "一", "二", "三", "四", "五", "六")[c.get(Calendar.DAY_OF_WEEK) - 1]
    return "${year}年${month}月${day}日星期$weekday"
}

/**
 * 一览结论的用户提示词（纯函数）：只给模型需要的信息——目标日期、当前时间、
 * 天气描述与最高最低温、日程（时间／脱敏标题／脱敏地点／有无入会链接／
 * 来源应用）、未读（来源名＋条数）。不给入会链接原文、只给来源应用名；
 * 标题与地点先过 sanitizeForLlm 脱敏（沿用 SmartCardPrep 的规则）。
 */
fun buildDayOverviewUserPrompt(
    targetDateKey: String,
    nowMs: Long,
    weatherDesc: String?,
    weatherHigh: Int?,
    weatherLow: Int?,
    events: List<CalEvent>,
    unread: List<UnreadSource>
): String {
    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val nowText = "${nowCal.get(Calendar.MONTH) + 1}月${nowCal.get(Calendar.DAY_OF_MONTH)}日 ${formatClock(nowMs)}"
    val weatherText = if (weatherDesc.isNullOrBlank()) {
        "未知"
    } else if ((weatherHigh ?: 0) == 0 && (weatherLow ?: 0) == 0) {
        weatherDesc
    } else {
        "$weatherDesc，最高 ${weatherHigh}°，最低 ${weatherLow}°"
    }
    val eventLines = if (events.isEmpty()) {
        "无"
    } else {
        events.sortedBy { it.begin }.joinToString("\n") { e ->
            val title = sanitizeForLlm(e.title.ifBlank { "有日程" })
            val sb = StringBuilder()
            if (e.allDay) {
                sb.append("全天 $title")
            } else {
                sb.append("${formatClock(e.begin)}–${formatClock(e.end)} $title")
            }
            val url = extractMeetingUrl(e)
            // 入会链接原文不进提示词（只给来源应用名）：地点里若含入会链接先去掉。
            val locRaw = if (url != null) {
                e.location.replace(url, "").trim().trimEnd('.', ',', '，', '。', '；', ';').trim()
            } else {
                e.location
            }
            val loc = sanitizeForLlm(locRaw)
            if (loc.isNotBlank()) sb.append("，地点：$loc")
            if (url != null) {
                val app = meetingSourceApp(e)
                if (app != null) sb.append("（线上会，来源：$app）") else sb.append("（线上会）")
            } else if (e.calendarName.isNotBlank()) {
                sb.append("（日历：${sanitizeForLlm(e.calendarName)}）")
            }
            sb.toString()
        }
    }
    val totalUnread = unread.sumOf { it.count }
    val unreadText = if (unread.isEmpty()) {
        "无"
    } else {
        unread.sortedByDescending { it.count }
            .joinToString("、") { "${it.label} ${it.count} 条" } + "，共 $totalUnread 条"
    }
    return "目标日期：${formatOverviewDate(targetDateKey)}\n" +
        "当前时间：$nowText\n" +
        "天气：$weatherText\n" +
        "日程：\n$eventLines\n" +
        "未读：$unreadText"
}

/**
 * 解析 /chat/completions 响应里的一览结论（纯函数）：取
 * choices[0].message.content → 解析 JSON → 校验。校验不过返回 null
 * （调用方静默回落规则，不报错、不贴假清单）：
 * JSON 能解析、结论非空且 ≤100 字且单行、type/action 在允许集内、
 * 类型与文本一致（RAIN_OUT 含雨/雪/雷/雹、SAME_SOURCE/FREE_TIME_UNREAD
 * 含未读、GOOD_WEATHER_FREE 含晴/多云/适合、SHAPE 不校验）、
 * 文本里没有相对时间词（今天/明天/明早/今晚等——同一份结论服务两次）。
 */
fun parseDayOverviewConclusion(rawBody: String): DayOverviewConclusion? {
    val content = try {
        JSONObject(rawBody)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    } catch (_: Exception) {
        return null
    }
    if (content.isBlank()) return null
    val jsonText = content.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val obj = try {
        JSONObject(jsonText)
    } catch (_: Exception) {
        return null
    }
    val text = obj.optString("conclusion", "").trim()
    if (text.isEmpty() || text.length > 100 || text.contains('\n')) return null
    if (OVERVIEW_RELATIVE_DAY_WORDS.any { text.contains(it) }) return null
    val type = try {
        DayOverviewConclusionType.valueOf(obj.optString("type", "").uppercase())
    } catch (_: Exception) {
        return null
    }
    val action = try {
        DayOverviewAction.valueOf(obj.optString("action", "").uppercase())
    } catch (_: Exception) {
        return null
    }
    val consistent = when (type) {
        DayOverviewConclusionType.RAIN_OUT ->
            text.contains("雨") || text.contains("雪") || text.contains("雷") || text.contains("雹")
        DayOverviewConclusionType.SAME_SOURCE, DayOverviewConclusionType.FREE_TIME_UNREAD ->
            text.contains("未读")
        DayOverviewConclusionType.GOOD_WEATHER_FREE ->
            text.contains("晴") || text.contains("多云") || text.contains("适合")
        DayOverviewConclusionType.SHAPE -> true
    }
    if (!consistent) return null
    return DayOverviewConclusion(text, type, action)
}

// ---------- 存好的模型结论（21:30 算好，早上直接显示） ----------

/**
 * 一览结论的本地存取（SharedPreferences，按目标 dateKey yyyyMMdd 存一条）：
 * 21:30 复盘时为明天算好存下；当晚「明天一览」与次日早上「今天一览」
 * 是同一天，共用同一条存好的结论。每天只留一条，存新时清掉旧的日期。
 */
object OverviewConclusionStore {
    private const val PREFS_NAME = "overview_conclusions"
    private const val KEY_PREFIX = "conclusion_"

    fun save(context: Context, dateKey: String, conclusion: DayOverviewConclusion) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("text", conclusion.text)
            put("type", conclusion.type.name)
            put("action", conclusion.action.name)
        }
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(KEY_PREFIX) && key != KEY_PREFIX + dateKey) editor.remove(key)
        }
        editor.putString(KEY_PREFIX + dateKey, json.toString())
        editor.apply()
    }

    fun load(context: Context, dateKey: String): DayOverviewConclusion? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PREFIX + dateKey, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val text = json.optString("text", "")
            val type = DayOverviewConclusionType.valueOf(json.optString("type", ""))
            val action = DayOverviewAction.valueOf(json.optString("action", ""))
            if (text.isBlank()) null else DayOverviewConclusion(text, type, action)
        } catch (_: Exception) {
            null
        }
    }
}

/** 总览卡的天气行：今天用今天的最高/最低＋描述，明天用明天的；取不到返回 null（该行不出现）。 */
fun dayOverviewWeatherRow(weather: WeatherRepository.WeatherInfo?, isToday: Boolean): String? {
    if (weather == null) return null
    return if (isToday) {
        if (weather.desc.isBlank() && weather.high == 0 && weather.low == 0) return null
        val descPart = if (weather.desc.isNotBlank()) "${weather.desc} " else ""
        "今天$descPart${weather.low}°～${weather.high}°"
    } else {
        if (weather.tomorrowDesc.isBlank()) return null
        "明天${weather.tomorrowDesc} ${weather.tomorrowLow}°～${weather.tomorrowHigh}°"
    }
}

/** 总览卡的未读行：前 4 个来源＋余下记「等 N 个来源」。 */
fun unreadOverviewRow(unread: List<UnreadSource>): String {
    val shown = unread.take(4)
    val extra = unread.size - shown.size
    val joined = shown.joinToString("、") { "${it.label} ${it.count} 条" }
    return "未读：" + joined + if (extra > 0) " 等 $extra 个来源" else ""
}

/**
 * 结论动作解析成具体动作＋文案＋小标（纯函数）：首选结论的动作目标，
 * 目标不可用时顺延（EVENT → UNREAD → CALENDAR 的顺序试）；三个目标都
 * 不可用返回 null（调用方不出卡）。
 */
private fun resolveOverviewAction(
    preferred: DayOverviewAction,
    input: DayOverviewInput
): Triple<SmartCardAction, String, TargetApp?>? {
    val order = listOf(preferred) + DayOverviewAction.values().filter { it != preferred }
    for (candidate in order) {
        when (candidate) {
            DayOverviewAction.EVENT -> {
                val event = input.firstEvent ?: continue
                return Triple(
                    SmartCardAction.CalendarEvent(event.eventId, input.meetingUrl),
                    if (input.meetingUrl != null) "加入会议" else "看日程",
                    input.meetingTargetApp
                )
            }
            DayOverviewAction.UNREAD -> {
                val top = input.unread.maxByOrNull { it.count } ?: continue
                return Triple(
                    SmartCardAction.LaunchApp(top.packageName),
                    "打开看看",
                    TargetApp(top.packageName, top.label)
                )
            }
            DayOverviewAction.CALENDAR -> {
                val pkg = input.calendarAppPackage ?: continue
                return Triple(
                    SmartCardAction.LaunchApp(pkg),
                    "看日程",
                    input.calendarAppTargetApp
                )
            }
        }
    }
    return null
}

/** 总览卡的 SmartCard 拼装（两路共用：结论版 leadBold=true，533 版 false）。 */
private fun overviewCard(
    input: DayOverviewInput,
    fact: String,
    action: SmartCardAction,
    actionLabel: String,
    targetApp: TargetApp?,
    leadBold: Boolean
): SmartCard {
    val isToday = input.dayOffset == 0
    val dayLabel = if (isToday) "今天一览" else "明天一览"
    val id = "day_overview_${if (isToday) "today" else "tomorrow"}_${input.dateKey}"
    return SmartCard(
        id = id,
        kind = SmartCardKind.DAY_OVERVIEW,
        sourceKey = id,
        sourceLabel = dayLabel,
        fact = fact,
        prep = null,
        actionLabel = actionLabel,
        sortMs = Long.MAX_VALUE,
        deadlineMs = input.windowEndMs,
        action = action,
        appearFromMs = input.windowStartMs,
        windowStartMs = 0L,
        targetApp = targetApp,
        leadBold = leadBold
    )
}

/**
 * 组装总览卡（纯函数）。排序：sortMs = Long.MAX_VALUE，排在有时效真卡
 * 之后、设默认卡之前（引导卡只在无真卡时出现，本来就在它之后）；
 * 截止点 = 窗口结束，过了窗口卡片即消失；id 按日拼（点「我知道了」
 * = markDone 当天不再出，第二天 id 不同、正常再出）。
 *
 * 结论版（有日历权限）：正文第一行是结论句（lead，加粗），下面只显示
 * 结论没覆盖的来源（去重：RAIN_OUT／GOOD_WEATHER_FREE 不显示天气行；
 * SAME_SOURCE／FREE_TIME_UNREAD 不显示未读行；SHAPE 两行都显示）。
 * 按钮跟着结论走（EVENT → 第一场会；UNREAD → 未读最多来源；
 * CALENDAR → 日历应用），目标不可用时顺延。
 *
 * 533 三行并列版（没日历权限）：天气＋第一场会＋未读三行，动作链
 * 有会 → 日历/入会，没会 → 未读来源，再没 → 日历应用。
 */
fun buildDayOverviewCard(input: DayOverviewInput): SmartCard? {
    val isToday = input.dayOffset == 0
    val conclusion = if (input.hasCalendarPermission) dayOverviewConclusion(input, input.nowMs) else null
    if (conclusion != null) {
        // 三个源全缺 → 不出卡（不贴「今天没什么事」的假清单；沿用 533 的判定）。
        if (dayOverviewWeatherRow(input.weather, isToday) == null &&
            input.firstEvent == null && input.unread.isEmpty()
        ) return null
        val rows = mutableListOf(conclusion.text)
        val showWeather = conclusion.type != DayOverviewConclusionType.RAIN_OUT &&
            conclusion.type != DayOverviewConclusionType.GOOD_WEATHER_FREE
        val showUnread = conclusion.type != DayOverviewConclusionType.SAME_SOURCE &&
            conclusion.type != DayOverviewConclusionType.FREE_TIME_UNREAD
        if (showWeather) dayOverviewWeatherRow(input.weather, isToday)?.let { rows += it }
        if (showUnread && input.unread.isNotEmpty()) rows += unreadOverviewRow(input.unread)
        val (action, actionLabel, targetApp) = resolveOverviewAction(conclusion.action, input) ?: return null
        return overviewCard(input, rows.joinToString("\n"), action, actionLabel, targetApp, leadBold = true)
    }
    val rows = mutableListOf<String>()
    dayOverviewWeatherRow(input.weather, isToday)?.let { rows += it }
    val event = input.firstEvent
    if (event != null) {
        val title = event.title.ifBlank { "有日程" }
        rows += "第一场会 ${formatClock(event.begin)} $title"
    }
    if (input.unread.isNotEmpty()) {
        rows += unreadOverviewRow(input.unread)
    }
    // 三个源全缺 → 不出卡（不贴「今天没什么事」的假清单）。
    if (rows.isEmpty()) return null
    // 动作：有会进日历/入会，没会进未读来源，再没就进日历应用；都没有 → 不出卡。
    val (action, actionLabel, targetApp) = resolveOverviewAction(DayOverviewAction.EVENT, input) ?: return null
    return overviewCard(input, rows.joinToString("\n"), action, actionLabel, targetApp, leadBold = false)
}
