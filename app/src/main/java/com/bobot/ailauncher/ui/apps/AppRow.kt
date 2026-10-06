package com.bobot.ailauncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
    onActionsVisibleChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showHideConfirm by remember { mutableStateOf(false) }

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

    // v0.41.12（Bob）：左滑跟手动画——行内容跟手指移动，★ 在底层露出；
    // 滑满 120dp 时 ★ 放大提示"松手直接触发"，给明确的触觉反馈。
    val offsetX = remember { Animatable(0f) }
    val animScope = rememberCoroutineScope()
    val buttonWidthPx = with(density) { 72.dp.toPx() }
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxLeftPx = with(density) { 160.dp.toPx() }
    val maxRightPx = with(density) { 56.dp.toPx() }
    val springSpec = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = 0.9f)

    fun hideActions() {
        onActionsVisibleChange(false)
        animScope.launch { offsetX.animateTo(0f, springSpec) }
    }

    // 外部收起（如另一行展开）时跟回
    LaunchedEffect(actionsVisible) {
        if (!actionsVisible && offsetX.value != 0f) {
            offsetX.animateTo(0f, springSpec)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            // 内容：跟手滑动，保持透明（B 方案呼吸感，不加白底）
            // v0.41.15：左滑时内容整体淡出——图标先变透明，避免被生硬裁掉（Bob 反馈丑）
            val contentAlpha = 1f - (-offsetX.value / buttonWidthPx).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer { alpha = contentAlpha }
                    .pointerInput(app.packageName) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val v = offsetX.value
                                animScope.launch {
                                    when {
                                        v < -fullPx -> {
                                            // v0.41.11：左滑满 → 直接加到 Dock（或移出）
                                            togglePin()
                                            hideActions()
                                        }
                                        v < -revealPx -> {
                                            // 左滑=多：★ 淡入，点按触发
                                            offsetX.animateTo(-buttonWidthPx, springSpec)
                                            onActionsVisibleChange(true)
                                        }
                                        v > revealPx -> {
                                            // 右滑=少：弹确认框再隐藏
                                            offsetX.animateTo(0f, springSpec)
                                            showHideConfirm = true
                                        }
                                        else -> hideActions()
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                animScope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount)
                                            .coerceIn(-maxLeftPx, maxRightPx)
                                    )
                                }
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = {
                            // ★ 显示时点行 = 收起；否则启动应用
                            if (offsetX.value != 0f) hideActions()
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

            // v0.41.13：★ 改为覆盖层（不占布局、不加白底）——随滑动淡入，
            // 滑满时放大提示"松手直接触发"。透明设计不受影响。
            val swipeProgress = (-offsetX.value / buttonWidthPx).coerceIn(0f, 1f)
            if (swipeProgress > 0.01f) {
                val overscroll = (-offsetX.value - buttonWidthPx).coerceAtLeast(0f)
                val starScale = 1f +
                    (overscroll / (maxLeftPx - buttonWidthPx)).coerceIn(0f, 1f) * 0.35f
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = swipeProgress
                            scaleX = starScale
                            scaleY = starScale
                        }
                        .clip(CircleShape)
                        .clickable(
                            enabled = offsetX.value < -buttonWidthPx / 2,
                            onClick = {
                                togglePin()
                                hideActions()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = if (isPinned) "移出 Dock" else "加到 Dock",
                        tint = if (isPinned) Color(0xFFC9A227) else Color(0xFF8A8478),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    // 右滑隐藏确认框
    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("隐藏应用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "是否隐藏「${app.label}」？点右下眼睛图标可恢复。",
                    fontSize = 14.sp,
                    color = AILauncherColors.Body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showHideConfirm = false
                    onHide(app)
                }) { Text("隐藏", color = AILauncherColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirm = false }) { Text("取消") }
            }
        )
    }
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
