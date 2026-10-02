package com.bobot.ailauncher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * 常用应用：优先用 UsageStatsManager 取近 7 天前台时长最高的；
 * 未授权（返回空）或异常时，兜底取前 N 个可启动应用。
 */
fun getFrequentApps(context: Context, limit: Int = 8): List<AppInfo> {
    val pm = context.packageManager
    try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 7L * 24 * 3600 * 1000,
            now
        )
        if (!stats.isNullOrEmpty()) {
            val ranked = stats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .mapNotNull { s ->
                    try {
                        if (pm.getLaunchIntentForPackage(s.packageName) == null) return@mapNotNull null
                        val ai = pm.getApplicationInfo(s.packageName, 0)
                        AppInfo(
                            packageName = s.packageName,
                            label = pm.getApplicationLabel(ai),
                            icon = pm.getApplicationIcon(ai)
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }
                .distinctBy { it.packageName }
                .take(limit)
            if (ranked.isNotEmpty()) return ranked
        }
    } catch (_: SecurityException) {
        // 未授权用量统计，走兜底
    }
    return listLaunchableApps(context).take(limit)
}
