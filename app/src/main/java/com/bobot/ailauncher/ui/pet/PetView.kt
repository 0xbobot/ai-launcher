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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetMouth

/**
 * 暖光小精灵（原型 pet-v4 SVG 的 Compose 还原）：
 * 星形暖橙身体 + 眼睛 + 腮红 + 四态嘴型，右手拿小手机。
 * 位移动画由 mood 驱动：FETCH 上跳 / FILE 右移 / HAPPY 放大弹跳 / IDLE 轻微浮动。
 */
@Composable
fun PetView(
    mood: PetMood,
    mouth: PetMouth,
    blinking: Boolean,
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

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = with(density) { offX.dp.toPx() }
                translationY = with(density) { (offY + bob).dp.toPx() }
                scaleX = scale
                scaleY = scale
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPet(mouth, blinking)
        }
    }
}

/** 在 120x120 坐标系里绘制（按 Canvas 实际尺寸等比缩放） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPet(
    mouth: PetMouth,
    blinking: Boolean
) {
    val s = size.width / 120f
    val dark = Color(0xFF33272A)

    // 星形身体
    val body = Path().apply {
        moveTo(60f * s, 8f * s)
        cubicTo(66f * s, 34f * s, 86f * s, 52f * s, 112f * s, 58f * s)
        cubicTo(86f * s, 64f * s, 66f * s, 82f * s, 60f * s, 108f * s)
        cubicTo(54f * s, 82f * s, 34f * s, 64f * s, 8f * s, 58f * s)
        cubicTo(34f * s, 52f * s, 54f * s, 34f * s, 60f * s, 8f * s)
        close()
    }
    drawPath(
        body,
        Brush.verticalGradient(listOf(Color(0xFFFFE6BB), Color(0xFFFFAB72)))
    )

    // 左手臂
    withTransform({
        translate(30f * s, 74f * s)
        rotate(-24f)
    }) {
        drawOval(
            Color(0xFFFFC490),
            Offset(-10f * s, -6.5f * s),
            Size(20f * s, 13f * s)
        )
    }

    // 右手臂 + 小手机（绕 95,80 旋转 14°）
    withTransform({
        translate(95f * s, 80f * s)
        rotate(14f)
        translate(-95f * s, -80f * s)
    }) {
        withTransform({
            translate(94f * s, 72f * s)
            rotate(20f)
        }) {
            drawOval(
                Color(0xFFFFC490),
                Offset(-10f * s, -6.5f * s),
                Size(20f * s, 13f * s)
            )
        }
        drawRoundRect(
            Color(0xFF3A3F5C),
            Offset(84f * s, 62f * s),
            Size(23f * s, 36f * s),
            CornerRadius(6.5f * s)
        )
        drawRoundRect(
            Color(0xFF8FB8FF),
            Offset(87.5f * s, 66.5f * s),
            Size(16f * s, 25f * s),
            CornerRadius(3f * s)
        )
        drawCircle(Color(0xFF8A8FA8), 1.7f * s, Offset(95.5f * s, 95f * s))
    }

    // 腮红
    val blush = Color(0xFFFF8F9F).copy(alpha = 0.5f)
    drawOval(blush, Offset((36f - 7f) * s, (60f - 4.6f) * s), Size(14f * s, 9.2f * s))
    drawOval(blush, Offset((84f - 7f) * s, (60f - 4.6f) * s), Size(14f * s, 9.2f * s))

    // 眼睛（眨眼时压扁）
    val eyeSY = if (blinking) 0.12f else 1f
    listOf(46f, 74f).forEach { ex ->
        withTransform({
            translate(ex * s, 52f * s)
            scale(1f, eyeSY)
        }) {
            drawOval(dark, Offset(-7f * s, -8.6f * s), Size(14f * s, 17.2f * s))
        }
        if (!blinking) {
            drawCircle(Color.White, 2f * s, Offset((ex + 2f) * s, 50f * s))
        }
    }

    // 嘴型四态
    if (mouth == PetMouth.O) {
        drawOval(dark, Offset(56f * s, 62f * s), Size(8f * s, 9f * s))
    } else {
        val p = Path()
        when (mouth) {
            PetMouth.IDLE -> {
                p.moveTo(53f * s, 66f * s)
                p.quadraticBezierTo(60f * s, 71f * s, 67f * s, 66f * s)
            }
            PetMouth.HAPPY -> {
                p.moveTo(50f * s, 64f * s)
                p.quadraticBezierTo(60f * s, 74f * s, 70f * s, 64f * s)
            }
            PetMouth.BUSY -> {
                p.moveTo(54f * s, 67f * s)
                p.lineTo(66f * s, 67f * s)
            }
            PetMouth.O -> {}
        }
        drawPath(
            p,
            dark,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.6f * s,
                cap = StrokeCap.Round
            )
        )
    }
}
