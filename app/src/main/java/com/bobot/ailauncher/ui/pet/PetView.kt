package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetMouth
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    // v0.28.1：低电量省电模式——七仔变困、动画降频
    val lowPower by com.bobot.ailauncher.data.BatteryState.isLowPower
        .collectAsState()
    // 低电量时强制闭眼（像没电犯困）
    val effectiveSleepy = sleepy || lowPower
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
    // v0.28.1：低电量时停止呼吸（静态，省电）
    val breathe: Float = if (lowPower) {
        1f
    } else {
        rememberInfiniteTransition(label = "petBreathe").animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathe"
        ).value
    }
    // P0：眨眼——每 4s 一次，140ms 图片快切（睁眼→闭眼→睁眼）
    // v0.28.1：低电量时 12s 眨一次（本来就闭眼，少折腾）
    var blinkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lowPower) {
        while (true) {
            delay(if (lowPower) 12000 else 4000)
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
        effectiveSleepy || mood == PetMood.IDLE -> R.drawable.qizai_idle
        mood == PetMood.HAPPY || mood == PetMood.FILE -> R.drawable.qizai_happy
        else -> R.drawable.qizai_sorting // SORTING / FETCH
    }
    // 眨眼时强制显示闭眼图（140ms）；低电量时本来就闭眼，不眨了
    val resId = if (isBlinking && !effectiveSleepy && mood != PetMood.IDLE) {
        R.drawable.qizai_idle
    } else {
        baseResId
    }

    // v0.26.0：拖拽——<40dp 范围内跟手，松手 spring 回弹（纯玩）
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val dragX by animateFloatAsState(
        targetValue = dragOffset.x,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "dragX"
    )
    val dragY by animateFloatAsState(
        targetValue = dragOffset.y,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "dragY"
    )
    val maxDragPx = with(density) { 40.dp.toPx() }

    Box(
        // v0.26.4：拖拽放外层（先于 clickable），解决拖拽不动
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = dragOffset.x + dragAmount.x
                        val newY = dragOffset.y + dragAmount.y
                        // 限制在 40dp 半径内
                        val dist = sqrt(newX * newX + newY * newY)
                        dragOffset = if (dist > maxDragPx) {
                            androidx.compose.ui.geometry.Offset(
                                newX / dist * maxDragPx,
                                newY / dist * maxDragPx
                            )
                        } else {
                            androidx.compose.ui.geometry.Offset(newX, newY)
                        }
                    },
                    onDragEnd = {
                        // 松手回弹
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                    },
                    onDragCancel = {
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                    }
                )
            }
            .then(modifier)
            .graphicsLayer {
                translationX = with(density) { offX.dp.toPx() } + dragX
                translationY = with(density) { (offY + bob).dp.toPx() } + dragY
                val s = scale * breathe * jelly
                scaleX = s
                scaleY = s
            }
    ) {
        // v0.26.0：图片切换 Crossfade（代替硬切）
        Crossfade(targetState = resId, label = "petImage") { id ->
            Image(
                painter = painterResource(id),
                contentDescription = "七仔",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
