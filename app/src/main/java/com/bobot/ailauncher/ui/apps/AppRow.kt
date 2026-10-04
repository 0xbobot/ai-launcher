package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
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
    val swipePx = with(density) { 56.dp.toPx() }

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
                                if (accumX < -swipePx) {
                                    // 左滑=多：展开操作（胶囊按钮已在行内）
                                    if (!actionsVisible) onActionsVisibleChange(true)
                                } else if (accumX > swipePx) {
                                    // 右滑=少：展开态收起；收起态弹确认框再隐藏
                                    if (actionsVisible) onActionsVisibleChange(false)
                                    else showHideConfirm = true
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
                // v0.25.9：更紧凑——纵向 padding 8dp，图标 36dp
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = actionsVisible,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = 0.9f
                ),
                modifier = Modifier.fillMaxWidth()
            ) { expanded ->
                if (!expanded) {
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
                    // 展开态：图标（降透明）+ 两个胶囊图标按钮（同行，不另起行）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .graphicsLayer { alpha = 0.7f }
                        )
                        // 应用信息：灰色描边胶囊
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 44.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    1.dp,
                                    AILauncherColors.Divider,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable(onClick = ::openAppDetails),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "应用信息",
                                tint = AILauncherColors.Body,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        // 卸载：柔和红底胶囊
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 44.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFE57373))
                                .clickable(onClick = ::uninstallApp),
                            contentAlignment = Alignment.Center
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

    // 右滑隐藏确认框
    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("隐藏应用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
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
