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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bobot.ailauncher.core.brain.AiBrain
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
        CapabilityRegistry.load(this)
        // v0.15 宠物整理员：初始化（亲密度持久化等）
        PetRepository.init(this)
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

        // v0.11 玻璃拟态：API 31+ 开启窗口真实背景模糊（壁纸/下层内容），
        // 低版本 graceful 降级为半透明底色（无 blur）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setBackgroundBlurRadius(80)
            } catch (_: Exception) {
            }
        }

        // v0.17.0（PRD 技术方案 Phase 1）：事件驱动 AI Brain。
        // Launcher 只生产事件、消费决策结果；Brain 挂掉也不影响 Launcher。
        lifecycleScope.launch {
            EventBus.events.collect { event ->
                AiBrain.onEvent(event, this@MainActivity)
            }
        }

        setContent {
            AILauncherTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                // 全透明底：系统壁纸从半透明主题透出，不再全屏铺暖灰底
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
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

    override fun onDestroy() {
        downloadReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "ai_launcher"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
