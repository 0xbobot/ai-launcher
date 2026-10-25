package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetMouth
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.GazeTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 小流星（宠物 IP 形象，图片渲染，全身版）：
 * 按 mood 显示对应图片——IDLE/sleepy → meteor_main（站立），
 * HAPPY → meteor_jump（跳跃），FILE → meteor_hold（全身抱卡片），
 * SORTING/FETCH → meteor_wave（挥手）。
 * 位移动画由 mood 驱动：FETCH 上跳 / FILE 右移 / HAPPY 放大弹跳 / IDLE 轻微浮动。
 * P0：呼吸（4s 缩放）+ 眨眼（140ms 图片快切）+ 点按果冻（jellyTick 触发）。
 * v0.29.1 动作设计（学 Muse：慢速单轴轻摆，循环头尾无缝）：
 * - 姿势切换"顿一下"：切图时 90ms 挤到 0.88，再弹簧回弹，落点精确回 1.0
 * - 随移动倾斜：rotationZ 跟随 offX（±8°），弹簧跟随，静止精确回 0
 * - 各 mood 小循环（sin 曲线 + Restart，头尾都是 0，重复播放不突兀）：
 *   IDLE 轻摆 ±2.5°，HAPPY 小跳 8dp，FILE 轻摆 ±3°，SORTING/FETCH 挥手摆 ±5°，周期 2.4s
 * - 低电量时循环动作全停（省电）
 * mouth/blinking 为 Canvas 手绘时代遗留参数，保留签名兼容，内部不再使用。
 * alive-p1（七仔活物一期）：眨眼改眼部纵向压缩（复用 QizaiScene 眼盒，
 * 不再切图闪烁）；注视由注视导演统一调度（倾听 > 说话目标 > 瞥视 > 巡看）；
 * 浮动/呼吸/小循环去周期化（三路独立游走，周期与幅度 ±20% 重抽）。
 * alive-p2（七仔活物 P0 程序化运动变换，《七仔视觉活化设计》§5）：
 * - L0 呼吸绕脚底 pivot（transformOrigin 0.5,1.0），体积守恒
 *   （scaleY 1.025 / scaleX 0.993），吸 40% 呼 60% 非对称波形
 * - L0 眨眼三段时序：闭 55ms → 停 25ms → 开 110ms；新增 12% 半眨
 * - L0 微扫视：注视停留期间瞳孔 ±1.5px 每 1.5–3s 轻跳
 * - L1 重心摆动 ±1.2°（5.5–8s 游走，第 4 路 wander）+ 重心转移
 *   ±2.5°（25–50s 一次，hold 2–4s，spring d0.6 回）；浮动 -8dp → ±2dp
 *   且挂呼气相位（呼气时身体下沉）
 * - L2 瞳孔 saccade 两段式：120ms 冲到 106%、90ms 回落；30% 扫视伴眨；
 *   头部跟随改 spring d0.7（自带 ~3% overshoot）
 * - L3 微行为池：每 20–45s 抽歪头/伸懒腰/抖毛，AttentionBudget 管上限，
 *   idle 越久越安静（FidgetPool.quietedIntervalMs）
 * - 切图藏在 squash 最低点那一帧，删除 Crossfade（活物不换图，活物做动作）；
 *   落地伴随呼气（bob 相位重置到呼气段）；squash 深度随情绪
 * - 全部常驻层参数变化，不引入 AnimatedVisibility，不与 IME 首秀同窗；
 *   新通道统一挂 motionActive（低电量/减动效）门禁
 */
@Composable
fun PetView(
    mood: PetMood,
    mouth: PetMouth,
    blinking: Boolean,
    /** alive-p1：意图输入聚焦时的倾听态——注视输入框方向，巡看让位。 */
    listening: Boolean = false,
    /** 夜晚/睡眠态：强制显示闭眼待机图 */
    sleepy: Boolean = false,
    /** 点按果冻：每次 +1 触发一次果冻回弹（纯视觉反馈） */
    jellyTick: Int = 0,
    /** F 批起：抑制 FETCH 姿态的 -52dp 上浮——输入态伴随七仔必须钉在
     * 既定位置（F 批坐胶囊边缘时上浮量超过遮挡深度会读成悬空；v4 紧凑
     * 行时上浮会破坏与推荐问的居中对齐）；Dock 栖坐等其他语境不传，
     * 保持原姿态语言。 */
    suppressPoseLift: Boolean = false,
    /** v0.43.0：场景动画（Mii 式程序化）。非 NONE 时接管本体渲染，mood 动画暂停 */
    scene: QizaiScene = QizaiScene.NONE,
    onSceneDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val inScene = scene != QizaiScene.NONE
    // 场景播放时 mood 驱动的位移动作归零（场景自带动作），拖拽/果冻保留
    val moodM = if (inScene) 0f else 1f
    // R3-D：系统减动效时装饰循环（浮动/呼吸/摆动/害羞轻颤）全部静止，姿态图与状态切换保留
    val reduceMotion = com.bobot.ailauncher.ui.theme.rememberReduceMotion()
    val density = LocalDensity.current
    // v0.28.1：低电量省电模式——七仔变困、动画降频
    val lowPower by com.bobot.ailauncher.data.BatteryState.isLowPower
        .collectAsState()
    // 低电量时强制闭眼（像没电犯困）
    val effectiveSleepy = sleepy || lowPower
    // v0.56.0 M4 深夜：动作减速 30%（幅度 × 0.7）
    val nightSlow = if (PetRepository.isNight()) 0.7f else 1f
    // alive-p2：新生命通道统一门禁——低电量/减动效时静止（FloatingPet 同款口径）
    val motionActive = !lowPower && !reduceMotion
    // alive-p1 去周期化 + alive-p2 第 4 路：呼吸/小循环/重心摆动各走一路
    // 独立游走相位（32ms tick），每周期末周期与幅度在 ±20% 内重抽——
    // 严格正弦是机器最强的自白。各通道相位互相独立（不同相是活物）。
    var breathePhase by remember { mutableFloatStateOf(0f) }
    var breathePeriod by remember { mutableFloatStateOf(BREATHE_PERIOD_BASE_MS) }
    var breatheAmp by remember { mutableFloatStateOf(1f) }
    var loopPhase by remember { mutableFloatStateOf(0f) }
    var loopPeriod by remember { mutableFloatStateOf(2400f) }
    var loopAmp by remember { mutableFloatStateOf(1f) }
    var swayPhase by remember { mutableFloatStateOf(0f) }
    var swayPeriod by remember { mutableFloatStateOf(SWAY_PERIOD_BASE_MS) }
    var swayAmp by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(32)
            val r = kotlin.random.Random
            breathePhase += (2 * PI * 32 / breathePeriod).toFloat()
            if (breathePhase >= 2 * PI) {
                breathePhase -= (2 * PI).toFloat()
                breathePeriod = wanderPeriodMs(BREATHE_PERIOD_BASE_MS, r)
                breatheAmp = wanderAmp(r)
            }
            loopPhase += (2 * PI * 32 / loopPeriod).toFloat()
            if (loopPhase >= 2 * PI) {
                loopPhase -= (2 * PI).toFloat()
                loopPeriod = wanderPeriodMs(2400f, r); loopAmp = wanderAmp(r)
            }
            swayPhase += (2 * PI * 32 / swayPeriod).toFloat()
            if (swayPhase >= 2 * PI) {
                swayPhase -= (2 * PI).toFloat()
                swayPeriod = wanderPeriodMs(SWAY_PERIOD_BASE_MS, r)
                swayAmp = wanderAmp(r)
            }
        }
    }
    // alive-p2 L0 呼吸：非对称波形（吸 40% 呼 60%），体积守恒
    // （scaleY 1.025 / scaleX 0.993），绕脚底 pivot（graphicsLayer 处设定）。
    // L1 浮动：-8dp → ±2dp，挂呼吸相位——呼气（breathW 回落）时身体下沉。
    val breathW = breatheWave(breathePhase) * breatheAmp
    val bob = -BOB_AMP_DP * breathW
    val breatheScaleY = if (motionActive) 1f + (BREATHE_SCALE_Y - 1f) * breathW else 1f
    val breatheScaleX = if (motionActive) 1f + (BREATHE_SCALE_X - 1f) * breathW else 1f
    // alive-p2 L1 重心摆动：±1.2° 绕脚底，与呼吸刻意不同相（独立 wander 通道）
    val swayRot = if (motionActive) {
        sin(swayPhase) * SWAY_AMP_DEG * swayAmp * nightSlow
    } else 0f

    // alive-p2 L0 眨眼：三段时序（闭 55 / 停 25 / 开 110）+ 12% 半眨。
    // 眼盒纵向压缩（沿用 alive-p1 画法），Animatable 顺序驱动，Mutex 保证
    // 间隔眨眼与扫视伴眨不打架（串行执行，不做半路取消——半路取消会把
    // 眼盒卡在半闭）。
    val blinkScale = remember { Animatable(1f) }
    val blinkMutex = remember { Mutex() }
    val doBlink: suspend (Boolean) -> Unit = { half ->
        blinkMutex.withLock {
            blinkScale.animateTo(
                if (half) BLINK_HALF_TARGET else BLINK_FULL_TARGET,
                tween(BLINK_CLOSE_MS, easing = FastOutLinearEasing)
            )
            delay(BLINK_HOLD_MS.toLong())
            blinkScale.animateTo(
                1f,
                tween(BLINK_OPEN_MS, easing = FastOutSlowInEasing)
            )
        }
    }
    LaunchedEffect(lowPower, effectiveSleepy) {
        val r = kotlin.random.Random
        while (true) {
            delay(BlinkPolicy.intervalMs(lowPower, r))
            if (effectiveSleepy) continue // 本来就闭眼待机，不眨
            doBlink(BlinkPolicy.isHalfBlink(r))
            if (BlinkPolicy.isDoubleBlink(r)) {
                delay(120)
                doBlink(false)
            }
        }
    }

    // alive-p1 注视导演：倾听 > 说话目标 > 瞥视 > 巡看（优先级即因果链）。
    // alive-p2 L2：瞳孔 saccade 两段式（120ms 冲 106% → 90ms 回落）+
    // 30% 扫视伴眨 + 微扫视；头部延迟 80ms 跟随，spring d0.7 自带回弹。
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
    // 巡看：8–15 秒一次，落点只从桌面语义点里选，停留 1.2 秒后回正前方。
    var patrolTarget by remember { mutableStateOf(GazeTarget.CENTER) }
    LaunchedEffect(lowPower) {
        while (true) {
            delay(8000 + kotlin.random.Random.nextLong(7000))
            if (lowPower) continue
            patrolTarget = patrolTargetPool().random()
            delay(1200)
            patrolTarget = GazeTarget.CENTER
        }
    }
    val effectiveGazeTarget = when {
        listening -> GazeTarget.INPUT
        repoGazeTarget != GazeTarget.CENTER -> repoGazeTarget
        glanceTarget != null -> glanceTarget!!
        else -> patrolTarget
    }
    val gazeOff = gazeOffsetFor(effectiveGazeTarget)
    // 瞳孔 saccade（x ±6px，y ±4px）：生物眼球是跳跃式的，匀速是云台。
    // 出发瞬间 30% 概率伴眨；中途改目标时从当前位置重新起跳（不回落再跳）。
    val pupilX = remember { Animatable(0f) }
    val pupilY = remember { Animatable(0f) }
    LaunchedEffect(gazeOff) {
        val tx = gazeOff.x * 6f
        val ty = gazeOff.y * 4f
        if (pupilX.value == tx && pupilY.value == ty) return@LaunchedEffect
        if (!effectiveSleepy && BlinkPolicy.isSaccadeBlink(kotlin.random.Random)) {
            launch { doBlink(false) }
        }
        launch {
            pupilX.animateTo(
                pupilX.value + (tx - pupilX.value) * SACCADE_OVERSHOOT,
                tween(SACCADE_OUT_MS, easing = FastOutLinearEasing)
            )
            pupilX.animateTo(
                tx,
                tween(SACCADE_SETTLE_MS, easing = FastOutSlowInEasing)
            )
        }
        launch {
            pupilY.animateTo(
                pupilY.value + (ty - pupilY.value) * SACCADE_OVERSHOOT,
                tween(SACCADE_OUT_MS, easing = FastOutLinearEasing)
            )
            pupilY.animateTo(
                ty,
                tween(SACCADE_SETTLE_MS, easing = FastOutSlowInEasing)
            )
        }
    }
    // 微扫视：注视停留期间瞳孔在目标点 ±1.5px 内每 1.5–3s 轻跳（死眼 = 图片）。
    var jitterX by remember { mutableFloatStateOf(0f) }
    var jitterY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        val r = kotlin.random.Random
        while (true) {
            delay(MicroSaccade.intervalMs(r))
            jitterX = MicroSaccade.offsetPx(r)
            jitterY = MicroSaccade.offsetPx(r)
        }
    }
    val jitterXAnim by animateFloatAsState(
        targetValue = jitterX,
        animationSpec = tween(MICRO_SACCADE_STEP_MS, easing = FastOutLinearEasing),
        label = "jitterX"
    )
    val jitterYAnim by animateFloatAsState(
        targetValue = jitterY,
        animationSpec = tween(MICRO_SACCADE_STEP_MS, easing = FastOutLinearEasing),
        label = "jitterY"
    )
    // 头部延迟 80ms 跟随：spring d0.7 自带 ~3% overshoot（x ±10px，y ±6px）
    var headGaze by remember { mutableStateOf(GazeOffset(0f, 0f)) }
    LaunchedEffect(effectiveGazeTarget) {
        delay(80)
        headGaze = gazeOff
    }
    val headX by animateFloatAsState(
        targetValue = headGaze.x * 10f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "headX"
    )
    val headY by animateFloatAsState(
        targetValue = headGaze.y * 6f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "headY"
    )

    // alive-p2 L1 重心转移：每 25–50s 偏 ±2.5°，hold 2–4s，spring d0.6 回——
    // 像换了一只脚承重。
    var weightShiftTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        val r = kotlin.random.Random
        while (true) {
            delay(WeightShift.intervalMs(r))
            weightShiftTarget = WeightShift.angleDeg(r)
            delay(WeightShift.holdMs(r))
            weightShiftTarget = 0f
        }
    }
    val weightShift by animateFloatAsState(
        targetValue = weightShiftTarget,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "weightShift"
    )

    // alive-p2 L3 微行为池：每 20–45s（idle 越久越安静）抽 1 个，
    // AttentionBudget 管上限；每个行为完整播完再清零，transformOrigin
    // 切换只发生在参数归零的间隙，不会跳变。
    var fidget by remember { mutableStateOf<Fidget?>(null) }
    val headTiltDeg = remember { Animatable(0f) }
    val stretchT = remember { Animatable(0f) }
    val shakeDeg = remember { Animatable(0f) }
    LaunchedEffect(motionActive, inScene) {
        if (!motionActive || inScene) return@LaunchedEffect
        val r = kotlin.random.Random
        while (true) {
            val base = FidgetPool.intervalMs(r)
            delay(FidgetPool.quietedIntervalMs(base, PetRepository.minutesIdleNow()))
            if (!PetRepository.attentionBudget.tryConsume()) continue
            when (FidgetPool.pick(r)) {
                Fidget.HEAD_TILT -> {
                    fidget = Fidget.HEAD_TILT
                    headTiltDeg.animateTo(
                        FidgetPool.headTiltAngleDeg(r),
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                    )
                    delay(FidgetPool.headTiltHoldMs(r))
                    headTiltDeg.animateTo(
                        0f,
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                    )
                }
                Fidget.STRETCH -> {
                    fidget = Fidget.STRETCH
                    stretchT.animateTo(
                        1f,
                        tween(FidgetPool.STRETCH_UP_MS, easing = FastOutSlowInEasing)
                    )
                    delay(FidgetPool.STRETCH_HOLD_MS)
                    stretchT.animateTo(
                        0f,
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
                    )
                }
                Fidget.SHAKE -> {
                    fidget = Fidget.SHAKE
                    repeat(FidgetPool.SHAKE_REPS) {
                        shakeDeg.animateTo(
                            FidgetPool.SHAKE_AMP_DEG,
                            tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing)
                        )
                        shakeDeg.animateTo(
                            -FidgetPool.SHAKE_AMP_DEG,
                            tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing)
                        )
                    }
                    shakeDeg.animateTo(
                        0f,
                        tween(FidgetPool.SHAKE_STEP_MS, easing = LinearEasing)
                    )
                }
            }
            fidget = null
        }
    }

    // P0：点按果冻——按压 120ms 后 spring 回弹（纯视觉反馈）
    // v0.56.0 M2：非对称果冻 scaleX 1.2 / scaleY 0.8，0.3 秒回弹 + 开心表情，不说话
    var jellyPress by remember { mutableStateOf(false) }
    LaunchedEffect(jellyTick) {
        if (jellyTick > 0) {
            jellyPress = true
            PetRepository.setUserInteracting(true)
            delay(120)
            jellyPress = false
            PetRepository.setUserInteracting(false)
        }
    }
    val jellyX by animateFloatAsState(
        targetValue = if (jellyPress) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jellyX"
    )
    val jellyY by animateFloatAsState(
        targetValue = if (jellyPress) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jellyY"
    )

    val targetX = if (mood == PetMood.FILE) 104f else 0f
    val targetY = if (mood == PetMood.FETCH && !suppressPoseLift) -52f else 0f
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

    // v0.56.0 M2：SHY 害羞 / DIZZY 晕眩 / SURPRISED 惊讶（用现有图近似，表情靠说话+动作传达）
    val baseResId = when {
        effectiveSleepy || mood == PetMood.IDLE -> R.drawable.meteor_main
        mood == PetMood.HAPPY -> R.drawable.meteor_jump
        mood == PetMood.FILE -> R.drawable.meteor_hold
        mood == PetMood.SHY -> R.drawable.meteor_main // 害羞：站立 + 腮红 overlay
        mood == PetMood.DIZZY -> R.drawable.meteor_main // 晕眩：站立 + 旋转
        mood == PetMood.SURPRISED -> R.drawable.meteor_wave // 惊讶：挥手（手举起）
        else -> R.drawable.meteor_wave // SORTING / FETCH
    }
    // alive-p1：眨眼不再切图闪烁——眼带压缩覆盖层见下方 Canvas；
    // 低电量/睡眠时本来就待机态，不眨（覆盖层同样受 effectiveSleepy 门禁）。
    val resId = baseResId

    // alive-p2 转场：活物不换图，活物做动作——先挤到 squash 最低点那一帧
    // 再换图（删除 Crossfade，幻灯片感消失）；squash 深度随情绪
    // （HAPPY 0.85 跳得高，困时 0.95 没力气）；落地伴随呼气：
    // 回弹瞬间把呼吸相位重置到呼气段起点，生物落地时呼气。
    var displayedResId by remember { mutableIntStateOf(resId) }
    var poseSquash by remember { mutableStateOf(false) }
    LaunchedEffect(resId) {
        poseSquash = true
        delay(90)
        displayedResId = resId
        breathePhase = BREATHE_EXHALE_START_RAD
        poseSquash = false
    }
    val posePop by animateFloatAsState(
        targetValue = if (poseSquash) squashDepth(mood, effectiveSleepy) else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "posePop"
    )
    // v0.29.1：随移动倾斜——往哪边飘就往哪边倾一点，弹簧跟随，静止时精确回 0
    val tilt by animateFloatAsState(
        targetValue = (offX * 0.12f).coerceIn(-8f, 8f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tilt"
    )
    // v0.29.1：小循环——sin 曲线走满 2π，头尾都是 0，重复播放不突兀；
    // 周期慢、幅度小（不招摇）；低电量全停。alive-p1：相位改游走驱动，
    // 每周期末幅度在 ±20% 内重抽（loopAmp）。
    val loopTheta = loopPhase
    val loopRot = when {
        lowPower || reduceMotion -> 0f
        mood == PetMood.HAPPY -> 0f // 开心用小跳，不转
        mood == PetMood.IDLE || effectiveSleepy -> sin(loopTheta) * 2.5f * nightSlow * loopAmp
        mood == PetMood.FILE -> sin(loopTheta) * 3f * nightSlow * loopAmp
        else -> sin(loopTheta) * 5f * nightSlow * loopAmp // SORTING / FETCH 挥手摆动
    }
    val loopYdp = when {
        lowPower || reduceMotion -> 0f
        mood == PetMood.HAPPY -> -abs(sin(loopTheta)) * 8f * nightSlow // 开心小跳，只往上，落点回 0
        else -> 0f
    }

    // v0.56.0 M2 晕眩：松手后转 2 圈（1 秒）
    var dizzySpin by remember { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (mood == PetMood.DIZZY) {
            dizzySpin = true
            delay(1000)
            dizzySpin = false
        }
    }
    val dizzyRot by animateFloatAsState(
        targetValue = if (dizzySpin) 720f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "dizzyRot"
    )
    // v0.56.0 M2 害羞轻颤：高频小幅抖动
    val shyTremble by rememberInfiniteTransition(label = "shyTremble").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shyTrembleVal"
    )
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // v0.56.0 M2：拖拽状态
    var isDragging by remember { mutableStateOf(false) }
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
                    onDragStart = {
                        // v0.56.0 M2：提起惊讶 + 播报让行
                        isDragging = true
                        PetRepository.onDragStart()
                        PetRepository.say("借过借过~")
                    },
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
                        // v0.56.0 M2：松手弹跳 + 晕眩 1 秒
                        isDragging = false
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                        PetRepository.onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = androidx.compose.ui.geometry.Offset.Zero
                        PetRepository.onDragEnd()
                    }
                )
            }
            .then(modifier)
            .graphicsLayer {
                // alive-p2 L0/L1：呼吸/摆动/重心转移绕脚底 pivot——绕中心
                // 缩放是贴纸放大，绕脚底才是胸腔起伏。
                transformOrigin = TransformOrigin(0.5f, 1f)
                // alive-p2 注视合成：瞳孔 saccade + 微扫视 jitter + 头部延迟跟随；
                // 倾听/说话/瞥视/巡看由注视导演统一排优先级
                // v0.56.0 M2 害羞轻颤
                // R3-D：减动效时浮动（bob）与轻颤归零，宠物静止在原位
                val tremble = if (mood == PetMood.SHY && !reduceMotion) shyTremble else 0f
                val bobEff = if (reduceMotion) 0f else bob
                val jx = if (inScene) 1f else jellyX
                val jy = if (inScene) 1f else jellyY
                translationX = with(density) { offX.dp.toPx() } + dragX +
                    pupilX.value + headX + tremble + jitterXAnim
                translationY = with(density) { (offY + bobEff * moodM + loopYdp * moodM).dp.toPx() } +
                    dragY + pupilY.value + headY + jitterYAnim
                // v0.56.0 M2 晕眩旋转；alive-p2：摆动/重心转移进 moodM 组
                rotationZ = (tilt + loopRot + swayRot + weightShift) * moodM + dizzyRot
                // alive-p2：呼吸非对称体积守恒（scaleX 微收 / scaleY 鼓起）
                val s = scale * posePop
                scaleX = s * breatheScaleX * jx
                scaleY = s * breatheScaleY * jy
            }
    ) {
        if (!inScene) {
            // alive-p2 L3 微行为层：歪头绕头部 pivot、伸懒腰绕底、抖毛绕中心。
            // 同一时刻至多一个 fidget，且 transformOrigin 只在参数归零时切换。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (fidget) {
                            Fidget.HEAD_TILT -> {
                                transformOrigin = TransformOrigin(0.5f, FidgetPool.HEAD_PIVOT_Y)
                                rotationZ = headTiltDeg.value
                            }
                            Fidget.STRETCH -> {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleY = 1f + (FidgetPool.STRETCH_SCALE_Y - 1f) * stretchT.value
                                scaleX = 1f + (FidgetPool.STRETCH_SCALE_X - 1f) * stretchT.value
                            }
                            Fidget.SHAKE -> {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationZ = shakeDeg.value
                            }
                            null -> {}
                        }
                    }
            ) {
                // alive-p2：切图藏在 squash 最低点那一帧（displayedResId 滞后
                // resId 90ms），删除 Crossfade——慢溶接是幻灯片，squash 换图
                // 是活物在动。
                Image(
                    painter = painterResource(displayedResId),
                    contentDescription = "七仔",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // alive-p1 眨眼：眼带纵向压缩覆盖层（QizaiScene 同款做法的
                // Image 版）。只在压缩进行中绘制：scale=1 时与底图像素完全重合，
                // 平时不画，避免双重绘制的接缝。眼盒复用 QizaiScene.EYE_BOXES
                // （jump 姿态无专属眼盒，落 main 的眼盒近似）。
                // alive-p2：三段时序由 blinkScale Animatable 驱动；半眨同一通道。
                if (blinkScale.value < 0.999f && !effectiveSleepy) {
                    // 与 QizaiScene 一致：直接取 ImageBitmap，不走 BitmapPainter
                    val blinkBmp = ImageBitmap.imageResource(displayedResId)
                    run {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 与底图 ContentScale.Fit 完全一致的放置数学
                            val fit = minOf(
                                size.width / blinkBmp.width,
                                size.height / blinkBmp.height
                            )
                            val dw = blinkBmp.width * fit
                            val dh = blinkBmp.height * fit
                            val left = (size.width - dw) / 2f
                            val top = (size.height - dh) / 2f
                            val eyeKey = when (displayedResId) {
                                R.drawable.meteor_wave -> "wave"
                                R.drawable.meteor_hold -> "hold"
                                else -> "main"
                            }
                            val eyeScale = blinkScale.value
                            for (b in EYE_BOXES.getValue(eyeKey)) {
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
                                    srcOffset = IntOffset(sbl, sbt),
                                    srcSize = IntSize(sbw, sbh),
                                    dstOffset = IntOffset(
                                        dbl.toInt(),
                                        (cy - dbh / 2f).toInt()
                                    ),
                                    dstSize = IntSize(dbw, dbh)
                                )
                            }
                        }
                    }
                }
                // v0.56.0 M2 害羞腮红：两坨粉色椭圆
                if (mood == PetMood.SHY) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        drawOval(
                            color = androidx.compose.ui.graphics.Color(255, 150, 150, 180),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.42f),
                            size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.07f)
                        )
                        drawOval(
                            color = androidx.compose.ui.graphics.Color(255, 150, 150, 180),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.42f),
                            size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.07f)
                        )
                    }
                }
            }
        } else {
            // v0.43.0：场景动画接管本体渲染
            QizaiScenePlayer(
                scene = scene,
                onDone = onSceneDone,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
