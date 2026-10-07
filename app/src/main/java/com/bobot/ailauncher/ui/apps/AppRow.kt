package com.bobot.ailauncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.roundToInt

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

    // v0.41.27（Bob 方案 B）：iOS 式——内容整体滑动，下面露出动作按钮。
    // 左滑内容左移露出右侧 ★，右滑内容右移露出左侧眼睛。按钮在下层，无透明度变化。
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) } // 松手后动画用（px），左移为负
    var dragOffsetX by remember { mutableStateOf(0f) } // 拖动时直接设置，无协程竞态
    var dragDist by remember { mutableStateOf(0f) } // 左滑为正
    var isDragging by remember { mutableStateOf(false) }
    // 显示用偏移：拖动时直接跟手，否则用动画值
    val displayOffset = if (isDragging) dragOffsetX else offsetX.value
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    val settleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun hideAll() {
        dragDist = 0f
        dragOffsetX = 0f
        onActionsVisibleChange(false)
        onHideVisibleChange(false)
        scope.launch { offsetX.snapTo(0f) }
    }

    // 按钮方向：拖动时按手指方向，否则按保持状态（两者互斥，AppCenter 保证）
    val buttonIsLeft = when {
        isDragging -> dragDist > 0
        actionsVisible -> true
        hideVisible -> false
        else -> true
    }

    // 外部收起（如另一行展开）时，内容直接回位（不用动画，避免与按钮消失的时机错位）
    LaunchedEffect(actionsVisible, hideVisible) {
        if (!actionsVisible && !hideVisible) {
            dragDist = 0f
            dragOffsetX = 0f
            offsetX.snapTo(0f)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // v0.41.27（Bob 方案 B）：底层动作按钮——位置固定，无透明度变化。
            // 左滑 ★ 在右侧，右滑眼睛在左侧；内容滑开后露出。
            if (dragDist != 0f || actionsVisible || hideVisible) {
                // ★ 双状态：亮金=加入 Dock，灰=已在 Dock（点按移出）
                // 眼睛双状态：闭眼=隐藏，开眼=取消隐藏
                // v0.41.28（Bob）：对比度提高——深金/深灰/深 slate，白色图标更醒目
                val btnBg = when {
                    buttonIsLeft && isPinned -> Color(0xFF5A564E)
                    buttonIsLeft -> Color(0xFF8C6D1F)
                    else -> Color(0xFF2F3A4A)
                }
                val btnIcon = when {
                    buttonIsLeft -> Icons.Filled.Star
                    onUnhide != null -> Icons.Filled.Visibility
                    else -> Icons.Filled.VisibilityOff
                }
                val btnDesc = when {
                    buttonIsLeft -> if (isPinned) "移出 Dock" else "加到 Dock"
                    onUnhide != null -> "取消隐藏"
                    else -> "隐藏"
                }
                Box(
                    modifier = Modifier
                        .align(if (buttonIsLeft) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(
                            end = if (buttonIsLeft) 8.dp else 0.dp,
                            start = if (buttonIsLeft) 0.dp else 8.dp
                        )
                        .size(48.dp)
                        .shadow(8.dp, CircleShape)
                        .background(btnBg, CircleShape)
                        .clickable(
                            onClick = {
                                when {
                                    buttonIsLeft -> togglePin()
                                    onUnhide != null -> onUnhide(app)
                                    else -> onHide(app)
                                }
                                hideAll()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = btnIcon,
                        contentDescription = btnDesc,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 上层：内容 Row——跟手指整体滑动（左滑左移露出右侧 ★，右滑右移露出左侧眼睛）。
            // 不透明背景盖住底层按钮，无透明度变化。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(displayOffset.roundToInt(), 0) }
                    .pointerInput(app.packageName) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragCancel = {
                                isDragging = false
                                dragDist = 0f
                                dragOffsetX = 0f
                                scope.launch { offsetX.animateTo(0f, settleSpring) }
                            },
                            onDragEnd = {
                                isDragging = false
                                when {
                                    dragDist > fullPx -> {
                                        // 左滑满 → 直接加到 Dock（或移出）
                                        togglePin()
                                        hideAll()
                                    }
                                    dragDist < -fullPx -> {
                                        // 右滑满 → 直接隐藏（或取消隐藏）
                                        if (onUnhide != null) onUnhide(app) else onHide(app)
                                        hideAll()
                                    }
                                    dragDist > revealPx -> {
                                        // 左滑=多：内容停在 -revealPx，★ 保持露出
                                        val start = dragOffsetX
                                        dragDist = 0f
                                        dragOffsetX = 0f
                                        onActionsVisibleChange(true)
                                        scope.launch {
                                            offsetX.snapTo(start)
                                            offsetX.animateTo(-revealPx, settleSpring)
                                        }
                                    }
                                    dragDist < -revealPx -> {
                                        // 右滑=少：内容停在 +revealPx，眼睛保持露出
                                        val start = dragOffsetX
                                        dragDist = 0f
                                        dragOffsetX = 0f
                                        onActionsVisibleChange(false)
                                        onHideVisibleChange(true)
                                        scope.launch {
                                            offsetX.snapTo(start)
                                            offsetX.animateTo(revealPx, settleSpring)
                                        }
                                    }
                                    else -> {
                                        val start = dragOffsetX
                                        dragDist = 0f
                                        dragOffsetX = 0f
                                        scope.launch {
                                            offsetX.snapTo(start)
                                            offsetX.animateTo(0f, settleSpring)
                                        }
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                // 左滑 dragAmount 为负，转成正数距离；内容向左移（offset 为负）
                                dragDist = (dragDist - dragAmount)
                                    .coerceIn(-maxDragPx, maxDragPx)
                                dragOffsetX = -dragDist // 直接设置 state，无协程竞态
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = {
                            // 动作按钮露出时点行 = 收起复原；否则启动应用
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
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = app.label.toString(),
                    fontSize = 15.sp,
                    color = AILauncherColors.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
