package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 节点一起床窗单测（方案第一批）：中位数、夹取、冷启动默认、睡前窗。
 * （SharedPreferences 的记/读需要真机或 Robolectric，JVM 单测只锁
 * 纯计算；记录行为靠真机验收：连续几天亮屏后窗口跟着走。）
 */
class WakeWindowTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun at(ms: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = ms }

    @Test
    fun medianOddAndEven() {
        assertEquals(420, WakeWindowStore.medianMinutes(listOf(400, 420, 440)))
        assertEquals(420, WakeWindowStore.medianMinutes(listOf(400, 440)))
        // 偶数个取中间两个的平均（向下取整）。
        assertEquals(415, WakeWindowStore.medianMinutes(listOf(400, 430)))
        assertEquals(400, WakeWindowStore.medianMinutes(listOf(400)))
    }

    @Test
    fun coldStartUsesDefaultWindow() {
        // 没攒够 7 天（0 / 3 / 6 个样本）都用 6:30–8:30 默认值。
        assertEquals(390 to 510, WakeWindowStore.wakeWindowMinutes(emptyList()))
        assertEquals(390 to 510, WakeWindowStore.wakeWindowMinutes(listOf(420, 430, 410)))
        assertEquals(
            390 to 510,
            WakeWindowStore.wakeWindowMinutes(listOf(420, 430, 410, 425, 415, 435))
        )
    }

    @Test
    fun sevenSamplesMedianPlusMinusSixty() {
        // 7 天第一次亮屏都在 7:00 左右 → 窗口 6:00–8:00。
        val samples = listOf(415, 420, 425, 420, 418, 422, 419)
        assertEquals(360 to 480, WakeWindowStore.wakeWindowMinutes(samples))
    }

    @Test
    fun centerClampedToSixToNine() {
        // 中位数 5:00（太早）→ 中心夹到 6:00，窗口 5:00–7:00。
        val early = listOf(290, 295, 300, 300, 305, 310, 295)
        assertEquals(300 to 420, WakeWindowStore.wakeWindowMinutes(early))
        // 中位数 10:00（太晚）→ 中心夹到 9:00，窗口 8:00–10:00。
        val late = listOf(590, 595, 600, 600, 605, 610, 595)
        assertEquals(480 to 600, WakeWindowStore.wakeWindowMinutes(late))
    }

    @Test
    fun sleepWindowIsFixed2130ToMidnight() {
        val now = ms(2026, 11, 15, 12, 0)
        val (start, end) = WakeWindowStore.sleepWindowMs(now)
        val s = at(start)
        assertEquals(2026, s.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, s.get(Calendar.MONTH))
        assertEquals(15, s.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, s.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, s.get(Calendar.MINUTE))
        val e = at(end)
        assertEquals(16, e.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, e.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, e.get(Calendar.MINUTE))
        assertTrue(start <= now + 12 * 3_600_000L && now < end)
    }

    @Test
    fun wakeWindowMsMapsMinutesToToday() {
        val todayStart = ms(2026, 11, 15, 0, 0)
        val (start, end) = WakeWindowStore.wakeWindowMs(todayStart, 390 to 510)
        assertEquals(ms(2026, 11, 15, 6, 30), start)
        assertEquals(ms(2026, 11, 15, 8, 30), end)
    }

    @Test
    fun minutesOfDay() {
        assertEquals(425, WakeWindowStore.minutesOfDay(ms(2026, 11, 15, 7, 5)))
        assertEquals(1290, WakeWindowStore.minutesOfDay(ms(2026, 11, 15, 21, 30)))
        assertEquals(0, WakeWindowStore.minutesOfDay(ms(2026, 11, 15, 0, 0)))
    }
}
