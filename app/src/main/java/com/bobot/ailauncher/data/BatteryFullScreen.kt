package com.bobot.ailauncher.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * v0.38.0：全屏 intent 权限检测与引导。
 *
 * 背景：Android 14+ 把 USE_FULL_SCREEN_INTENT 做成用户可撤销的特殊权限；
 * 关掉后，setFullScreenIntent 会被系统**静默降级**成普通 heads-up 通知——
 * 用户只能先看到通知、点一下才进全屏（之前上报的正是这个现象）。
 * 另：国产 ROM（小米/华为/OPPO/vivo）还有一道「后台弹出界面」权限，
 * 没开同样会被降级；系统 API 查不出来，只能文字引导。
 */
object BatteryFullScreen {

    /** 全屏 intent 是否可用（API 34+ 查系统开关；以下版本无此开关，视为可用） */
    fun canUse(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            nm.canUseFullScreenIntent()
        } catch (_: Exception) {
            true
        }
    }

    /** 跳系统设置：优先全屏通知页，打不开则退到应用详情页 */
    fun openSettings(context: Context) {
        val launched = if (Build.VERSION.SDK_INT >= 34) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                true
            } catch (_: Exception) {
                false
            }
        } else false
        if (!launched) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
