package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 批B ②：日历查询下沉 CalendarRepository 后，今日窗口纯函数的语义回归锁
 * （查询本体依赖 ContentResolver 不可在 JVM 单测覆盖，窗口计算是可测点）。
 */
class CalendarRepositoryTest {

    @Test
    fun todayWindowStartsAtLocalMidnight() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 22, 15, 43, 12)
            set(Calendar.MILLISECOND, 345)
        }.timeInMillis
        val (start, _) = CalendarRepository.todayWindow(now)
        val s = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(2026, s.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, s.get(Calendar.MONTH))
        assertEquals(22, s.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, s.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, s.get(Calendar.MINUTE))
        assertEquals(0, s.get(Calendar.SECOND))
        assertEquals(0, s.get(Calendar.MILLISECOND))
    }

    @Test
    fun todayWindowEndsNextMidnightAndContainsNow() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 22, 23, 59, 59)
        }.timeInMillis
        val (start, end) = CalendarRepository.todayWindow(now)
        assertTrue(start <= now && now < end)
        val expectedEnd = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        assertEquals(expectedEnd, end)
    }
}
