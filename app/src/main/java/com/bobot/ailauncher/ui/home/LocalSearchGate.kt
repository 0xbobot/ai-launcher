package com.bobot.ailauncher.ui.home

import androidx.compose.ui.text.input.TextFieldValue

/**
 * 胶囊本地搜索的有效查询派生（并行线 D，Bob 2026-10-23 拍板、13:03 更正版）：
 * 「还没有中文字上屏时，按用户输入字母做关键字搜索；一旦有汉字上屏，
 * 只按已上屏内容搜，还在敲的拼音字母不拼进查询。」
 *
 * 派生规则（逐键计算、纯函数、无状态）：
 * - committedText = 全文去掉 composing 区间（未上屏的那截拼音字母）；
 * - committedText 含汉字 → 有效查询 = committedText：汉字照常参与
 *   搜索，composing 中的字母不拼入（已上屏「微」+ 正敲 xin → 只按
 *   「微」搜；「信」上屏后按「微信」搜）；
 * - 否则 → 有效查询 = 全文（含 composing 中的拼音字母，按字母搜）。
 *
 * 搜索全程保持响应：本函数恒返回查询串（可为空串），不存在「停响应 /
 * 清空」状态。汉字判定（Char 级）：CJK 统一表意 U+4E00..U+9FFF +
 * 扩展 A U+3400..U+4DBF；扩展 B 以上属增补平面（代理对），Char 级
 * 覆盖不了，口径在此明示。
 *
 * 本派生只供首页胶囊的本地结果驱动链。应用中心过滤有其自有落定链
 * （组合暂停口径），不共用本函数、不受影响。
 */
fun effectiveLocalQuery(value: TextFieldValue): String {
    val text = value.text
    val comp = value.composition ?: return text
    val start = comp.start.coerceIn(0, text.length)
    val end = comp.end.coerceIn(start, text.length)
    val committed = text.removeRange(start, end)
    return if (committed.any(::isHanChar)) committed else text
}

/** Char 是否落在 CJK 统一表意（含扩展 A）区段。 */
fun isHanChar(c: Char): Boolean =
    c in '一'..'鿿' || c in '㐀'..'䶿'
