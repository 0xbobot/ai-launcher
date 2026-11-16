package com.bobot.ailauncher

import android.app.Application
import com.bobot.ailauncher.data.BatteryGuard
import com.bobot.ailauncher.data.CrashLog
import com.bobot.ailauncher.data.DailyReviewScheduler

/**
 * v0.40.0：Application 入口。
 * 低电量守护不再用前台服务（常驻通知打扰），改为进程生命周期内动态注册
 * ACTION_BATTERY_CHANGED。桌面进程基本常驻，这里注册一次即可。
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // v0.68.100（卫生批）：先装崩溃本地日志处理器——越早装越能兜住
        // 后续初始化路径上的崩溃（写 files/crashes/，链回系统原处理器）
        CrashLog.install(this)
        BatteryGuard.ensureStarted(this)
        // 第四十四批：每天一次整体复盘（21:30 周期任务，KEEP 幂等）；
        // 调度失败静默（refresh() 的补跑兜底还在），不崩启动。
        DailyReviewScheduler.schedule(this)
    }
}
