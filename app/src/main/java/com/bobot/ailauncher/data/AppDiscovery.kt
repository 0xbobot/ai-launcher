package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** 手机上一个可启动应用 */
data class AppInfo(
    val packageName: String,
    val label: CharSequence,
    val icon: Drawable
)

/** 列出全部可启动应用（桌面图标来源） */
fun listLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
        try {
            AppInfo(
                packageName = ri.activityInfo.packageName,
                label = ri.loadLabel(pm),
                icon = ri.loadIcon(pm)
            )
        } catch (_: Exception) {
            null
        }
    }
}

