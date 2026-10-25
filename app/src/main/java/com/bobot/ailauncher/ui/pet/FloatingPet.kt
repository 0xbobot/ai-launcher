package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.BatteryState
import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.PetRepository.PetManualAction
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * 漂浮玻璃头（曜仔）代码层：混合路线的常驻生命层。
 *
 * - 位置由调用方定（Dock 圆肩上方居中、压入肩峰 5dp）；本体等比例不变形。
 * - 原地呼吸（圆肩批）：AliveLogic 游走相位（基线 3500ms ±20%）
 *   + breatheWave 非对称波形，纵向 +1.5% / 横向 -0.7% 微缩放
 *   （绕底缩放，底部钉在肩峰上不脱离）；光晕随呼吸明暗。
 *   上下浮动已随圆肩连接取消——浮动等于每呼吸一次就脱离身体一次。
 * - 眨眼：与视频层共享 [PetBlinkState]（新幅度 240ms：闭合 85ms →
 *   保持 70ms 画粗 ∪ 形弯曲线 + 2dp 点头 → 睁开 85ms；旧 140ms 细线作废）。
 * - 注视：PetView 同款导演（repo 目标 ＞ 瞥视 ＞ 巡看），瞳孔先行、
 *   头部延迟跟上；新幅度：瞳孔 ±10px、头 ±8px、向视线方向倾斜 4°、
 *   转向 300ms；低电量/后台时循环停、画面静止。
 * - 手动动作（动作清单触发）：[PetRepository.actionTick] 事件驱动的
 *   全身动作引擎——所有动作都走平移 / 旋转 / 绕底缩放 / 眼带 / 肩线
 *   峰值（PullUpDock 侧协同），头与面板始终是一个身体，不许部件分离。
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

    // 手动动作幅度体（提前声明：呼吸缩放要读深呼吸增幅）。
    val breathBoost = remember { Animatable(0f) }
    // —— 原地呼吸（AliveLogic 游走相位 + breatheWave 非对称波形，
    // 32ms tick；上下浮动已取消，见文件头）——
    var bobPhase by remember { mutableFloatStateOf(0f) }
    var bobPeriodMs by remember { mutableFloatStateOf(3500f) }
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
            }
        }
    }
    // 纵向 +1.5% / 横向 -0.7%（体积守恒方向，与 P0 的 2.5%/0.7% 同形、
    // 幅度收进圆肩批 ±1–2% 口径）；绕底缩放，底部钉在肩峰上。
    val breathe = if (motionActive) breatheWave(bobPhase) else 0f
    // 深呼吸动作把呼吸幅度顶到约 2 倍（breathBoost 0→1）。
    val breatheScaleY = 1f + 0.015f * breathe * (1f + breathBoost.value)
    val breatheScaleX = 1f - 0.007f * breathe * (1f + breathBoost.value)

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
    // —— 手动动作目标（动作清单触发，优先级高于自主巡看）——
    var manualPatrol by remember { mutableStateOf<GazeTarget?>(null) }
    var manualTurn by remember { mutableStateOf<GazeTarget?>(null) }
    var nextTurnLeft by remember { mutableStateOf(true) }
    val effectiveGazeTarget = when {
        repoGazeTarget != GazeTarget.CENTER -> repoGazeTarget
        glanceTarget != null -> glanceTarget!!
        manualTurn != null -> manualTurn!!
        manualPatrol != null -> manualPatrol!!
        else -> patrolTarget
    }
    val gazeOff = gazeOffsetFor(effectiveGazeTarget)
    // 瞳孔先行：快速 200ms 到位（新幅度 x ±10px，y ±6px）
    val pupilX by animateFloatAsState(
        targetValue = gazeOff.x * 10f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatPupilX",
    )
    val pupilY by animateFloatAsState(
        targetValue = gazeOff.y * 6f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatPupilY",
    )
    // 头部延迟 80ms 跟随：300ms 到位（新幅度 x ±8px，y ±5px）+ 向视线方向倾斜 4°
    var headGaze by remember { mutableStateOf(GazeOffset(0f, 0f)) }
    LaunchedEffect(effectiveGazeTarget) {
        delay(80)
        headGaze = gazeOff
    }
    val headX by animateFloatAsState(
        targetValue = headGaze.x * 8f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "floatHeadX",
    )
    val headY by animateFloatAsState(
        targetValue = headGaze.y * 5f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "floatHeadY",
    )
    val turnTilt by animateFloatAsState(
        targetValue = headGaze.x * 4f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "floatTurnTilt",
    )

    // —— 眨眼：闭合期眼盒压成扁椭圆（半闭过渡），保持期画粗 ∪ 形弯曲线 ——
    val eyeScale by animateFloatAsState(
        targetValue = if (blinkState.blinking) 0.35f else 1f,
        animationSpec = tween(85),
        label = "floatEyeScale",
    )
    // 闭合保持期同步点头 2dp（与 PetBlinkState.closedPhase 同步）。
    val density = LocalDensity.current
    val blinkNodPx by animateFloatAsState(
        targetValue = if (blinkState.closedPhase) with(density) { 2.dp.toPx() } else 0f,
        animationSpec = tween(85),
        label = "floatBlinkNod",
    )

    // —— 手动全身动作引擎（动作清单触发；全部绕底 / 旋转，不碰底部锚点）——
    // 幅度口径复用 AliveLogic 常量：伸懒腰 scaleY 1.05 / scaleX 0.97、
    // 歪头 7–10°、抖毛 ±1.5°×3；肩线峰值由 PullUpDock 侧按同事件协同。
    val tiltAnim = remember { Animatable(0f) }
    val stretchAnim = remember { Animatable(0f) }
    val shakeAnim = remember { Animatable(0f) }
    val nodAnim = remember { Animatable(0f) }
    val dipAnim = remember { Animatable(0f) }
    val swayAnim = remember { Animatable(0f) }
    val actionTick by PetRepository.actionTick.collectAsState()
    val actionKind by PetRepository.actionKind.collectAsState()
    LaunchedEffect(actionTick) {
        if (actionTick == 0) return@LaunchedEffect
        // 新动作打断旧动作：手动注视目标先清零，避免旧 delay 把新目标冲掉。
        manualPatrol = null
        manualTurn = null
        when (actionKind) {
            PetManualAction.BLINK -> blinkState.requestBlink(this)
            PetManualAction.PATROL -> {
                manualPatrol = patrolTargetPool().random()
                delay(1200)
                manualPatrol = null
            }
            PetManualAction.TURN -> {
                manualTurn = if (nextTurnLeft) GazeTarget.DOCK_LEFT else GazeTarget.DOCK_RIGHT
                nextTurnLeft = !nextTurnLeft
                delay(800) // 转头保持约 0.8 秒再回正
                manualTurn = null
            }
            PetManualAction.GAZE_HOLD -> PetRepository.gazeHold(GazeTarget.TODAY_CARD, 3000)
            PetManualAction.DEEP_BREATH -> {
                breathBoost.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                delay(1200)
                breathBoost.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
            }
            PetManualAction.SWAY -> {
                swayAnim.animateTo(
                    WeightShift.angleDeg(Random),
                    spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                )
                delay(WeightShift.holdMs(Random))
                swayAnim.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
            }
            PetManualAction.TILT -> {
                tiltAnim.animateTo(
                    FidgetPool.headTiltAngleDeg(Random),
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                )
                delay(FidgetPool.headTiltHoldMs(Random))
                tiltAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            PetManualAction.STRETCH, PetManualAction.HAPPY -> {
                stretchAnim.animateTo(1f, tween(FidgetPool.STRETCH_UP_MS, easing = FastOutSlowInEasing))
                delay(FidgetPool.STRETCH_HOLD_MS)
                stretchAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            PetManualAction.GREET, PetManualAction.SURPRISED -> {
                stretchAnim.animateTo(0.6f, tween(260, easing = FastOutSlowInEasing))
                delay(400)
                stretchAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            PetManualAction.SHAKE -> {
                repeat(FidgetPool.SHAKE_REPS) {
                    shakeAnim.animateTo(FidgetPool.SHAKE_AMP_DEG, tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing))
                    shakeAnim.animateTo(-FidgetPool.SHAKE_AMP_DEG, tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing))
                }
                shakeAnim.animateTo(0f, tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing))
            }
            PetManualAction.HEAD_SHAKE -> {
                repeat(2) {
                    shakeAnim.animateTo(6f, tween(120, easing = LinearEasing))
                    shakeAnim.animateTo(-6f, tween(120, easing = LinearEasing))
                }
                shakeAnim.animateTo(0f, tween(120, easing = LinearEasing))
            }
            PetManualAction.NOD -> {
                nodAnim.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                nodAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            PetManualAction.SHY, PetManualAction.PETTED -> {
                dipAnim.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                delay(400)
                dipAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            PetManualAction.DIZZY -> {
                repeat(2) {
                    swayAnim.animateTo(3f, tween(250, easing = LinearEasing))
                    swayAnim.animateTo(-3f, tween(250, easing = LinearEasing))
                }
                swayAnim.animateTo(0f, tween(250, easing = LinearEasing))
            }
            PetManualAction.DRAG_REACT -> {
                dipAnim.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                delay(300)
                dipAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                repeat(2) {
                    swayAnim.animateTo(3f, tween(250, easing = LinearEasing))
                    swayAnim.animateTo(-3f, tween(250, easing = LinearEasing))
                }
                swayAnim.animateTo(0f, tween(250, easing = LinearEasing))
            }
            else -> Unit
        }
    }

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
                // 与旧 DockPetPerch 一致：contentDescription + clickable，
                // TalkBack 即识别为可点按钮（本 BOM 无 Semantics role 属性）。
                contentDescription = "问曜仔"
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
        // 玻璃头本体：底层整图 + 眨眼覆盖层（闭合保持期画粗 ∪ 形弯曲线，
        // 半闭过渡期画眼带压缩条；scale=1 时与底图像素完全重合，平时不画，
        // 避免双重绘制的接缝）。
        // 全身动作合成（绕底原点，底部钉在肩峰上不脱离）：
        // 旋转 = 转头倾斜 + 歪头 + 抖动 + 重心微移；纵向 = 呼吸 × 拉伸 × 下压；
        // 横向 = 呼吸 × 拉伸收束 × 下压外扩；位移 = 注视 + 眨眼点头 + 点头/下压。
        val motionScaleY = (1f + 0.05f * stretchAnim.value) *
            (1f - 0.03f * dipAnim.value) * (1f - 0.02f * nodAnim.value)
        val motionScaleX = (1f - 0.03f * stretchAnim.value) *
            (1f + 0.02f * dipAnim.value)
        val motionDipPx = with(density) { 2.dp.toPx() } * (dipAnim.value + nodAnim.value)
        Box(
            modifier = Modifier
                .size(width = petWidth, height = petHeight)
                .graphicsLayer {
                    // 两段式：瞳孔先行（±10px/±6px，200ms）
                    // + 头部延迟跟上（±8px/±5px，80ms 延迟 300ms）+ 倾斜 4°。
                    // 注视位移不碰底部锚点：headY 上移至多约 2.3dp，
                    // 小于 5dp 压入量，连接不断；呼吸只缩放不位移。
                    translationX = headX + pupilX
                    translationY = headY + pupilY + blinkNodPx + motionDipPx
                    rotationZ = turnTilt + tiltAnim.value + shakeAnim.value + swayAnim.value
                    scaleX = popScale * breatheScaleX * motionScaleX
                    scaleY = popScale * breatheScaleY * motionScaleY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.glass_head),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (blinkState.closedPhase) {
                // 闭合保持期：两条粗 ∪ 形弯曲线（5px 线宽量级），不用细线压缩条。
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fit = minOf(size.width / 1043f, size.height / 1318f)
                    val dw = 1043f * fit
                    val dh = 1318f * fit
                    val left = (size.width - dw) / 2f
                    val top = (size.height - dh) / 2f
                    for (b in GLASS_EYE_BOXES) {
                        val bl = left + b.l * dw
                        val bt = top + b.t * dh
                        val br = left + b.r * dw
                        val bb = top + b.b * dh
                        val bw = br - bl
                        val bh = bb - bt
                        drawArc(
                            color = Color.Black.copy(alpha = 0.9f),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(bl + bw * 0.1f, bt + bh * 0.2f),
                            size = androidx.compose.ui.geometry.Size(bw * 0.8f, bh * 0.8f),
                            style = Stroke(width = with(density) { 2.5.dp.toPx() }),
                        )
                    }
                }
            } else if (eyeScale < 0.999f) {
                // 与 PetView 一致：直接取 ImageBitmap，不走 BitmapPainter。
                val blinkBmp = ImageBitmap.imageResource(R.drawable.glass_head)
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
