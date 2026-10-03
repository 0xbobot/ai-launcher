package com.bobot.ailauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bobot.ailauncher.MainActivity
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.BatteryGuardPrefs
import com.bobot.ailauncher.ui.battery.BatteryGuardActivity

/**
 * v0.28.0：低电量守护前台服务。
 * 实时监听电量，10%/5% 两档温和提醒。只提醒，不做任何自动操作。
 *
 * 保活通知用 IMPORTANCE_MIN：静默、无声、不打扰。
 */
class BatteryGuardService : Service() {

    companion object {
        private const val FOREGROUND_ID = 0xBA77
        private const val ALERT_ID = 0xBA71
        private const val CHANNEL_KEEPALIVE = "battery_guard_keepalive"
        private const val CHANNEL_ALERT = "battery_guard_alert"
        private const val RESET_PCT = 15

        fun start(context: Context) {
            val intent = Intent(context, BatteryGuardService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) { /* 忽略启动失败 */ }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BatteryGuardService::class.java))
            } catch (_: Exception) { }
        }
    }

    private var warnFired = false
    private var criticalFired = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            if (!BatteryGuardPrefs.isEnabled(context)) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = (level * 100) / scale

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // v0.28.1：同步电池状态给七仔省电模式
            com.bobot.ailauncher.data.BatteryState.update(pct, charging)

            // 充电中或回升到安全线以上：重置，下轮再提醒
            if (charging || pct >= RESET_PCT) {
                warnFired = false
                criticalFired = false
                return
            }

            val criticalPct = BatteryGuardPrefs.criticalPct(context)
            val warnPct = BatteryGuardPrefs.warnPct(context)

            if (pct <= criticalPct && !criticalFired) {
                criticalFired = true
                warnFired = true
                fireAlert(pct, critical = true)
            } else if (pct <= warnPct && !warnFired) {
                warnFired = true
                fireAlert(pct, critical = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(FOREGROUND_ID, keepaliveNotification())
        // targetSdk 34+：动态注册广播必须显式声明 exported 状态
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被杀后由系统拉起时 flags 处理；保持运行
        return START_STICKY
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 保活通道：最低优先级，静默
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_KEEPALIVE,
                "低电量守护",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "低电量守护保活，无声" }
        )
        // 提醒通道：高优先级，用于低电量弹窗
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "电量提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "低电量时的温和提醒" }
        )
    }

    private fun keepaliveNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_KEEPALIVE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("七仔的能量守护运行中")
            .setContentText("七仔快没能量时会温和提醒你")
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** 低电量提醒：高优先级通知 + 全屏温和弹窗（来电同款机制） */
    private fun fireAlert(pct: Int, critical: Boolean) {
        val fullScreen = Intent(this, BatteryGuardActivity::class.java).apply {
            putExtra(BatteryGuardActivity.EXTRA_PCT, pct)
            putExtra(BatteryGuardActivity.EXTRA_CRITICAL, critical)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, if (critical) 2 else 1, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (critical) "七仔快要睡着了…" else "七仔快没能量了"
        val text = if (critical) "电量掉到 $pct% 了，再不充电就关机了"
        else "充个电帮它回血吧"
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(ALERT_ID, notification)
        } catch (_: Exception) { }
    }
}
