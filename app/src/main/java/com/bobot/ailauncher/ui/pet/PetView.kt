package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import com.bobot.ailauncher.data.PetRepository.GazeTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
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
 * - 切图 Crossfade 加快到 150ms；低电量时循环动作全停（省电）
 * mouth/blinking 为 Canvas 手绘时代遗留参数，保留签名兼容，内部不再使用。
 * alive-p1（七仔活物一期）：眨眼改眼部纵向压缩（复用 QizaiScene 眼盒，
 * 不再切图闪烁）；注视由注视导演统一调度（倾听 > 说话目标 > 瞥视 > 巡看）；
 * 浮动/呼吸/小循环去周期化（三路独立游走，周期与幅度 ±20% 重抽）。
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
    // alive-p1 去周期化：浮动/呼吸/小循环各走一路独立游走相位（32ms tick），
    // 每周期末周期与幅度在 ±20% 内重抽——严格正弦是机器最强的自白。
    // 旧口径：浮动 3.2s 周期 -8dp、呼吸 4s 周期 1.03、小循环 2.4s 周期。
    var bobPhase by remember { mutableFloatStateOf(0f) }
    var bobPeriod by remember { mutableFloatStateOf(3200f) }
    var bobAmp by remember { mutableFloatStateOf(1f) }
    var breathePhase by remember { mutableFloatStateOf(0f) }
    var breathePeriod by remember { mutableFloatStateOf(4000f) }
    var breatheAmp by remember { mutableFloatStateOf(1f) }
    var loopPhase by remember { mutableFloatStateOf(0f) }
    var loopPeriod by remember { mutableFloatStateOf(2400f) }
    var loopAmp by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(32)
            val r = kotlin.random.Random
            bobPhase += (2 * PI * 32 / bobPeriod).toFloat()
            if (bobPhase >= 2 * PI) {
                bobPhase -= (2 * PI).toFloat()
                bobPeriod = wanderPeriodMs(3200f, r); bobAmp = wanderAmp(r)
            }
            breathePhase += (2 * PI * 32 / breathePeriod).toFloat()
            if (breathePhase >= 2 * PI) {
                breathePhase -= (2 * PI).toFloat()
                breathePeriod = wanderPeriodMs(4000f, r); breatheAmp = wanderAmp(r)
            }
            loopPhase += (2 * PI * 32 / loopPeriod).toFloat()
            if (loopPhase >= 2 * PI) {
                loopPhase -= (2 * PI).toFloat()
                loopPeriod = wanderPeriodMs(2400f, r); loopAmp = wanderAmp(r)
            }
        }
    }
    // idle 轻微浮动（余弦缓动近似原 FastOutSlowIn 往返）
    val bob = -8f * bobAmp * (0.5f - 0.5f * cos(bobPhase))
    // P0：呼吸——细微缩放，比浮动更"活"
    // v0.28.1：低电量时停止呼吸（静态，省电）
    val breathe: Float = if (lowPower) {
        1f
    } else {
        1f + 0.03f * breatheAmp * (0.5f - 0.5f * cos(breathePhase))
    }
    // alive-p1 眨眼：眼部纵向压缩（复用 QizaiScene 做法，废弃切图快闪）。
    // 间隔 2–6 秒随机，约 10% 连眨；低电量 12 秒（沿用现有口径）。
    var blinkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lowPower) {
        while (true) {
            delay(BlinkPolicy.intervalMs(lowPower, kotlin.random.Random))
            blinkTick++
            delay(140)
            blinkTick++
            if (BlinkPolicy.isDoubleBlink(kotlin.random.Random)) {
                delay(120)
                blinkTick++
                delay(140)
                blinkTick++
            }
        }
    }
    val isBlinking = blinkTick % 2 == 1
    // 压缩动画：70ms 闭合 + 70ms 睁开（≈原 140ms 快闪），绕眼带中心缩放。
    val blinkScale by animateFloatAsState(
        targetValue = if (isBlinking) 0.08f else 1f,
        animationSpec = tween(70, easing = FastOutSlowInEasing),
        label = "blinkScale"
    )
    // alive-p1 注视导演：倾听 > 说话目标 > 瞥视 > 巡看（优先级即因果链）。
    // 瞳孔（快，200ms）→ 头部（慢，延迟 80ms 后 280ms 跟上），与原巡看同款时序。
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
    // 瞳孔先行：快速 200ms 到位（x ±6px，y ±4px）
    val pupilX by animateFloatAsState(
        targetValue = gazeOff.x * 6f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pupilX"
    )
    val pupilY by animateFloatAsState(
        targetValue = gazeOff.y * 4f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pupilY"
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
        label = "headX"
    )
    val headY by animateFloatAsState(
        targetValue = headGaze.y * 6f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "headY"
    )
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

    // v0.29.1：姿势切换"顿一下"——切图时先挤到 0.88（90ms），再弹簧回弹（轻微 overshoot），
    // 落点精确回到 1.0，遮掉切图的生硬感；比慢溶接更像活物在动
    var poseSquash by remember { mutableStateOf(false) }
    LaunchedEffect(resId) {
        poseSquash = true
        delay(90)
        poseSquash = false
    }
    val posePop by animateFloatAsState(
        targetValue = if (poseSquash) 0.88f else 1f,
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
                // alive-p1 注视：瞳孔（x ±6px / y ±4px）先行 + 头部（x ±10px /
                // y ±6px）延迟跟随；倾听/说话/瞥视/巡看由注视导演统一排优先级
                // v0.56.0 M2 害羞轻颤
                // R3-D：减动效时浮动（bob）与轻颤归零，宠物静止在原位
                val tremble = if (mood == PetMood.SHY && !reduceMotion) shyTremble else 0f
                val bobEff = if (reduceMotion) 0f else bob
                translationX = with(density) { offX.dp.toPx() } + dragX + pupilX + headX + tremble
                translationY = with(density) { (offY + bobEff * moodM + loopYdp * moodM).dp.toPx() } + dragY + pupilY + headY
                // v0.56.0 M2 晕眩旋转
                rotationZ = (tilt + loopRot) * moodM + dizzyRot
                // v0.56.0 M2：非对称果冻
                val jx = if (inScene) 1f else jellyX
                val jy = if (inScene) 1f else jellyY
                val breatheEff = if (reduceMotion) 1f else breathe // R3-D：减动效停呼吸缩放
                val s = scale * breatheEff * posePop
                scaleX = s * jx
                scaleY = s * jy
            }
    ) {
        if (!inScene) {
            // v0.26.0：图片切换 Crossfade；v0.29.1：150ms 快溶（慢溶接像幻灯片）+ 姿势顿一下，不再拖沓
            Crossfade(
                targetState = resId,
                animationSpec = tween(150),
                label = "petImage"
            ) { id ->
                Image(
                    painter = painterResource(id),
                    contentDescription = "七仔",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // alive-p1 眨眼：眼带纵向压缩覆盖层（QizaiScene 同款做法的
            // Image 版）。只在压缩进行中绘制：scale=1 时与底图像素完全重合，
            // 平时不画，避免双重绘制的接缝。眼盒复用 QizaiScene.EYE_BOXES
            // （jump 姿态无专属眼盒，落 main 的眼盒近似）。
            if (blinkScale < 0.999f && !effectiveSleepy) {
                val blinkPainter = painterResource(resId) as? BitmapPainter
                val blinkBmp = blinkPainter?.imageBitmap
                if (blinkBmp != null) {
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
                        val eyeKey = when (resId) {
                            R.drawable.meteor_wave -> "wave"
                            R.drawable.meteor_hold -> "hold"
                            else -> "main"
                        }
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
                            val dbh = ((dbb - dbt) * blinkScale).toInt()
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
