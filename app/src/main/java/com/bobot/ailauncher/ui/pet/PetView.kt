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
import com.bobot.ailauncher.data.PetRepository
import kotlin.math.abs
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
    /** v0.43.0：场景动画（Mii 式程序化）。非 NONE 时接管本体渲染，mood 动画暂停 */
    scene: QizaiScene = QizaiScene.NONE,
    onSceneDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val inScene = scene != QizaiScene.NONE
    // 场景播放时 mood 驱动的位移动作归零（场景自带动作），拖拽/果冻保留
    val moodM = if (inScene) 0f else 1f
    val density = LocalDensity.current
    // v0.28.1：低电量省电模式——七仔变困、动画降频
    val lowPower by com.bobot.ailauncher.data.BatteryState.isLowPower
        .collectAsState()
    // 低电量时强制闭眼（像没电犯困）
    val effectiveSleepy = sleepy || lowPower
    // v0.56.0 M4 深夜：动作减速 30%（幅度 × 0.7）
    val nightSlow = if (PetRepository.isNight()) 0.7f else 1f
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
    // P0：眨眼——0.15 秒快闪
    // v0.56.0 M1：间隔 8 秒 ± 随机 3 秒（5~11 秒），静置不觉得死了也不吵
    var blinkTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lowPower) {
        while (true) {
            val interval = if (lowPower) 12000L
            else 8000L + (kotlin.random.Random.nextFloat() * 6000L - 3000L).toLong()
            delay(interval.coerceAtLeast(3000L))
            blinkTick++
            delay(150)
            blinkTick++
        }
    }
    val isBlinking = blinkTick % 2 == 1
    // v0.56.0 M1 巡看：瞳孔先行、头部延迟 80ms 跟随——用整体轻微左右摆模拟
    // 瞳孔（快，200ms）→ 头部（慢，延迟 80ms 后 280ms 跟上）
    var lookDir by remember { mutableStateOf(0f) } // -1 左 / 0 中 / 1 右
    LaunchedEffect(lowPower) {
        while (true) {
            delay(9000 + kotlin.random.Random.nextLong(8000)) // 9~17 秒看一次
            if (lowPower) continue
            lookDir = if (kotlin.random.Random.nextBoolean()) 1f else -1f
            delay(1200) // 停留看 1.2 秒
            lookDir = 0f
        }
    }
    // 瞳孔先行：快速 200ms 到位
    val pupilX by animateFloatAsState(
        targetValue = lookDir * 6f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pupilX"
    )
    // 头部延迟 80ms 跟随：280ms 到位
    var headLookDir by remember { mutableStateOf(0f) }
    LaunchedEffect(lookDir) {
        delay(80)
        headLookDir = lookDir
    }
    val headX by animateFloatAsState(
        targetValue = headLookDir * 10f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "headX"
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
    // 眨眼时切主图闪一下（140ms）；低电量时本来就待机态，不眨了
    val resId = if (isBlinking && !effectiveSleepy && mood != PetMood.IDLE) {
        R.drawable.meteor_main
    } else {
        baseResId
    }

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
    // v0.29.1：Muse 式无缝小循环——sin 曲线走满 2π，Restart 模式头尾都是 0，
    // 重复播放不突兀；周期慢（2.4s）、幅度小（不招摇）；低电量全停
    val loopPhase by rememberInfiniteTransition(label = "petLoop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loopPhase"
    )
    val loopTheta = (loopPhase * 2 * Math.PI).toFloat()
    val loopRot = when {
        lowPower -> 0f
        mood == PetMood.HAPPY -> 0f // 开心用小跳，不转
        mood == PetMood.IDLE || effectiveSleepy -> sin(loopTheta) * 2.5f * nightSlow
        mood == PetMood.FILE -> sin(loopTheta) * 3f * nightSlow
        else -> sin(loopTheta) * 5f * nightSlow // SORTING / FETCH 挥手摆动
    }
    val loopYdp = when {
        lowPower -> 0f
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
                // v0.56.0 M1 巡看：瞳孔 6px 先行 + 头部 10px 延迟跟随
                // v0.56.0 M2 害羞轻颤
                val tremble = if (mood == PetMood.SHY) shyTremble else 0f
                translationX = with(density) { offX.dp.toPx() } + dragX + pupilX + headX + tremble
                translationY = with(density) { (offY + bob * moodM + loopYdp * moodM).dp.toPx() } + dragY
                // v0.56.0 M2 晕眩旋转
                rotationZ = (tilt + loopRot) * moodM + dizzyRot
                // v0.56.0 M2：非对称果冻
                val jx = if (inScene) 1f else jellyX
                val jy = if (inScene) 1f else jellyY
                val s = scale * breathe * posePop
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
