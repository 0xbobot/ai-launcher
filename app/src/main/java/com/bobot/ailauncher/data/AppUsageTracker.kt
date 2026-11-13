package com.bobot.ailauncher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
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
    // 折叠组（419）：不活跃判定——首次见到 first_<pkg>（毫秒）；90 天阈值
    private const val KEY_FIRST_PREFIX = "first_"
    private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    // 修法三（Bob 2026-11-13 22:24：30 天太长，改成 7 天）：近 7 天
    // 窗口计数 day_<yyyyMMdd>_<pkg>，与月度衰减的 count_/hw_/tr_ 键分开——
    // 窗口计数是真实时间窗口，不参与衰减；7 天外的旧天由 lastDayCleanup
    // 每天清一次，读数本身只加窗口内日期。
    private const val KEY_DAY_PREFIX = "day_"
    private const val KEY_LAST_DAY_CLEANUP = "lastDayCleanup"
    /** “近期”的窗口天数（判断值，写死 7 天，显示就写“近 7 天”）。 */
    const val RECENT_WINDOW_DAYS = 7

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
            // 近 7 天窗口计数（修法二）：当天 day 键 +1；7 天外旧天每天清
            // 一次（读数只加窗口内日期，清不清理不影响正确性，只防键攒着）。
            // day 键不在月度衰减的键前缀里，不参与衰减。
            val today = todayYmd()
            val dayKey = KEY_DAY_PREFIX + today + "_" + packageName
            editor.putInt(dayKey, curInt(dayKey) + 1)
            if (p.getString(KEY_LAST_DAY_CLEANUP, "") != today) {
                val cutoff = shiftYmd(today, -(RECENT_WINDOW_DAYS - 1))
                for ((k, _) in p.all) {
                    if (k.startsWith(KEY_DAY_PREFIX)) {
                        val date = k.removePrefix(KEY_DAY_PREFIX).take(8)
                        if (date.length == 8 && date < cutoff) editor.remove(k)
                    }
                }
                editor.putString(KEY_LAST_DAY_CLEANUP, today)
            }
            editor.putString(KEY_PREV_PKG, packageName)
            editor.putLong(KEY_PREV_TIME, now)
            editor.apply()
        } catch (_: Exception) {
        }
    }

    /** 自己记的最近启动时间（毫秒；从未启动返回 0）。系统数据兜底用。 */
    fun lastUsed(context: Context, packageName: String): Long {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_PREFIX + packageName, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 折叠组（419）：系统最后前台时间表（包名 -> 近 6 个月内 max lastTimeUsed）。
     * 查到空或抛 SecurityException 即视为未授权，返回 null 走兜底——与
     * AppDiscovery、ContextEngine 现有策略一致，不新增 AppOps 检查；
     * PACKAGE_USAGE_STATS Manifest 已声明，不新增权限。
     * 月度聚合保留半年左右，6 个月窗口覆盖 90 天判断所需历史。
     */
    private fun systemLastUsedMap(context: Context): Map<String, Long>? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -6)
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_MONTHLY,
                cal.timeInMillis,
                now
            )
            if (stats.isNullOrEmpty()) return null
            val map = mutableMapOf<String, Long>()
            for (s in stats) {
                if (s.lastTimeUsed > 0L) {
                    val prev = map[s.packageName] ?: 0L
                    if (s.lastTimeUsed > prev) map[s.packageName] = s.lastTimeUsed
                }
            }
            if (map.isEmpty()) null else map
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 有效最后使用时间（单应用便捷口径）：系统优先——授权且有记录时为系统
     * lastTimeUsed（通知/最近任务/他 App 跳过来到前台都算）；否则自己记的
     * last_used；0 表无记录。批量判定走 [inactiveMembers]（一次查系统表）。
     */
    fun effectiveLastUsed(context: Context, packageName: String): Long {
        return try {
            val sys = systemLastUsedMap(context)
            if (sys != null) sys[packageName] ?: 0L
            else lastUsed(context, packageName)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 单应用不活跃判定（纯规则，系统表与历史深度由调用方一次算好传入）：
     * 有效最后使用 > 0 且距今 > 90 天 → 不活跃。
     * 有效最后使用 == 0（无记录）时：系统已授权且历史够深（任一应用的系统
     * lastTimeUsed 早于 90 天，证明系统有 90 天以上历史）→ 不活跃；
     * 否则（未授权，或新手机没历史）走首次见到宽限：first_<pkg> 缺失时懒打戳
     * 为 now 并当次按活跃算（打戳本身不触发重组，下次算才生效），满 90 天
     * 没打开才算不活跃——新用户/新装应用不会一上来就被折进去。
     */
    private fun isInactiveRule(
        p: SharedPreferences,
        packageName: String,
        sys: Map<String, Long>?,
        historyDeep: Boolean,
        now: Long
    ): Boolean {
        val eff = if (sys != null) (sys[packageName] ?: 0L)
            else p.getLong(KEY_LAST_PREFIX + packageName, 0L)
        if (eff > 0L) return now - eff > NINETY_DAYS_MS
        if (sys != null && historyDeep) return true
        val key = KEY_FIRST_PREFIX + packageName
        val first = p.getLong(key, 0L)
        if (first == 0L) {
            p.edit().putLong(key, now).apply()
            return false
        }
        return now - first > NINETY_DAYS_MS
    }

    /**
     * 不活跃成员（共享规则：折叠组与一键隐藏按钮共用；Dock 排序 topApps 与
     * 搜索 tie-break 的时间源一律不动，仍走自己记的 counts/last）。
     * 输入须为可见集（已去隐藏、去自己，沿用 azApps 口径）；固定位
     * （UiPrefs.getDockPinned 置顶集合）永远不进。系统表只查一次。
     */
    fun inactiveMembers(context: Context, apps: List<AppInfo>): List<AppInfo> {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val sys = systemLastUsedMap(context)
            val historyDeep = sys?.values?.any { now - it > NINETY_DAYS_MS } ?: false
            val pinned = UiPrefs.getDockPinned(context).toSet()
            apps.filter { app ->
                app.packageName !in pinned &&
                    isInactiveRule(p, app.packageName, sys, historyDeep, now)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 衰减后总启动次数。供 Dock 排序分、转移占比的分母、说明页“最近没怎么开”
     * 判定用；“常开”分支的显示改用近 7 天窗口计数 [recentCount]。 */
    fun totalCount(context: Context, packageName: String): Int {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_COUNT_PREFIX + packageName, 0)
        } catch (_: Exception) {
            0
        }
    }

    /** 衰减后小时×星期桶值（单个星期几×小时）。Dock 现走按日类型加总的
     *  [hwDayTypeCount]，本函数保留供单格查询用。 */
    fun hwCount(context: Context, packageName: String, dow: Int, hour: Int): Int {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HW_PREFIX + dow + "_" + hour + "_" + packageName, 0)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 日类型时段桶加总（纯函数）：工作日（周一–周五）同一小时求和；周六/
     * 周日单算。hwByDow 为星期几（1–7）→ 该小时的衰减后桶值；缺失按 0 计。
     * 168 格/应用太细攒不到 3 次，改按日类型加总后理由与排序主导才够得着。
     */
    fun dayTypeHwSum(hwByDow: Map<Int, Int>, dow: Int): Int {
        return if (dow in 1..5) (1..5).sumOf { hwByDow[it] ?: 0 } else hwByDow[dow] ?: 0
    }

    /**
     * 衰减后小时桶按日类型加总。供 Dock 排序、说明页、灯泡共用同一口径——
     * 理由与排序同源。只改读数时加总，不新增存储键，历史数据立即生效。
     */
    fun hwDayTypeCount(context: Context, packageName: String, dow: Int, hour: Int): Int {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val hwByDow = (1..7).associateWith { d ->
                p.getInt(KEY_HW_PREFIX + d + "_" + hour + "_" + packageName, 0)
            }
            dayTypeHwSum(hwByDow, dow)
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
     * 转移是否“占主导”（纯函数）：转移计数 ≥2 且占该应用衰减后总次数一半以上
     * （transfer * 2 >= total，整数算，无浮点）。枢纽应用（Muse）带出来的
     * 转移计数再高、占比低也不算——“接着开”只在真是习惯时才出现。0.5 是
     * 判断值（Bob 2026-11-13 21:33：全是 Muse 之后打开，失去意义）。
     * 转移与总量同按月度衰减，衰减不改变占比。
     */
    fun transferDominant(transfer: Int, total: Int): Boolean {
        return transfer >= 2 && total > 0 && transfer * 2 >= total
    }

    /** yyyyMMdd 格式化（纯函数，month 取 1-12），不用 String.format 避 locale。 */
    fun formatYmd(year: Int, month: Int, day: Int): String {
        val m = month.toString().padStart(2, '0')
        val d = day.toString().padStart(2, '0')
        return "$year$m$d"
    }

    /** 当天 yyyyMMdd（本地时区），与 [currentDowHour] 同一 Calendar 口径。 */
    fun todayYmd(): String {
        val cal = Calendar.getInstance()
        return formatYmd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** yyyyMMdd 按天平移（纯函数，跨月/跨年由 Calendar 算；解析失败原样返回）。 */
    fun shiftYmd(ymd: String, deltaDays: Int): String {
        return try {
            val cal = Calendar.getInstance()
            cal.set(
                ymd.substring(0, 4).toInt(),
                ymd.substring(4, 6).toInt() - 1,
                ymd.substring(6, 8).toInt()
            )
            cal.add(Calendar.DAY_OF_YEAR, deltaDays)
            formatYmd(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        } catch (_: Exception) {
            ymd
        }
    }

    /**
     * 近 N 天窗口计数（纯函数）：dayCounts 为日期 yyyyMMdd → 当天启动次数，
     * 只加 [todayYmd] 往前数 days 天（含今天）的日期；窗口外与脏键（非 8 位
     * 数字日期）不计。days <= 0 返回 0。
     */
    fun recentCount(
        dayCounts: Map<String, Int>,
        todayYmd: String,
        days: Int = RECENT_WINDOW_DAYS
    ): Int {
        if (days <= 0) return 0
        val start = shiftYmd(todayYmd, -(days - 1))
        var sum = 0
        for ((date, c) in dayCounts) {
            if (date.length == 8 && date.all { it.isDigit() } && date in start..todayYmd) {
                sum += c
            }
        }
        return sum
    }

    /** 近 N 天窗口启动次数（默认 7 天）。读 day_<yyyyMMdd>_<pkg> 键，只加窗口内日期。 */
    fun recentCount(
        context: Context,
        packageName: String,
        days: Int = RECENT_WINDOW_DAYS
    ): Int {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val dayCounts = mutableMapOf<String, Int>()
            val prefixLen = KEY_DAY_PREFIX.length
            for ((k, v) in p.all) {
                if (v is Int && k.startsWith(KEY_DAY_PREFIX) && k.length > prefixLen + 8) {
                    val date = k.substring(prefixLen, prefixLen + 8)
                    val rest = k.substring(prefixLen + 8)
                    if (rest == "_$packageName") dayCounts[date] = v
                }
            }
            recentCount(dayCounts, todayYmd(), days)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Dock 排序分（纯函数）：时段聚合 ≥3 时 ×10000 主导 + 衰减后总次数
     * + 10 分钟内转移占主导（[transferDominant]：≥2 且占比 ≥0.5）时 +500
     * − 30 分钟内刚用过 −2；并列按最近（调用方）。
     * 与说明页理由同源——[dockReason] 用同一阈值。
     * 排序分仍用衰减后总次数：窗口计数刚换过去头几天数字偏小（要从新数据
     * 攒），排序不能跟着跳——过渡期见收据。
     */
    fun dockScore(
        hwDayType: Int,
        total: Int,
        transfer: Int,
        prevRecent: Boolean,
        lastUsedMs: Long,
        nowMs: Long,
    ): Double {
        val hwPart = if (hwDayType >= 3) hwDayType else 0
        val trPart = if (prevRecent && transferDominant(transfer, total)) 500.0 else 0.0
        val recentPenalty =
            if (lastUsedMs != 0L && nowMs - lastUsedMs < 30 * 60 * 1000) 2.0 else 0.0
        return hwPart * 10000.0 + total + trPart - recentPenalty
    }

    /**
     * Dock 说明页理由（纯函数）：与排序同源——时段聚合 ≥3、转移占主导
     * （≥2 且占比 ≥0.5）、近 7 天窗口计数 >0。
     * “常开”分支的触发与显示都用窗口计数 [recent]，不再用衰减后总量：
     * recent > 0 →“常开 · 近 7 天 X 次”；总量 >0 但 7 天内没开过 →
     * “最近没怎么开”，绝不显示“近 7 天 0 次”。
     */
    fun dockReason(
        pinned: Boolean,
        hwDayType: Int,
        dow: Int,
        hour: Int,
        prevLabel: String?,
        prevRecent: Boolean,
        transfer: Int,
        total: Int,
        recent: Int,
    ): String {
        return when {
            pinned -> "你固定在 Dock 上"
            hwDayType >= 3 -> {
                val weekday = when (dow) {
                    6 -> "周六"
                    7 -> "周日"
                    else -> "工作日"
                }
                val period = when (hour) {
                    in 0..5 -> "夜里"
                    in 6..11 -> "早上"
                    in 12..13 -> "中午"
                    in 14..17 -> "下午"
                    else -> "晚上"
                }
                "$weekday$period $hour 点常开"
            }
            prevRecent && prevLabel != null && transferDominant(transfer, total) -> "打开 $prevLabel 后常接着开"
            recent > 0 -> "常开 · 近 ${RECENT_WINDOW_DAYS} 天 $recent 次"
            total > 0 -> "最近没怎么开"
            else -> "热门应用（暂无你的使用记录）"
        }
    }

    /**
     * 取 [count] 个最常用应用。
     * @param smartSort true=智能排序（智能 v3：当前小时的日类型聚合桶
     *   ≥3 时 ×10000 主导 + 衰减后总次数 + 10 分钟内转移 ≥2 时 +500
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
        // 当前小时的日类型聚合桶 ≥3 才参与主导（×10000），否则按 0 计——
        // 稀疏格子不靠一两次偶然上位；转移只看 10 分钟内且 ≥2 次；-2 惩罚
        // 保留（防单调）。分值与理由同源，走 [dockScore] 纯函数。
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
            val hw = hwDayTypeCount(context, pkg, dow, hour)
            val tr = p?.getInt(KEY_TR_PREFIX + prevPkg + "_" + pkg, 0) ?: 0
            Scored(app, dockScore(hw, total, tr, prevRecent, last, now), last)
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
