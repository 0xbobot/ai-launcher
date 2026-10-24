package com.bobot.ailauncher.ui.home

import androidx.compose.ui.text.input.TextFieldValue

/**
 * 胶囊本地搜索的查询派生（并行线 D；Bob 2026-10-23 12:55 定、13:03 更正
 * 派生版、14:07 拍板升级为拼音流版）：
 *
 * 派生版口径（13:03）解决的是「微xin」式废串：没有汉字上屏时按全文
 * （含 composing 中的拼音字母）搜；有汉字上屏后只按已上屏内容搜、
 * 未上屏拼音不拼入——但第二音节 composing 期间结果冻结在一上屏状态。
 *
 * 拼音流版（本版）把搜索键统一成一条连续拼音流，上屏动作对搜索无感：
 * - **字面键** = 派生版的有效查询（见 [effectiveLocalQuery]），按名称
 *   字面匹配；
 * - **拼音流键** = 已上屏内容经 [streamOf] 换算成拼音全拼流（汉字逐字
 *   取字典主读音、拉丁/数字小写原样、缺字原样占位——与索引侧
 *   AppSearchIndex.pinyinOf 完全同口径）+ 未上屏 composing 字母原样
 *   接续。例：已上屏「微」+ 正敲 xin → 流键 = weixin，全程活搜向
 *   「微信」收敛，不再冻结。
 * - 两键各走一遍 AppSearchIndex.search，合并时**字面命中在前、纯流
 *   命中在后**（[mergeLiteralFirst]，按包名去重）。
 *
 * 已知边界（Bob 已知情）：多音字查询侧只取字典主读音，个别读音
 * （如「行」hang）流键可能漏配，字面键负责兜住已上屏部分。
 * 无汉字上屏时两键相等（流键 := 字面键），与派生版行为严格等价、
 * 只搜一遍。派生逐键计算、纯函数、无状态；搜索全程保持响应，
 * 不存在「停响应 / 清空」状态。
 *
 * 本派生只供首页胶囊的本地结果驱动链。应用中心过滤有其自有落定链
 * （组合暂停口径），不共用本函数、不受影响。
 */

/** 本地搜索的双键：字面键 + 拼音流键（相等时只需搜一遍）。 */
data class LocalSearchKeys(
    val literal: String,
    val phonetic: String,
)

/** 字段值的已上屏段与 composing 段（无 composing 时后者为空串）。 */
private fun splitCommitted(value: TextFieldValue): Pair<String, String> {
    val text = value.text
    val comp = value.composition ?: return text to ""
    val start = comp.start.coerceIn(0, text.length)
    val end = comp.end.coerceIn(start, text.length)
    return text.removeRange(start, end) to text.substring(start, end)
}

/**
 * 派生版有效查询（字面键的派生本体，口径不变）：
 * committedText 含汉字 → 已上屏内容；否则 → 全文（含 composing 字母）。
 */
fun effectiveLocalQuery(value: TextFieldValue): String {
    val (committed, _) = splitCommitted(value)
    return if (committed.any(::isHanChar)) committed else value.text
}

/**
 * 拼音流版双键派生（纯函数）：
 * - 已上屏内容不含汉字 → 两键都取全文字面，与派生版严格等价；
 * - 含汉字 → 字面键 = 已上屏内容；流键 = streamOf(已上屏内容) +
 *   composing 段原样接续（composing 是尾段拼音字母）。
 * [streamOf] 由调用方注入（生产 = AppSearchIndex.pinyinStreamOf，
 * 与索引构建共用字典与换算口径；测试注入合成字典版）。
 */
fun localSearchKeys(
    value: TextFieldValue,
    streamOf: (String) -> String,
): LocalSearchKeys {
    val (committed, composing) = splitCommitted(value)
    if (!committed.any(::isHanChar)) {
        return LocalSearchKeys(literal = value.text, phonetic = value.text)
    }
    return LocalSearchKeys(
        literal = committed,
        phonetic = streamOf(committed) + composing,
    )
}

/**
 * 双路结果合并（纯函数）：字面命中整体在前（保持其内排序），
 * 流命中只补字面未覆盖的（按 [keyOf] 去重、保持流内排序）。
 */
fun <T> mergeLiteralFirst(
    literal: List<T>,
    phonetic: List<T>,
    keyOf: (T) -> String,
): List<T> {
    if (phonetic.isEmpty()) return literal
    if (literal.isEmpty()) return phonetic
    val seen = literal.mapTo(HashSet(), keyOf)
    return literal + phonetic.filter { seen.add(keyOf(it)) }
}

/**
 * Char 是否落在 CJK 统一表意（含扩展 A）区段。扩展 B 以上属增补平面
 * （代理对），Char 级判定覆盖不了，口径在此明示（与派生版一致）。
 */
fun isHanChar(c: Char): Boolean =
    c in '一'..'鿿' || c in '㐀'..'䶿'
