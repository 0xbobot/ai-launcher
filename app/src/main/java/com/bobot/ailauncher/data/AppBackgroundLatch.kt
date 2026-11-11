package com.bobot.ailauncher.data

import android.os.Handler
import android.os.Looper

/**
 * 门闩回退（Bob 2026-11-11 10:52：微信「和和美美」行点了没打开、行也没了——
 * 同 Google 健康行一例同类 bug：contentIntent.send() 不抛错也可能静默无动作，
 * 最可能 Android 12+ 通知跳板拦截，是假设）。
 *
 * 490 的前台核验回退只在用量统计权限已授时做；Bob 2026-11-11 10:33 定了
 * 先不申请权限，那件在他手机上休眠——行会留着（不丢了），但跳转本身还是
 * 打不开。这一半是不用权限的回退：信号是我们自己的应用有没有切到后台。
 *
 * 信号（门闩）：跳成功了，我们的 Activity 一定会 onPause/切后台；静默丢
 * 了，我们还停在前台。只认点按之后的那次——门闩记的是最近一次切后台的
 * 时间戳，核验时拿点按时间比（早于点按的旧暂停不算）。
 *
 * 依赖选择（动手前核过）：ProcessLifecycleOwner 不在——版本目录里只有
 * lifecycle-runtime-ktx（2.8.6），全仓无 lifecycle-process，也无任何引用；
 * 简报说不要为这事新加依赖。所以接 MainActivity.onPause（应用中心是盖
 * 在同一个 Activity 上的浮层，进应用中心本身不触发 onPause，不会误触发门闩）。
 *
 * 时间用单调时钟（门闩时间戳与点按时间都由 SystemClock.uptimeMillis()
 * 给，调用点传进来）：门闩对象本身只做时间算术、不调系统时钟，所以纯函数
 * 判定与门闩对象都能在本地单测里测；Handler 延迟检查是系统调用，验收走
 * Bob 真机（与 ForegroundConfirm 同款口径，不测 Handler）。
 *
 * 故意不用 onResume 的“回来”做二次判定：只认 onPause 一次就够了。原因——
 * 如果中兴跳转确认框是 Activity 实现的，它弹出来也会让我们 onPause；
 * 用户在框里点取消回来，我们再把“回来”当失败去重发 launchApp，等于在他
 * 明确取消后又把框弹一遍（479 的核心反例：框里点取消，通知必须留着、什么
 * 都不该再发生）。所以 pause-only：框里取消那一例里门闩只当“跳成功了”，
 * 不回退、行留着，用户再点才行。这是保守选择，写清在收据里。
 */
object AppBackgroundLatch {

    /** 门闩回退的等待时长：点按后约 2 秒检查一次，与有权限回退的 2 秒窗一致。 */
    const val LATCH_FALLBACK_DELAY_MILLIS = 2000L

    // 主线程 Handler：lazy——对象在本地单测里只测时间算术，类加载时就建
    // Handler 会在单测 JVM 里炸（Looper.getMainLooper() 为空）；真正排检查
    // 时才建，跑在有 Looper 的主线程上。
    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val pending = mutableSetOf<String>()

    /** 最近一次切后台的时间（调用点给的 uptimeMillis）；从没切过后台是下界。 */
    @Volatile
    private var lastBackgroundedAtMillis: Long = Long.MIN_VALUE

    /** MainActivity.onPause 里调用：记下这次切后台的时间。 */
    fun markBackgrounded(atMillis: Long) {
        lastBackgroundedAtMillis = atMillis
    }

    /** 点按之后我们的进程到过后台没有（早于点按的旧暂停不算）。 */
    fun backgroundedSince(sinceMillis: Long): Boolean =
        lastBackgroundedAtMillis >= sinceMillis

    /** 单测隔离用：把门闩复位到“从没切过后台”。 */
    fun resetForTest() {
        lastBackgroundedAtMillis = Long.MIN_VALUE
    }

    /**
     * 门闩回退调度器（只在没用量统计权限时排——有权限时已有
     * ForegroundConfirm.scheduleFallbackIfNotForeground 在管，一次点按只走
     * 一条回退路）。
     *
     * 点按后约 2 秒检查一次：窗内我们的进程到过后台（门闩动过）= 跳成功了，
     * 什么都不做；一次都没到过 = 跳失败了，回调 onNotSeen（调用方回退
     * launchApp 直接打开）。主线程 Handler，活过应用中心 dispose（与
     * ForegroundConfirm 同款；这里不握 Context、不读系统服务，所以不传
     * appContext——没东西可泄漏）。同一包同时只排一轮（重复点按不重复排队；
     * 第一轮的检查已覆盖后一次点按：后一次点按后的后台切换时间戳也在第一
     * 轮的点按时间之后，照样算跳成功；都没动过时一轮回退就够）。
     * 回调抛异常也不杀 Handler（包住）。
     */
    fun scheduleFallbackIfNotBackgrounded(
        targetPkg: String,
        tapUptimeMillis: Long,
        onNotSeen: () -> Unit
    ) {
        if (!pending.add(targetPkg)) return
        handler.postDelayed(
            {
                pending.remove(targetPkg)
                if (decideLatchFallback(backgroundedSince(tapUptimeMillis))) {
                    try {
                        onNotSeen()
                    } catch (_: Exception) {
                        // 回退打开失败不报错（行留着，用户可从字母组/Dock 打开）
                    }
                }
            },
            LATCH_FALLBACK_DELAY_MILLIS
        )
    }
}

/**
 * 纯函数：门闩回退要不要开。
 * - 点按之后我们的进程到过后台 = 跳成功了，不回退；
 * - 一次都没到过 = 跳失败了，回退直接打开。
 */
fun decideLatchFallback(backgroundedSinceTap: Boolean): Boolean =
    !backgroundedSinceTap
