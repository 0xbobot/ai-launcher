package com.bobot.ailauncher.ui.theme

import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat

/**
 * 批B ⑧：窗口 insets 统一收口（竖屏 + 共享层）。
 *
 * 此前各处手工散写：MainActivity 手工 setDecorFitsSystemWindows、AppCenter /
 * SettingsScreen / IslandToast 各写 statusBarsPadding、PullUpDock 直读
 * WindowInsets.systemGestures。全部收敛到本文件三个入口，取值逐一等价、像素零变化。
 * 边界：横屏文件（AppCenterLandscape 等）的 inset 处理归横屏专项线，不在此列。
 */

/**
 * Activity 侧 edge-to-edge：内容延伸到状态栏/导航栏之后。
 * 注意不用 androidx.activity 的 enableEdgeToEdge()——它默认附带 SystemBarStyle
 * 自动明暗处理，会改状态栏图标观感；这里只收口原 MainActivity 的手工调用本身。
 */
fun enableEdgeToEdge(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/**
 * 顶部状态栏 inset padding。等价于 statusBarsPadding()：
 * WindowInsets.statusBars 只有 Top 分量，only(Top) 取值相同。
 * 注：WindowInsets.statusBars 的 getter 本身是 @Composable，故本 helper 亦然；
 * 调用点都在组合上下文（Modifier 链），与原 statusBarsPadding() 调用位置一致。
 */
@Composable
fun Modifier.statusBarsTopPadding(): Modifier =
    windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))

/**
 * 底部系统手势区高度（px）——Dock 拖拽起点避让系统手势用。
 * 等价于原 PullUpDock 内联的 WindowInsets.systemGestures.getBottom(density)。
 */
@Composable
fun systemGestureBottomPx(): Int {
    val density = LocalDensity.current
    return WindowInsets.systemGestures.getBottom(density)
}
