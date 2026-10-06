package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.abs

/**
 * 上拉 Dock（v0.14：手势签名纠正为「左滑=多，右滑=少」，横线手柄彻底去掉）。
 *
 * 状态机（全部离散档位，弹簧动画切换，不再连续拖动）：
 * - Hidden：什么都不显示
 * - D1：悬浮玻璃卡（iOS dock 式：左右 16dp、底部 16dp，严格居中），4 个常用大图标（56dp，卡内垂直居中）
 * - D2：同一张卡片，10 个小图标（2×5，46dp）；d1↔d2 内容 crossfade + scale
 * - D3：不透明整屏替换（App 底色），顶部常用横滑一行 + 全部应用 A-Z 列表 + 弧形 A-Z 导航
 *
 * 手势签名（全 App 统一，v0.14 纠正）：左滑 = 更多，右滑 = 更少
 * - D1 —左滑→ D2 —左滑→ D3；D3 —右滑→ D2 —右滑→ D1
 * - v0.16：隐藏的唯一入口是 D1 右滑（弹确认框，"不再提醒"可记）；D1 下滑不再隐藏；
 *   点按卡片外部不再隐藏（D2→D1 收一档，D1 无动作）
 * - 上滑：隐藏→记住的行数（首页上滑）；D1/D2→D3（直接全屏）
 * - 下滑：D3→D2→D1（逐级收回）；点按卡片外部 → D2 收回 D1
 */
enum class DockState { Hidden, D1, D2, D3 }

@Composable
fun PullUpDock(
    state: DockState,
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onHideDockRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val onStateChangeState = rememberUpdatedState(onStateChange)
    val onD3SwipeRightState = rememberUpdatedState(onD3SwipeRight)
    val onHideDockRequestState = rememberUpdatedState(onHideDockRequest)

    val allApps = remember {
        listLaunchableApps(context).filter { it.packageName != context.packageName }
    }
    var usageTick by remember { mutableIntStateOf(0) }
    // v0.20：智能排序开关（设置页可关）；key 里带上开关值，切换后返回即生效
    val smartSort = UiPrefs.getDockSmartSort(context)
    // v0.35.0：用户置顶——排最前（按用户自定义顺序），后面用智能排序补齐并去重
    val dockTick by UiPrefs.dockTick.collectAsState()
    val pinnedPkgs = remember(dockTick) { UiPrefs.getDockPinned(context) }
    val top10 = remember(allApps, usageTick, smartSort, dockTick) {
        val pinned = pinnedPkgs.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
        val rest = AppUsageTracker.topApps(
            context,
            allApps.filter { it.packageName !in pinnedPkgs },
            10,
            smartSort
        )
        (pinned + rest).take(10)
    }
    val top4 = remember(top10) { top10.take(4) }

    fun launchApp(app: AppInfo) {
        AppUsageTracker.recordLaunch(context, app.packageName)
        usageTick++
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "无法打开「${app.label}」", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "无法打开「${app.label}」", Toast.LENGTH_SHORT).show()
        }
    }

    val cardVisible = state == DockState.D1 || state == DockState.D2
    // v0.39.0：Dock 长按 → 真弹窗快捷菜单（锚定在图标上）
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // v0.27.4：删除全屏"点按外部收起"层（Bob：它挡住了主页非 Dock 区上滑，导致 D2→D1）
        // D2→D1 改由下滑/右滑手势触发

        // ---- D1/D2 悬浮图标（无卡片，v0.27.1 去底） ----
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = cardVisible,
                enter = slideInVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.86f
                    )
                ) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = 0.9f
                    )
                ) { it } + fadeOut(tween(150)),
                label = "dockCard"
            ) {
                val hThreshPx = with(density) { 48.dp.toPx() }
                val vThreshPx = with(density) { 72.dp.toPx() }
                // v0.16.1：底部系统手势区高度（导航条/手势区），落在该区域内的拖拽起点不抢——
                // 让系统处理底部左右滑（切最近应用）、底部上滑（回桌面/最近任务）
                val sysGestureBottomPx = WindowInsets.systemGestures.getBottom(density)
                val dockBottomPadPx = with(density) { 16.dp.toPx() }
                // v0.27.1：去底（Bob 选一）——图标直接浮在壁纸上，无卡片无阴影
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .pointerInput(state, sysGestureBottomPx, dockBottomPadPx) {
                            var accumX = 0f
                            var accumY = 0f
                            var fired = false
                            var inSysGestureZone = false
                            detectDragGestures(
                                onDragStart = { start ->
                                    accumX = 0f
                                    accumY = 0f
                                    fired = false
                                    // 起点在底部系统手势区内（卡片底部 dockBottomPadPx 之上、
                                    // 屏幕底部 sysGestureBottomPx 之内）→ 本次不消费、不触发，交还系统
                                    val dangerPx = sysGestureBottomPx - dockBottomPadPx
                                    inSysGestureZone =
                                        dangerPx > 0 && start.y > size.height - dangerPx
                                },
                                onDrag = { change, dragAmount ->
                                    if (inSysGestureZone) return@detectDragGestures
                                    change.consume()
                                    accumX += dragAmount.x
                                    accumY += dragAmount.y
                                    if (fired) return@detectDragGestures
                                    val ax = abs(accumX)
                                    val ay = abs(accumY)
                                    val go = onStateChangeState.value
                                    // v0.27.3：上滑优先（Bob：不管什么状态，上滑都开 D3）
                                    // 斜向上滑不再被误判为右滑（D2→D1）
                                    if (ay > vThreshPx && accumY < 0) {
                                        fired = true
                                        go(DockState.D3) // 上滑 → 全屏应用中心
                                    } else if (ax > hThreshPx && ax >= ay) {
                                        fired = true
                                        if (accumX < 0) {
                                            // 左滑 = 更多：D1→D2→D3
                                            if (state == DockState.D1) go(DockState.D2)
                                            else if (state == DockState.D2) go(DockState.D3)
                                        } else {
                                            // 右滑 = 更少：D2→D1；D1 右滑 → 隐藏确认（唯一隐藏入口）
                                            if (state == DockState.D2) go(DockState.D1)
                                            else if (state == DockState.D1) onHideDockRequestState.value()
                                        }
                                    } else if (ay > vThreshPx && ay > ax) {
                                        fired = true
                                        // 下滑 → 收一档（D1 下滑不再隐藏）
                                        if (state == DockState.D2) go(DockState.D1)
                                    }
                                }
                            )
                        }
                ) {
                    // 一二档内容：crossfade + scale，卡片高度弹簧跟随
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                                scaleIn(
                                    spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = 0.9f
                                    ),
                                    initialScale = 0.92f
                                ))
                                .togetherWith(fadeOut(tween(120)))
                                .using(
                                    SizeTransform(clip = false) { _, _ ->
                                        spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = 0.9f
                                        )
                                    }
                                )
                        },
                        label = "dockContent"
                    ) { s ->
                        if (s == DockState.D2) {
                            DockGrid10(
                                apps = top10,
                                onLaunch = ::launchApp,
                                menuApp = popupApp,
                                onLongPress = { popupApp = it },
                                onDismissMenu = { popupApp = null }
                            )
                        } else {
                            DockRow4(
                                apps = top4,
                                onLaunch = ::launchApp,
                                menuApp = popupApp,
                                onLongPress = { popupApp = it },
                                onDismissMenu = { popupApp = null }
                            )
                        }
                    }
                }
            }
        }

        // ---- D3 全屏：不透明整屏替换 ----
        AnimatedVisibility(
            visible = state == DockState.D3,
            enter = slideInVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = 0.9f
                )
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = 0.9f
                )
            ) { it } + fadeOut(tween(150)),
            label = "fullOverlay"
        ) {
            FullAppsOverlay(
                onStateChange = onStateChangeState.value,
                onD3SwipeRight = onD3SwipeRightState.value,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

/**
 * D3 全屏：不透明底色整屏替换，内容为「应用中心」（单列表：分类区在上 + A-Z 在下）。
 * 手势签名（全 App 统一，v0.14 纠正：左滑 = 多，右滑 = 少）：
 * - D3 → D2：header 标题区右滑 / 顶部区域下滑 / 列表到顶继续下滑
 *   （右滑检测只放在 header 标题区：A-Z 行与分组组头自有横滑手势，
 *   全屏级检测会双重触发，按"最具体目标优先"收拢到标题区）
 * - 顶部"常用"横滑行是横向滚动区：行内手势由 LazyRow 先消费
 */
@Composable
private fun FullAppsOverlay(
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val onStateChangeState = rememberUpdatedState(onStateChange)
    val onD3SwipeRightState = rememberUpdatedState(onD3SwipeRight)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // v0.25.7：玻璃半透明——壁纸透出， frosted glass 效果
            .background(Color.White.copy(alpha = 0.72f))
    ) {
        AppCenterContent(
            onOpenSettings = onOpenSettings,
            onPullDownToD2 = { onStateChangeState.value(DockState.D2) },
            onHeaderSwipeRight = { onD3SwipeRightState.value() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/** 第一档：4 个常用大图标（56dp），只留图标，卡内严格垂直居中 */
@Composable
private fun DockRow4(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        // v0.27.2：居中（之前 spacedBy 默认左对齐）
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            DockIconButton(
                app = app,
                iconSize = 56.dp,
                corner = 16.dp,
                menuApp = menuApp,
                onLaunch = onLaunch,
                onLongPress = onLongPress,
                onDismissMenu = onDismissMenu
            )
        }
    }
}

/** 第二档：同一批常用重排成 10 个小图标（2×5，46dp），只留图标 */
@Composable
private fun DockGrid10(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { app ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        DockIconButton(
                            app = app,
                            iconSize = 46.dp,
                            corner = 13.dp,
                            menuApp = menuApp,
                            onLaunch = onLaunch,
                            onLongPress = onLongPress,
                            onDismissMenu = onDismissMenu
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.39.0：Dock 图标按钮——点击启动，长按弹出真弹窗快捷菜单。
 * 菜单锚定在图标所在的 Box 上（图标在屏幕下方时自动弹到图标上面）。
 */
@Composable
private fun DockIconButton(
    app: AppInfo,
    iconSize: Dp,
    corner: Dp,
    menuApp: AppInfo?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(corner))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress(app)
                    }
                )
        )
        AppPopupMenu(
            app = app,
            expanded = menuApp == app,
            onDismiss = onDismissMenu
        )
    }
}
