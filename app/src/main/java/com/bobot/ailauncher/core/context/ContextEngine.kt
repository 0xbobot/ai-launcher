package com.bobot.ailauncher.core.context

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Context Engine 基础版（PRD §十八，技术方案 Phase 3）。
 *
 * 当前提供：
 * - 时间段（MORNING/AFTERNOON/EVENING/NIGHT）：Dock 智能排序、Pet 状态机共用
 * - 前台应用追踪：UsageStats 轮询（60 秒，best-effort）→ AppOpened/AppClosed 事件
 * - 屏幕 on/off → ScreenOn/ScreenOff 事件（亮屏时立即追一次前台）
 *
 * 全部 best-effort：无权限/异常时静默跳过，不影响 Launcher。
 * 轮询是无奈之举——Android 没有前台变化的事件 API（无障碍除外，PRD 明确不做核心路径）。
 */
object ContextEngine {

    enum class TimeSlot { MORNING, AFTERNOON, EVENING, NIGHT }

    /** 6–11 早，11–17 下午，17–23 晚，其余夜 */
    fun currentSlot(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): TimeSlot =
        when (hour) {
            in 6..10 -> TimeSlot.MORNING
            in 11..16 -> TimeSlot.AFTERNOON
            in 17..22 -> TimeSlot.EVENING
            else -> TimeSlot.NIGHT
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var lastForeground: String? = null
    private var started = false

    /** 幂等启动 */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val appCtx = context.applicationContext

        // 屏幕 on/off → 事件
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        EventBus.emit(LauncherEvent.ScreenOn)
                        scope.launch { pollForeground(appCtx) }
                    }
                    Intent.ACTION_SCREEN_OFF ->
                        EventBus.emit(LauncherEvent.ScreenOff)
                }
            }
        }
        receiver = r
        try {
            appCtx.registerReceiver(
                r,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            )
        } catch (t: Throwable) {
            Log.w("ContextEngine", "registerReceiver failed", t)
        }

        // 前台应用轮询（60 秒一次，低功耗）
        pollJob = scope.launch {
            pollForeground(appCtx)
            while (isActive) {
                delay(60_000)
                pollForeground(appCtx)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        started = false
    }

    /** 查最近 90 秒内最后一次前台的包，变化则发事件 */
    private fun pollForeground(context: Context) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 90_000,
                now
            ).orEmpty()
            val top = stats
                .filter { it.packageName != context.packageName && it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
            if (top != null && top != lastForeground) {
                val prev = lastForeground
                lastForeground = top
                if (prev != null) EventBus.emit(LauncherEvent.AppClosed(prev))
                EventBus.emit(LauncherEvent.AppOpened(top))
            }
        } catch (_: SecurityException) {
            // 未授权用量访问：静默跳过（AppDiscovery 同策略）
        } catch (t: Throwable) {
            Log.w("ContextEngine", "pollForeground failed", t)
        }
    }
}
