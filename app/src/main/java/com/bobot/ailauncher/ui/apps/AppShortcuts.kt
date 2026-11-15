package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * v0.39.0：应用快捷方式公共 helpers。
 * 快捷方式小工具（v0.39.0 从 bottom sheet 抽出，v0.40.0 起专供真弹窗菜单 AppPopupMenu）。
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
        IslandToasts.show("这个快捷方式打不开，稍后再试试吧")
    }
}

/**
 * 应用信息页的打开目标（纯数据，单测锁死在 UnreadSwipeTest）：
 * action 与 data Uri 都是 Settings 的编译期常量。
 */
internal data class AppDetailsTarget(
    val action: String,
    val dataUri: String
)

/** 未读组右滑「关闭通知」的打开目标：该应用的应用信息页。 */
internal fun appDetailsTarget(packageName: String): AppDetailsTarget =
    AppDetailsTarget(
        action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        dataUri = "package:$packageName"
    )

/**
 * 打开系统应用信息页（未读组右滑「关闭通知」用，Bob 2026-11-14 09:55
 * 亲定右滑；2026-11-15 改走这页：直接跳通知设置页
 * ACTION_APP_NOTIFICATION_SETTINGS 在 Bob 的中兴机上对微信白屏闪退
 * （页打开了、"允许通知"开关可见，随后白屏退回），应用信息页能正常
 * 打开，而且"允许推送通知"开关就直接在那页上，用户一 tap 就能关，
 * 目标不变。打不开时 toast 兜底。
 */
internal fun openAppDetailsPage(context: Context, packageName: String) {
    try {
        val target = appDetailsTarget(packageName)
        val intent = Intent(
            target.action,
            android.net.Uri.parse(target.dataUri)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (_: Exception) {
        IslandToasts.show("应用信息打不开，稍后再试试吧")
    }
}
