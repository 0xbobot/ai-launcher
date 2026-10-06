package com.bobot.ailauncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    // v0.41.23（Bob v4 统一版）：图标原地交叉溶解成动作图标，标题淡出，位置不动。
    // 左滑=★（金），右滑=眼睛（灰）。溶解进度跟手指走。
    var dragDist by remember { mutableStateOf(0f) } // 左滑为正
    // v0.41.25（Bob）：过渡动画——拖动时 1:1 跟手，松手后弹簧动画到目标
    var isDragging by remember { mutableStateOf(false) }
    val dragProgress = (kotlin.math.abs(dragDist) / revealPx).coerceIn(0f, 1f)
    val settleTarget = if (actionsVisible || hideVisible) 1f else 0f
    val settleProgress by animateFloatAsState(
        targetValue = settleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dissolveSettle"
    )
    // 拖动中直接跟手；松手后用弹簧动画
    val baseProgress = if (isDragging) dragProgress else settleProgress
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }

    fun hideAll() {
        dragDist = 0f
        onActionsVisibleChange(false)
        onHideVisibleChange(false)
    }

    // 溶解进度：正在拖时按拖动方向（优先于保持状态），否则按保持状态
    // v0.41.24（Bob）：修 bug——左滑保持 ★ 后再右滑，眼睛出不来
    // （原来 actionsVisible 优先，导致拖右滑时仍显示 ★）
    // v0.41.25：baseProgress 已包含拖动/弹簧动画
    val isLeft = when {
        isDragging -> dragDist > 0
        actionsVisible -> true
        hideVisible -> false
        else -> true
    }
    val dissolveProgress = baseProgress

    // 外部收起（如另一行展开）时
    LaunchedEffect(actionsVisible, hideVisible) {
        if (!actionsVisible && !hideVisible) dragDist = 0f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // 内容：v0.41.23 统一版——位置不动，图标溶解、标题淡出
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(app.packageName) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragCancel = {
                                isDragging = false
                                dragDist = 0f
                            },
                            onDragEnd = {
                                isDragging = false
                                when {
                                    dragDist > fullPx -> {
                                        // 左滑满 → 直接加到 Dock（或移出）
                                        togglePin()
                                        hideAll()
                                    }
                                    dragDist > revealPx -> {
                                        // 左滑=多：★ 保持显示，点按触发
                                        dragDist = 0f
                                        onActionsVisibleChange(true)
                                    }
                                    dragDist < -revealPx -> {
                                        // 右滑=少：眼睛保持显示，点按隐藏/取消隐藏
                                        dragDist = 0f
                                        onHideVisibleChange(true)
                                    }
                                    else -> dragDist = 0f
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
                    .combinedClickable(
                        onClick = {
                            // 动作图标显示时点行 = 收起复原；否则启动应用
                            if (actionsVisible || hideVisible) hideAll()
                            else onLaunch()
                        },
                        onLongClick = onLongClick
                    )
                    // v0.41.0（B 方案）：行距拉大——纵向 padding 12dp，图标 40dp，呼吸感
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // v0.41.23 统一版：图标原地交叉溶解
                // 原图标淡出，动作图标（★/眼睛）淡入，位置不动
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconImage(
                        drawable = app.icon,
                        contentDescription = app.label.toString(),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer { alpha = 1f - dissolveProgress }
                    )
                    if (dissolveProgress > 0.01f) {
                        // v0.41.24（Bob）：★ 双状态——金底=点按加入 Dock，灰底=已在 Dock 点按移出；
                        // 眼睛双状态——闭眼=隐藏，开眼=取消隐藏（图标区分）
                        // v0.41.25（Bob）：颜色更显眼——★ 用亮金，眼睛用深灰蓝；
                        // 动作图标带缩放弹入动画
                        val actionBg = when {
                            isLeft && isPinned -> Color(0xFF8A8478)
                            isLeft -> Color(0xFFD4A017) // 更亮的金
                            else -> Color(0xFF5A6C7D) // 深灰蓝，隐藏更显眼
                        }
                        val actionIcon = when {
                            isLeft -> Icons.Filled.Star
                            onUnhide != null -> Icons.Filled.Visibility
                            else -> Icons.Filled.VisibilityOff
                        }
                        val actionDesc = when {
                            isLeft -> if (isPinned) "移出 Dock" else "加到 Dock"
                            onUnhide != null -> "取消隐藏"
                            else -> "隐藏"
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(actionBg)
                                .graphicsLayer {
                                    alpha = dissolveProgress
                                    // v0.41.25：弹入缩放（0.7→1.0）
                                    val s = 0.7f + dissolveProgress * 0.3f
                                    scaleX = s
                                    scaleY = s
                                }
                                .clickable(
                                    enabled = actionsVisible || hideVisible ||
                                        dissolveProgress > 0.5f,
                                    onClick = {
                                        when {
                                            isLeft -> togglePin()
                                            onUnhide != null -> onUnhide(app)
                                            else -> onHide(app)
                                        }
                                        hideAll()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionDesc,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                // v0.41.23：标题淡出到 15%，位置不动
                Text(
                    text = app.label.toString(),
                    fontSize = 15.sp,
                    color = AILauncherColors.Title.copy(
                        alpha = 1f - dissolveProgress * 0.85f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }

    // v0.41.17：右滑隐藏确认框已删除，改用左侧隐藏图标点按直接隐藏
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
