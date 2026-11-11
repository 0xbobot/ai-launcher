package com.bobot.ailauncher.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * 未读行跳转日志（Bob 2026-11-11 21:59 亲令：删掉质检逻辑，加日志、记例外，
 * 之后凭日志分析原因和优化）。
 *
 * 只记未读行点按那条路（openUnreadApp 发 contentIntent / 回退开应用、
 * AppBackgroundLatch 自检、ForegroundConfirm 前台核验、
 * scheduleUnreadCancelAfterOpen 取消那半）。判定逻辑本身、取消规则、角标、
 * HomeScreen、智能卡片一律不动。
 *
 * 事件模型（结构化 JSONL，一行一个事件；一条点按产生一到两条事件）：
 * - type=tap：点按时立即写。字段：ts（点按墙钟）、tapTs（同 ts，配对键）、
 *   pkg、hasContentIntent、path（content_intent | launch_app）、
 *   sendResult（ok | no_intent | error:<异常类名>）、launchOk（
 *   launch_app 路才有：launchApp 的返回值；content_intent 路为 null）、
 *   fallbackPath（foreground | latch | none）。
 * - type=outcome：该次点按被寄的各结果都到齐时写一笔。字段同 tapTs/pgk
 *   配对，另有：cancel（cancelled | kept | cancel_error:<异常类名>）、
 *   cancelFg（CONFIRMED | NOT_SEEN | UNKNOWN）和 cancelFgMs（点按到
 *   确认前台的毫秒数，仅 CONFIRMED 有）；fallbackFired（回退发没发）、
 *   fallbackOpened（回退开没开：launchApp 返回值）、fallbackMs（点按
 *   到回退的毫秒数）、fallbackFg / fallbackFgMs（前台回退那条路）、
 *   latchBg / latchBgMs（门闩那条路：窗内切没切后台、点按到切后台的
 *   毫秒数）；partial=true 表示等到 10 秒过期还没齐全、只记了已有的部分。
 *
 * 隐私边界：只记结果、不记内容——不记通知标题、正文、聊天名、人名、
 * 任何消息内容；异常只记类名。fromJson + allowed-key 校验守住这条线
 * （见 UnreadJumpLogTest 的隐私守卫单测）。
 *
 * 存储：files 目录下 unread-jump-log.jsonl，环形上限 200 条（超了丢
 * 最旧的）；同时镜像一份到 logcat（tag UnreadJump，INFO 级），接电脑
 * 时能实时看。所有写操作都在 runCatching 里：日志写失败不影响点按。
 */

// ---------- 纯函数：拼装 / 解析 / 合并 / 摘要（可单测） ----------

object UnreadJumpLogLogic {
    const val LOG_FILE_NAME = "unread-jump-log.jsonl"
    const val LOG_TAG = "UnreadJump"
    const val MAX_EVENTS = 200
    const val STALE_PENDING_MILLIS = 10_000L
    const val MAX_PENDING = 50

    data class Record(
        val type: String, // "tap" | "outcome"
        val ts: Long,
        val tapTs: Long,
        val pkg: String,
        val hasContentIntent: Boolean? = null,
        val path: String? = null, // "content_intent" | "launch_app"
        val sendResult: String? = null, // "ok" | "no_intent" | "error:<Simple>"
        val launchOk: Boolean? = null,
        val fallbackPath: String? = null, // "foreground" | "latch" | "none"
        val cancel: String? = null, // "cancelled" | "kept" | "cancel_error:<Simple>"
        val cancelFg: String? = null, // "CONFIRMED" | "NOT_SEEN" | "UNKNOWN"
        val cancelFgMs: Long? = null,
        val fallbackFired: Boolean? = null,
        val fallbackOpened: Boolean? = null,
        val fallbackMs: Long? = null,
        val fallbackFg: String? = null,
        val fallbackFgMs: Long? = null,
        val latchBg: Boolean? = null,
        val latchBgMs: Long? = null,
        val partial: Boolean? = null,
    )

    /** 允许出现的字段名（隐私守卫：谁也不许往日志里加内容字段）。 */
    val ALLOWED_KEYS = setOf(
        "type", "v", "ts", "tapTs", "pkg",
        "hasContentIntent", "path", "sendResult", "launchOk", "fallbackPath",
        "cancel", "cancelFg", "cancelFgMs",
        "fallbackFired", "fallbackOpened", "fallbackMs",
        "fallbackFg", "fallbackFgMs", "latchBg", "latchBgMs", "partial",
    )

    fun toJson(r: Record): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("type", r.type)
        o.put("ts", r.ts)
        o.put("tapTs", r.tapTs)
        o.put("pkg", r.pkg)
        r.hasContentIntent?.let { o.put("hasContentIntent", it) }
        r.path?.let { o.put("path", it) }
        r.sendResult?.let { o.put("sendResult", it) }
        r.launchOk?.let { o.put("launchOk", it) }
        r.fallbackPath?.let { o.put("fallbackPath", it) }
        r.cancel?.let { o.put("cancel", it) }
        r.cancelFg?.let { o.put("cancelFg", it) }
        r.cancelFgMs?.let { o.put("cancelFgMs", it) }
        r.fallbackFired?.let { o.put("fallbackFired", it) }
        r.fallbackOpened?.let { o.put("fallbackOpened", it) }
        r.fallbackMs?.let { o.put("fallbackMs", it) }
        r.fallbackFg?.let { o.put("fallbackFg", it) }
        r.fallbackFgMs?.let { o.put("fallbackFgMs", it) }
        r.latchBg?.let { o.put("latchBg", it) }
        r.latchBgMs?.let { o.put("latchBgMs", it) }
        r.partial?.let { o.put("partial", it) }
        return o.toString()
    }

    /** 坏行 / 类型不对 / 包名空 / 出现非法字段 → null（读时跳过，不炸屏）。 */
    fun fromJson(line: String): Record? = runCatching {
        val o = JSONObject(line)
        val keys = o.keys().asSequence().toSet()
        if (!keys.all { it in ALLOWED_KEYS }) return@runCatching null
        val type = o.optString("type")
        if (type != "tap" && type != "outcome") return@runCatching null
        val pkg = o.optString("pkg")
        if (pkg.isBlank()) return@runCatching null
        fun s(k: String): String? = if (o.has(k)) o.optString(k) else null
        fun b(k: String): Boolean? = if (o.has(k)) o.optBoolean(k) else null
        fun l(k: String): Long? = if (o.has(k)) o.optLong(k) else null
        Record(
            type = type,
            ts = o.optLong("ts"),
            tapTs = o.optLong("tapTs"),
            pkg = pkg,
            hasContentIntent = b("hasContentIntent"),
            path = s("path"),
            sendResult = s("sendResult"),
            launchOk = b("launchOk"),
            fallbackPath = s("fallbackPath"),
            cancel = s("cancel"),
            cancelFg = s("cancelFg"),
            cancelFgMs = l("cancelFgMs"),
            fallbackFired = b("fallbackFired"),
            fallbackOpened = b("fallbackOpened"),
            fallbackMs = l("fallbackMs"),
            fallbackFg = s("fallbackFg"),
            fallbackFgMs = l("fallbackFgMs"),
            latchBg = b("latchBg"),
            latchBgMs = l("latchBgMs"),
            partial = b("partial"),
        )
    }.getOrNull()

    /** 环形上限：只留最后 cap 行（cap 固定 200，见 MAX_EVENTS）。 */
    fun trimToLast(lines: List<String>, cap: Int = MAX_EVENTS): List<String> =
        if (lines.size <= cap) lines else lines.takeLast(cap)

    fun formatTime(ts: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))

    // ---------- 一次点按的配对与摘要（界面用） ----------

    data class GroupedEntry(val tapTs: Long, val pkg: String, val tap: Record?, val outcome: Record?)

    /** 按 (tapTs, pkg) 把 tap 和 outcome 配成一卡；新在前。 */
    fun groupByTap(records: List<Record>): List<GroupedEntry> {
        val map = LinkedHashMap<Pair<Long, String>, GroupedEntry>()
        for (r in records) {
            val key = r.tapTs to r.pkg
            val cur = map[key] ?: GroupedEntry(r.tapTs, r.pkg, null, null)
            map[key] = when (r.type) {
                "tap" -> cur.copy(tap = r)
                "outcome" -> cur.copy(outcome = r)
                else -> cur
            }
        }
        return map.values.sortedByDescending { it.tapTs }
    }

    /** 一卡一句话：怎么点的、打开没打开、通知清没清（人名/消息内容不进这里）。 */
    fun summarize(entry: GroupedEntry): String {
        val tap = entry.tap
        val out = entry.outcome
        val head = when {
            tap == null -> "点按记录丢了，只留了结果"
            tap.path == "launch_app" && tap.sendResult?.startsWith("error:") == true ->
                if (tap.launchOk == true) "通知跳转发送失败，改走直接打开成功"
                else "通知跳转发送失败，改走直接打开也失败"
            tap.path == "launch_app" && tap.sendResult == "no_intent" ->
                if (tap.launchOk == true) "这条通知没有跳转目标，直接打开了应用"
                else "这条通知没有跳转目标，直接打开也失败"
            tap.path == "launch_app" ->
                if (tap.launchOk == true) "直接打开了应用" else "直接打开失败"
            else -> "通知跳转已发出"
        }
        val openPart = when {
            out == null -> "等确认"
            out.fallbackFired == true && out.fallbackOpened == true -> "回退打开了应用"
            out.fallbackFired == true && out.fallbackOpened == false -> "回退了，还是没打开"
            out.latchBg == true || out.fallbackFg == "CONFIRMED" || out.cancelFg == "CONFIRMED" ->
                "已打开"
            out.fallbackFg == "UNKNOWN" || out.cancelFg == "UNKNOWN" -> "没法确认有没有打开"
            else -> "还没打开"
        }
        val cancelPart = when (val c = out?.cancel) {
            "cancelled" -> "通知已清"
            "kept" -> "通知留着"
            null -> null
            else -> if (c.startsWith("cancel_error:")) "取消时出错" else "通知留着"
        }
        val parts = listOfNotNull(head, openPart, cancelPart)
        return parts.joinToString(" · ") +
            if (out?.partial == true) "（结果不全）" else ""
    }

    // ---------- 待定合并（纯函数，事件写出逻辑在 UnreadJumpLog 里） ----------

    data class OutcomeState(
        val tapTs: Long,
        val pkg: String,
        val expectedCancel: Boolean = false,
        val expectedFallback: Boolean = false,
        val cancel: String? = null,
        val cancelFg: String? = null,
        val cancelFgMs: Long? = null,
        val fallbackFired: Boolean? = null,
        val fallbackOpened: Boolean? = null,
        val fallbackMs: Long? = null,
        val fallbackFg: String? = null,
        val fallbackFgMs: Long? = null,
        val latchBg: Boolean? = null,
        val latchBgMs: Long? = null,
    )

    fun withCancel(state: OutcomeState, cancel: String, fg: String?, fgMs: Long?): OutcomeState =
        state.copy(cancel = cancel, cancelFg = fg, cancelFgMs = fgMs)

    fun withFallback(
        state: OutcomeState,
        fired: Boolean,
        opened: Boolean?,
        ms: Long?,
        fg: String?,
        fgMs: Long?,
        bg: Boolean?,
        bgMs: Long?,
    ): OutcomeState =
        state.copy(
            fallbackFired = fired, fallbackOpened = opened, fallbackMs = ms,
            fallbackFg = fg, fallbackFgMs = fgMs, latchBg = bg, latchBgMs = bgMs,
        )

    /** 被寄的各部分都到齐了才出 outcome 事件（没被寄的部分不等）。 */
    fun isComplete(state: OutcomeState): Boolean =
        (!state.expectedCancel || state.cancel != null) &&
            (!state.expectedFallback || state.fallbackFired != null)

    fun toOutcomeRecord(state: OutcomeState, ts: Long, partial: Boolean): Record = Record(
        type = "outcome",
        ts = ts,
        tapTs = state.tapTs,
        pkg = state.pkg,
        cancel = state.cancel,
        cancelFg = state.cancelFg,
        cancelFgMs = state.cancelFgMs,
        fallbackFired = state.fallbackFired,
        fallbackOpened = state.fallbackOpened,
        fallbackMs = state.fallbackMs,
        fallbackFg = state.fallbackFg,
        fallbackFgMs = state.fallbackFgMs,
        latchBg = state.latchBg,
        latchBgMs = state.latchBgMs,
        partial = if (partial) true else null,
    )
}

// ---------- 落盘 / logcat / 合并（主线程调用，synchronized 护着待定表） ----------

object UnreadJumpLog {
    private val lock = Any()
    private val pending = LinkedHashMap<Pair<Long, String>, UnreadJumpLogLogic.OutcomeState>()

    fun recordTap(
        context: Context,
        tapWallMillis: Long,
        pkg: String,
        hasContentIntent: Boolean,
        path: String,
        sendResult: String,
        launchOk: Boolean?,
        fallbackPath: String,
    ) {
        val record = UnreadJumpLogLogic.Record(
            type = "tap",
            ts = tapWallMillis,
            tapTs = tapWallMillis,
            pkg = pkg,
            hasContentIntent = hasContentIntent,
            path = path,
            sendResult = sendResult,
            launchOk = launchOk,
            fallbackPath = fallbackPath,
        )
        synchronized(lock) {
            sweepStaleLocked(context, tapWallMillis)
            pending[tapWallMillis to pkg] = UnreadJumpLogLogic.OutcomeState(
                tapTs = tapWallMillis,
                pkg = pkg,
                expectedFallback = fallbackPath != "none",
            )
            appendLocked(context, record)
        }
    }

    /**
     * 取消那半被寄了：在这次点按的待定项上登记“等取消结果”。
     * 返回该次点按的墙钟（0 = 找不到这次点按——不该发生，防着崩）；
     * 供调用方同样传给取消调度器（它拿这个值记 outcome 的配对键）。
     */
    fun expectCancel(context: Context, pkg: String): Long {
        synchronized(lock) {
            val key = pending.keys.filter { it.second == pkg }
                .maxByOrNull { it.first } ?: return 0L
            val cur = pending[key] ?: return 0L
            val next = cur.copy(expectedCancel = true)
            pending[key] = next
            if (UnreadJumpLogLogic.isComplete(next)) {
                emitLocked(context, next, partial = false)
            }
            return key.first
        }
    }

    fun onCancelOutcome(
        context: Context,
        tapTs: Long,
        pkg: String,
        cancel: String,
        fg: String?,
        fgMs: Long?,
    ) {
        synchronized(lock) {
            val key = tapTs to pkg
            val cur = pending[key] ?: return
            val next = UnreadJumpLogLogic.withCancel(cur, cancel, fg, fgMs)
            pending[key] = next
            if (UnreadJumpLogLogic.isComplete(next)) {
                emitLocked(context, next, partial = false)
            }
        }
    }

    fun onFallbackOutcome(
        context: Context,
        tapTs: Long,
        pkg: String,
        fired: Boolean,
        opened: Boolean?,
        ms: Long?,
        fg: String?,
        fgMs: Long?,
        bg: Boolean?,
        bgMs: Long?,
    ) {
        synchronized(lock) {
            val key = tapTs to pkg
            val cur = pending[key] ?: return
            val next = UnreadJumpLogLogic.withFallback(cur, fired, opened, ms, fg, fgMs, bg, bgMs)
            pending[key] = next
            if (UnreadJumpLogLogic.isComplete(next)) {
                emitLocked(context, next, partial = false)
            }
        }
    }

    /** 最近事件（新在前）；文件被删/读坏 → 空列表，不炸屏。 */
    fun readRecent(context: Context): List<UnreadJumpLogLogic.Record> = runCatching {
        File(context.filesDir, UnreadJumpLogLogic.LOG_FILE_NAME).readLines()
            .mapNotNull { UnreadJumpLogLogic.fromJson(it) }
            .sortedByDescending { it.ts }
    }.getOrDefault(emptyList())

    /** 分享用的原文（JSONL 全文）；读失败 → 空串。 */
    fun shareText(context: Context): String = runCatching {
        File(context.filesDir, UnreadJumpLogLogic.LOG_FILE_NAME).readText()
    }.getOrDefault("")

    private fun emitLocked(
        context: Context,
        state: UnreadJumpLogLogic.OutcomeState,
        partial: Boolean,
    ) {
        pending.remove(state.tapTs to state.pkg)
        appendLocked(
            context,
            UnreadJumpLogLogic.toOutcomeRecord(state, System.currentTimeMillis(), partial),
        )
    }

    /**
     * 过期清扫（每次新点按时跑一次）：超过 10 秒还没齐全的待定项，
     * 有已到部分就按 partial 写一笔 outcome、没任何部分就静默丢掉。
     * 另有安全阀：待定项超过 50 个时丢最旧的（不写日志）。
     * 注意：取消调度器有去重（同一包上一轮未完直接 return），极少数情况
     * 下一次点按的取消结果永远到不了——就靠这一轮兜底出 partial。
     */
    private fun sweepStaleLocked(context: Context, nowWall: Long) {
        val stale = pending.entries
            .filter { nowWall - it.key.first > UnreadJumpLogLogic.STALE_PENDING_MILLIS }
        for ((_, state) in stale) {
            pending.remove(state.tapTs to state.pkg)
            if (state.cancel != null || state.fallbackFired != null) {
                appendLocked(
                    context,
                    UnreadJumpLogLogic.toOutcomeRecord(state, nowWall, partial = true),
                )
            }
        }
        while (pending.size > UnreadJumpLogLogic.MAX_PENDING) {
            val oldest = pending.entries.minByOrNull { it.key.first } ?: break
            pending.remove(oldest.key)
        }
    }

    private fun appendLocked(context: Context, record: UnreadJumpLogLogic.Record) {
        try {
            val json = UnreadJumpLogLogic.toJson(record)
            Log.i(UnreadJumpLogLogic.LOG_TAG, json)
            val file = File(context.filesDir, UnreadJumpLogLogic.LOG_FILE_NAME)
            file.appendText(json + "\n")
            val lines = file.readLines()
            if (lines.size > UnreadJumpLogLogic.MAX_EVENTS) {
                file.writeText(
                    UnreadJumpLogLogic.trimToLast(lines)
                        .joinToString(separator = "\n", postfix = "\n"),
                )
            }
        } catch (_: Exception) {
            // 日志写失败不影响点按（静默丢弃，不弹任何提示）。
        }
    }
}
