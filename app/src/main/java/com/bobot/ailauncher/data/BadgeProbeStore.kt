package com.bobot.ailauncher.data

import android.content.Context

/**
 * 探针数存盘（Bob 2026-11-09 12:20 ①：版本升级后角标要等下一条通知才更新——
 * 探针数只存内存（BadgeProbe 的 StateFlow），升级重启必丢，抖音那路已知）。
 *
 * 做法照粘性数 BadgeStickyStore 的 SharedPreferences 做法：包名 →
 * "count,timestamp"，广播到达时写穿、启动应用时清掉该包、恢复时按时间戳
 * 取新、过期丢掉。格式复用粘性数同一套 parse/format（同包顶层函数）。
 */

/** 探针数存盘文件。 */
private const val PREFS_NAME = "badge_probe"

/**
 * 探针数最长保留 7 天：广播数是应用在某个时刻自报的未读数，7 天没新广播
 * 基本已过期（应用有新消息会再广播；用户大概率已读）。超过 7 天不复活，
 * 避免升级后把几个月前的旧数算进角标。
 */
const val PROBE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

/** 纯函数：按时间戳剪掉过期条目（包名 → 数 to 上报时间）；时间戳 <= 0 视为脏数据丢掉。 */
fun pruneProbeEntries(
    entries: Map<String, Pair<Int, Long>>,
    nowMillis: Long,
    maxAgeMillis: Long = PROBE_MAX_AGE_MILLIS
): Map<String, Pair<Int, Long>> {
    if (entries.isEmpty()) return entries
    return entries.filterValues { (_, t) -> t > 0L && nowMillis - t <= maxAgeMillis }
}

/**
 * 纯函数：两份探针数合并，每包留时间戳更新的那份（平局留后一份——磁盘是
 * 写穿的，至少和内存一样新）。恢复时用它合并不覆盖内存里更新的数。
 */
fun mergeProbeEntries(
    current: Map<String, Pair<Int, Long>>,
    incoming: Map<String, Pair<Int, Long>>
): Map<String, Pair<Int, Long>> {
    if (incoming.isEmpty()) return current
    if (current.isEmpty()) return incoming
    val out = current.toMutableMap()
    for ((pkg, entry) in incoming) {
        val old = out[pkg]
        if (old == null || entry.second >= old.second) out[pkg] = entry
    }
    return out
}

/** 探针数存取（薄壳，I/O 在 SharedPreferences；纯逻辑在上面两个纯函数里，可单测）。 */
object BadgeProbeStore {
    /** 读全部探针数（包名 → 数 to 上报时间）；脏条目跳过。 */
    fun loadEntries(context: Context): Map<String, Pair<Int, Long>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val out = mutableMapOf<String, Pair<Int, Long>>()
        for ((pkg, value) in prefs.all) {
            val parsed = (value as? String)?.let { parseStickyValue(it) } ?: continue
            out[pkg] = parsed
        }
        return out
    }

    /**
     * 全量写回（广播到达时写穿调用）：内存里有的全写，磁盘上有、内存里没了
     * 的包删掉（清掉的探针数不复活）。
     */
    fun saveEntries(context: Context, entries: Map<String, Pair<Int, Long>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for ((pkg, entry) in entries) {
            editor.putString(pkg, formatStickyValue(entry.first, entry.second))
        }
        for (pkg in prefs.all.keys) {
            if (pkg !in entries) editor.remove(pkg)
        }
        editor.apply()
    }

    /** 启动应用时清掉该包探针数（启动=去读消息了，与粘性数同款）。 */
    fun clearForPackage(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(pkg)) return
        prefs.edit().remove(pkg).apply()
    }
}
