package com.bobot.ailauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.SimpleNotification

/**
 * 通知监听服务："正在进行时"信息流的数据来源。
 * 用户需在「设置 > 通知 > 通知读取权限」中手动开启（冷启动 Step3 引导）。
 */
class LauncherNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.setConnected(true)
        pushActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationRepository.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // v0.15 宠物整理员：单条新通知 → 宠物接 → 分类 → 呈现（去重/忙碌保护在 PetRepository 内）
        sbn?.let {
            if (it.isClearable && !it.isOngoing) {
                it.toSimpleNotification()?.let { n ->
                    PetRepository.handleIncomingNotification(n)
                }
            }
        }
        pushActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        pushActiveNotifications()
    }

    private fun pushActiveNotifications() {
        try {
            val list = activeNotifications
                .filter { it.isClearable && !it.isOngoing }
                .mapNotNull { sbn -> sbn.toSimpleNotification() }
                .sortedByDescending { it.time }
                .take(20)
            NotificationRepository.update(list)
        } catch (_: SecurityException) {
            // 权限被撤销时静默忽略，首页会显示空态引导
        }
    }

    private fun StatusBarNotification.toSimpleNotification(): SimpleNotification? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val appName = try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName
        }
        return SimpleNotification(
            appName = appName,
            title = title,
            text = text,
            time = postTime,
            packageName = packageName
        )
    }
}
