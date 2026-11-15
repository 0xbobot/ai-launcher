package com.bobot.ailauncher.data

import android.content.Context
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * 起床窗学习（节点一，方案 ac-missing-nodes-plan.md 第一批）：
 * ContextEngine 只发 LauncherEvent.ScreenOn、不记时间；这里订阅它，
 * 记最近 14 天每天第一次亮屏的钟点。起床窗 = 最近 7 天第一次亮屏的
 * 中位数 ±60 分钟，夹在 5:00–10:00；冷启动（没攒够 7 天）用
 * 6:30–8:30 默认值。卡片上不写窗口怎么来的。
 *
 * 睡前窗 v1 固定 21:30 锚点（21:30 → 24:00）；学睡前窗是 v2，不做。
 */
object WakeWindowStore {
    private const val PREFS = "wake_window"
    private const val KEY_PREFIX = "first_screen_on_"

    /** 保留天数：只记最近 14 天。 */
    private const val KEEP_DAYS = 14

    /** 学习天数：中位数取最近 7 天。 */
    private const val LEARN_DAYS = 7

    /** 冷启动默认值：6:30–8:30（分钟）。 */
    val DEFAULT_WINDOW_MINUTES: Pair<Int, Int> = 390 to 510

    /** 睡前窗锚点：21:30（分钟）。 */
    private const val SLEEP_ANCHOR_MINUTES = 21 * 60 + 30

    private var started = false

    /** 启动：订阅 EventBus 的 ScreenOn，记每天第一次亮屏。MainActivity 里随 ContextEngine.start 一起调。 */
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            EventBus.events.filterIsInstance<LauncherEvent.ScreenOn>().collect {
                try {
                    recordScreenOn(appContext)
                } catch (_: Exception) {
                    // 记录失败不影响桌面。
                }
            }
        }
    }

    /** 当天第一次亮屏的钟点（分钟）；只记第一次，已记过的不覆盖。 */
    fun minutesOfDay(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** 记一次亮屏（只记当天第一次），并顺手清掉 14 天前的旧记录。 */
    fun recordScreenOn(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + dateKeyOf(nowMs)
        if (prefs.contains(key)) return
        val editor = prefs.edit()
        // yyyyMMdd 按字典序即时间序：清掉比 14 天前更早的记录。
        val cutoff = dateKeyOf(nowMs - (KEEP_DAYS - 1) * 86_400_000L)
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) && it.substringAfter(KEY_PREFIX) < cutoff }
            .forEach { editor.remove(it) }
        editor.putInt(key, minutesOfDay(nowMs))
        editor.apply()
    }

    /** 中位数（偶数个取中间两个的平均，向下取整）。 */
    fun medianMinutes(samples: List<Int>): Int {
        val sorted = samples.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }

    /**
     * 起床窗（分钟）：样本不足 7 天 → 冷启动默认值；否则中位数 ±60，
     * 中心夹在 6:00–9:00（窗口整体落在 5:00–10:00）。
     */
    fun wakeWindowMinutes(samples: List<Int>): Pair<Int, Int> {
        if (samples.size < LEARN_DAYS) return DEFAULT_WINDOW_MINUTES
        val center = medianMinutes(samples).coerceIn(360, 540)
        return (center - 60) to (center + 60)
    }

    /** 分钟窗换算到今天（毫秒）：[startMs, endMs)。 */
    fun wakeWindowMs(todayStartMs: Long, windowMinutes: Pair<Int, Int>): Pair<Long, Long> =
        (todayStartMs + windowMinutes.first * 60_000L) to
            (todayStartMs + windowMinutes.second * 60_000L)

    /** 睡前窗（毫秒）：当天 21:30 → 24:00，固定锚点。 */
    fun sleepWindowMs(nowMs: Long): Pair<Long, Long> {
        val startOfDay = startOfDayMs(nowMs)
        return (startOfDay + SLEEP_ANCHOR_MINUTES * 60_000L) to
            (startOfDay + 24 * 3_600_000L)
    }

    /** 读最近 7 天（不含今天）的第一次亮屏样本，算今天的起床窗（分钟）。 */
    fun wakeWindowMinutesFor(context: Context, nowMs: Long): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val samples = mutableListOf<Int>()
        var cursor = startOfDayMs(nowMs) - 86_400_000L // 从昨天往回找
        repeat(KEEP_DAYS) {
            if (samples.size >= LEARN_DAYS) return@repeat
            val key = KEY_PREFIX + dateKeyOf(cursor)
            if (prefs.contains(key)) samples += prefs.getInt(key, 0)
            cursor -= 86_400_000L
        }
        return wakeWindowMinutes(samples)
    }

    /** 今天的起床窗（毫秒）。 */
    fun wakeWindowFor(context: Context, nowMs: Long): Pair<Long, Long> =
        wakeWindowMs(startOfDayMs(nowMs), wakeWindowMinutesFor(context, nowMs))
}
