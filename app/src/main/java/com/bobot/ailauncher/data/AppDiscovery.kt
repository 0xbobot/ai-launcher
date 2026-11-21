package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/** 手机上一个可启动应用 */
data class AppInfo(
    val packageName: String,
    val label: CharSequence,
    val icon: Drawable,
    /** 首次安装时间（毫秒，PackageInfo.firstInstallTime；读不到为 0，表未知） */
    val firstInstallTime: Long = 0L
)

/** 列出全部可启动应用（桌面图标来源） */
fun listLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
        try {
            val pkg = ri.activityInfo.packageName
            AppInfo(
                packageName = pkg,
                label = ri.loadLabel(pm),
                icon = ri.loadIcon(pm),
                // 安装时间（折叠组新装豁免用）：读不到按未知（0）计，不阻塞枚举
                firstInstallTime = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).firstInstallTime
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0).firstInstallTime
                    }
                } catch (_: Exception) {
                    0L
                }
            )
        } catch (_: Exception) {
            null
        }
    }
}

