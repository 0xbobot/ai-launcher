package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import com.bobot.ailauncher.BuildConfig

/**
 * 崩溃逃生通道（核心版）的纯逻辑：连续崩溃计数与恢复屏触发判定，JVM 可测。
 *
 * 信号源是 CrashLog 写下的崩溃记录文件（uncaught handler 同步落盘），
 * 不是“干净退出”标志——进程被系统正常杀掉不写崩溃记录，不会误判为崩溃。
 * 恢复屏只在连续崩溃时出现：单次崩溃只走现有的冷启动 toast 提示。
 */
object CrashEscapeLogic {
    /** 连续崩溃几次出恢复屏 */
    const val RECOVERY_THRESHOLD = 2

    /** 正常运行多久后计数清零（毫秒） */
    const val GRACE_PERIOD_MILLIS = 60_000L

    /** 计数上限（防坏值溢出） */
    const val MAX_CONSECUTIVE = 100

    data class StartupState(
        val consecutive: Int,
        val lastSeenCrashEpoch: Long,
        val shouldShowRecovery: Boolean
    )

    /**
     * 启动时更新连续崩溃计数。
     *
     * @param maxCrashEpoch 当前最新崩溃记录的时间戳；没有记录时 null
     * @param lastSeenCrashEpoch 上次启动已处理过的最新崩溃时间戳（没有时 0）
     * @param consecutive 当前连续崩溃计数
     * @param storedVersionCode 存下的版本号；与当前版本不同时计数清零
     * @param currentVersionCode 当前版本号
     */
    fun onStartup(
        maxCrashEpoch: Long?,
        lastSeenCrashEpoch: Long,
        consecutive: Int,
        storedVersionCode: Int,
        currentVersionCode: Int
    ): StartupState {
        val safeConsecutive = consecutive.coerceIn(0, MAX_CONSECUTIVE)
        val safeLastSeen = lastSeenCrashEpoch.coerceAtLeast(0L)
        // 升版后旧计数不清零会误弹恢复屏：版本变了就清零，
        // 并把已有崩溃记录都记为“已见”，不让旧记录在新版里再触发。
        if (storedVersionCode != currentVersionCode) {
            return StartupState(
                consecutive = 0,
                lastSeenCrashEpoch = maxCrashEpoch ?: 0L,
                shouldShowRecovery = false
            )
        }
        val newCrash = maxCrashEpoch != null && maxCrashEpoch > safeLastSeen
        if (!newCrash) {
            return StartupState(
                consecutive = safeConsecutive,
                lastSeenCrashEpoch = safeLastSeen,
                shouldShowRecovery = safeConsecutive >= RECOVERY_THRESHOLD
            )
        }
        val next = (safeConsecutive + 1).coerceAtMost(MAX_CONSECUTIVE)
        return StartupState(
            consecutive = next,
            lastSeenCrashEpoch = maxCrashEpoch,
            shouldShowRecovery = next >= RECOVERY_THRESHOLD
        )
    }
}

/**
 * 崩溃逃生通道（核心版）的存储与动作：启动评估、计数清零、
 * 切回系统桌面、测试入口。所有读写都包在 runCatching 里，
 * 坏值/IO 异常不抛、不挡启动——逃生通道本身绝不能成为新的崩溃源。
 */
object CrashEscape {
    private const val PREFS = "crash_escape"
    private const val KEY_CONSECUTIVE = "consecutive"
    private const val KEY_LAST_SEEN = "last_seen_crash_epoch"
    private const val KEY_VERSION_CODE = "version_code"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 启动时评估：按 [CrashEscapeLogic.onStartup] 更新连续崩溃计数，
     * 返回是否该出恢复屏。失败（读不到记录、写不进 prefs）时返回 false，
     * 不挡正常启动。
     */
    fun evaluateStartup(context: Context): Boolean {
        return runCatching {
            val p = prefs(context)
            val consecutive = p.getInt(KEY_CONSECUTIVE, 0)
            val lastSeen = p.getLong(KEY_LAST_SEEN, 0L)
            val storedVersion = p.getInt(KEY_VERSION_CODE, 0)
            val maxCrash = CrashLog.list(context).maxOfOrNull { it.epochMillis }
            val state = CrashEscapeLogic.onStartup(
                maxCrashEpoch = maxCrash,
                lastSeenCrashEpoch = lastSeen,
                consecutive = consecutive,
                storedVersionCode = storedVersion,
                currentVersionCode = BuildConfig.VERSION_CODE
            )
            p.edit()
                .putInt(KEY_CONSECUTIVE, state.consecutive)
                .putLong(KEY_LAST_SEEN, state.lastSeenCrashEpoch)
                .putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
                .apply()
            state.shouldShowRecovery
        }.getOrDefault(false)
    }

    /** 计数清零（点“重试”时、正常运行满宽限期后）。失败不抛。 */
    fun resetCounter(context: Context) {
        runCatching {
            prefs(context).edit().putInt(KEY_CONSECUTIVE, 0).apply()
        }
    }

    /**
     * 切回系统桌面：清掉我们的默认启动器，再发 HOME Intent 触发启动器选择，
     * 用户点系统桌面即可。此后按 Home 进系统桌面，手机立刻能正常用；
     * 修好了再从系统设置里切回来。失败不抛。
     */
    fun switchToSystemLauncher(context: Context) {
        runCatching {
            context.packageManager.clearPackagePreferredActivities(context.packageName)
        }
        runCatching {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(home)
        }
    }

    /**
     * 测试入口：模拟一次崩溃循环（诊断区“模拟崩溃循环”按钮用）。
     * 写一条标明“测试”的假崩溃记录，把计数置为 1、已见水位置为 0；
     * 下次启动时真实判定逻辑会把它当成一次新崩溃、计数涨到 2、出恢复屏。
     * 假记录进设置页「崩溃记录」可删除，不影响真实崩溃数据。
     */
    fun simulateCrashLoop(context: Context) {
        runCatching {
            CrashLog.writeCrash(
                context,
                Thread.currentThread(),
                RuntimeException("模拟崩溃循环（测试入口，非真实崩溃）")
            )
        }
        runCatching {
            prefs(context).edit()
                .putInt(KEY_CONSECUTIVE, 1)
                .putLong(KEY_LAST_SEEN, 0L)
                .putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE)
                .apply()
        }
    }
}
