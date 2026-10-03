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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
 * P0：呼吸（4s 缩放）+ 眨眼（140ms 图片快切）+ 点按果冻（jellyTick 触发）。
 * mouth/blinking 为 Canvas 手绘时代遗留参数，保留签名兼容，内部不再使用。
 */
@Composable
fun PetView(
    mood: PetMood,
    mouth: PetMouth,
    blinking: Boolean,
    /** 夜晚/睡眠态：强制显示闭眼待机图 */
    sleepy: Boolean = false,
    /** 点按果冻：每次 +1 触发一次果冻回弹（纯视觉反馈） */
    jellyTick: Int = 0,
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
    // P0：呼吸——4s 一次的细微缩放，比浮动更"活"
    val breathe by rememberInfiniteTransition(label = "petBreathe").animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    // P0：眨眼——每 4s 一次，140ms 图片快切（睁眼→闭眼→睁眼）
    var blinkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            blinkTick++
            delay(140)
            blinkTick++
        }
    }
    val isBlinking = blinkTick % 2 == 1
    // P0：点按果冻——按压 120ms 后 spring 回弹（纯视觉反馈）
    var jellyPress by remember { mutableStateOf(false) }
    LaunchedEffect(jellyTick) {
        if (jellyTick > 0) {
            jellyPress = true
            delay(120)
            jellyPress = false
        }
    }
    val jelly by animateFloatAsState(
        targetValue = if (jellyPress) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.35f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jelly"
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

    val baseResId = when {
        sleepy || mood == PetMood.IDLE -> R.drawable.qizai_idle
        mood == PetMood.HAPPY || mood == PetMood.FILE -> R.drawable.qizai_happy
        else -> R.drawable.qizai_sorting // SORTING / FETCH
    }
    // 眨眼时强制显示闭眼图（140ms）
    val resId = if (isBlinking && !sleepy && mood != PetMood.IDLE) {
        R.drawable.qizai_idle
    } else {
        baseResId
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = with(density) { offX.dp.toPx() }
                translationY = with(density) { (offY + bob).dp.toPx() }
                val s = scale * breathe * jelly
                scaleX = s
                scaleY = s
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
