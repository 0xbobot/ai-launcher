package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角标粘性数纯逻辑单测（Bob 2026-11-08 08:02 反馈③：诊断数字还是不对，系统 15、飞书 125）。
 * 覆盖：存/更新/启动清掉/number=0 清掉、与现有三路+探针取最大、空粘性无变化、
 * 旧粘性不覆盖更大的新数、存盘值解析/格式化。
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

    // ---------- 并进角标（取最大） ----------

    @Test
    fun `mergeStickyCounts - empty sticky leaves base unchanged`() {
        val base = mapOf("com.example.chat" to 3)
        assertEquals(base, mergeStickyCounts(base, emptyMap()))
    }

    @Test
    fun `mergeStickyCounts - sticky larger than base wins`() {
        val merged = mergeStickyCounts(
            base = mapOf("com.example.chat" to 3),
            sticky = mapOf("com.example.chat" to 125)
        )
        assertEquals(mapOf("com.example.chat" to 125), merged)
    }

    @Test
    fun `mergeStickyCounts - base larger than sticky keeps base`() {
        val merged = mergeStickyCounts(
            base = mapOf("com.example.chat" to 130),
            sticky = mapOf("com.example.chat" to 125)
        )
        assertEquals(mapOf("com.example.chat" to 130), merged)
    }

    @Test
    fun `mergeStickyCounts - sticky-only package appears in result`() {
        val merged = mergeStickyCounts(
            base = mapOf("com.example.chat" to 3),
            sticky = mapOf("com.ss.android.lark" to 125)
        )
        assertEquals(
            mapOf("com.example.chat" to 3, "com.ss.android.lark" to 125),
            merged
        )
    }

    @Test
    fun `mergeStickyCounts - zero sticky value is ignored`() {
        val merged = mergeStickyCounts(
            base = mapOf("com.example.chat" to 3),
            sticky = mapOf("com.example.chat" to 0)
        )
        assertEquals(mapOf("com.example.chat" to 3), merged)
    }

    // ---------- 按新通知更新 ----------

    @Test
    fun `updateStickyMap - number above zero is stored`() {
        val updated = updateStickyMap(emptyMap(), mapOf("com.ss.android.lark" to 125))
        assertEquals(mapOf("com.ss.android.lark" to 125), updated)
    }

    @Test
    fun `updateStickyMap - new number overwrites old even when smaller`() {
        // 应用自报的最新总数为准：用户在应用里读了一部分，新总数 120 < 旧 125，仍以 120 为准。
        val updated = updateStickyMap(
            mapOf("com.ss.android.lark" to 125),
            mapOf("com.ss.android.lark" to 120)
        )
        assertEquals(mapOf("com.ss.android.lark" to 120), updated)
    }

    @Test
    fun `updateStickyMap - number zero clears the package`() {
        val updated = updateStickyMap(
            mapOf("com.ss.android.lark" to 125, "com.example.chat" to 3),
            mapOf("com.ss.android.lark" to 0)
        )
        assertEquals(mapOf("com.example.chat" to 3), updated)
    }

    @Test
    fun `updateStickyMap - package without new notification is untouched`() {
        // 通知栏清掉不影响粘性：该包没有新通知时，粘性数原样保留。
        val current = mapOf("com.ss.android.lark" to 125)
        val updated = updateStickyMap(current, mapOf("com.example.chat" to 5))
        assertEquals(
            mapOf("com.ss.android.lark" to 125, "com.example.chat" to 5),
            updated
        )
    }

    @Test
    fun `updateStickyMap - empty numbers leave map unchanged`() {
        val current = mapOf("com.ss.android.lark" to 125)
        assertEquals(current, updateStickyMap(current, emptyMap()))
    }

    // ---------- 启动清掉 ----------

    @Test
    fun `clearStickyForPackage - removes only that package`() {
        val cleared = clearStickyForPackage(
            mapOf("com.ss.android.lark" to 125, "com.example.chat" to 3),
            "com.ss.android.lark"
        )
        assertEquals(mapOf("com.example.chat" to 3), cleared)
    }

    @Test
    fun `clearStickyForPackage - absent package leaves map unchanged`() {
        val current = mapOf("com.example.chat" to 3)
        assertEquals(current, clearStickyForPackage(current, "com.ss.android.lark"))
        assertTrue(clearStickyForPackage(emptyMap(), "com.ss.android.lark").isEmpty())
    }
}
