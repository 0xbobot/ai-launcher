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
 *    （用人名占位符“××”代替，全文统一一种）；第四十四批起另去车牌号、
 *    家庭住址（路＋门牌号、栋/单元/室）；保留事项类型、时间、地点名称、
 *    与准备相关的原文关键词。原文不出仓库。
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
/** 车牌号：省简称＋字母＋5–6 位字母数字（新能源 8 位），含可选间隔符 ·/空格/横杠 */
private val PLATE_REGEX =
    Regex("""[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][·\s\-]?[A-Z0-9]{5,6}""")
/**
 * 家庭住址（门牌号及之后，含可选栋/单元/室）：石围路123号3栋2单元501室
 * → 石围[家庭住址]。只从“路/街/巷/道”这个字开始 mask，路名与区/街道名
 * （如光明区、石围路、马田街道）和场馆名（如深圳第一健康鼎和分院四楼）
 * 原样保留——路名本身不定位到户，门牌号＋栋/单元/室才是要去掉的。
 * 注意：路名前不加中文前缀——加了会把前面的区名尾巴一起吃掉
 * （“明区石围”正好 4 字，{1,4} 会从“明”开始匹配）。
 */
private val ADDRESS_ROAD_REGEX =
    Regex("""(?:路|街|巷|道)\s*\d+\s*号(?:\s*\d+\s*(?:栋|幢|号楼))?(?:\s*\d+\s*单元)?(?:\s*\d+\s*(?:室|房))?""")
/** 家庭住址（无路名，只有栋/单元/室）：3栋2单元501室、A栋1201室、5栋301房 */
private val ADDRESS_UNIT_REGEX =
    Regex("""(?:[A-Za-z]?\d+|[A-Za-z])\s*(?:栋|幢)\s*(?:\d+\s*单元\s*)?\d+\s*(?:室|房)""")
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
 * 银行卡号 → 卡尾号 → 车牌号 → 家庭住址 → 金额 → 订单号 → 人名。
 * 人名占位符全文统一用“××”。其余原文（事项类型、时间、地点名称、
 * 准备相关关键词）原样保留。车牌与住址是第四十四批（每天一次整体复盘）
 * 新增：复盘把通知/短信批量发给模型，车牌与详细门牌必须先在本地去掉。
 */
fun sanitizeForLlm(raw: String): String {
    var s = raw
    s = EMAIL_REGEX.replace(s, "[邮箱]")
    s = ID_CARD_REGEX.replace(s, "[证件号]")
    s = PHONE_REGEX.replace(s, "[电话]")
    s = BANK_CARD_REGEX.replace(s, "[卡号]")
    s = TAIL_REGEX.replace(s, "尾号[卡号]")
    s = PLATE_REGEX.replace(s, "[车牌]")
    s = ADDRESS_ROAD_REGEX.replace(s, "[家庭住址]")
    s = ADDRESS_UNIT_REGEX.replace(s, "[家庭住址]")
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
只写查证结果和本地事实都支持的条目，查证结果与本地事实冲突的，以本地事实为准、那条不写。
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

// ---------- DeepSeek 全家桶 API 试点 B 路线（Bob 2026-11-15 批，方案 5.1–5.3） ----------
// 先本地搜/抓一次、再喂模型。出处先到本地、复核得住；查不到、查不准、对不上就不写。
// 全部是纯函数，JVM 单测锁死；网络调用本身（LlmRouter.search/fetch）不进 JVM 单测。

/** 查证结果的一条（标题＋链接＋正文摘要）；rankScore／authority 用于挑 1–2 条 */
data class PrepEvidence(
    val title: String,
    val url: String,
    val text: String,
    val rankScore: Double,
    val authority: Int
)

/** 联网计划：要搜的词／要抓的链接（都可为空；都空＝不联网，走老路） */
data class EvidencePlan(val searchQuery: String?, val fetchUrl: String?)

/** 喂给模型的单条查证正文上限（字符）：搜索取 summary／snippet、抓取取正文前段，都截到它 */
const val EVIDENCE_TEXT_MAX_CHARS = 200

/** 搜索词总长上限（字符）：官方 query 上限 128，本地再收紧到 100，留余量 */
const val SEARCH_QUERY_MAX_CHARS = 100

/** 检查／体检项目词（预约卡触发搜的信号，方案 5.1） */
private val EXAM_ITEM_KEYWORDS = listOf(
    "胃镜", "肠镜", "CT", "核磁", "磁共振", "超声", "B超", "彩超", "心电图",
    "抽血", "验血", "尿检", "便检", "空腹", "体检", "X光", "胸片", "血压",
    "血糖", "血脂", "肝功能", "肾功能", "幽门", "碳13", "碳14", "钼靶",
    "眼底", "肺功能", "骨密度", "甲状腺", "血常规", "尿常规"
)

/** 机构名标记（预约卡触发搜的信号：出现机构名才搜） */
private val INSTITUTION_MARKERS = listOf(
    "医院", "体检中心", "健康管理", "分院", "诊所", "门诊", "疾控",
    "卫生院", "体检科", "健康中心", "医疗中心", "康复中心"
)

/** 公开场馆／机构名标记（日程卡触发搜的信号；单字“中心”不算，避免“市中心”误判） */
private val PLACE_MARKERS = INSTITUTION_MARKERS + listOf(
    "体育馆", "博物馆", "图书馆", "展览馆", "会议中心", "会展中心", "剧院",
    "影院", "影城", "体育场", "体育中心", "文化馆", "科技馆", "美术馆",
    "音乐厅", "歌剧院", "酒店", "机场", "火车站", "高铁站", "汽车站",
    "公园", "商场", "购物中心", "大厦", "学院", "大学"
)

/** 会议入会链接的 host 特征（抓取时排除，方案 5.2：入会链接不发、不抓） */
private val MEETING_LINK_HOSTS = listOf(
    "feishu", "dingtalk", "wemeet", "zoom", "webex", "voov",
    "teams.microsoft", "teams.live", "meet.google", "meeting.tencent"
)

/** 私人住址特征（有这些就不搜，方案 5.2：私人住址不做搜索词） */
private val PRIVATE_ADDRESS_REGEX =
    Regex("""\d+\s*[号栋单元室房楼层]|小区|花园|新村|城中村|[路街巷道胡同]\s*\d""")

/** 链接提取：http(s) 链接（只认 ASCII 的 URL 字符；中文、全角标点会自然截断
 * 链接——短信/日程描述里链接后面常直接跟中文，不截断会把中文吞进链接） */
private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&*+,;=%]+""")

/** 出处校验用的停用词对：通用词不算依据（只有这些词对上不算有出处） */
private val EVIDENCE_STOPWORDS = setOf(
    "注意", "准备", "提前", "按时", "准时", "相关", "可能", "如果",
    "以及", "或者", "不要", "不能", "可以", "应该", "建议", "记得",
    "务必", "尽量", "避免", "确保", "检查", "确认"
)

/**
 * 提取地点名（纯函数）：找含场馆／机构标记的中文词串（如“深圳湾体育中心”、
 * “深圳第一健康鼎和分院”），取最长的一段（末尾 16 字以内）。
 * 命中私人住址特征的不要（私人住址不搜）。
 */
fun extractPlaceName(text: String): String? {
    val markerPattern = PLACE_MARKERS.joinToString("|") { Regex.escape(it) }
    val match = Regex("""[\u4e00-\u9fa5]{1,12}(?:$markerPattern)""").find(text) ?: return null
    val name = match.value.takeLast(16)
    if (isPrivateAddress(name)) return null
    return name
}

/** 私人住址判断（纯函数）：含门牌／栋／单元／室／小区等特征 */
fun isPrivateAddress(text: String): Boolean = PRIVATE_ADDRESS_REGEX.containsMatchIn(text)

/** 会议入会链接判断（纯函数）：host 含会议服务商特征 */
fun isMeetingLink(url: String): Boolean {
    val host = url.lowercase().substringAfter("://").substringBefore("/")
        .substringBefore("?").substringBefore("#")
    if (host.isBlank()) return false
    return MEETING_LINK_HOSTS.any { host.contains(it) }
}

/**
 * 提取公开链接（纯函数）：文本里的 http(s) 链接，去尾部标点、去重；
 * 会议入会链接与 [excludeUrls] 里的排除（卡片动作上的入会链接）一律不要。
 */
fun extractPublicLinks(text: String, excludeUrls: Set<String> = emptySet()): List<String> {
    return URL_REGEX.findAll(text)
        .map { it.value.trimEnd('.', ',', ';', '!', '?', '。', '，', '！', '？', '、', '）', ')') }
        .filter { it.isNotBlank() && !isMeetingLink(it) && it !in excludeUrls }
        .distinct()
        .toList()
}

/**
 * 拼搜索词（纯函数）：只用脱敏后的词——检查项目＋地点名（预约卡再加“准备”）。
 * 输入已是脱敏原文；构造方式本身保证敏感信息进不来（只挑固定词表里的
 * 项目词与地点名，不拼占位符、不拼私人住址、不拼原始长句）。
 * 返回 null＝没值得查的（不联网）。总长收紧到 SEARCH_QUERY_MAX_CHARS。
 */
fun buildSearchQuery(source: String, kind: SmartCardKind): String? {
    if (kind != SmartCardKind.APPOINTMENT && kind != SmartCardKind.CALENDAR) return null
    val items = EXAM_ITEM_KEYWORDS.filter { source.contains(it) }
    val place = extractPlaceName(source)
    if (items.isEmpty() && place == null) return null
    val parts = when (kind) {
        SmartCardKind.APPOINTMENT -> items + listOfNotNull(place) + "准备"
        SmartCardKind.CALENDAR -> listOfNotNull(place)
        else -> return null
    }
    return parts.joinToString(" ").trim().take(SEARCH_QUERY_MAX_CHARS).ifBlank { null }
}

/**
 * 联网计划（纯函数，方案 5.1）：预约卡含检查项目词或机构名才搜；日程卡
 * 有公开链接才抓、或地点是公开场馆名才搜；其余不联网（返回 null）。
 * [meetingUrl] 是卡片动作上的入会链接（有则从抓取里排除）。
 * 日程卡链接＋场馆都有时，抓＋搜各一次（每卡最多 2 次工具调用，方案 5.5）。
 */
fun decideEvidencePlan(
    kind: SmartCardKind,
    source: String,
    meetingUrl: String? = null
): EvidencePlan? {
    if (kind != SmartCardKind.APPOINTMENT && kind != SmartCardKind.CALENDAR) return null
    val fetchUrl = if (kind == SmartCardKind.CALENDAR) {
        extractPublicLinks(source, setOfNotNull(meetingUrl)).firstOrNull()
    } else {
        null
    }
    val searchQuery = buildSearchQuery(source, kind)
    if (fetchUrl == null && searchQuery == null) return null
    return EvidencePlan(searchQuery = searchQuery, fetchUrl = fetchUrl)
}

/**
 * 挑查证结果（纯函数）：按权威度（authority，≥3 高权威）再按相关分
 * （rerank_score，≥1.5 高质量）排，取前 [max] 条、按 url 去重。
 * 抓取来的直链证据由调用方给高分（rankScore=3.0、authority=3），优先入选。
 */
fun pickEvidence(candidates: List<PrepEvidence>, max: Int = 2): List<PrepEvidence> {
    if (candidates.isEmpty() || max <= 0) return emptyList()
    return candidates.distinctBy { it.url }
        .sortedWith(
            compareByDescending<PrepEvidence> { it.authority }
                .thenByDescending { it.rankScore }
        )
        .take(max)
}

/**
 * 出处校验（纯函数，方案 5.3）：在 [validatePrepLines] 之后加一道——
 * 每条清单必须能在本地事实（[source]）或查证结果（[evidence] 的标题＋
 * 正文）里找到依据；找不到依据的删掉，不改写、不补写；删完为空，
 * 整段丢弃（返回 null）。证据为空时只按本地事实校验（搜／抓失败就
 * 退回原文校验，不重试）。
 */
fun validatePrepWithEvidence(
    prep: String,
    source: String,
    evidence: List<PrepEvidence>
): String? {
    val lines = prep.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val evidenceText = evidence.joinToString("\n") { "${it.title}\n${it.text}" }
    val kept = lines.filter { line -> hasEvidenceSupport(line, source, evidenceText) }
    if (kept.isEmpty()) return null
    val joined = kept.joinToString(" · ")
    if (joined.length > 110) return null
    return joined
}

/**
 * 单条清单有没有依据：检查项目词／拉丁词整词命中，或中文连续两字
 * （去掉停用词对）出现在本地事实或查证正文里。中文无词界，用双字
 * 匹配是启发式——能拦住明显编造的条目，拦不住共用常见词的擦边条目，
 * 残留风险如实记在这里；模型端提示词那句出处规则是第一道防线。
 */
private fun hasEvidenceSupport(line: String, source: String, evidenceText: String): Boolean {
    val haystack = source + "\n" + evidenceText
    // 检查项目词本身就是依据（如 B超、CT）：行里有、原文／查证里也有
    if (EXAM_ITEM_KEYWORDS.any { it in line && haystack.contains(it) }) return true
    // 拉丁词（≥2 字母）整词命中
    if (Regex("""[A-Za-z]{2,}""").findAll(line).any { haystack.contains(it.value) }) return true
    // 中文：逐个取连续两字（去掉停用词对），任一出现在本地事实或查证正文里即有依据
    val chars = line.filter { it in '\u4e00'..'\u9fa5' }
    for (i in 0 until chars.length - 1) {
        val bigram = chars.substring(i, i + 2)
        if (bigram in EVIDENCE_STOPWORDS) continue
        if (haystack.contains(bigram)) return true
    }
    return false
}
