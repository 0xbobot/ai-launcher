package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.16：长按应用 → 常用操作 bottom sheet（安卓习惯）。
 * - 系统应用快捷方式（LauncherApps.getShortcuts()，能取到才显示；
 *   非默认桌面/取不到时优雅降级，只显示下面两项）
 * - 应用信息
 * v0.22：分类取消，"调整分类"入口移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppQuickActionsSheet(
    app: AppInfo,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit
) {
    val context = LocalContext.current
    val shortcuts = remember(app.packageName) { loadShortcuts(context, app.packageName) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AILauncherColors.Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(
                    drawable = app.icon,
                    contentDescription = app.label.toString(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = app.label.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 系统快捷方式（有才显示）
            shortcuts.forEach { s ->
                val icon = remember(s.id) { shortcutIcon(context, app.packageName, s.id) }
                SheetRow(
                    label = s.shortLabel?.toString().orEmpty().ifBlank { "快捷方式" },
                    icon = icon,
                    onClick = {
                        launchShortcut(context, app.packageName, s.id)
                        onDismiss()
                    }
                )
            }
            SheetRow(label = "应用信息", onClick = {
                onAppInfo()
                onDismiss()
            })
        }
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: Drawable? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AppIconImage(
                drawable = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = label, fontSize = 15.sp, color = AILauncherColors.Title)
    }
}

/** 取系统快捷方式；非默认桌面/异常时返回空列表（优雅降级） */
internal fun loadShortcuts(context: Context, packageName: String): List<ShortcutInfo> {
    return try {
        val lm = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        lm.getShortcuts(query, Process.myUserHandle()).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun shortcutIcon(context: Context, packageName: String, shortcutId: String): Drawable? {
    return try {
        val lm = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setShortcutIds(listOf(shortcutId))
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        val info = lm.getShortcuts(query, Process.myUserHandle())?.firstOrNull()
        if (info != null) lm.getShortcutIconDrawable(info, 0) else null
    } catch (_: Exception) {
        null
    }
}

internal fun launchShortcut(context: Context, packageName: String, shortcutId: String) {
    try {
        val lm = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        lm.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
    } catch (_: Exception) {
        Toast.makeText(context, "无法启动快捷方式", Toast.LENGTH_SHORT).show()
    }
}
