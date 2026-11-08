package com.bobot.ailauncher.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.bobot.ailauncher.data.NotificationRepository
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

/**
 * 两次重绑之间的最小间隔（Bob 2026-11-08 20:42：监听断开自查重绑）。
 * 只在“已授权限但未连接”时才重绑；系统若迟迟不回绑（例如省电模式反
 * 复杀绑定），频繁 toggle 组件本身也会干扰系统绑定，所以节流一次。
 */
const val LISTENER_REBIND_MIN_INTERVAL_MILLIS = 30_000L

@Volatile
private var lastRebindAtMillis = 0L

/**
 * 是否需要重绑（纯函数，供单测）。
 *
 * 判定用两路信号，不只看仓库 flag：
 * - `connected`：NotificationRepository.isConnected（onListenerConnected 置
 *   true、onListenerDisconnected / onDestroy 置 false）；
 * - `serviceBound`：LauncherNotificationService.isBound()（服务实例是否活着）。
 * 两路本应在同一回调里同变，但 onListenerDisconnected 在个别机型/省电
 * 场景下可能漏回调，flag 会陈旧地停在 true；实例是另一路独立信号，
 * 任一路说“没连上”就重绑。
 * 未授权限时绝不重绑（toggle 组件也绑不上，白白打扰系统）。
 */
fun shouldRebindListener(
    granted: Boolean,
    connected: Boolean,
    serviceBound: Boolean,
    lastRebindAtMillis: Long,
    nowMillis: Long,
    minIntervalMillis: Long = LISTENER_REBIND_MIN_INTERVAL_MILLIS,
): Boolean {
    if (!granted) return false
    if (connected && serviceBound) return false
    if (nowMillis - lastRebindAtMillis < minIntervalMillis) return false
    return true
}

/**
 * 前台复核入口（首页 ON_RESUME、应用中心进组合 + ON_RESUME 共用）：
 * 已授权限但监听未连接时重绑一次，返回是否触发了重绑。
 * 已连接时不做任何事；未授权限时不做任何事。
 */
fun ensureListenerBound(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val granted = NotificationRepository.hasAccess(context)
    val connected = NotificationRepository.isConnected.value
    val bound = LauncherNotificationService.isBound()
    if (!shouldRebindListener(granted, connected, bound, lastRebindAtMillis, nowMillis)) {
        return false
    }
    // 先记时间再 toggle：toggle 本身抛异常也不许下次前台立刻再来一次。
    lastRebindAtMillis = nowMillis
    rebindListener(context)
    return true
}
