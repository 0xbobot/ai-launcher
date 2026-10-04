package com.bobot.ailauncher.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bobot.ailauncher.ui.settings.SettingsScreen
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.14 导航：单页桌面（能力页已移除，左滑/右滑只剩卡片手势语义：左=多/右=少）。
 * - 只有首页；上拉 Dock（D1/D2/D3）是找应用的唯一入口
 * - v0.16：首页上滑直达 D3「应用中心」，不再经 D1；隐藏的唯一入口是 D1 右滑+确认框
 * - NavHost 只剩 "home" 和 "settings"（应用中心的齿轮进入）
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    var dockVisible by remember { mutableStateOf(UiPrefs.getDockVisible(context)) }
    // v0.15.1：Dock 默认显示记住的行数（默认 D1 一行），不再默认隐藏
    var dockState by remember {
        mutableStateOf(if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1)
    }
    // v0.26.6：返回键处理——D3（应用中心）返回主页，首页不做事
    // 用 BackHandler 拦截，避免系统默认行为导致页面刷新/重建
    androidx.activity.compose.BackHandler(enabled = true) {
        if (dockState == DockState.D3) {
            // 应用中心返回主页
            val target = if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1
            dockState = target
        }
        // 首页：不做事（消费掉返回键）
    }
    var showHideDockDialog by remember { mutableStateOf(false) }

    // v0.15.1：Dock 状态机统一出口
    // - 记住行数：D1 左滑→D2 时记 2，D2 右滑→D1 时记 1
    // - 隐藏上滑唤出 / D3 右滑返回 → 直接回到记住的行数（D1/D2）
    // - v0.16：隐藏的唯一入口是 D1 右滑（经 requestHideDock 确认）；D1 下滑/点按外部不再隐藏
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

    fun doHideDock() {
        UiPrefs.setDockVisible(context, false)
        dockVisible = false
        dockState = DockState.Hidden
    }

    fun requestHideDock() {
        if (UiPrefs.getDockHideWarned(context)) doHideDock()
        else showHideDockDialog = true
    }

    // v0.16：设置页"显示 Dock"开关 → 恢复到记住的行数
    fun setDockVisibleAndRestore(visible: Boolean) {
        UiPrefs.setDockVisible(context, visible)
        dockVisible = visible
        if (visible) {
            goDock(if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1)
        } else {
            dockState = DockState.Hidden
        }
    }

    // v0.16：上滑直达 D3 不受 dockVisible 影响（否则隐藏 Dock 后将无法进入应用中心）；
    // 上滑不改变 Dock 显示/隐藏状态
    val effectiveDockState = if (dockVisible || dockState == DockState.D3) dockState
    else DockState.Hidden

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeHost(
                dockState = effectiveDockState,
                onOpenAppDrawer = { goDock(DockState.D3) },
                onStateChange = { goDock(it) },
                onD3SwipeRight = { goDock(DockState.D2, fromD3SwipeRight = true) },
                onHideDockRequest = { requestHideDock() },
                onOpenSettings = { navController.navigate("settings") },
                showHideDockDialog = showHideDockDialog,
                onHideDockDialogDismiss = { showHideDockDialog = false },
                onHideDockConfirm = { noRemind ->
                    if (noRemind) UiPrefs.setDockHideWarned(context, true)
                    showHideDockDialog = false
                    doHideDock()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                dockVisible = dockVisible,
                onDockVisibleChange = { setDockVisibleAndRestore(it) }
            )
        }
    }
}

@Composable
private fun HomeHost(
    dockState: DockState,
    onOpenAppDrawer: () -> Unit,
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onHideDockRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    showHideDockDialog: Boolean,
    onHideDockDialogDismiss: () -> Unit,
    onHideDockConfirm: (Boolean) -> Unit
) {
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

    // v0.15 宠物整理员：toast 事件 → 系统 Toast
    LaunchedEffect(Unit) {
        PetRepository.toast.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // v0.32.0：全透明——去掉渐隐罩，壁纸直接透出（Bob）
        // 单页桌面：只有首页；v0.16 首页上滑直达 D3，不再经 D1
        // v0.31.0：首屏只留 Dock（Bob）
        HomeScreen(onOpenAppDrawer = onOpenAppDrawer)
        // 上拉 Dock（常驻）：悬浮卡 D1/D2 + 全屏 D3 应用中心，手势切换
        PullUpDock(
            state = dockState,
            onStateChange = onStateChange,
            onD3SwipeRight = onD3SwipeRight,
            onHideDockRequest = onHideDockRequest,
            onOpenSettings = onOpenSettings
        )
        // v0.26.6：侧边标签栏已删除（Bob：和 Dock/宠物无关的全部清除）
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
        // v0.16：D1 右滑隐藏确认框（"不再提醒"可记）
        if (showHideDockDialog) {
            var noRemind by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onHideDockDialogDismiss,
                title = { Text("隐藏 Dock", fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            text = "隐藏后可在「设置」中重新打开。",
                            fontSize = 14.sp,
                            color = AILauncherColors.Body
                        )
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = noRemind,
                                onCheckedChange = { noRemind = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "不再提醒", fontSize = 13.sp, color = AILauncherColors.Body)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onHideDockConfirm(noRemind) }) {
                        Text("隐藏", color = AILauncherColors.Accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onHideDockDialogDismiss) { Text("取消") }
                }
            )
        }
    }
}
