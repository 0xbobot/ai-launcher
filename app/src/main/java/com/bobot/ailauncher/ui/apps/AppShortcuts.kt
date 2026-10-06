package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.widget.Toast

/**
 * v0.39.0：应用快捷方式公共 helpers。
 * 从 AppQuickActionsSheet 抽出，供 bottom sheet 与 Dock 真弹窗菜单共用。
 */

/** 取系统快捷方式；非默认桌面/异常时返回空列表（优雅降级） */
internal fun loadAppShortcuts(context: Context, packageName: String): List<ShortcutInfo> {
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

internal fun loadShortcutIcon(context: Context, packageName: String, shortcutId: String): Drawable? {
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

internal fun launchAppShortcut(context: Context, packageName: String, shortcutId: String) {
    try {
        val lm = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        lm.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
    } catch (_: Exception) {
        Toast.makeText(context, "无法启动快捷方式", Toast.LENGTH_SHORT).show()
    }
}

/** 打开系统应用信息页 */
internal fun openAppDetailsPage(context: Context, packageName: String) {
    try {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
    }
}
