@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 未读组左滑清理行（Bob 2026-11-08 08:02 反馈①：未读组里的行支持左滑清理）。
 * 竖屏 AppCenter 与横屏 AppCenterLandscape 共用同一组件、同一手势。
 *
 * 行为：
 * - 左滑（EndToStart）→ 松手时若超过阈值，onClear（取消该应用的在用通知，
 *   走通知监听服务），行弹回原位（不真正 dismiss——行靠角标归 0 离开未读组；
 *   若角标未归 0 如短信库口径，行留在原位、角标已更新）。
 * - 中央图标（“已读”：信封 + 对勾）只是视觉指示，不可点；清理只走“松手过阈值”这一条路。
 *   点图标/点蒙层空白处 → 原有浮层行为（松手后弹簧收起），不触发清理。
 * - enabled=false（该应用无在用通知可取消，如短信库口径、飞书无通知）时，
 *   手势关闭、蒙层不画，行只剩点按/长按；用户去应用里读完自然清。
 * - 右滑不启用（只清通知，不做别的）。
 * - 修正诊断（Bob 2026-11-08 23:20 澄清）：第②点不是点图标——Bob 点的是未读
 *   组里的行（打开应用），打开后消息没被清除。行点按原来只走普通开应用路径
 *   （getLaunchIntentForPackage + startActivity），不取消通知：通知仍在栏里，
 *   角标里通知派生那几路（number 最大值、在用通知条数）还算着，行就还留在
 *   未读组。修法在调用方（AppCenter 竖屏未读段 + 横屏未读行）：未读行点按改
 *   成打开 + 清除（像点通知栏通知一样），成功打开且有在用通知时走左滑清理
 *   同一条取消路径。本文件只换图标、不改按钮：图标继续只是视觉指示，不可点。
 *   （此前按 23:04 字面理解给图标加过按钮，已随本次修正撤掉，见收据。）
 *
 * 实现（2026-11-08 应用中心崩溃 hotfix）：不用 Material3 SwipeToDismissBox——
 * 它在 LazyColumn 首帧布局时可崩（Bob 2026-11-08 08:46 报进应用中心就崩，
 * 458 里它是应用中心入口路径上唯一未经验证的新 API；另 confirmValueChange
 * 对 Settled 也返回 false，会否决弹回原位的 settle）。改用 AppRow 已在用的
 * detectHorizontalDragGestures（本仓库验证过的横滑机制）+ animateFloatAsState
 * （拖动时 snap 跟手，松手 spring 弹回），与行内 ★/眼睛浮层同一套手势语言。
 *
 * 样式（465，Bob 2026-11-08 19:29：沿用字母组行收藏/隐藏浮层设计）：
 * 内容不位移、弱化到 30%；蒙层整行覆盖、Card 色 80% 透明、20dp 圆角；
 * 中央一个“已读”线条图标（MarkEmailRead：信封 + 对勾，26dp、48dp 触控框、
 * 图标形投影），无红色底、无“清除”文字。两段阈值与 AppRow 同款：56dp 现形、
 * 120dp 到底主按钮脉冲、松手触发清理。
 * 图标含义（Bob 2026-11-08 23:04：垃圾桶不准；23:31 定：换成“已读”）：
 * 未读组用的就是未读信封，清除图标用信封 + 对勾的“已读”，意思就是“这条
 * 已读、清除掉”；这个动作是取消该应用的在用通知（从通知栏清除），不是
 * 删除数据，垃圾桶会误导。
 *
 * 手势隔离：行内 AppRow 的滑动浮层（★/眼睛）由调用方传 swipeActionsEnabled=false
 * 关掉，避免两套横滑抢事件；竖屏列表竖滑与右侧 rail 不受影响（横滑只消费横向拖动）。
 * 横屏注意：列是横向滚动的 LazyRow，左滑与列横滚同向——从未读行起手左滑会先触发
 * 清理（行内手势优先），要横滚列请从其他行或空白处起手（见收据）。
 */

/** 左滑现形阈值（dp）：拖到这里蒙层完全现形、内容弱化到 30%（与 AppRow 同款 56dp）。 */
internal const val UNREAD_SWIPE_REVEAL_DP = 56f

/** 左滑触发清理的阈值（dp）：松手时超过这里触发清理（与 AppRow 到底 120dp 同款）。 */
internal const val UNREAD_SWIPE_CLEAR_THRESHOLD_DP = 120f

/** 左滑最大位移（dp，与 AppRow 同款 160dp）。 */
internal const val UNREAD_SWIPE_MAX_DP = 160f

/** 左滑位移钳制（纯函数）：只许向左（<=0），不超过最大位移。 */
fun coerceSwipeOffset(offsetPx: Float, maxSwipePx: Float): Float =
    offsetPx.coerceIn(-maxSwipePx, 0f)

/** 左滑进度（纯函数）：0=没滑，1=达到阈值；供蒙层/内容弱化进度（调用方传现形阈值）。 */
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
    val revealPx = with(density) { UNREAD_SWIPE_REVEAL_DP.dp.toPx() }
    val thresholdPx = with(density) { UNREAD_SWIPE_CLEAR_THRESHOLD_DP.dp.toPx() }
    val maxSwipePx = with(density) { UNREAD_SWIPE_MAX_DP.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    // 拖动时 snap 跟手（1:1），松手 spring 弹回原位。
    val animatedOffset by animateFloatAsState(
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
    // 弱化进度：56dp 现形；内容最低 30%（与 AppRow 同款）。
    val dimProgress = swipeClearProgress(animatedOffset, revealPx)
    val contentAlpha = 1f - dimProgress * 0.7f
    // 武装：拖过 120dp，图标脉冲（与 AppRow 主按钮脉冲同款）。
    val armed = isDragging && -dragOffset > thresholdPx
    val pulseTransition = rememberInfiniteTransition(label = "unreadClearPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val iconScale = (0.6f + 0.4f * dimProgress) * if (armed) pulseScale else 1f

    Box(modifier = modifier) {
        // 内容：不位移，弱化到 30%（与 AppRow 同款）。
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
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
        // 蒙层：整行覆盖、Card 色 80% 透明、20dp 圆角（与 AppRow 同款）；
        // 中央一个“已读”线条图标（信封 + 对勾，视觉指示，不可点），无红色底、无“清除”文字。
        // 清理只走“松手过阈值”这一条路；点图标/点空白按原有浮层行为收起，
        // 不触发清理。点按落到行点按（打开应用）是未读行本来的行为，打开 +
        // 清除见 AppCenter 未读段（竖屏）与横屏未读行。
        if (enabled && dimProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AILauncherColors.current.Card.copy(alpha = 0.8f))
                    .graphicsLayer { alpha = dimProgress },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 图标形投影：深色副本下移 2dp（与 AppRow 同款）。
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.16f),
                            modifier = Modifier
                                .size(26.dp)
                                .offset(y = 2.dp)
                                .semantics { invisibleToUser() }
                        )
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = "清除通知",
                            tint = Color(0xFF5A6C7D),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
