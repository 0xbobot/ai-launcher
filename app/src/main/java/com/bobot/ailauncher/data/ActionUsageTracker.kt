package com.bobot.ailauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

/**
 * 智能滚动关键字·用户动作记录（Bob 2026-11-05：记用户成功打开应用的动作，
 * 和应用推荐一样按频次、时间习惯排列，加入输入框滚动关键字清单）。
 *
 * 记的是动作本身（能力＋目的地，如“导航去机场”，不是包名——同一地图应用
 * 去机场和去公司是两个动作），只在能力动作成功打开应用时记（深链发出/
 * 应用打开成功才算，失败的不记）。
 *
 * 存储复用 AppUsageTracker 的思路（星期×小时桶 hw_<dow>_<hour>_<key> ＋
 * 总次数 count_<key> ＋ last_<key>），但另开一份 SharedPreferences
 * "action_usage"——动作键不是包名，和 "app_usage" 混放会污染 Dock 排序的
 * 数据面；排序打分与 Dock 智能 v3 同一套（当前桶 ≥3 时 ×10000 主导 ＋
 * 衰减后总次数，并列按最近），不另造排序体系。月度衰减与 AppUsageTracker
 * 同规矩（30 天半衰的整数实现，首跑建基线不追溯减半）。
 */
object ActionUsageTracker {
    private const val PREFS = "action_usage"
    private const val KEY_COUNT_PREFIX = "count_"
    private const val KEY_HW_PREFIX = "hw_"
    private const val KEY_LAST_PREFIX = "last_"
    private const val KEY_LAST_DECAY_MONTH = "lastDecayMonth"
    /** 动作键内能力与目的地的分隔（控制字符，不会出现在目的地里） */
    private const val KEY_SEP = "\u0001"
    /** 进入个性前排的门槛（与 Dock v3 的“桶 ≥3 才主导”同数）：当前桶 ≥3，
     *  或总次数 ≥3（跨时段的习惯）；低于此的动作算“很少用”，不进前排。 */
    private const val MIN_COUNT_FOR_FRONT = 3

    /** 动作记录版本：每记一条 +1，供胶囊 remember 跟着重算个性清单。 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** 一条已记动作（供胶囊拼个性清单用）。 */
    data class TopAction(val capabilityId: String, val destination: String?, val score: Double)

    /** 动作键：能力 id，带目的地时再拼目的地（"daohang\u0001机场"）；无目的地时只有能力 id。 */
    fun actionKey(capabilityId: String, destination: String?): String =
        if (destination.isNullOrBlank()) capabilityId else capabilityId + KEY_SEP + destination

    /** 动作键还原（能力 id，目的地，无目的地时 null）。 */
    fun parseActionKey(key: String): Pair<String, String?> {
        val idx = key.indexOf(KEY_SEP)
        return if (idx < 0) key to null else key.substring(0, idx) to key.substring(idx + 1)
    }

    /** 当前星期（周一=1…周日=7）与小时（0-23），与 AppUsageTracker 同口径。 */
    fun currentDowHour(): Pair<Int, Int> = AppUsageTracker.currentDowHour()

    private fun monthOf(nowMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    private fun dowHourOf(nowMs: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        val dow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return dow to cal.get(Calendar.HOUR_OF_DAY)
    }

    /** 成功打开才记（生产入口）：opened 为假时什么都不写。 */
    fun recordIfOpened(context: Context, opened: Boolean, capabilityId: String, destination: String?) {
        if (!opened) return
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val (dow, hour) = dowHourOf(now)
            recordIfOpened(p, true, capabilityId, destination, dow, hour, now)
        } catch (_: Exception) {
        }
    }

    /** 成功打开才记（可测入口，prefs 与时间显式传入）：opened 为假时什么都不写。 */
    fun recordIfOpened(
        prefs: SharedPreferences,
        opened: Boolean,
        capabilityId: String,
        destination: String?,
        dow: Int,
        hour: Int,
        nowMs: Long
    ) {
        if (!opened) return
        try {
            val key = actionKey(capabilityId, destination)
            val editor = prefs.edit()
            // 月度衰减（与 AppUsageTracker 同规矩）：年月变化时全部 Int 计数值
            // 按经过月数连续减半；首跑建基线不追溯减半。
            val decayed = mutableMapOf<String, Int>()
            if (!prefs.contains(KEY_LAST_DECAY_MONTH)) {
                editor.putInt(KEY_LAST_DECAY_MONTH, monthOf(nowMs))
            } else {
                val months = monthOf(nowMs) - prefs.getInt(KEY_LAST_DECAY_MONTH, 0)
                if (months > 0) {
                    for ((k, v) in prefs.all) {
                        if (v is Int && (k.startsWith(KEY_COUNT_PREFIX) || k.startsWith(KEY_HW_PREFIX))) {
                            var nv = v as Int
                            repeat(months) { nv /= 2 }
                            decayed[k] = nv
                        }
                    }
                    for ((k, v) in decayed) editor.putInt(k, v)
                    editor.putInt(KEY_LAST_DECAY_MONTH, monthOf(nowMs))
                }
            }
            fun curInt(k: String, def: Int = 0): Int = decayed[k] ?: prefs.getInt(k, def)
            val hwKey = KEY_HW_PREFIX + dow + "_" + hour + "_" + key
            editor.putInt(KEY_COUNT_PREFIX + key, curInt(KEY_COUNT_PREFIX + key) + 1)
            editor.putInt(hwKey, curInt(hwKey) + 1)
            editor.putLong(KEY_LAST_PREFIX + key, nowMs)
            editor.apply()
            _version.update { it + 1 }
        } catch (_: Exception) {
        }
    }

    /**
     * 应用中心点按路径：成功打开后，若整句是该应用的能力动作（tapAction
     * 匹配：含带参能力 label、抠得出目的地、被点应用是该能力提供方），记
     * 一条；不是能力动作（纯打开应用）或打开失败时不记。调用方只在打开
     * 成功后调（opened 为真才记）。
     */
    fun recordTapAction(context: Context, query: String, packageName: String, opened: Boolean) {
        if (!opened) return
        try {
            CapabilityRegistry.load(context)
            val (cap, dest) = CapabilityResolution.tapAction(
                CapabilityRegistry.groups().flatMap { it.capabilities }, query, packageName
            ) ?: return
            recordIfOpened(context, true, cap.id, dest)
        } catch (_: Exception) {
        }
    }

    /** 当前时间最相关的动作（生产入口）：按 v3 打分取前 count 条。 */
    fun topActions(context: Context, count: Int = 3): List<TopAction> {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val (dow, hour) = currentDowHour()
            topActions(p, dow, hour, count)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 当前时间最相关的动作（可测入口）：Dock 智能 v3 同一套打分——当前
     * dow+hour 桶 ≥3 时 ×10000 主导 ＋衰减后总次数，并列按最近；只有
     * “当前桶 ≥3 或总次数 ≥3”的动作才进前排（很少用的不进），最多 count 条。
     * 没历史时返回空，调用方清单退回纯静态能力词。
     */
    fun topActions(prefs: SharedPreferences, dow: Int, hour: Int, count: Int = 3): List<TopAction> {
        return try {
            val all = prefs.all
            val keys = all.keys
                .filter { it.startsWith(KEY_COUNT_PREFIX) }
                .map { it.removePrefix(KEY_COUNT_PREFIX) }
            val scored = keys.mapNotNull { key ->
                val total = (all[KEY_COUNT_PREFIX + key] as? Int) ?: 0
                if (total <= 0) return@mapNotNull null
                val hw = (all[KEY_HW_PREFIX + dow + "_" + hour + "_" + key] as? Int) ?: 0
                if (hw < MIN_COUNT_FOR_FRONT && total < MIN_COUNT_FOR_FRONT) return@mapNotNull null
                val last = (all[KEY_LAST_PREFIX + key] as? Long) ?: 0L
                val hwPart = if (hw >= MIN_COUNT_FOR_FRONT) hw else 0
                val (capabilityId, destination) = parseActionKey(key)
                TopAction(capabilityId, destination, hwPart * 10000.0 + total) to last
            }
            scored.sortedWith(
                compareByDescending<Pair<TopAction, Long>> { it.first.score }
                    .thenByDescending { it.second }
            ).take(count).map { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
