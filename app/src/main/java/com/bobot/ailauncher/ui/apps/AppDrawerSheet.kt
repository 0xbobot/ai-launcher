package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 抽屉三档：露出把手+搜索 / 半屏 / 全屏（值为 sheet 占屏幕高度的比例） */
enum class DrawerDetent(val fraction: Float) {
    Peek(0.22f),
    Half(0.55f),
    Full(1.0f)
}

/**
 * 可拖动的应用抽屉 BottomSheet。
 * - 手指按住顶部把手区上下拖动，跟手；松手后按速度/位置吸附到最近档位
 * - 在 Peek 档继续下滑超过阈值 → 关闭；点击背景 scrim → 关闭
 * - 全屏时列表到顶继续下滑 → 收起 sheet（嵌套滚动联动）
 * - [dismissTick] +1 时播出场动画后关闭（用于首次引导 peek）
 */
@Composable
fun AppDrawerSheet(
    initialDetent: DrawerDetent = DrawerDetent.Half,
    dismissTick: Int = 0,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenH = constraints.maxHeight.toFloat()
        if (screenH <= 0f) return@BoxWithConstraints
        val hiddenY = screenH
        fun targetFor(d: DrawerDetent) = screenH * (1f - d.fraction)
        val fullTarget = targetFor(DrawerDetent.Full)
        val ordered = DrawerDetent.entries.map { targetFor(it) }.sorted() // 全屏→半屏→peek（由小到大）

        val offsetY = remember { Animatable(hiddenY) }
        var dragging by remember { mutableStateOf(false) }
        var currentDetent by remember { mutableStateOf(initialDetent) }
        val listState = rememberLazyListState()
        // 搜索框聚焦时若在 peek 档 → 展开到半屏，避免被键盘盖住
        var searchFocusTick by remember { mutableIntStateOf(0) }

        suspend fun dismiss() {
            offsetY.animateTo(hiddenY, spring(stiffness = Spring.StiffnessMedium))
            onDismiss()
        }

        fun settle(velocity: Float) {
            scope.launch {
                val current = offsetY.value
                val peekTarget = targetFor(DrawerDetent.Peek)
                // 拖过 peek 一大截 → 直接关闭
                if (current > peekTarget + with(density) { 90.dp.toPx() }) {
                    dismiss()
                    return@launch
                }
                val target = when {
                    velocity < -600f ->
                        ordered.filter { it < current - 1f }.minOrNull() ?: fullTarget
                    velocity > 600f -> {
                        val lower = ordered.filter { it > current + 1f }.maxOrNull()
                        if (lower == null) {
                            dismiss()
                            return@launch
                        }
                        lower
                    }
                    else -> ordered.minByOrNull { abs(it - current) } ?: current
                }
                currentDetent = DrawerDetent.entries.first { targetFor(it) == target }
                offsetY.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }

        // 入场
        LaunchedEffect(Unit) {
            offsetY.animateTo(targetFor(initialDetent), spring(stiffness = Spring.StiffnessMediumLow))
        }
        // 外部关闭信号（首次引导 peek 收回）
        LaunchedEffect(dismissTick) {
            if (dismissTick > 0) dismiss()
        }
        // 搜索聚焦 → 从 peek 展开到半屏
        LaunchedEffect(searchFocusTick) {
            if (searchFocusTick > 0 && currentDetent == DrawerDetent.Peek) {
                currentDetent = DrawerDetent.Half
                offsetY.animateTo(targetFor(DrawerDetent.Half), spring(stiffness = Spring.StiffnessMediumLow))
            }
        }

        // 列表到顶下滑 → sheet 接管收起（嵌套滚动）
        val nested = remember(screenH) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput || dragging) return Offset.Zero
                    val atTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    if (available.y > 0f && atTop && offsetY.value < hiddenY - 1f) {
                        val target = (offsetY.value + available.y).coerceIn(fullTarget, hiddenY)
                        val consumed = target - offsetY.value
                        if (consumed > 0.5f) {
                            scope.launch { offsetY.snapTo(target) }
                            return Offset(0f, consumed)
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        // 背景 scrim：随 sheet 开合淡入淡出，点击关闭
        val openness = (1f - offsetY.value / hiddenY).coerceIn(0f, 1f)
        if (openness > 0.02f && !dragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f * openness))
                    .clickable { scope.launch { dismiss() } }
            )
        }

        // Sheet 本体
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offsetY.value }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(AILauncherColors.Background)
            ) {
                // 把手区（拖拽手柄）：整块可拖
                val velocityTracker = remember { VelocityTracker() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(screenH) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    velocityTracker.resetTracking()
                                },
                                onDragEnd = {
                                    dragging = false
                                    val v = try {
                                        velocityTracker.calculateVelocity().y
                                    } catch (_: Exception) {
                                        0f
                                    }
                                    settle(v)
                                },
                                onDragCancel = {
                                    dragging = false
                                    settle(0f)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(
                                        change.uptimeMillis,
                                        change.position
                                    )
                                    val target = (offsetY.value + dragAmount)
                                        .coerceIn(fullTarget, hiddenY)
                                    scope.launch { offsetY.snapTo(target) }
                                }
                            )
                        }
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AILauncherColors.Divider)
                    )
                }
                // 内容：搜索 + 列表（A-Z 索引条只在半屏以上显示）
                AllAppsContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    listState = listState,
                    nestedScrollConnection = nested,
                    onSearchFocus = { searchFocusTick++ },
                    showIndexBar = openness > 0.35f,
                    topPadding = 0.dp
                )
            }
        }
    }
}
