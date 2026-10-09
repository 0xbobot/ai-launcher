package com.bobot.ailauncher.data

/**
* v0.59.0：AI 附加价值（规则+启发式，端侧实现，不用 LLM）。
*
* 设计原则：
* - 所有判断都是可解释的规则，用户能理解"为什么"
* - 误判成本低：宁可少标，不可错标（"需回复"标错了会打扰用户）
* - 全部端侧运行，不上传任何数据
*/
object AiInsight {

// ============ AI-1：要不要回 ============

/** 行动词：出现这些词的消息很可能需要用户回复/处理 */
private val ACTION_WORDS = listOf(
"记得", "尽快", "麻烦", "请", "需要", "确认",
"@你", "@所有人", "回复", "处理", "审批", "签字",
"deadline", "ASAP", "pls", "please"
)

/** 纯通知词：只有这些词、没有行动词的消息通常可以忽略 */
private val PURE_NOTIFY_WORDS = listOf(
"已更新", "通知", "提醒", "同步完成", "备份完成",
"下载完成", "上传完成", "发布成功"
)

/**
* AI-1：判断消息是否需要回复。
* 规则：
* 1. 含行动词 → 需要回复（needsReply=true）
* 2. 只含纯通知词、无行动词 → 可忽略（canIgnore=true）
* 3. 其他 → 中性（都不标记）
*/
fun analyzeReplyNeed(title: String, text: String): ReplyAnalysis {
val combined = "$title $text"
val hasAction = ACTION_WORDS.any { combined.contains(it)}
val hasPureNotify = PURE_NOTIFY_WORDS.any { combined.contains(it)}

return when {
hasAction -> ReplyAnalysis(needsReply = true, canIgnore = false)
hasPureNotify -> ReplyAnalysis(needsReply = false, canIgnore = true)
else -> ReplyAnalysis(needsReply = false, canIgnore = false)
}
}

data class ReplyAnalysis(
val needsReply: Boolean,
val canIgnore: Boolean
)

// ============ AI-2：消息关联日历 ============

/**
* AI-2：消息是否和某个日历事件相关。
* 规则：分词后取消息中的 2+ 字关键词，与日历标题做交集。
* 为避免误判，要求至少命中 1 个 3+ 字的关键词，或 2 个 2 字关键词。
*
* @param msgTitle 消息标题
* @param msgText 消息正文
* @param eventTitle 日历事件标题
* @return 匹配到的关键词，null 表示不相关
*/
fun findCalendarLink(
msgTitle: String,
msgText: String,
eventTitle: String
): String? {
if (eventTitle.isBlank()) return null
val combined = "$msgTitle $msgText"

// 提取日历标题中的关键词（2+ 字，过滤常见虚词）
val stopWords = setOf("会议", "讨论", "评审", "同步", "沟通", "的", "和", "与")
val keywords = extractKeywords(eventTitle)
.filter { it.length >= 2 && it!in stopWords}

// 找消息中命中的关键词
val hits = keywords.filter { combined.contains(it)}

// 判定：1 个 3+ 字词，或 2 个 2 字词
val strongHit = hits.firstOrNull { it.length >= 3}
return when {
strongHit!= null -> strongHit
hits.size >= 2 -> hits.take(2).joinToString("、")
else -> null
}
}

/**
* 简单中文分词：按 2-4 字滑动窗口提取候选词。
* 这是启发式分词，不求完美，只求够用。
*/
private fun extractKeywords(text: String): List<String> {
val cleaned = text.replace(Regex("[\\s\\p{Punct}，。！？；：、（）]"), "")
val result = mutableSetOf<String>()
// 提取 2-4 字的所有子串作为候选
for (len in 2..4) {
for (i in 0..cleaned.length - len) {
val word = cleaned.substring(i, i + len)
// 过滤纯数字和纯英文单字母
if (!word.matches(Regex("^[0-9a-zA-Z]+$")) || word.length >= 3) {
result.add(word)
}
}
}
return result.toList()
}

// ============ AI-3：待办提取 ============

/** 时间词：出现这些词 + 行动词 → 很可能是待办 */
private val TIME_WORDS = listOf(
"明天", "后天", "今天", "周一", "周二", "周三", "周四", "周五",
"周六", "周日", "下周", "本月", "月底", "截止", "之前", "前",
"deadline", "due"
)

data class TodoItem(
val id: String, // 消息 id，用于去重
val source: String, // 来源：应用名
val action: String, // 行动：提取的待办内容
val deadline: String, // 截止时间词
val time: Long // 消息时间
)

/**
* AI-3：从消息提取待办。
* 规则：同时含时间词和行动词 → 待办。
* 行动提取：取行动词前后各 8 字作为待办描述。
*/
fun extractTodo(
id: String,
appName: String,
title: String,
text: String,
time: Long
): TodoItem? {
val combined = "$title $text"

// 找时间词
val timeWord = TIME_WORDS.firstOrNull { combined.contains(it)}
?: return null

// 找行动词
val actionWord = ACTION_WORDS.firstOrNull { combined.contains(it)}
?: return null

// 提取行动描述：行动词前后各取 8 字
val actionIdx = combined.indexOf(actionWord)
val start = maxOf(0, actionIdx - 8)
val end = minOf(combined.length, actionIdx + actionWord.length + 8)
var action = combined.substring(start, end).trim()
// 清理标点
action = action.replace(Regex("^[\\p{Punct}，。！？；：、]+"), "")
.replace(Regex("[\\p{Punct}，。！？；：、]+$"), "")
.take(20)

if (action.isBlank()) return null

return TodoItem(
id = id,
source = appName,
action = action,
deadline = timeWord,
time = time
)
}

// ============ AI-4：群聊折叠 ============

/**
* AI-4：判断一组同群聊消息是否应该折叠。
* 规则（必须同时满足）：
* 1. 同一群聊（同 appName + 同 title）
* 2. 3 小时内超过 5 条
* 3. 没有任何一条含 @ 或行动词
*
* @param items 同群聊的消息列表（已按时间排序）
* @return true=应该折叠
*/
fun shouldFoldGroup(items: List<FoldCandidate>): Boolean {
if (items.size <= 5) return false

// 检查是否有 @ 或行动词
val hasImportant = items.any { item ->
val combined = "${item.title} ${item.text}"
combined.contains("@") || ACTION_WORDS.any { combined.contains(it)}
}

return!hasImportant
}

data class FoldCandidate(
val title: String,
val text: String
)

// ============ v0.63.0：AI 简报总结句 ============

/**
* v0.63.0：生成 TODAY 卡片的 AI 总结标题。
* 纯规则拼接，不调 LLM。
* 例："2 条要回，1 个会要开" / "3 条要回" / "1 个会要开" / "今日无事"
*/
fun buildSummary(replyCount: Int, meetingCount: Int): String {
return when {
replyCount > 0 && meetingCount > 0 -> "$replyCount 条要回，$meetingCount 个会要开"
replyCount > 0 -> "$replyCount 条要回"
meetingCount > 0 -> "$meetingCount 个会要开"
else -> "今日无事"
}
}
}
