package com.bobot.ailauncher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bobot.ailauncher.core.brain.AiBrain
import com.bobot.ailauncher.core.context.ContextEngine
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.MainScreen
import com.bobot.ailauncher.ui.onboarding.OnboardingNav
import com.bobot.ailauncher.ui.theme.AILauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var downloadReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v0.25：全屏 edge-to-edge，桌面内容延伸到状态栏/导航栏后面
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        CapabilityRegistry.load(this)
        // v0.15 宠物整理员：初始化（亲密度持久化等）
        PetRepository.init(this)
        // v0.28.0：低电量守护（默认开启，温和提醒）
        if (com.bobot.ailauncher.data.BatteryGuardPrefs.isEnabled(this)) {
            com.bobot.ailauncher.service.BatteryGuardService.start(this)
        }
        // OTA：版本变化后清理旧安装包，避免"直接安装"命中旧包
        OtaUpdater.onAppUpgraded(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        // OTA：下载完成广播 → 下载的是我们的更新包则弹安装
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != -1L && id == OtaUpdater.pendingDownloadId(ctx)
                    && OtaUpdater.shouldPromptInstall(ctx)
                ) {
                    OtaUpdater.downloadedApk(ctx)?.let {
                        OtaUpdater.promptInstall(ctx, it)
                        OtaUpdater.markInstallPrompted(ctx)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // v0.26.5：去掉窗口背景模糊——现在壁纸由 Compose 内绘制，
        // window blur 只模糊窗口后的内容，对内绘壁纸无效，反而可能导致切换闪烁

        // v0.17.0（PRD 技术方案 Phase 1）：事件驱动 AI Brain。
        // Launcher 只生产事件、消费决策结果；Brain 挂掉也不影响 Launcher。
        lifecycleScope.launch {
            EventBus.events.collect { event ->
                AiBrain.onEvent(event, this@MainActivity)
            }
        }

        // v0.20（PRD §十八 Context Engine）：前台应用追踪 + 屏幕事件 → EventBus
        ContextEngine.start(this)

        setContent {
            AILauncherTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                // v0.32.0：全透明——窗口半透明，系统壁纸直接透出（Bob）
                // 不再 Compose 内绘制壁纸
                Box(modifier = Modifier.fillMaxSize()) {
                    // 上层：原有内容（玻璃效果靠各页面半透明遮罩）
                    if (onboarded) {
                        MainScreen()
                    } else {
                        OnboardingNav(onFinish = {
                            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                            onboarded = true
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // OTA：用户切出去等下载、回来时如果包已下好就弹安装（只弹一次）
        if (OtaUpdater.shouldPromptInstall(this)) {
            OtaUpdater.downloadedApk(this)?.let {
                OtaUpdater.promptInstall(this, it)
                OtaUpdater.markInstallPrompted(this)
            }
        }
    }

    // v0.26.6：返回键由 MainScreen 的 BackHandler 处理（D3 返回主页，首页不做事）
    // 这里不再重写 onBackPressed

    // v0.26.5：singleTask 模式下按 Home 键回来走这里，确保不重建页面
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 不做任何事：MainScreen 的 Compose 状态保持，避免"空页面"
    }

    override fun onDestroy() {
        downloadReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "ai_launcher"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
