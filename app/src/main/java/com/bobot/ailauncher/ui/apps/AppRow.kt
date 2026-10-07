package com.bobot.ailauncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material.icons.outlined.Visibility as OutlinedVisibility
import androidx.compose.material.icons.outlined.VisibilityOff as OutlinedVisibilityOff
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 应用行（从已删除的 AllAppsScreen.kt 迁移，v0.22 分类取消后去掉"移到分组"）。
 * 左滑=更多（快捷操作），右滑=更少（隐藏，需确认）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: AppInfo,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    onHide: (AppInfo) -> Unit,
    actionsVisible: Boolean,
    onActionsVisibleChange: (Boolean) -> Unit,
    // v0.41.20（Bob）：右滑=隐藏或取消隐藏——隐藏分组内的应用传 onUnhide，
    // 普通应用 onUnhide 为 null，右滑=隐藏。
    onUnhide: ((AppInfo) -> Unit)? = null,
    // v0.41.23（Bob v4 统一版）：右滑眼睛的保持状态（与 actionsVisible 互斥，列表级管理）
    hideVisible: Boolean = false,
    onHideVisibleChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // v0.41.11：openAppDetails/uninstallApp 已移到长按菜单（AppPopupMenu），此处删除

    // v0.35.0：Dock 置顶切换——已置顶则移出，未置顶则追加到末尾（Dock 里按此顺序排最前）
    val dockTick by UiPrefs.dockTick.collectAsState()
    val isPinned = remember(dockTick) { UiPrefs.getDockPinned(context).contains(app.packageName) }

    fun togglePin() {
        val cur = UiPrefs.getDockPinned(context).toMutableList()
        if (app.packageName in cur) {
            cur.remove(app.packageName)
            UiPrefs.setDockPinned(context, cur)
            Toast.makeText(context, "已从 Dock 移出", Toast.LENGTH_SHORT).show()
        } else {
            if (cur.size >= 10) {
                Toast.makeText(context, "Dock 最多置顶 10 个", Toast.LENGTH_SHORT).show()
                return
            }
            cur.add(app.packageName)
            UiPrefs.setDockPinned(context, cur)
            Toast.makeText(context, "已加到 Dock", Toast.LENGTH_SHORT).show()
        }
    }

    // v0.41.30（Bob 统一双钮版）：左右滑同一效果——内容（图标+标题）弱化到 30%，位置不动；
    // 蒙层中央并排双钮：眼睛（隐藏/取消隐藏）+ ★（收藏/取消收藏），简洁线条图标无底色，居中。
    // 第一段（>56dp 松手）保持蒙层可点；第二段（>120dp）主按钮脉冲，松手直接触发。
    // 左滑到底=★，右滑到底=眼睛。
    val scope = rememberCoroutineScope()
    val dimAnim = remember { Animatable(0f) } // 弱化进度 0→1
    var dragDist by remember { mutableStateOf(0f) } // 左滑为正，右滑为负
    var isDragging by remember { mutableStateOf(false) }
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    val settleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 弱化进度：拖动时跟手，松手后弹簧动画到目标
    val dragDim = (kotlin.math.abs(dragDist) / revealPx).coerceIn(0f, 1f)
    val dimProgress = if (isDragging) dragDim else dimAnim.value
    val contentAlpha = 1f - dimProgress * 0.7f // 最低 30%

    // 第二段武装：左滑到底=★，右滑到底=眼睛；主按钮放大脉冲
    val armedPrimary: String? = when {
        !isDragging -> null
        dragDist > fullPx -> "star"
        dragDist < -fullPx -> "eye"
        else -> null
    }
    val starScale by animateFloatAsState(
        targetValue = if (armedPrimary == "star") 1.3f else 1f,
        animationSpec = settleSpring, label = "starPulse"
    )
    val eyeScale by animateFloatAsState(
        targetValue = if (armedPrimary == "eye") 1.3f else 1f,
        animationSpec = settleSpring, label = "eyePulse"
    )

    fun hideAll() {
        dragDist = 0f
        onActionsVisibleChange(false)
        onHideVisibleChange(false)
        scope.launch { dimAnim.animateTo(0f, settleSpring) }
    }

    fun settleDim(from: Float, to: Float) {
        scope.launch {
            dimAnim.snapTo(from)
            dimAnim.animateTo(to, settleSpring)
        }
    }

    // 外部收起（如另一行展开）时直接复原
    LaunchedEffect(actionsVisible, hideVisible) {
        if (!actionsVisible && !hideVisible) {
            dragDist = 0f
            dimAnim.snapTo(0f)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(app.packageName) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragCancel = {
                            isDragging = false
                            val from = dragDim
                            dragDist = 0f
                            settleDim(from, 0f)
                        },
                        onDragEnd = {
                            isDragging = false
                            val from = dragDim
                            when {
                                dragDist > fullPx -> {
                                    // 左滑到底 → ★（收藏/取消收藏）
                                    togglePin()
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                                dragDist < -fullPx -> {
                                    // 右滑到底 → 眼睛（隐藏/取消隐藏）
                                    if (onUnhide != null) onUnhide(app) else onHide(app)
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                                dragDist > revealPx || dragDist < -revealPx -> {
                                    // 第一段 → 保持蒙层+双钮（方向无关，保持标记互斥由 AppCenter 保证）
                                    dragDist = 0f
                                    onActionsVisibleChange(true)
                                    settleDim(from, 1f)
                                }
                                else -> {
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // 左滑 dragAmount 为负，转成正数距离
                            dragDist = (dragDist - dragAmount)
                                .coerceIn(-maxDragPx, maxDragPx)
                        }
                    )
                }
        ) {
            // 内容：静态不位移，弱化到 30%
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (actionsVisible || hideVisible) hideAll()
                            else onLaunch()
                        },
                        onLongClick = onLongClick
                    )
                    // v0.41.0（B 方案）：行距拉大——纵向 padding 12dp，图标 40dp，呼吸感
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    drawable = app.icon,
                    contentDescription = app.label.toString(),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .graphicsLayer { alpha = contentAlpha }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = app.label.toString(),
                    fontSize = 15.sp,
                    color = AILauncherColors.Title.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 蒙层：透明全行覆盖，中央并排双钮；点空白处复原
            if (dimProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { hideAll() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 眼睛：隐藏/取消隐藏（简洁线条，无底色）
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = eyeScale
                                    scaleY = eyeScale
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (onUnhide != null) onUnhide(app) else onHide(app)
                                        hideAll()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (onUnhide != null) OutlinedVisibility else OutlinedVisibilityOff,
                                contentDescription = if (onUnhide != null) "取消隐藏" else "隐藏",
                                tint = Color(0xFF5A6C7D),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        // ★：收藏/取消收藏（简洁线条无底色；已在 Dock 用实心金）
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        togglePin()
                                        hideAll()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.Star else OutlinedStar,
                                contentDescription = if (isPinned) "移出 Dock" else "加到 Dock",
                                tint = Color(0xFFC9A227),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // v0.41.27：方案 B——内容滑动露出底层按钮；右滑隐藏确认框已删除，点眼睛直接隐藏
}

/** 分组 key：a-z/A-Z→大写；中文→拼音首字母大写（GB2312 区位边界法，无需第三方库）；数字及其他→'#' */
internal fun groupKey(label: CharSequence, pinyinInitialOf: (Char) -> Char): Char {
    val c = label.firstOrNull() ?: return '#'
    if (c in 'a'..'z') return c.uppercaseChar()
    if (c in 'A'..'Z') return c
    if (c in '0'..'9') return '#'
    return pinyinInitialOf(c)
}

/** 汉字拼音首字母：GB2312 编码区位与拼音首字母边界对照（i/u/v 不做声母，23 个字母） */
internal fun pinyinInitial(c: Char): Char {
    return try {
        val bytes = c.toString().toByteArray(charset("GB2312"))
        if (bytes.size < 2) return '#'
        val secPos = (bytes[0].toInt() and 0xFF) * 100 + (bytes[1].toInt() and 0xFF) - 16160
        // 上式 = ((b0-160)*100 + (b1-160))，即区位码
        val bounds = intArrayOf(
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
            3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
            4390, 4558, 4684, 4925, 5249, 5590
        )
        val letters = "abcdefghjklmnopqrstwxyz"
        for (i in bounds.indices.reversed()) {
            if (secPos >= bounds[i]) return letters[i].uppercaseChar()
        }
        '#'
    } catch (_: Exception) {
        '#'
    }
}
