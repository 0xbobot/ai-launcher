package com.bobot.ailauncher.ui.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 并行线 D（胶囊有效查询派生，Bob 2026-10-23 拍板、13:03 更正版）：
 * 无已上屏汉字 → 有效查询 = 全文（含 composing 中的拼音字母）；
 * 有汉字上屏 → 有效查询 = 仅已上屏内容（去掉 composing 区间），
 * 汉字照常参与搜索、未上屏拼音不拼入。逐键派生、恒有查询、无停搜态。
 */
class LocalSearchGateTest {

    /** compStart/compEnd 同传 = 带 composing 区间的字段值；都不传 = 无 composing。 */
    private fun tfv(text: String, compStart: Int? = null, compEnd: Int? = null) =
        TextFieldValue(
            text = text,
            selection = TextRange(text.length),
            composition = if (compStart != null && compEnd != null) {
                TextRange(compStart, compEnd)
            } else {
                null
            }
        )

    @Test
    fun pureLatinComposing_usesFullText() {
        assertEquals("weixin", effectiveLocalQuery(tfv("weixin", 0, 6)))
        assertEquals("wei", effectiveLocalQuery(tfv("wei", 0, 3)))
        // 组合只占后半段（前面是已上屏拉丁字母）：无汉字上屏，仍用全文
        assertEquals("openwei", effectiveLocalQuery(tfv("openwei", 4, 7)))
    }

    @Test
    fun pureLatinNoComposition_usesFullText() {
        assertEquals("clo", effectiveLocalQuery(tfv("clo")))
        assertEquals("open wechat", effectiveLocalQuery(tfv("open wechat")))
    }

    @Test
    fun hanCommitted_composingLatinExcluded() {
        // 已上屏「微」+ 正敲 xin（未上屏）→ 只按「微」搜，不按「微xin」搜
        assertEquals("微", effectiveLocalQuery(tfv("微xin", 1, 4)))
    }

    @Test
    fun hanAndCommittedLatin_withComposing_usesCommittedOnly() {
        // 已上屏「微a」+ composing 的 xin → 已上屏全文「微a」
        assertEquals("微a", effectiveLocalQuery(tfv("微axin", 2, 5)))
        // 已上屏「去a」+ composing 的 bc → 「去a」
        assertEquals("去a", effectiveLocalQuery(tfv("去abc", 2, 4)))
    }

    @Test
    fun hanAndLatin_allCommitted_usesFullText() {
        // 全部已上屏（无 composing）：混合串即已上屏全文，照常作查询
        assertEquals("微xin", effectiveLocalQuery(tfv("微xin")))
        assertEquals("打车go", effectiveLocalQuery(tfv("打车go")))
    }

    @Test
    fun keystrokeSequence_derivesEachStep() {
        // 逐字上屏序列：wei（composing）→ 微（上屏）→ 微+xin（composing）→ 微信（上屏）
        assertEquals("wei", effectiveLocalQuery(tfv("wei", 0, 3)))
        assertEquals("微", effectiveLocalQuery(tfv("微")))
        assertEquals("微", effectiveLocalQuery(tfv("微xin", 1, 4)))
        assertEquals("微信", effectiveLocalQuery(tfv("微信")))
    }

    @Test
    fun hanOnlyNoComposition_searchesHanAsIs() {
        // 汉字本身照常参与搜索（不清空、不停搜）
        assertEquals("微信", effectiveLocalQuery(tfv("微信")))
        assertEquals("打车去机场", effectiveLocalQuery(tfv("打车去机场")))
    }

    @Test
    fun empty_returnsEmpty() {
        assertEquals("", effectiveLocalQuery(tfv("")))
    }

    @Test
    fun digitsAndSymbols_noHan_usesFullText() {
        assertEquals("v2", effectiveLocalQuery(tfv("v2")))
        assertEquals("we-chat!", effectiveLocalQuery(tfv("we-chat!")))
    }

    @Test
    fun extensionAHan_countsAsCommittedHan() {
        // 扩展 A 的字上屏同样算「有汉字」：查询取已上屏内容、排除 composing
        assertEquals("㐀", effectiveLocalQuery(tfv("㐀pin", 1, 4)))
    }

    @Test
    fun isHanChar_boundaries() {
        assertTrue(isHanChar('汉'))
        assertTrue(isHanChar('一')) // U+4E00 统一表意首字
        assertTrue(isHanChar('鿿')) // U+9FFF 统一表意末字
        assertTrue(isHanChar('㐀')) // U+3400 扩展 A 首字
        assertTrue(isHanChar('䶿')) // U+4DBF 扩展 A 末字
        assertFalse(isHanChar('A'))
        assertFalse(isHanChar('1'))
        assertFalse(isHanChar(' '))
        assertFalse(isHanChar('ア')) // 假名非表意字
    }
}
