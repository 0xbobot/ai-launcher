package com.bobot.ailauncher.data

import java.util.Calendar

/**
 * 智能卡片解析（纯函数，JVM 单测锁死 SmartCardParserTest）。
 *
 * 只解析账单/预约两类短信；验证码短信永不进卡片；解析完不长期存原文
 * （设计稿 §4.5：数据只在本地处理，不存历史）。
 *
 * 锚点例子（2026-11-05 Bob 亲定）：
 * - 账单：“中信银行：您尾号 7072 的信用卡本期应还 ¥2,057.47，请于 11 月 7 日前还款”
 * - 体检：“体检预约：11 月 6 日 08:00–09:00，深圳第一健康鼎和分院四楼，订单已支付”
 */
data class ParsedBill(
    /** 银行名：发件方标签里有“银行”就用它，否则从正文里找“XX 银行” */
    val bank: String,
    /** 卡尾号 4 位 */
    val tail4: String,
    /** 显示用金额（含 ¥ 与千分位，如 ¥2,057.47） */
    val amountText: String,
    /** 金额（分），去重键用 */
    val amountFen: Long,
    /** 到期日当天 23:59:59（本地时区） */
    val dueDayEndMs: Long,
    /** 显示用日期，如 11月7日 */
    val dueLabel: String
)

data class ParsedAppointment(
    /** 当天 00:00（本地时区） */
    val dayStartMs: Long,
    /** 开始分钟（自 00:00 起，如 08:00 → 480） */
    val startMin: Int,
    /** 结束分钟（无结束时间时为空，按开始 +60 分钟算） */
    val endMin: Int?,
    /** 地点（启发式提取，提不到为空，不硬凑） */
    val place: String?,
    /** 是否体检类（正文含“体检”）——体检才附准备清单 */
    val isCheckup: Boolean
)

/** 账单在到期前几天出现（设计稿 §3：到期前 2 天出现，到期当天置顶） */
const val BILL_APPEAR_DAYS_BEFORE = 2L

/** 预约在开始前几小时出现（体检锚点：前一天下午出现，24 小时窗口盖住） */
const val APPOINTMENT_APPEAR_HOURS_BEFORE = 24L

private val TAIL_REGEX = Regex("""尾号\s*(\d{4})""")
private val AMOUNT_REGEX = Regex("""[¥￥]\s*([\d,]+(?:\.\d{1,2})?)""")
private val DUE_REGEX = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
private val BANK_IN_TEXT_REGEX = Regex("""([^\s，。,：:；;]{2,8}银行)""")
private val DATE_REGEX = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
private val TIME_RANGE_REGEX =
    Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*[–—\-~～]\s*(\d{1,2})\s*[:：]\s*(\d{2})""")
private val TIME_SINGLE_REGEX = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")

/** 验证码/动态码短信：永不解析成卡片 */
fun isVerificationCodeSms(text: String): Boolean =
    text.contains("验证码") || text.contains("动态码") || text.contains("校验码")

/** 快递/外卖/航班等有时效的通知关键词（设计稿 §3，只取有时效的） */
private val DELIVERY_TRAVEL_KEYWORDS = listOf(
    "取件", "包裹", "快递", "外卖", "航班", "起飞", "登机", "值机", "送达", "派送", "已到"
)

fun isDeliveryOrTravelNotification(title: String, text: String): Boolean {
    val combined = "$title $text"
    return DELIVERY_TRAVEL_KEYWORDS.any { combined.contains(it) }
}

/** 当天 00:00（本地时区） */
fun startOfDayMs(ms: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 当天 23:59:59.999（本地时区） */
fun endOfDayMs(ms: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

private fun monthDayToMs(nowMs: Long, month: Int, day: Int): Long? {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    cal.set(Calendar.MONTH, month - 1)
    cal.set(Calendar.DAY_OF_MONTH, day)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    // 非法日期（如 2 月 30 日）会被 Calendar 顺延，校验后丢弃
    if (cal.get(Calendar.MONTH) != month - 1 || cal.get(Calendar.DAY_OF_MONTH) != day) return null
    return cal.timeInMillis
}

/** 相对日期文案：今天 / 明天 / 后天 / M月d日（不写星期，避免与用户日历口径打架） */
fun relativeDayLabel(dayStartMs: Long, nowMs: Long): String {
    val today = startOfDayMs(nowMs)
    val dayMs = 24L * 60 * 60 * 1000
    return when ((dayStartMs - today) / dayMs) {
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        }
    }
}

/** 分钟 → HH:mm；时间段 → 08:00–09:00（无结束时间只写开始） */
fun formatTimeRange(startMin: Int, endMin: Int?): String {
    fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    return if (endMin != null) "${fmt(startMin)}–${fmt(endMin)}" else fmt(startMin)
}

/**
 * 解析账单短信（纯函数）。
 * 三要素缺一不可：尾号 4 位、¥ 金额、M 月 d 日到期；三者齐了才成卡，
 * 宁可漏一条也不错报一条。已过期的不返回（过了截止点卡片即消失）。
 */
fun parseBillSms(text: String, senderLabel: String, nowMs: Long): ParsedBill? {
    if (isVerificationCodeSms(text)) return null
    if (!text.contains("还款") && !text.contains("应还") && !text.contains("待还")) return null
    val tail = TAIL_REGEX.find(text)?.groupValues?.get(1) ?: return null
    val amountRaw = AMOUNT_REGEX.find(text)?.groupValues?.get(1) ?: return null
    val due = DUE_REGEX.find(text) ?: return null
    val month = due.groupValues[1].toIntOrNull() ?: return null
    val day = due.groupValues[2].toIntOrNull() ?: return null
    val dueStart = monthDayToMs(nowMs, month, day) ?: return null
    val dueEnd = endOfDayMs(dueStart)
    if (dueEnd < nowMs) return null
    // 到期日离现在超过 45 天：不是当期账单，不解析（防旧短信里的日期误触发）
    if (dueStart - startOfDayMs(nowMs) > 45L * 24 * 60 * 60 * 1000) return null
    val amountFen = run {
        val normalized = amountRaw.replace(",", "")
        val parts = normalized.split(".")
        val yuan = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val fen = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        yuan * 100 + fen
    }
    val bank = when {
        senderLabel.contains("银行") -> senderLabel.trim()
        else -> BANK_IN_TEXT_REGEX.find(text)?.groupValues?.get(1) ?: "银行"
    }
    return ParsedBill(
        bank = bank,
        tail4 = tail,
        amountText = "¥$amountRaw",
        amountFen = amountFen,
        dueDayEndMs = dueEnd,
        dueLabel = "${month}月${day}日"
    )
}

/** 账单是否到了出现窗口（到期前 2 天起，到期当天置顶靠排序） */
fun isBillInWindow(nowMs: Long, dueDayEndMs: Long): Boolean {
    val dueStart = startOfDayMs(dueDayEndMs)
    val windowStart = dueStart - BILL_APPEAR_DAYS_BEFORE * 24L * 60 * 60 * 1000
    return nowMs in windowStart..dueDayEndMs
}

/** 地点启发式：逗号分段里含地点词、且不含订单/支付/金额的那一段 */
private val PLACE_KEYWORDS = listOf(
    "楼", "层", "院", "中心", "大厦", "路", "街", "分院", "诊所", "医院", "体检", "门诊"
)
private val PLACE_EXCLUDE = listOf("订单", "支付", "金额", "¥", "￥", "预约", "挂号", "就诊", "复诊")

private fun extractPlace(text: String): String? {
    val segments = text.split('，', ',', '；', ';')
    for (seg in segments) {
        val s = seg.trim()
        if (s.isEmpty()) continue
        if (PLACE_EXCLUDE.any { s.contains(it) }) continue
        // 含日期或时间的分段是预约本身，不是地点（如“挂号成功：明天 09:30”）
        if (DATE_REGEX.containsMatchIn(s) || TIME_SINGLE_REGEX.containsMatchIn(s)) continue
        if (PLACE_KEYWORDS.any { s.contains(it) }) return s
    }
    return null
}

/**
 * 解析预约短信（纯函数）：体检/预约/挂号/就诊类，且带日期与时间。
 * 日期支持 “M 月 d 日”与“明天/后天/今天＋时间”；无日期或无时间不解析。
 * 已结束的不返回。
 */
fun parseAppointmentSms(text: String, nowMs: Long): ParsedAppointment? {
    if (isVerificationCodeSms(text)) return null
    val isCheckup = text.contains("体检")
    if (!isCheckup && !text.contains("预约") && !text.contains("挂号") &&
        !text.contains("就诊") && !text.contains("复诊")
    ) return null
    val dayStart: Long
    val timeMatch = TIME_RANGE_REGEX.find(text)
    val singleMatch = if (timeMatch == null) TIME_SINGLE_REGEX.find(text) else null
    val (startMin, endMin) = when {
        timeMatch != null -> {
            val sh = timeMatch.groupValues[1].toIntOrNull() ?: return null
            val sm = timeMatch.groupValues[2].toIntOrNull() ?: return null
            val eh = timeMatch.groupValues[3].toIntOrNull() ?: return null
            val em = timeMatch.groupValues[4].toIntOrNull() ?: return null
            if (sh !in 0..23 || sm !in 0..59 || eh !in 0..23 || em !in 0..59) return null
            (sh * 60 + sm) to (eh * 60 + em)
        }
        singleMatch != null -> {
            val h = singleMatch.groupValues[1].toIntOrNull() ?: return null
            val m = singleMatch.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            (h * 60 + m) to null
        }
        else -> return null
    }
    val dateMatch = DATE_REGEX.find(text)
    dayStart = when {
        dateMatch != null -> {
            val month = dateMatch.groupValues[1].toIntOrNull() ?: return null
            val day = dateMatch.groupValues[2].toIntOrNull() ?: return null
            monthDayToMs(nowMs, month, day) ?: return null
        }
        text.contains("后天") -> startOfDayMs(nowMs) + 2L * 24 * 60 * 60 * 1000
        text.contains("明天") -> startOfDayMs(nowMs) + 24L * 60 * 60 * 1000
        text.contains("今天") -> startOfDayMs(nowMs)
        else -> return null
    }
    val startMs = dayStart + startMin * 60L * 1000
    val endMs = dayStart + (endMin ?: (startMin + 60)) * 60L * 1000
    if (endMs < nowMs) return null
    return ParsedAppointment(
        dayStartMs = dayStart,
        startMin = startMin,
        endMin = endMin,
        place = extractPlace(text),
        isCheckup = isCheckup
    )
}

/** 预约是否到了出现窗口（开始前 24 小时起，结束后消失） */
fun isAppointmentInWindow(nowMs: Long, appt: ParsedAppointment): Boolean {
    val startMs = appt.dayStartMs + appt.startMin * 60L * 1000
    val endMs = appt.dayStartMs + (appt.endMin ?: (appt.startMin + 60)) * 60L * 1000
    val windowStart = startMs - APPOINTMENT_APPEAR_HOURS_BEFORE * 60L * 60 * 1000
    return nowMs in windowStart until endMs
}

/** 体检准备清单（锚点例子原话，不写成“你的体检”） */
fun checkupPrepLine(): String = "今晚 20:00 后禁食 · 明天空腹去 · 带身份证 · 穿宽松衣服"
