package com.bobot.ailauncher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.MainScreen
import com.bobot.ailauncher.ui.onboarding.OnboardingNav
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherTheme

class MainActivity : ComponentActivity() {

    private var downloadReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CapabilityRegistry.load(this)
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

        setContent {
            AILauncherTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AILauncherColors.Background
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
