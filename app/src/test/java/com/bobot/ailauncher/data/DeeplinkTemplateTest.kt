package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deep link 模板填充单测（意图改进二批·兜底防线）：
 * 残留占位符永不产出可打开的链接——这是「假成功」在最底层的封口。
 */
class DeeplinkTemplateTest {

    private val navTemplate = "amap://route?sourceApplication=ai-launcher&dname={to}&dev=0&t=1"
    private val subwayTemplate = "amap://route?sourceApplication=ai-launcher&dlat={lat}&dlon={lon}&dev=0&t=1"

    @Test
    fun `fill replaces and url-encodes values`() {
        val uri = DeeplinkTemplate.fill(navTemplate, mapOf("to" to "机场"))
        assertEquals(
            "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1",
            uri
        )
    }

    @Test
    fun `missing param invalidates the whole link`() {
        assertNull(DeeplinkTemplate.fill(navTemplate, emptyMap()))
        assertNull(DeeplinkTemplate.fill(subwayTemplate, mapOf("lat" to "31.2")))
    }

    @Test
    fun `blank param invalidates the whole link`() {
        assertNull(DeeplinkTemplate.fill(navTemplate, mapOf("to" to "   ")))
    }

    @Test
    fun `template without placeholders passes through`() {
        assertEquals("amap://home", DeeplinkTemplate.fill("amap://home", emptyMap()))
    }

    @Test
    fun `placeholders are listed in order`() {
        assertEquals(listOf("lat", "lon"), DeeplinkTemplate.placeholders(subwayTemplate))
        assertEquals(listOf("to"), DeeplinkTemplate.placeholders(navTemplate))
    }
}
