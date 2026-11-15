package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 准备清单脱敏＋本地过滤＋返回校验单测（第十二批，Bob 2026-11-09 22:08 批复）：
 * 脱敏是纯函数，逐类锁死（给正反例子）；本地过滤锁死哪类卡调、哪类不调；
 * 返回校验锁死条数/长度/去空去重/不可用丢弃。网络调用本身（requestPrep）
 * 不进 JVM 单测，由 CI 构建＋真机验收。
 */
class SmartCardPrepTest {

    private fun card(kind: SmartCardKind) = SmartCard(
        id = "t|${kind.name}",
        kind = kind,
        sourceLabel = "短信",
        fact = "fact",
        prep = null,
        prepSource = "体检预约：11月6日 08:00–09:00，深圳第一健康鼎和分院四楼",
        actionLabel = "看预约",
        sortMs = 100L,
        deadlineMs = 200L,
        action = SmartCardAction.SmsThread("95558"),
        sourceKey = "t|${kind.name}"
    )

    // ---------- 本地过滤：只有预约卡与日程卡才调 ----------

    @Test
    fun needsLlmPrep_onlyAppointmentAndCalendar() {
        assertTrue(needsLlmPrep(card(SmartCardKind.APPOINTMENT)))
        assertTrue(needsLlmPrep(card(SmartCardKind.CALENDAR)))
        assertFalse(needsLlmPrep(card(SmartCardKind.BILL)))
        assertFalse(needsLlmPrep(card(SmartCardKind.MISSED_CALL)))
        assertFalse(needsLlmPrep(card(SmartCardKind.NOTIFICATION)))
        assertFalse(needsLlmPrep(card(SmartCardKind.GUIDE)))
    }

    // ---------- 脱敏：去掉敏感信息 ----------

    @Test
    fun sanitize_removesPhoneNumber() {
        assertEquals(
            "请联系 [电话] 确认",
            sanitizeForLlm("请联系 13812345678 确认")
        )
        // 已打码的不再动（138****1234 不是完整手机号）
        assertEquals(
            "未接来电 138****1234",
            sanitizeForLlm("未接来电 138****1234")
        )
    }

    @Test
    fun sanitize_removesIdCardNumber() {
        assertEquals(
            "身份证 [证件号] 已登记",
            sanitizeForLlm("身份证 110101199001011234 已登记")
        )
    }

    @Test
    fun sanitize_removesBankCardAndTail() {
        assertEquals(
            "卡号 [卡号] 待还",
            sanitizeForLlm("卡号 6222021234567890 待还")
        )
        assertEquals(
            "卡号 [卡号] 待还",
            sanitizeForLlm("卡号 6222 0212 3456 7890 待还")
        )
        assertEquals(
            "中信尾号[卡号] 有待还",
            sanitizeForLlm("中信尾号 7072 有待还")
        )
    }

    @Test
    fun sanitize_removesAmount() {
        assertEquals("本期应还 [金额]", sanitizeForLlm("本期应还 ¥2,057.47"))
        assertEquals("本期应还 [金额]", sanitizeForLlm("本期应还 2057.47元"))
    }

    @Test
    fun sanitize_removesEmailAndOrderNo() {
        assertEquals(
            "联系 [邮箱] 查询",
            sanitizeForLlm("联系 bob@example.com 查询")
        )
        assertEquals(
            "订单号[单号] 已支付",
            sanitizeForLlm("订单号：AB12345678 已支付")
        )
        assertEquals(
            "预约号[单号] 请准时到",
            sanitizeForLlm("预约号：XYZ98765 请准时到")
        )
    }

    @Test
    fun sanitize_replacesPersonNameWithPlaceholder() {
        // 人名占位符全文统一用“××”
        assertEquals("××先生请准时到", sanitizeForLlm("张三先生请准时到"))
        assertEquals("××女士请准时到", sanitizeForLlm("李四女士请准时到"))
        assertEquals("就诊人×× 请准时到", sanitizeForLlm("就诊人：张三 请准时到"))
        assertEquals("联系人×× 138", sanitizeForLlm("联系人：王五 138"))
    }

    @Test
    fun sanitize_keepsTypeTimeAndPlace() {
        // 正例：事项类型、时间、地点名称、准备相关关键词原样保留
        val text = "体检预约：11月6日 08:00–09:00，深圳第一健康鼎和分院四楼，订单已支付"
        assertEquals(
            "体检预约：11月6日 08:00–09:00，深圳第一健康鼎和分院四楼，订单已支付",
            sanitizeForLlm(text)
        )
        val mixed = "体检预约：11月6日 08:00，深圳第一健康鼎和分院四楼，" +
            "张三先生，电话 13812345678，订单号：AB12345678，应还 ¥100.00"
        assertEquals(
            "体检预约：11月6日 08:00，深圳第一健康鼎和分院四楼，" +
                "××先生，电话 [电话]，订单号[单号]，应还 [金额]",
            sanitizeForLlm(mixed)
        )
    }

    // ---------- 返回校验：限条数、限长度、去空去重、不可用丢弃 ----------

    @Test
    fun validatePrepLines_joinsUpToFourItems() {
        assertEquals(
            "今晚 20:00 后禁食 · 明天空腹去 · 带身份证 · 穿宽松衣服",
            validatePrepLines("今晚 20:00 后禁食\n明天空腹去\n带身份证\n穿宽松衣服")
        )
        // 序号与项目符号去掉
        assertEquals(
            "带身份证 · 穿宽松衣服",
            validatePrepLines("1. 带身份证\n2. 穿宽松衣服")
        )
        assertEquals(
            "带身份证 · 穿宽松衣服",
            validatePrepLines("• 带身份证\n- 穿宽松衣服")
        )
    }

    @Test
    fun validatePrepLines_capsAtFourAndDedupes() {
        assertEquals(
            "一 · 二 · 三 · 四",
            validatePrepLines("一\n二\n三\n四\n五\n六")
        )
        assertEquals("带身份证", validatePrepLines("带身份证\n带身份证\n\n"))
    }

    @Test
    fun validatePrepLines_rejectsUnusable() {
        assertNull(validatePrepLines(""))
        assertNull(validatePrepLines("   \n\n"))
        // 过长的一条（>24 字）丢掉，剩下的不够就整体丢弃
        assertNull(validatePrepLines("这是一条超过二十四个字的准备事项根本读不完还要更长一点才够"))
        // 模型拒绝回答时不贴清单
        assertNull(validatePrepLines("抱歉，我无法提供准备清单"))
    }

    @Test
    fun parsePrepResponse_extractsContentAndValidates() {
        val body = """{"choices":[{"message":{"content":"今晚禁食\n带身份证"}}]}"""
        assertEquals("今晚禁食 · 带身份证", parsePrepResponse(body))
        // 垃圾响应 / 空内容 / 报错体都丢弃
        assertNull(parsePrepResponse("not json"))
        assertNull(parsePrepResponse("{}"))
        assertNull(parsePrepResponse("""{"choices":[{"message":{"content":""}}]}"""))
        assertNull(
            parsePrepResponse(
                """{"choices":[{"message":{"content":"抱歉，无法提供"}}]}"""
            )
        )
    }

    // ---------- 试点 B 路线：搜索词（方案 5.1／5.2） ----------

    @Test
    fun buildSearchQuery_appointmentUsesExamItemsAndPlace() {
        val source = "体检预约：11月6日 08:00，胃镜检查，空腹，深圳第一健康鼎和分院四楼"
        val query = buildSearchQuery(source, SmartCardKind.APPOINTMENT)
        assertTrue(query != null)
        assertTrue(query!!.contains("胃镜"))
        assertTrue(query.contains("空腹"))
        assertTrue(query.contains("分院"))
        assertTrue(query.length <= SEARCH_QUERY_MAX_CHARS)
    }

    @Test
    fun buildSearchQuery_noSensitiveInfoInQuery() {
        // 脱敏后的占位符与原始敏感信息都不进搜索词（构造方式本身只挑固定词表）
        val source = "体检预约：××先生，电话 [电话]，身份证 [证件号]，卡号 [卡号]，" +
            "订单号[单号]，应还 [金额]，联系 [邮箱]，胃镜，空腹"
        val query = buildSearchQuery(source, SmartCardKind.APPOINTMENT)
        assertTrue(query != null)
        assertFalse(query!!.contains("[电话]"))
        assertFalse(query.contains("[证件号]"))
        assertFalse(query.contains("[卡号]"))
        assertFalse(query.contains("[金额]"))
        assertFalse(query.contains("[邮箱]"))
        assertFalse(query.contains("[单号]"))
        assertFalse(query.contains("××"))
        assertFalse(query.contains("张三"))
        assertFalse(query.contains("138"))
    }

    @Test
    fun buildSearchQuery_returnsNullWithoutKeyword() {
        // 预约卡没检查项目词、没机构名 → 不搜
        assertNull(buildSearchQuery("预约：11月6日 08:00，取报告", SmartCardKind.APPOINTMENT))
        // 日程卡没场馆名 → 不搜
        assertNull(buildSearchQuery("开会：11月6日 10:00，讨论方案", SmartCardKind.CALENDAR))
        // 其他卡种不搜
        assertNull(buildSearchQuery("胃镜，空腹", SmartCardKind.BILL))
    }

    @Test
    fun buildSearchQuery_calendarUsesVenueName() {
        val query = buildSearchQuery(
            "产品发布会\n深圳湾体育中心\n14:00 入场",
            SmartCardKind.CALENDAR
        )
        assertEquals("深圳湾体育中心", query)
    }

    // ---------- 试点 B 路线：联网计划（方案 5.1／5.2） ----------

    @Test
    fun decideEvidencePlan_meetingLinkNotFetched() {
        val source = "周会\n线上\n描述：加入会议 https://vc.feishu.cn/j/123456789\n资料：https://example.com/guide"
        // 动作上的入会链接＋描述里的飞书链接都不进抓取，只抓公开资料链接
        val plan = decideEvidencePlan(
            SmartCardKind.CALENDAR, source, "https://vc.feishu.cn/j/123456789"
        )
        assertEquals("https://example.com/guide", plan?.fetchUrl)
        // 只有入会链接时不抓
        val onlyMeeting = decideEvidencePlan(
            SmartCardKind.CALENDAR,
            "周会\n线上\n加入会议 https://vc.feishu.cn/j/123456789",
            "https://vc.feishu.cn/j/123456789"
        )
        assertNull(onlyMeeting)
    }

    @Test
    fun decideEvidencePlan_privateAddressNotSearched() {
        // 私人住址（门牌号）不搜、也不抓
        val plan = decideEvidencePlan(
            SmartCardKind.CALENDAR,
            "家访\n深圳市南山区科技南路12号\n15:00",
            null
        )
        assertNull(plan)
        // 小区地址也不搜
        assertNull(
            decideEvidencePlan(
                SmartCardKind.CALENDAR,
                "取件\n金地花园3栋2单元501\n",
                null
            )
        )
    }

    @Test
    fun decideEvidencePlan_appointmentSearchOnlyNoFetch() {
        val plan = decideEvidencePlan(
            SmartCardKind.APPOINTMENT,
            "体检预约：11月6日 08:00，胃镜，空腹，深圳第一健康鼎和分院",
            null
        )
        assertTrue(plan?.searchQuery != null)
        assertNull(plan?.fetchUrl)
    }

    @Test
    fun extractPublicLinks_excludesMeetingLinks() {
        val text = "资料 https://example.com/guide，入会 https://vc.feishu.cn/j/123456789，" +
            "腾讯会议 https://meeting.tencent.com/dm/abc"
        assertEquals(
            listOf("https://example.com/guide"),
            extractPublicLinks(text)
        )
        // 链接后直接跟中文（无标点、无空格）也要截断，不能把中文吞进链接
        assertEquals(
            listOf("https://example.com/guide"),
            extractPublicLinks("详情见 https://example.com/guide请提前到场")
        )
    }

    // ---------- 试点 B 路线：挑证据（方案 5.2） ----------

    @Test
    fun pickEvidence_picksTopTwoByAuthorityAndScore() {
        val low = PrepEvidence("低权威", "https://a.com/1", "摘要", 2.9, 1)
        val mid = PrepEvidence("中权威", "https://a.com/2", "摘要", 1.0, 3)
        val high = PrepEvidence("高权威高分", "https://a.com/3", "摘要", 2.5, 4)
        val dup = PrepEvidence("重复链接", "https://a.com/3", "摘要", 3.0, 4)
        val picked = pickEvidence(listOf(low, mid, high, dup))
        assertEquals(listOf(high, mid), picked)
        assertEquals(emptyList<PrepEvidence>(), pickEvidence(emptyList()))
    }

    // ---------- 试点 B 路线：出处校验（方案 5.3） ----------

    @Test
    fun validatePrepWithEvidence_keepsSupportedDropsUnsupported() {
        val source = "体检预约：11月6日 08:00，胃镜，空腹，深圳第一健康鼎和分院"
        val evidence = listOf(
            PrepEvidence(
                "胃镜检查前准备", "https://example.com/wei",
                "胃镜检查前需空腹8小时，检查当天穿宽松衣服", 2.5, 3
            )
        )
        // 本地事实支持的留、查证支持的留、都没依据的丢
        assertEquals(
            "检查前空腹8小时 · 穿宽松衣服",
            validatePrepWithEvidence(
                "检查前空腹8小时 · 穿宽松衣服 · 带防晒霜",
                source, evidence
            )
        )
        // 全都不支持 → 整段丢
        assertNull(
            validatePrepWithEvidence("带防晒霜 · 记得带伞", source, evidence)
        )
    }

    @Test
    fun validatePrepWithEvidence_emptyEvidenceFallsBackToSource() {
        // 搜/抓失败（证据为空）时退回只按本地事实校验，不重试、不报错
        val source = "体检预约：11月6日 08:00，胃镜，空腹"
        assertEquals(
            "检查前空腹",
            validatePrepWithEvidence("检查前空腹 · 带防晒霜", source, emptyList())
        )
        assertNull(validatePrepWithEvidence("带防晒霜", source, emptyList()))
    }
}
