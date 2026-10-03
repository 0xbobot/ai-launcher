package com.bobot.ailauncher.data

import android.content.Context
import com.bobot.ailauncher.core.context.ContextEngine

/**
 * 应用使用频次统计（Dock"常用应用"的数据来源）。
 * SharedPreferences "app_usage"：包名 -> 启动次数；另记 last_used 供并列时参考。
 * v0.20：加时间槽亲和度（PRD §三十二）——记录每个时间段各 App 的启动次数，
 * 早/中/晚/夜常用不同的 App 时，Dock 会跟着时间变。
 * 无历史数据时 Dock 用内置的国民应用优先级兜底。
 */
object AppUsageTracker {
    private const val PREFS = "app_usage"
    private const val KEY_COUNT_PREFIX = "count_"
    private const val KEY_LAST_PREFIX = "last_"
    private const val KEY_SLOT_PREFIX = "slot_" // slot_<SLOT>_<pkg> -> 该时段启动次数

    /** Dock 兜底优先级：国民应用（仅取本机已安装的） */
    private val fallbackPriority = listOf(
        "com.tencent.mm",              // 微信
        "com.eg.android.AlipayGphone", // 支付宝
        "com.ss.android.ugc.aweme",    // 抖音
        "com.larus.nova",              // 豆包
        "com.tencent.mobileqq",        // QQ
        "com.xingin.xhs",              // 小红书
        "com.sina.weibo",              // 微博
        "com.moonshot.kimi",           // Kimi
        "com.deepseek.chat"            // DeepSeek
    )

    fun recordLaunch(context: Context, packageName: String) {
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val n = p.getInt(KEY_COUNT_PREFIX + packageName, 0)
            val slot = ContextEngine.currentSlot().name
            val sn = p.getInt(KEY_SLOT_PREFIX + slot + "_" + packageName, 0)
            p.edit()
                .putInt(KEY_COUNT_PREFIX + packageName, n + 1)
                .putLong(KEY_LAST_PREFIX + packageName, System.currentTimeMillis())
                .putInt(KEY_SLOT_PREFIX + slot + "_" + packageName, sn + 1)
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * 取 [count] 个最常用应用。
     * @param smartSort true=智能排序（PRD §三十二：总频次 + 3×当前时段亲和度，并列按最近）；
     *   false=按名称排列（用户在设置里关闭智能排序时用，可预期）。
     * 不足时用国民应用优先级补齐（只取本机已安装的），排除桌面自身。
     */
    fun topApps(
        context: Context,
        allApps: List<AppInfo>,
        count: Int = 4,
        smartSort: Boolean = true
    ): List<AppInfo> {
        val selfPkg = context.packageName
        val candidates = allApps.filter { it.packageName != selfPkg }
        if (candidates.isEmpty()) return emptyList()
        if (!smartSort) {
            return candidates.sortedBy { it.label.toString() }.take(count)
        }
        val p = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
        val slot = ContextEngine.currentSlot().name
        data class Scored(val app: AppInfo, val score: Double, val last: Long)
        val scored = candidates.map { app ->
            val c = p?.getInt(KEY_COUNT_PREFIX + app.packageName, 0) ?: 0
            val sc = p?.getInt(KEY_SLOT_PREFIX + slot + "_" + app.packageName, 0) ?: 0
            val last = p?.getLong(KEY_LAST_PREFIX + app.packageName, 0L) ?: 0L
            // 总频次 + 3×当前时段亲和度：早晚常用不同的 App，Dock 跟着变
            Scored(app, c + 3.0 * sc, last)
        }
        val byUsage = scored.filter { it.score > 0 }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.last })
            .map { it.app }
        if (byUsage.size >= count) return byUsage.take(count)
        val picked = byUsage.toMutableList()
        val pickedPkgs = picked.map { it.packageName }.toMutableSet()
        // 兜底：国民应用优先级
        for (pkg in fallbackPriority) {
            if (picked.size >= count) break
            if (pkg in pickedPkgs) continue
            candidates.firstOrNull { it.packageName == pkg }?.let {
                picked += it
                pickedPkgs += pkg
            }
        }
        // 还不够：按应用名补齐，保证 Dock 始终有 4 个（或全部）
        for (app in candidates) {
            if (picked.size >= count) break
            if (app.packageName !in pickedPkgs) {
                picked += app
                pickedPkgs += app.packageName
            }
        }
        return picked.take(count)
    }
}
