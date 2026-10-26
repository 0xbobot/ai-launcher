package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.BatteryState
import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.PetRepository.PetManualAction
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 漂浮玻璃头（曜仔）代码层：混合路线的常驻生命层。
 *
 * - 位置由调用方定（Dock 圆肩上方居中、压入肩峰 5dp）；本体等比例不变形。
 * - 原地呼吸（圆肩批）：AliveLogic 游走相位（基线 3500ms ±20%）
 *   + breatheWave 非对称波形，纵向 +1.5% / 横向 -0.7% 微缩放
 *   （绕底缩放，底部钉在肩峰上不脱离）；光晕随呼吸明暗。
 *   上下浮动已随圆肩连接取消——浮动等于每呼吸一次就脱离身体一次。
 * - 眨眼：与视频层共享 [PetBlinkState]（240ms：闭合 85ms → 保持 70ms
 *   + 2dp 点头 → 睁开 85ms；旧 140ms 细线作废）；eyes-alive 起改盖法——
 *   上眼睑从开孔上缘落下来盖住不变形的瞳孔，不再纵向压扁瞳孔、
 *   不再画 ∪ 形弯曲线；眨眼节律随状态变（专注 4–8s、闲时 2–6s）。
 * - 注视：PetView 同款导演（repo 目标 ＞ 瞥视 ＞ 巡看），眼睛先行、
 *   头部延迟跟上；eyes-alive 起瞳孔独立层：注视只动瞳孔层（开孔内
 *   ±5px/±3px，两段式扫视 120ms 冲 106% → 90ms 回落），头 ±8px/±5px
 *   延迟 80ms 跟上 + 向视线方向倾斜 4°；反光点层钉在右上光源方向，
 *   瞳孔动它不动（只随头动，不随瞳孔偏移）；低电量/后台时循环停、画面静止。
 * - 扫视：8–15 秒语义巡看锚点保留（看向房间），但过渡不再平滑漂移——
 *   全部改成扫视跳动；另加高频小扫视（0.3–1.2s 一次、±2–5px、120ms 到位、
 *   30% 伴眨），注视停留期间眼睛也不死。
 * - 瞳孔大小与眼睑：惊讶/开心/反应警觉瞳孔 ×1.15、专注/思考 ×0.92，
 *   200ms 平滑过渡；闲时上睑半阖 0.15（盖住珠体上缘 15%，不吞反光点），
 *   专注/惊讶/警觉全开，害羞深阖 0.30。
 * - 反应链：点按/瞥视/打招呼/惊讶时眼睛先反应（200ms 内睁大 + 瞳孔
 *   放大 + 注视过去，约 700ms 后衰减），头和身体后跟。
 * - 手动动作（动作清单触发）：[PetRepository.actionTick] 事件驱动的
 *   全身动作引擎——所有动作都走平移 / 旋转 / 绕底缩放 / 眼带 / 肩线
 *   峰值（PullUpDock 侧协同），头与面板始终是一个身体，不许部件分离。
 * - 全程无脚下阴影：光晕是头周围的径向晕，不画在 Dock 面上的投影。
 *
 * 眼睛分层（相对位图坐标，Python 离线检测 glass-cut.png 1043×1318，
 * 全部从原图派生、无 AI 重画）：
 * - 常驻底图 glass_head_nopupil：眼内框以面部米色修复（去瞳孔），
 *   棕圈完整保留；静止态三层合成与原图一致（仅羽化边缘 1–2px 差异）。
 * - 瞳孔层 glass_pupils：眼内框整颗珠体（暗核 + 下部玻璃渐变 + 反射弧，
 *   扣掉反光点），相对原图分毫不差；注视时在开孔椭圆内滑动、
 *   被开孔裁剪（滑出侧露底图米色、读作眼白，滑入侧压进圈下）。
 * - 反光点层 glass_catchlights：左 1 点、右 2 点（主点 + 卫星点），
 *   从原瞳孔干净分离；绘制时不跟瞳孔偏移。
 * 眼内框（去 6px 棕圈后的珠体开孔）：左 l=0.2119 t=0.6548 r=0.3356
 * b=0.7792；右 l=0.6654 t=0.6548 r=0.7862 b=0.7640。旧眼盒
 * （0.207/0.650/0.343/0.785、0.660/0.650/0.793/0.770）随压扁眨眼作废。
 */
/** 眼内框（相对位图坐标，Python 离线检测 glass-cut.png 1043×1318）。 */
internal val GLASS_EYE_OPENINGS = listOf(
    FRect(0.2119f, 0.6548f, 0.3356f, 0.7792f),
    FRect(0.6654f, 0.6548f, 0.7862f, 0.7640f),
)
/** 眼睑填充色：眼框上方取样的面部米色（左/右眼各一档，随右上光方向右眼更亮）。 */
internal val GLASS_FACE_TONES = listOf(
    Color(214, 194, 179),
    Color(223, 212, 205),
)
/** 眼睑边缘缝线色：棕圈代表色（扫描线实测 lum≈63、R−B≈30）。 */
internal val GLASS_RIM_BROWN = Color(79, 57, 49)

@Composable
fun FloatingPet(
    petWidth: Dp,
    blinkState: PetBlinkState,
    active: Boolean,
    onTap: () -> Unit,
    // 2026-10-26 入口子项：长按 = 表演清单（点按已还给“问”，见调用方）。
    // 默认空实现，HybridPet 转发 PullUpDock 的 onPetCellLongPress。
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
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

    // —— 眼睛状态源（瞳孔大小 / 眼睑 / 眨眼节律共用）：mood 与思考态 ——
    val mood by PetRepository.mood.collectAsState()
    val intentThinking by PetRepository.intentThinking.collectAsState()
    val eyeFocused = mood == PetMood.FETCH || intentThinking

    // —— 反应警觉：点按/瞥视/打招呼/惊讶时眼睛先反应（约 700ms 后衰减）——
    var eyeAlert by remember { mutableStateOf(false) }
    LaunchedEffect(eyeAlert) {
        if (eyeAlert) {
            delay(700)
            eyeAlert = false
        }
    }

    // —— 眨眼间隔循环（BlinkPolicy，低电量/后台停；专注态少眨 4–8s）——
    LaunchedEffect(active, lowPower, eyeFocused) {
        if (!active || lowPower) return@LaunchedEffect
        var next = BlinkPolicy.intervalMsForState(eyeFocused, Random)
        while (isActive) {
            delay(next)
            if (lowPower) break
            blinkState.requestBlink(this)
            next = BlinkPolicy.intervalMsForState(eyeFocused, Random)
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
            eyeAlert = true // 瞥视也是反应链：眼睛先睁大注视过去
            delay(600) // 瞥视至多 600ms，到期释放（新瞥视重启本 effect 刷新窗口）
            glanceTarget = null
        }
    }
    // 语义巡看锚点保留（看向房间），过渡不再平滑漂移——瞳孔以扫视跳动到位（见下）。
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
    // 瞳孔独立层：只动瞳孔层（开孔内 x ±5px、y ±3px），两段式扫视——
    // 120ms 冲到 106% → 90ms 回落到目标（PetView 同款扫视口径）；
    // 中途改目标从当前位置重新起跳，不回落再跳。
    val pupilBaseX = remember { Animatable(0f) }
    val pupilBaseY = remember { Animatable(0f) }
    LaunchedEffect(effectiveGazeTarget) {
        val tx = gazeOff.x * 5f
        val ty = gazeOff.y * 3f
        if (pupilBaseX.value == tx && pupilBaseY.value == ty) return@LaunchedEffect
        launch {
            pupilBaseX.animateTo(
                pupilBaseX.value + (tx - pupilBaseX.value) * SACCADE_OVERSHOOT,
                tween(SACCADE_OUT_MS, easing = FastOutLinearInEasing)
            )
            pupilBaseX.animateTo(tx, tween(SACCADE_SETTLE_MS, easing = FastOutSlowInEasing))
        }
        launch {
            pupilBaseY.animateTo(
                pupilBaseY.value + (ty - pupilBaseY.value) * SACCADE_OVERSHOOT,
                tween(SACCADE_OUT_MS, easing = FastOutLinearInEasing)
            )
            pupilBaseY.animateTo(ty, tween(SACCADE_SETTLE_MS, easing = FastOutSlowInEasing))
        }
    }
    // 高频小扫视：0.3–1.2s 一次快速小跳（120ms 到位）+ 随机停顿，
    // 叠加在注视基点上；30% 概率伴眨；低电量/后台停、画面静止。
    var jitterX by remember { mutableFloatStateOf(0f) }
    var jitterY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionActive) {
        if (!motionActive) {
            jitterX = 0f
            jitterY = 0f
            return@LaunchedEffect
        }
        val r = Random
        while (isActive) {
            delay(SaccadePolicy.intervalMs(r))
            jitterX = SaccadePolicy.offsetX(r)
            jitterY = SaccadePolicy.offsetY(r)
            if (BlinkPolicy.isSaccadeBlink(r)) blinkState.requestBlink(this)
        }
    }
    val jitterXAnim by animateFloatAsState(
        targetValue = jitterX,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "floatJitterX",
    )
    val jitterYAnim by animateFloatAsState(
        targetValue = jitterY,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "floatJitterY",
    )
    // 头部延迟 80ms 跟随：300ms 到位（x ±8px，y ±5px）+ 向视线方向倾斜 4°；
    // 头只跟注视目标，不跟小扫视抖动——眼睛先行、头延迟跟上。
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

    // —— 眼睛状态：瞳孔大小与眼睑开合（200ms 平滑过渡，不跳变）——
    val pupilScale by animateFloatAsState(
        targetValue = eyePupilScale(mood, intentThinking, eyeAlert),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatPupilScale",
    )
    val baseLid by animateFloatAsState(
        targetValue = eyeLidFraction(mood, intentThinking, eyeAlert),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "floatBaseLid",
    )
    // —— 眨眼改盖法：上睑 85ms 落下 → 保持 70ms → 85ms 抬起；
    // blinkLid 与 PetBlinkState 全程同步（blinking 覆盖闭合+保持+睁开），
    // 最终眼睑取最大值——眨眼全闭压过闲时半阖，睁开后回到状态眼睑。
    val blinkLid by animateFloatAsState(
        targetValue = if (blinkState.blinking) 1f else 0f,
        animationSpec = tween(85),
        label = "floatBlinkLid",
    )
    val lidFraction = maxOf(baseLid, blinkLid)
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
                eyeAlert = true // 反应链：眼睛先睁大注视，身体拉伸后跟
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

    // —— Dock 宠物拖拽（2026-10-26 入口子项，与输入态 PetView 同口径）：
    // 40dp 半径内跟手；提起时 SURPRISED + say("借过借过~")（走现有 say()
    // 路径；文本可见性依赖气泡子项复活 SpeechBubble 渲染面，本子项不新建
    // 渲染面）；松手弹簧弹回 + onDragEnd 给 DIZZY 1s。拖拽不走 actionTick，
    // 故不触发 PullUpDock 的肩线脉冲（与输入态拖拽一致）；拖拽是整只提起，
    // 松手回到底锚，不破坏头 + 面板 = 一个身体（提起期间底锚临时脱离
    // 是拖拽本身的语义，弹回即复位）。
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // 长按已触发的同一手势内不再跟手（长按消费了按下，拖拽本应被取消，
    // 此旗标是第二道保险；新一手势的 onDragStart 会复位它）。
    var longPressFired by remember { mutableStateOf(false) }
    val dragMaxPx = with(density) { 40.dp.toPx() }
    val dragX by animateFloatAsState(
        targetValue = dragOffset.x,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "floatDragX",
    )
    val dragY by animateFloatAsState(
        targetValue = dragOffset.y,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "floatDragY",
    )

    Box(
        modifier = modifier
            .size(width = touch, height = maxOf(petHeight, 48.dp))
            // 拖拽放外层（先于 combinedClickable，PetView 同款写法）：
            // 未超 slop 前不消费，点按 / 长按仍能触发；超 slop 后消费位移，
            // 点按 / 长按即取消——三手势同一节点不打架。
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        longPressFired = false
                        PetRepository.onDragStart()
                        PetRepository.say("借过借过~")
                    },
                    onDrag = { change, dragAmount ->
                        if (longPressFired) return@detectDragGestures
                        change.consume()
                        val newX = dragOffset.x + dragAmount.x
                        val newY = dragOffset.y + dragAmount.y
                        val dist = sqrt(newX * newX + newY * newY)
                        dragOffset = if (dist > dragMaxPx) {
                            Offset(
                                newX / dist * dragMaxPx,
                                newY / dist * dragMaxPx
                            )
                        } else {
                            Offset(newX, newY)
                        }
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        PetRepository.onDragEnd()
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        PetRepository.onDragEnd()
                    }
                )
            }
            .semantics {
                // 与旧 DockPetPerch 一致：contentDescription + 可点语义，
                // TalkBack 即识别为可点按钮。点按 = 问（2026-10-26 起说真话，
                // 不再是开清单）；长按动作经 onLongClickLabel 提示。
                contentDescription = "问曜仔"
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClickLabel = "长按看动作",
                onClick = {
                    pressed = true
                    eyeAlert = true // 反应链：点按当下眼睛先睁大，输入后跟
                    onTap()
                },
                onLongClick = {
                    longPressFired = true
                    eyeAlert = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
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
        // 玻璃头本体：去瞳孔底图 + 眼睛三层（瞳孔 / 反光点 / 眼睑）。
        // 静止态三层合成与原图一致（仅羽化边缘 1–2px 差异，显示约亚像素）。
        // 全身动作合成（绕底原点，底部钉在肩峰上不脱离）：
        // 旋转 = 转头倾斜 + 歪头 + 抖动 + 重心微移；纵向 = 呼吸 × 拉伸 × 下压；
        // 横向 = 呼吸 × 拉伸收束 × 下压外扩；位移 = 头部跟随 + 眨眼点头 + 点头/下压
        // （瞳孔偏移只发生在眼睛 Canvas 内，不动整头）。
        val motionScaleY = (1f + 0.05f * stretchAnim.value) *
            (1f - 0.03f * dipAnim.value) * (1f - 0.02f * nodAnim.value)
        val motionScaleX = (1f - 0.03f * stretchAnim.value) *
            (1f + 0.02f * dipAnim.value)
        val motionDipPx = with(density) { 2.dp.toPx() } * (dipAnim.value + nodAnim.value)
        Box(
            modifier = Modifier
                .size(width = petWidth, height = petHeight)
                .graphicsLayer {
                    // 头部延迟跟上（±8px/±5px，80ms 延迟 300ms）+ 倾斜 4°。
                    // 注视位移不碰底部锚点：headY 上移至多约 2.3dp，
                    // 小于 5dp 压入量，连接不断；呼吸只缩放不位移。
                    // 拖拽位移叠加其上：整只跟手（提起语义），松手弹簧回零。
                    translationX = headX + dragX
                    translationY = headY + blinkNodPx + motionDipPx + dragY
                    rotationZ = turnTilt + tiltAnim.value + shakeAnim.value + swayAnim.value
                    scaleX = popScale * breatheScaleX * motionScaleX
                    scaleY = popScale * breatheScaleY * motionScaleY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.glass_head_nopupil),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            // 眼睛三层：与底图同盒同 Fit 放置数学，相对坐标严格对齐。
            // 瞳孔层（开孔裁剪 + 注视偏移 + 绕珠心缩放）→ 反光点层
            // （整图不偏移，钉在右上光源方向）→ 眼睑层（最后画，落下时
            // 盖住瞳孔与反光点；全开时不画）。
            val pupilsBmp = ImageBitmap.imageResource(R.drawable.glass_pupils)
            val catchBmp = ImageBitmap.imageResource(R.drawable.glass_catchlights)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 盒子与位图同比，实际无留白；仍显式算 Fit 以防盒子比例漂移。
                val fit = minOf(size.width / 1043f, size.height / 1318f)
                val dw = 1043f * fit
                val dh = 1318f * fit
                val left = (size.width - dw) / 2f
                val top = (size.height - dh) / 2f
                val pupilDx = pupilBaseX.value + jitterXAnim
                val pupilDy = pupilBaseY.value + jitterYAnim
                // 第一遍：瞳孔层（开孔裁剪 + 注视偏移 + 绕珠心缩放）。
                for (b in GLASS_EYE_OPENINGS) {
                    val bl = left + b.l * dw
                    val bt = top + b.t * dh
                    val br = left + b.r * dw
                    val bb = top + b.b * dh
                    val bw = br - bl
                    val bh = bb - bt
                    val cx = (bl + br) / 2f
                    val cy = (bt + bb) / 2f
                    // 瞳孔：绕珠心缩放 + 注视偏移，输出被开孔椭圆裁剪——
                    // 滑出侧露底图米色（读作眼白），滑入侧压进圈下。
                    val eyeClip = Path().apply {
                        addOval(Rect(bl, bt, br, bb))
                    }
                    clipPath(eyeClip) {
                        val sw = bw * pupilScale
                        val sh = bh * pupilScale
                        drawImage(
                            image = pupilsBmp,
                            srcOffset = androidx.compose.ui.unit.IntOffset(
                                (b.l * pupilsBmp.width).toInt(),
                                (b.t * pupilsBmp.height).toInt(),
                            ),
                            srcSize = androidx.compose.ui.unit.IntSize(
                                ((b.r - b.l) * pupilsBmp.width).toInt().coerceAtLeast(2),
                                ((b.b - b.t) * pupilsBmp.height).toInt().coerceAtLeast(2),
                            ),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (cx - sw / 2f + pupilDx).toInt(),
                                (cy - sh / 2f + pupilDy).toInt(),
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                sw.toInt().coerceAtLeast(2),
                                sh.toInt().coerceAtLeast(2),
                            ),
                        )
                    }
                }
                // 反光点：整图不偏移绘制——瞳孔动它不动，只随头动。
                // 必须画在瞳孔之后、眼睑之前：眼睑层最后画，落下时
                // 盖住瞳孔与反光点（G2；原来反光点画在眼睑之后，
                // 闭眼时亮点浮在眼皮上）。反光点仍画在瞳孔之后——
                // 瞳孔贴片扣掉了反光点位置，顺序反了会错位遮挡。
                drawImage(
                    image = catchBmp,
                    srcOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                    srcSize = androidx.compose.ui.unit.IntSize(catchBmp.width, catchBmp.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                )
                // 眼睑：从开孔上缘落下的拱形盖（中央最深、两侧收起），
                // 面部米色实心填充 + 棕圈色睑缘缝线；最后画。
                // 几何与瞳孔遍完全一致（开孔坐标系同一套）。
                for ((index, b) in GLASS_EYE_OPENINGS.withIndex()) {
                    val bl = left + b.l * dw
                    val bt = top + b.t * dh
                    val br = left + b.r * dw
                    val bb = top + b.b * dh
                    val bh = bb - bt
                    val cx = (bl + br) / 2f
                    if (lidFraction > 0.001f) {
                        val lidH = bh * lidFraction
                        val lidPath = Path().apply {
                            moveTo(bl, bt)
                            lineTo(br, bt)
                            lineTo(br, bt + lidH * 0.55f)
                            quadraticTo(cx, bt + lidH * 1.3f, bl, bt + lidH * 0.55f)
                            close()
                        }
                        drawPath(lidPath, color = GLASS_FACE_TONES[index])
                        val seamPath = Path().apply {
                            moveTo(br, bt + lidH * 0.55f)
                            quadraticTo(cx, bt + lidH * 1.3f, bl, bt + lidH * 0.55f)
                        }
                        drawPath(
                            seamPath,
                            color = GLASS_RIM_BROWN.copy(alpha = 0.6f),
                            style = Stroke(width = with(density) { 1.2.dp.toPx() }),
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
