@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * v0.39.0：Dock 长按 → 真弹窗快捷菜单（Material3 DropdownMenu）。
 * v0.40.0：应用中心长按也改用这一套（替代 bottom sheet），每行自带锚点。
 * 锚定在图标/行所在的 Box 上：DropdownMenu 自动翻转——图标在屏幕下方时
 * 弹到图标上面，图标靠上时弹到图标下方。点外部/返回即关闭。
 *
 * 内容：应用提供的系统快捷方式 + 标题行右侧的应用信息图标。
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
        containerColor = AILauncherColors.current.Card,
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
                // 体检批D ⑨：装饰（同行已有应用名文字）标装饰语义
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { invisibleToUser() }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = app.label.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.current.Title,
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
                    tint = AILauncherColors.current.Hint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (shortcuts.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = AILauncherColors.current.Divider
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
                        color = AILauncherColors.current.Title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = if (icon != null) {
                    {
                        AppIconImage(
                            drawable = icon,
                            // 体检批D ⑨：装饰（菜单项已有文字标签）标装饰语义
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .semantics { invisibleToUser() }
                        )
                    }
                } else null,
                onClick = {
                    launchAppShortcut(context, app.packageName, s.id)
                    onDismiss()
                },
                colors = MenuDefaults.itemColors(textColor = AILauncherColors.current.Title),
                contentPadding = MenuDefaults.DropdownMenuItemContentPadding
            )
        }
        // v0.41.11（Bob 选方案1）：卸载从左滑移到长按菜单；ⓘ 已在标题行
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = AILauncherColors.current.Divider
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "卸载",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    // 体检批D ⑨：装饰（菜单项文字「卸载」已表达）标装饰语义
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { invisibleToUser() }
                )
            },
            onClick = {
                try {
                    val intent = Intent(
                        Intent.ACTION_DELETE,
                        android.net.Uri.parse("package:${app.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    IslandToasts.show("无法卸载")
                }
                onDismiss()
            },
            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
            contentPadding = MenuDefaults.DropdownMenuItemContentPadding
        )
    }
}

/**
 * v0.68.53（Bob）：内联生长式快捷菜单——无头（不重复图标标题）、无 DropdownMenu，
 * 直接嵌在应用行下方的圆角卡里，像从行里长出来。
 */
@Composable
fun AppShortcutInline(
    app: AppInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shortcuts = remember(app.packageName) {
        loadAppShortcuts(context, app.packageName)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // v0.68.180（Bob 纠正）：387 的单独「应用管理」行已删——不要单独一行；
        // 入口改到应用行本身最右边（见 AppRow showInfoIcon），与 Dock 标题行
        // （图标左、名字中、ⓘ 右）一致，展开时才出现。
        // 金线分隔（与行内容区分，但同一张卡）
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = AILauncherColors.current.Divider
        )
        // 系统快捷方式
        shortcuts.forEach { s ->
            val icon = remember(s.id) { loadShortcutIcon(context, app.packageName, s.id) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        launchAppShortcut(context, app.packageName, s.id)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (icon != null) {
                    AppIconImage(
                        drawable = icon,
                        // 体检批D ⑨：装饰（同行已有快捷方式文字）标装饰语义
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .semantics { invisibleToUser() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = s.shortLabel?.toString().orEmpty().ifBlank { "快捷方式" },
                    fontSize = 14.sp,
                    color = AILauncherColors.current.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 卸载
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = AILauncherColors.current.Divider
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        val intent = Intent(
                            Intent.ACTION_DELETE,
                            android.net.Uri.parse("package:${app.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        IslandToasts.show("无法卸载")
                    }
                    onDismiss()
                }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                // 体检批D ⑨：装饰（同行文字「卸载」已表达）标装饰语义
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(22.dp)
                    .semantics { invisibleToUser() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "卸载",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
