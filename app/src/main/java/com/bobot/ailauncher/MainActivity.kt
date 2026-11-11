@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import com.bobot.ailauncher.core.brain.AiBrain
import com.bobot.ailauncher.core.context.ContextEngine
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.MainScreen
import com.bobot.ailauncher.ui.theme.AILauncherTheme
import kotlinx.coroutines.launch
import com.bobot.ailauncher.ui.island.IslandToasts

class MainActivity : FragmentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v0.68.100（卫生批）：仅 debug 包的冒烟入口——带 extra 启动即抛一次
        // 真异常，验证崩溃日志端到端落盘；release 包 BuildConfig.DEBUG 恒
        // false 不存在此入口，用户界面没有任何「测试崩溃」按钮
        throwIfSmokeCrash(intent)
        // 崩溃逃生通道（核心版）：连续崩两次出恢复屏，不再进正常首页。
        // 在 setContent 之前算好，恢复屏要盖过首页与引导。
        val showRecoveryAtStart =
            com.bobot.ailauncher.data.CrashEscape.evaluateStartup(this)
        val latestCrashRecord = if (showRecoveryAtStart) {
            com.bobot.ailauncher.data.CrashLog.list(this).firstOrNull()
        } else {
            null
        }
        // v0.32.3：冷启动时也处理快捷方式
        if (intent?.action == "com.bobot.ailauncher.CHECK_UPDATE") {
            // 延迟到 UI 就绪后检查
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                checkUpdateNow()
            }
        }
        // v0.25：全屏 edge-to-edge，桌面内容延伸到状态栏/导航栏后面
        // 批B ⑧：手工调用收口到 ui/theme/Insets.kt 的统一 helper（行为不变）
        com.bobot.ailauncher.ui.theme.enableEdgeToEdge(window)
        // v0.68.83（Bob 报）：横屏左侧出现一条"空状态栏"（蓝色）——窗口未进挖孔区被
        // letterbox 避让，没画到的地方透出系统壁纸。SHORT_EDGES 让窗口在横屏时
        // 延伸进左侧短边挖孔区，全屏绘制
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        CapabilityRegistry.load(this)
        // v0.15 宠物整理员：初始化（亲密度持久化等）
        // v0.68.56（Bob）：B 方案——灵动岛系统通知通道初始化
        com.bobot.ailauncher.ui.island.IslandToasts.init(this)
        // v0.68.100（卫生批）：有没看过的崩溃记录时，冷启动非模态提示一次
        // （每个新崩溃至多提示一次，在设置页看过后不再提）。
        // 恢复屏出现时不弹 toast——恢复屏本身已说明崩溃，两处提示会打架。
        if (!showRecoveryAtStart) {
            com.bobot.ailauncher.data.CrashLog.promptTextIfNeeded(this)
                ?.let { IslandToasts.show(it) }
        }
        // v0.40.0：低电量守护不再用前台服务（常驻通知打扰），BatteryGuard 跟随进程生命周期；
        // LauncherApp.onCreate 已注册，这里幂等再保一次
        com.bobot.ailauncher.data.BatteryGuard.ensureStarted(this)
        // OTA：版本变化后清理旧安装包，避免"直接安装"命中旧包
        OtaUpdater.onAppUpgraded(this)

        // v0.41.12：DownloadManager 已换成 App 内 OkHttp（OtaDownloader），下载完成由
        // OtaDownloader 直接弹安装，不再需要系统下载广播。onResume 保留兜底。

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
                // 崩溃逃生通道（核心版）：出恢复屏时优先显示它，盖过首页；
                // 点“重试”后改回 false，恢复正常桌面。
                var showRecovery by remember { mutableStateOf(showRecoveryAtStart) }
                // v0.37.2：系统壁纸直接透出（windowShowWallpaper），不再手动绘制
                Box(modifier = Modifier
                    .fillMaxSize()
                    // 体检批D ⑥：全 App testTag 经此暴露为 uiautomator resource-id，
                    // 供冒烟脚本语义定位（semantics 修饰不影响布局与长相）
                    .semantics { testTagsAsResourceId = true }
                ) {
                    if (showRecovery) {
                        com.bobot.ailauncher.ui.recovery.CrashRecoveryScreen(
                            latestRecord = latestCrashRecord,
                            onSwitchToSystemLauncher = {
                                com.bobot.ailauncher.data.CrashEscape
                                    .switchToSystemLauncher(this@MainActivity)
                            },
                            onRetry = {
                                com.bobot.ailauncher.data.CrashEscape
                                    .resetCounter(this@MainActivity)
                                showRecovery = false
                                scheduleGraceReset()
                            },
                            onShareRecord = { record ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "AI桌面崩溃记录")
                                    putExtra(Intent.EXTRA_TEXT, record.content)
                                }
                                runCatching {
                                    startActivity(
                                        Intent.createChooser(send, "分享崩溃记录")
                                    )
                                }.onFailure {
                                    IslandToasts.show("没有可用的分享方式")
                                }
                            }
                        )
                    } else {
                        MainScreen()
                    }
                }
            }
        }
        // 崩溃逃生通道：正常运行满 60 秒后，连续崩溃计数清零（崩溃循环已断）；
        // 恢复屏出现时不排——用户还没正常跑起来，清零无意义。
        if (!showRecoveryAtStart) {
            scheduleGraceReset()
        }
    }

    override fun onResume() {
        super.onResume()
        // OTA：用户切出去等下载、回来时如果包已下好就弹安装（只弹一次）
        if (OtaUpdater.shouldPromptInstall(this)) {
            OtaUpdater.downloadedApk(this)?.let {
                if (OtaUpdater.promptInstall(this, it)) {
                    OtaUpdater.markInstallPrompted(this)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 门闩回退（Bob 2026-11-11 两例：未读行 contentIntent 静默无动作时，
        // 没权限也能回退——信号是我们自己切没切后台）：记下这次切后台的时间
        // （单调时钟）。应用中心是盖在同一个 Activity 上的浮层，进应用中心
        // 本身不触发 onPause，不会误触发门闩；判断只在点按后 0.8 秒那次检查里做。
        com.bobot.ailauncher.data.AppBackgroundLatch.markBackgrounded(
            SystemClock.uptimeMillis()
        )
    }

    // v0.26.6：返回键由 MainScreen 的 BackHandler 处理（D3 返回主页，首页不做事）
    // 这里不再重写 onBackPressed

    // v0.26.5：singleTask 模式下按 Home 键回来走这里，确保不重建页面
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // v0.68.100（卫生批）：冒烟崩溃 extra 也认 onNewIntent——桌面进程
        // 常驻/被系统自动拉起时 am start 走的是这里，不是 onCreate
        throwIfSmokeCrash(intent)
        // v0.32.3：长按快捷方式"检查更新"
        if (intent.action == "com.bobot.ailauncher.CHECK_UPDATE") {
            checkUpdateNow()
        }
        // 其他情况不做任何事：MainScreen 的 Compose 状态保持，避免"空页面"
    }

    /** 崩溃逃生通道：正常运行满宽限期后，连续崩溃计数清零。 */
    private fun scheduleGraceReset() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(
                com.bobot.ailauncher.data.CrashEscapeLogic.GRACE_PERIOD_MILLIS
            )
            com.bobot.ailauncher.data.CrashEscape.resetCounter(this@MainActivity)
        }
    }

    /** v0.68.100（卫生批）：debug 冒烟专用崩溃触发（release 包恒不触发） */
    private fun throwIfSmokeCrash(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("smoke_crash", false) == true) {
            throw RuntimeException("smoke-test crash (debug only)")
        }
    }

    /** v0.32.3：快捷方式触发立即检查更新 */
    private fun checkUpdateNow() {
        lifecycleScope.launch {
            try {
                val info = com.bobot.ailauncher.data.OtaUpdater.checkForUpdate(this@MainActivity)
                if (info != null) {
                    // 有新版：通过广播或直接弹对话框
                    // 简单起见，发一个本地事件让 MainScreen 显示
                    checkUpdateResult.value = info
                } else {
                    IslandToasts.show("已经是最新版本了")
                }
            } catch (_: Exception) {
                IslandToasts.show("检查失败，请稍后再试")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        /** v0.32.3：快捷方式检查更新结果，MainScreen 观察后弹对话框 */
        val checkUpdateResult = androidx.compose.runtime.mutableStateOf<com.bobot.ailauncher.data.OtaInfo?>(null)
    }
}
