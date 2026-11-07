package com.bobot.ailauncher.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import com.bobot.ailauncher.data.BadgeProbe
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SimpleNotification
import com.bobot.ailauncher.data.SmsRepository
import com.bobot.ailauncher.data.aggregateBadgeCounts
import com.bobot.ailauncher.data.aggregateNumberMax
import com.bobot.ailauncher.data.combineBadgeCounts

/**
 * 通知监听服务：通知事件的生产者。
 * v0.17.0（PRD 技术方案 Phase 1）：只发 EventBus 事件，不再直调归档仓储——
 * "收到通知"这个事实与"归档"的决策解耦，决策权在 AiBrain。
 * 用户需在「设置 > 通知 > 通知读取权限」中手动开启（冷启动 Step3 引导）。
 */
class LauncherNotificationService : NotificationListenerService() {

    /** 短信库变化观察者（角标用）：短信在应用里读了/删了会触发角标重算，
     * 通知事件覆盖不到“应用内读短信”的场景。只在监听已连接且授了 READ_SMS 时注册。 */
    private var smsObserverRegistered = false
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            recomputeBadgeCounts()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.setConnected(true)
        pushActiveNotifications()
        registerSmsObserver()
        registerBadgeReceiver()
        probeZteBadgeAsync()
    }

    override fun onListenerDisconnected() {
        unregisterBadgeReceiver()
        unregisterSmsObserver()
        super.onListenerDisconnected()
        NotificationRepository.setConnected(false)
    }

    override fun onDestroy() {
        unregisterBadgeReceiver()
        unregisterSmsObserver()
        super.onDestroy()
    }

    /**
     * 角标广播运行时接收器（探针，Bob 2026-11-08 凌晨：实测，不要只推理）。
     * 监听 ShortcutBadger 的两个广播 action，只收不发；收到且校验通过后
     * 立即重算一次角标（第四路信号进数）。manifest 里另有同逻辑的
     * BadgeBroadcastReceiver（供 queryBroadcastReceivers 显式发送时命中）。
     */
    private var badgeReceiver: BroadcastReceiver? = null

    private fun registerBadgeReceiver() {
        if (badgeReceiver != null) return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (BadgeProbe.onBroadcastReceived(context, intent)) {
                        pushActiveNotifications()
                    }
                }
            }
            val filter = BadgeProbe.badgeIntentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            badgeReceiver = receiver
        } catch (_: Exception) {
            // 注册失败不影响通知通道（探针收不到广播，诊断行会如实显示“未收到”）
        }
    }

    private fun unregisterBadgeReceiver() {
        val receiver = badgeReceiver ?: return
        badgeReceiver = null
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
            // 未注册或已注销，忽略
        }
    }

    /**
     * ZTE provider 探针（后台线程跑，阻塞调用不上主线程）：试读
     * content://com.android.launcher3.cornermark.unreadbadge，
     * 拿到数就重算一次角标（第四路信号）；探针失败时角标行为一字不变。
     * 只读，绝不调 setAppUnreadCount。
     */
    private fun probeZteBadgeAsync() {
        Thread {
            try {
                val candidates = activeNotifications.map { it.packageName }.toSet() +
                    listOfNotNull(BadgeProbe.resolveFeishuPackage(this))
                BadgeProbe.probeZteProvider(this, candidates)
                pushActiveNotifications()
            } catch (_: Exception) {
                // 探针失败不影响通知通道
            }
        }.start()
    }

    private fun registerSmsObserver() {
        if (smsObserverRegistered) return
        // 未授 READ_SMS 时短信数恒为 0，观察无意义，跳过；授权后下次连接时再注册。
        if (!SmsRepository.hasSmsPermission(this)) return
        try {
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, smsObserver
            )
            smsObserverRegistered = true
        } catch (_: Exception) {
            // 注册失败不影响通知通道，角标仍由通知事件驱动
        }
    }

    private fun unregisterSmsObserver() {
        if (!smsObserverRegistered) return
        try {
            contentResolver.unregisterContentObserver(smsObserver)
        } catch (_: Exception) {
            // 未注册或已注销，忽略
        }
        smsObserverRegistered = false
    }

    /** 角标重算（短信库变化触发）：从在用通知列表重算三路信号并合并推送。 */
    private fun recomputeBadgeCounts() {
        try {
            val active = activeNotifications.filter { it.isClearable && !it.isOngoing }
            val all = active.mapNotNull { sbn -> sbn.toSimpleNotification() }
            updateBadgeCounts(active, all)
        } catch (_: SecurityException) {
            // 未连接时静默忽略
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // v0.17.0：只发事件。通知分类归档由 AiBrain 决策后走 FiledMessages。
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
                com.bobot.ailauncher.data.FiledMessages.removeByPredicate { item ->
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
            val active = activeNotifications.filter { it.isClearable && !it.isOngoing }
            // 角标计数（Bob 2026-11-07 晚）：从完整在用通知列表汇总，不 take(20)——
            // 展示列表只取最新 20 条，计数若共用它，“125 条”会被截成 20 条以内。
            // （toSimpleNotification 只丢标题正文都空的条目——这类通知无可读内容。）
            val all = active.mapNotNull { sbn -> sbn.toSimpleNotification() }
            updateBadgeCounts(active, all)
            val list = all.sortedByDescending { it.time }.take(20)
            NotificationRepository.update(list)
        } catch (_: SecurityException) {
            // 权限被撤销时静默忽略，首页会显示空态引导
        }
    }

    /**
     * 角标三路合并 + 探针第四路（Bob 2026-11-07 晚：角标要的是应用的未读数据，
     * 不是通知栏的消息数；通知栏的数保留作兜底）。每包取最大值：
     * ① 短信库未读数（需 READ_SMS）② 通知 number 最大值（应用自报未读数）
     * ③ 在用通知条数（兜底）④ 探针（角标广播 + ZTE provider 读到的数）。
     * 短信数记到默认短信应用与用户实际看到的短信应用两个包上（各取最大值），
     * 两者相同时只记一处（Bob 2026-11-08 凌晨：短信角标 6、系统桌面 15，
     * 机制已通，归属加固只作兜底，主因是 read 标志与应用未读定义对不上）。
     */
    private fun updateBadgeCounts(
        active: List<StatusBarNotification>,
        all: List<SimpleNotification>
    ) {
        // number 从原始列表取（toSimpleNotification 会丢标题正文都空的条目，
        // 但 number 仍是有效的未读信号）。
        val numberMax = aggregateNumberMax(
            active.map { it.packageName to it.notification.number }
        )
        val notifCounts = aggregateBadgeCounts(all)
        val defaultSms = try {
            Telephony.Sms.getDefaultSmsPackage(this)
        } catch (_: Exception) {
            null
        }
        val visibleSms = SmsRepository.resolveVisibleSmsPackage(this)
        val smsPackages = listOfNotNull(defaultSms, visibleSms).toSet()
        val smsUnread = SmsRepository.countUnreadSms(this)
        NotificationRepository.updateBadgeCounts(
            combineBadgeCounts(
                notifCounts, numberMax, smsUnread, smsPackages,
                extraCounts = BadgeProbe.currentProbeCounts()
            )
        )
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
