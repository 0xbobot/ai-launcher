package com.bobot.ailauncher.core.intent

import android.content.Context
import com.bobot.ailauncher.core.context.ContextEngine
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.WeatherState
import com.bobot.ailauncher.data.analyzeReplyNeed
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.pet.TodayEventsHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 上下文包的生产采集（意图改进二批）：把 [IntentContextPack] 需要的快照从
 * App 现有数据源里凑齐。原则：
 * - **只读已缓存/已在内存的数据**：天气只读 [WeatherState] 缓存，绝不为 plan 新拉网络；
 *   会议读第一批的 [TodayEventsHolder] 镜像；待回数对内存通知列表跑现成的
 *   [analyzeReplyNeed] 规则（与 TODAY 摘要同一口径）。
 * - 每一项独立 runCatching：任何一项失败只省略该项，永远不让上下文采集拖垮提交。
 * - PackageManager 查询走 IO 线程，不压主线程。
 */
object IntentContextProvider {

    suspend fun current(context: Context): String {
        val appContext = context.applicationContext
        val snapshot = IntentContextSnapshot(
            nowText = runCatching { nowText() }.getOrDefault(""),
            nextMeeting = runCatching { nextMeeting() }.getOrNull(),
            weather = runCatching { weather() }.getOrNull(),
            topApps = runCatching {
                withContext(Dispatchers.IO) { topAppNames(appContext) }
            }.getOrDefault(emptyList()),
            installedApps = runCatching {
                withContext(Dispatchers.IO) { installedAppNames(appContext) }
            }.getOrDefault(emptyList()),
            pendingReplies = runCatching { pendingReplies() }.getOrNull()
        )
        return IntentContextPack.build(snapshot)
    }

    /** "2026年10月21日 星期三 22:20（晚上）"：日期时间 + 时段（ContextEngine 口径） */
    private fun nowText(): String {
        val now = Date()
        val base = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE).format(now)
        val slot = when (ContextEngine.currentSlot()) {
            ContextEngine.TimeSlot.MORNING -> "上午"
            ContextEngine.TimeSlot.AFTERNOON -> "下午"
            ContextEngine.TimeSlot.EVENING -> "晚上"
            ContextEngine.TimeSlot.NIGHT -> "夜里"
        }
        return "$base（$slot）"
    }

    /** 下一会议一行："23:00 展台方案评审 · 3F 会议室"；已结束的不算 */
    private fun nextMeeting(): String? {
        val e = TodayEventsHolder.nextEvent.value ?: return null
        if (e.end <= System.currentTimeMillis()) return null
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.begin))
        val title = e.title.ifBlank { "日程" }
        return if (e.location.isNotBlank()) "$time $title · ${e.location}" else "$time $title"
    }

    /** 天气一句话，只读缓存：首页没拉到过天气就是没有，不为此发网络请求 */
    private fun weather(): String? {
        val w = WeatherState.full.value ?: return null
        val range = if (w.high > 0 || w.low > 0) "（今日 ${w.low}~${w.high}°）" else ""
        return "${w.city} ${w.desc} ${w.temp}°$range"
    }

    private fun topAppNames(context: Context): List<String> {
        val apps = launchableVisibleApps(context)
        if (apps.isEmpty()) return emptyList()
        return AppUsageTracker.topApps(context, apps, 5).map { it.label.toString() }
    }

    /** 已装名录精简版：可启动应用名去重（截断在拼装层按字符预算做） */
    private fun installedAppNames(context: Context): List<String> =
        launchableVisibleApps(context).map { it.label.toString() }.distinct()

    /** 与首页本地层同一口径：去桌面自己、去用户隐藏的应用 */
    private fun launchableVisibleApps(context: Context) =
        try {
            val hidden = HiddenApps.getHidden(context)
            listLaunchableApps(context).filter {
                it.packageName != context.packageName && it.packageName !in hidden
            }
        } catch (_: Exception) {
            emptyList()
        }

    /** 待回消息数：内存通知列表 × 现成规则；没有通知监听数据时返回 null（整项省略） */
    private fun pendingReplies(): Int? {
        val list = NotificationRepository.notifications.value
        if (list.isEmpty()) return null
        return list.count { analyzeReplyNeed(it.title, it.text).needsReply }
    }
}
