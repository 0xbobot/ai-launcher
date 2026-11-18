package com.bobot.ailauncher.data

import android.content.Context
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/**
 * 每天一次智能复盘（第四十四批，Bob 2026-11-16 13:45 定稿）：
 * “考虑一天一次整体复盘，把收到的历史消息脱敏后给大模型提供建议，
 * 注意走缓存节省模型调用成本”；触发时间 Bob 17:31 亲定——“放晚上，
 * 没有响应一直留到上午”。
 *
 * 为什么是复盘、不是逐条实时调模型：车险的教训——微信通知其实到了桌面
 * （通知监听开着就能读到标题＋正文），是解析器只认快递/航班两种格式、
 * 车险不在里面才丢掉；短信里的“保单已承保”也一样，解析器只认“还有事
 * 要办”的提醒、不认“已经办完了”的确认。Bob 亲口：“我需要的是智能，
 * 不是通过规则”“不只是通过规则”。复盘让大模型读脱敏后的全部通知与
 * 短信、自己判断什么值得推卡，规则解析只作为兜底保留（不动）。
 *
 * 触发：每天晚上 21:30（常量 [REVIEW_HOUR]/[REVIEW_MINUTE]，对齐节点一
 * 「今天/明天一览」的睡前窗锚点；Bob 若改时间只改一处）。WorkManager
 * 周期任务（DailyReviewWorker）＋ refresh() 里的补跑兜底：21:30 设备
 * 关机/休眠时，醒来后补跑一次（见 [isReviewDue]）。
 *
 * 输入：过去 24 小时内收到的通知与短信（自上次复盘以来新增的消息）。
 * 通知历史靠 LauncherNotificationService 收件箱（[ReviewInboxStore]，
 * 通知到达即记一条，只记可清除的非进行中通知）；短信走
 * SmsRepository.loadRecentSms（已授权才读，无 READ_SMS 返回空表）。
 *
 * 脱敏：发给模型前本地脱敏，沿用 SmartCardPrep.sanitizeForLlm（本批
 * 扩展了车牌号、家庭住址）；原文不出仓库、不进日志（528 已立的规矩）。
 *
 * 缓存（省调用，两层，见 [ReviewJudgmentStore] 与仓库的复盘水位）：
 * ①每条消息按脱敏后内容哈希记一次，判过的不再送模型——判断结果按
 * 内容哈希存本地（推/不推都记）；②每天只送自上次复盘以来新增的消息，
 * 不重复送已判过的。正常一天就是一次模型调用。
 * 判断缓存只决定“什么是新增”（哪些消息还没判过、今天要不要调模型），
 * 绝不用来砍 session 历史：判过的消息早已在历史里（首判那天送进去的），
 * 历史必须是完整的。
 *
 * Session（完整历史，第四十七批，Bob 2026-11-18 21:34 纠正）：
 * 549 的滚动摘要是误读——Bob 说的缓存是模型调用的 token 缓存
 * （DeepSeek API 的 Context Caching，提示词缓存），不是应用的缓存；
 * 应用侧的有损摘要（丢行、截断）会丢信息、丢智能。正确做法：session
 * 存完整会话历史（只追加的日志，见 [ReviewHistoryStore]）：每天的新消息
 * ＋模型每轮的回复原文，应用侧不摘要、不丢、不截断，模型看到全部过去的事。
 * token 成本靠 API 的前缀缓存控制，不靠应用丢信息——提示词排布按缓存友好：
 * 稳定的系统提示词放最前面（每天字节级一模一样，顶部不放每天都变的时间戳），
 * 然后是累积的历史（只追加），最后才是当天的新消息。DeepSeek 的缓存默认开启，
 * 不用改代码、不用传参数、没有额外费用；只有请求开头部分与已存的缓存前缀单元
 * 完全一致才命中，开头稍有变化、后面全都不命中。deepseek-flash 命中价
 * $0.003/百万 token（非高峰），是未命中 $0.15 的 1/50；21:30 CST＝13:30 UTC
 * 是非高峰。每次调用后把 usage 里的 prompt_cache_hit_tokens /
 * prompt_cache_miss_tokens 记到日志（只记数字），便于核实缓存到底命没命中、
 * 实际花了多少。缓存尽力而为、不保证 100% 命中；停止使用后几小时到几天内清除
 * （每天一次复盘隔 24 小时，命中与否看日志实测）。上下文窗口 100 万 token。
 *
 * 输出：复盘出的建议变智能卡片（[reviewSuggestionsToCards]），按现有
 * 卡种归类（能归到 BILL/APPOINTMENT/NOTIFICATION/CALENDAR 的归过去，
 * 归不上的用 NOTIFICATION 通用卡承载，来源标原通知/短信的应用名）。
 * 有截止点的按时间排（沿用现有排序：有时效真卡按截止点在前）。
 * 按钮意图按模型给的动作写，不用「下一步」。
 *
 * 留存：晚上复盘出的卡，若用户当晚没响应（没点「我知道了」也没点
 * 动作按钮），一直留到上午，不在午夜过期；过了截止点、用户摘卡、或
 * 下一次晚上复盘出新卡时再处理（过期摘、用户摘卡照常）。
 * 复盘卡片存本地（[ReviewCardStore]），进程死后仍留到上午；动作只
 * 存包名/短信地址（LaunchApp/SmsThread/Dial），不存 PendingIntent。
 *
 * 无 Key / 离线 / 失败：静默、不重试、不出卡（沿用 528 的规矩）。
 * 没配 Key 时复盘不跑，只显示原来的规则卡片。
 *
 * 本文件只放纯函数＋轻量本地存储（SharedPreferences＋org.json），
 * 不碰网络；模型调用与编排在 SmartCardRepository.runDailyReview，
 * 调度在 DailyReviewWorker。JVM 单测锁死（DailyReviewTest）。
 */

/** 复盘触发时间：每天晚上 21:30（Bob 2026-11-16 17:31 亲定）。 */
const val REVIEW_HOUR = 21
const val REVIEW_MINUTE = 30

/** 复盘输入窗口：过去 24 小时（自上次复盘以来新增的消息）。 */
const val REVIEW_WINDOW_MS = 24L * 60L * 60L * 1000L

/** 复盘卡片最长留存：模型没给截止点时，复盘当晚 21:30 起留 48 小时。 */
const val REVIEW_CARD_MAX_AGE_MS = 48L * 60L * 60L * 1000L

/** 判断缓存有效期：判过的内容哈希 30 天内不再送模型。 */
const val REVIEW_JUDGMENT_TTL_MS = 30L * 24L * 60L * 60L * 1000L

/** 一次复盘最多送模型的消息条数（防提示词过长；超了只送最新的）。 */
const val REVIEW_PROMPT_MAX_ITEMS = 50

/** 收件箱最多存的消息条数（超了丢最旧的）。 */
const val REVIEW_INBOX_MAX_ITEMS = 200

/** 判断缓存最多存的条数（超了丢最旧的）。 */
const val REVIEW_JUDGMENT_MAX_ITEMS = 500

/**
 * 复盘的一条输入消息：通知或短信。title/text 是原文（只存本地收件箱，
 * 发模型前先脱敏）；source 区分 "notification" / "sms"。
 */
data class DailyReviewMessage(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timeMs: Long,
    val source: String
)

/**
 * 内容哈希（纯函数）：对脱敏后的标题＋正文做 SHA-256。同一内容只判一次，
 * 与来源应用、到达时间无关——同一条提醒换个应用重发，判断结果相同。
 */
fun reviewMessageHash(message: DailyReviewMessage): String {
    val sanitized = sanitizeForLlm(
        (message.title.trim() + "\n" + message.text.trim()).trim()
    )
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(sanitized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * 当天 21:30 的触发时刻（纯函数）。minSdk 29，java.time 可用。
 */
fun reviewTriggerTodayMs(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        .toLocalDate().atTime(REVIEW_HOUR, REVIEW_MINUTE)
    return today.atZone(zoneId).toInstant().toEpochMilli()
}

/**
 * 最近一次应当触发复盘的时刻（纯函数）：现在若已过今天 21:30 就是今天
 * 21:30，否则是昨天 21:30。补跑靠它——设备 21:30 关机、早上 7 点醒来，
 * 最近一次触发点是昨晚 21:30，仍算到期。
 */
fun reviewLatestTriggerMs(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val today = reviewTriggerTodayMs(nowMs, zoneId)
    return if (nowMs >= today) today else today - REVIEW_WINDOW_MS
}

/**
 * 是否该跑复盘（纯函数）：上次复盘早于最近一次触发点即到期。
 * 正常：21:30 一到，上次是昨晚 → 到期；已跑过今晚 → 不到期；
 * 补跑：早上醒来，上次是前天 → 到期。
 */
fun isReviewDue(lastReviewMs: Long, nowMs: Long): Boolean =
    lastReviewMs < reviewLatestTriggerMs(nowMs)

/**
 * 复盘总门（纯函数）：配了 Key 且到期才跑。没配 Key 时复盘不跑，
 * 只显示原来的规则卡片（528 的规矩）；没到期也不跑。
 */
fun shouldRunReview(hasKey: Boolean, lastReviewMs: Long, nowMs: Long): Boolean =
    hasKey && isReviewDue(lastReviewMs, nowMs)

/**
 * 距下一次 21:30 的毫秒数（纯函数）：WorkManager 首次调度的初始延迟。
 * 现在 10:00 → 到今天 21:30；现在 22:00 → 到明天 21:30。
 */
fun reviewDelayToNextTriggerMs(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val today = reviewTriggerTodayMs(nowMs, zoneId)
    return if (nowMs < today) today - nowMs else today + REVIEW_WINDOW_MS - nowMs
}

/**
 * 大模型对一条消息的复盘判断。push=false 也要缓存（判过“不值得推”，
 * 下次不再送）；hash 是复盘输入消息的 [reviewMessageHash]。
 * action 取值：open_app（actionParam＝包名）/ open_sms（actionParam＝
 * 短信地址）/ open_calendar / none；模型乱写的动作一律按 none 处理。
 */
data class ReviewSuggestion(
    val hash: String,
    val push: Boolean,
    val kind: String,
    val fact: String,
    val actionLabel: String,
    val action: String,
    val actionParam: String,
    val deadlineMs: Long?,
    val sourceLabel: String,
    val sourcePackage: String,
    val savedMs: Long = 0L
)

/**
 * 复盘的系统提示词：只按脱敏后的消息判断，不编造时间地点，不写「下一步」。
 * 缓存友好是硬要求：这段提示词每天字节级一模一样（没有任何插值、没有任何
 * 每天都变的时间戳），DeepSeek 的提示词缓存（Context Caching）才能把它连同
 * 后面的历史一起命中；开头稍有变化，后面全都不命中。不要往这里加动态内容。
 */
val REVIEW_SYSTEM_PROMPT = """
你是手机桌面助手的复盘助手。下面是用户过去收到的通知与短信（已脱敏，敏感信息已用占位符替换），按时间顺序排：先是过去的复盘记录（每轮有当时的新消息和模型的判断），最后是今天的新消息。
请只对今天的新消息逐条判断：哪些值得在桌面上推一张智能卡片提醒用户，哪些不值得。
值得推的：有明确截止点、过期会吃亏的事（账单还款、预约/体检、取件、航班、会议、证件/保单到期、缴费、报名截止等）。
不值得推的：已经办完了的确认（保单已承保、缴费成功、订单已完成）、纯营销/广告、验证码、系统提示、无明确事项的闲聊。
过去的复盘记录只供参考：用它判断哪些事已经办完、不用再推；如有冲突，以今天的新消息为准。
只返回一个 JSON 对象，不要解释、不要寒暄。格式：
{"suggestions":[{"hash":"<消息编号>","push":true/false,"kind":"BILL|APPOINTMENT|NOTIFICATION|CALENDAR","fact":"<一句话事实，含截止点，如：中信信用卡 11 月 24 日到期，应还 [金额]>","actionLabel":"<下一步意图，如：去还款/查看/回拨，不要写下一步>","action":"open_app|open_sms|open_calendar|none","deadlineMs":<截止点毫秒时间戳，没有填 null>,"sourceLabel":"<原应用名>"}]}
不知道的字段填空字符串或 null，不编造时间、地点、金额、包名。
注意：动作的目标（打开哪个应用/哪条短信）由桌面按消息来源定，你只给意图（actionLabel）和动作方向（action），不要写包名、不要写号码。
""".trimIndent()

/** 复盘时间的可读写法（纯函数）："2026-11-16 21:30"（本地时区）。 */
fun reviewTimeText(timeMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/**
 * 复盘的用户提示词：消息逐条编号，编号就是内容哈希（模型原样回填）。
 * 时间写可读日期（不写 epoch 毫秒——模型据此算截止点才准）。
 *
 * 缓存友好的排布（DeepSeek Context Caching 只认前缀完全一致）：
 * [history] 是存下的完整 session 历史（过去每轮发出去的提示词原文＋
 * 模型回复原文），原样放在最前面、一字不改；今天的新消息只追加在末尾。
 * 每天都变的「现在是」只出现在末尾的今天这段里，绝不放开头——开头稍有
 * 变化，后面全都不命中。今天这段本身就是明天历史的开头，所以前缀只会
 * 越长越稳，不会漂移（见 ReviewHistoryTest 的前缀不变量单测）。
 * 历史为空（第一天）时直接就是今天这段，不加空的历史段。
 */
fun reviewUserPrompt(
    messages: List<DailyReviewMessage>,
    nowMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    history: String = ""
): String {
    val lines = messages.mapIndexed { index, message ->
        val sanitized = sanitizeForLlm(
            (message.title.trim() + "\n" + message.text.trim()).trim()
        )
        val hash = reviewMessageHash(message)
        "[${index + 1}] hash=$hash 来源=${message.appName} 时间=${reviewTimeText(message.timeMs, zoneId)}\n$sanitized"
    }
    val todayBlock = "现在是：${reviewTimeText(nowMs, zoneId)}（复盘时间）。今天的新消息（共 ${messages.size} 条）：\n" +
        lines.joinToString("\n\n")
    return if (history.isBlank()) todayBlock else history + "\n\n" + todayBlock
}

/**
 * 复盘调用的完整返回：建议列表＋清洗后的回复原文。
 * [replyText] 只有解析成功时才有（对象格式或裸数组格式都算）：调用方把它
 * 连同当天发出去的提示词一起追加进 session 历史（见 [reviewHistoryAppend]）。
 * 解析失败时建议为空、回复原文也为空——那一轮不进历史（失败也算一次）。
 */
data class ReviewResult(
    val suggestions: List<ReviewSuggestion>,
    val replyText: String?
)

/** 取 choices[0].message.content，去 ```json 围栏；取不到/空白返回 null。 */
private fun reviewResponseContent(rawBody: String): String? {
    val content = try {
        JSONObject(rawBody)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    } catch (_: Exception) {
        return null
    }
    if (content.isBlank()) return null
    return content.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
}

/** JSONArray → 建议列表（纯函数）：单条缺 hash/fact 就跳过那条，其余照收。 */
private fun parseReviewSuggestions(array: JSONArray, nowMs: Long): List<ReviewSuggestion> {
    val out = mutableListOf<ReviewSuggestion>()
    for (i in 0 until array.length()) {
        val obj = try {
            array.getJSONObject(i)
        } catch (_: Exception) {
            continue
        }
        val suggestion = reviewSuggestionFromJson(obj, nowMs) ?: continue
        out.add(suggestion)
    }
    return out
}

/**
 * 解析复盘响应（纯函数）：新格式是对象 {"suggestions":[...]}；
 * 549 的对象格式带 sessionSummary 也兼容（摘要键直接忽略，不再读取）；
 * 兼容 546 的裸数组格式。replyText 是清洗后的回复原文（解析成功才有）。
 * 任何解析失败返回空建议＋空回复原文（调用方静默、不出卡、不动已存历史）。
 */
fun parseReviewResult(rawBody: String, nowMs: Long): ReviewResult {
    val json = reviewResponseContent(rawBody) ?: return ReviewResult(emptyList(), null)
    // 对象格式（新）：先试 JSONObject
    try {
        val obj = JSONObject(json)
        if (obj.has("suggestions")) {
            val array = obj.optJSONArray("suggestions") ?: JSONArray()
            return ReviewResult(parseReviewSuggestions(array, nowMs), json)
        }
    } catch (_: Exception) {
        // 不是对象，往下按数组试
    }
    // 裸数组格式（546，兼容）
    return try {
        ReviewResult(parseReviewSuggestions(JSONArray(json), nowMs), json)
    } catch (_: Exception) {
        ReviewResult(emptyList(), null)
    }
}

/**
 * 解析复盘响应里的建议列表（纯函数，兼容入口）：等价于
 * parseReviewResult(...).suggestions。任何解析失败返回空表。
 */
fun parseReviewResponse(rawBody: String, nowMs: Long): List<ReviewSuggestion> =
    parseReviewResult(rawBody, nowMs).suggestions

/**
 * 把一轮复盘追加进 session 历史（纯函数）：存下的就是当天发给模型的
 * 用户提示词原文＋模型的回复原文。只追加、不改写、不摘要、不截断。
 * 下一次复盘把存下的历史原样放在新消息之前发出去——前缀字节级一致，
 * DeepSeek 的提示词缓存才能命中（见本文件头部的说明）。
 * 回复原文照存、不再脱敏：模型只见过脱敏输入，不可能复述出真实敏感
 * 信息；反而 sanitizeForLlm 会误伤 hash（64 位十六进制里的 11 位数字串
 * 会被 PHONE_REGEX 当电话 mask 掉，hash 一坏、模型就对不上消息了）。
 * 回复为空时原样返回（不追加空轮次）。
 */
fun reviewHistoryAppend(sentUserContent: String, replyText: String): String {
    val reply = replyText.trim()
    if (reply.isEmpty()) return sentUserContent
    return sentUserContent + "\n模型判断：\n" + reply
}

/** 单条复盘判断解析（纯函数）：缺 hash/fact 返回 null；其余字段缺了用缺省值。 */
fun reviewSuggestionFromJson(obj: JSONObject, nowMs: Long): ReviewSuggestion? {
    val hash = obj.optString("hash", "").trim()
    val fact = obj.optString("fact", "").trim()
    if (hash.isEmpty() || fact.isEmpty()) return null
    val push = obj.optBoolean("push", false)
    val kind = obj.optString("kind", "NOTIFICATION").trim().uppercase()
    val actionLabel = obj.optString("actionLabel", "").trim()
    val action = obj.optString("action", "none").trim().lowercase()
    val actionParam = obj.optString("actionParam", "").trim()
    val deadlineMs = if (obj.isNull("deadlineMs")) null else obj.optLong("deadlineMs", 0L)
        .takeIf { it > 0L }
    val sourceLabel = obj.optString("sourceLabel", "").trim()
    val sourcePackage = obj.optString("sourcePackage", "").trim()
    return ReviewSuggestion(
        hash = hash,
        push = push,
        kind = kind,
        fact = fact,
        actionLabel = actionLabel,
        action = action,
        actionParam = actionParam,
        deadlineMs = deadlineMs,
        sourceLabel = sourceLabel,
        sourcePackage = sourcePackage,
        savedMs = nowMs
    )
}

/** 模型给的卡种字符串 → SmartCardKind：认不出的归 NOTIFICATION 通用卡。 */
fun reviewKindFromString(kind: String): SmartCardKind = when (kind.uppercase()) {
    "BILL" -> SmartCardKind.BILL
    "APPOINTMENT" -> SmartCardKind.APPOINTMENT
    "CALENDAR" -> SmartCardKind.CALENDAR
    else -> SmartCardKind.NOTIFICATION
}

/**
 * 复盘卡片的截止点（纯函数）：模型给了未来截止点就用它；没给、或给了
 * 已过去的点，就按复盘当晚起留 48 小时（[REVIEW_CARD_MAX_AGE_MS]）。
 */
fun reviewCardDeadline(modelDeadlineMs: Long?, reviewMs: Long): Long {
    return if (modelDeadlineMs != null && modelDeadlineMs > reviewMs) {
        modelDeadlineMs
    } else {
        reviewMs + REVIEW_CARD_MAX_AGE_MS
    }
}

/**
 * 复盘判断 → 智能卡片（纯函数）。只收 push=true 的；fact 超 120 字截断、
 * actionLabel 为空或超 16 字回落「查看」；截止点按 [reviewCardDeadline]
 * 算（模型没给、或给了已过去的点，都按复盘当晚起留 48 小时——逾期事项
 * 仍值得提醒一次）；动作按 [reviewSuggestionAction] 转，解析不出可执行
 * 动作的（open_app 又无包名又无来源包）整张丢弃，不硬凑。
 * 卡片 id＝"review|<hash>"，稳定可去重；sourceKey 同 id。
 * sortMs＝deadlineMs（有时效真卡按截止点排，沿用现有排序）。
 */
fun reviewSuggestionsToCards(
    suggestions: List<ReviewSuggestion>,
    batchId: Long,
    nowMs: Long
): List<SmartCard> {
    val out = mutableListOf<SmartCard>()
    for (s in suggestions) {
        if (!s.push) continue
        val deadlineMs = reviewCardDeadline(s.deadlineMs, batchId)
        if (deadlineMs <= nowMs) continue
        val fact = s.fact.take(120).trim()
        if (fact.isEmpty()) continue
        val actionLabel = s.actionLabel.take(16).trim().ifEmpty { "查看" }
        val sourceLabel = s.sourceLabel.ifEmpty { "通知" }
        val action = reviewSuggestionAction(s) ?: continue
        val targetApp = when (action) {
            is SmartCardAction.LaunchApp ->
                TargetApp(action.packageName, sourceLabel)
            else -> null
        }
        out.add(
            SmartCard(
                id = "review|${s.hash}",
                kind = reviewKindFromString(s.kind),
                sourceLabel = sourceLabel,
                fact = fact,
                prep = null,
                actionLabel = actionLabel,
                sortMs = deadlineMs,
                deadlineMs = deadlineMs,
                action = action,
                sourceKey = "review|${s.hash}",
                appearFromMs = batchId,
                targetApp = targetApp
            )
        )
    }
    return out
}

/**
 * 复盘卡事实与规则卡事实是否同一件事（纯函数）：去空白/标点后相等、
 * 或一方包含另一方（较短一方 ≥6 字才认，避免“查看”这类短词误撞）。
 * 规则解析已出的账单/预约，复盘不再重复推一张——同事实只留规则卡。
 */
fun reviewFactMatchesRuleFact(reviewFact: String, ruleFact: String): Boolean {
    fun normalize(s: String): String = s.filter { it.isLetterOrDigit() }
    val a = normalize(reviewFact)
    val b = normalize(ruleFact)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val short = minOf(a.length, b.length)
    if (short < 6) return false
    return a.contains(b) || b.contains(a)
}

/**
 * 复盘判断的动作 → 可持久化的 SmartCardAction（纯函数）：
 * open_app＋有效包名（actionParam 优先、来源包名兜底）→ LaunchApp；
 * open_sms＋地址 → SmsThread；open_calendar → 打开原应用（日历通知的
 * 来源包本身就是日历应用）；其余（none / 模型乱写）→ 打开原应用
 * （来源包名），来源包名也没有时返回 null（这张卡不推，不硬凑一个动作）。
 */
fun reviewSuggestionAction(s: ReviewSuggestion): SmartCardAction? {
    return when (s.action) {
        "open_app" -> {
            val pkg = s.actionParam.ifEmpty { s.sourcePackage }
            if (pkg.isBlank()) null else SmartCardAction.LaunchApp(pkg)
        }
        "open_sms" -> {
            if (s.actionParam.isBlank()) null
            else SmartCardAction.SmsThread(s.actionParam)
        }
        "open_calendar" -> {
            // 日历包名不由模型定、也不硬编码：日历通知的来源包本身就是
            // 日历应用；来源包名没有时不推（不硬凑一个动作）。
            if (s.sourcePackage.isBlank()) null
            else SmartCardAction.LaunchApp(s.sourcePackage)
        }
        else -> {
            if (s.sourcePackage.isBlank()) null
            else SmartCardAction.LaunchApp(s.sourcePackage)
        }
    }
}

/**
 * 把模型的判断落到可执行的动作上（纯函数）：模型看不到包名与号码
 * （脱敏后），动作目标一律以消息来源为准——短信来源打开短信会话
 * （发件方地址在消息里，存在 packageName 字段），通知来源打开原应用
 * （包名在消息里）。模型的 action 只作方向参考，actionLabel（按钮文案）
 * 是模型真正的贡献。找不到原消息时原样返回（卡片转换时会因无动作丢弃）。
 */
fun resolveReviewAction(
    s: ReviewSuggestion,
    message: DailyReviewMessage?
): ReviewSuggestion {
    if (message == null) return s
    val sourceLabel = s.sourceLabel.ifEmpty { message.appName }
    return if (message.source == "sms") {
        s.copy(
            action = "open_sms",
            actionParam = message.packageName,
            sourcePackage = "",
            sourceLabel = sourceLabel
        )
    } else {
        s.copy(
            action = "open_app",
            actionParam = message.packageName,
            sourcePackage = message.packageName,
            sourceLabel = sourceLabel
        )
    }
}

/**
 * 通知收件箱（轻量持久化）：NotificationRepository 只存当前在用通知
 * （内存，最新 20 条），24 小时历史要另外记。LauncherNotificationService
 * 在 onNotificationPosted 里记一条（只记可清除的非进行中通知），
 * 复盘时按“自上次复盘以来”取。原文只存本地、不出仓库、不进日志。
 * SharedPreferences＋org.json，与现有缓存方式一致；全部 try/catch，
 * 读写失败静默（复盘少一条输入，不崩、不报错）。
 */
object ReviewInboxStore {
    private const val PREFS = "daily_review_inbox"
    private const val KEY_ITEMS = "items_v1"

    fun record(context: Context, message: DailyReviewMessage) {
        try {
            val items = loadAll(context).toMutableList()
            items.add(message)
            val now = System.currentTimeMillis()
            val kept = items
                .filter { now - it.timeMs <= REVIEW_WINDOW_MS * 2 }
                .sortedBy { it.timeMs }
                .takeLast(REVIEW_INBOX_MAX_ITEMS)
            saveAll(context, kept)
        } catch (_: Exception) {
            // 静默：收件箱记不上，复盘少一条输入，不崩
        }
    }

    /** 自 sinceMs 以来的消息（按时间升序）。 */
    fun loadSince(context: Context, sinceMs: Long): List<DailyReviewMessage> {
        return try {
            loadAll(context).filter { it.timeMs >= sinceMs }.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadAll(context: Context): List<DailyReviewMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        val out = mutableListOf<DailyReviewMessage>()
        for (i in 0 until array.length()) {
            val obj = try {
                array.getJSONObject(i)
            } catch (_: Exception) {
                continue
            }
            out.add(
                DailyReviewMessage(
                    packageName = obj.optString("pkg", ""),
                    appName = obj.optString("app", ""),
                    title = obj.optString("title", ""),
                    text = obj.optString("text", ""),
                    timeMs = obj.optLong("time", 0L),
                    source = obj.optString("source", "notification")
                )
            )
        }
        return out
    }

    private fun saveAll(context: Context, items: List<DailyReviewMessage>) {
        val array = JSONArray()
        for (m in items) {
            array.put(
                JSONObject().apply {
                    put("pkg", m.packageName)
                    put("app", m.appName)
                    put("title", m.title)
                    put("text", m.text)
                    put("time", m.timeMs)
                    put("source", m.source)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}

/**
 * 判断缓存（省调用的第一层）：每条消息按脱敏后内容哈希记一次，
 * 推/不推都记——判过的不再送模型。30 天过期、最多 500 条（超了丢最旧的）。
 */
object ReviewJudgmentStore {
    private const val PREFS = "daily_review_judgments"
    private const val PREFIX = "j_"

    fun save(context: Context, suggestion: ReviewSuggestion) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("hash", suggestion.hash)
                put("push", suggestion.push)
                put("kind", suggestion.kind)
                put("fact", suggestion.fact)
                put("actionLabel", suggestion.actionLabel)
                put("action", suggestion.action)
                put("actionParam", suggestion.actionParam)
                put("deadlineMs", suggestion.deadlineMs ?: -1L)
                put("sourceLabel", suggestion.sourceLabel)
                put("sourcePackage", suggestion.sourcePackage)
                put("savedMs", suggestion.savedMs)
            }
            val editor = prefs.edit().putString(PREFIX + suggestion.hash, json.toString())
            // 满了先清过期、再丢最旧的（按 savedMs 升序，腾出一个位置给新判断）
            val keys = prefs.all.keys.filter { it.startsWith(PREFIX) }
            if (keys.size >= REVIEW_JUDGMENT_MAX_ITEMS) {
                val now = System.currentTimeMillis()
                val withSaved = keys.map { key ->
                    val saved = try {
                        JSONObject(prefs.getString(key, "") ?: "").optLong("savedMs", 0L)
                    } catch (_: Exception) {
                        0L
                    }
                    key to saved
                }
                val expired = withSaved.filter { now - it.second > REVIEW_JUDGMENT_TTL_MS }
                for ((key, _) in expired) editor.remove(key)
                val remaining = keys.size - expired.size
                if (remaining >= REVIEW_JUDGMENT_MAX_ITEMS) {
                    val oldest = withSaved
                        .filter { (key, _) -> key !in expired.map { it.first } }
                        .sortedBy { it.second }
                        .take(remaining - REVIEW_JUDGMENT_MAX_ITEMS + 1)
                    for ((key, _) in oldest) editor.remove(key)
                }
            }
            editor.apply()
        } catch (_: Exception) {
            // 静默：缓存写不上，下次多调一次，不崩
        }
    }

    /** 判过返回判断（30 天内有效），没判过/过期返回 null。 */
    fun load(context: Context, hash: String): ReviewSuggestion? {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREFIX + hash, null) ?: return null
            val obj = JSONObject(raw)
            val savedMs = obj.optLong("savedMs", 0L)
            if (System.currentTimeMillis() - savedMs > REVIEW_JUDGMENT_TTL_MS) return null
            ReviewSuggestion(
                hash = obj.optString("hash", hash),
                push = obj.optBoolean("push", false),
                kind = obj.optString("kind", "NOTIFICATION"),
                fact = obj.optString("fact", ""),
                actionLabel = obj.optString("actionLabel", ""),
                action = obj.optString("action", "none"),
                actionParam = obj.optString("actionParam", ""),
                deadlineMs = obj.optLong("deadlineMs", -1L).takeIf { it > 0L },
                sourceLabel = obj.optString("sourceLabel", ""),
                sourcePackage = obj.optString("sourcePackage", ""),
                savedMs = savedMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun contains(context: Context, hash: String): Boolean = load(context, hash) != null
}

/**
 * Session 历史存储（完整会话，只追加的日志，第四十七批）：
 * 本地存完整的脱敏会话——每天发给模型的提示词原文（历史＋当天新消息）
 * ＋模型每轮的回复原文。每天复盘时原样取出、放在新消息之前一起发给
 * 模型，应用侧不摘要、不丢行、不截断：模型看到全部过去的事，不丢信息、
 * 不丢智能。token 成本不靠应用丢信息，靠 DeepSeek 的提示词缓存
 * （Context Caching，默认开启）：提示词按“稳定系统提示词在前、历史在中、
 * 新消息在末尾”排布，前缀字节级一致的部分按命中价（deepseek-flash 非高峰
 * $0.003/百万 token，是未命中 $0.15 的 1/50）计费，每天只有新消息那段
 * 按未命中价算。每次调用的命中／未命中 token 数见 LlmRouter 的日志。
 * 隐私：历史里只有脱敏内容——消息部分在渲染进提示词时已脱敏（见
 * [reviewUserPrompt]）；模型回复只见过脱敏输入，不可能复述出真实敏感信息，
 * 原文照存（不再脱敏，见 [reviewHistoryAppend]）。
 * SharedPreferences＋纯文本，与现有存储方式一致；全部 try/catch，
 * 读写失败静默（历史丢了，明天复盘没有上下文，不崩）。
 * 549 的 ReviewSessionStore（滚动摘要）已拆掉：本版起不再读写
 * "daily_review_session"，旧摘要数据留在设备上成为孤儿数据（约 1KB，
 * 不再读取、不删除）。
 */
object ReviewHistoryStore {
    private const val PREFS = "daily_review_history"
    private const val KEY_LOG = "log_v1"

    fun save(context: Context, log: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LOG, log)
                .apply()
        } catch (_: Exception) {
            // 静默：历史存不上，明天没有上下文，不崩
        }
    }

    /** 存过的完整历史；没有/读失败返回空串（调用方当作没有上下文）。 */
    fun load(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LOG, null).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}

/**
 * 复盘卡片存储（留存层）：晚上复盘出的卡存本地，进程死后仍留到上午；
 * 动作只存包名/短信地址/号码（LaunchApp/SmsThread/Dial），不存
 * PendingIntent（OpenNotification 那种动作复盘卡片不用，也存不下来）。
 * 下一次复盘出新卡时，上一批的卡整体替换（旧批过期摘、用户摘卡照常，
 * 仓库的 done 标记按卡片 id 走，不受替换影响）。
 */
object ReviewCardStore {
    private const val PREFS = "daily_review_cards"
    private const val KEY_CARDS = "cards_v1"

    fun save(context: Context, cards: List<SmartCard>) {
        try {
            val array = JSONArray()
            for (card in cards) {
                val actionJson = when (val a = card.action) {
                    is SmartCardAction.LaunchApp ->
                        JSONObject().put("type", "launch").put("param", a.packageName)
                    is SmartCardAction.SmsThread ->
                        JSONObject().put("type", "sms").put("param", a.address)
                    is SmartCardAction.Dial ->
                        JSONObject().put("type", "dial").put("param", a.number)
                    else -> null
                } ?: continue
                array.put(
                    JSONObject().apply {
                        put("id", card.id)
                        put("kind", card.kind.name)
                        put("sourceLabel", card.sourceLabel)
                        put("fact", card.fact)
                        put("actionLabel", card.actionLabel)
                        put("sortMs", card.sortMs)
                        put("deadlineMs", card.deadlineMs)
                        put("sourceKey", card.sourceKey)
                        put("appearFromMs", card.appearFromMs)
                        put("action", actionJson)
                        card.targetApp?.let {
                            put("targetPkg", it.packageName ?: "")
                            put("targetLabel", it.label)
                        }
                    }
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CARDS, array.toString()).apply()
        } catch (_: Exception) {
            // 静默：卡片存不上，复盘白跑一次，不崩
        }
    }

    fun load(context: Context): List<SmartCard> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CARDS, null) ?: return emptyList()
            val array = JSONArray(raw)
            val out = mutableListOf<SmartCard>()
            for (i in 0 until array.length()) {
                val obj = try {
                    array.getJSONObject(i)
                } catch (_: Exception) {
                    continue
                }
                val card = reviewCardFromJson(obj) ?: continue
                out.add(card)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun reviewCardFromJson(obj: JSONObject): SmartCard? {
        val id = obj.optString("id", "")
        if (id.isEmpty()) return null
        val kind = try {
            SmartCardKind.valueOf(obj.optString("kind", "NOTIFICATION"))
        } catch (_: Exception) {
            SmartCardKind.NOTIFICATION
        }
        val actionObj = try {
            obj.getJSONObject("action")
        } catch (_: Exception) {
            return null
        }
        val action: SmartCardAction = when (actionObj.optString("type", "")) {
            "launch" -> SmartCardAction.LaunchApp(actionObj.optString("param", ""))
            "sms" -> SmartCardAction.SmsThread(actionObj.optString("param", ""))
            "dial" -> SmartCardAction.Dial(actionObj.optString("param", ""))
            else -> return null
        }
        if (action is SmartCardAction.LaunchApp && action.packageName.isBlank()) return null
        val targetPkg = obj.optString("targetPkg", "")
        val targetLabel = obj.optString("targetLabel", "")
        return SmartCard(
            id = id,
            kind = kind,
            sourceLabel = obj.optString("sourceLabel", "通知"),
            fact = obj.optString("fact", ""),
            prep = null,
            actionLabel = obj.optString("actionLabel", "查看"),
            sortMs = obj.optLong("sortMs", 0L),
            deadlineMs = obj.optLong("deadlineMs", 0L),
            action = action,
            sourceKey = obj.optString("sourceKey", id),
            appearFromMs = obj.optLong("appearFromMs", 0L),
            targetApp = if (targetLabel.isEmpty()) null
            else TargetApp(targetPkg.ifEmpty { null }, targetLabel)
        )
    }
}
