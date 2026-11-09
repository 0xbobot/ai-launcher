package com.bobot.ailauncher.data

import org.json.JSONObject

/**
 * 智能卡片准备清单的大模型理解（第十二批，Bob 2026-11-09 22:08 批复 A）：
 * 体检写死模板作废，准备清单改由大模型读原文生成。
 *
 * 三条缺一不可：
 * 1. 本地过滤先行（[needsLlmPrep]）：只有预约卡（短信 APPOINTMENT）
 *    与日程卡（日历 CALENDAR）才调；账单、未接来电、快递/航班不调。
 *    每张卡最多调一次（仓库按卡片 id 缓存结果＋记请求，见
 *    SmartCardRepository.requestPrep）。
 * 2. 脱敏在本地做、发之前做（[sanitizeForLlm]，纯函数）：去掉手机号、
 *    身份证号、银行卡号/尾号、订单号/确认号/预约号、金额、邮箱、人名
 *    （用人名占位符“××”代替，全文统一一种）；保留事项类型、时间、
 *    地点名称、与准备相关的原文关键词。原文不出仓库。
 * 3. 异步、不挡卡片：卡片先按事实＋动作显示，准备清单等大模型回来了
 *    再填上（仓库 prepById）；超时、失败、没配 Key 时只显示事实＋动作，
 *    不贴准备清单、不向用户报大模型错误、不追问配 Key。
 *
 * 只走已有通路：LlmRouter.postChatCompletions（默认 DeepSeek，用户自己
 * 配 Key）；只有 hasKey 为真才调。返回结果要校验（[parsePrepResponse]），
 * 不可用的直接丢弃。
 *
 * 已知局限：人名只换“带称谓/带标签”的（张三先生、就诊人：张三）；正文
 * 里没带标签、孤零零出现的人名，纯函数认不出来——输入仍以事实（时间＋
 * 事项类型＋地点）为主、原文为辅，残留风险如实记在这里，不谎称全脱敏。
 */
private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
/** 身份证号：18 位（末位可为 X）；先于银行卡号换——18 位数字两边都合 */
private val ID_CARD_REGEX = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")
/** 手机号：11 位 1[3-9] 开头；先于银行卡号换 */
private val PHONE_REGEX = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
/** 银行卡号：16–19 位连续数字，或 4-4-4-4 分组写法 */
private val BANK_CARD_REGEX =
    Regex("""(?<!\d)(\d{4}[ \-]?){3}\d{4,7}(?!\d)""")
/** 卡尾号：尾号＋4 位数字 */
private val TAIL_REGEX = Regex("""尾号\s*\d{4}""")
/** 金额：¥ 前缀，或数字＋元 */
private val AMOUNT_REGEX = Regex("""[¥￥]\s*[\d,]+(?:\.\d{1,2})?|[\d,]+(?:\.\d{1,2})?\s*元""")
/** 订单号/确认号/预约号/单号/取件码：标签＋编号（编号 4 位以上字母数字/横杠） */
private val ORDER_NO_REGEX =
    Regex("""(订单号|确认号|预约号|单号|取件码)\s*[：:]\s*[A-Za-z0-9\-]{4,}""")
/** 带标签的人名：就诊人/患者/联系人/收件人/预订人/姓名＋人名 */
private val LABELED_NAME_REGEX =
    Regex("""(就诊人|患者|联系人|收件人|预订人|姓名)\s*[：:]\s*[\u4e00-\u9fa5]{2,4}""")
/** 带称谓的人名：张三先生 / 李四女士 */
private val TITLED_NAME_REGEX = Regex("""[\u4e00-\u9fa5]{2,3}(先生|女士)""")

/**
 * 脱敏（纯函数）：按“最敏感先换”的顺序替换——邮箱 → 身份证 → 手机号 →
 * 银行卡号 → 卡尾号 → 金额 → 订单号 → 人名。人名占位符全文统一用“××”。
 * 其余原文（事项类型、时间、地点名称、准备相关关键词）原样保留。
 */
fun sanitizeForLlm(raw: String): String {
    var s = raw
    s = EMAIL_REGEX.replace(s, "[邮箱]")
    s = ID_CARD_REGEX.replace(s, "[证件号]")
    s = PHONE_REGEX.replace(s, "[电话]")
    s = BANK_CARD_REGEX.replace(s, "[卡号]")
    s = TAIL_REGEX.replace(s, "尾号[卡号]")
    s = AMOUNT_REGEX.replace(s, "[金额]")
    s = ORDER_NO_REGEX.replace(s, "$1[单号]")
    s = LABELED_NAME_REGEX.replace(s, "$1××")
    s = TITLED_NAME_REGEX.replace(s, "××$1")
    return s.trim()
}

/** 本地过滤（纯函数）：只有预约卡与日程卡才需要大模型给准备建议 */
fun needsLlmPrep(card: SmartCard): Boolean =
    card.kind == SmartCardKind.APPOINTMENT || card.kind == SmartCardKind.CALENDAR

/** 准备清单的系统提示词：只按脱敏后的原文给简明准备项、不编造细节 */
val PREP_SYSTEM_PROMPT = """
你是手机桌面助手的准备清单助手。只根据下面提供的内容，给出用户在事项开始前需要准备的事项。
规则：只按提供的内容写，不编造时间、地点、物品或要求；不知道的不要写；最多 3–4 条，每条不超过 20 个字；只返回条目，每行一条，不要序号、不要解释、不要寒暄。
""".trimIndent()

/**
 * 校验大模型返回的准备清单（纯函数）：去序号/去项目符号、去空去重、
 * 每条 ≤24 字、最多 4 条、含“无法/抱歉”的行整段丢弃；拼成一行
 * “条目 · 条目”的卡片 prep 文案。不可用返回 null（调用方直接丢弃，
 * 不贴清单、不报错）。
 */
fun validatePrepLines(raw: String): String? {
    val lines = raw.lines()
        .map { it.trim() }
        .map { it.trimStart('•', '-', '*', '·').trim() }
        .map { it.replace(Regex("""^\d+[.、．]\s*"""), "") }
        .filter { it.isNotEmpty() && it.length <= 24 }
        .filter { !it.contains("无法") && !it.contains("抱歉") }
        .distinct()
        .take(4)
    if (lines.isEmpty()) return null
    val joined = lines.joinToString(" · ")
    if (joined.length > 110) return null
    return joined
}

/**
 * 解析 /chat/completions 响应里的准备清单（纯函数）：
 * 取 choices[0].message.content → validatePrepLines。任何解析失败、
 * 空内容、校验不过都返回 null。
 */
fun parsePrepResponse(rawBody: String): String? {
    val content = try {
        JSONObject(rawBody)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    } catch (_: Exception) {
        return null
    }
    if (content.isBlank()) return null
    return validatePrepLines(content)
}
