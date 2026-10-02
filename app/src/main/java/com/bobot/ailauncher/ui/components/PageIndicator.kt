package com.bobot.ailauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 首页指示器 v4（v0.10）：
 * - 只剩两点一组（6dp、8dp 间距），管左右切页；当前页深色 1.3x 缩放
 * - 切页时整行随手势方向轻推（nudge）+ 圆点平滑切换
 * - 菱形已移除：上滑改由底部横线手柄（PullUpDock）承担
 */
@Composable
fun PageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // 切页轻推：整行动画
    val nudgeX = remember { Animatable(0f) }
    var lastPage by remember { mutableIntStateOf(currentPage) }
    LaunchedEffect(currentPage) {
        if (currentPage != lastPage) {
            val dir = if (currentPage > lastPage) -1f else 1f
            lastPage = currentPage
            val distPx = with(density) { 8.dp.toPx() }
            nudgeX.snapTo(dir * distPx)
            nudgeX.animateTo(
                0f,
                spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            )
        }
    }

    Row(
        modifier = modifier.graphicsLayer { translationX = nudgeX.value },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(2) { i ->
            val selected = currentPage == i
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.3f else 1f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "dotScale"
            )
            val dotAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.35f,
                animationSpec = tween(250),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        if (selected) AILauncherColors.Title
                        else AILauncherColors.Hint.copy(alpha = dotAlpha)
                    )
            )
        }
    }
}
