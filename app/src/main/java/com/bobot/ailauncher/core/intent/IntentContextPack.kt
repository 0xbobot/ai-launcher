package com.bobot.ailauncher.core.intent

/**
* planner 上下文包（意图改进二批）：把「七仔已经知道、但模型之前完全不知道」的
* 用户现状压成一段紧凑文本，随每次 plan 注入 prompt。纯数据 + 纯拼装，可单测；
* 生产侧的采集（日历/天气缓存/使用统计/通知）在 [IntentContextProvider]。
*
* 设计纪律：
* - 只给事实，不给指令；每项一行、带，方便模型引用、用户事后可核对。
* - 总长硬上限 [MAX_CHARS]：优先保时间/会议/天气/常用，已装名录最长、最先被截断，
* 截断时注明总数（「共 N 个，其余略」），不静默丢。
* - 缺失项整行省略，绝不编造（无缓存天气就是不写天气行）。
*/
data class IntentContextSnapshot(
/** "2026年10月21日 星期三 22:20（晚上）"，由采集侧格式化好 */
val nowText: String,
/** "23:00 展台方案评审 · 3F 会议室"，无下一会议为 null */
val nextMeeting: String? = null,
/** "深圳 晴 28°（今日 24~30°）"，无缓存为 null */
val weather: String? = null,
/** 常用应用名（已按频次排好，最多给 5 个由采集侧控制） */
val topApps: List<String> = emptyList(),
/** 已装可启动应用名录（精简版在拼装时按预算截断） */
val installedApps: List<String> = emptyList(),
/** 待回消息数，无现成数据为 null，0 条也省略 */
val pendingReplies: Int? = null
)

object IntentContextPack {

/** 总长上限（字符）：planner 每次调用都带，防 token 膨胀与延迟恶化 */
const val MAX_CHARS = 700

fun build(snapshot: IntentContextSnapshot): String {
val fixed = mutableListOf<String>()
fixed += "【现在】${snapshot.nowText}"
snapshot.nextMeeting?.takeIf { it.isNotBlank()}?.let { fixed += "【下一会议】$it" }
snapshot.weather?.takeIf { it.isNotBlank()}?.let { fixed += "【天气】$it" }
if (snapshot.topApps.isNotEmpty()) {
fixed += "【常用应用】${snapshot.topApps.joinToString("、")}"
}
snapshot.pendingReplies?.takeIf { it > 0}?.let { fixed += "【待回消息】$it 条" }

val result = StringBuilder(fixed.joinToString("\n"))

// 已装名录：用剩余预算逐个塞，塞不下就截断并注明总数
if (snapshot.installedApps.isNotEmpty()) {
val prefix = "【已装应用】"
val total = snapshot.installedApps.size
val kept = mutableListOf<String>()
var used = result.length + 1 + prefix.length // 换行 + 标签
for (name in snapshot.installedApps) {
// 每个名字额外算 1 个顿号位；被截断时还要留「…（共N个，其余略）」的尾巴
val tailReserve = if (kept.size < total) "…（共${total}个，其余略）".length else 0
val cost = name.length + if (kept.isEmpty()) 0 else 1
if (used + cost + tailReserve > MAX_CHARS) break
kept += name
used += cost
}
if (kept.isNotEmpty()) {
result.append('\n').append(prefix).append(kept.joinToString("、"))
if (kept.size < total) result.append("…（共${total}个，其余略）")
}
}
return result.toString()
}
}
