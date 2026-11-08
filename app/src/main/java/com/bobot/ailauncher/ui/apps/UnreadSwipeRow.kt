package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import kotlin.math.roundToInt

/**
 * 未读组左滑清理行（Bob 2026-11-08 08:02 反馈①：未读组里的行支持左滑清理）。
 * 竖屏 AppCenter 与横屏 AppCenterLandscape 共用同一组件、同一手势。
 *
 * 行为：
 * - 左滑（EndToStart）→ 松手时若超过阈值，onClear（取消该应用的在用通知，
 *   走通知监听服务），行弹回原位（不真正 dismiss——行靠角标归 0 离开未读组；
 *   若角标未归 0 如短信库口径，行留在原位、角标已更新）。
 * - enabled=false（该应用无在用通知可取消，如短信库口径、飞书无通知）时，
 *   手势关闭、底色不画，行只剩点按/长按；用户去应用里读完自然清。
 * - 右滑不启用（只清通知，不做别的）。
 *
 * 实现（2026-11-08 应用中心崩溃 hotfix）：不用 Material3 SwipeToDismissBox——
 * 它在 LazyColumn 首帧布局时可崩（Bob 2026-11-08 08:46 报进应用中心就崩，
 * 458 里它是应用中心入口路径上唯一未经验证的新 API；另 confirmValueChange
 * 对 Settled 也返回 false，会否决弹回原位的 settle）。改用 AppRow 已在用的
 * detectHorizontalDragGestures（本仓库验证过的横滑机制）+ animateFloatAsState
 * （拖动时 snap 跟手，松手 spring 弹回），与行内 ★/眼睛浮层同一套手势语言。
 *
 * 手势隔离：行内 AppRow 的滑动浮层（★/眼睛）由调用方传 swipeActionsEnabled=false
 * 关掉，避免两套横滑抢事件；竖屏列表竖滑与右侧 rail 不受影响（横滑只消费横向拖动）。
 * 横屏注意：列是横向滚动的 LazyRow，左滑与列横滚同向——从未读行起手左滑会先触发
 * 清理（行内手势优先），要横滚列请从其他行或空白处起手（见收据）。
 */

/** 左滑触发清理的阈值（dp，纯函数口径，供单测）。 */
internal const val UNREAD_SWIPE_CLEAR_THRESHOLD_DP = 72f

/** 左滑最大位移（dp，纯函数口径，供单测）。 */
internal const val UNREAD_SWIPE_MAX_DP = 120f

/** 左滑位移钳制（纯函数）：只许向左（<=0），不超过最大位移。 */
fun coerceSwipeOffset(offsetPx: Float, maxSwipePx: Float): Float =
    offsetPx.coerceIn(-maxSwipePx, 0f)

/** 左滑进度（纯函数）：0=没滑，1=达到清理阈值；供底色透明度。 */
fun swipeClearProgress(offsetPx: Float, thresholdPx: Float): Float =
    // 静止时 -offsetPx 得 -0f，-0f/阈值仍是 -0f；coerceIn 的原始比较把 -0f
    // 当成 0f 不钳制，会原样返回 -0f。末尾 +0f 把 -0f 归一化成 0f
    //（IEEE：-0+0=+0），避免下游把 -0f 当成与 0f 不同的值
    //（Float.equals 按位比较区分 -0f/0f）。其余值 +0f 不变。
    (-offsetPx / thresholdPx).coerceIn(0f, 1f) + 0f

/** 松手时是否触发清理（纯函数）：左滑超过阈值。 */
fun shouldClearOnRelease(offsetPx: Float, thresholdPx: Float): Boolean =
    offsetPx < -thresholdPx

@Composable
internal fun ClearableUnreadRow(
    enabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { UNREAD_SWIPE_CLEAR_THRESHOLD_DP.dp.toPx() }
    val maxSwipePx = with(density) { UNREAD_SWIPE_MAX_DP.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    // 拖动时 snap 跟手（1:1），松手 spring 弹回原位。
    val offsetX by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "unreadSwipeOffset"
    )
    // 关掉手势时复位（行离开未读组/失去通知时不留残影）。
    LaunchedEffect(enabled) {
        if (!enabled) dragOffset = 0f
    }

    Box(modifier = modifier) {
        // 红色“清除”底：随左滑进度淡入，只在启用时画。
        if (enabled) {
            val progress = swipeClearProgress(offsetX, thresholdPx)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(progress)
                    .background(
                        color = AILauncherColors.current.Alert,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "清除通知",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "清除",
                        fontSize = AILauncherType.Body.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (shouldClearOnRelease(dragOffset, thresholdPx)) onClear()
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffset = coerceSwipeOffset(dragOffset + dragAmount, maxSwipePx)
                    }
                }
        ) {
            content()
        }
    }
}
