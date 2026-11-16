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
 * 判定用三路信号，不只看仓库 flag：
 * - `connected`：NotificationRepository.isConnected（onListenerConnected 置
 *   true、onListenerDisconnected / onDestroy 置 false）；
 * - `serviceBound`：LauncherNotificationService.isBound()（服务实例是否活着）。
 * - `liveNotifications`：系统现查的活信号（activeNotifications 未连接时
 *   返回 null）。Bob 2026-11-08 22:15：bobot 与 ChatGPT 两条可划掉的消息
 *   通知在栏里两分钟、监听报已连接、角标仍空——绑定可能已死、
 *   onListenerDisconnected 漏回调导致 flag 陈旧；flag 与实例都说“已连接”
 *   时只有这路活信号能验出来。默认 true（老调用方不传即按旧行为）。
 * flag 与实例本应在同一回调里同变，但 onListenerDisconnected 在个别机型/省电
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
    liveNotifications: Boolean = true,
): Boolean {
    if (!granted) return false
    if (connected && serviceBound && liveNotifications) return false
    if (nowMillis - lastRebindAtMillis < minIntervalMillis) return false
    return true
}

/**
 * 前台复核入口（首页 ON_RESUME、应用中心进组合 + ON_RESUME 共用）：
 * 已授权限但监听未连接时重绑一次，返回是否触发了重绑。
 * 已连接时先查一次系统现查的活信号（activeNotifications 为 null 即
 * 绑定已死，见 shouldRebindListener 的 liveNotifications）；活信号
 * 也说已死才重绑。未授权限时不做任何事。
 * 活信号是一次 Binder 现查，只在 flag 与实例都说“已连接”时才查
 * （已判需重绑时直接重绑，不再查）；触发重绑同样受 30 秒节流约束。
 */
fun ensureListenerBound(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val granted = NotificationRepository.hasAccess(context)
    val connected = NotificationRepository.isConnected.value
    val bound = LauncherNotificationService.isBound()
    val live = if (granted && connected && bound) {
        LauncherNotificationService.hasLiveNotifications()
    } else {
        true
    }
    if (!shouldRebindListener(granted, connected, bound, lastRebindAtMillis, nowMillis, liveNotifications = live)) {
        return false
    }
    // 先记时间再 toggle：toggle 本身抛异常也不许下次前台立刻再来一次。
    lastRebindAtMillis = nowMillis
    rebindListener(context)
    return true
}
