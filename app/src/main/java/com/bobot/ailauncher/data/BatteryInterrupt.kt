package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bobot.ailauncher.ui.battery.BatteryGuardActivity

/**
 * v0.41.14：低电量强制打断（Bob 要求"中断播放的效果"）。
 *
 * 背景：全屏通知在 Android 14+ 是可撤销权限，国产 ROM 还有"后台弹出界面"限制，
 * 导致低电提醒经常只出通知、点一下才全屏；而且即使弹出来也暂停不了视频，
 * 视频继续播、继续耗电，低电保护就没意义。
 *
 * 两件套：
 * 1. 音频焦点（无需额外权限）：申请 AUDIOFOCUS_GAIN_TRANSIENT，B站/YouTube 等
 *    规范播放器会自己暂停。这是真正"中断播放"的关键，也是省电的关键。
 *    用户点掉提醒或插上充电后归还焦点，播放器可恢复。
 * 2. 悬浮窗（需用户去设置开一次"允许显示在其他应用上层"）：SYSTEM_ALERT_WINDOW
 *    获批后，后台启动 Activity 被系统豁免，全屏提醒可稳定盖住视频/游戏，
 *    比全屏通知可靠得多，国产 ROM 也认。
 *
 * 没开悬浮窗权限时：降级用全屏通知（原逻辑）+ 音频焦点（视频照样暂停）。
 */
object BatteryInterrupt {

    private var audioManager: AudioManager? = null
    private var focusRequest26: AudioFocusRequest? = null

    /** 是否已授予"显示在其他应用上层"权限 */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** 跳系统设置：为本应用开启"显示在其他应用上层" */
    fun openOverlaySettings(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        } catch (_: Exception) {
        }
    }

    /**
     * 申请瞬时音频焦点 → 正在播放的视频/音乐 App 会暂停。
     * 幂等：重复调用不会叠加。
     */
    fun requestAudioFocus(context: Context) {
        if (audioManager != null) return
        val app = context.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                am.requestAudioFocus(req)
                focusRequest26 = req
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            audioManager = am
        } catch (_: Exception) {
        }
    }

    /** 归还音频焦点 → 播放器可恢复播放。 */
    fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest26?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        } finally {
            audioManager = null
            focusRequest26 = null
        }
    }

    /**
     * 启动低电全屏提醒。
     * 有悬浮窗权限 → 直接启动 Activity（系统豁免后台启动限制，稳定盖住）；
     * 没有 → 返回 false，调用方降级用全屏通知。
     */
    fun launchAlertDirectly(context: Context, pct: Int, critical: Boolean): Boolean {
        if (!canDrawOverlays(context)) return false
        return try {
            val intent = Intent(context, BatteryGuardActivity::class.java).apply {
                putExtra(BatteryGuardActivity.EXTRA_PCT, pct)
                putExtra(BatteryGuardActivity.EXTRA_CRITICAL, critical)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
