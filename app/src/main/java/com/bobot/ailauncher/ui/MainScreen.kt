package com.bobot.ailauncher.ui

import android.widget.Toast
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
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.apps.DockState
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.pet.PetTabsOverlay
import com.bobot.ailauncher.ui.settings.SettingsScreen

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
    val context = LocalContext.current
    // v0.15.1：Dock 默认显示记住的行数（默认 D1 一行），不再默认隐藏
    var dockState by remember {
        mutableStateOf(if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1)
    }
    var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }

    // OTA：每天最多自动检查一次，有新版弹更新对话框
    LaunchedEffect(Unit) {
        if (OtaUpdater.shouldAutoCheck(context)) {
            val info = OtaUpdater.checkForUpdate(context)
            OtaUpdater.markChecked(context)
            if (info != null) updateInfo = info
        }
    }

    // v0.15.1：Dock 状态机统一出口
    // - 记住行数：D1 左滑→D2 时记 2，D2 右滑→D1 时记 1
    // - 隐藏上滑唤出 / D3 右滑返回 → 直接回到记住的行数（D1/D2）
    // - D1 右滑→隐藏、D1/D2 上滑→D3、D3 下滑逐级收回保持不变
    // （v0.12 的首次 peek 引导已删除：默认显示，不需要了）
    fun goDock(requested: DockState, fromD3SwipeRight: Boolean = false) {
        val cur = dockState
        val remembered =
            if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1
        var target = requested
        if (fromD3SwipeRight && cur == DockState.D3) target = remembered
        if (cur == DockState.Hidden && target == DockState.D1) target = remembered
        if (cur == DockState.D1 && target == DockState.D2) UiPrefs.setDockRows(context, 2)
        if (cur == DockState.D2 && target == DockState.D1) UiPrefs.setDockRows(context, 1)
        dockState = target
    }

    // v0.15 宠物整理员：toast 事件 → 系统 Toast
    LaunchedEffect(Unit) {
        PetRepository.toast.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
            onOpenAppDrawer = { goDock(DockState.D1) }
        )
        // 上拉 Dock（常驻）：悬浮卡 D1/D2 + 全屏 D3 应用中心，手势切换
        PullUpDock(
            state = dockState,
            onStateChange = { goDock(it) },
            onD3SwipeRight = { goDock(DockState.D2, fromD3SwipeRight = true) },
            onOpenSettings = onOpenSettings
        )
        // v0.15 宠物整理员：右侧文件夹标签栏（Dock 打开时隐藏，避免和 A-Z rail 冲突）
        PetTabsOverlay(dockHidden = dockState == DockState.Hidden)
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
