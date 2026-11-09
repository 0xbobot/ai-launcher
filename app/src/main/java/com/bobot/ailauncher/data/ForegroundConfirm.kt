package com.bobot.ailauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * 前台确认（Bob 2026-11-09 12:20 ②：未读行点按有时只清消息、不打开应用——
 * 行点按是“调起返回成功就取消”：contentIntent.send() / startActivity 返回
 * 正常就算打开成功，但中兴跳转确认框里点取消、或跳转目标没出现时，应用
 * 根本没打开，通知却已经取消了）。
 *
 * 修法：先打开、确认应用真的到了前台再取消。点按后在主线程 Handler 上
 * 轮询（200ms 一次、最多 1 秒），见到目标应用到过前台就取消；1 秒内没
 * 见到就不取消（通知留着，用户可左滑清）；没拿到用量统计权限时退回
 * “调起成功就取消”（老行为）。
 *
 * 前台判定走 UsageStatsManager.queryEvents（应用已为折叠组在用，有权限
 * 基础设施；PACKAGE_USAGE_STATS Manifest 已声明，不新增权限）。
 * ActivityManager 新版拿不到别家应用的前台状态，不用它当主路。
 * 轮询窗内任一时刻见到目标到前台就算确认——用户打开后 1 秒内又切走
 * 也算（打开过就是读到了），只查“窗末是不是前台”会漏掉这种。
 */

/** 轮询间隔：200ms。 */
const val FOREGROUND_POLL_INTERVAL_MILLIS = 200L

/** 最多轮询次数：5 次，窗口约 1 秒。 */
const val FOREGROUND_MAX_POLLS = 5

/** 事件回看窗口：每次轮询查最近 10 秒的事件，刚发生、还没被下次轮询
 * 看到的事件不丢（事件上报本身可能滞后几百毫秒）。 */
const val FOREGROUND_LOOKBACK_MILLIS = 10_000L

/** 一条前台事件：包名 + 是进前台还是退后台。 */
data class ForegroundEvent(val packageName: String, val isForeground: Boolean)

/** 前台确认的三种结论。 */
enum class ForegroundConfirmResult {
    /** 窗内见到目标应用到过前台：确认打开了，可以取消通知。 */
    CONFIRMED,
    /** 有权限、窗内有事件，但目标应用一次也没到前台：没打开，不取消。 */
    NOT_SEEN,
    /** 拿不到用量统计权限（或事件查不到）：无法判定，退回老行为（调起成功就取消）。 */
    UNKNOWN
}

/**
 * 纯函数：给定窗内全部前台事件，判定目标应用是否到过前台。
 * - 没权限 → UNKNOWN（退回老行为）；
 * - 有权限、事件里有目标应用的进前台事件 → CONFIRMED；
 * - 有权限、事件里没有目标应用 → NOT_SEEN（事件为空也算 NOT_SEEN——
 *   有权限时查不到事件就是窗内没人切前台）。
 */
fun decideForegroundConfirm(
    events: List<ForegroundEvent>?,
    targetPkg: String,
    hasPermission: Boolean
): ForegroundConfirmResult {
    if (!hasPermission) return ForegroundConfirmResult.UNKNOWN
    val list = events ?: return ForegroundConfirmResult.UNKNOWN
    if (list.any { it.packageName == targetPkg && it.isForeground }) {
        return ForegroundConfirmResult.CONFIRMED
    }
    return ForegroundConfirmResult.NOT_SEEN
}

/** 用量统计权限是否已授（AppOps 现查；未授时 queryEvents 拿不到事件）。 */
fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

/**
 * 查一段窗口内的前台事件（只取进/退前台两种事件）。
 * 未授权限或查询失败返回 null（调用方按 UNKNOWN 处理，退回老行为）。
 */
fun queryForegroundEvents(
    context: Context,
    startMillis: Long,
    endMillis: Long
): List<ForegroundEvent>? {
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startMillis, endMillis) ?: return null
        val out = mutableListOf<ForegroundEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    out.add(ForegroundEvent(e.packageName, true))
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    out.add(ForegroundEvent(e.packageName, false))
            }
        }
        out
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * 延迟取消调度器：确认目标应用到了前台才回调 onConfirmed（由调用方取消通知）。
 *
 * 必须活过应用中心 dispose——用户点行打开应用后应用中心浮层就没了，组合
 * 作用域取消会把延迟取消一起杀掉。所以不用组合作用域，用主线程 Handler +
 * applicationContext（应用单例，持有无泄漏），轮询与取消都不依赖任何组合状态。
 * 同一包同时只排一轮（重复点按不重复排队；取消本身幂等，多排一次也无害）。
 * onConfirmed 抛异常也不杀轮询线程（包住）。
 */
object ForegroundConfirm {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<String>()

    fun scheduleCancelIfForeground(
        appContext: Context,
        targetPkg: String,
        onConfirmed: () -> Unit
    ) {
        if (!pending.add(targetPkg)) return
        var polls = 0
        val task = object : Runnable {
            override fun run() {
                polls++
                val result = try {
                    if (!hasUsageStatsPermission(appContext)) {
                        ForegroundConfirmResult.UNKNOWN
                    } else {
                        val now = System.currentTimeMillis()
                        val events = queryForegroundEvents(
                            appContext,
                            now - FOREGROUND_LOOKBACK_MILLIS,
                            now
                        )
                        decideForegroundConfirm(events, targetPkg, true)
                    }
                } catch (_: Exception) {
                    ForegroundConfirmResult.UNKNOWN
                }
                when (result) {
                    ForegroundConfirmResult.CONFIRMED,
                    ForegroundConfirmResult.UNKNOWN -> {
                        pending.remove(targetPkg)
                        try {
                            onConfirmed()
                        } catch (_: Exception) {
                            // 取消失败不报错（角标不动，用户可再滑一次）
                        }
                    }
                    ForegroundConfirmResult.NOT_SEEN -> {
                        if (polls >= FOREGROUND_MAX_POLLS) {
                            pending.remove(targetPkg)
                        } else {
                            handler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MILLIS)
                        }
                    }
                }
            }
        }
        handler.post(task)
    }
}
