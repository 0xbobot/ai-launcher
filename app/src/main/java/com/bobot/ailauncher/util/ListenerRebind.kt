package com.bobot.ailauncher.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.bobot.ailauncher.service.LauncherNotificationService

/**
 * 强制系统重新绑定通知监听服务。
 *
 * 场景：用户在 App 运行中去设置里开启通知读取权限，系统不会自动回调
 * onListenerConnected，导致首页"正在进行时"一直不刷新。先 DISABLED 再
 * ENABLED 组件（DONT_KILL_APP），可触发系统重新绑定。
 */
fun rebindListener(context: Context) {
    try {
        val pm = context.packageManager
        val cn = ComponentName(context, LauncherNotificationService::class.java)
        pm.setComponentEnabledSetting(
            cn,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            cn,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (_: Exception) {
        // 忽略：首页空态会继续引导用户
    }
}
