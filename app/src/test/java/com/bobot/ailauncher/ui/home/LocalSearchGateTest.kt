package com.bobot.ailauncher.ui.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.bobot.ailauncher.data.AppSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 并行线 D（胶囊有效查询派生，Bob 2026-10-23 拍板、13:03 更正版）：
 * 无已上屏汉字 → 有效查询 = 全文（含 composing 中的拼音字母）；
 * 有汉字上屏 → 有效查询 = 仅已上屏内容（去掉 composing 区间），
 * 汉字照常参与搜索、未上屏拼音不拼入。逐键派生、恒有查询、无停搜态。
 *
 * 14:07 升级拼音流版另测 localSearchKeys / mergeLiteralFirst：
 * 字面键 = 上述派生；拼音流键 = 已上屏汉字逐字换算拼音 + composing
 * 字母接续，两路合并字面在前。
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

    // ---------- 拼音流版（14:07 拍板）：localSearchKeys ----------

    /**
     * 合成字典版的流换算注入：与生产同一本体（AppSearchIndex.pinyinOf），
     * 只换字典。字典里「行」只给主读音 xing——多音字查询侧取主读音的
     * 已知边界在 streamUsesPrimaryReadingOnly 一例锁死。
     */
    private val streamDict = mapOf(
        "微" to "wei", "信" to "xin", "去" to "qu",
        "银" to "yin", "行" to "xing",
    )
    private val fakeStream: (String) -> String = { s ->
        AppSearchIndex.pinyinOf(s, streamDict).first
    }

    @Test
    fun streamKeystrokeSequence_convergesWithoutFreezing() {
        // wei（composing）→ 微（上屏）→ 微+xin（composing）→ 微信（上屏）
        assertEquals(
            LocalSearchKeys("wei", "wei"),
            localSearchKeys(tfv("wei", 0, 3), fakeStream),
        )
        assertEquals(
            LocalSearchKeys("微", "wei"),
            localSearchKeys(tfv("微"), fakeStream),
        )
        // 核心一步：第二音节 composing 期间流键已是 weixin，不冻结在「微」
        assertEquals(
            LocalSearchKeys("微", "weixin"),
            localSearchKeys(tfv("微xin", 1, 4), fakeStream),
        )
        assertEquals(
            LocalSearchKeys("微信", "weixin"),
            localSearchKeys(tfv("微信"), fakeStream),
        )
    }

    @Test
    fun streamNoHanPhase_equalsLiteral() {
        // 无汉字阶段两键相等、与派生版严格等价（含 composing 中途态）
        assertEquals(
            LocalSearchKeys("openwei", "openwei"),
            localSearchKeys(tfv("openwei", 4, 7), fakeStream),
        )
        assertEquals(
            LocalSearchKeys("clo", "clo"),
            localSearchKeys(tfv("clo"), fakeStream),
        )
    }

    @Test
    fun streamCommittedLatinMixesIntoStream() {
        // 已上屏「微a」+ composing xin：字面键「微a」，流键 wei+a+xin
        assertEquals(
            LocalSearchKeys("微a", "weiaxin"),
            localSearchKeys(tfv("微axin", 2, 5), fakeStream),
        )
        // 全部已上屏的拉丁同样进流：微A（大写）→ 流内小写归一（与索引口径一致）
        assertEquals(
            LocalSearchKeys("微A", "weia"),
            localSearchKeys(tfv("微A"), fakeStream),
        )
    }

    @Test
    fun streamUsesPrimaryReadingOnly() {
        // 「银行」：查询侧流键取字典主读音 yinxing（已知边界）；hang 读音
        // 由索引侧关键词别名与字面键兜底，不在本函数职责内
        assertEquals(
            LocalSearchKeys("银行", "yinxing"),
            localSearchKeys(tfv("银行"), fakeStream),
        )
    }

    @Test
    fun streamMissingDictChar_fallsBackToRawChar() {
        // 字典缺字（龘 不在合成字典）：与索引侧 pinyinOf 同口径原样占位、
        // 流不断开；该字靠字面键兜底。第二音节 composing 照常接续
        assertEquals(
            LocalSearchKeys("龘", "龘xin"),
            localSearchKeys(tfv("龘xin", 1, 4), fakeStream),
        )
    }

    @Test
    fun streamEmptyAndSymbols() {
        assertEquals(
            LocalSearchKeys("", ""),
            localSearchKeys(tfv(""), fakeStream),
        )
        // 纯符号/数字无汉字：两键同取全文（符号在流内由 pinyinOf 略过是
        // 索引口径，但无汉字阶段根本不构造流键、保持等价）
        assertEquals(
            LocalSearchKeys("v2!", "v2!"),
            localSearchKeys(tfv("v2!"), fakeStream),
        )
        // 扩展 A 汉字上屏同样触发流键构造（缺字占位口径）
        assertEquals(
            LocalSearchKeys("㐀", "㐀pin"),
            localSearchKeys(tfv("㐀pin", 1, 4), fakeStream),
        )
    }

    // ---------- 拼音流版：mergeLiteralFirst 排序契约 ----------

    @Test
    fun merge_literalFirst_streamOnlyAppended_deduped() {
        val literal = listOf("微信", "微店")
        val stream = listOf("微博", "微信", "微视", "微店")
        assertEquals(
            listOf("微信", "微店", "微博", "微视"),
            mergeLiteralFirst(literal, stream) { it },
        )
    }

    @Test
    fun merge_emptySides() {
        assertEquals(listOf("a"), mergeLiteralFirst(listOf("a"), emptyList<String>()) { it })
        assertEquals(listOf("b"), mergeLiteralFirst(emptyList<String>(), listOf("b")) { it })
        assertEquals(
            emptyList<String>(),
            mergeLiteralFirst(emptyList<String>(), emptyList<String>()) { it },
        )
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
