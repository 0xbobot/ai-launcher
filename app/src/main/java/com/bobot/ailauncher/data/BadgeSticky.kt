package com.bobot.ailauncher.data

import android.content.Context

/**
 * 角标粘性数（Bob 2026-11-08 08:02 反馈③：诊断数字还是不对，系统 15、飞书 125）。
 *
 * 机制：应用通过通知 number 自报未读数时，把最后一次上报的 number 粘住
 * （存 SharedPreferences "badge_sticky"，包名→number+时间）；角标按包取
 * 现有三路 + 探针 + 粘性数的最大值。通知栏清掉后，粘性数仍在，角标不归零。
 *
 * 清掉时机（只两处）：
 * ① 用户在我们桌面里点图标启动该应用（启动=去读消息了）；
 * ② 新通知到达且 number=0（应用说没有未读了）。
 *
 * 如实局限：粘性数只在应用此后新发通知时才有数——不会立刻变出飞书 125
 * （现在没有捕获到的飞书 number，仍显示 0，直到飞书新发一条带 number 的
 * 通知）；若用户不在我们桌面里启动应用（比如从最近任务进），粘性数可能
 * 偏高滞留，直到下一次通知更新。不要谎称已拿到精确数。
 */

/** 粘性数存盘：包名 → "number,timestamp"（时间是该 number 的上报时间，仅作记录）。 */
private const val PREFS_NAME = "badge_sticky"

/** 纯函数：解析一条存盘值；格式不对 / number<=0 → null（脏数据跳过，不崩）。 */
fun parseStickyValue(value: String): Pair<Int, Long>? {
    val parts = value.split(",")
    if (parts.size != 2) return null
    val n = parts[0].toIntOrNull() ?: return null
    val t = parts[1].toLongOrNull() ?: return null
    if (n <= 0) return null
    return n to t
}

/** 纯函数：格式化一条存盘值。 */
fun formatStickyValue(number: Int, time: Long): String = "$number,$time"

/**
 * 纯函数：把粘性数并进角标（每包取最大值；粘性为 0/缺失的包不参与；
 * 空粘性时原样返回，不改调用方 map）。
 */
fun mergeStickyCounts(base: Map<String, Int>, sticky: Map<String, Int>): Map<String, Int> {
    if (sticky.isEmpty()) return base
    val out = base.toMutableMap()
    for ((pkg, n) in sticky) {
        if (n > 0) out[pkg] = maxOf(out[pkg] ?: 0, n)
    }
    return out
}

/**
 * 纯函数：按新到通知的 number 更新粘性 map（事件驱动，只在通知到达时调）：
 * - number > 0：记下（新数覆盖旧数，即使新数更小——应用自报的最新总数为准）；
 * - number == 0：清掉该包粘性（应用说没有未读了）；
 * - 该包没有新通知：不动（通知栏清掉不影响粘性，这正是粘住的意义）。
 * 输入 numbers：每包新到通知的 number（0 也要传进来才能清）。
 */
fun updateStickyMap(current: Map<String, Int>, numbers: Map<String, Int>): Map<String, Int> {
    if (numbers.isEmpty()) return current
    val out = current.toMutableMap()
    for ((pkg, n) in numbers) {
        if (n > 0) out[pkg] = n else out.remove(pkg)
    }
    return out
}

/** 纯函数：启动应用时清掉该包粘性（启动=去读消息了）；该包无粘性时原样返回。 */
fun clearStickyForPackage(current: Map<String, Int>, pkg: String): Map<String, Int> {
    if (!current.containsKey(pkg)) return current
    return current - pkg
}

/** 粘性数存取（薄壳，I/O 在 SharedPreferences；纯逻辑在上面三个纯函数里，可单测）。 */
object BadgeStickyStore {
    /** 读全部粘性数（包名→number）；脏条目跳过。 */
    fun loadNumbers(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val out = mutableMapOf<String, Int>()
        for ((pkg, value) in prefs.all) {
            val parsed = (value as? String)?.let { parseStickyValue(it) } ?: continue
            out[pkg] = parsed.first
        }
        return out
    }

    /**
     * 新通知到达即记/清（事件驱动，只在 onNotificationPosted 里调）：
     * number > 0 记下（带上报时间），number == 0 清掉该包。
     */
    fun recordPostedNumber(context: Context, pkg: String, number: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (number > 0) {
            prefs.edit()
                .putString(pkg, formatStickyValue(number, System.currentTimeMillis()))
                .apply()
        } else {
            if (!prefs.contains(pkg)) return
            prefs.edit().remove(pkg).apply()
        }
    }

    /** 启动应用时清掉该包粘性（启动=去读消息了）。 */
    fun clearForPackage(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(pkg)) return
        prefs.edit().remove(pkg).apply()
    }
}
