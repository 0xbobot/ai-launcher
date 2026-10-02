package com.bobot.ailauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.apps.DockState
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

/**
 * v0.14 导航：单页桌面（能力页已移除，左滑/右滑只剩卡片手势语义：右=多/左=少）。
 * - 只有首页；上拉 Dock（D1/D2/D3）是找应用的唯一入口
 * - 找应用 → 上滑进 D3「应用中心」（分组 + A-Z 双视图）
 * - NavHost 只剩 "home" 和 "settings"（应用中心的齿轮进入）
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeHost(onOpenSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun HomeHost(onOpenSettings: () -> Unit) {
    // 上拉 Dock（v0.12+）：离散状态机，弹簧动画切换，无横线手柄
    var dockState by remember { mutableStateOf(DockState.Hidden) }
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }

    // OTA：每天最多自动检查一次，有新版弹更新对话框
    LaunchedEffect(Unit) {
        if (OtaUpdater.shouldAutoCheck(context)) {
            val info = OtaUpdater.checkForUpdate(context)
            OtaUpdater.markChecked(context)
            if (info != null) updateInfo = info
        }
    }

    // 首次发现引导：D1 轻轻 peek 一下（升起停 1 秒再落下），每设备一次
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pullup_coach", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("peek_shown_v12", false)) {
            delay(1200)
            if (dockState == DockState.Hidden) {
                dockState = DockState.D1
                delay(1000)
                if (dockState == DockState.D1) dockState = DockState.Hidden
            }
            prefs.edit().putBoolean("peek_shown_v12", true).apply()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // v0.11：壁纸轻微渐隐罩，保证悬浮在壁纸上的文字可读（上深下浅）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f)
                    )
                )
        )
        // 单页桌面：只有首页
        HomeScreen(
            onOpenAppDrawer = { dockState = DockState.D1 }
        )
        // 上拉 Dock（常驻）：悬浮卡 D1/D2 + 全屏 D3 应用中心，手势切换
        PullUpDock(
            state = dockState,
            onStateChange = { dockState = it },
            onOpenSettings = onOpenSettings
        )
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
