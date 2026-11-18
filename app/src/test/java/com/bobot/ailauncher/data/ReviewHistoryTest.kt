package com.bobot.ailauncher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 每天复盘完整 session 历史单测（第四十七批，Bob 2026-11-18 21:34 纠正）：
 * 549 的滚动摘要是误读——Bob 说的缓存是 DeepSeek API 的 Context Caching
 * （提示词缓存），不是应用的缓存。本批把 session 改成完整会话历史
 * （只追加的日志：每天的新消息＋模型每轮的回复原文），应用侧不摘要、
 * 不丢、不截断；token 成本靠 API 前缀缓存控制。
 * 锁住的行为：系统提示词每天字节一致、历史原样在前、新消息只追加在末尾、
 * 前缀不变量（明天发的前缀就是今天发过的全文）、回复原文照存不脱敏、
 * 命中／未命中 token 解析、响应格式兼容 546 裸数组与 549 对象格式。
 * 带 Context 的 ReviewHistoryStore 不进 JVM 单测（与 549 的 store 同例）。
 */
class ReviewHistoryTest {

    private val utc = ZoneId.of("UTC")

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(utc).toInstant().toEpochMilli()

    private fun msg(
        title: String = "提醒",
        text: String = "内容",
        pkg: String = "com.example.app",
        app: String = "示例应用",
        time: Long = ms(2026, 11, 16, 20, 0),
        source: String = "notification"
    ) = DailyReviewMessage(pkg, app, title, text, time, source)

    private fun reviewResponseBody(content: String): String {
        return JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put("message", JSONObject().put("content", content))
                )
            ).toString()
    }

    private fun replyJsonFor(message: DailyReviewMessage): String {
        val hash = reviewMessageHash(message)
        return """{"suggestions":[{"hash":"$hash","push":true,"kind":"BILL","fact":"中信信用卡 11 月 24 日到期","actionLabel":"去还款","action":"open_app","deadlineMs":null,"sourceLabel":"中信银行"}]}"""
    }

    // ---------- 系统提示词：每天字节一致（缓存命中的前提） ----------

    @Test
    fun systemPrompt_isStatic_noSummaryNoTimestamp() {
        // 没有任何插值与动态内容：不提摘要、不含 sessionSummary、不含时间戳与年份
        assertFalse(REVIEW_SYSTEM_PROMPT.contains("摘要"))
        assertFalse(REVIEW_SYSTEM_PROMPT.contains("sessionSummary"))
        assertFalse(REVIEW_SYSTEM_PROMPT.contains("现在是"))
        assertFalse(REVIEW_SYSTEM_PROMPT.contains("2026"))
        assertFalse(REVIEW_SYSTEM_PROMPT.contains("\${"))
        // 讲清历史的用法：过去的复盘记录只供参考，只判今天的新消息
        assertTrue(REVIEW_SYSTEM_PROMPT.contains("过去的复盘记录"))
        assertTrue(REVIEW_SYSTEM_PROMPT.contains("只对今天的新消息"))
        assertTrue(REVIEW_SYSTEM_PROMPT.contains("以今天的新消息为准"))
        // 响应格式只要建议列表，不要摘要字段
        assertTrue(REVIEW_SYSTEM_PROMPT.contains("\"suggestions\""))
    }

    // ---------- 用户提示词：历史原样在前、新消息只追加在末尾 ----------

    @Test
    fun reviewUserPrompt_emptyHistory_startsWithTodayBlock() {
        val m = msg()
        val prompt = reviewUserPrompt(listOf(m), ms(2026, 11, 16, 21, 30), utc)
        // 历史为空（第一天）时直接就是今天这段，不加空的历史段
        assertTrue(prompt.startsWith("现在是：2026-11-16 21:30"))
        assertTrue(prompt.contains("今天的新消息（共 1 条）"))
        assertTrue(prompt.contains(reviewMessageHash(m)))
    }

    @Test
    fun reviewUserPrompt_historyVerbatimFirst() {
        val history = "现在是：2026-11-16 21:30（复盘时间）。今天的新消息（共 1 条）：\n[1] hash=oldhash 来源=平安\n旧消息\n模型判断：\n{\"suggestions\":[]}"
        val m = msg(title = "新提醒", text = "新内容", time = ms(2026, 11, 17, 20, 0))
        val prompt = reviewUserPrompt(listOf(m), ms(2026, 11, 17, 21, 30), utc, history)
        // 历史一字不改地放在最前面
        assertTrue(prompt.startsWith(history))
        // 今天的新消息在历史之后
        assertTrue(prompt.indexOf(reviewMessageHash(m)) > history.length)
        assertTrue(prompt.contains("2026-11-17 21:30"))
    }

    @Test
    fun reviewUserPrompt_prefixInvariant_twoRounds() {
        // 第一轮：历史为空
        val batch1 = listOf(msg(title = "账单", text = "中信信用卡 11 月 24 日到期"))
        val user1 = reviewUserPrompt(batch1, ms(2026, 11, 16, 21, 30), utc)
        // 第一轮的回复追加进历史
        val history2 = reviewHistoryAppend(user1, replyJsonFor(batch1[0]))
        // 第二轮：前缀就是第一轮发过的全文（缓存命中的关键不变量）
        val batch2 = listOf(msg(title = "新提醒", text = "体检预约明天上午", time = ms(2026, 11, 17, 20, 0)))
        val user2 = reviewUserPrompt(batch2, ms(2026, 11, 17, 21, 30), utc, history2)
        assertTrue(user2.startsWith(user1))
        assertTrue(user2.contains(reviewMessageHash(batch2[0])))
    }

    @Test
    fun reviewUserPrompt_prefixInvariant_threeRounds() {
        val batch1 = listOf(msg(title = "账单", text = "中信信用卡 11 月 24 日到期"))
        val user1 = reviewUserPrompt(batch1, ms(2026, 11, 16, 21, 30), utc)
        val history2 = reviewHistoryAppend(user1, replyJsonFor(batch1[0]))
        val batch2 = listOf(msg(title = "提醒二", text = "体检预约明天上午", time = ms(2026, 11, 17, 20, 0)))
        val user2 = reviewUserPrompt(batch2, ms(2026, 11, 17, 21, 30), utc, history2)
        val history3 = reviewHistoryAppend(user2, replyJsonFor(batch2[0]))
        val batch3 = listOf(msg(title = "提醒三", text = "航班明天起飞", time = ms(2026, 11, 18, 20, 0)))
        val user3 = reviewUserPrompt(batch3, ms(2026, 11, 18, 21, 30), utc, history3)
        // 第三轮的前缀是第二轮的全文，第二轮的前缀是第一轮的全文
        assertTrue(user3.startsWith(user2))
        assertTrue(user2.startsWith(user1))
        assertTrue(user3.contains(reviewMessageHash(batch3[0])))
    }

    @Test
    fun reviewUserPrompt_reviewTimeOnlyInTodayBlock() {
        // 历史里自带上一轮的“现在是”，今天这段的“现在是”只能出现一次、且在历史之后
        val history = "现在是：2026-11-16 21:30（复盘时间）。今天的新消息（共 1 条）：\n[1] hash=old\n模型判断：\n{\"suggestions\":[]}"
        val prompt = reviewUserPrompt(
            listOf(msg(time = ms(2026, 11, 17, 20, 0))),
            ms(2026, 11, 17, 21, 30), utc, history
        )
        val tail = prompt.removePrefix(history)
        assertTrue(tail.startsWith("\n\n现在是：2026-11-17 21:30"))
        assertEquals(1, tail.split("现在是：").size - 1)
    }

    @Test
    fun reviewUserPrompt_stillSanitizes() {
        val m = msg(
            title = "平安",
            text = "车牌粤B·F38583已承保，请联系 13812345678",
            pkg = "com.pingan.app",
            app = "平安"
        )
        val prompt = reviewUserPrompt(listOf(m), ms(2026, 11, 16, 21, 30), utc)
        assertTrue(prompt.contains(reviewMessageHash(m)))
        assertTrue(prompt.contains("[车牌]"))
        assertTrue(prompt.contains("[电话]"))
        assertFalse(prompt.contains("粤B"))
        assertFalse(prompt.contains("13812345678"))
        // 时间写可读日期，不写 epoch 毫秒
        assertTrue(prompt.contains("2026-11-16 21:30"))
        assertFalse(prompt.contains(m.timeMs.toString()))
    }

    // ---------- 历史追加：只追加、回复原文照存 ----------

    @Test
    fun reviewHistoryAppend_exactFormat() {
        val sent = "现在是：2026-11-16 21:30（复盘时间）。今天的新消息（共 1 条）：\n[1] hash=h"
        val reply = """{"suggestions":[]}"""
        assertEquals(sent + "\n模型判断：\n" + reply, reviewHistoryAppend(sent, reply))
    }

    @Test
    fun reviewHistoryAppend_emptyReplyUnchanged() {
        val sent = "现在是：2026-11-16 21:30（复盘时间）。今天的新消息（共 1 条）：\n[1] hash=h"
        assertEquals(sent, reviewHistoryAppend(sent, ""))
        assertEquals(sent, reviewHistoryAppend(sent, "   \n  "))
    }

    @Test
    fun reviewHistoryAppend_replyNotSanitized_hashIntact() {
        // hash 是 64 位十六进制，里面有一段 11 位数字串（形似电话）：
        // sanitizeForLlm 会把它当电话 mask 掉、hash 一坏模型就对不上消息了——
        // 所以回复原文照存、不再脱敏，这处单测把这个决定锁死。
        val hashWithPhoneLikeRun = "ab13812345678cd" + "0".repeat(49)
        assertEquals(64, hashWithPhoneLikeRun.length)
        val sent = "现在是：2026-11-16 21:30（复盘时间）。今天的新消息（共 1 条）：\n[1] hash=$hashWithPhoneLikeRun"
        val reply = """{"suggestions":[{"hash":"$hashWithPhoneLikeRun","push":true,"kind":"BILL","fact":"账单到期","actionLabel":"去还款"}]}"""
        val out = reviewHistoryAppend(sent, reply)
        assertTrue(out.contains(hashWithPhoneLikeRun))
        assertFalse(out.contains("[电话]"))
    }

    // ---------- 响应解析：兼容 546 裸数组与 549 对象格式，摘要键忽略 ----------

    @Test
    fun parseReviewResult_objectWithSummary_ignoresSummary() {
        // 549 风格的对象（带 sessionSummary）：建议照解析，摘要键直接忽略
        val content = """{"suggestions":[{"hash":"h1","push":true,"kind":"BILL","fact":"账单到期","actionLabel":"去还款","action":"open_app","deadlineMs":null,"sourceLabel":"银行"}],"sessionSummary":"【已办】车险已续"}"""
        val result = parseReviewResult(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(1, result.suggestions.size)
        assertEquals("h1", result.suggestions[0].hash)
        // 解析成功就有回复原文（供历史追加用）
        assertNotNull(result.replyText)
        assertTrue(result.replyText!!.contains("suggestions"))
    }

    @Test
    fun parseReviewResult_bareArrayCompatible() {
        val content = """[{"hash":"h1","push":true,"kind":"BILL","fact":"账单到期","actionLabel":"去还款","action":"open_app","deadlineMs":null,"sourceLabel":"银行"}]"""
        val result = parseReviewResult(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(1, result.suggestions.size)
        assertEquals("h1", result.suggestions[0].hash)
        assertNotNull(result.replyText)
    }

    @Test
    fun parseReviewResult_garbageHasNoReplyText() {
        // 乱码／空串 → 空建议＋空回复原文（那一轮不进历史）
        val bad = parseReviewResult("not json", ms(2026, 11, 16, 22, 0))
        assertEquals(0, bad.suggestions.size)
        assertNull(bad.replyText)
        val empty = parseReviewResult("", ms(2026, 11, 16, 22, 0))
        assertEquals(0, empty.suggestions.size)
        assertNull(empty.replyText)
    }

    @Test
    fun parseReviewResult_objectWithoutSuggestions() {
        // 有 suggestions 键才算成功；没有 → 空建议＋空回复原文
        val result = parseReviewResult(reviewResponseBody("""{"foo":1}"""), ms(2026, 11, 16, 22, 0))
        assertEquals(0, result.suggestions.size)
        assertNull(result.replyText)
    }

    // ---------- usage 解析：命中／未命中 token 数 ----------

    @Test
    fun parseLlmUsage_fullUsage() {
        val raw = JSONObject().put(
            "usage",
            JSONObject()
                .put("prompt_tokens", 12000)
                .put("completion_tokens", 300)
                .put("prompt_cache_hit_tokens", 10000)
                .put("prompt_cache_miss_tokens", 2000)
        ).toString()
        val u = parseLlmUsage(raw)
        assertNotNull(u)
        assertEquals(12000, u?.promptTokens)
        assertEquals(300, u?.completionTokens)
        assertEquals(10000, u?.cacheHitTokens)
        assertEquals(2000, u?.cacheMissTokens)
    }

    @Test
    fun parseLlmUsage_missingCacheFields() {
        // 只有 prompt／completion，没有缓存字段 → 命中／未命中为 null（不是 0）
        val raw = JSONObject().put(
            "usage",
            JSONObject().put("prompt_tokens", 500).put("completion_tokens", 50)
        ).toString()
        val u = parseLlmUsage(raw)
        assertNotNull(u)
        assertEquals(500, u?.promptTokens)
        assertEquals(50, u?.completionTokens)
        assertNull(u?.cacheHitTokens)
        assertNull(u?.cacheMissTokens)
    }

    @Test
    fun parseLlmUsage_noUsageOrGarbage() {
        assertNull(parseLlmUsage("{}"))
        assertNull(parseLlmUsage("not json"))
        assertNull(parseLlmUsage(""))
    }
}
