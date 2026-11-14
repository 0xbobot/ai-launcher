package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 日历卡单测（第十二批）：会议判定（有会议链接即会议类）、出现窗口
 * （会议 30 分钟 / 非会议 2 小时）、事实文案、去重键（与短信预约卡
 * 同格式、短信在前优先）。仓库的建卡（calendarCard，需 Context 与
 * PackageManager）不进 JVM 单测，由 CI 构建＋真机验收。
 */
class CalendarCardTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun event(
        title: String,
        begin: Long,
        end: Long,
        location: String = "",
        description: String = "",
        eventId: Long = 7L
    ) = CalEvent(
        title = title,
        begin = begin,
        end = end,
        location = location,
        description = description,
        allDay = false,
        eventId = eventId
    )

    @Test
    fun isMeetingEvent_meetingLinkCountsAsMeeting() {
        // 地点里的腾讯会议链接
        val tencent = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            location = "https://meeting.tencent.com/dm/abc123"
        )
        assertTrue(isMeetingEvent(tencent))
        // 描述里的 Zoom 链接
        val zoom = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            description = "加入 Zoom 会议 https://zoom.us/j/123456789"
        )
        assertTrue(isMeetingEvent(zoom))
        // 标题里的飞书会议链接
        val feishu = event(
            "周会 https://meeting.feishu.cn/l/abc",
            ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0)
        )
        assertTrue(isMeetingEvent(feishu))
    }

    @Test
    fun isMeetingEvent_noLinkIsNotMeeting() {
        // 只有地点文字、没有链接：非会议日程，不另造“标题含会议词”判定
        val offline = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            location = "3楼会议室"
        )
        assertFalse(isMeetingEvent(offline))
        // 非白名单域名的链接也不算会议链接
        val other = event(
            "看文档", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            description = "https://example.com/doc/123"
        )
        assertFalse(isMeetingEvent(other))
    }

    @Test
    fun calendarCardWindow_meeting30minOtherwise2h() {
        val meeting = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            location = "https://meeting.tencent.com/dm/abc123"
        )
        assertEquals(
            ms(2026, 11, 6, 9, 30) to ms(2026, 11, 6, 11, 0),
            calendarCardWindow(meeting)
        )
        val errand = event(
            "去银行办业务", ms(2026, 11, 6, 14, 0), ms(2026, 11, 6, 15, 0),
            location = "招商银行深圳分行"
        )
        assertEquals(
            ms(2026, 11, 6, 12, 0) to ms(2026, 11, 6, 15, 0),
            calendarCardWindow(errand)
        )
    }

    @Test
    fun calendarCardFact_timeTitlePlace() {
        val now = ms(2026, 11, 5, 15, 0)
        val withPlace = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            location = "3楼会议室"
        )
        assertEquals("明天 10:00–11:00 项目周会，3楼会议室", calendarCardFact(withPlace, now))
        // 没有地点不硬凑
        val noPlace = event(
            "去银行办业务", ms(2026, 11, 6, 14, 0), ms(2026, 11, 6, 15, 0)
        )
        assertEquals("明天 14:00–15:00 去银行办业务", calendarCardFact(noPlace, now))
    }

    @Test
    fun calendarCardFact_inProgressPrefix() {
        // 第十九批：开始前无「进行中 · 」前缀，开始那一刻有，结束那一刻去掉。
        val meeting = event(
            "项目周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            location = "3楼会议室"
        )
        assertEquals(
            "今天 10:00–11:00 项目周会，3楼会议室",
            calendarCardFact(meeting, ms(2026, 11, 6, 9, 59))
        )
        assertEquals(
            "进行中 · 今天 10:00–11:00 项目周会，3楼会议室",
            calendarCardFact(meeting, ms(2026, 11, 6, 10, 0))
        )
        assertEquals(
            "今天 10:00–11:00 项目周会，3楼会议室",
            calendarCardFact(meeting, ms(2026, 11, 6, 11, 0))
        )
    }

    @Test
    fun calendarCardSourceKey_matchesSmsAppointmentKey() {
        // 去重键与短信预约卡同格式：同一件事短信在前、日历在后，
        // 去重后短信版优先（见 dedupe_sameSourceKeyKeepsFirst）。
        // 第二十五批：键里加结束分钟（08:00–09:00 → 480|540）。
        val dayStart = ms(2026, 11, 6)
        val place = "深圳第一健康鼎和分院四楼"
        val calEvent = event(
            "体检", ms(2026, 11, 6, 8, 0), ms(2026, 11, 6, 9, 0),
            location = place
        )
        assertEquals(
            "appt|$dayStart|480|540|${place.hashCode()}",
            calendarCardSourceKey(calEvent)
        )
        // 同一件事短信和日历各一张：去重只留第一张（短信那条）。
        val sms = SmartCard(
            id = "appt|$dayStart|480|540|${place.hashCode()}",
            kind = SmartCardKind.APPOINTMENT,
            sourceLabel = "短信",
            fact = "短信里有一条体检预约",
            prep = null,
            actionLabel = "帮我建提醒",
            sortMs = ms(2026, 11, 6, 8, 0),
            deadlineMs = ms(2026, 11, 6, 9, 0),
            action = SmartCardAction.SmsThread("10086"),
            sourceKey = "appt|$dayStart|480|540|${place.hashCode()}"
        )
        val cal = sms.copy(
            id = "cal|7|${ms(2026, 11, 6, 8, 0)}",
            kind = SmartCardKind.CALENDAR,
            sourceLabel = "日历"
        )
        assertEquals(
            listOf(sms.id),
            dedupeSmartCards(listOf(sms, cal)).map { it.id }
        )
    }

    @Test
    fun calendarCardSourceKey_sameStartDifferentEnd_differentKeys() {
        // 第二十五批（问题 2 根因）：同一天、同一开始（08:00）、都没地点，
        // 结束不同（09:00 vs 10:00）→ 去重键不同，两张都出，不再吞后一场。
        val dayStart = ms(2026, 11, 14)
        val first = event(
            "测试", ms(2026, 11, 14, 8, 0), ms(2026, 11, 14, 9, 0)
        )
        val second = event(
            "测试", ms(2026, 11, 14, 8, 0), ms(2026, 11, 14, 10, 0)
        )
        val firstKey = calendarCardSourceKey(first)
        val secondKey = calendarCardSourceKey(second)
        assertEquals("appt|$dayStart|480|540|0", firstKey)
        assertEquals("appt|$dayStart|480|600|0", secondKey)
        assertTrue(firstKey != secondKey)
        // 两张卡进叠层：去重后两张都在。
        val firstCard = cardWithKey("cal|1|${ms(2026, 11, 14, 8, 0)}", firstKey)
        val secondCard = cardWithKey("cal|2|${ms(2026, 11, 14, 8, 0)}", secondKey)
        assertEquals(
            listOf(firstCard.id, secondCard.id),
            dedupeSmartCards(listOf(firstCard, secondCard)).map { it.id }
        )
    }

    @Test
    fun appointmentCardSourceKey_smsWithoutEnd_matchesCalendarOneHour() {
        // 第二十五批：短信没写结束时间 → 按开始＋60（与截止点口径一致），
        // 与日历上 08:00–09:00 同一场对上同一把键，去重只留一张（短信优先）。
        val dayStart = ms(2026, 11, 6)
        val smsKey = appointmentCardSourceKey(dayStart, 480, 480 + 60, null)
        val calKey = calendarCardSourceKey(
            event("体检", ms(2026, 11, 6, 8, 0), ms(2026, 11, 6, 9, 0))
        )
        assertEquals(calKey, smsKey)
        val sms = cardWithKey("appt|$dayStart|480|540|0", smsKey)
        val cal = cardWithKey("cal|7|${ms(2026, 11, 6, 8, 0)}", calKey)
        assertEquals(
            listOf(sms.id),
            dedupeSmartCards(listOf(sms, cal)).map { it.id }
        )
    }

    @Test
    fun appointmentCardSourceKey_smsWithEnd_matchesCalendarSameEnd() {
        // 短信写了结束时间（08:00–10:00）→ 与日历同一场对上同一把键。
        val dayStart = ms(2026, 11, 14)
        val smsKey = appointmentCardSourceKey(dayStart, 480, 600, null)
        val calKey = calendarCardSourceKey(
            event("测试", ms(2026, 11, 14, 8, 0), ms(2026, 11, 14, 10, 0))
        )
        assertEquals(calKey, smsKey)
    }

    @Test
    fun appointmentCardSourceKey_smsEndDiffersFromCalendar_bothShown() {
        // 短信结束时间与日历同一场对不上（短信 09:00、日历 10:00）→
        // 两张并存，与地点写法不一致时同属已知局限，不硬凑。
        val dayStart = ms(2026, 11, 14)
        val smsKey = appointmentCardSourceKey(dayStart, 480, 540, null)
        val calKey = calendarCardSourceKey(
            event("测试", ms(2026, 11, 14, 8, 0), ms(2026, 11, 14, 10, 0))
        )
        assertTrue(smsKey != calKey)
        val sms = cardWithKey("appt|$dayStart|480|540|0", smsKey)
        val cal = cardWithKey("cal|7|${ms(2026, 11, 14, 8, 0)}", calKey)
        assertEquals(
            listOf(sms.id, cal.id),
            dedupeSmartCards(listOf(sms, cal)).map { it.id }
        )
    }

    /** 去重单测用的最小日历卡（只关心 id 与 sourceKey）。 */
    private fun cardWithKey(id: String, sourceKey: String) = SmartCard(
        id = id,
        kind = SmartCardKind.CALENDAR,
        sourceLabel = "日历",
        fact = "测试",
        prep = null,
        actionLabel = "看日程",
        sortMs = ms(2026, 11, 14, 8, 0),
        deadlineMs = ms(2026, 11, 14, 9, 0),
        action = SmartCardAction.CalendarEvent(7L, null),
        sourceKey = sourceKey
    )

    @Test
    fun extractMeetingUrl_trimsTrailingPunctuation() {
        val e = event(
            "周会", ms(2026, 11, 6, 10, 0), ms(2026, 11, 6, 11, 0),
            description = "链接：https://zoom.us/j/123456789。"
        )
        assertEquals("https://zoom.us/j/123456789", extractMeetingUrl(e))
    }
}
