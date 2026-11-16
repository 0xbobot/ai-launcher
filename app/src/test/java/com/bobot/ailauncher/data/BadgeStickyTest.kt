package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 角标粘性数纯逻辑单测（Bob 2026-11-08 08:02 反馈③：诊断数字还是不对，系统 15、飞书 125）。
 * 覆盖：存盘值解析/格式化。
 * SharedPreferences I/O（BadgeStickyStore）不进 JVM 单测，由 CI 构建 + 真机验收覆盖。
 */
class BadgeStickyTest {

    // ---------- 存盘值解析 / 格式化 ----------

    @Test
    fun `parseStickyValue - valid number comma timestamp parses`() {
        assertEquals(125 to 1791234567890L, parseStickyValue("125,1791234567890"))
    }

    @Test
    fun `parseStickyValue - malformed values return null`() {
        assertNull(parseStickyValue(""))
        assertNull(parseStickyValue("125"))
        assertNull(parseStickyValue("125,"))
        assertNull(parseStickyValue(",1791234567890"))
        assertNull(parseStickyValue("abc,1791234567890"))
        assertNull(parseStickyValue("125,abc"))
        assertNull(parseStickyValue("125,1791234567890,extra"))
    }

    @Test
    fun `parseStickyValue - zero or negative number returns null`() {
        assertNull(parseStickyValue("0,1791234567890"))
        assertNull(parseStickyValue("-3,1791234567890"))
    }

    @Test
    fun `formatStickyValue - round trips through parse`() {
        val formatted = formatStickyValue(125, 1791234567890L)
        assertEquals(125 to 1791234567890L, parseStickyValue(formatted))
    }

}
