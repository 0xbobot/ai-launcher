package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** v0.40.1：rail 所在的一侧（左右双 rail 常驻，不再需要设置里选惯用手） */
internal enum class RailSide { LEFT, RIGHT }

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
    letters: List<Char>,
    onActiveLetter: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    side: RailSide = RailSide.RIGHT,
    // v0.40.2：visible=false 时为左侧隐形触发区——只响应触摸，不绘制字母/气泡；
    // 触摸时通过 forcedActiveIndex 让右侧可见 rail 跟随显示波浪
    visible: Boolean = true,
    forcedActiveIndex: Int? = null,
    railWidth: Dp = 44.dp
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

    // v0.40.2：字母切换给一记结实的震动——TextHandleMove 在部分机型上几乎无感
    fun emitLetter(i: Int) {
        if (i == activeIndex) return
        activeIndex = i
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onActiveLetter(letters[i])
    }

    // 字母列占容器高度的比例（紧凑）
    val railFraction = 0.62f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
    ) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f || letters.isEmpty()) return@BoxWithConstraints
        val railHpx = hPx * railFraction
        val railTopPx = (hPx - railHpx) / 2f
        val rowHpx = railHpx / letters.size
        val bulgePx = with(density) { 36.dp.toPx() }
        val shiftPx = with(density) { 56.dp.toPx() }
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
            return (((y - railTopPx) / railHpx) * letters.size)
                .toInt().coerceIn(letters.indices)
        }

        // 触摸层：点按与纵向拖动
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(letters, hPx) {
                    detectTapGestures(onTap = { offset ->
                        // 点按：直接跳转
                        val i = indexAt(offset.y)
                        cancelPendingTapRelease()
                        activeIndex = -1 // 允许点按重复触发同一字母
                        emitLetter(i)
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
                .pointerInput(letters, hPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            // 点按后立刻转拖动：掐掉点按的延迟收波浪，拖动接管
                            cancelPendingTapRelease()
                            emitLetter(indexAt(offset.y))
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
                            emitLetter(indexAt(change.position.y))
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
            letters.forEachIndexed { i, ch ->
                val d = if (displayIndex >= 0) (i - displayIndex).toFloat() else 999f
                // v0.25.7：波浪更宽更平（sigma 4.5），头尾字母也有弧度，更优美
                // v0.40.2：tween 120→80，波浪响应更快更轻盈
                val gTarget = if (displayIndex >= 0) exp(-(d * d) / 40.5f) else 0f
                val g by animateFloatAsState(
                    targetValue = gTarget,
                    animationSpec = tween(80),
                    label = "waveG"
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.toString(),
                        // v0.41.0（B 方案）：更细更淡
                        // v0.41.2：提示灰在透壁纸上对比度不够（Bob：看不清）→
                        // 深色 60%，字重保持常规，波浪交互不变
                        fontSize = 11.sp,
                        color = AILauncherColors.Title.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            // 高斯波浪：越靠近手指越大；波浪整体再往拇指反方向避让
                            translationX = dirSign * bulgePx * g + waveShiftX * g
                            val sc = 1f + g
                            scaleX = sc
                            scaleY = sc
                        }
                    )
                }
            }
        }
        } // if (visible)

        // 当前字母气泡：靠应用的一侧（远离 rail），跟随手指高度（隐形触发区不画）
        // v0.41.0（B 方案）：实心金圆 → 金环（2dp 描边 + 深色字母），更轻
        if (visible && displayIndex >= 0) {
            val idx = displayIndex.coerceIn(letters.indices)
            val rPx = with(density) { 24.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            (dirSign * bubbleOutX).roundToInt(),
                            (railTopPx + idx * rowHpx + rowHpx / 2f - rPx - hPx / 2f).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .border(2.dp, AILauncherColors.Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letters[idx].toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title
                )
            }
        }
    }
}
