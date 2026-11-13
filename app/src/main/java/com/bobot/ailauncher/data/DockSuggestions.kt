package com.bobot.ailauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 大模型建议第一批（Bob 2026-11-13 23:18 亲定「按改后的方案做第一批」，
 * 方案见 ~/workspace/ai-launcher/ac-app-llm-suggestions-proposal.md）。
 *
 * 只做两类（提案 §2、§5）：① 固定 / 取消固定某个应用到 Dock（采纳走
 * 现有 UiPrefs 固定位机制，立即重排）；③ 规则引擎说不出的模式（只有
 * 「知道了」，不改任何东西）。② 时段沉降（周末沉降标记）是第二批，不
 * 在这批做——模型输出 sink 一律由复核门丢弃。
 *
 * 触发（提案 §3，Bob 2026-11-13 22:25 定）：说明页底部「建议」区上方
 * 一个按钮，用户主动点；不自动跑、不新增后台、不新增权限。点后本地预
 * 筛候选 → 没候选不调用 → 有候选调一次模型 → 复核数字后展示。无 Key
 * 时按钮不出现（静默，不引导配 Key）。
 *
 * 数据（提案 §1）：只喂 AppUsageTracker 已有的使用统计（时段格子、转移
 * 对 + 占比、近 7 天次数、距上次打开天数、Dock 状态、前 10 进出变化）；
 * 不喂通知正文、消息、日历、联系人、位置、任何应用内容。调用链复用
 * LlmRouter.postChatCompletions + 用户已配的 Key（默认 DeepSeek
 * deepseek-flash），不新增供应商与 Key 存储。
 *
 * 反幻觉门（提案 §6，核心护栏）：模型只许输出固定 schema 的 JSON；
 * 展示前逐条用快照读数复核每一个数字——对不上、引用快照之外的应用、
 * 动作类型未知、JSON 解析失败，一律整条/整轮丢弃，不展示半轮结果。
 * 复核以“调模型那一刻的快照”为基准（不是复核时重读），模型只能按
 * 它看到的数字被评判——调用期间用户刚开了一个应用导致数字变了，
 * 那不是模型的错。
 *
 * 与提案的两处有意偏差（原因写在这里，收据里也记）：
 * 1. payload 不含 slot[4] 四槽计数：小时格子（hwDayType）比槽更细，
 *    槽是格子的粗聚合，模型不需要两份同一信息。
 * 2. 建议的去重 id 不用模型给的 id：按证据内容本地算 dedupeKey
 *    （kind + 动作 + 证据排序拼串的 SHA-256 前 16 位），模型给的 id
 *    只作展示用——去重键不能信任模型输出。
 */

// ---------- 模型 ----------

enum class SuggestionKind { PIN, UNPIN, OBSERVE }

enum class SuggestionState { NEW, APPLIED, DISMISSED }

/** 证据项（提案附录 schema 的 evidence）：metric 决定用哪些字段。 */
data class SuggestionEvidence(
    val metric: String, // hwDayType | total7 | total | pinned | transfer | transferDominant | lastUsedDays | dockRank
    val pkg: String,
    val fromPkg: String? = null, // transfer / transferDominant：发起方包名
    val dayType: String? = null, // hwDayType：weekday | saturday | sunday
    val hour: Int? = null, // hwDayType：0–23
    val intValue: Int? = null,
    val boolValue: Boolean? = null,
)

data class SuggestionAction(val type: String, val pkg: String) // type: pin | unpin | observe

data class DockSuggestion(
    val id: String, // 模型给的 id（展示用）；去重另算 dedupeKey
    val kind: SuggestionKind,
    val title: String,
    val body: String,
    val evidence: List<SuggestionEvidence>,
    val action: SuggestionAction,
    val dedupeKey: String, // 本地算（见 dockSuggestionDedupeKey）
    val state: SuggestionState = SuggestionState.NEW,
    val appliedAtMs: Long = 0L, // APPLIED 的采纳时间（撤销窗口 7 天用）
)

/** 流入转移（快照用）：发起方包名、次数、是否占主导。 */
data class TransferIn(val fromPkg: String, val count: Int, val dominant: Boolean)

/** 单应用的使用统计快照（喂模型 + 复核的同一份基准）。 */
data class AppStats(
    val pkg: String,
    val label: String,
    /** 日类型小时格子（非零）："weekday-21" -> 5。 */
    val hw: Map<String, Int>,
    val total7: Int,
    val total: Int,
    /** 距上次打开的天数；-1 = 无记录。 */
    val lastUsedDays: Int,
    val pinned: Boolean,
    /** top10 内名次（0 起），不在为 null。 */
    val dockRank: Int?,
    /** 流入转移（按次数取前 5）。 */
    val transfersIn: List<TransferIn>,
    /** 该应用作为发起方的转移总次数 / 去向数。 */
    val transfersOutTotal: Int,
    val transfersOutTargets: Int,
)

/** 本次分析用的快照：候选应用（上限 20）+ 当前 Dock 前 10 + 当前钟点。 */
data class UsageSnapshot(
    val apps: List<AppStats>,
    val top10: List<String>,
    val dow: Int,
    val hour: Int,
    val dayType: String, // weekday | saturday | sunday（当前）
) {
    fun app(pkg: String): AppStats? = apps.firstOrNull { it.pkg == pkg }
}

/** 本地预筛结果：hints 进 payload 的 watch 字段（给模型看的关注点）。 */
data class Nomination(val hints: List<String>, val kinds: Set<SuggestionKind>) {
    val hasCandidates: Boolean get() = hints.isNotEmpty()
}

// ---------- 常量 ----------

/** 候选应用上限（payload 体积控制：十几个应用 × 稀疏字段，几 KB）。 */
const val SUGGESTION_MAX_APPS = 12

/** 模型一次最多给的建议条数（多了不读，复核后取前 3 条）。 */
const val SUGGESTION_MAX_ITEMS = 3

/** 固定位 14 天以上没开 → 取消固定候选（提案 §2 例：稀缺位置别浪费）。 */
const val UNPIN_STALE_DAYS = 14

/** 枢纽假象判定：同一发起方占主导的去向 ≥3 个 → 枢纽观察候选。 */
const val HUB_MIN_TARGETS = 3

/** 采纳后撤销窗口（提案 §5）：7 天。 */
const val SUGGESTION_UNDO_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/** 建议反馈日志文件名（files 目录，环形上限 200 条，仿照 UnreadJumpLog）。 */
const val SUGGESTION_FEEDBACK_LOG = "dock-suggestion-feedback.jsonl"

// ---------- 系统提示词 ----------

val SUGGESTIONS_SYSTEM_PROMPT = """
你是手机桌面助手的 Dock 建议助手。你会收到一台手机的使用统计 JSON（只有计数，没有任何内容）与关注点列表。
任务：从中挑出最多 3 条值得用户知道的建议。
只返回 JSON，不要任何其他文字，格式如下：
{"suggestions":[{"id":"pin_<包名>","kind":"pin","title":"一句话标题","body":"一句话理由，带数字","evidence":[{"metric":"hwDayType","pkg":"<包名>","dayType":"weekday","hour":21,"value":5}],"action":{"type":"pin","pkg":"<包名>"}}]}
规则：
- kind 只能是 pin（固定到 Dock）、unpin（取消固定）、observe（只说明一个模式，不改东西）；action.type 必须与 kind 一致。
- pin 只给未固定的应用，unpin 只给已固定的应用；observe 不改东西。
- evidence 的 metric 只能是 hwDayType、total7、total、pinned、transfer、transferDominant、lastUsedDays、dockRank；数值必须照抄输入 JSON 里的数，不许改、不许编；每条建议至少 1 条 evidence。
- 只引用输入里出现过的应用与数字；输入里没有的不要写。
- title 不超过 20 字，body 不超过 40 字；每条建议都要让人一眼看懂“为什么”。
""".trimIndent()

// ---------- 本地预筛（纯函数） ----------

/** 日类型名（提案 §1 payload 口径；与 AppUsageTracker.dayTypeHwSum 的分法一致）。 */
fun dayTypeName(dow: Int): String = when (dow) {
    6 -> "saturday"
    7 -> "sunday"
    else -> "weekday"
}

/**
 * 本地预筛（纯函数）：本地规则提名，模型只做判断（提案 §3）。
 * standing 规则（不依赖基线，首次运行也有候选）：固定（时段桶 ≥3 且未
 * 固定）、取消固定（固定位 14 天以上没开）、周末分化、枢纽假象。
 * 新发规则（依赖上次快照，无基线时跳过）：时段桶本周新过 ≥3、转移
 * 新占主导、进出 Dock 前 10。被 mute 的种类对应的规则不进候选；
 * 已忽略/已采纳的固定/取消固定（按包名）不重复提名。
 */
fun nominateCandidates(
    snap: UsageSnapshot,
    prevSnap: UsageSnapshot?,
    prevTop10: List<String>?,
    mutedKinds: Set<SuggestionKind>,
    suppressedPinPkgs: Set<String>,
    suppressedUnpinPkgs: Set<String>,
): Nomination {
    val hints = mutableListOf<String>()
    val kinds = mutableSetOf<SuggestionKind>()
    val pinMuted = SuggestionKind.PIN in mutedKinds
    val unpinMuted = SuggestionKind.UNPIN in mutedKinds
    val observeMuted = SuggestionKind.OBSERVE in mutedKinds

    fun bestHwCell(app: AppStats): Pair<String, Int>? =
        app.hw.maxByOrNull { it.value }?.toPair()

    for (app in snap.apps) {
        // ① 固定：时段桶 ≥3 且未固定、近 7 天开过。已有“本周新过”时
        // 标 pin-new，否则标 pin（ standing 模式，第一轮就有候选）。
        if (!pinMuted && !app.pinned && app.pkg !in suppressedPinPkgs && app.total7 > 0) {
            val best = bestHwCell(app)
            if (best != null && best.second >= 3) {
                val prevBest = prevSnap?.app(app.pkg)?.hw?.get(best.first) ?: 0
                if (prevSnap != null && prevBest < 3) {
                    hints += "pin-new pkg=${app.pkg} hw=${best.first}:${best.second} prev=$prevBest total7=${app.total7}"
                } else {
                    hints += "pin pkg=${app.pkg} hw=${best.first}:${best.second} total7=${app.total7}"
                }
                kinds += SuggestionKind.PIN
            }
        }
        // ① 取消固定：固定位 14 天以上没开。
        if (!unpinMuted && app.pinned && app.pkg !in suppressedUnpinPkgs &&
            app.lastUsedDays >= UNPIN_STALE_DAYS
        ) {
            hints += "unpin pkg=${app.pkg} lastUsedDays=${app.lastUsedDays}"
            kinds += SuggestionKind.UNPIN
        }
        // ②（第一批只作模式观察，不做沉降）：周末分化——周末全天 0 次、
        // 工作日有 ≥3 的格子，或反过来。
        if (!observeMuted) {
            val weekendTotal = app.hw.filterKeys { it.startsWith("saturday-") || it.startsWith("sunday-") }
                .values.sum()
            val weekdayMax = app.hw.filterKeys { it.startsWith("weekday-") }.values.maxOrNull() ?: 0
            val weekendMax = app.hw.filterKeys { it.startsWith("saturday-") || it.startsWith("sunday-") }
                .values.maxOrNull() ?: 0
            val weekdayTotal = app.hw.filterKeys { it.startsWith("weekday-") }.values.sum()
            if (weekendTotal == 0 && weekdayMax >= 3) {
                hints += "split pkg=${app.pkg} weekend=0 weekdayMax=$weekdayMax"
                kinds += SuggestionKind.OBSERVE
            } else if (weekdayTotal == 0 && weekendMax >= 3) {
                hints += "split pkg=${app.pkg} weekday=0 weekendMax=$weekendMax"
                kinds += SuggestionKind.OBSERVE
            }
        }
        // ③ 转移新占主导（依赖基线）：某对转移从不占主导变占主导。
        if (!observeMuted && prevSnap != null) {
            val prevApp = prevSnap.app(app.pkg)
            for (tin in app.transfersIn) {
                if (!tin.dominant) continue
                val prevDominant = prevApp?.transfersIn
                    ?.firstOrNull { it.fromPkg == tin.fromPkg }?.dominant ?: false
                if (!prevDominant) {
                    val share = if (app.total > 0) tin.count * 100 / app.total else 0
                    hints += "transfer-new from=${tin.fromPkg} to=${app.pkg} count=${tin.count} share=$share%"
                    kinds += SuggestionKind.OBSERVE
                }
            }
        }
    }
    // ③ 枢纽假象：同一发起方占主导的去向 ≥3 个（不依赖基线，第一轮就能发现）。
    if (!observeMuted) {
        val byFrom = mutableMapOf<String, MutableList<String>>()
        for (app in snap.apps) {
            for (tin in app.transfersIn) {
                if (tin.dominant) {
                    byFrom.getOrPut(tin.fromPkg) { mutableListOf() } += app.pkg
                }
            }
        }
        for ((from, targets) in byFrom) {
            if (targets.size >= HUB_MIN_TARGETS) {
                val fromStats = snap.app(from)
                hints += "hub from=$from targets=${targets.take(5).joinToString(",")} " +
                    "outTotal=${fromStats?.transfersOutTotal ?: 0}"
                kinds += SuggestionKind.OBSERVE
            }
        }
    }
    // ③ 进出 Dock 前 10（依赖基线）。
    if (!observeMuted && prevTop10 != null) {
        val prevSet = prevTop10.toSet()
        val curSet = snap.top10.toSet()
        for (pkg in snap.top10) {
            if (pkg !in prevSet) {
                hints += "top10-in pkg=$pkg"
                kinds += SuggestionKind.OBSERVE
            }
        }
        for (pkg in prevTop10) {
            if (pkg !in curSet) {
                hints += "top10-out pkg=$pkg"
                kinds += SuggestionKind.OBSERVE
            }
        }
    }
    return Nomination(hints, kinds)
}

// ---------- payload（纯函数，确定性拼串） ----------

/** JSON 字符串转义（只转义 payload 里会出现的 label/包名，用 JSONObject.quote）。 */
private fun jsonStr(s: String): String = JSONObject.quote(s)

/**
 * payload 规范 JSON（纯函数）：确定性拼串，两次构建结果相同——
 * 候选未变判定（fingerprint）靠它。includeNow=false 时不含 now 字段，
 * 指纹只比数据部分（钟点本身不算“数据变了”）。
 */
fun buildPayloadJson(snap: UsageSnapshot, hints: List<String>, includeNow: Boolean = true): String {
    val sb = StringBuilder()
    sb.append("{")
    if (includeNow) {
        sb.append("\"now\":{\"dow\":${snap.dow},\"hour\":${snap.hour},")
        sb.append("\"dayType\":${jsonStr(snap.dayType)}},")
    }
    sb.append("\"top10\":[")
    sb.append(snap.top10.joinToString(",") { jsonStr(it) })
    sb.append("],\"pinned\":[")
    val pinned = snap.apps.filter { it.pinned }.map { it.pkg }
    sb.append(pinned.joinToString(",") { jsonStr(it) })
    sb.append("],\"watch\":[")
    sb.append(hints.joinToString(",") { jsonStr(it) })
    sb.append("],\"apps\":[")
    // 排序：top10 名次优先，其次固定位，其次近 7 天次数；上限 12 个。
    val rank = snap.top10.withIndex().associate { it.value to it.index }
    val ordered = snap.apps.sortedWith(
        compareBy<AppStats> { rank[it.pkg] ?: Int.MAX_VALUE }
            .thenByDescending { if (it.pinned) 1 else 0 }
            .thenByDescending { it.total7 }
    ).take(SUGGESTION_MAX_APPS)
    ordered.joinTo(sb, ",") { app ->
        buildString {
            append("{\"pkg\":${jsonStr(app.pkg)},")
            append("\"label\":${jsonStr(app.label)},")
            append("\"hw\":{")
            append(app.hw.entries.sortedBy { it.key }
                .joinToString(",") { (k, v) -> "${jsonStr(k)}:$v" })
            append("},")
            append("\"total7\":${app.total7},\"total\":${app.total},")
            append("\"lastUsedDays\":${app.lastUsedDays},")
            append("\"pinned\":${app.pinned},")
            append("\"dockRank\":${app.dockRank ?: "null"},")
            append("\"transfersIn\":[")
            append(app.transfersIn.joinToString(",") { tin ->
                "{\"from\":${jsonStr(tin.fromPkg)},\"count\":${tin.count}," +
                    "\"dominant\":${tin.dominant}}"
            })
            append("],")
            append("\"transfersOut\":{\"total\":${app.transfersOutTotal},")
            append("\"targets\":${app.transfersOutTargets}}}")
        }
    }
    sb.append("]}")
    return sb.toString()
}

/** 候选指纹：payload 数据部分（不含 now）的 SHA-256 hex。 */
fun payloadFingerprint(snap: UsageSnapshot, hints: List<String>): String {
    val bytes = buildPayloadJson(snap, hints, includeNow = false).toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

// ---------- 解析（纯函数） ----------

/** 模型返回的原始建议（解析层，不含本地校验）。 */
data class ParsedSuggestion(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val evidence: List<SuggestionEvidence>,
    val action: SuggestionAction,
)

/** 从 /chat/completions 响应体里取 choices[0].message.content（纯函数）。 */
fun extractModelContent(rawBody: String): String? {
    return try {
        JSONObject(rawBody)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    } catch (_: Exception) {
        null
    }
}

/** 去 markdown 代码块围栏（与 LlmRouter.parseResult 同款清洗）。 */
fun stripCodeFence(content: String): String {
    return content.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
}

/**
 * 解析模型返回的建议 JSON（纯函数）：顶层解析失败 → null（整轮丢弃）；
 * 单条结构坏 → 跳过该条；suggestions 缺失 → 空列表。
 */
fun parseSuggestionsResponse(rawBody: String): List<ParsedSuggestion>? {
    val content = extractModelContent(rawBody) ?: return null
    val clean = stripCodeFence(content)
    val arr = try {
        JSONObject(clean).optJSONArray("suggestions") ?: return emptyList()
    } catch (_: Exception) {
        return null
    }
    val out = mutableListOf<ParsedSuggestion>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val evidence = mutableListOf<SuggestionEvidence>()
        val evArr = o.optJSONArray("evidence")
        if (evArr != null) {
            for (j in 0 until evArr.length()) {
                val e = evArr.optJSONObject(j) ?: continue
                evidence += SuggestionEvidence(
                    metric = e.optString("metric", ""),
                    pkg = e.optString("pkg", ""),
                    fromPkg = e.optString("from", "").ifBlank { null }
                        ?: e.optString("fromPkg", "").ifBlank { null },
                    dayType = e.optString("dayType", "").ifBlank { null },
                    hour = if (e.has("hour")) e.optInt("hour") else null,
                    intValue = if (e.has("value") && e.opt("value") is Number) e.optInt("value") else null,
                    boolValue = if (e.has("value") && e.opt("value") is Boolean) e.optBoolean("value") else null,
                )
            }
        }
        val actionObj = o.optJSONObject("action")
        out += ParsedSuggestion(
            id = o.optString("id", ""),
            kind = o.optString("kind", ""),
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            evidence = evidence,
            action = SuggestionAction(
                type = actionObj?.optString("type", "") ?: "",
                pkg = actionObj?.optString("pkg", "") ?: "",
            ),
        )
    }
    return out
}

// ---------- 复核门（纯函数，核心护栏） ----------

/** 去重键：kind + 动作 + 证据排序拼串的 SHA-256 前 16 位（本地算，不信任模型给的 id）。 */
fun dockSuggestionDedupeKey(
    kind: SuggestionKind,
    action: SuggestionAction,
    evidence: List<SuggestionEvidence>,
): String {
    val canon = buildString {
        append(kind.name).append("|").append(action.type).append("|").append(action.pkg)
        evidence.sortedWith(
            compareBy<SuggestionEvidence> { it.metric }
                .thenBy { it.pkg }.thenBy { it.fromPkg ?: "" }
                .thenBy { it.dayType ?: "" }.thenBy { it.hour ?: -1 }
        ).forEach { e ->
            append("|").append(e.metric).append(":").append(e.pkg)
            append(":").append(e.fromPkg ?: "").append(":").append(e.dayType ?: "")
            append(":").append(e.hour ?: "").append(":")
            append(e.intValue?.toString() ?: "").append(":")
            append(e.boolValue?.toString() ?: "")
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canon.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(16)
}

/**
 * 复核门（纯函数）：逐条用快照读数复核模型引用的每一个数字。
 * 任一证据对不上 / 引用快照外的应用 / 动作类型未知 / kind 与动作不一致 /
 * 固定状态与事实不符 / 种类被 mute / 去重键已忽略 → 整条丢弃。
 * 通过的按模型顺序取前 [SUGGESTION_MAX_ITEMS] 条。
 */
fun verifySuggestions(
    parsed: List<ParsedSuggestion>,
    snap: UsageSnapshot,
    mutedKinds: Set<SuggestionKind>,
    ignoredKeys: Set<String>,
): List<DockSuggestion> {
    val out = mutableListOf<DockSuggestion>()
    for ((index, p) in parsed.withIndex()) {
        val kind = when (p.kind) {
            "pin" -> SuggestionKind.PIN
            "unpin" -> SuggestionKind.UNPIN
            "observe" -> SuggestionKind.OBSERVE
            else -> null
        } ?: continue
        if (kind in mutedKinds) continue
        if (p.title.isBlank() || p.body.isBlank()) continue
        if (p.evidence.isEmpty()) continue
        // 动作类型必须与 kind 一致；pin/unpin 的包必须在快照里；observe 的包
        // 为空或在快照里（模式可跨多个应用，只以 evidence 为准）。
        if (p.action.type != p.kind) continue
        val actionPkg = p.action.pkg
        val actionApp = if (actionPkg.isEmpty()) null else snap.app(actionPkg)
        if (kind != SuggestionKind.OBSERVE && actionApp == null) continue
        if (kind == SuggestionKind.OBSERVE && actionPkg.isNotEmpty() && actionApp == null) continue
        // 固定状态与事实一致：pin 只给未固定的，unpin 只给已固定的。
        if (kind == SuggestionKind.PIN && actionApp?.pinned != false) continue
        if (kind == SuggestionKind.UNPIN && actionApp?.pinned != true) continue
        // 逐条复核证据数字（以快照为基准）。
        var ok = true
        for (e in p.evidence) {
            val app = snap.app(e.pkg)
            if (app == null) {
                ok = false
                break
            }
            val match = when (e.metric) {
                "hwDayType" -> e.dayType != null && e.hour != null && e.intValue != null &&
                    (app.hw["${e.dayType}-${e.hour}"] ?: 0) == e.intValue
                "total7" -> e.intValue != null && app.total7 == e.intValue
                "total" -> e.intValue != null && app.total == e.intValue
                "pinned" -> e.boolValue != null && app.pinned == e.boolValue
                "transfer" -> e.fromPkg != null && e.intValue != null &&
                    app.transfersIn.firstOrNull { it.fromPkg == e.fromPkg }?.count == e.intValue
                "transferDominant" -> e.fromPkg != null && e.boolValue != null &&
                    app.transfersIn.firstOrNull { it.fromPkg == e.fromPkg }?.dominant == e.boolValue
                "lastUsedDays" -> e.intValue != null && app.lastUsedDays >= 0 &&
                    app.lastUsedDays == e.intValue
                "dockRank" -> e.intValue != null && app.dockRank == e.intValue
                else -> false
            }
            if (!match) {
                ok = false
                break
            }
        }
        if (!ok) continue
        val dedupeKey = dockSuggestionDedupeKey(kind, p.action, p.evidence)
        if (dedupeKey in ignoredKeys) continue
        out += DockSuggestion(
            id = p.id.ifBlank { "suggestion-$index" },
            kind = kind,
            title = p.title,
            body = p.body,
            evidence = p.evidence,
            action = p.action,
            dedupeKey = dedupeKey,
        )
        if (out.size >= SUGGESTION_MAX_ITEMS) break
    }
    return out
}

// ---------- 快照构建 / 基线回放 ----------

/** 从保存的 payload JSON 回放上次快照（基线用；top10 / pinned 顺序从字段取）。 */
fun snapshotFromPayloadJson(json: String): UsageSnapshot? {
    return try {
        val o = JSONObject(json)
        val now = o.optJSONObject("now")
        val top10 = o.optJSONArray("top10")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        val pinned = o.optJSONArray("pinned")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet()
        val apps = mutableListOf<AppStats>()
        val arr = o.optJSONArray("apps") ?: return null
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val pkg = a.getString("pkg")
            val hw = mutableMapOf<String, Int>()
            val hwObj = a.optJSONObject("hw")
            if (hwObj != null) {
                val keys = hwObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    hw[k] = hwObj.optInt(k)
                }
            }
            val transfersIn = mutableListOf<TransferIn>()
            val tinArr = a.optJSONArray("transfersIn")
            if (tinArr != null) {
                for (j in 0 until tinArr.length()) {
                    val t = tinArr.getJSONObject(j)
                    transfersIn += TransferIn(
                        fromPkg = t.getString("from"),
                        count = t.optInt("count"),
                        dominant = t.optBoolean("dominant"),
                    )
                }
            }
            val outObj = a.optJSONObject("transfersOut")
            apps += AppStats(
                pkg = pkg,
                label = a.optString("label", pkg),
                hw = hw,
                total7 = a.optInt("total7"),
                total = a.optInt("total"),
                lastUsedDays = a.optInt("lastUsedDays"),
                pinned = a.optBoolean("pinned", pkg in pinned),
                dockRank = if (a.isNull("dockRank")) null else a.optInt("dockRank"),
                transfersIn = transfersIn,
                transfersOutTotal = outObj?.optInt("total") ?: 0,
                transfersOutTargets = outObj?.optInt("targets") ?: 0,
            )
        }
        UsageSnapshot(
            apps = apps,
            top10 = top10,
            dow = now?.optInt("dow") ?: 0,
            hour = now?.optInt("hour") ?: 0,
            dayType = now?.optString("dayType", "") ?: "",
        )
    } catch (_: Exception) {
        null
    }
}

private const val TAG = "DockSuggestions"

/** 快照构建：候选应用 = 当前 Dock 前 10 ∪ 固定位应用（上限 20 个）。 */
fun buildSnapshot(
    context: Context,
    allApps: List<com.bobot.ailauncher.data.AppInfo>,
    top10: List<String>,
): UsageSnapshot {
    val pinned = UiPrefs.getDockPinned(context)
    val candidates = (top10 + pinned).distinct().take(20)
    val labelOf = allApps.associateBy({ it.packageName }, { it.label.toString() })
    val cal = java.util.Calendar.getInstance()
    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=周日 … 7=周六
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    // Calendar 的 DAY_OF_WEEK 是 1=周日；AppUsageTracker 的 hw key 用 1=周一…
    // 7=周日。换算：(dow + 5) % 7 + 1（周日 1→7，周一 2→1）。
    val trackerDow = (dow + 5) % 7 + 1
    val recent7 = AppUsageTracker.bulkRecent7(context)
    val nowMs = System.currentTimeMillis()
    val apps = candidates.map { pkg ->
        val stats = AppUsageTracker
        val hw = stats.hwDayTypeGrid(context, pkg)
        val total = stats.totalCount(context, pkg)
        val transfersIn = stats.transfersIn(context, pkg).map { (from, count) ->
            TransferIn(from, count, stats.transferDominant(count, total))
        }
        val (outTotal, outTargets) = stats.transfersOut(context, pkg)
        // 距上次打开天数：自记 last_used（与 Dock 自有计数同一数据源；
        // 系统 UsageStats 未授权时 effectiveLastUsed 本就回落到它）。
        // 0 = 无记录 → -1。
        val lastUsedMs = stats.lastUsed(context, pkg)
        val lastUsedDays = if (lastUsedMs <= 0L) -1
        else ((nowMs - lastUsedMs) / 86_400_000L).toInt().coerceAtLeast(0)
        AppStats(
            pkg = pkg,
            label = labelOf[pkg] ?: pkg,
            hw = hw,
            total7 = recent7[pkg] ?: 0,
            total = total,
            lastUsedDays = lastUsedDays,
            pinned = pkg in pinned,
            dockRank = top10.indexOf(pkg).takeIf { it >= 0 },
            transfersIn = transfersIn,
            transfersOutTotal = outTotal,
            transfersOutTargets = outTargets,
        )
    }
    return UsageSnapshot(
        apps = apps,
        top10 = top10,
        dow = trackerDow,
        hour = hour,
        dayType = dayTypeName(trackerDow),
    )
}

// ---------- 运行器 ----------

sealed interface AnalysisOutcome {
    /** 无候选（没调模型）。 */
    data object NoCandidates : AnalysisOutcome

    /** 有建议（fromCache=true = 候选未变，直接显示上次结果，没调模型）。 */
    data class Ready(val suggestions: List<DockSuggestion>, val fromCache: Boolean) : AnalysisOutcome

    /** 失败（网络错、模型非 2xx、解析失败、复核后 0 条）。 */
    data object Failed : AnalysisOutcome
}

/**
 * 一次分析（IO 线程跑）：建快照 → 预筛 → 无候选直接返回 → 候选与上次
 * 相同则返回缓存 → 有 Key 则调一次模型 → 解析 → 复核门 → 持久化。
 * 成功时把快照（payload）存为下次基线；失败时基线不动。
 */
suspend fun analyzeSuggestions(
    context: Context,
    allApps: List<com.bobot.ailauncher.data.AppInfo>,
    top10: List<String>,
    store: DockSuggestionStore,
): AnalysisOutcome = withContext(Dispatchers.IO) {
    val snap = buildSnapshot(context, allApps, top10)
    val prevSnap = store.lastPayload?.let { snapshotFromPayloadJson(it) }
    val prevTop10 = prevSnap?.top10
    val suppressedPinPkgs = store.suppressedPinPkgs()
    val suppressedUnpinPkgs = store.suppressedUnpinPkgs()
    val nomination = nominateCandidates(
        snap = snap,
        prevSnap = prevSnap,
        prevTop10 = prevTop10,
        mutedKinds = store.mutedKinds(),
        suppressedPinPkgs = suppressedPinPkgs,
        suppressedUnpinPkgs = suppressedUnpinPkgs,
    )
    if (!nomination.hasCandidates) {
        // 没候选就不调模型；但仍推进基线（“本周新过 ≥3”需要上次快照做比较）。
        store.saveBaselineOnly(buildPayloadJson(snap, emptyList()))
        return@withContext AnalysisOutcome.NoCandidates
    }
    val payloadJson = buildPayloadJson(snap, nomination.hints)
    val fingerprint = payloadFingerprint(snap, nomination.hints)
    // 候选与上次相同 → 显示上次结果，不重新调。
    val cached = store.cachedResult(fingerprint)
    if (cached != null) {
        return@withContext AnalysisOutcome.Ready(cached, fromCache = true)
    }
    if (!LlmConfig.hasKey(context)) {
        Log.d(TAG, "analyze: no key, skip call")
        return@withContext AnalysisOutcome.Failed
    }
    val rawBody = try {
        val r = LlmRouter.postChatCompletions(
            baseUrl = LlmConfig.getBaseUrl(context),
            apiKey = LlmConfig.getApiKey(context),
            model = LlmConfig.getModel(context),
            systemPrompt = SUGGESTIONS_SYSTEM_PROMPT,
            userContent = payloadJson,
            temperature = 0.2,
            maxTokens = 1200,
            tag = TAG,
        )
        if (r.networkError != null || r.httpCode !in 200..299) {
            Log.d(TAG, "analyze: http=${r.httpCode} err=${r.networkError}")
            return@withContext AnalysisOutcome.Failed
        }
        r.rawBody
    } catch (e: Exception) {
        Log.d(TAG, "analyze: call failed (${e.javaClass.simpleName})")
        return@withContext AnalysisOutcome.Failed
    }
    val parsed = parseSuggestionsResponse(rawBody) ?: run {
        Log.d(TAG, "analyze: parse failed")
        return@withContext AnalysisOutcome.Failed
    }
    val verified = verifySuggestions(
        parsed = parsed,
        snap = snap,
        mutedKinds = store.mutedKinds(),
        ignoredKeys = store.ignoredKeys(),
    )
    if (verified.isEmpty()) {
        Log.d(TAG, "analyze: verify dropped all (${parsed.size} parsed)")
        return@withContext AnalysisOutcome.Failed
    }
    Log.d(TAG, "analyze: parsed=${parsed.size} verified=${verified.size}")
    store.saveResult(verified, payloadJson, fingerprint)
    AnalysisOutcome.Ready(verified, fromCache = false)
}
