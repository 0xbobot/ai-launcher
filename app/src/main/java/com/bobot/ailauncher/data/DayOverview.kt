package com.bobot.ailauncher.data

import java.util.Calendar

/**
 * 节点一「今天 / 明天一览」（方案 ac-missing-nodes-plan.md 第一批）：
 * 起床窗出「今天一览」、睡前窗（21:30 锚点）出「明天一览」，三行
 * （天气＋第一场会＋未读）拼成一张总览卡，进 524 单卡槽轮换。
 *
 * 纯组装——所有 Android 依赖（查日历、读天气缓存、读角标、解析日历
 * 应用）都在 SmartCardRepository.dayOverviewCard 里做，这里只认
 * 已取到的数据。
 *
 * 降级（写死）：缺哪行就不显示哪行；三个源全缺不出卡；没有可点的
 * 动作（没会、没未读、也没能打开的日历应用）也不出卡——不出没有
 * 下一步的卡。
 */

/** 未读来源：只记包名、显示名与条数，不读正文、不展示预览。 */
data class UnreadSource(
    val packageName: String,
    val label: String,
    val count: Int
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
    val calendarAppTargetApp: TargetApp?
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

/**
 * 组装总览卡（纯函数）。排序：sortMs = Long.MAX_VALUE，排在有时效真卡
 * 之后、设默认卡之前（引导卡只在无真卡时出现，本来就在它之后）；
 * 截止点 = 窗口结束，过了窗口卡片即消失；id 按日拼（点「我知道了」
 * = markDone 当天不再出，第二天 id 不同、正常再出）。
 */
fun buildDayOverviewCard(input: DayOverviewInput): SmartCard? {
    val isToday = input.dayOffset == 0
    val rows = mutableListOf<String>()
    dayOverviewWeatherRow(input.weather, isToday)?.let { rows += it }
    val event = input.firstEvent
    if (event != null) {
        val title = event.title.ifBlank { "有日程" }
        rows += "第一场会 ${formatClock(event.begin)} $title"
    }
    if (input.unread.isNotEmpty()) {
        val shown = input.unread.take(4)
        val extra = input.unread.size - shown.size
        val joined = shown.joinToString("、") { "${it.label} ${it.count} 条" }
        rows += "未读：" + joined + if (extra > 0) " 等 $extra 个来源" else ""
    }
    // 三个源全缺 → 不出卡（不贴「今天没什么事」的假清单）。
    if (rows.isEmpty()) return null
    // 动作：有会进日历/入会，没会进未读来源，再没就进日历应用；都没有 → 不出卡。
    val (action, actionLabel, targetApp) = when {
        event != null -> Triple(
            SmartCardAction.CalendarEvent(event.eventId, input.meetingUrl),
            if (input.meetingUrl != null) "加入会议" else "看日程",
            input.meetingTargetApp
        )
        input.unread.isNotEmpty() -> {
            val top = input.unread.first()
            Triple(
                SmartCardAction.LaunchApp(top.packageName),
                "打开看看",
                TargetApp(top.packageName, top.label)
            )
        }
        input.calendarAppPackage != null -> Triple(
            SmartCardAction.LaunchApp(input.calendarAppPackage),
            "看日程",
            input.calendarAppTargetApp
        )
        else -> return null
    }
    val dayLabel = if (isToday) "今天一览" else "明天一览"
    val id = "day_overview_${if (isToday) "today" else "tomorrow"}_${input.dateKey}"
    return SmartCard(
        id = id,
        kind = SmartCardKind.DAY_OVERVIEW,
        sourceKey = id,
        sourceLabel = dayLabel,
        fact = rows.joinToString("\n"),
        prep = null,
        actionLabel = actionLabel,
        sortMs = Long.MAX_VALUE,
        deadlineMs = input.windowEndMs,
        action = action,
        appearFromMs = input.windowStartMs,
        windowStartMs = 0L,
        targetApp = targetApp
    )
}
