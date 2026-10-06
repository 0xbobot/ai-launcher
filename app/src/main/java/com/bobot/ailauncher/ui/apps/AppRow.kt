package com.bobot.ailauncher.ui.apps

import android.widget.Toast
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
    // v0.41.18：已隐藏分组内的应用——禁用右滑隐藏手势（已经是隐藏的）
    isHidden: Boolean = false,
    // v0.41.19（Bob）：隐藏分组内左滑=恢复显示（长按菜单与其他应用一致，不再放恢复）
    onUnhide: ((AppInfo) -> Unit)? = null
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

    // v0.41.16（Bob）：滑动后空白分不清——内容不再移动/淡出，始终完整可见；
    // ★ 的出现/放大跟手指走（跟手反馈），松手后按阈值定。图标不再被裁。
    var dragDist by remember { mutableStateOf(0f) } // 左滑为正
    var hideIconShown by remember { mutableStateOf(false) } // 右滑隐藏图标是否保持显示
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }

    fun hideStar() {
        dragDist = 0f
        onActionsVisibleChange(false)
        hideIconShown = false
    }

    // 外部收起（如另一行展开）时
    LaunchedEffect(actionsVisible) {
        if (!actionsVisible) dragDist = 0f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            // 内容：完全静态，始终完整可见（B 方案呼吸感）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(app.packageName) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragDist > fullPx -> {
                                        // 左滑满 → 直接加到 Dock（或移出）
                                        togglePin()
                                        hideStar()
                                    }
                                    dragDist > revealPx -> {
                                        // 左滑=多：★ 保持显示，点按触发
                                        dragDist = 0f
                                        onActionsVisibleChange(true)
                                    }
                                    dragDist < -revealPx -> {
                                        // 右滑=少：隐藏图标保持显示，点按隐藏（v0.41.17 Bob：用图标表示）
                                        dragDist = 0f
                                        hideIconShown = true
                                    }
                                    else -> dragDist = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                // 左滑 dragAmount 为负，转成正数距离
                                // v0.41.18：已隐藏的应用禁用右滑（dragDist 不为负）
                                val newDist = (dragDist - dragAmount)
                                    .coerceIn(-maxDragPx, maxDragPx)
                                dragDist = if (isHidden) newDist.coerceAtLeast(0f) else newDist
                            }
                        )
                    }
                    .combinedClickable(
                        onClick = {
                            // ★/隐藏图标显示时点行 = 收起；否则启动应用
                            if (actionsVisible || hideIconShown) hideStar()
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

            // ★：跟手出现/放大。dragDist 驱动透明度和缩放，松手后由 actionsVisible 保持。
            // v0.41.19（Bob）：隐藏分组内左滑显示"恢复显示"（眼睛图标），不再是 ★
            val starProgress = if (actionsVisible) 1f
                else (dragDist / revealPx).coerceIn(0f, 1f)
            if (starProgress > 0.01f) {
                val overscroll = (dragDist - revealPx).coerceAtLeast(0f)
                val starScale = 1f +
                    (overscroll / (maxDragPx - revealPx)).coerceIn(0f, 1f) * 0.35f
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = starProgress
                            scaleX = starScale
                            scaleY = starScale
                        }
                        .clip(CircleShape)
                        .clickable(
                            enabled = actionsVisible || dragDist > revealPx / 2,
                            onClick = {
                                if (isHidden) {
                                    onUnhide?.invoke(app)
                                } else {
                                    togglePin()
                                }
                                hideStar()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isHidden) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "恢复显示",
                            tint = Color(0xFF8A8478),
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = if (isPinned) "移出 Dock" else "加到 Dock",
                            tint = if (isPinned) Color(0xFFC9A227) else Color(0xFF8A8478),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // v0.41.17（Bob）：右滑隐藏也用图标表示——左侧眼睛图标，跟手出现，点按直接隐藏
            // v0.41.18（Bob）：图标移到左侧留白（translationX），不和应用图标重叠
            val hideProgress = if (hideIconShown) 1f
                else (-dragDist / revealPx).coerceIn(0f, 1f)
            if (hideProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = hideProgress
                            // 左移 30dp 到行左留白区，避开应用图标（12dp 起）
                            translationX = with(density) { (-30).dp.toPx() }
                        }
                        .clip(CircleShape)
                        .clickable(
                            enabled = hideIconShown || dragDist < -revealPx / 2,
                            onClick = {
                                onHide(app)
                                hideStar()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "隐藏",
                        tint = Color(0xFF8A8478),
                        modifier = Modifier.size(26.dp)
                    )
                }
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
