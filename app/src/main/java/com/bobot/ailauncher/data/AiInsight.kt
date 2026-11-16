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
/**
* v0.64.0 收紧：必须明确指向用户才标需回复。
* 规则：
* 1. 含"@你"/"@所有人" → 需要回复
* 2. 含"你" + 行动词（记得/确认/回复/看一下/处理…）→ 需要回复
* 3. 纯陈述句（"拖了两天"这类状态同步，无指向用户的动词）→ 不标
* 4. 只含纯通知词 → 可忽略
*/
private val YOU_ACTION_WORDS = listOf(
"记得", "确认", "回复", "看一下", "处理", "尽快", "麻烦",
"查收", "过目", "审批", "签字", "反馈"
)

fun analyzeReplyNeed(title: String, text: String): ReplyAnalysis {
val combined = "$title $text"
// 1. @直接点名
if (combined.contains("@你") || combined.contains("@所有人")) {
return ReplyAnalysis(needsReply = true, canIgnore = false)
}
// 2. "你" + 行动词（明确指向用户）
val hasYou = combined.contains("你")
val hasYouAction = YOU_ACTION_WORDS.any { combined.contains(it) }
if (hasYou && hasYouAction) {
return ReplyAnalysis(needsReply = true, canIgnore = false)
}
// 3. 纯通知词 → 可忽略
val hasPureNotify = PURE_NOTIFY_WORDS.any { combined.contains(it)}
if (hasPureNotify) {
return ReplyAnalysis(needsReply = false, canIgnore = true)
}
// 4. 其他 → 中性（不标，宁可少标不可错标）
return ReplyAnalysis(needsReply = false, canIgnore = false)
}

data class ReplyAnalysis(
val needsReply: Boolean,
val canIgnore: Boolean
)

// ============ v0.63.0：AI 简报总结句 ============

/**
* v0.63.0：生成 TODAY 卡片的 AI 总结标题。
* 纯规则拼接，不调 LLM。
* 例："2 条消息要回，1 个会要开" / "3 条消息要回" / "1 个会要开" / "难得清闲"
*/
fun buildSummary(replyCount: Int, meetingCount: Int): String {
return when {
replyCount > 0 && meetingCount > 0 -> "$replyCount 条消息要回，$meetingCount 个会要开"
replyCount > 0 -> "$replyCount 条消息要回"
meetingCount > 0 -> "$meetingCount 个会要开"
else -> "难得清闲"
}
}
}
