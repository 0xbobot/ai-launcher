package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 波浪字母导航 v2（v0.25）。
 * - 常显于屏幕右侧（修复 v0.24 藏入屏外看不见的问题）
 * - 紧凑高度（约 62% 屏高，居中），字母不拉得太开
 * - 拖动时：附近字母按高斯衰减放大并向内鼓起，大气泡在靠应用的一侧跟随手指；
 *   列表进入聚焦模式（只显示当前字母），松手恢复全量
 * - 只做定位，不承载其他功能
 */
@Composable
internal fun WaveRail(
    letters: List<Char>,
    onActiveLetter: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    handed: UiPrefs.Handed = UiPrefs.Handed.RIGHT
) {
    val density = LocalDensity.current
    // 右手：-1（往左/向内）；左手：+1（往右/向内）
    val dirSign = if (handed == UiPrefs.Handed.RIGHT) -1f else 1f
    var activeIndex by remember { mutableIntStateOf(-1) }

    // 字母列占容器高度的比例（紧凑）
    val railFraction = 0.62f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
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
        val waveShiftX by animateFloatAsState(
            targetValue = if (activeIndex >= 0) dirSign * shiftPx else 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = 0.85f
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
                        onActiveLetter(letters[i])
                        onRelease()
                    })
                }
                .pointerInput(letters, hPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val i = indexAt(offset.y)
                            activeIndex = i
                            onActiveLetter(letters[i])
                        },
                        onDragEnd = {
                            val last = activeIndex.takeIf { it >= 0 }
                                ?.let { letters[it] }
                            activeIndex = -1
                            onRelease()
                        },
                        onDragCancel = {
                            activeIndex = -1
                            onRelease()
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val i = indexAt(change.position.y)
                            if (i != activeIndex) {
                                activeIndex = i
                                onActiveLetter(letters[i])
                            }
                        }
                    )
                }
        )

        // 字母列：紧凑居中，常显
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .fillMaxHeight(railFraction),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEachIndexed { i, ch ->
                val d = if (activeIndex >= 0) (i - activeIndex).toFloat() else 999f
                // v0.25.7：波浪更宽更平（sigma 4.5），头尾字母也有弧度，更优美
                val gTarget = if (activeIndex >= 0) exp(-(d * d) / 40.5f) else 0f
                val g by animateFloatAsState(
                    targetValue = gTarget,
                    animationSpec = tween(120),
                    label = "waveG"
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.toString(),
                        fontSize = 11.sp,
                        color = if (g > 0.5f) AILauncherColors.Accent
                        else AILauncherColors.Title.copy(alpha = 0.85f),
                        fontWeight = if (g > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.White.copy(alpha = 0.6f),
                                offset = Offset(0f, 1f),
                                blurRadius = 2f
                            )
                        ),
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

        // 当前字母气泡：靠应用的一侧（远离 rail），跟随手指高度
        if (activeIndex >= 0) {
            val idx = activeIndex.coerceIn(letters.indices)
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
                    .clip(CircleShape)
                    .background(AILauncherColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letters[idx].toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
