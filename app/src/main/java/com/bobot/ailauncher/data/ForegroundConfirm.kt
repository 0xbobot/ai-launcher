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
 * 见到就不取消（通知留着，用户可左滑清）。2026-11-11 改：拿不到用量
 * 统计权限（UNKNOWN）时也不取消——无法判定就别动通知（Bob 2026-11-11
 * 09:53 亲报：Google 健康行点了没打开、消息却不见了，就是 UNKNOWN
 * 立即取消那条退路清掉的）。
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

/** 回退核验（scheduleFallbackIfNotForeground）最多轮询次数：10 次，窗口
 * 约 2 秒——contentIntent 静默无动作时，2 秒内没见到目标到前台才回退。 */
const val FOREGROUND_FALLBACK_MAX_POLLS = 10

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
    /** 拿不到用量统计权限（或事件查不到）：无法判定，不取消、不回退。 */
    UNKNOWN
}

/**
 * 纯函数：给定窗内全部前台事件，判定目标应用是否到过前台。
 * - 没权限 → UNKNOWN（不取消，见 ForegroundConfirmResult.UNKNOWN）；
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

/** 一轮前台查询走完后，下一步做什么（纯函数，供取消调度与回退核验共用）。 */
enum class ForegroundPollStep {
    /** 目标到过前台：取消调度走 onConfirmed；回退核验什么都不做。 */
    CONFIRMED,
    /** 窗内一次也没见到目标：取消调度就此停（不取消）；回退核验走 onNotSeen。 */
    EXHAUSTED,
    /** 继续下一轮。 */
    CONTINUE,
    /** 无法判定（没权限或查不到事件）：两个调度都就此停，不取消、不回退。 */
    UNDECIDABLE
}

/**
 * 纯函数：给定一轮查询结果与已轮询次数，判定下一步。
 * - CONFIRMED → CONFIRMED（第几轮见到都算）；
 * - UNKNOWN → UNDECIDABLE（不取消、不回退）；
 * - NOT_SEEN 且未到上限 → CONTINUE；已到上限 → EXHAUSTED。
 */
fun decidePollStep(
    result: ForegroundConfirmResult,
    polls: Int,
    maxPolls: Int
): ForegroundPollStep = when (result) {
    ForegroundConfirmResult.CONFIRMED -> ForegroundPollStep.CONFIRMED
    ForegroundConfirmResult.UNKNOWN -> ForegroundPollStep.UNDECIDABLE
    ForegroundConfirmResult.NOT_SEEN ->
        if (polls >= maxPolls) ForegroundPollStep.EXHAUSTED else ForegroundPollStep.CONTINUE
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
 * 未授权限或查询失败返回 null（调用方按 UNKNOWN 处理，不取消、不回退）。
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
 * 两个前台轮询调度器（取消 + 回退核验）：确认目标应用到了前台才回调
 * onConfirmed（由调用方取消通知）或停下。
 *
 * 必须活过应用中心 dispose——用户点行打开应用后应用中心浮层就没了，组合
 * 作用域取消会把延迟取消一起杀掉。所以不用组合作用域，用主线程 Handler +
 * applicationContext（应用单例，持有无泄漏），轮询与取消都不依赖任何组合状态。
 * 同一包同一调度同时只排一轮（重复点按不重复排队；取消本身幂等，多排一次也无害）。
 * 回调抛异常也不杀轮询线程（包住）。
 */
object ForegroundConfirm {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableSetOf<String>()
    private val pendingFallback = mutableSetOf<String>()

    /** 一轮前台查询的结果（两个调度器共用）。 */
    private fun pollResult(
        appContext: Context,
        targetPkg: String
    ): ForegroundConfirmResult {
        return try {
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
    }

    fun scheduleCancelIfForeground(
        appContext: Context,
        targetPkg: String,
        tapWallMillis: Long = 0L,
        onConfirmed: () -> Unit
    ) {
        if (!pending.add(targetPkg)) return
        var polls = 0
        val task = object : Runnable {
            override fun run() {
                polls++
                val pollWallMillis = System.currentTimeMillis()
                when (decidePollStep(pollResult(appContext, targetPkg), polls, FOREGROUND_MAX_POLLS)) {
                    ForegroundPollStep.CONFIRMED -> {
                        pending.remove(targetPkg)
                        val cancelOutcome = try {
                            onConfirmed()
                            "cancelled"
                        } catch (e: Exception) {
                            // 取消失败不报错（角标不动，用户可再滑一次），
                            // 但记进日志——取消失败本身就是一次例外。
                            "cancel_error:${e.javaClass.simpleName}"
                        }
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onCancelOutcome(
                                appContext, tapWallMillis, targetPkg,
                                cancel = cancelOutcome, fg = "CONFIRMED",
                                fgMs = pollWallMillis - tapWallMillis,
                            )
                        }
                    }
                    ForegroundPollStep.EXHAUSTED -> {
                        pending.remove(targetPkg)
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onCancelOutcome(
                                appContext, tapWallMillis, targetPkg,
                                cancel = "kept", fg = "NOT_SEEN", fgMs = null,
                            )
                        }
                    }
                    ForegroundPollStep.UNDECIDABLE -> {
                        pending.remove(targetPkg)
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onCancelOutcome(
                                appContext, tapWallMillis, targetPkg,
                                cancel = "kept", fg = "UNKNOWN", fgMs = null,
                            )
                        }
                    }
                    ForegroundPollStep.CONTINUE ->
                        handler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MILLIS)
                }
            }
        }
        handler.post(task)
    }

    /**
     * 回退核验调度器（Bob 2026-11-11 09:53 亲报：Google 健康未读行点了没
     * 打开、消息却不见了——contentIntent.send() 不抛错也可能静默无动作，
     * Android 12+ 通知跳板拦截时系统在启动时悄悄拦掉，发送方拿不到异常；
     * 上面那条是假设，见收据）。
     *
     * 在 contentIntent.send() 返回成功后排：2 秒窗内见到目标到过前台就停，
     * 什么都不做（取消仍由 scheduleCancelIfForeground 那边处理）；2 秒都
     * 没见到就回调 onNotSeen（调用方回退 launchApp 直接打开）。没权限
     * （UNKNOWN）时无法核验，就此停、不回退。
     *
     * 与取消调度分开排队（各自的 pending 集）：两边同时跑、互不挡——取消
     * 调度 1 秒窗内确认了就取消通知，回退核验 2 秒窗内确认了就停；都没见
     * 到时取消不执行、回退在 2 秒后打开应用。不要去探测中兴跳转确认框
     * （没有可靠 API）：框正弹着、2 秒窗过了时，回退会再发一次 launchApp，
     * 用户可能看到同一对应用的第二个确认框，列为 Bob 真机验收项。
     */
    fun scheduleFallbackIfNotForeground(
        appContext: Context,
        targetPkg: String,
        tapWallMillis: Long = 0L,
        onNotSeen: () -> Boolean
    ) {
        if (!pendingFallback.add(targetPkg)) return
        var polls = 0
        val task = object : Runnable {
            override fun run() {
                polls++
                val pollWallMillis = System.currentTimeMillis()
                when (decidePollStep(pollResult(appContext, targetPkg), polls, FOREGROUND_FALLBACK_MAX_POLLS)) {
                    ForegroundPollStep.EXHAUSTED -> {
                        pendingFallback.remove(targetPkg)
                        val opened = try {
                            onNotSeen()
                        } catch (_: Exception) {
                            // 回退打开失败不报错（行留着，用户可从字母组/Dock 打开）
                            false
                        }
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onFallbackOutcome(
                                appContext, tapWallMillis, targetPkg,
                                fired = true, opened = opened,
                                ms = pollWallMillis - tapWallMillis,
                                fg = "NOT_SEEN", fgMs = null, bg = null, bgMs = null,
                            )
                        }
                    }
                    ForegroundPollStep.CONFIRMED -> {
                        pendingFallback.remove(targetPkg)
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onFallbackOutcome(
                                appContext, tapWallMillis, targetPkg,
                                fired = false, opened = null, ms = null,
                                fg = "CONFIRMED", fgMs = pollWallMillis - tapWallMillis,
                                bg = null, bgMs = null,
                            )
                        }
                    }
                    ForegroundPollStep.UNDECIDABLE -> {
                        pendingFallback.remove(targetPkg)
                        if (tapWallMillis != 0L) {
                            UnreadJumpLog.onFallbackOutcome(
                                appContext, tapWallMillis, targetPkg,
                                fired = false, opened = null, ms = null,
                                fg = "UNKNOWN", fgMs = null, bg = null, bgMs = null,
                            )
                        }
                    }
                    ForegroundPollStep.CONTINUE ->
                        handler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MILLIS)
                }
            }
        }
        handler.post(task)
    }
}
