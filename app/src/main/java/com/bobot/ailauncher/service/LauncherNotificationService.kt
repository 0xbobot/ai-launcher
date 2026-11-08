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
import android.util.Log
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import com.bobot.ailauncher.data.BadgeProbe
import com.bobot.ailauncher.data.BadgeStickyStore
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SimpleNotification
import com.bobot.ailauncher.data.SmsRepository
import com.bobot.ailauncher.data.aggregateBadgeCounts
import com.bobot.ailauncher.data.aggregateNumberMax
import com.bobot.ailauncher.data.combineBadgeCounts
import com.bobot.ailauncher.data.latestNotificationByPackage

/**
 * 通知监听服务：通知事件的生产者。
 * v0.17.0（PRD 技术方案 Phase 1）：只发 EventBus 事件，不再直调归档仓储——
 * "收到通知"这个事实与"归档"的决策解耦，决策权在 AiBrain。
 * 用户需在「设置 > 通知 > 通知读取权限」中手动开启（冷启动 Step3 引导）。
 */
class LauncherNotificationService : NotificationListenerService() {

    companion object {
        /** 正在连接的服务实例（未读组左滑清理用）：未连接时为 null，取消静默无-op。 */
        @Volatile
        private var instance: LauncherNotificationService? = null

        /**
         * 服务实例是否活着（监听自查重绑用，Bob 2026-11-08 20:42）：
         * 与 NotificationRepository.isConnected 是两路独立信号——flag 只在
         * onListenerConnected / onListenerDisconnected 里变，个别机型上
         * onListenerDisconnected 漏回调时 flag 会陈旧地停在 true；实例在
         * onDestroy 里也会清掉。任一路说“没连上”就重绑。
         */
        fun isBound(): Boolean = instance != null

        /**
         * 取消该包的全部在用通知（未读组左滑清理，Bob 2026-11-08）：
         * 只取消通知，不改短信、不标已读、不碰用户数据。取消后系统回调
         * onNotificationRemoved，角标随之重算。未连接时静默无-op。
         */
        fun cancelNotificationsForPackage(packageName: String) {
            instance?.cancelPackageNotifications(packageName)
        }

        /**
         * 从外部触发一次角标重算（角标广播 manifest 接收器、应用中心
         * 前台复核用，Bob 2026-11-08 21:54：抖音广播已收到但没合进
         * 角标——manifest 接收器只记数不推，合入完全等下一次通知事件）。
         * 未连接时静默无-op（记下的探针数不丢，下次 onListenerConnected
         * 的 push 会把它合进去）。与 cancelNotificationsForPackage 同款：
         * 只调实例方法，不复制五路合并逻辑，两处算不会漂移。
         */
        fun requestBadgeRecompute() {
            instance?.recomputeBadgeCounts()
        }
    }

    /** 取消该包的全部在用通知（实例方法，见 companion 说明）。 */
    private fun cancelPackageNotifications(packageName: String) {
        try {
            activeNotifications
                .filter { it.packageName == packageName }
                .forEach { cancelNotification(it.key) }
        } catch (_: Exception) {
            // 取消失败不报错（角标不动，用户可再滑一次）
        }
    }

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
        instance = this
        NotificationRepository.setConnected(true)
        pushActiveNotifications()
        registerSmsObserver()
        registerBadgeReceiver()
        probeZteBadgeAsync()
    }

    override fun onListenerDisconnected() {
        unregisterBadgeReceiver()
        unregisterSmsObserver()
        if (instance == this) instance = null
        super.onListenerDisconnected()
        NotificationRepository.setConnected(false)
    }

    override fun onDestroy() {
        unregisterBadgeReceiver()
        unregisterSmsObserver()
        if (instance == this) instance = null
        // 监听自查重绑（Bob 2026-11-08 20:42）：onDestroy 里实例已清，flag
        // 也必须清——之前这里漏了，服务销毁后 isConnected 会陈旧地停在 true，
        // 前台复核误判“已连接”而不重绑。
        NotificationRepository.setConnected(false)
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
        } catch (e: Exception) {
            // 2026-11-08 应用中心崩溃 hotfix：通知监听服务与桌面同进程，
            // 角标重算任何异常都不许崩进程（只记日志，等下一次推送）；之前只接
            // SecurityException，非 SecurityException 会直接杀掉整个应用。
            Log.e("LauncherNotifSvc", "recomputeBadgeCounts failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // v0.17.0：只发事件。通知分类归档由 AiBrain 决策后走 FiledMessages。
        sbn?.let {
            if (it.isClearable && !it.isOngoing) {
                it.toSimpleNotification()?.let { n ->
                    EventBus.emit(LauncherEvent.NotificationReceived(n))
                }
                // 粘性数（Bob 2026-11-08）：新通知到达即记/清 number（事件驱动，
                // 只在到达时写；只对计入角标的通知记——进行中通知（音乐/导航）
                // 的 number 不是未读消息，不应粘住）。
                BadgeStickyStore.recordPostedNumber(this, it.packageName, it.notification.number)
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
            // 未读组预览 + 左滑启用判定（Bob 2026-11-08）：每包最新一条在用通知，
            // 从完整列表汇总（不截 20 条），与角标计数同一口径。无 `!!` 的纯函数
            //（2026-11-08 应用中心崩溃 hotfix：458 差分里唯一的 `!!` 在此）。
            val latestByPkg = latestNotificationByPackage(all)
            NotificationRepository.updateLatestActiveByPackage(latestByPkg)
            val list = all.sortedByDescending { it.time }.take(20)
            NotificationRepository.update(list)
        } catch (e: Exception) {
            // 2026-11-08 应用中心崩溃 hotfix：通知监听服务与桌面同进程，
            // 推送汇总任何异常都不许崩进程（只记日志，等下一次推送）；之前只接
            // SecurityException，非 SecurityException 会直接杀掉整个应用。
            Log.e("LauncherNotifSvc", "pushActiveNotifications failed", e)
        }
    }

    /**
     * 角标三路合并 + 探针第四路 + 粘性数第五路（Bob 2026-11-07 晚：角标要的是应用的未读数据，
     * 不是通知栏的消息数；通知栏的数保留作兜底）。每包取最大值：
     * ① 短信库未读数（需 READ_SMS）② 通知 number 最大值（应用自报未读数）
     * ③ 在用通知条数（兜底）④ 探针（角标广播 + ZTE provider 读到的数）
     * ⑤ 粘性数（Bob 2026-11-08：应用最后一次上报的 number，通知栏清掉后仍粘住；
     * 启动应用或新通知 number=0 时清掉；不会立刻变出飞书 125）。
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
        // 粘性数第五路（Bob 2026-11-08）：应用最后一次上报的 number，通知栏清掉后仍粘住。
        val stickyCounts = BadgeStickyStore.loadNumbers(this)
        NotificationRepository.updateBadgeCounts(
            combineBadgeCounts(
                notifCounts, numberMax, smsUnread, smsPackages,
                extraCounts = BadgeProbe.currentProbeCounts(),
                stickyCounts = stickyCounts
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
