package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.SmartCardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * v0.68.95（批二-A·F-05）：封面时间派生纯函数单测。
 * 此前日期/时段/模板都各自从系统时钟一次性捕获、互不跟随；参数化后
 * 统一由分钟 ticker 的 nowMs 派生，这里钉死文案口径不漂移。
 * 2026-11-21（Stageshow 桌面优化）：三模板轮换收敛到 GIANT_TIME 一个模板
 * （coverTemplateFor 与 CoverTemplate 已删）；「帮你盯着呢」已砍
 * （daypartForHour 6..11 返回空串）；isQuietHero 钉死安静版判定。
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
        // 2026-11-21（Bob 拍板，Stageshow 桌面优化）：「帮你盯着呢」已砍，
        // 6..11 时段返回空串，安静版与完整版都不留。
        assertEquals("", daypartForHour(6))
        assertEquals("", daypartForHour(11))
        assertEquals("适合出门走走", daypartForHour(12))
        assertEquals("适合出门走走", daypartForHour(17))
        assertEquals("晚上好好放松", daypartForHour(18))
        assertEquals("晚上好好放松", daypartForHour(22))
        assertEquals("好好休息", daypartForHour(23))
    }

    // ---------- isQuietHero（2026-11-21，Stageshow 桌面优化 3.1） ----------

    @Test
    fun `quiet hero only when top card is day overview`() {
        // 顶卡是一览卡时 hero 安静（不讲简报标题、不讲时段句），卡片当主角；
        // 其他卡或无卡时 hero 完整。
        assertTrue(isQuietHero(SmartCardKind.DAY_OVERVIEW))
        assertFalse(isQuietHero(SmartCardKind.CALENDAR))
        assertFalse(isQuietHero(SmartCardKind.BILL))
        assertFalse(isQuietHero(SmartCardKind.MISSED_CALL))
        assertFalse(isQuietHero(SmartCardKind.GUIDE))
        assertFalse(isQuietHero(null))
    }
}
