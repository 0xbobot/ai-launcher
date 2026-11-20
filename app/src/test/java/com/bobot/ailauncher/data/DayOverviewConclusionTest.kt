package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 「今天 / 明天一览」重设计（第四十八批）单测：
 * 跨来源结论优先级五条、去重规则、Key 分流（有 Key 走模型、无 Key 走规则、
 * 失败回落规则）、脱敏仍在、明天一览规则（08:00 起点、「明天」）。
 * DayOverviewTest 里的 input() helper 是私有的，这里自带一份。
 */
class DayOverviewConclusionTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun weather(
        desc: String = "晴", high: Int = 26, low: Int = 18,
        tomorrowHigh: Int = 24, tomorrowLow: Int = 17, tomorrowDesc: String = "多云"
    ) = WeatherRepository.WeatherInfo(
        temp = 20, desc = desc, city = "深圳", high = high, low = low,
        tomorrowHigh = tomorrowHigh, tomorrowLow = tomorrowLow, tomorrowDesc = tomorrowDesc
    )

    private fun event(
        title: String = "产品周会", day: Int = 15, hour: Int = 9, min: Int = 30,
        endHour: Int = 10, endMin: Int = 30, eventId: Long = 123L,
        allDay: Boolean = false, location: String = "", calendarName: String = ""
    ) = CalEvent(
        title = title,
        begin = ms(2026, 11, day, hour, min),
        end = ms(2026, 11, day, endHour, endMin),
        location = location,
        allDay = allDay,
        eventId = eventId,
        calendarName = calendarName
    )

    private fun input(
        dayOffset: Int = 0,
        dateKey: String = "20261115",
        weather: WeatherRepository.WeatherInfo? = weather(),
        events: List<CalEvent> = listOf(event()),
        nowMs: Long = ms(2026, 11, 15, 7, 0),
        hasCalendarPermission: Boolean = true,
        modelConclusion: DayOverviewConclusion? = null,
        unread: List<UnreadSource> = listOf(
            UnreadSource("com.tencent.mm", "微信", 5),
            UnreadSource("com.ss.android.lark", "飞书", 3)
        ),
        calendarAppPackage: String? = "com.android.calendar",
        calendarAppTargetApp: TargetApp? = TargetApp("com.android.calendar", "日历")
    ): DayOverviewInput {
        val first = firstRelevantEvent(events, nowMs)
        return DayOverviewInput(
            dayOffset = dayOffset,
            dateKey = dateKey,
            windowStartMs = ms(2026, 11, 15, 6, 30),
            windowEndMs = ms(2026, 11, 15, 8, 30),
            weather = weather,
            firstEvent = first,
            meetingUrl = first?.let { extractMeetingUrl(it) },
            meetingTargetApp = first?.let { TargetApp(null, "豆包日历") },
            unread = unread,
            calendarAppPackage = calendarAppPackage,
            calendarAppTargetApp = calendarAppTargetApp,
            events = events,
            nowMs = nowMs,
            hasCalendarPermission = hasCalendarPermission,
            modelConclusion = modelConclusion
        )
    }

    private fun conclusion(
        dayOffset: Int = 0,
        weather: WeatherRepository.WeatherInfo? = weather(),
        events: List<CalEvent> = listOf(event()),
        nowMs: Long = ms(2026, 11, 15, 7, 0),
        unread: List<UnreadSource> = listOf(
            UnreadSource("com.tencent.mm", "微信", 5),
            UnreadSource("com.ss.android.lark", "飞书", 3)
        ),
        modelConclusion: DayOverviewConclusion? = null
    ): DayOverviewConclusion? = dayOverviewConclusion(
        input(
            dayOffset = dayOffset, weather = weather, events = events,
            nowMs = nowMs, unread = unread, modelConclusion = modelConclusion
        ),
        nowMs
    )

    private fun chatBody(content: String): String =
        """{"choices":[{"message":{"content":$content}}]}"""

    // ---------- 3.1 雨＋出门 ----------

    @Test
    fun rainOutToday() {
        val c = conclusion(
            weather = weather(desc = "雨"),
            events = listOf(event(title = "去银行办业务", hour = 10, min = 0, endHour = 11, endMin = 0, location = "银行"))
        )
        assertEquals(DayOverviewConclusionType.RAIN_OUT, c!!.type)
        assertEquals(DayOverviewAction.EVENT, c.action)
        assertEquals("今天有雨，10:00 去银行办业务要出门，带伞、早点出门", c.text)
    }

    @Test
    fun rainOutWinsOverSameSource() {
        // 雨＋出门（第二场）和同来源（第一场飞书会）都适用 → 雨最优先。
        val events = listOf(
            event(title = "团队周会", hour = 10, min = 0, endHour = 11, endMin = 0, location = "https://meeting.feishu.cn/l/123"),
            event(title = "去银行办业务", hour = 15, min = 0, endHour = 16, endMin = 0, location = "银行", eventId = 124L)
        )
        val c = conclusion(
            weather = weather(desc = "雨"),
            events = events,
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.RAIN_OUT, c!!.type)
        assertEquals("今天有雨，15:00 去银行办业务要出门，带伞、早点出门", c.text)
    }

    @Test
    fun rainOutSkipsOnlineMeeting() {
        // 只有线上会：雨不影响线上会，3.1 不触发 → 同来源接上。
        val c = conclusion(
            weather = weather(desc = "雷阵雨"),
            events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c!!.type)
    }

    @Test
    fun rainOutSkipsNoLocation() {
        // 事件没地点：不知道要不要出门，不瞎猜 → 3.1 不触发。
        val c = conclusion(
            weather = weather(desc = "小雨"),
            events = listOf(event(location = ""))
        )
        assertEquals(DayOverviewConclusionType.SHAPE, c!!.type)
    }

    // ---------- 3.2 同来源 ----------

    @Test
    fun sameSourceByDomain() {
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(title = "团队周会", hour = 10, min = 0, endHour = 11, endMin = 0, location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c!!.type)
        assertEquals(DayOverviewAction.UNREAD, c.action)
        assertEquals("10:00 飞书团队周会，飞书还有 18 条未读，会前看一眼", c.text)
    }

    @Test
    fun sameSourceByCalendarName() {
        // 没入会链接，日历账户名「飞书日历」含未读应用名「飞书」也算同来源。
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(calendarName = "飞书日历")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c!!.type)
        assertEquals("09:30 飞书产品周会，飞书还有 18 条未读，会前看一眼", c.text)
    }

    @Test
    fun sameSourceMismatchFallsThrough() {
        // 会是飞书的，未读最多是微信 → 对不上，往下走（未读 ≥10 → 空闲 × 未读）。
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.tencent.mm", "微信", 12), UnreadSource("com.ss.android.lark", "飞书", 3))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("09:30 开会前还有 2.5 小时，微信 12 条未读现在看", c.text)
    }

    @Test
    fun sameSourceBeatsFreeTimeUnread() {
        // 同来源和空闲 × 未读都适用 → 同来源先。
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c!!.type)
    }

    // ---------- 3.3 空闲 × 未读 ----------

    @Test
    fun freeTimeUnreadLongWindow() {
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("10:00 开会前还有 3 小时，飞书 18 条未读现在看", c.text)
    }

    @Test
    fun freeTimeUnreadShortWindow() {
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
            nowMs = ms(2026, 11, 15, 9, 20),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("10:00 开会，只剩 40 分钟，18 条未读开完会再看", c.text)
    }

    @Test
    fun freeTimeUnreadNoMeeting() {
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = emptyList(),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("今天没有会，飞书 18 条未读有空慢慢看", c.text)
    }

    @Test
    fun freeTimeUnreadBelowThresholdFallsThrough() {
        // 未读总数 9 条（<10）不值得当 lead → 往下走（阴天 → 形状句）。
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 6), UnreadSource("com.tencent.mm", "微信", 3))
        )
        assertEquals(DayOverviewConclusionType.SHAPE, c!!.type)
    }

    @Test
    fun freeTimeUnreadBeatsGoodWeather() {
        // 空闲 × 未读和好天气 × 大段空闲都适用 → 空闲 × 未读先。
        val c = conclusion(
            events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
    }

    // ---------- 3.4 好天气 × 大段空闲 ----------

    @Test
    fun goodWeatherFreeToday() {
        val c = conclusion()
        assertEquals(DayOverviewConclusionType.GOOD_WEATHER_FREE, c!!.type)
        assertEquals(DayOverviewAction.CALENDAR, c.action)
        assertEquals("10:30之后全天空着，今天晴，适合出去走走", c.text)
    }

    @Test
    fun goodWeatherFreeTooHotSkips() {
        // 最高温 34° 太热，不建议出门 → 回落形状句。
        val c = conclusion(weather = weather(high = 34))
        assertEquals(DayOverviewConclusionType.SHAPE, c!!.type)
    }

    @Test
    fun goodWeatherFreeNoMeeting() {
        val c = conclusion(events = emptyList())
        assertEquals(DayOverviewConclusionType.GOOD_WEATHER_FREE, c!!.type)
        assertEquals("今天没有会，今天晴，适合出去走走", c.text)
    }

    // ---------- 3.5 形状句 ----------

    @Test
    fun shapeNoMeeting() {
        assertEquals("今天没有会", dayShapeSentence(emptyList(), ms(2026, 11, 15, 7, 0), true))
    }

    @Test
    fun shapeAllEnded() {
        val events = listOf(event(hour = 9, min = 30, endHour = 10, endMin = 30))
        assertEquals("今天的会都开完了", dayShapeSentence(events, ms(2026, 11, 15, 17, 0), true))
    }

    @Test
    fun shapeAllDayOnly() {
        val events = listOf(event(title = "出差", hour = 0, min = 0, endHour = 23, endMin = 59, allDay = true))
        assertEquals("今天没有会，全天「出差」", dayShapeSentence(events, ms(2026, 11, 15, 7, 0), true))
    }

    @Test
    fun shapeSingleUpcoming() {
        val events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0))
        assertEquals(
            "今天只有 1 个会，10:00 团队周会，11:00 之后全天空着",
            dayShapeSentence(events, ms(2026, 11, 15, 7, 0), true)
        )
    }

    @Test
    fun shapeSingleUpcomingLateEnd() {
        // 最后一场结束 16:00（>14:00）→ 写「之后空着」，不写「全天空着」。
        val events = listOf(event(hour = 15, min = 0, endHour = 16, endMin = 0))
        assertEquals(
            "今天只有 1 个会，15:00 团队周会，16:00 之后空着",
            dayShapeSentence(events, ms(2026, 11, 15, 7, 0), true)
        )
    }

    @Test
    fun shapeSingleInProgress() {
        val events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0))
        assertEquals(
            "团队周会进行中，11:00 结束，之后全天空着",
            dayShapeSentence(events, ms(2026, 11, 15, 10, 30), true)
        )
    }

    @Test
    fun shapeConsecutive() {
        val events = listOf(
            event(hour = 10, min = 0, endHour = 11, endMin = 0, eventId = 1L),
            event(title = "午会", hour = 11, min = 0, endHour = 12, endMin = 0, eventId = 2L),
            event(title = "下午会", hour = 12, min = 0, endHour = 13, endMin = 0, eventId = 3L)
        )
        assertEquals(
            "今天 3 个会连轴转，第一场 10:00 团队周会",
            dayShapeSentence(events, ms(2026, 11, 15, 7, 0), true)
        )
    }

    @Test
    fun shapeMaxGap() {
        val events = listOf(
            event(hour = 10, min = 0, endHour = 11, endMin = 0, eventId = 1L),
            event(title = "午会", hour = 13, min = 0, endHour = 14, endMin = 0, eventId = 2L),
            event(title = "下午会", hour = 15, min = 0, endHour = 16, endMin = 0, eventId = 3L)
        )
        assertEquals(
            "今天 3 个会，第一场 10:00 团队周会，中间最长空 2 小时",
            dayShapeSentence(events, ms(2026, 11, 15, 7, 0), true)
        )
    }

    @Test
    fun shapeFirstInProgressMulti() {
        val events = listOf(
            event(hour = 10, min = 0, endHour = 11, endMin = 0, eventId = 1L),
            event(title = "午会", hour = 13, min = 0, endHour = 14, endMin = 0, eventId = 2L)
        )
        assertEquals(
            "团队周会进行中，11:00 结束，下一场 13:00",
            dayShapeSentence(events, ms(2026, 11, 15, 10, 30), true)
        )
    }

    @Test
    fun noCalendarPermissionReturnsNull() {
        val c = dayOverviewConclusion(
            input(hasCalendarPermission = false),
            ms(2026, 11, 15, 7, 0)
        )
        assertNull(c)
    }

    // ---------- 去重规则（结论用到的来源不再出配角行） ----------

    @Test
    fun dedupRainOutHidesWeatherRow() {
        val card = buildDayOverviewCard(
            input(
                weather = weather(desc = "雨"),
                events = listOf(event(title = "去银行办业务", hour = 10, min = 0, endHour = 11, endMin = 0, location = "银行"))
            )
        )
        assertTrue(card!!.fact.startsWith("今天有雨，10:00 去银行办业务要出门"))
        assertFalse(card.fact.contains("今天雨"))
        assertTrue(card.fact.contains("未读：微信 5 条、飞书 3 条"))
    }

    @Test
    fun dedupSameSourceHidesUnreadRow() {
        val card = buildDayOverviewCard(
            input(
                weather = weather(desc = "阴"),
                events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
                unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
            )
        )
        assertTrue(card!!.fact.startsWith("09:30 飞书产品周会，飞书还有 18 条未读，会前看一眼"))
        assertTrue(card.fact.contains("今天阴 18°～26°"))
        assertFalse(card.fact.contains("未读："))
    }

    @Test
    fun dedupFreeTimeUnreadHidesUnreadRow() {
        val card = buildDayOverviewCard(
            input(
                weather = weather(desc = "阴"),
                events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
                unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
            )
        )
        assertTrue(card!!.fact.startsWith("10:00 开会前还有 3 小时，飞书 18 条未读现在看"))
        assertTrue(card.fact.contains("今天阴 18°～26°"))
        assertFalse(card.fact.contains("未读："))
    }

    @Test
    fun dedupGoodWeatherHidesWeatherRow() {
        val card = buildDayOverviewCard(input())
        assertTrue(card!!.fact.startsWith("10:30之后全天空着，今天晴，适合出去走走"))
        assertFalse(card.fact.contains("今天晴 18°"))
        assertTrue(card.fact.contains("未读：微信 5 条、飞书 3 条"))
    }

    @Test
    fun dedupShapeShowsBothRows() {
        val card = buildDayOverviewCard(
            input(
                weather = weather(desc = "阴"),
                events = listOf(event(hour = 10, min = 0, endHour = 11, endMin = 0)),
                unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 6), UnreadSource("com.tencent.mm", "微信", 3))
            )
        )
        assertTrue(card!!.fact.startsWith("今天只有 1 个会，10:00 产品周会，11:00 之后全天空着"))
        assertTrue(card.fact.contains("今天阴 18°～26°"))
        assertTrue(card.fact.contains("未读：飞书 6 条、微信 3 条"))
    }

    // ---------- Key 分流 ----------

    @Test
    fun modelConclusionUsedWhenPremiseHolds() {
        // 有 Key、存好的模型结论前提还对得上 → 直接用模型文本。
        val stored = DayOverviewConclusion(
            "09:30 飞书团队周会，飞书还有 18 条未读，会前看一眼",
            DayOverviewConclusionType.SAME_SOURCE,
            DayOverviewAction.UNREAD
        )
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5)),
            modelConclusion = stored
        )
        assertEquals(stored.text, c!!.text)
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c.type)
    }

    @Test
    fun modelConclusionFallsBackWhenPremiseFails() {
        // 存好的 RAIN_OUT 结论，但现在已经不下雨了 → 回落规则（阴天 → 形状句）。
        val stored = DayOverviewConclusion(
            "有雨，10:00 去银行办业务要出门，带伞",
            DayOverviewConclusionType.RAIN_OUT,
            DayOverviewAction.EVENT
        )
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(title = "去银行办业务", hour = 10, min = 0, endHour = 11, endMin = 0, location = "银行")),
            modelConclusion = stored
        )
        assertEquals(DayOverviewConclusionType.SHAPE, c!!.type)
        assertFalse(c.text.contains("雨"))
    }

    @Test
    fun noModelConclusionUsesRules() {
        val c = conclusion(
            weather = weather(desc = "阴"),
            events = listOf(event(location = "https://meeting.feishu.cn/l/123")),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c!!.type)
        assertEquals("09:30 飞书产品周会，飞书还有 18 条未读，会前看一眼", c.text)
    }

    // ---------- 提示词与解析校验 ----------

    @Test
    fun systemPromptHasNoTimestampOrRelativeDayWord() {
        // 系统提示词稳定、每天字节级一模一样：不插值、不放时间戳、不放相对时间词。
        assertFalse(DAY_OVERVIEW_SYSTEM_PROMPT.contains("2026"))
        assertFalse(DAY_OVERVIEW_SYSTEM_PROMPT.contains("今天"))
        assertFalse(DAY_OVERVIEW_SYSTEM_PROMPT.contains("明天"))
    }

    @Test
    fun userPromptSanitizesSensitiveInfo() {
        // 模型输入不含手机号／身份证号／家庭住址等；入会链接只给来源应用名、不给原文。
        val prompt = buildDayOverviewUserPrompt(
            targetDateKey = "20261121",
            nowMs = ms(2026, 11, 20, 21, 30),
            weatherDesc = "多云",
            weatherHigh = 26,
            weatherLow = 18,
            events = listOf(
                event(
                    title = "张三先生 13812345678 来访", day = 21, hour = 10, min = 0,
                    endHour = 11, endMin = 0, location = "石围路123号3栋2单元501室"
                ),
                event(
                    title = "团队周会", day = 21, hour = 15, min = 0,
                    endHour = 16, endMin = 0, location = "https://meeting.feishu.cn/l/abc123"
                )
            ),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18))
        )
        assertTrue(prompt.contains("2026年11月21日星期六"))
        assertFalse(prompt.contains("13812345678"))
        assertTrue(prompt.contains("[电话]"))
        assertFalse(prompt.contains("张三"))
        assertFalse(prompt.contains("123号3栋2单元501室"))
        assertTrue(prompt.contains("[家庭住址]"))
        assertFalse(prompt.contains("meeting.feishu.cn"))
        assertFalse(prompt.contains("abc123"))
        assertTrue(prompt.contains("来源：飞书"))
        assertTrue(prompt.contains("飞书 18 条"))
    }

    @Test
    fun parseValidConclusion() {
        val body = chatBody(
            "\"{\\\"conclusion\\\":\\\"10:00 飞书团队周会，飞书还有 18 条未读，会前看一眼\\\",\\\"type\\\":\\\"same_source\\\",\\\"action\\\":\\\"unread\\\"}\""
        )
        val c = parseDayOverviewConclusion(body)
        assertEquals("10:00 飞书团队周会，飞书还有 18 条未读，会前看一眼", c!!.text)
        assertEquals(DayOverviewConclusionType.SAME_SOURCE, c.type)
        assertEquals(DayOverviewAction.UNREAD, c.action)
    }

    @Test
    fun parseInvalidJsonReturnsNull() {
        assertNull(parseDayOverviewConclusion("not json"))
        assertNull(parseDayOverviewConclusion(chatBody("\"not an object\"")))
    }

    @Test
    fun parseTypeTextMismatchReturnsNull() {
        // type 说是雨＋出门，文本里却没有雨／雪／雷／雹 → 不合格。
        val body = chatBody(
            "\"{\\\"conclusion\\\":\\\"10:00 去银行办业务要出门\\\",\\\"type\\\":\\\"rain_out\\\",\\\"action\\\":\\\"event\\\"}\""
        )
        assertNull(parseDayOverviewConclusion(body))
    }

    @Test
    fun parseTooLongReturnsNull() {
        val longText = "雨".repeat(101)
        val body = chatBody(
            "\"{\\\"conclusion\\\":\\\"$longText\\\",\\\"type\\\":\\\"rain_out\\\",\\\"action\\\":\\\"event\\\"}\""
        )
        assertNull(parseDayOverviewConclusion(body))
    }

    @Test
    fun parseRelativeDayWordReturnsNull() {
        // 同一份结论晚上当「明天一览」、早上当「今天一览」，文本里不能有相对时间词。
        val body = chatBody(
            "\"{\\\"conclusion\\\":\\\"明天下雨，10:00 去银行办业务要出门，记得带伞\\\",\\\"type\\\":\\\"rain_out\\\",\\\"action\\\":\\\"event\\\"}\""
        )
        assertNull(parseDayOverviewConclusion(body))
    }

    @Test
    fun parseUnknownTypeOrActionReturnsNull() {
        val badType = chatBody(
            "\"{\\\"conclusion\\\":\\\"有雨要出门\\\",\\\"type\\\":\\\"unknown_type\\\",\\\"action\\\":\\\"event\\\"}\""
        )
        assertNull(parseDayOverviewConclusion(badType))
        val badAction = chatBody(
            "\"{\\\"conclusion\\\":\\\"有雨要出门\\\",\\\"type\\\":\\\"rain_out\\\",\\\"action\\\":\\\"fly\\\"}\""
        )
        assertNull(parseDayOverviewConclusion(badAction))
    }

    // ---------- 明天一览（08:00 起点、「明天」） ----------

    @Test
    fun tomorrowRainOut() {
        val c = conclusion(
            dayOffset = 1,
            weather = weather(tomorrowDesc = "雨"),
            events = listOf(event(title = "去银行办业务", day = 16, hour = 10, min = 0, endHour = 11, endMin = 0, location = "银行")),
            nowMs = ms(2026, 11, 15, 21, 30)
        )
        assertEquals(DayOverviewConclusionType.RAIN_OUT, c!!.type)
        assertEquals("明天下雨，10:00 去银行办业务要出门，记得带伞", c.text)
    }

    @Test
    fun tomorrowFreeTimeLongWindowFromEight() {
        // 明天窗口从 08:00 算起：10:00 开会 → 明早有 2 小时。
        val c = conclusion(
            dayOffset = 1,
            weather = weather(tomorrowDesc = "阴"),
            events = listOf(event(day = 16, hour = 10, min = 0, endHour = 11, endMin = 0)),
            nowMs = ms(2026, 11, 15, 21, 30),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("明天 10:00 开会，明早有 2 小时，飞书 18 条未读明早看", c.text)
    }

    @Test
    fun tomorrowFreeTimeShortWindow() {
        // 明天 08:30 就开会 → 明早窗口只有半小时，今晚看。
        val c = conclusion(
            dayOffset = 1,
            weather = weather(tomorrowDesc = "阴"),
            events = listOf(event(day = 16, hour = 8, min = 30, endHour = 9, endMin = 30)),
            nowMs = ms(2026, 11, 15, 21, 30),
            unread = listOf(UnreadSource("com.ss.android.lark", "飞书", 18), UnreadSource("com.tencent.mm", "微信", 5))
        )
        assertEquals(DayOverviewConclusionType.FREE_TIME_UNREAD, c!!.type)
        assertEquals("明天 08:30 就开会，18 条未读今晚看", c.text)
    }

    @Test
    fun tomorrowGoodWeatherFree() {
        val c = conclusion(
            dayOffset = 1,
            events = listOf(event(day = 16, hour = 10, min = 0, endHour = 11, endMin = 0)),
            nowMs = ms(2026, 11, 15, 21, 30)
        )
        assertEquals(DayOverviewConclusionType.GOOD_WEATHER_FREE, c!!.type)
        assertEquals("明天 11:00之后空着，天多云，适合出去走走", c.text)
    }

    @Test
    fun tomorrowShapeNoMeeting() {
        val c = conclusion(
            dayOffset = 1,
            weather = weather(tomorrowDesc = "阴"),
            events = emptyList(),
            nowMs = ms(2026, 11, 15, 21, 30),
            unread = emptyList()
        )
        assertEquals(DayOverviewConclusionType.SHAPE, c!!.type)
        assertEquals("明天没有会", c.text)
    }

    @Test
    fun tomorrowShapeNeverSaysAllEnded() {
        // 明天同一套，但不会出现「都开完了」（明天的事件都还没开始）。
        val events = listOf(event(day = 16, hour = 10, min = 0, endHour = 11, endMin = 0))
        assertEquals(
            "明天只有 1 个会，10:00 团队周会，11:00 之后全天空着",
            dayShapeSentence(events, ms(2026, 11, 15, 21, 30), false)
        )
    }
}
