package com.bobot.ailauncher.ui.battery

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.R

/**
 * v0.68.27：低电量悬浮窗提醒（替代 BatteryGuardActivity 弹窗）。
 *
 * Bob 反馈：点通知拽回桌面弹模态框太打扰；在视频/游戏前台时经常弹不出来。
 * 新方案：小气泡直接盖在当前应用上层（TYPE_APPLICATION_OVERLAY），不抢焦点、
 * 不拽回桌面，点 × 就消失。音频焦点照样申请（暂停视频省电）。
 *
 * 位置：顶部中央（Bob 选）。
 * 触发：BatteryGuard.fireAlert → 10%/5% 两档。
 * 消失：点 × / 插上充电 / 电量回升到 15% 以上。
 */
class BatteryOverlayService : Service() {

    companion object {
        const val EXTRA_PCT = "pct"
        const val EXTRA_CRITICAL = "critical"

        private var instance: BatteryOverlayService? = null

        /** 显示悬浮窗（需 SYSTEM_ALERT_WINDOW 权限）。 */
        fun show(context: Context, pct: Int, critical: Boolean) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, BatteryOverlayService::class.java).apply {
                putExtra(EXTRA_PCT, pct)
                putExtra(EXTRA_CRITICAL, critical)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        /** 隐藏悬浮窗。 */
        fun hide(context: Context) {
            instance?.removeOverlay()
            try {
                context.stopService(Intent(context, BatteryOverlayService::class.java))
            } catch (_: Exception) {
            }
        }

        fun isShowing(): Boolean = instance?.overlayView != null
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pct = intent?.getIntExtra(EXTRA_PCT, 5) ?: 5
        val critical = intent?.getBooleanExtra(EXTRA_CRITICAL, true) ?: true
        // 前台服务通知（常驻，低优先级）
        startForegroundNotification()
        showOverlay(pct, critical)
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "battery_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                channelId,
                "低电量悬浮窗",
                android.app.NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("七仔在提醒你充电")
            .setOngoing(true)
            .build()
        startForeground(0xBA72, notification)
    }

    override fun onDestroy() {
        removeOverlay()
        instance = null
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(pct: Int, critical: Boolean) {
        if (overlayView != null) {
            // 已在显示，更新文案即可
            updateText(pct, critical)
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = createBubbleView(pct, critical)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // 不抢焦点：用户可继续操作底下的应用
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (resources.displayMetrics.density * 64).toInt() // 顶部往下 64dp
        }

        try {
            wm.addView(view, params)
            overlayView = view
        } catch (_: Exception) {
        }
    }

    private fun createBubbleView(pct: Int, critical: Boolean): View {
        val ctx = this
        val density = resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, (12 * density).toInt(), pad, (12 * density).toInt())
            background = ContextCompat.getDrawable(ctx, R.drawable.battery_bubble_bg)
            elevation = 8 * density
        }

        // 七仔头像（用应用图标占位，实际用七仔资源）
        val avatar = ImageView(ctx).apply {
            val size = (40 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            // TODO：换成七仔真实资源
            setImageResource(R.mipmap.ic_launcher)
        }
        root.addView(avatar)

        val textLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            layoutParams = lp
        }

        val title = TextView(ctx).apply {
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1F1D1A.toInt())
        }
        val desc = TextView(ctx).apply {
            textSize = 13f
            setTextColor(0xFF8A847E.toInt())
        }
        textLayout.addView(title)
        textLayout.addView(desc)
        root.addView(textLayout)

        val close = TextView(ctx).apply {
            text = "×"
            textSize = 22f
            setTextColor(0xFF999999.toInt())
            val s = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            gravity = Gravity.CENTER
            setOnClickListener { hide(this@BatteryOverlayService) }
        }
        root.addView(close)

        root.tag = Pair(title, desc)
        updateTextViews(title, desc, pct, critical)
        return root
    }

    private fun updateText(pct: Int, critical: Boolean) {
        val (title, desc) = overlayView?.tag as? Pair<TextView, TextView> ?: return
        updateTextViews(title, desc, pct, critical)
    }

    private fun updateTextViews(title: TextView, desc: TextView, pct: Int, critical: Boolean) {
        if (critical) {
            title.text = "电量只剩 $pct% 了"
            desc.text = "再不充电，七仔就要睡着了…"
        } else {
            title.text = "电量还有 $pct%"
            desc.text = "七仔提醒你，该找充电器了"
        }
    }

    private fun removeOverlay() {
        overlayView?.let { v ->
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }
}
