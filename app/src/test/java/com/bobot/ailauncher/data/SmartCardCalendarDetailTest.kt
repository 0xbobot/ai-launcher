package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 本地日程详情展示行单测（第四十批，「看日程」退路）：calendarEventDetailLines
 * 是纯函数，单测锁死——时间行（进行中前缀＋相对日期＋时间段）、地点／
 * 说明／日历名有才写、没有不硬凑。Dialog 标题就是事件标题，这里不重复
 * 标题，只给时间起。
 */
class SmartCardCalendarDetailTest {

    /** 当天某时刻（本地时区）：hour:minute。 */
    private fun todayAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun event(
        begin: Long,
        end: Long,
        location: String = "",
        description: String = "",
        calendarName: String = ""
    ) = CalEvent(
        title = "测试会议2",
        begin = begin,
        end = end,
        location = location,
        description = description,
        allDay = false,
        eventId = 42L,
        calendarName = calendarName
    )

    @Test
    fun detailLines_allFields_inProgress() {
        val lines = calendarEventDetailLines(
            event(
                begin = todayAt(16, 30),
                end = todayAt(16, 40),
                location = "会议室 A",
                description = "周会，带上周报",
                calendarName = "豆包日历"
            ),
            todayAt(16, 35)
        )
        assertEquals(
            listOf(
                "进行中 · 今天 16:30–16:40",
                "地点：会议室 A",
                "周会，带上周报",
                "日历：豆包日历"
            ),
            lines
        )
    }

    @Test
    fun detailLines_beforeStart_noInProgressPrefix() {
        val lines = calendarEventDetailLines(
            event(begin = todayAt(16, 45), end = todayAt(16, 55)),
            todayAt(16, 0)
        )
        assertEquals(listOf("今天 16:45–16:55"), lines)
    }

    @Test
    fun detailLines_afterEnd_noInProgressPrefix() {
        val lines = calendarEventDetailLines(
            event(begin = todayAt(16, 30), end = todayAt(16, 40)),
            todayAt(17, 0)
        )
        assertEquals(listOf("今天 16:30–16:40"), lines)
    }

    @Test
    fun detailLines_missingOptionalFields_omitted() {
        // 地点／说明／日历名都没有：只剩时间行，不硬凑。
        val lines = calendarEventDetailLines(
            event(begin = todayAt(16, 30), end = todayAt(16, 40)),
            todayAt(16, 0)
        )
        assertEquals(listOf("今天 16:30–16:40"), lines)
    }

    @Test
    fun detailLines_blankFields_treatedAsMissing() {
        // 只有空白的地点／说明／日历名：trim 后视为空，不写。
        val lines = calendarEventDetailLines(
            event(
                begin = todayAt(16, 30),
                end = todayAt(16, 40),
                location = "   ",
                description = "  ",
                calendarName = ""
            ),
            todayAt(16, 0)
        )
        assertEquals(listOf("今天 16:30–16:40"), lines)
    }

    @Test
    fun detailLines_tomorrow_label() {
        val dayMs = 24L * 60 * 60 * 1000
        val lines = calendarEventDetailLines(
            event(
                begin = todayAt(9, 0) + dayMs,
                end = todayAt(10, 0) + dayMs,
                location = "公司",
                calendarName = "豆包日历"
            ),
            todayAt(16, 0)
        )
        assertEquals(
            listOf("明天 09:00–10:00", "地点：公司", "日历：豆包日历"),
            lines
        )
    }
}
