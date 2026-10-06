package com.bobot.ailauncher

import android.app.Application
import com.bobot.ailauncher.data.BatteryGuard

/**
 * v0.40.0：Application 入口。
 * 低电量守护不再用前台服务（常驻通知打扰），改为进程生命周期内动态注册
 * ACTION_BATTERY_CHANGED。桌面进程基本常驻，这里注册一次即可。
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BatteryGuard.ensureStarted(this)
    }
}
