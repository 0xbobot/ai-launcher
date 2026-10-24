package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.BatteryState
import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 漂浮玻璃头（曜仔）代码层：混合路线的常驻生命层。
 *
 * - 位置由调用方定（Dock 上方居中、留 12dp 气隙）；本体等比例不变形。
 * - 呼吸起伏：AliveLogic 游走（bob 基线 3500ms ±20%、幅度 5px ±20%
 *   = ±4–6px、约 3–4 秒周期）；光晕随呼吸明暗。
 * - 眨眼：与视频层共享 [PetBlinkState]（140ms，眼部纵向压缩，同 alive-p1）。
 * - 注视：PetView 同款导演（repo 目标 ＞ 瞥视 ＞ 巡看），瞳孔先行、
 *   头部延迟跟上；低电量/后台时循环停、画面静止。
 * - 全程无脚下阴影：光晕是头周围的径向晕，不画在 Dock 面上的投影。
 *
 * 玻璃头眼盒（相对位图坐标，Python 离线检测 glass-cut.png 1043×1318）：
 * 左眼 l=0.207 t=0.650 r=0.343 b=0.785；右眼 l=0.660 t=0.650 r=0.793 b=0.770。
 * 眨眼画法与 PetView 同款（眼盒区域纵向压缩到 8%），区别是底图是
 * 玻璃头而非七仔毛绒，调用方不再需要传 bitmap——资源 ID 固定。
 */
/** 玻璃头眼盒（相对位图坐标，Python 离线检测 glass-cut.png 1043×1318）。 */
internal val GLASS_EYE_BOXES = listOf(
    FRect(0.207f, 0.650f, 0.343f, 0.785f),
    FRect(0.660f, 0.650f, 0.793f, 0.770f),
)

@Composable
fun FloatingPet(
    petWidth: Dp,
    blinkState: PetBlinkState,
    active: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val lowPower by BatteryState.isLowPower.collectAsState()
    val motionActive = active && !lowPower && !reduceMotion

    // 玻璃头长宽比（1043×1318），盒子按此定高，不拉伸。
    val petHeight = petWidth * (1318f / 1043f)

    // —— 呼吸起伏（AliveLogic 游走，32ms tick）——
    var bobPhase by remember { mutableFloatStateOf(0f) }
    var bobPeriodMs by remember { mutableFloatStateOf(3500f) }
    var bobAmpPx by remember { mutableFloatStateOf(5f) }
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (isActive) {
            delay(32)
            val now = System.currentTimeMillis()
            val dt = (now - last).coerceIn(0, 100)
            last = now
            bobPhase += dt.toFloat() / bobPeriodMs * 2f * Math.PI.toFloat()
            if (bobPhase > 2f * Math.PI.toFloat()) {
                bobPhase -= 2f * Math.PI.toFloat()
                bobPeriodMs = 3500f * (1f + (Random.nextFloat() * 2f - 1f) * 0.2f)
                bobAmpPx = 5f * (1f + (Random.nextFloat() * 2f - 1f) * 0.2f)
            }
        }
    }
    val bobPx = if (motionActive) -kotlin.math.sin(bobPhase) * bobAmpPx else 0f

    // —— 眨眼间隔循环（BlinkPolicy，低电量/后台停）——
    LaunchedEffect(active, lowPower) {
        if (!active || lowPower) return@LaunchedEffect
        var next = BlinkPolicy.intervalMs(lowPower, Random)
        while (isActive) {
            delay(next)
            if (lowPower) break
            blinkState.requestBlink(this)
            next = BlinkPolicy.intervalMs(lowPower, Random)
        }
    }

    // —— 注视导演（PetView 同款优先级，Dock 宠物不呈现输入态故无倾听层）：
    // repo 目标 ＞ 瞥视（600ms 持有）＞ 巡看（8–15 秒一次，停留 1.2 秒）——
    val repoGazeTarget by PetRepository.gazeTarget.collectAsState()
    val repoGlanceTarget by PetRepository.glanceTarget.collectAsState()
    val repoGlanceTick by PetRepository.glanceTick.collectAsState()
    var glanceTarget by remember { mutableStateOf<GazeTarget?>(null) }
    LaunchedEffect(repoGlanceTick) {
        if (repoGlanceTick > 0) {
            glanceTarget = repoGlanceTarget
            delay(600) // 瞥视至多 600ms，到期释放（新瞥视重启本 effect 刷新窗口）
            glanceTarget = null
        }
    }
    var patrolTarget by remember { mutableStateOf(GazeTarget.CENTER) }
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(8000, 15000))
            patrolTarget = patrolTargetPool().random()
            delay(1200)
            patrolTarget = GazeTarget.CENTER
        }
    }
    val effectiveGazeTarget = when {
        repoGazeTarget != GazeTarget.CENTER -> repoGazeTarget
        glanceTarget != null -> glanceTarget!!
        else -> patrolTarget
    }
    val gazeOff = gazeOffsetFor(effectiveGazeTarget)
    // 瞳孔先行：快速 200ms 到位（x ±6px，y ±4px）
    val pupilX by animateFloatAsState(
        targetValue = gazeOff.x * 6f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatPupilX",
    )
    val pupilY by animateFloatAsState(
        targetValue = gazeOff.y * 4f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatPupilY",
    )
    // 头部延迟 80ms 跟随：280ms 到位（x ±10px，y ±6px）
    var headGaze by remember { mutableStateOf(GazeOffset(0f, 0f)) }
    LaunchedEffect(effectiveGazeTarget) {
        delay(80)
        headGaze = gazeOff
    }
    val headX by animateFloatAsState(
        targetValue = headGaze.x * 10f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "floatHeadX",
    )
    val headY by animateFloatAsState(
        targetValue = headGaze.y * 6f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "floatHeadY",
    )

    // —— 眨眼：眼盒纵向压缩（同 PetView 的 eyeScale 画法，140ms）——
    val eyeScale by animateFloatAsState(
        targetValue = if (blinkState.blinking) 0.08f else 1f,
        animationSpec = tween(70),
        label = "floatEyeScale",
    )

    // —— 点击回弹（果冻式，120ms）——
    var pressed by remember { mutableStateOf(false) }
    val popScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "floatPop",
    )
    val touch = maxOf(petWidth, 48.dp)

    Box(
        modifier = modifier
            .size(width = touch, height = maxOf(petHeight, 48.dp))
            .semantics {
                contentDescription = "问曜仔"
                this.role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    pressed = true
                    onTap()
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 光晕：头周围的径向晕，随呼吸明暗（不是脚下阴影）。
        val glowAlpha = 0.30f + 0.12f * kotlin.math.sin(bobPhase)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glowAlpha),
                        Color.White.copy(alpha = 0f),
                    ),
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * 0.45f),
            )
        }
        // 玻璃头本体：底层整图 + 眨眼眼带压缩覆盖层（与 PetView 同款做法的
        // Image 版：只在压缩进行中绘制，scale=1 时与底图像素完全重合，
        // 平时不画，避免双重绘制的接缝）。
        Box(
            modifier = Modifier
                .size(width = petWidth, height = petHeight)
                .graphicsLayer {
                    // PetView 同款两段式：瞳孔先行（±6px/±4px，200ms）
                    // + 头部延迟跟上（±10px/±6px，80ms 延迟 280ms）。
                    translationX = headX + pupilX
                    translationY = headY + pupilY + bobPx
                    scaleX = popScale
                    scaleY = popScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.glass_head),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (eyeScale < 0.999f) {
                // 与 PetView 一致：直接取 ImageBitmap，不走 BitmapPainter。
                val blinkBmp = androidx.compose.ui.graphics.ImageBitmap
                    .imageResource(R.drawable.glass_head)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 与底图 ContentScale.Fit 完全一致的放置数学
                    // （盒子与位图同比，实际无留白）。
                    val fit = minOf(
                        size.width / blinkBmp.width,
                        size.height / blinkBmp.height,
                    )
                    val dw = blinkBmp.width * fit
                    val dh = blinkBmp.height * fit
                    val left = (size.width - dw) / 2f
                    val top = (size.height - dh) / 2f
                    for (b in GLASS_EYE_BOXES) {
                        val sbl = (b.l * blinkBmp.width).toInt()
                        val sbt = (b.t * blinkBmp.height).toInt()
                        val sbw = ((b.r - b.l) * blinkBmp.width).toInt()
                            .coerceAtLeast(2)
                        val sbh = ((b.b - b.t) * blinkBmp.height).toInt()
                            .coerceAtLeast(4)
                        val dbl = left + b.l * dw
                        val dbt = top + b.t * dh
                        val dbr = left + b.r * dw
                        val dbb = top + b.b * dh
                        val dbw = (dbr - dbl).toInt().coerceAtLeast(2)
                        val dbh = ((dbb - dbt) * eyeScale).toInt()
                            .coerceAtLeast(2)
                        val cy = (dbt + dbb) / 2f
                        drawImage(
                            image = blinkBmp,
                            srcOffset = androidx.compose.ui.unit.IntOffset(sbl, sbt),
                            srcSize = androidx.compose.ui.unit.IntSize(sbw, sbh),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                dbl.toInt(),
                                (cy - dbh / 2f).toInt(),
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(dbw, dbh),
                        )
                    }
                }
            }
        }
        // 回弹复位：点击后 120ms 松开。
        LaunchedEffect(pressed) {
            if (pressed) {
                delay(120)
                pressed = false
            }
        }
    }
}
