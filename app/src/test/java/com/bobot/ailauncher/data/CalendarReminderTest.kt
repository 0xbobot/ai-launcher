package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 预约卡“帮我建提醒”单测（第十一批，设计稿 §3）：纯函数、JVM 可跑。
 * 提醒时间：体检类按禁食截止点（前一晚 20:00）；没有禁食这类截止点的
 * 预约，按开始前 2 小时。提醒时间已过时夹到开始时（0 分钟）。
 */
class CalendarReminderTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // 锚点体检：2026-11-06 08:00–09:00（Bob 亲定的例子）
    private val checkupStart = ms(2026, 11, 6, 8, 0)

    @Test
    fun reminderTime_checkupIsPreviousNightEightPm() {
        assertEquals(ms(2026, 11, 5, 20, 0), reminderTimeMs(checkupStart, true))
    }

    @Test
    fun reminderTime_nonCheckupIsTwoHoursBeforeStart() {
        val start = ms(2026, 11, 6, 14, 30)
        assertEquals(ms(2026, 11, 6, 12, 30), reminderTimeMs(start, false))
    }

    @Test
    fun reminderMinutes_checkupIsTwelveHours() {
        // 08:00 开始、提醒在前一晚 20:00：提前 720 分钟。
        val now = ms(2026, 11, 5, 15, 0)
        assertEquals(720L, reminderMinutesBefore(checkupStart, true, now))
    }

    @Test
    fun reminderMinutes_nonCheckupIsTwoHours() {
        val start = ms(2026, 11, 6, 14, 30)
        val now = ms(2026, 11, 6, 10, 0)
        assertEquals(120L, reminderMinutesBefore(start, false, now))
    }

    @Test
    fun reminderMinutes_pastReminderTimeClampsToZero() {
        // 用户点得晚（已过前一晚 20:00）：开始时响一次，总比不提醒强。
        val now = ms(2026, 11, 6, 7, 0)
        assertEquals(0L, reminderMinutesBefore(checkupStart, true, now))
    }

    @Test
    fun reminderEventTitle_checkupWithRange() {
        assertEquals("体检预约 08:00–09:00", reminderEventTitle("体检预约", 480, 540))
    }

    @Test
    fun reminderEventTitle_noEndOnlyStart() {
        assertEquals("预约 14:30", reminderEventTitle("预约", 870, null))
    }
}
