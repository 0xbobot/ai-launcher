package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 节点一「今天 / 明天一览」纯组装单测（方案第一批）：
 * 三行拼法、动作选择、降级组合、按日抑制的 id 口径。
 * （markDone 本身的抑制语义由 SmartCardLogicTest 锁，这里只锁
 * id 按日拼、同一天稳定、第二天不同。）
 */
class DayOverviewTest {

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
        title: String = "产品周会", hour: Int = 9, min: Int = 30,
        endHour: Int = 10, endMin: Int = 30, eventId: Long = 123L,
        allDay: Boolean = false
    ) = CalEvent(
        title = title,
        begin = ms(2026, 11, 15, hour, min),
        end = ms(2026, 11, 15, endHour, endMin),
        location = "",
        allDay = allDay,
        eventId = eventId
    )

    private fun input(
        dayOffset: Int = 0,
        dateKey: String = "20261115",
        weather: WeatherRepository.WeatherInfo? = weather(),
        firstEvent: CalEvent? = event(),
        meetingUrl: String? = null,
        meetingTargetApp: TargetApp? = TargetApp(null, "豆包日历"),
        unread: List<UnreadSource> = listOf(
            UnreadSource("com.tencent.mm", "微信", 5),
            UnreadSource("com.ss.android.lark", "飞书", 3)
        ),
        calendarAppPackage: String? = "com.android.calendar",
        calendarAppTargetApp: TargetApp? = TargetApp("com.android.calendar", "日历")
    ) = DayOverviewInput(
        dayOffset = dayOffset,
        dateKey = dateKey,
        windowStartMs = ms(2026, 11, 15, 6, 30),
        windowEndMs = ms(2026, 11, 15, 8, 30),
        weather = weather,
        firstEvent = firstEvent,
        meetingUrl = meetingUrl,
        meetingTargetApp = meetingTargetApp,
        unread = unread,
        calendarAppPackage = calendarAppPackage,
        calendarAppTargetApp = calendarAppTargetApp
    )

    @Test
    fun todayFullCardThreeRows() {
        val card = buildDayOverviewCard(input())
        assertEquals("day_overview_today_20261115", card!!.id)
        assertEquals(SmartCardKind.DAY_OVERVIEW, card.kind)
        assertEquals("今天一览", card.sourceLabel)
        assertEquals(
            "今天晴 18°～26°\n第一场会 09:30 产品周会\n未读：微信 5 条、飞书 3 条",
            card.fact
        )
        assertEquals(SmartCardAction.CalendarEvent(123L, null), card.action)
        assertEquals("看日程", card.actionLabel)
        assertEquals(TargetApp(null, "豆包日历"), card.targetApp)
        assertEquals(Long.MAX_VALUE, card.sortMs)
        assertEquals(ms(2026, 11, 15, 8, 30), card.deadlineMs)
        assertEquals(ms(2026, 11, 15, 6, 30), card.appearFromMs)
    }

    @Test
    fun meetingWithUrlJoinsMeeting() {
        val card = buildDayOverviewCard(input(meetingUrl = "https://meeting.feishu.cn/l/123"))
        assertEquals(
            SmartCardAction.CalendarEvent(123L, "https://meeting.feishu.cn/l/123"),
            card!!.action
        )
        assertEquals("加入会议", card.actionLabel)
    }

    @Test
    fun blankTitleShowsHasSchedule() {
        val card = buildDayOverviewCard(input(firstEvent = event(title = "  ")))
        assertTrue(card!!.fact.contains("第一场会 09:30 有日程"))
    }

    @Test
    fun tomorrowCardUsesTomorrowWeather() {
        val card = buildDayOverviewCard(
            input(dayOffset = 1, dateKey = "20261116", firstEvent = event(hour = 10, min = 0, endHour = 11, endMin = 0))
        )
        assertEquals("day_overview_tomorrow_20261116", card!!.id)
        assertEquals("明天一览", card.sourceLabel)
        assertTrue(card.fact.startsWith("明天多云 17°～24°\n第一场会 10:00 产品周会"))
    }

    @Test
    fun noWeatherHidesWeatherRow() {
        val card = buildDayOverviewCard(input(weather = null))
        assertEquals("第一场会 09:30 产品周会\n未读：微信 5 条、飞书 3 条", card!!.fact)
    }

    @Test
    fun tomorrowWeatherMissingHidesWeatherRow() {
        val card = buildDayOverviewCard(
            input(dayOffset = 1, dateKey = "20261116", weather = weather(tomorrowDesc = ""))
        )
        assertTrue(!card!!.fact.contains("明天"))
        assertTrue(card.fact.contains("第一场会"))
    }

    @Test
    fun noMeetingOpensTopUnread() {
        val card = buildDayOverviewCard(input(firstEvent = null))
        assertEquals(SmartCardAction.LaunchApp("com.tencent.mm"), card!!.action)
        assertEquals("打开看看", card.actionLabel)
        assertEquals(TargetApp("com.tencent.mm", "微信"), card.targetApp)
        assertEquals("今天晴 18°～26°\n未读：微信 5 条、飞书 3 条", card.fact)
    }

    @Test
    fun noMeetingNoUnreadOpensCalendarApp() {
        val card = buildDayOverviewCard(input(firstEvent = null, unread = emptyList()))
        assertEquals(SmartCardAction.LaunchApp("com.android.calendar"), card!!.action)
        assertEquals("看日程", card.actionLabel)
        assertEquals("今天晴 18°～26°", card.fact)
    }

    @Test
    fun noActionAvailableBuildsNoCard() {
        // 有天气行但没有可点的动作（没会、没未读、也没日历应用）→ 不出没有下一步的卡。
        val card = buildDayOverviewCard(
            input(firstEvent = null, unread = emptyList(), calendarAppPackage = null, calendarAppTargetApp = null)
        )
        assertNull(card)
    }

    @Test
    fun allSourcesMissingBuildsNoCard() {
        val card = buildDayOverviewCard(
            input(
                weather = null, firstEvent = null, unread = emptyList(),
                calendarAppPackage = null, calendarAppTargetApp = null
            )
        )
        assertNull(card)
    }

    @Test
    fun unreadMoreThanFourShowsExtraCount() {
        // 仓库按条数倒序传进来；这里只显示前 4 个，余下记「等 2 个来源」。
        val unread = (6 downTo 1).map { UnreadSource("pkg$it", "应用$it", it) }
        val card = buildDayOverviewCard(input(firstEvent = null, unread = unread))
        assertTrue(card!!.fact.contains("未读：应用6 6 条、应用5 5 条、应用4 4 条、应用3 3 条 等 2 个来源"))
    }

    @Test
    fun dayScopedIdStableSameDayDifferentNextDay() {
        val today = buildDayOverviewCard(input())!!
        val todayAgain = buildDayOverviewCard(input())!!
        val tomorrow = buildDayOverviewCard(input(dayOffset = 1, dateKey = "20261116"))!!
        assertEquals(today.id, todayAgain.id)
        assertTrue(today.id != tomorrow.id)
    }

    @Test
    fun firstRelevantEventPicksEarliestUpcoming() {
        val now = ms(2026, 11, 15, 7, 0)
        val events = listOf(
            event(title = "晚会", hour = 15, min = 0, endHour = 16, endMin = 0, eventId = 3L),
            event(title = "早会", hour = 9, min = 30, endHour = 10, endMin = 30, eventId = 1L),
            event(title = "午会", hour = 12, min = 0, endHour = 13, endMin = 0, eventId = 2L)
        )
        assertEquals(1L, firstRelevantEvent(events, now)!!.eventId)
    }

    @Test
    fun firstRelevantEventSkipsEndedAndAllDay() {
        val now = ms(2026, 11, 15, 11, 0)
        val events = listOf(
            event(title = "已结束", hour = 9, min = 30, endHour = 10, endMin = 30, eventId = 1L),
            event(title = "全天", hour = 0, min = 0, endHour = 23, endMin = 59, eventId = 2L, allDay = true),
            event(title = "下午会", hour = 15, min = 0, endHour = 16, endMin = 0, eventId = 3L)
        )
        assertEquals(3L, firstRelevantEvent(events, now)!!.eventId)
    }

    @Test
    fun firstRelevantEventIncludesInProgress() {
        val now = ms(2026, 11, 15, 9, 45)
        val events = listOf(event(title = "进行中", hour = 9, min = 30, endHour = 10, endMin = 30, eventId = 1L))
        assertEquals(1L, firstRelevantEvent(events, now)!!.eventId)
    }

    @Test
    fun firstRelevantEventNullWhenAllEnded() {
        val now = ms(2026, 11, 15, 17, 0)
        val events = listOf(event(title = "已结束", hour = 9, min = 30, endHour = 10, endMin = 30, eventId = 1L))
        assertNull(firstRelevantEvent(events, now))
    }

    @Test
    fun dateKeyAndClockFormat() {
        assertEquals("20261115", dateKeyOf(ms(2026, 11, 15, 7, 5)))
        assertEquals("07:05", formatClock(ms(2026, 11, 15, 7, 5)))
        assertEquals("21:30", formatClock(ms(2026, 11, 15, 21, 30)))
    }
}
