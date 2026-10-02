package com.bobot.ailauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * 首页指示器 v3.5（v0.7 样式重构）：
 * - 两点一组（6dp、8dp 间距）：首页 / 能力页；当前页深色 1.3x 缩放，非当前页半透明灰
 * - 实心菱形 12dp 悬浮在两点正上方（水平居中，底端与圆点顶端留约 2dp 间隙），
 *   上方再叠两颗渐隐小菱形（9dp @32%、7dp @16%）作"上升拖尾"
 * - 菱形组常驻呼吸浮动（上下约 3dp、周期约 2.8s）
 * - 点按菱形 / 上滑：菱形上跳约 13dp 回弹（带透明度变化）
 * - 切页时整行随手势方向轻推（nudge）+ 圆点平滑切换
 *
 * 注：原型是深色稿（选中白色）；App 是浅色主题，白色在浅底上不可见，
 * 这里按"深色强调 / 弱灰"转译，设计语言不变。
 */
@Composable
fun PageIndicator(
    currentPage: Int,
    jumpTick: Int,
    onDiamondClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // 弱灰（浅底转译，原型 #C9C9D4 在深底上的相对观感）
    val diamondColor = Color(0xFF9BA0AA)

    // ---- 切页轻推：整行动画 ----
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

    // ---- 呼吸浮动：2.8s 周期 ----
    val breatheInf = rememberInfiniteTransition(label = "diamondBreathe")
    val breatheY by breatheInf.animateFloat(
        initialValue = 0f,
        targetValue = with(density) { -3.dp.toPx() },
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheY"
    )

    // ---- 上跳回弹（点按 / 上滑 / 首次引导时 jumpTick+1） ----
    val jumpY = remember { Animatable(0f) }
    val jumpAlpha = remember { Animatable(1f) }
    LaunchedEffect(jumpTick) {
        if (jumpTick <= 0) return@LaunchedEffect
        val upPx = with(density) { -13.dp.toPx() }
        launch {
            jumpY.animateTo(upPx, tween(260, easing = FastOutSlowInEasing))
            jumpY.animateTo(
                0f,
                spring(
                    stiffness = Spring.StiffnessLow,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            )
        }
        launch {
            jumpAlpha.animateTo(0.35f, tween(260))
            jumpAlpha.animateTo(1f, tween(320))
        }
    }

    Box(
        modifier = modifier.graphicsLayer { translationX = nudgeX.value }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 菱形组：可点，触区放大但不抢视觉
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = breatheY + jumpY.value
                        alpha = jumpAlpha.value
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDiamondClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Diamond(size = 7.dp, color = diamondColor.copy(alpha = 0.16f))
                    Diamond(size = 9.dp, color = diamondColor.copy(alpha = 0.32f))
                    Diamond(size = 12.dp, color = diamondColor)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            // 两点：当前页深色 1.3x
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val dotColor by animateFloatAsState(
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
                                else AILauncherColors.Hint.copy(alpha = dotColor)
                            )
                    )
                }
            }
        }
    }
}

/** 实心菱形 */
@Composable
private fun Diamond(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h / 2f)
                lineTo(w / 2f, h)
                lineTo(0f, h / 2f)
                close()
            },
            color = color
        )
    }
}
