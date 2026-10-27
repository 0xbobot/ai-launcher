package com.bobot.ailauncher.ui.island

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * v0.68.56（Bob）：B 方案——走系统通知通道的真·灵动岛。
 * 系统有状态栏特权，渲染出的 pill 和计时器那个一样干净，不会和状态栏打架。
 * 用 setTimeoutAfter(2500) 让系统 2.5 秒后自动撤掉，无需自己 handler。
 */
object IslandNotification {
    // 渠道重建：旧渠道名改了老用户仍见旧名，故改 id 并删旧渠道，新用户直接见新名。
    private const val CHANNEL_ID = "island_toast_v2"
    private const val LEGACY_CHANNEL_ID = "island_toast"
    private const val NOTIF_ID = 0x1A4D  // 固定 ID：新 toast 覆盖旧的，不堆积

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "顶部提示",
                    NotificationManager.IMPORTANCE_HIGH  // HIGH 才能 heads-up 弹出
                ).apply {
                    description = "提示以顶部横条形式显示"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 尝试发系统通知。返回 true 表示已发出（不需要应用内 pill）。
     */
    fun show(context: Context, text: String): Boolean {
        return try {
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Android 13+ 需要 POST_NOTIFICATIONS 权限，没有就回落到应用内 pill
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.bobot.ailauncher.R.drawable.ic_stat_island)
                .setContentTitle(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(false)
                .setAutoCancel(true)
                .setTimeoutAfter(2500L)  // 2.5 秒后系统自动撤掉
                .setOnlyAlertOnce(false)
                .build()
            manager.notify(NOTIF_ID, notification)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cancel(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIF_ID)
        } catch (_: Exception) { }
    }
}
