package com.bobot.ailauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * v0.68.95（批二-A·F-05）：封面时间派生纯函数单测。
 * 此前日期/时段/模板都各自从系统时钟一次性捕获、互不跟随；参数化后
 * 统一由分钟 ticker 的 nowMs 派生，这里钉死文案口径不漂移。
 */
class CoverTimeTextTest {

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ---------- currentDateText ----------

    @Test
    fun `date text formats month day and weekday`() {
        // 2026-10-22 是星期四（date -d 实证）
        assertEquals("10月22日 星期四", currentDateText(millisAt(2026, Calendar.OCTOBER, 22, 12)))
        // 2026-01-01 是星期四
        assertEquals("1月1日 星期四", currentDateText(millisAt(2026, Calendar.JANUARY, 1, 12)))
    }

    // ---------- daypartForHour ----------

    @Test
    fun `daypart boundaries match legacy wording`() {
        assertEquals("好好休息", daypartForHour(0))
        assertEquals("好好休息", daypartForHour(5))
        assertEquals("七仔帮你盯着呢", daypartForHour(6))
        assertEquals("七仔帮你盯着呢", daypartForHour(11))
        assertEquals("适合出门走走", daypartForHour(12))
        assertEquals("适合出门走走", daypartForHour(17))
        assertEquals("晚上好好放松", daypartForHour(18))
        assertEquals("晚上好好放松", daypartForHour(22))
        assertEquals("好好休息", daypartForHour(23))
    }

    // ---------- coverTemplateFor ----------

    @Test
    fun `template rotates by day of year`() {
        // DAY_OF_YEAR % 3：1→HEADLINE（枚举序 1）、2→MINIMAL（2）、3→GIANT_TIME（0）
        assertEquals(CoverTemplate.HEADLINE, coverTemplateFor(millisAt(2026, Calendar.JANUARY, 1, 12)))
        assertEquals(CoverTemplate.MINIMAL, coverTemplateFor(millisAt(2026, Calendar.JANUARY, 2, 12)))
        assertEquals(CoverTemplate.GIANT_TIME, coverTemplateFor(millisAt(2026, Calendar.JANUARY, 3, 12)))
        // 跨天必翻篇：第 4 天回到 HEADLINE
        assertEquals(CoverTemplate.HEADLINE, coverTemplateFor(millisAt(2026, Calendar.JANUARY, 4, 12)))
    }
}
