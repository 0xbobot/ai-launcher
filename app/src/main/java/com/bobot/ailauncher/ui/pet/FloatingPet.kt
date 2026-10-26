package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
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
 * - 接触影（活感第一批新增，取代旧“无脚下阴影”口径）：头下方一枚
 *   62.2×10dp、黑 14% 的柔边椭圆（按宠物宽相对 86.4dp 参考宽缩放），
 *   由头状态驱动——x 跟头走，头抬高变小变淡、头压低变宽变深、
 *   下压过冲再加宽加深一档；光晕仍是头周围的径向晕（不是脚下阴影）。
 * - 活感第一批（2026-10-26，Bob 全部接受）：头弹簧（整颗头作颈上弹簧
 *   质量，k≈25/s²、阻尼比 0.3、可见 2–3 次过冲，复用头 offset／倾角通道）；
 *   注视跟随触点（头转强度＝触点位移 × 0.35，瞳孔复用眼睛引擎、
 *   瞳孔 clamp、注视停顿门、眨眼中不新起扫视）；眨眼多样化（正常／双眨／
 *   半眨／慢半眨）；待机小行为（20–60s 随机歪头 4°／环顾，交互立即取消）；
 *   自主重心转移（25–50s 换一只脚承重 ±2.5°，复用 WeightShift 口径）；
 *   不做纯缩放呼吸。注视优先级：repo ＞ 瞥视 ＞ 手动 ＞ 触点 ＞ 待机 ＞ 巡看。
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

// combinedClickable 在本仓 Compose BOM（2024.10.01）下仍是实验 API，
// 调用方 PullUpDock / AppRow 同款做法：函数级 OptIn。
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingPet(
    petWidth: Dp,
    blinkState: PetBlinkState,
    active: Boolean,
    onTap: () -> Unit,
    // 2026-10-26 入口子项：长按 = 表演清单（点按已还给“问”，见调用方）。
    // 默认空实现，HybridPet 转发 PullUpDock 的 onPetCellLongPress。
    onLongPress: () -> Unit = {},
    // 拖拽一体拉伸状态机（2026-10-26 定稿）：非空时走 Dock 一体拉伸
    // 路径（头升/肩滞后/脖子连接带/弹簧回弹）；空时走旧 head-only 跟手
    // （意图路径保持现状一字不动）。由 PullUpDock remember 后传入。
    dragBody: DragBodyState? = null,
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

    // 触点交互源（活感第一批）：combinedClickable 的按压位置由此取，
    // 供注视跟随触点；源体提前声明，收集 effect 在后（拖拽段之后）。
    val petInteractionSource = remember { MutableInteractionSource() }

    // —— 注视导演（PetView 同款优先级，Dock 宠物不呈现输入态故无倾听层）：
    // repo 目标 ＞ 瞥视（600ms 持有）＞ 巡看（8–15 秒一次，停留 1.2 秒）——
    // 活感第一批在其下加两层：触点（按压／拖拽位置）＞ 待机（偶发环顾）。
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
    // 活感第一批注视层：触点（按压／拖拽位置，收集 effect 在拖拽段之后）与
    // 待机环顾（待机循环在拖拽段之后）；两者只在无更高目标时生效。
    var touchGaze by remember { mutableStateOf<GazeOffset?>(null) }
    var idleGazeTarget by remember { mutableStateOf<GazeTarget?>(null) }
    val effectiveGazeTarget = when {
        repoGazeTarget != GazeTarget.CENTER -> repoGazeTarget
        glanceTarget != null -> glanceTarget!!
        manualTurn != null -> manualTurn!!
        manualPatrol != null -> manualPatrol!!
        else -> patrolTarget
    }
    val higherGazeActive = repoGazeTarget != GazeTarget.CENTER ||
        glanceTarget != null || manualTurn != null || manualPatrol != null
    val gazeOff = when {
        higherGazeActive -> gazeOffsetFor(effectiveGazeTarget)
        touchGaze != null -> touchGaze!!
        idleGazeTarget != null -> gazeOffsetFor(idleGazeTarget!!)
        else -> gazeOffsetFor(effectiveGazeTarget)
    }
    // 瞳孔独立层：只动瞳孔层（开孔内 x ±5px、y ±3px），两段式扫视——
    // 120ms 冲到 106% → 90ms 回落到目标（PetView 同款扫视口径）；
    // 中途改目标从当前位置重新起跳，不回落再跳。
    // 活感第一批：眨眼互斥——眨眼中不新起扫视，待闭眼结束再跳
    // （扫视藏在眨眼后是真眼行为；眨眼中途改目标，effect 重启后仍等眨眼结束）。
    val pupilBaseX = remember { Animatable(0f) }
    val pupilBaseY = remember { Animatable(0f) }
    LaunchedEffect(gazeOff) {
        val tx = gazeOff.x * 5f
        val ty = gazeOff.y * 3f
        if (pupilBaseX.value == tx && pupilBaseY.value == ty) return@LaunchedEffect
        while (blinkState.blinking) delay(16)
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
    // 头部延迟 80ms 跟随（x ±8px，y ±5px）+ 向视线方向倾斜 4°；
    // 头只跟注视目标，不跟小扫视抖动——眼睛先行、头延迟跟上。
    // 活感第一批头弹簧：跟随不再 300ms tween 到位，改走颈上弹簧
    // （k≈25/s²、阻尼比 0.3，可见 2–3 次过冲；倾角同弹簧，稳态仍为 4°）。
    var headGaze by remember { mutableStateOf(GazeOffset(0f, 0f)) }
    LaunchedEffect(gazeOff) {
        delay(80)
        headGaze = gazeOff
    }
    val headX by animateFloatAsState(
        targetValue = headGaze.x * 8f,
        animationSpec = spring(
            dampingRatio = HeadSpring.DAMPING_RATIO,
            stiffness = HeadSpring.STIFFNESS,
        ),
        label = "floatHeadX",
    )
    val headY by animateFloatAsState(
        targetValue = headGaze.y * 5f,
        animationSpec = spring(
            dampingRatio = HeadSpring.DAMPING_RATIO,
            stiffness = HeadSpring.STIFFNESS,
        ),
        label = "floatHeadY",
    )
    val turnTilt by animateFloatAsState(
        targetValue = headGaze.x * 4f,
        animationSpec = spring(
            dampingRatio = HeadSpring.DAMPING_RATIO,
            stiffness = HeadSpring.STIFFNESS,
        ),
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
    // 活感第一批多样化：半眨时 blinkDepth 为 0.45（眼睑只落一半，不点头）。
    val blinkLid by animateFloatAsState(
        targetValue = if (blinkState.blinking) blinkState.blinkDepth else 0f,
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
    // 活感第一批门：手动动作进行中时不新起待机小行为／自主重心转移
    // （finally 复位：effect 被新动作取消时也不粘住）。
    var manualBusy by remember { mutableStateOf(false) }
    LaunchedEffect(actionTick) {
        if (actionTick == 0) return@LaunchedEffect
        manualBusy = true
        try {
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
        } finally {
            manualBusy = false
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
    // Dock 一体拉伸路径的原始行程（px，不钳制向量）：上拉走 tanh 阻力
    // 曲线、横向/下压走 40dp 硬钳制，换算在 onDrag 里做。意图路径不用它。
    var dragTravelPx by remember { mutableStateOf(Offset.Zero) }
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

    // 拖拽一体拉伸：肩滞后追踪（仅 Dock 路径，dragBody 非空时生效）。
    // 肩升 = 0.55 × 头升、肩横移 = 0.5625 × 头横移（= 0.45 × 手指），
    // 均经 critically damped 弹簧（stiffness 550，约 90ms 达 63%）滞后——
    // 头先行、肩延迟跟上；collectLatest 使新头量到达时取消旧追踪、立即
    // 改向；松手过冲时头量逐帧回落（含负值下压），肩跟着同一弹簧走、
    // 同样带过冲下压，连接处不焊死。
    if (dragBody != null) {
        LaunchedEffect(dragBody) {
            snapshotFlow { dragBody.headRisePx }.collectLatest { rise ->
                animate(
                    dragBody.shoulderRisePx,
                    DragBodyLag.SHOULDER_RISE_RATIO * rise,
                    animationSpec = dragShoulderLagSpring(),
                ) { v, _ -> dragBody.shoulderRisePx = v }
            }
        }
        LaunchedEffect(dragBody) {
            snapshotFlow { dragBody.headXPx }.collectLatest { hx ->
                animate(
                    dragBody.shoulderXPx,
                    DragBodyLag.SHOULDER_X_RATIO * hx,
                    animationSpec = dragShoulderLagSpring(),
                ) { v, _ -> dragBody.shoulderXPx = v }
            }
        }
    }
    // —— 活感第一批：待机小行为／自主重心转移／触点注视（状态驱动层）——
    // 全部状态驱动（animateFloatAsState／目标状态），不直接操作 Animatable：
    // 交互取消时只需复位目标，动画弹簧回正，不存在协程取消杀死循环的问题。
    var idleTiltTarget by remember { mutableFloatStateOf(0f) }
    var weightShiftTarget by remember { mutableFloatStateOf(0f) }
    var idleGen by remember { mutableStateOf(0) }
    val idleTilt by animateFloatAsState(
        targetValue = idleTiltTarget,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "idleTilt",
    )
    val weightShift by animateFloatAsState(
        targetValue = weightShiftTarget,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "weightShift",
    )
    // 互斥：任何交互开始（点按按下／拖拽／新手动动作）立即取消待机与重心转移。
    LaunchedEffect(pressed, dragOffset, actionTick) {
        if (pressed || dragOffset != Offset.Zero || actionTick > 0) {
            idleGen++
            idleTiltTarget = 0f
            idleGazeTarget = null
            weightShiftTarget = 0f
        }
    }
    // 低电量／后台／减动效时循环退出：进行中的待机／重心／触点目标一律复位，
    // 不许循环在 delay 中被取消后把目标粘在半路上（与高频抖动的复位同款）。
    LaunchedEffect(motionActive) {
        if (!motionActive) {
            idleTiltTarget = 0f
            idleGazeTarget = null
            weightShiftTarget = 0f
            touchGaze = null
        }
    }
    // ④ 待机小行为：20–60s 随机，歪头 4° 或环顾；与重心转移互斥；
    // 交互中途开始由上 effect 复位，行为后 delay 的复位凭代次校验（已取消则不复写）。
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        while (isActive) {
            delay(IdleBehavior.intervalMs(Random))
            if (!motionActive) break
            if (pressed || dragOffset != Offset.Zero || manualBusy) continue
            if (weightShiftTarget != 0f) continue
            val gen = idleGen
            when (IdleBehavior.pick(Random)) {
                IdleKind.TILT -> {
                    idleTiltTarget = if (Random.nextBoolean()) IdleBehavior.TILT_ANGLE_DEG else -IdleBehavior.TILT_ANGLE_DEG
                    delay(IdleBehavior.tiltHoldMs(Random))
                    if (idleGen == gen) idleTiltTarget = 0f
                }
                IdleKind.LOOK -> {
                    idleGazeTarget = patrolTargetPool().random()
                    delay(IdleBehavior.LOOK_HOLD_MS)
                    if (idleGen == gen) idleGazeTarget = null
                }
            }
        }
    }
    // ⑤ 自主重心转移：复用 WeightShift 口径（25–50s、±2.5°、持有 2–4s）；
    // 与待机小行为互斥；交互取消由上 effect 复位。
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        while (isActive) {
            delay(WeightShift.intervalMs(Random))
            if (!motionActive) break
            if (pressed || dragOffset != Offset.Zero || manualBusy) continue
            if (idleTiltTarget != 0f || idleGazeTarget != null) continue
            weightShiftTarget = WeightShift.angleDeg(Random)
            delay(WeightShift.holdMs(Random))
            weightShiftTarget = 0f
        }
    }
    // ③ 注视跟随触点：按压位置（PressInteraction）与拖拽位移经停顿门后
    // 写入 touchGaze；松手／取消后 600ms dwell 再释放回底层目标。
    // 释放 dwell 由收集 effect 内的 releaseJob 拥有：新按压先取消旧 dwell，
    // 拖拽结束的 Cancel 事件同样走释放 dwell（双保险，不另起 effect）。
    var touchAcceptedDx by remember { mutableFloatStateOf(0f) }
    var touchAcceptedDy by remember { mutableFloatStateOf(0f) }
    var touchAcceptedMs by remember { mutableStateOf(0L) }
    // 头心（外层触摸盒坐标）：头盒底对齐、横向居中。
    val boxWPx = with(density) { touch.toPx() }
    val boxHPx = with(density) { maxOf(petHeight, 48.dp).toPx() }
    val headCenterXpx = boxWPx / 2f
    val headCenterYpx = boxHPx - with(density) { petHeight.toPx() } / 2f
    LaunchedEffect(petInteractionSource) {
        var releaseJob: Job? = null
        petInteractionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    releaseJob?.cancel()
                    releaseJob = null
                    val dx = interaction.pressPosition.x - headCenterXpx
                    val dy = interaction.pressPosition.y - headCenterYpx
                    val now = System.currentTimeMillis()
                    if (TouchGaze.shouldUpdate(touchAcceptedDx, touchAcceptedDy, dx, dy, touchAcceptedMs, now)) {
                        touchGaze = TouchGaze.offsetFor(dx, dy)
                        touchAcceptedDx = dx
                        touchAcceptedDy = dy
                        touchAcceptedMs = now
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    releaseJob?.cancel()
                    releaseJob = launch {
                        delay(TouchGaze.RELEASE_HOLD_MS)
                        touchGaze = null
                    }
                }
            }
        }
    }
    // 拖拽位移同样喂给触点注视（方向即手指方向；停顿门防连续小位移抽搐）。
    // dragOffset 归零即松手：释放 dwell 由交互源的 Cancel 事件接管，此处不另起。
    LaunchedEffect(dragOffset) {
        if (dragOffset == Offset.Zero) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (TouchGaze.shouldUpdate(touchAcceptedDx, touchAcceptedDy, dragOffset.x, dragOffset.y, touchAcceptedMs, now)) {
            touchGaze = TouchGaze.offsetFor(dragOffset.x, dragOffset.y)
            touchAcceptedDx = dragOffset.x
            touchAcceptedDy = dragOffset.y
            touchAcceptedMs = now
        }
    }

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
                        dragBody?.onDragStart()
                        dragTravelPx = Offset.Zero
                        PetRepository.onDragStart()
                        PetRepository.say("借过借过~")
                    },
                    onDrag = { change, dragAmount ->
                        if (longPressFired) return@detectDragGestures
                        change.consume()
                        if (dragBody != null) {
                            // Dock 一体拉伸路径：累计原始行程（不钳制向量），
                            // 上拉走 tanh 阻力曲线、横向/下压走 40dp 硬钳制。
                            dragTravelPx += dragAmount
                            val upDp = with(density) {
                                maxOf(0f, -dragTravelPx.y).toDp().value
                            }
                            val latDp = with(density) { dragTravelPx.x.toDp().value }
                            val downDp = with(density) {
                                maxOf(0f, dragTravelPx.y).toDp().value
                            }
                            dragBody.onDragMove(
                                headRiseTargetPx = with(density) {
                                    dragHeadRiseDp(upDp).dp.toPx()
                                },
                                headXTargetPx = with(density) {
                                    dragHeadXDps(latDp).dp.toPx()
                                },
                                headSinkTargetPx = with(density) {
                                    minOf(downDp, 40f).dp.toPx()
                                },
                            )
                        } else {
                            // 意图路径：旧行为一字不动（40dp 半径内跟手）。
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
                        }
                    },
                    onDragEnd = {
                        if (dragBody != null) {
                            dragBody.onDragEnd(
                                with(density) { dragBody.headRisePx.toDp().value }
                            )
                        } else {
                            dragOffset = Offset.Zero
                        }
                        PetRepository.onDragEnd()
                    },
                    onDragCancel = {
                        if (dragBody != null) {
                            dragBody.onDragEnd(
                                with(density) { dragBody.headRisePx.toDp().value }
                            )
                        } else {
                            dragOffset = Offset.Zero
                        }
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
                interactionSource = petInteractionSource,
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
        // 拖拽一体拉伸：脖子连接带（仅 Dock 路径）。头底与肩峰之间、
        // 上窄下宽、随拉伸收窄；面板材质（Card 色）向上延伸，读作身体
        // 的一部分而非贴纸。几何（pet Box 坐标，Y 向下）：
        // 头底 Y = petHeight − headRise + headSink；肩峰 Y = petHeight −
        // press(5dp) − shoulderRise；可见间隙 = neckGap − press − sink；
        // 间隙 > 0 才画（≤ 0 时头已压进肩峰、连接自带）；盒子两端
        // 各伸 2px 塞缝（上端被后画的头盒盖住、下端盖住肩峰顶）。
        // 画在光晕之后、头盒之前：面板先画、脖子次之、头最后——前面图层。
        if (dragBody != null) {
            val neckGapDp = dragNeckGapDp(
                with(density) { dragBody.headRisePx.toDp().value }
            )
            val sinkDp = with(density) { dragBody.headSinkPx.toDp().value }
            val visibleGapDp = neckGapDp - 5f - sinkDp
            if (visibleGapDp > 0f) {
                val shoulderRiseDp = with(density) {
                    dragBody.shoulderRisePx.toDp().value
                }
                val pxToDp = 1f / density.density
                val neckHeightDp = visibleGapDp + 4f * pxToDp
                val neckOffsetYDp = -(shoulderRiseDp + 5f - 2f * pxToDp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = petWidth, height = neckHeightDp.dp)
                        .offset(y = neckOffsetYDp.dp),
                ) {
                    val cardColor = AILauncherColors.current.Card.copy(alpha = 0.92f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val narrow = 1f - 0.15f * (neckGapDp / 14.4f).coerceIn(0f, 1f)
                        val topW = w * 0.55f * narrow
                        val botW = w * 0.80f * narrow
                        val topCx = w / 2f + dragBody.headXPx
                        val botCx = w / 2f + dragBody.shoulderXPx
                        val neckPath = Path().apply {
                            moveTo(topCx - topW / 2f, 0f)
                            lineTo(topCx + topW / 2f, 0f)
                            quadraticTo(
                                (topCx + topW / 2f + botCx + botW / 2f) / 2f + h * 0.12f,
                                h / 2f,
                                botCx + botW / 2f,
                                h,
                            )
                            lineTo(botCx - botW / 2f, h)
                            quadraticTo(
                                (topCx - topW / 2f + botCx - botW / 2f) / 2f - h * 0.12f,
                                h / 2f,
                                topCx - topW / 2f,
                                0f,
                            )
                            close()
                        }
                        drawPath(neckPath, color = cardColor)
                    }
                }
            }
        }
        // 玻璃头本体：去瞳孔底图 + 眼睛三层（瞳孔 / 反光点 / 眼睑）。
        // 静止态三层合成与原图一致（仅羽化边缘 1–2px 差异，显示约亚像素）。
        // 全身动作合成（绕底原点，底部钉在肩峰上不脱离）：
        // 旋转 = 转头倾斜 + 歪头 + 抖动 + 重心微移 + 待机歪头 + 自主重心转移；
        // 纵向 = 呼吸 × 拉伸 × 下压；横向 = 呼吸 × 拉伸收束 × 下压外扩；
        // 位移 = 头部跟随 + 眨眼点头 + 点头/下压
        // （瞳孔偏移只发生在眼睛 Canvas 内，不动整头）。
        val motionScaleY = (1f + 0.05f * stretchAnim.value) *
            (1f - 0.03f * dipAnim.value) * (1f - 0.02f * nodAnim.value)
        val motionScaleX = (1f - 0.03f * stretchAnim.value) *
            (1f + 0.02f * dipAnim.value)
        val motionDipPx = with(density) { 2.dp.toPx() } * (dipAnim.value + nodAnim.value)
        // ② 接触影（活感第一批新增；生产此前并无此层，评估称“已存在”
        // 实为 mock 影）：头下方柔边椭圆，由头状态驱动——x 跟头走，
        // 头抬高变小变淡、头压低变宽变深、下压过冲再加宽加深一档。
        // 画在头盒之前（头压住影上沿），影心在盒底之下约 1/4 影高处：
        // 宠物底压入肩峰 5dp，影落点仍在面板面上；纵使上层 clip 盒底，
        // 盒内 3/4 仍可见为下巴下的一抹软影，不穿帮。
        // 头盒位移（与下方 graphicsLayer 同一套）：Dock 路径取 DragBodyState，
        // 意图路径取旧 dragX/dragY；接触影 x 跟头走，故此处与头盒共用同一量，
        // Dock 拖拽时 dragOffset 恒为零、不可沿用 dragX/dragY。
        val dragHeadX = dragBody?.headXPx ?: dragX
        val dragHeadY = dragBody?.let { -it.headRisePx + it.headSinkPx } ?: dragY
        val shadowHeadTxPx = headX + dragHeadX
        val shadowHeadTyPx = headY + blinkNodPx + motionDipPx + dragHeadY
        val shadowParams = ContactShadow.params(
            liftPx = -shadowHeadTyPx,
            squash01 = dipAnim.value,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseW = petWidth.toPx() * (ContactShadow.BASE_WIDTH_DP / ContactShadow.REF_PET_WIDTH_DP)
            val baseH = petWidth.toPx() * (ContactShadow.BASE_HEIGHT_DP / ContactShadow.REF_PET_WIDTH_DP)
            val w = baseW * shadowParams.widthScale
            val h = baseH * shadowParams.widthScale
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = shadowParams.alpha),
                        Color.Black.copy(alpha = 0f),
                    ),
                    center = Offset(
                        size.width / 2f + shadowHeadTxPx,
                        size.height + h * 0.25f,
                    ),
                    radius = w / 2f,
                ),
                topLeft = Offset(
                    size.width / 2f + shadowHeadTxPx - w / 2f,
                    size.height + h * 0.25f - h / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(w, h),
            )
        }
        Box(
            modifier = Modifier
                .size(width = petWidth, height = petHeight)
                .graphicsLayer {
                    // 头部延迟跟上（±8px/±5px，80ms 延迟 + 颈上弹簧）+ 倾斜 4°。
                    // 注视位移不碰底部锚点：headY 上移至多约 2.3dp，
                    // 小于 5dp 压入量，连接不断；呼吸只缩放不位移。
                    // 拖拽位移叠加其上：Dock 路径走一体拉伸（头升/横移/下沉
                    // 来自 DragBodyState，倾角取肩横移，形变取头升），松手
                    // 弹簧回零且带过冲压扁；意图路径走旧 dragX/dragY 一字不动。
                    // 活感第一批叠加其上：待机歪头与自主重心转移同为绕底旋转。
                    // dragHeadX/dragHeadY 已在接触影处与头盒共用定义，此处不重定义。
                    val dragTilt = dragBody?.let {
                        dragTiltDeg(with(density) { it.shoulderXPx.toDp().value })
                    } ?: 0f
                    val dragScale = dragBody?.let {
                        dragStretchScale(
                            with(density) { it.headRisePx.toDp().value },
                            it.releaseStartRiseDp,
                        )
                    } ?: (1f to 1f)
                    translationX = headX + dragHeadX
                    translationY = headY + blinkNodPx + motionDipPx + dragHeadY
                    rotationZ = turnTilt + tiltAnim.value + shakeAnim.value + swayAnim.value + dragTilt + idleTilt + weightShift
                    scaleX = popScale * breatheScaleX * motionScaleX * dragScale.first
                    scaleY = popScale * breatheScaleY * motionScaleY * dragScale.second
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
                // 活感第一批瞳孔 clamp：基点扫视＋高频抖动叠加后仍钳制在
                // 开孔名义范围（x ±5px、y ±3px），不许整颗滑出露全白。
                val pupilClamped = clampPupilOffset(
                    pupilBaseX.value + jitterXAnim,
                    pupilBaseY.value + jitterYAnim,
                )
                val pupilDx = pupilClamped.x
                val pupilDy = pupilClamped.y
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
