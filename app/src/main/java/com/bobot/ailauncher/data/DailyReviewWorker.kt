package com.bobot.ailauncher.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 每天一次整体复盘的 WorkManager 任务（第四十四批，Bob 2026-11-16 定稿）：
 * 每天晚上 21:30 跑一次 [SmartCardRepository.runDailyReview]。
 *
 * 调度：[DailyReviewScheduler.schedule] 在 LauncherApp.onCreate 里调一次，
 * UniquePeriodicWork（KEEP，幂等）；首次延迟到下一个 21:30（见
 * [reviewDelayToNextTriggerMs]），之后每 24 小时一次。设备在 21:30
 * 关机/休眠时，醒来后 WorkManager 补跑（周期任务到期即跑）；若补跑
 * 仍没赶上，refresh() 里的 maybeRunDailyReview 是第二层兜底。
 *
 * 约束：需要联网（NetworkType.CONNECTED）——没网时等有网再跑，
 * 不跑空任务。失败不重试：doWork 一律返回 Result.success()（不让
 * WorkManager 退避重试），复盘一天一次、失败等下次触发点（见
 * SmartCardRepository.runDailyReview「失败也算一次」的口径）。
 */
class DailyReviewWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            SmartCardRepository.runDailyReview(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // 静默：任何异常都不重试、不报错，等下次触发点
            Result.success()
        }
    }
}

object DailyReviewScheduler {
    const val UNIQUE_WORK_NAME = "daily-smart-review"

    fun schedule(context: Context) {
        try {
            val delayMs = reviewDelayToNextTriggerMs(System.currentTimeMillis())
            val request = PeriodicWorkRequestBuilder<DailyReviewWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        } catch (_: Exception) {
            // 静默：调度不上，refresh() 的补跑兜底还在，不崩
        }
    }
}
