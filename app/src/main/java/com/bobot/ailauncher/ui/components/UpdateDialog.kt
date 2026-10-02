package com.bobot.ailauncher.ui.components

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater

/**
 * OTA 更新对话框：首页自动检查与设置页手动检查共用。
 * "立即更新"：若安装包已下载好则直接弹安装，否则走 DownloadManager 后台下载。
 * forceUpdate=true 时不提供"稍后"按钮。
 */
@Composable
fun UpdateDialog(info: OtaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!info.forceUpdate) onDismiss() },
        title = { Text("发现新版本 v${info.versionName}") },
        text = { Text(info.changelog) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val apk = OtaUpdater.downloadedApk(context)
                if (apk != null && OtaUpdater.isDownloadComplete(
                        context, OtaUpdater.pendingDownloadId(context)
                    )
                ) {
                    OtaUpdater.promptInstall(context, apk)
                    OtaUpdater.markInstallPrompted(context)
                } else {
                    OtaUpdater.startDownload(context, info)
                    Toast.makeText(context, "正在后台下载，可在通知栏查看进度", Toast.LENGTH_LONG)
                        .show()
                }
            }) {
                Text("立即更新")
            }
        },
        dismissButton = {
            if (!info.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        }
    )
}
