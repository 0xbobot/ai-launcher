package com.bobot.ailauncher.data

import android.content.Context
import com.bobot.ailauncher.core.context.ContextEngine
import java.util.Calendar

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
    // 智能 v3：小时×星期桶 hw_<dow1-7>_<hour0-23>_<pkg>（周一=1…周日=7）；
    // 转移 tr_<prevPkg>_<curPkg>；上次启动 prev_pkg/prev_time；月度衰减基线 lastDecayMonth
    private const val KEY_HW_PREFIX = "hw_"
    private const val KEY_TR_PREFIX = "tr_"
    private const val KEY_PREV_PKG = "prev_pkg"
    private const val KEY_PREV_TIME = "prev_time"
    private const val KEY_LAST_DECAY_MONTH = "lastDecayMonth"

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

    /** 当前星期（周一=1…周日=7）与小时（0-23），Dock/说明页与记录共用同一口径。 */
    fun currentDowHour(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        val dow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return dow to cal.get(Calendar.HOUR_OF_DAY)
    }

    /** 当前年月（year*12+month，month 取 0-11），月度衰减基线用。 */
    private fun currentMonth(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    fun recordLaunch(context: Context, packageName: String) {
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val (dow, hour) = currentDowHour()
            val editor = p.edit()
            // 月度衰减（30 天半衰的整数实现）：年月变化时，对全部 Int 计数值
            // 按经过月数连续减半（值/2 取整，0 保持 0）。首个 v3 版本建立基线，
            // 不追溯减半——老用户终身数据不在升级当夜腰斩；此后每月自动衰减，
            // 活跃应用靠每次启动 +1 顶住，不活跃的三四个月内淡出。
            val decayed = mutableMapOf<String, Int>()
            if (!p.contains(KEY_LAST_DECAY_MONTH)) {
                editor.putInt(KEY_LAST_DECAY_MONTH, currentMonth())
            } else {
                val months = currentMonth() - p.getInt(KEY_LAST_DECAY_MONTH, 0)
                if (months > 0) {
                    for ((k, v) in p.all) {
                        if (v is Int && (k.startsWith(KEY_COUNT_PREFIX) ||
                                k.startsWith(KEY_SLOT_PREFIX) ||
                                k.startsWith(KEY_HW_PREFIX) ||
                                k.startsWith(KEY_TR_PREFIX))
                        ) {
                            var nv = v as Int
                            repeat(months) { nv /= 2 }
                            decayed[k] = nv
                        }
                    }
                    for ((k, v) in decayed) editor.putInt(k, v)
                    editor.putInt(KEY_LAST_DECAY_MONTH, currentMonth())
                }
            }
            // 读数统一走衰减后（有衰减时读衰减值，否则读原值），避免本次 +1
            // 把衰减结果覆盖回旧值。
            fun curInt(key: String, def: Int = 0): Int = decayed[key] ?: p.getInt(key, def)
            // 转移：本次距上次启动（任意应用）≤15 分钟才记
            val prevPkg = p.getString(KEY_PREV_PKG, "") ?: ""
            val prevTime = p.getLong(KEY_PREV_TIME, 0L)
            if (prevPkg.isNotEmpty() && now - prevTime <= 15 * 60 * 1000) {
                val trKey = KEY_TR_PREFIX + prevPkg + "_" + packageName
                editor.putInt(trKey, curInt(trKey) + 1)
            }
            val slot = ContextEngine.currentSlot().name
            val hwKey = KEY_HW_PREFIX + dow + "_" + hour + "_" + packageName
            editor.putInt(KEY_COUNT_PREFIX + packageName, curInt(KEY_COUNT_PREFIX + packageName) + 1)
            editor.putLong(KEY_LAST_PREFIX + packageName, now)
            editor.putInt(KEY_SLOT_PREFIX + slot + "_" + packageName, curInt(KEY_SLOT_PREFIX + slot + "_" + packageName) + 1)
            editor.putInt(hwKey, curInt(hwKey) + 1)
            editor.putString(KEY_PREV_PKG, packageName)
            editor.putLong(KEY_PREV_TIME, now)
            editor.apply()
        } catch (_: Exception) {
        }
    }

    /** 最近启动时间（毫秒；从未启动返回 0）。供隐藏一键按钮的 90 天判断用。 */
    fun lastUsed(context: Context, packageName: String): Long {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_PREFIX + packageName, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** 衰减后总启动次数。供 Dock 说明页“经常用”判断用。 */
    fun totalCount(context: Context, packageName: String): Int {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_COUNT_PREFIX + packageName, 0)
        } catch (_: Exception) {
            0
        }
    }

    /** 衰减后小时×星期桶值。供 Dock 排序与说明页用。 */
    fun hwCount(context: Context, packageName: String, dow: Int, hour: Int): Int {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HW_PREFIX + dow + "_" + hour + "_" + packageName, 0)
        } catch (_: Exception) {
            0
        }
    }

    /** 上次启动（包名，时间毫秒）；无记录返回 ("", 0L)。供 Dock 转移加分与说明页用。 */
    fun prevLaunch(context: Context): Pair<String, Long> {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            (p.getString(KEY_PREV_PKG, "") ?: "") to p.getLong(KEY_PREV_TIME, 0L)
        } catch (_: Exception) {
            "" to 0L
        }
    }

    /** 转移次数（prevPkg → packageName 的衰减后计数值）。 */
    fun transferCount(context: Context, prevPkg: String, packageName: String): Int {
        if (prevPkg.isEmpty()) return 0
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TR_PREFIX + prevPkg + "_" + packageName, 0)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 单个应用的智能排序分（总频次 + 3×当前时段亲和度 - 30 分钟内刚用过的
     * 重复惩罚 2 分）。注意：智能 v3 后搜索不再调用本函数（改用
     * [usageScoreForSearch]，不含 -2 惩罚）；Dock 新公式内联实现 -2 惩罚，
     * 本函数保留未删除。
     */
    fun usageScore(context: Context, packageName: String): Double {
        val p = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        } ?: return 0.0
        val slot = ContextEngine.currentSlot().name
        val c = p.getInt(KEY_COUNT_PREFIX + packageName, 0)
        val sc = p.getInt(KEY_SLOT_PREFIX + slot + "_" + packageName, 0)
        val last = p.getLong(KEY_LAST_PREFIX + packageName, 0L)
        var score = c + 3.0 * sc
        if (System.currentTimeMillis() - last < 30 * 60 * 1000) {
            score -= 2.0
        }
        return score
    }

    /**
     * 搜索同档 tie-break 用（智能 v3）：总频次 + 3×当前时段亲和度，不含
     * -2 惩罚——搜索时刚用过的应用正是用户要找的，不该被压下去。
     * 档位本身不动（精确命中仍压过同档），只破同分。
     */
    fun usageScoreForSearch(context: Context, packageName: String): Double {
        val p = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        } ?: return 0.0
        val slot = ContextEngine.currentSlot().name
        val c = p.getInt(KEY_COUNT_PREFIX + packageName, 0)
        val sc = p.getInt(KEY_SLOT_PREFIX + slot + "_" + packageName, 0)
        return c + 3.0 * sc
    }

    /**
     * 取 [count] 个最常用应用。
     * @param smartSort true=智能排序（智能 v3：当前 dow+hour 的衰减后小时桶
     *   ≥3 时 ×10000 主导 + 衰减后总次数 + 10 分钟内转移 ≥3 时 +500
     *   - 30 分钟内刚用过 -2；并列按最近）；false=按名称排列。
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
        // 智能 v3 排序分（Dock 与助手常用 5 名共用同一口径）：
        // 当前 dow+hour 桶 ≥3 才参与主导（×10000），否则按 0 计——稀疏格子
        // 不靠一两次偶然上位；转移只看 10 分钟内且 ≥3 次；-2 惩罚保留（防单调）。
        val now = System.currentTimeMillis()
        val (dow, hour) = currentDowHour()
        val (prevPkg, prevTime) = if (p == null) "" to 0L else
            (p.getString(KEY_PREV_PKG, "") ?: "") to p.getLong(KEY_PREV_TIME, 0L)
        val prevRecent = prevPkg.isNotEmpty() && now - prevTime <= 10 * 60 * 1000
        data class Scored(val app: AppInfo, val score: Double, val last: Long)
        val scored = candidates.map { app ->
            val pkg = app.packageName
            val last = p?.getLong(KEY_LAST_PREFIX + pkg, 0L) ?: 0L
            val total = p?.getInt(KEY_COUNT_PREFIX + pkg, 0) ?: 0
            val hw = p?.getInt(KEY_HW_PREFIX + dow + "_" + hour + "_" + pkg, 0) ?: 0
            val hwPart = if (hw >= 3) hw else 0
            val tr = if (prevRecent) {
                p?.getInt(KEY_TR_PREFIX + prevPkg + "_" + pkg, 0) ?: 0
            } else 0
            val trPart = if (tr >= 3) 500.0 else 0.0
            val recentPenalty =
                if (last != 0L && now - last < 30 * 60 * 1000) 2.0 else 0.0
            Scored(app, hwPart * 10000.0 + total + trPart - recentPenalty, last)
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
