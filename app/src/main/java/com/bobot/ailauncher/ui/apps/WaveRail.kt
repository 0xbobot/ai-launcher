package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** v0.40.1：rail 所在的一侧（左右双 rail 常驻，不再需要设置里选惯用手） */
internal enum class RailSide { LEFT, RIGHT }

/**
 * v0.67.13（Bob）：rail 滑动项——房子/收藏/字母/隐藏都在一条导航里滑动到达
 */
internal sealed interface RailItem {
    data object House : RailItem
    data object Star : RailItem
    data class Letter(val char: Char) : RailItem
    data object Eye : RailItem
    // v0.68.13（Bob）：分隔线——图标组和字母组之间的视觉分隔，不可触摸
    data object Separator : RailItem
}

/**
 * 波浪字母导航 v2（v0.25）。
 * - v0.40.2：右侧一条可见 rail + 左侧隐形触发区（Bob：左边不摆 rail，摆两个很奇怪；
 *   但左侧滑动同样触发字母切换，触发后右侧 rail 亮起波浪跟随；列表左侧缩进给手指留空间）
 * - 紧凑高度（约 62% 屏高，居中），字母不拉得太开
 * - 拖动时：附近字母按高斯衰减放大并向内鼓起，大气泡在靠应用的一侧跟随手指；
 *   列表进入聚焦模式（只显示当前字母），松手恢复全量
 * - 字母切换时给一记震动（v0.40.2：用 LongPress，TextHandleMove 在部分机型上无感）
 * - v0.41.0（B 方案·呼吸感）：rail 字母更细更淡（10sp/Normal/提示灰），
 *   当前字母气泡从实心金圆改为金环（描边 2dp + 深色字母），更轻
 * - v0.41.1：修点按 bug——点按后不能立刻 onRelease，否则右侧 rail 的
 *   stopGlide 会把刚启动的 animateScrollToItem 掐掉（列表不动、波浪卡住）；
 *   改为等 500ms 滚动落定再收波浪（左侧 rail 的 onRelease 不掐任务，本来就正常）
 * - 只做定位，不承载其他功能
 */
@Composable
internal fun WaveRail(
    items: List<RailItem>,
    onActiveItem: (RailItem) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    side: RailSide = RailSide.RIGHT,
    // v0.40.2：visible=false 时为左侧隐形触发区——只响应触摸，不绘制字母/气泡；
    // 触摸时通过 forcedActiveIndex 让右侧可见 rail 跟随显示波浪
    visible: Boolean = true,
    forcedActiveIndex: Int? = null,
    railWidth: Dp = 44.dp,
    // v0.67.13：脉冲高亮某一项（收藏/隐藏时的闪烁反馈）
    pulseTick: Int = 0,
    pulseItem: RailItem? = null,
    // v0.68.20（Bob）：手势驱动——房子上拉/眼睛下拉的意向进度 0→1，替代停留时长
    housePull: Float = 0f,
    eyePull: Float = 0f,
    onHousePull: (Float) -> Unit = {},
    onEyePull: (Float) -> Unit = {},
    // v0.68.19（Bob）：柔和消散进度 0→1（阈值触发后的时间驱动）
    explodeProgress: Float = 0f
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 右侧 rail：波浪/气泡往左（向内）；左侧 rail：往右（向内）
    val dirSign = if (side == RailSide.RIGHT) -1f else 1f
    var activeIndex by remember { mutableIntStateOf(-1) }
    // v0.41.1：点按后的延迟收波浪任务——点按不能立刻 onRelease，
    // 否则右侧 rail 的 stopGlide 会把刚启动的 animateScrollToItem 直接掐掉
    var tapReleaseJob by remember { mutableStateOf<Job?>(null) }
    fun cancelPendingTapRelease() {
        tapReleaseJob?.cancel()
        tapReleaseJob = null
    }
    // 外部驱动优先（左侧隐形区触发时），否则用内部触摸状态
    val displayIndex = forcedActiveIndex ?: activeIndex

    // v0.67.13：脉冲高亮（收藏/隐藏时的闪烁+震动反馈）
    val pulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(pulseTick) {
        if (pulseItem != null && pulseTick > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pulseScale.animateTo(1.7f, tween(180))
            pulseScale.animateTo(1f, tween(220))
        }
    }

    // v0.40.2：字母切换给一记结实的震动——TextHandleMove 在部分机型上几乎无感
    fun emitItem(i: Int) {
        if (i == activeIndex) return
        // v0.68.13：分隔线不可触摸，滑过时无反应
        if (items[i] is RailItem.Separator) return
        activeIndex = i
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onActiveItem(items[i])
    }

    // v0.43.3（Bob）：字母被截断——行高 < 字高，比例提到 0.85
    val railFraction = 0.85f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
    ) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f || items.isEmpty()) return@BoxWithConstraints
        val railHpx = hPx * railFraction
        val railTopPx = (hPx - railHpx) / 2f
        val rowHpx = railHpx / items.size
        val bulgePx = with(density) { 36.dp.toPx() }
        val shiftPx = with(density) { 56.dp.toPx() }
        // v0.68.15（Bob）：波浪散开时字母纵向也拉开，更灵动不拥挤
        val spreadPx = with(density) { 7.dp.toPx() }
        // 气泡在靠应用的一侧：明显离开波浪区
        val bubbleOutX = with(density) { 128.dp.toPx() }

        // 波浪避让：拖动时整体往拇指反方向偏移
        // v0.40.2：更轻盈——加硬弹簧、减少晃悠，波浪更跟手
        val waveShiftX by animateFloatAsState(
            targetValue = if (displayIndex >= 0) dirSign * shiftPx else 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = 0.9f
            ),
            label = "waveShift"
        )

        fun indexAt(y: Float): Int {
            return (((y - railTopPx) / railHpx) * items.size)
                .toInt().coerceIn(items.indices)
        }

        // 触摸层：点按与纵向拖动
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items, hPx) {
                    detectTapGestures(onTap = { offset ->
                        // 点按：直接跳转
                        val i = indexAt(offset.y)
                        cancelPendingTapRelease()
                        activeIndex = -1 // 允许点按重复触发同一字母
                        emitItem(i)
                        // v0.41.1：等滚动落定再收波浪——立刻 onRelease 的话，
                        // 右侧 rail 的 stopGlide 会把刚启动的 animateScrollToItem
                        // （近距离字母）直接掐掉，导致列表不动、波浪卡住；
                        // 左侧 rail 的 onRelease 只清 mirrorIndex 不掐任务，所以左侧正常
                        tapReleaseJob = scope.launch {
                            delay(500)
                            activeIndex = -1
                            onRelease()
                        }
                    })
                }
                .pointerInput(items, hPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // 点按后立刻转拖动：掐掉点按的延迟收波浪，拖动接管
                            cancelPendingTapRelease()
                            emitItem(indexAt(offset.y))
                        },
                        onDragEnd = {
                            activeIndex = -1
                            onRelease()
                        },
                        onDragCancel = {
                            activeIndex = -1
                            onRelease()
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val y = change.position.y
                            val i = indexAt(y)
                            emitItem(i)
                            // v0.68.20（Bob）：意向拉拽——房子继续上拉/眼睛继续下拉，进度 0→1
                            val pullThresholdPx = with(density) { 100.dp.toPx() }
                            if (items.isNotEmpty() && items[0] is RailItem.House &&
                                i == 0 && y < railTopPx
                            ) {
                                onHousePull(
                                    ((railTopPx - y) / pullThresholdPx).coerceIn(0f, 1.2f)
                                )
                            } else {
                                onHousePull(0f)
                            }
                            val lastIdx = items.indices.lastOrNull()
                            if (lastIdx != null && items[lastIdx] is RailItem.Eye &&
                                i == lastIdx && y > railTopPx + railHpx
                            ) {
                                onEyePull(
                                    ((y - (railTopPx + railHpx)) / pullThresholdPx)
                                        .coerceIn(0f, 1.2f)
                                )
                            } else {
                                onEyePull(0f)
                            }
                        }
                    )
                }
        )

        // 字母列：紧凑居中，常显（visible=false 的隐形触发区不绘制）
        if (visible) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .fillMaxHeight(railFraction),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEachIndexed { i, item ->
                // v0.68.13（Bob）：分隔线——图标组和字母组之间，视觉分隔+拉开间距
                if (item is RailItem.Separator) {
                    Box(
                        modifier = Modifier.weight(0.5f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(1.5.dp)
                                .background(
                                    // v0.68.18（Bob）：Divider色太接近背景看不见，改深色低透明
                                    AILauncherColors.Title.copy(alpha = 0.25f),
                                    CircleShape
                                )
                        )
                    }
                    return@forEachIndexed
                }
                val d = if (displayIndex >= 0) (i - displayIndex).toFloat() else 999f
                // v0.25.7：波浪更宽更平（sigma 4.5），头尾字母也有弧度，更优美
                // v0.40.2：tween 120→80，波浪响应更快更轻盈
                val gTarget = if (displayIndex >= 0) exp(-(d * d) / 40.5f) else 0f
                val g by animateFloatAsState(
                    targetValue = gTarget,
                    animationSpec = tween(80),
                    label = "waveG"
                )
                // v0.67.13：脉冲项额外放大
                val pulse = if (item == pulseItem && pulseTick > 0) pulseScale.value else 1f
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // v0.67.13：房子/收藏/隐藏图标，同样参与波浪
                    if (item is RailItem.Letter) {
                        Text(
                            text = item.char.toString(),
                            // v0.41.0（B 方案）：更细更淡
                            // v0.41.2：提示灰在透壁纸上对比度不够（Bob：看不清）→
                            // 深色 60%，字重保持常规，波浪交互不变
                            // v0.55.3（Bob）：G/S 底部被截断——显式 lineHeight 给足字高
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = AILauncherColors.Title.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                // 高斯波浪：越靠近手指越大；波浪整体再往拇指反方向避让
                                translationX = dirSign * bulgePx * g + waveShiftX * g
                                // v0.68.14（Bob）：纵向散开，更灵动不拥挤
                                translationY = d * spreadPx * g
                                val sc = (1f + g) * pulse
                                scaleX = sc
                                scaleY = sc
                            }
                        )
                    } else {
                        // v0.68.6：精致版 HouseGlyph（细描边+圆角+门洞）
                        val waveMod = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                translationX = dirSign * bulgePx * g + waveShiftX * g
                                // v0.68.14：纵向散开
                                translationY = d * spreadPx * g
                                val sc = (1f + g) * pulse
                                scaleX = sc
                                scaleY = sc
                            }
                        when (item) {
                            RailItem.House -> HouseGlyph(
                                color = AILauncherColors.Title.copy(alpha = 0.6f),
                                modifier = waveMod
                            )
                            // v0.68.8：线条风，与房子同款描边
                            RailItem.Star -> StarGlyph(
                                color = AILauncherColors.Title.copy(alpha = 0.6f),
                                modifier = waveMod
                            )
                            RailItem.Eye -> EyeOffGlyph(
                                color = AILauncherColors.Title.copy(alpha = 0.6f),
                                modifier = waveMod
                            )
                            is RailItem.Letter -> StarGlyph(
                                color = AILauncherColors.Title.copy(alpha = 0.6f),
                                modifier = waveMod
                            )
                            is RailItem.Separator -> {}
                        }
                    }
                }
            }
        }
        } // if (visible)

        // 当前字母气泡：靠应用的一侧（远离 rail），跟随手指高度（隐形触发区不画）
        // v0.41.0（B 方案）：实心金圆 → 金环（2dp 描边 + 深色字母），更轻
        if (visible && displayIndex >= 0) {
            val idx = displayIndex.coerceIn(items.indices)
            val rPx = with(density) { 24.dp.toPx() }
            // v0.68.19：消散时金环淡出
            val bubbleRingAlpha = 1f - explodeProgress
            // v0.68.20：气泡跟随拉拽——房子上拉时上移，眼睛下拉时下移
            val pullThresholdPx = with(density) { 100.dp.toPx() }
            val bubblePullOffsetY = when (items.getOrNull(idx)) {
                is RailItem.House -> -housePull * pullThresholdPx
                is RailItem.Eye -> eyePull * pullThresholdPx
                else -> 0f
            }
            // 眼睛下拉时边框加粗，给"即将解锁"的视觉提示
            val bubbleBorderWidth = 2.dp + eyePull * 2.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            (dirSign * bubbleOutX).roundToInt(),
                            (railTopPx + idx * rowHpx + rowHpx / 2f - rPx - hPx / 2f +
                                bubblePullOffsetY).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .border(
                        bubbleBorderWidth,
                        AILauncherColors.Accent.copy(alpha = bubbleRingAlpha),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val bubbleItem = items[idx]
                if (bubbleItem is RailItem.Letter) {
                    Text(
                        text = bubbleItem.char.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.Title
                    )
                } else {
                    // v0.68.6：精致版 HouseGlyph（细描边+圆角+门洞）
                    when (bubbleItem) {
                        RailItem.House -> {
                            // v0.68.20（Bob）：手势驱动——上拉 1x→2.5x；消散 2.5x→8x 同时淡出
                            val pullScale = 1f + housePull * 1.5f
                            val dissolveScale = 2.5f + explodeProgress * 5.5f
                            val houseScale =
                                if (explodeProgress > 0f) dissolveScale else pullScale
                            val houseAlpha = 1f - explodeProgress * explodeProgress
                            HouseGlyph(
                                color = AILauncherColors.Title,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = houseScale
                                        scaleY = houseScale
                                        alpha = houseAlpha
                                    }
                            )
                        }
                        // v0.68.8：线条风，与房子同款描边
                        RailItem.Star -> StarGlyph(
                            color = AILauncherColors.Title,
                            modifier = Modifier.size(24.dp)
                        )
                        RailItem.Eye -> {
                            // v0.68.20：下拉 1x→2x，配合震动给解锁反馈
                            val eyeScale = 1f + eyePull * 1f
                            EyeOffGlyph(
                                color = AILauncherColors.Title,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = eyeScale
                                        scaleY = eyeScale
                                    }
                            )
                        }
                        is RailItem.Letter -> StarGlyph(
                            color = AILauncherColors.Title,
                            modifier = Modifier.size(24.dp)
                        )
                        is RailItem.Separator -> {}
                    }
                }
            }
        }
    }
}

/**
 * v0.67.12（Bob）：共享 rail——单一样式单布局，应用中心和主屏 picker 共用，避免两套错位。
 * v0.67.13：房子/收藏/隐藏直接做进 WaveRail 的滑动项（RailItem），不再用外部 slot。
 */
@Composable
internal fun AppRail(
    items: List<RailItem>,
    onActiveItem: (RailItem) -> Unit,
    onRelease: () -> Unit,
    forcedActiveIndex: Int? = null,
    pulseTick: Int = 0,
    pulseItem: RailItem? = null,
    housePull: Float = 0f,
    eyePull: Float = 0f,
    onHousePull: (Float) -> Unit = {},
    onEyePull: (Float) -> Unit = {},
    explodeProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    WaveRail(
        items = items,
        onActiveItem = onActiveItem,
        onRelease = onRelease,
        forcedActiveIndex = forcedActiveIndex,
        pulseTick = pulseTick,
        pulseItem = pulseItem,
        housePull = housePull,
        eyePull = eyePull,
        onHousePull = onHousePull,
        onEyePull = onEyePull,
        explodeProgress = explodeProgress,
        modifier = modifier.height(400.dp)
    )
}

/**
 * v0.68.8（Bob）：线条风星星——与房子同款细描边+圆角，和字母统一。
 */
@Composable
private fun StarGlyph(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        val cx = w * 0.5f
        val cy = h * 0.52f
        val outerR = w * 0.42f
        val innerR = w * 0.175f
        val path = Path().apply {
            for (i in 0..9) {
                val angleRad = (-90f + i * 36f) * PI / 180.0
                val r = if (i % 2 == 0) outerR else innerR
                val x = cx + (r * cos(angleRad)).toFloat()
                val y = cy + (r * sin(angleRad)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * v0.68.8（Bob）：线条风眼睛（隐藏）——椭圆+瞳孔+斜线，与房子同款描边。
 */
@Composable
private fun EyeOffGlyph(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        // 眼睛轮廓
        drawOval(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.30f),
            size = Size(w * 0.84f, h * 0.40f),
            style = style
        )
        // 瞳孔
        drawCircle(
            color = color,
            radius = w * 0.085f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = style
        )
        // 斜线（表示隐藏）
        drawLine(
            color = color,
            start = Offset(w * 0.16f, h * 0.80f),
            end = Offset(w * 0.84f, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * v0.68.6（Bob）：精致版房子——细描边+圆角+门洞，告别实心丑房子。
 */
@Composable
fun HouseGlyph(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        val roofY = h * 0.44f
        val peakY = h * 0.14f
        val bodyBottom = h * 0.88f
        val path = Path().apply {
            // 屋顶：两条斜线在顶部圆角相接
            moveTo(w * 0.10f, roofY)
            lineTo(w * 0.50f, peakY)
            lineTo(w * 0.90f, roofY)
            // 房体左右竖线
            moveTo(w * 0.26f, roofY)
            lineTo(w * 0.26f, bodyBottom)
            moveTo(w * 0.74f, roofY)
            lineTo(w * 0.74f, bodyBottom)
            // 房体底线
            moveTo(w * 0.26f, bodyBottom)
            lineTo(w * 0.74f, bodyBottom)
            // 门：居中竖线
            moveTo(w * 0.50f, h * 0.64f)
            lineTo(w * 0.50f, bodyBottom)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
