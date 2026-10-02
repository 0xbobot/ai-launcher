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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
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
 * - D1 —左滑→ D2 —左滑→ D3；D3 —右滑→ D2 —右滑→ D1 —右滑→ 隐藏
 * - 上滑：隐藏→D1（首页上滑）；D1/D2→D3（直接全屏）
 * - 下滑：D3→D2→D1→隐藏（逐级收回）；点按卡片外部 → 隐藏
 */
enum class DockState { Hidden, D1, D2, D3 }

@Composable
fun PullUpDock(
    state: DockState,
    onStateChange: (DockState) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val onStateChangeState = rememberUpdatedState(onStateChange)

    val allApps = remember {
        listLaunchableApps(context).filter { it.packageName != context.packageName }
    }
    var usageTick by remember { mutableIntStateOf(0) }
    val top10 = remember(allApps, usageTick) {
        AppUsageTracker.topApps(context, allApps, 10)
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ---- D1/D2 点按外部关闭的 scrim（透明，只拦截点击） ----
        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onStateChangeState.value(DockState.Hidden) }
            )
        }

        // ---- D1/D2 悬浮玻璃卡 ----
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
                val cardShape = RoundedCornerShape(24.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .shadow(10.dp, cardShape)
                        .clip(cardShape)
                        .background(Color.White.copy(alpha = 0.66f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            cardShape
                        )
                        .pointerInput(state) {
                            var accumX = 0f
                            var accumY = 0f
                            var fired = false
                            detectDragGestures(
                                onDragStart = {
                                    accumX = 0f
                                    accumY = 0f
                                    fired = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumX += dragAmount.x
                                    accumY += dragAmount.y
                                    if (fired) return@detectDragGestures
                                    val ax = abs(accumX)
                                    val ay = abs(accumY)
                                    val go = onStateChangeState.value
                                    if (ax > hThreshPx && ax >= ay) {
                                        fired = true
                                        if (accumX < 0) {
                                            // 左滑 = 更多：D1→D2→D3
                                            if (state == DockState.D1) go(DockState.D2)
                                            else if (state == DockState.D2) go(DockState.D3)
                                        } else {
                                            // 右滑 = 更少：D2→D1→隐藏
                                            if (state == DockState.D2) go(DockState.D1)
                                            else if (state == DockState.D1) go(DockState.Hidden)
                                        }
                                    } else if (ay > vThreshPx && ay > ax) {
                                        fired = true
                                        if (accumY < 0) go(DockState.D3) // 上滑 → 全屏
                                        else go( // 下滑 → 下一层
                                            if (state == DockState.D1) DockState.Hidden
                                            else DockState.D1
                                        )
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
                            DockGrid10(apps = top10, onLaunch = ::launchApp)
                        } else {
                            DockRow4(apps = top4, onLaunch = ::launchApp)
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
                top10 = top10,
                onLaunch = ::launchApp,
                onStateChange = onStateChangeState.value,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

/**
 * D3 全屏：不透明底色整屏替换，内容为「应用中心」（分组 + A-Z 双视图）。
 * 手势签名（全 App 统一，v0.14 纠正：左滑 = 多，右滑 = 少）：
 * - D3 → D2：header 标题区右滑 / 顶部区域下滑 / 列表到顶继续下滑
 *   （右滑检测只放在 header 标题区：A-Z 行与分组组头自有横滑手势，
 *   全屏级检测会双重触发，按"最具体目标优先"收拢到标题区）
 * - 顶部"常用"横滑行是横向滚动区：行内手势由 LazyRow 先消费
 */
@Composable
private fun FullAppsOverlay(
    top10: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onStateChange: (DockState) -> Unit,
    onOpenSettings: () -> Unit
) {
    val onStateChangeState = rememberUpdatedState(onStateChange)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AILauncherColors.Background) // 不透明整屏替换，不再透出下层
    ) {
        AppCenterContent(
            top10 = top10,
            onLaunch = onLaunch,
            onOpenSettings = onOpenSettings,
            onPullDownToD2 = { onStateChangeState.value(DockState.D2) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/** 第一档：4 个常用大图标（56dp），只留图标，卡内严格垂直居中 */
@Composable
private fun DockRow4(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            AppIconImage(
                drawable = app.icon,
                contentDescription = app.label.toString(),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLaunch(app) }
            )
        }
    }
}

/** 第二档：同一批常用重排成 10 个小图标（2×5，46dp），只留图标 */
@Composable
private fun DockGrid10(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
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
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { onLaunch(app) }
                        )
                    }
                }
            }
        }
    }
}
