package com.bobot.ailauncher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 每天一次整体复盘单测（第四十四批，Bob 2026-11-16 定稿）：
 * 脱敏新增字段（车牌、住址）、按内容哈希缓存（判过的不再送）、
 * 每天只送新增消息（触发/到期/补跑）、复盘结果转卡片、无 Key 不跑。
 * 网络调用本身（runDailyReview）不进 JVM 单测，由 CI 构建＋真机验收。
 */
class DailyReviewTest {

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

    private fun suggestion(
        hash: String = "abc123",
        push: Boolean = true,
        kind: String = "BILL",
        fact: String = "中信信用卡 11 月 24 日到期",
        actionLabel: String = "去还款",
        action: String = "open_app",
        actionParam: String = "com.example.bank",
        deadlineMs: Long? = null,
        sourceLabel: String = "中信银行",
        sourcePackage: String = "com.example.bank"
    ) = ReviewSuggestion(
        hash, push, kind, fact, actionLabel, action, actionParam,
        deadlineMs, sourceLabel, sourcePackage, savedMs = ms(2026, 11, 16, 21, 30)
    )

    // ---------- 脱敏新增字段：车牌号 ----------

    @Test
    fun sanitize_removesPlateNumber() {
        assertEquals("车牌[车牌]已承保", sanitizeForLlm("车牌粤B·F38583已承保"))
        assertEquals("车牌[车牌]已承保", sanitizeForLlm("车牌粤BF38583已承保"))
        // 新能源 8 位
        assertEquals("车牌[车牌]已承保", sanitizeForLlm("车牌粤BD12345已承保"))
        // 没有省简称的不是车牌，不动
        assertEquals("编号 B38583", sanitizeForLlm("编号 B38583"))
    }

    // ---------- 脱敏新增字段：家庭住址 ----------

    @Test
    fun sanitize_removesHomeAddress() {
        // 门牌号及之后的栋/单元/室整段 mask，路名与区名保留
        assertEquals(
            "住址：光明区石围[家庭住址]",
            sanitizeForLlm("住址：光明区石围路123号3栋2单元501室")
        )
        // 只有栋/单元/室也 mask
        assertEquals("住址：[家庭住址]", sanitizeForLlm("住址：3栋2单元501室"))
        assertEquals("住址：[家庭住址]", sanitizeForLlm("住址：A栋1201室"))
        // 场馆名（无路＋门牌）原样保留
        assertEquals(
            "体检预约：深圳第一健康鼎和分院四楼",
            sanitizeForLlm("体检预约：深圳第一健康鼎和分院四楼")
        )
    }

    // ---------- 内容哈希：判过的不再送 ----------

    @Test
    fun reviewHash_sameContentSameHash() {
        val a = msg(text = "请联系 13812345678 确认")
        val b = msg(text = "请联系 13987654321 确认")
        // 只差手机号，脱敏后内容相同 → 同一哈希（判过一次就不再送）
        assertEquals(reviewMessageHash(a), reviewMessageHash(b))
    }

    @Test
    fun reviewHash_differentContentDifferentHash() {
        val a = msg(text = "信用卡 11 月 24 日到期")
        val b = msg(text = "信用卡 11 月 25 日到期")
        assertNotEquals(reviewMessageHash(a), reviewMessageHash(b))
        // 同一内容两次算，哈希稳定
        assertEquals(reviewMessageHash(a), reviewMessageHash(msg(text = "信用卡 11 月 24 日到期")))
    }

    // ---------- 触发时间：每天 21:30 ----------

    @Test
    fun reviewTrigger_today2130() {
        // 上午 10 点看，当天触发点是今天 21:30
        assertEquals(ms(2026, 11, 16, 21, 30), reviewTriggerTodayMs(ms(2026, 11, 16, 10, 0), utc))
        // 晚上 22 点看，当天触发点仍是今天 21:30（已过）
        assertEquals(ms(2026, 11, 16, 21, 30), reviewTriggerTodayMs(ms(2026, 11, 16, 22, 0), utc))
    }

    @Test
    fun reviewLatestTrigger_beforeAndAfter2130() {
        // 上午 10 点：最近一次触发点是昨天 21:30（补跑就靠它）
        assertEquals(
            ms(2026, 11, 15, 21, 30),
            reviewLatestTriggerMs(ms(2026, 11, 16, 10, 0), utc)
        )
        // 晚上 22 点：最近一次触发点是今天 21:30
        assertEquals(
            ms(2026, 11, 16, 21, 30),
            reviewLatestTriggerMs(ms(2026, 11, 16, 22, 0), utc)
        )
    }

    @Test
    fun reviewDelayToNextTrigger() {
        // 上午 10 点 → 到今天 21:30 是 11.5 小时
        assertEquals(
            11L * 60 * 60 * 1000 + 30L * 60 * 1000,
            reviewDelayToNextTriggerMs(ms(2026, 11, 16, 10, 0), utc)
        )
        // 晚上 22 点 → 到明天 21:30 是 23.5 小时
        assertEquals(
            23L * 60 * 60 * 1000 + 30L * 60 * 1000,
            reviewDelayToNextTriggerMs(ms(2026, 11, 16, 22, 0), utc)
        )
    }

    // ---------- 每天只送新增：到期与补跑 ----------

    @Test
    fun isReviewDue_normalAndCatchup() {
        // 正常：21:30 一到，上次是昨晚 → 到期
        assertTrue(isReviewDue(ms(2026, 11, 15, 21, 35), ms(2026, 11, 16, 22, 0)))
        // 已跑过今晚 → 不到期
        assertFalse(isReviewDue(ms(2026, 11, 16, 21, 35), ms(2026, 11, 16, 22, 0)))
        // 补跑：早上 7 点醒来，上次是前天 → 到期（昨晚 21:30 关机错过了）
        assertTrue(isReviewDue(ms(2026, 11, 15, 21, 35), ms(2026, 11, 17, 7, 0)))
        // 昨晚已跑过，早上醒来 → 不到期
        assertFalse(isReviewDue(ms(2026, 11, 16, 21, 35), ms(2026, 11, 17, 7, 0)))
        // 从没跑过 → 到期
        assertTrue(isReviewDue(0L, ms(2026, 11, 16, 22, 0)))
    }

    @Test
    fun shouldRunReview_noKeyDoesNotRun() {
        val dueLast = ms(2026, 11, 15, 21, 35)
        val now = ms(2026, 11, 16, 22, 0)
        // 没配 Key，到期也不跑
        assertFalse(shouldRunReview(false, dueLast, now))
        // 配了 Key，到期才跑
        assertTrue(shouldRunReview(true, dueLast, now))
        // 配了 Key，没到期不跑
        assertFalse(shouldRunReview(true, ms(2026, 11, 16, 21, 35), now))
    }

    // ---------- 复盘结果解析 ----------

    @Test
    fun parseReviewResponse_valid() {
        val content = """[
            {"hash":"h1","push":true,"kind":"BILL","fact":"中信信用卡 11 月 24 日到期","actionLabel":"去还款","action":"open_app","deadlineMs":1794000000000,"sourceLabel":"中信银行"},
            {"hash":"h2","push":false,"kind":"NOTIFICATION","fact":"保单已承保","actionLabel":"查看","action":"none","deadlineMs":null,"sourceLabel":"平安"}
        ]"""
        val out = parseReviewResponse(reviewResponseBody(content), ms(2026, 11, 16, 22, 0))
        assertEquals(2, out.size)
        assertTrue(out[0].push)
        assertEquals("BILL", out[0].kind)
        assertEquals("中信信用卡 11 月 24 日到期", out[0].fact)
        assertEquals(1794000000000L, out[0].deadlineMs)
        assertFalse(out[1].push)
        assertEquals("平安", out[1].sourceLabel)
        assertEquals(null, out[1].deadlineMs)
    }

    @Test
    fun parseReviewResponse_badJsonAndMissingFact() {
        // 乱码 → 空表
        assertEquals(0, parseReviewResponse("not json", ms(2026, 11, 16, 22, 0)).size)
        assertEquals(0, parseReviewResponse("", ms(2026, 11, 16, 22, 0)).size)
        // ```json 围栏能剥掉
        val fenced = "```json\n[{\"hash\":\"h1\",\"push\":true,\"kind\":\"BILL\",\"fact\":\"账单到期\",\"actionLabel\":\"去还款\"}]\n```"
        assertEquals(1, parseReviewResponse(reviewResponseBody(fenced), ms(2026, 11, 16, 22, 0)).size)
        // 缺 fact 的那条跳过，其余照收
        val partial = """[
            {"hash":"h1","push":true,"kind":"BILL","fact":"","actionLabel":"去还款"},
            {"hash":"h2","push":true,"kind":"BILL","fact":"账单到期","actionLabel":"去还款"}
        ]"""
        val out = parseReviewResponse(reviewResponseBody(partial), ms(2026, 11, 16, 22, 0))
        assertEquals(1, out.size)
        assertEquals("h2", out[0].hash)
    }

    // ---------- 复盘结果转卡片 ----------

    @Test
    fun reviewCardDeadline_futureNullAndPast() {
        val batch = ms(2026, 11, 16, 21, 30)
        // 模型给了未来截止点就用它
        assertEquals(ms(2026, 11, 24, 23, 59), reviewCardDeadline(ms(2026, 11, 24, 23, 59), batch))
        // 没给 → 复盘当晚起留 48 小时
        assertEquals(batch + REVIEW_CARD_MAX_AGE_MS, reviewCardDeadline(null, batch))
        // 给了已过去的点 → 也按 48 小时留（逾期事项仍值得提醒一次）
        assertEquals(batch + REVIEW_CARD_MAX_AGE_MS, reviewCardDeadline(ms(2026, 11, 15, 12, 0), batch))
    }

    @Test
    fun reviewSuggestionsToCards_pushOnly() {
        val now = ms(2026, 11, 16, 22, 0)
        val cards = reviewSuggestionsToCards(
            listOf(
                suggestion(hash = "h1", push = true, deadlineMs = ms(2026, 11, 24, 23, 59)),
                suggestion(hash = "h2", push = false, fact = "保单已承保")
            ),
            batchId = now,
            nowMs = now
        )
        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("review|h1", card.id)
        assertEquals("review|h1", card.sourceKey)
        assertEquals(SmartCardKind.BILL, card.kind)
        assertEquals("中信信用卡 11 月 24 日到期", card.fact)
        assertEquals("去还款", card.actionLabel)
        assertEquals(ms(2026, 11, 24, 23, 59), card.deadlineMs)
        assertEquals(ms(2026, 11, 24, 23, 59), card.sortMs)
        assertEquals(SmartCardAction.LaunchApp("com.example.bank"), card.action)
        assertEquals("中信银行", card.sourceLabel)
    }

    @Test
    fun reviewSuggestionsToCards_pastDeadlineKeeps48h() {
        val now = ms(2026, 11, 16, 22, 0)
        val cards = reviewSuggestionsToCards(
            listOf(suggestion(deadlineMs = ms(2026, 11, 15, 12, 0))),
            batchId = now,
            nowMs = now
        )
        // 模型给了已过去的截止点，卡片仍推、留 48 小时（逾期也值得提醒一次）
        assertEquals(1, cards.size)
        assertEquals(now + REVIEW_CARD_MAX_AGE_MS, cards[0].deadlineMs)
    }

    @Test
    fun reviewSuggestionsToCards_actionLabelFallbackAndKind() {
        val now = ms(2026, 11, 16, 22, 0)
        val cards = reviewSuggestionsToCards(
            listOf(
                suggestion(hash = "h1", kind = "WEIRD_KIND", actionLabel = "", sourcePackage = ""),
                suggestion(hash = "h2", kind = "APPOINTMENT", action = "open_sms", actionParam = "95558", sourcePackage = "")
            ),
            batchId = now,
            nowMs = now
        )
        // 认不出的卡种归 NOTIFICATION 通用卡；动作标签为空回落「查看」
        assertEquals(2, cards.size)
        assertEquals(SmartCardKind.NOTIFICATION, cards[0].kind)
        assertEquals("查看", cards[0].actionLabel)
        // open_sms＋地址 → 打开短信会话；来源包名没有也不影响
        assertEquals(SmartCardKind.APPOINTMENT, cards[1].kind)
        assertEquals(SmartCardAction.SmsThread("95558"), cards[1].action)
    }

    @Test
    fun reviewSuggestionsToCards_noActionableTargetDropped() {
        val now = ms(2026, 11, 16, 22, 0)
        // open_app 但既无包名又无来源包 → 不推（不硬凑一个动作）
        val cards = reviewSuggestionsToCards(
            listOf(suggestion(action = "open_app", actionParam = "", sourcePackage = "")),
            batchId = now,
            nowMs = now
        )
        assertEquals(0, cards.size)
    }

    // ---------- 动作落点：以消息来源为准 ----------

    @Test
    fun resolveReviewAction_notificationOpensSourceApp() {
        val s = suggestion(action = "open_calendar", actionParam = "", sourcePackage = "")
        val resolved = resolveReviewAction(s, msg(pkg = "com.example.calendar", app = "日历"))
        assertEquals("open_app", resolved.action)
        assertEquals("com.example.calendar", resolved.actionParam)
        assertEquals("com.example.calendar", resolved.sourcePackage)
        assertEquals("中信银行", resolved.sourceLabel)
    }

    @Test
    fun resolveReviewAction_smsOpensSmsThread() {
        val s = suggestion(action = "open_app", actionParam = "", sourcePackage = "")
        val resolved = resolveReviewAction(
            s,
            msg(pkg = "95558", app = "短信", text = "账单到期", source = "sms")
        )
        assertEquals("open_sms", resolved.action)
        assertEquals("95558", resolved.actionParam)
        assertEquals("", resolved.sourcePackage)
    }

    @Test
    fun resolveReviewAction_noMessageUnchanged() {
        val s = suggestion()
        assertEquals(s, resolveReviewAction(s, null))
    }

    // ---------- 同事实去重 ----------

    @Test
    fun reviewFactMatchesRuleFact() {
        assertTrue(reviewFactMatchesRuleFact("中信信用卡 11 月 24 日到期", "中信信用卡 11 月 24 日到期"))
        // 一方包含另一方（复盘 fact 更长）→ 同一件事
        assertTrue(reviewFactMatchesRuleFact("中信信用卡 11 月 24 日到期，应还 15118 元", "中信信用卡 11 月 24 日到期"))
        // 不同事 → false
        assertFalse(reviewFactMatchesRuleFact("中信信用卡 11 月 24 日到期", "体检预约 11 月 6 日"))
        // 短词不认（避免“查看”这类短词误撞）
        assertFalse(reviewFactMatchesRuleFact("查看", "查看一下"))
    }

    // ---------- 提示词：编号是哈希、内容已脱敏 ----------

    @Test
    fun reviewUserPrompt_containsHashAndSanitized() {
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
}
