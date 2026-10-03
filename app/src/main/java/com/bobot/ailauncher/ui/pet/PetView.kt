package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetMouth

/**
 * 七仔（宠物 IP 形象，图片渲染）：
 * 按 mood 显示对应图片——IDLE/sleepy → qizai_idle（闭眼待机），
 * HAPPY/FILE → qizai_happy，SORTING/FETCH → qizai_sorting。
 * 位移动画由 mood 驱动：FETCH 上跳 / FILE 右移 / HAPPY 放大弹跳 / IDLE 轻微浮动。
 * mouth/blinking 为 Canvas 手绘时代遗留参数，保留签名兼容，内部不再使用。
 */
@Composable
fun PetView(
    mood: PetMood,
    mouth: PetMouth,
    blinking: Boolean,
    /** 夜晚/睡眠态：强制显示闭眼待机图 */
    sleepy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // idle 轻微浮动
    val bob by rememberInfiniteTransition(label = "petBob").animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val targetX = if (mood == PetMood.FILE) 104f else 0f
    val targetY = if (mood == PetMood.FETCH) -52f else 0f
    val targetScale = if (mood == PetMood.HAPPY) 1.15f else 1f
    val offX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "petX"
    )
    val offY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "petY"
    )
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "petScale"
    )

    val resId = when {
        sleepy || mood == PetMood.IDLE -> R.drawable.qizai_idle
        mood == PetMood.HAPPY || mood == PetMood.FILE -> R.drawable.qizai_happy
        else -> R.drawable.qizai_sorting // SORTING / FETCH
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = with(density) { offX.dp.toPx() }
                translationY = with(density) { (offY + bob).dp.toPx() }
                scaleX = scale
                scaleY = scale
            }
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = "七仔",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
