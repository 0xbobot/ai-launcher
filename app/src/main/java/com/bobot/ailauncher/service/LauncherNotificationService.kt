package com.bobot.ailauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SimpleNotification

/**
 * 通知监听服务：通知事件的生产者。
 * v0.17.0（PRD 技术方案 Phase 1）：只发 EventBus 事件，不再直调 PetRepository——
 * "收到通知"这个事实与"宠物该怎么做"的决策解耦，决策权在 AiBrain。
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
        // v0.17.0：只发事件。宠物"接→分类→呈现"由 AiBrain 决策后走原链路，行为不变。
        sbn?.let {
            if (it.isClearable && !it.isOngoing) {
                it.toSimpleNotification()?.let { n ->
                    EventBus.emit(LauncherEvent.NotificationReceived(n))
                }
            }
        }
        pushActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        pushActiveNotifications()
        // v0.58.0（需求2）：通知栏划掉后，Today 首页同步清除对应消息
        sbn?.let {
            val pkg = it.packageName.orEmpty()
            val key = it.key.orEmpty()
            val extras = it.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (pkg.isNotBlank()) {
                com.bobot.ailauncher.data.PetRepository.removeFiledByPredicate { item ->
                    item.packageName == pkg &&
                        // v0.67.2：优先按通知 key 精确匹配，key 为空时 fallback 标题/正文匹配
                        (if (key.isNotBlank() && item.notifKey.isNotBlank()) item.notifKey == key
                         else (title.isBlank() || item.title == title || item.text.contains(text.take(20))))
                }
            }
        }
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
            packageName = packageName,
            // v0.60.0：带上 contentIntent，用于点击直达会话
            contentIntent = notification.contentIntent,
            // v0.67.2：带上通知 key，用于移除时精确匹配
            notifKey = key.orEmpty(),
            // v0.67.4：未接来电提取号码，用于显示+一键回拨
            phoneNumber = extractPhoneNumber(title, text, packageName)
        )
    }

    /**
     * v0.67.4：仅对未接来电类通知提取号码（避免普通消息里的数字误伤）。
     * 匹配 +86/0086 可选前缀 + 7~15 位数字（允许空格/横杠/括号）。
     */
    private fun extractPhoneNumber(title: String, text: String, packageName: String): String {
        val t = "$title $text"
        val pkg = packageName.lowercase()
        val isMissed = t.contains("未接来电") || t.contains("未接电话") ||
            t.contains("Missed call", ignoreCase = true) ||
            pkg.contains("dialer") || pkg.contains("incallui")
        if (!isMissed) return ""
        val m = Regex("""(\+?86)?[\s\-()]*(\d[\d\s\-()]{6,14}\d)""").find(t) ?: return ""
        val digits = m.value.filter { it.isDigit() }
        val num = when {
            digits.startsWith("0086") -> digits.removePrefix("0086")
            digits.startsWith("86") && digits.length > 11 -> digits.removePrefix("86")
            else -> digits
        }
        return if (num.length in 7..15) num else ""
    }
}
