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
}
