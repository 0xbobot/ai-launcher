package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
* 智能卡片解析单测（447）：锚点例子原句＋变体＋反例，纯函数、JVM 可跑。
* 锚点（2026-11-05 Bob 亲定）：
* - 账单：“中信银行：您尾号 7072 的信用卡本期应还 ¥2,057.47，请于 11 月 7 日前还款”
* - 体检：“体检预约：11 月 6 日 08:00–09:00，深圳第一健康鼎和分院四楼，订单已支付”
*/
class SmartCardParserTest {

private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0, sec: Int = 0): Long =
Calendar.getInstance().apply {
set(year, month - 1, day, hour, min, sec)
set(Calendar.MILLISECOND, 0)
}.timeInMillis

// 2026-11-05（周四）15:00：账单到期前 2 天、体检前一天下午
private val now = ms(2026, 11, 5, 15, 0)

@Test
fun parseBill_anchorExample() {
val bill = parseBillSms(
"中信银行：您尾号 7072 的信用卡本期应还 ¥2,057.47，请于 11 月 7 日前还款",
"95558", now
)!!
assertEquals("中信银行", bill.bank)
assertEquals("7072", bill.tail4)
assertEquals("¥2,057.47", bill.amountText)
assertEquals(205747L, bill.amountFen)
assertEquals("11月7日", bill.dueLabel)
assertEquals(ms(2026, 11, 7, 23, 59, 59), bill.dueDayEndMs - 999)
}

@Test
fun parseBill_senderLabelIsBankName() {
val bill = parseBillSms(
"您尾号 1234 的信用卡本期应还 ¥1,000.00，请于 12 月 1 日前还款",
"招商银行", now
)!!
assertEquals("招商银行", bill.bank)
}

@Test
fun parseBill_noCommaAmount() {
val bill = parseBillSms(
"您尾号 7072 的信用卡本期应还 ¥2057.47，请于 11 月 7 日前还款",
"95558", now
)!!
assertEquals(205747L, bill.amountFen)
}

// 448 回归：真实中信短信原文（2026-11-07 19:07 收到时仍在窗口内）。
// 447 三处全灭：无“尾号”、无 ¥（2057.47元）、第一个日期是“截至”日。
// 发件方 95558 是纯数字、正文无“银行”，银行名回退为泛称“银行”（号段映射未做）。
@Test
fun parseBill_realCitibankSms() {
val atDueDay = ms(2026, 11, 7, 19, 7)
val bill = parseBillSms(
"截至11月04日仍需还款2057.47元，最后还款日11月07日。如已还款请忽略。",
"95558", atDueDay
)!!
assertEquals(205747L, bill.amountFen)
assertEquals("¥2,057.47", bill.amountText)
assertEquals("11月7日", bill.dueLabel)
assertNull(bill.tail4)
assertEquals("银行", bill.bank)
assertEquals(ms(2026, 11, 7, 23, 59, 59), bill.dueDayEndMs - 999)
assertTrue(isBillInWindow(atDueDay, bill.dueDayEndMs))
}

// 448 回归：无 ¥ 金额（数字后跟“元”）+ 有尾号
@Test
fun parseBill_noYenAmount() {
val bill = parseBillSms(
"您尾号 7072 的信用卡本期应还 2057.47元，请于 11 月 7 日前还款",
"95558", now
)!!
assertEquals(205747L, bill.amountFen)
assertEquals("¥2,057.47", bill.amountText)
assertEquals("7072", bill.tail4)
assertEquals("11月7日", bill.dueLabel)
}

// 448 回归：多日期文本——“截至”日（账单日）在前、还款日在后，取还款日
@Test
fun parseBill_multipleDatesUsesDueKeyword() {
val bill = parseBillSms(
"您尾号 7072 的信用卡截至 11 月 4 日，本期应还 ¥2,057.47，最后还款日 11 月 7 日",
"95558", now
)!!
assertEquals("11月7日", bill.dueLabel)
assertEquals(ms(2026, 11, 7, 23, 59, 59), bill.dueDayEndMs - 999)
}

// 448 回归：无尾号文本仍可成卡
@Test
fun parseBill_noTailStillParses() {
val bill = parseBillSms(
"中信银行：您本期应还 ¥2,057.47，请于 11 月 7 日前还款",
"95558", now
)!!
assertNull(bill.tail4)
assertEquals("中信银行", bill.bank)
assertEquals(205747L, bill.amountFen)
assertEquals("11月7日", bill.dueLabel)
}

// 448 回归：只有“截至”日、定不出到期日时不成卡（账单日绝不当到期日）
@Test
fun parseBill_statementDateOnlyReturnsNull() {
assertNull(
parseBillSms(
"截至11月04日仍需还款2057.47元。如已还款请忽略。",
"95558", now
)
)
}

@Test
fun parseBill_verificationCodeNeverParses() {
assertNull(
parseBillSms("您的验证码是 123456，请勿泄露", "95558", now)
)
assertNull(
parseBillSms("您尾号 7072 的信用卡验证码 123456，本期应还 ¥2,057.47", "95558", now)
)
}

@Test
fun parseBill_missingAmountOrDateReturnsNull() {
assertNull(
parseBillSms("您尾号 7072 的信用卡请于 11 月 7 日前还款", "95558", now)
)
assertNull(
parseBillSms("您尾号 7072 的信用卡本期应还 ¥2,057.47，请尽快还款", "95558", now)
)
}

@Test
fun parseBill_expiredReturnsNull() {
val afterDue = ms(2026, 11, 8, 9, 0)
assertNull(
parseBillSms(
"中信银行：您尾号 7072 的信用卡本期应还 ¥2,057.47，请于 11 月 7 日前还款",
"95558", afterDue
)
)
}

@Test
fun billWindow_twoDaysBeforeDue() {
val dueEnd = ms(2026, 11, 7, 23, 59, 59)
assertTrue(isBillInWindow(ms(2026, 11, 5, 0, 0), dueEnd))
assertTrue(isBillInWindow(ms(2026, 11, 7, 23, 0), dueEnd))
assertFalse(isBillInWindow(ms(2026, 11, 4, 23, 59), dueEnd))
assertFalse(isBillInWindow(ms(2026, 11, 8, 0, 0), dueEnd))
}

@Test
fun parseAppointment_anchorExample() {
val appt = parseAppointmentSms(
"体检预约：11 月 6 日 08:00–09:00，深圳第一健康鼎和分院四楼，订单已支付",
now
)!!
assertEquals(ms(2026, 11, 6), appt.dayStartMs)
assertEquals(480, appt.startMin)
assertEquals(540, appt.endMin)
assertEquals("深圳第一健康鼎和分院四楼", appt.place)
assertTrue(appt.isCheckup)
}

@Test
fun parseAppointment_tomorrowSingleTime() {
val appt = parseAppointmentSms(
"挂号成功：明天 09:30，市人民医院门诊三楼，请提前 30 分钟到达",
now
)!!
assertEquals(ms(2026, 11, 6), appt.dayStartMs)
assertEquals(570, appt.startMin)
assertEquals(null, appt.endMin)
assertEquals("市人民医院门诊三楼", appt.place)
assertFalse(appt.isCheckup)
}

@Test
fun parseAppointment_verificationCodeNeverParses() {
assertNull(
parseAppointmentSms("您的体检预约验证码是 123456，明天 09:30", now)
)
}

@Test
fun parseAppointment_noTimeReturnsNull() {
assertNull(
parseAppointmentSms("体检预约：11 月 6 日，深圳第一健康鼎和分院四楼", now)
)
}

@Test
fun parseAppointment_pastReturnsNull() {
val afterAppt = ms(2026, 11, 7, 10, 0)
assertNull(
parseAppointmentSms(
"体检预约：11 月 6 日 08:00–09:00，深圳第一健康鼎和分院四楼",
afterAppt
)
)
}

@Test
fun appointmentWindow_24HoursBeforeStart() {
val appt = parseAppointmentSms(
"体检预约：11 月 6 日 08:00–09:00，深圳第一健康鼎和分院四楼",
now
)!!
assertTrue(isAppointmentInWindow(ms(2026, 11, 5, 15, 0), appt))
assertFalse(isAppointmentInWindow(ms(2026, 11, 5, 7, 59), appt))
assertFalse(isAppointmentInWindow(ms(2026, 11, 6, 9, 0), appt))
}

@Test
fun relativeDayLabel_todayTomorrowDayAfter() {
assertEquals("今天", relativeDayLabel(ms(2026, 11, 5), now))
assertEquals("明天", relativeDayLabel(ms(2026, 11, 6), now))
assertEquals("后天", relativeDayLabel(ms(2026, 11, 7), now))
assertEquals("11月10日", relativeDayLabel(ms(2026, 11, 10), now))
}

@Test
fun formatTimeRange_rangeAndSingle() {
assertEquals("08:00–09:00", formatTimeRange(480, 540))
assertEquals("09:30", formatTimeRange(570, null))
}

@Test
fun deliveryTravelKeywords() {
assertTrue(
isDeliveryOrTravelNotification("菜鸟", "您的包裹已到菜鸟驿站，请凭取件码取件")
)
assertTrue(
isDeliveryOrTravelNotification("航旅纵横", "MU1234 明天 08:00 起飞，提前 2 小时到机场")
)
assertFalse(isDeliveryOrTravelNotification("微信", "你有一条新消息"))
assertFalse(isDeliveryOrTravelNotification("日历", "10:00 项目会"))
}

@Test
fun checkupPrepLine_isStatic() {
assertEquals("今晚 20:00 后禁食 · 明天空腹去 · 带身份证 · 穿宽松衣服", checkupPrepLine())
}
}
