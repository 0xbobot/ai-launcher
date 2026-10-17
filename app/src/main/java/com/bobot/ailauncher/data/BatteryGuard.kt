package com.bobot.ailauncher.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.bobot.ailauncher.R
import com.bobot.ailauncher.ui.battery.BatteryGuardActivity
import com.bobot.ailauncher.ui.battery.BatteryOverlayService

/**
 * v0.40.0：低电量守护（替代已删除的 BatteryGuardService 前台服务）。
 * Bob 反馈常驻通知"有点打扰"——ACTION_BATTERY_CHANGED 是系统 sticky 广播，
 * 动态注册后由系统推送，不需要前台服务保活。监听跟随 App 进程生命周期：
 * 本 App 是桌面，进程基本常驻（开机自启、按 Home 回来），足够覆盖提醒场景。
 *
 * 逻辑与原来一致：10%/5% 两档温和提醒，只提醒、不做任何自动操作；
 * 充电中或回升到 15% 以上重置；电池状态同步给七仔省电模式。
 */
object BatteryGuard {
    private const val ALERT_ID = 0xBA71
    private const val CHANNEL_ALERT = "battery_guard_alert"
    private const val RESET_PCT = 15

    @Volatile
    private var registered = false
    private var warnFired = false
    private var criticalFired = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val app = context.applicationContext
            if (!BatteryGuardPrefs.isEnabled(app)) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = (level * 100) / scale

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // 电池状态同步给七仔省电模式
            BatteryState.update(pct, charging)

            // 充电中或回升到安全线以上：重置，下轮再提醒；归还音频焦点；关悬浮窗
            if (charging || pct >= RESET_PCT) {
                warnFired = false
                criticalFired = false
                BatteryInterrupt.abandonAudioFocus()
                dismissAlert(app)
                return
            }

            val criticalPct = BatteryGuardPrefs.criticalPct(app)
            val warnPct = BatteryGuardPrefs.warnPct(app)

            if (pct <= criticalPct && !criticalFired) {
                criticalFired = true
                warnFired = true
                fireAlert(app, pct, critical = true)
            } else if (pct <= warnPct && !warnFired) {
                warnFired = true
                fireAlert(app, pct, critical = false)
            }
        }
    }

    /** 幂等：进程内只注册一次。Application.onCreate 与各处开关都调它。 */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (registered) return
        val app = context.applicationContext
        if (!BatteryGuardPrefs.isEnabled(app)) return
        createAlertChannel(app)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // targetSdk 34+：动态注册广播必须显式声明 exported 状态
        if (Build.VERSION.SDK_INT >= 34) {
            app.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(batteryReceiver, filter)
        }
        registered = true
    }

    /** 设置页开关：写配置 + 立即注册/注销。 */
    @Synchronized
    fun setEnabled(context: Context, on: Boolean) {
        val app = context.applicationContext
        BatteryGuardPrefs.setEnabled(app, on)
        if (on) {
            registered = false
            ensureStarted(app)
        } else if (registered) {
            try {
                app.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            registered = false
        }
    }

    private fun createAlertChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "电量提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "低电量时的温和提醒" }
        )
    }

    /**
     * 低电量提醒（v0.68.27 重构）。
     * - critical 档：先申请音频焦点（暂停视频省电），再弹悬浮窗小气泡
     * - warn 档：只弹悬浮窗，不抢音频焦点
     * - 有悬浮窗权限 → 小气泡盖在当前应用上层；无权限 → 降级为普通通知
     */
    private fun fireAlert(context: Context, pct: Int, critical: Boolean) {
        if (critical) {
            // 先暂停正在播放的视频/音乐（省电的关键）
            BatteryInterrupt.requestAudioFocus(context)
        }
        // 悬浮窗小气泡（不拽回桌面，不弹模态框）
        if (Settings.canDrawOverlays(context)) {
            BatteryOverlayService.show(context, pct, critical)
            return
        }
        // 无悬浮窗权限：降级为普通通知（点开进设置页引导开权限）
        val overlayGuide = Intent(context, BatteryGuardActivity::class.java).apply {
            putExtra(BatteryGuardActivity.EXTRA_PCT, pct)
            putExtra(BatteryGuardActivity.EXTRA_CRITICAL, critical)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context, if (critical) 2 else 1, overlayGuide,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (critical) "七仔快要睡着了…" else "七仔快没能量了"
        val text = if (critical) "电量掉到 $pct% 了，再不充电就关机了"
        else "充个电帮它回血吧"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(ALERT_ID, notification)
        } catch (_: Exception) {
        }
    }

    /** 充电或电量恢复时：关掉悬浮窗。 */
    fun dismissAlert(context: Context) {
        BatteryOverlayService.hide(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.cancel(ALERT_ID)
        } catch (_: Exception) {
        }
    }
}
