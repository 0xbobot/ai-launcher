package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.components.rememberDrawableBitmap
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 应用行：两段式左滑。
 * - 左滑=多：轻滑（stage 1）显示该应用的系统快捷方式（无应用名）；
 *   继续滑到底（stage 2）在右侧追加管理按钮（应用信息/卸载，横向排列，差异化样式）。
 * - 右滑=少：stage 2→1→0 逐级收起；stage 0 右滑弹隐藏确认框。
 * 一次只展开一行（由调用方的 expandedActionsPkg 保证）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: AppInfo,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    onHide: (AppInfo) -> Unit,
    expandStage: Int,
    onExpandStageChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showHideConfirm by remember { mutableStateOf(false) }
    val swipePx = with(density) { 56.dp.toPx() }
    // 二段阈值：滑得更远才进管理
    val swipePxStage2 = with(density) { 140.dp.toPx() }

    // stage>=1 时加载系统快捷方式（AppQuickActionsSheet 的 internal helper）
    var shortcuts by remember { mutableStateOf<List<ShortcutInfo>>(emptyList()) }
    var shortcutsLoadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expandStage, app.packageName) {
        if (expandStage >= 1 && shortcutsLoadedFor != app.packageName) {
            shortcuts = loadShortcuts(context, app.packageName)
            shortcutsLoadedFor = app.packageName
        }
        if (expandStage == 0) shortcutsLoadedFor = null
    }

    fun openAppDetails() {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${app.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstallApp() {
        try {
            val intent = Intent(
                Intent.ACTION_DELETE,
                android.net.Uri.parse("package:${app.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "无法卸载", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(app.packageName) {
                    var accumX = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; fired = false },
                        onDragCancel = { fired = true },
                        onDragEnd = {
                            if (!fired) {
                                if (accumX < -swipePxStage2) {
                                    // 滑到底：stage 2（管理）
                                    onExpandStageChange(2)
                                } else if (accumX < -swipePx) {
                                    // 轻滑：stage 1（快捷方式）；已在 1 则进 2
                                    onExpandStageChange(if (expandStage >= 1) 2 else 1)
                                } else if (accumX > swipePx) {
                                    // 右滑=少：逐级收起；0 则弹隐藏确认
                                    when {
                                        expandStage >= 2 -> onExpandStageChange(1)
                                        expandStage == 1 -> onExpandStageChange(0)
                                        else -> showHideConfirm = true
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (!fired) accumX += dragAmount
                        }
                    )
                }
                .combinedClickable(onClick = onLaunch, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = expandStage >= 1,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = 0.9f
                ),
                modifier = Modifier.fillMaxWidth()
            ) { expanded ->
                if (!expanded) {
                    // stage 0：图标 + 应用名（原样）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
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
                } else {
                    // stage 1/2：图标（降透明）+ 快捷方式 pills（无应用名）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .graphicsLayer { alpha = 0.7f }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (shortcuts.isEmpty()) {
                                Text(
                                    text = "暂无快捷方式",
                                    fontSize = 13.sp,
                                    color = AILauncherColors.Hint
                                )
                            } else {
                                shortcuts.forEach { sc ->
                                    ShortcutPill(
                                        info = sc,
                                        onClick = {
                                            launchShortcut(
                                                context,
                                                app.packageName,
                                                sc.id
                                            )
                                        },
                                        iconLoader = {
                                            shortcutIcon(
                                                context,
                                                app.packageName,
                                                sc.id
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        // stage 2：管理按钮（横向，差异化样式）
                        if (expandStage >= 2) {
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(
                                onClick = ::openAppDetails,
                                modifier = Modifier
                                    .size(44.dp)
                                    .border(
                                        1.dp,
                                        AILauncherColors.Divider,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "应用信息",
                                    tint = AILauncherColors.Body,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = ::uninstallApp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        Color(0xFFE57373),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "卸载",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 右滑隐藏确认框
    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("隐藏应用", fontSize = 16.sp) },
            text = {
                Text(
                    text = "是否隐藏「${app.label}」？可在设置页恢复。",
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

/** 快捷方式 pill：浅蓝灰底 + 深蓝字 + 小图标；长标题截断 */
@Composable
private fun ShortcutPill(
    info: ShortcutInfo,
    onClick: () -> Unit,
    iconLoader: () -> Drawable?
) {
    val label = info.shortLabel?.toString().orEmpty()
        .ifEmpty { info.longLabel?.toString().orEmpty() }
    var icon by remember(info.id) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(info.id) { icon = iconLoader() }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFD6E4F0))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 120.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val d = icon
        if (d != null) {
            Image(
                bitmap = rememberDrawableBitmap(d),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF1A365D),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
