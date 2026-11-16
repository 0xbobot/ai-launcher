package com.bobot.ailauncher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 滚动 session 摘要单测（第四十六批，Bob 2026-11-16 19:52 定）：
 * 每天的复盘保持和之前的同一个 session，同时不占用太多 token——
 * 本地只存一份紧凑的 session 摘要（≤1000 字），每天只发「摘要＋当天
 * 新增的脱敏消息」，不发全文历史；摘要超限本地压缩，不另调模型。
 * 网络调用本身（runDailyReview）不进 JVM 单测，由 CI 构建＋真机验收。
 */
class ReviewSessionTest {

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

    // ---------- 摘要上限常量 ----------

    @Test
    fun sessionSummary_maxCharsIs1000() {
        assertEquals(1000, REVIEW_SESSION_MAX_CHARS)
        assertTrue(REVIEW_SESSION_TARGET_CHARS < REVIEW_SESSION_MAX_CHARS)
    }

    // ---------- 提示词：有摘要先给摘要、再给新消息 ----------

    @Test
    fun sessionPrompt_includesSummaryBeforeMessages() {
        val prompt = reviewUserPrompt(
            listOf(msg(text = "信用卡 11 月 24 日到期")),
            ms(2026, 11, 16, 21, 30),
            utc,
            "【已办】车险已续\n【待办】周杰伦抢票 11 月 16 日 13:00"
        )
        assertTrue(prompt.contains("过去的复盘摘要"))
        assertTrue(prompt.contains("【已办】车险已续"))
        assertTrue(prompt.contains("【待办】周杰伦抢票 11 月 16 日 13:00"))
        // 摘要在新消息之前
        assertTrue(prompt.indexOf("【已办】车险已续") < prompt.indexOf("消息如下"))
        // 新消息仍在
        assertTrue(prompt.contains("信用卡 11 月 24 日到期"))
    }

    @Test
    fun sessionPrompt_noSummarySectionWhenEmpty() {
        val prompt = reviewUserPrompt(
            listOf(msg(text = "信用卡 11 月 24 日到期")),
            ms(2026, 11, 16, 21, 30),
            utc
        )
        assertFalse(prompt.contains("过去的复盘摘要"))
        assertTrue(prompt.contains("信用卡 11 月 24 日到期"))
    }

    @Test
    fun sessionPrompt_summarySanitizedAtEmbedTime() {
        val prompt = reviewUserPrompt(
            listOf(msg(text = "信用卡到期")),
            ms(2026, 11, 16, 21, 30),
            utc,
            "车牌粤B·F38583已承保，联系 13812345678"
        )
        assertTrue(prompt.contains("[车牌]"))
        assertTrue(prompt.contains("[电话]"))
        assertFalse(prompt.contains("粤B"))
        assertFalse(prompt.contains("13812345678"))
    }

    // ---------- 摘要压缩：本地压缩，不另调模型 ----------

    private fun paddedLine(prefix: String, index: Int): String =
        ("${prefix}第%02d件事".format(index)).padEnd(60, 'x')

    @Test
    fun compressSessionSummary_underCapUnchanged() {
        val summary = "【已办】车险已续\n【待办】周杰伦抢票 11 月 16 日 13:00\n【日期】自驾出发 11 月 17 日 08:00"
        assertEquals(summary, compressSessionSummary(summary))
        assertEquals("", compressSessionSummary(""))
    }

    @Test
    fun compressSessionSummary_dropsDoneLinesFirst() {
        // 待办部分本身 975 字（16 行），加 4 行【已办】后超限；
        // 压缩时 4 行【已办】全丢，16 行【待办】全留（975 ≤ 1000）
        val done = (0 until 4).joinToString("\n") { paddedLine("【已办】", it) }
        val todo = (0 until 16).joinToString("\n") { paddedLine("【待办】", it) }
        val summary = "$done\n$todo"
        assertTrue(summary.length > REVIEW_SESSION_MAX_CHARS)
        val out = compressSessionSummary(summary)
        assertTrue(out.length <= REVIEW_SESSION_MAX_CHARS)
        // 【已办】行先丢光，【待办】行全留
        assertFalse(out.contains("【已办】"))
        assertTrue(out.contains("【待办】"))
        assertTrue(out.contains("第00件"))
        assertTrue(out.contains("第15件"))
    }

    @Test
    fun compressSessionSummary_dropsOldestWhenStillOver() {
        val summary = (0 until 20).joinToString("\n") { paddedLine("【待办】", it) }
        assertTrue(summary.length > REVIEW_SESSION_MAX_CHARS)
        val out = compressSessionSummary(summary)
        assertTrue(out.length <= REVIEW_SESSION_MAX_CHARS)
        // 只剩【待办】时，从最旧的行开始丢（最近的结论保留）
        assertFalse(out.contains("第00件"))
        assertTrue(out.contains("第19件"))
    }

    @Test
    fun compressSessionSummary_singleLongLineTruncated() {
        val out = compressSessionSummary("x".repeat(1100))
        assertEquals(REVIEW_SESSION_MAX_CHARS, out.length)
        assertEquals("x".repeat(1000), compressSessionSummary("x".repeat(1000)))
    }

    // ---------- 摘要入库前再脱敏 ----------

    @Test
    fun sanitizeSessionSummary_masksSensitiveKeepsMarkers() {
        val out = sanitizeSessionSummary(
            "【已办】车险已续，车牌粤B·F38583；联系 13812345678；应还 2116.93 元\n【日期】抢票 11 月 16 日 13:00"
        )
        assertTrue(out.contains("[车牌]"))
        assertTrue(out.contains("[电话]"))
        assertTrue(out.contains("[金额]"))
        assertFalse(out.contains("粤B"))
        assertFalse(out.contains("13812345678"))
        assertFalse(out.contains("2116.93"))
        // 行标记与日期不受影响
        assertTrue(out.contains("【已办】"))
        assertTrue(out.contains("【日期】"))
        assertTrue(out.contains("11 月 16 日"))
    }

    // ---------- 响应解析：对象格式＋兼容裸数组 ----------

    @Test
    fun parseReviewResult_objectFormat() {
        val content = JSONObject()
            .put(
                "suggestions",
                JSONArray().put(
                    JSONObject()
                        .put("hash", "h1")
                        .put("push", true)
                        .put("kind", "BILL")
                        .put("fact", "中信信用卡 11 月 24 日到期")
                        .put("actionLabel", "去还款")
                        .put("action", "open_app")
                )
            )
            .put("sessionSummary", "【已办】车险已续\n【待办】周杰伦抢票 11 月 16 日 13:00")
            .toString()
        val result = parseReviewResult(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(1, result.suggestions.size)
        assertEquals("h1", result.suggestions[0].hash)
        assertEquals("【已办】车险已续\n【待办】周杰伦抢票 11 月 16 日 13:00", result.sessionSummary)
    }

    @Test
    fun parseReviewResult_legacyArrayFormatHasNoSummary() {
        val content = """[
            {"hash":"h1","push":true,"kind":"BILL","fact":"账单到期","actionLabel":"去还款"}
        ]"""
        val result = parseReviewResult(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(1, result.suggestions.size)
        assertNull(result.sessionSummary)
    }

    @Test
    fun parseReviewResult_blankOrMissingSummaryIsNull() {
        val blank = JSONObject()
            .put("suggestions", JSONArray())
            .put("sessionSummary", "   ")
            .toString()
        assertNull(parseReviewResult(reviewResponseBody(blank), ms(2026, 11, 16, 22, 0)).sessionSummary)
        val missing = JSONObject().put("suggestions", JSONArray()).toString()
        assertNull(parseReviewResult(reviewResponseBody(missing), ms(2026, 11, 16, 22, 0)).sessionSummary)
    }

    @Test
    fun parseReviewResult_badJsonEmpty() {
        val result = parseReviewResult("not json", ms(2026, 11, 16, 22, 0))
        assertEquals(0, result.suggestions.size)
        assertNull(result.sessionSummary)
        val empty = parseReviewResult("", ms(2026, 11, 16, 22, 0))
        assertEquals(0, empty.suggestions.size)
        assertNull(empty.sessionSummary)
    }

    @Test
    fun parseReviewResponse_objectFormatStillWorks() {
        val content = JSONObject()
            .put(
                "suggestions",
                JSONArray().put(
                    JSONObject()
                        .put("hash", "h1")
                        .put("push", true)
                        .put("kind", "BILL")
                        .put("fact", "账单到期")
                        .put("actionLabel", "去还款")
                )
            )
            .put("sessionSummary", "【已办】车险已续")
            .toString()
        val out = parseReviewResponse(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(1, out.size)
        assertEquals("h1", out[0].hash)
    }
}
