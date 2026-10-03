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
 * 波浪字母导航（v0.24，学 Niagara / Bob 发的参考视频）。
 * - 手指按下/拖动时，附近字母按高斯衰减放大（sigma≈2.8 个字母）并向屏幕内侧鼓起，
 *   整个波浪再往拇指反方向避让约 64dp，不被拇指盖住
 * - 大气泡显示当前字母，跟随手指高度
 * - 点按：回调 onActiveLetter；拖动：字母变化时回调 onActiveLetter（列表跟手滚动切换）；
 *   松手：回调 onRelease
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
    // 右手：-1（往左偏/向内展开）；左手：+1（往右偏/向内展开）
    val dirSign = if (handed == UiPrefs.Handed.RIGHT) -1f else 1f
    var activeIndex by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(48.dp)) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f || letters.isEmpty()) return@BoxWithConstraints
        val rowHpx = hPx / letters.size
        val bulgePx = with(density) { 40.dp.toPx() }
        val shiftPx = with(density) { 64.dp.toPx() }
        // 波浪避让：手指按住时整个波浪往拇指反方向偏移，spring 跟手
        val waveShiftX by animateFloatAsState(
            targetValue = if (activeIndex >= 0) dirSign * shiftPx else 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = 0.85f
            ),
            label = "waveShift"
        )
        // 触摸层：整块可触摸（含点按与纵向拖动），不偏移
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(letters, hPx) {
                    detectTapGestures(onTap = { offset ->
                        val i = ((offset.y / hPx) * letters.size)
                            .toInt().coerceIn(letters.indices)
                        onActiveLetter(letters[i])
                    })
                }
                .pointerInput(letters, hPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val i = ((offset.y / hPx) * letters.size)
                                .toInt().coerceIn(letters.indices)
                            activeIndex = i
                            onActiveLetter(letters[i])
                        },
                        onDragEnd = { activeIndex = -1; onRelease() },
                        onDragCancel = { activeIndex = -1; onRelease() },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val i = ((change.position.y / hPx) * letters.size)
                                .toInt().coerceIn(letters.indices)
                            if (i != activeIndex) {
                                activeIndex = i
                                onActiveLetter(letters[i])
                            }
                        }
                    )
                }
        )
        // 视觉层：字母列
        Column(
            modifier = Modifier
                .align(if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd else Alignment.CenterStart)
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEachIndexed { i, ch ->
                val d = if (activeIndex >= 0) (i - activeIndex).toFloat() else 999f
                val gTarget = if (activeIndex >= 0) exp(-(d * d) / 15.68f) else 0f
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
        // 当前字母气泡：拇指反方向，随波浪一起偏移，不被拇指盖住
        if (activeIndex >= 0) {
            val idx = activeIndex.coerceIn(letters.indices)
            val rPx = with(density) { 22.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(
                        if (handed == UiPrefs.Handed.RIGHT) Alignment.TopStart
                        else Alignment.TopEnd
                    )
                    .offset {
                        IntOffset(
                            waveShiftX.roundToInt(),
                            (idx * rowHpx + rowHpx / 2f - rPx).roundToInt()
                        )
                    }
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AILauncherColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letters[idx].toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
