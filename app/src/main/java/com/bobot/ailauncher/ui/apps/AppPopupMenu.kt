package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.39.0：Dock 长按 → 真弹窗快捷菜单（Material3 DropdownMenu）。
 * 锚定在图标所在的 Box 上：图标在屏幕下方时自动弹到图标上面，
 * 点外部/返回即关闭。不是半屏 bottom sheet。
 *
 * 内容与 AppQuickActionsSheet 一致：系统快捷方式 + 应用信息。
 */
@Composable
fun AppPopupMenu(
    app: AppInfo,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shortcuts = remember(app.packageName, expanded) {
        if (expanded) loadAppShortcuts(context, app.packageName) else emptyList()
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 208.dp, max = 300.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        // 头：应用图标 + 名称 + 应用信息图标（v0.39.1：应用信息收到标题行，用图标表示）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            AppIconImage(
                drawable = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = app.label.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    openAppDetailsPage(context, app.packageName)
                    onDismiss()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "应用信息",
                    tint = AILauncherColors.Hint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (shortcuts.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = AILauncherColors.Divider
            )
        }
        // 系统快捷方式（有才显示）
        shortcuts.forEach { s ->
            val icon = remember(s.id) { loadShortcutIcon(context, app.packageName, s.id) }
            DropdownMenuItem(
                text = {
                    Text(
                        text = s.shortLabel?.toString().orEmpty().ifBlank { "快捷方式" },
                        fontSize = 14.sp,
                        color = AILauncherColors.Title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = if (icon != null) {
                    {
                        AppIconImage(
                            drawable = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                    }
                } else null,
                onClick = {
                    launchAppShortcut(context, app.packageName, s.id)
                    onDismiss()
                },
                colors = MenuDefaults.itemColors(textColor = AILauncherColors.Title),
                contentPadding = MenuDefaults.DropdownMenuItemContentPadding
            )
        }
    }
}
