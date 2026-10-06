package com.bobot.ailauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import kotlinx.coroutines.delay

/**
 * OTA 更新对话框：首页自动检查与设置页手动检查共用。
 * v0.41.6（Bob）：点"立即更新"后进度条直接在对话框里显示，下载完成自动弹安装。
 * - 安装包已下载好 → 直接弹安装
 * - 下载中 → 对话框内进度条；可"后台下载"收起（通知栏继续显示进度，
 *   完成后由下载广播/onResume 兜底弹安装）
 * - 下载失败 → 显示重试
 * forceUpdate=true 时不提供"稍后"/"后台下载"按钮。
 */
@Composable
fun UpdateDialog(info: OtaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // v0.40.0：防双击——连点"立即更新"之前会产生两个下载任务抢同一个文件
    var busy by remember { mutableStateOf(false) }
    // -1 = 还没开始下载；>=0 = 下载任务 id，对话框内显示进度
    var downloadId by remember { mutableStateOf(-1L) }
    var progress by remember { mutableStateOf(0f) }
    var failed by remember { mutableStateOf(false) }

    fun startDownload() {
        busy = true
        failed = false
        progress = 0f
        downloadId = OtaUpdater.startDownload(context, info)
    }

    // 下载中：轮询进度；完成 → 直接弹安装（Bob），失败 → 显示重试
    if (downloadId >= 0 && !failed) {
        LaunchedEffect(downloadId) {
            while (true) {
                delay(500)
                if (OtaUpdater.isDownloadComplete(context, downloadId)) {
                    val apk = OtaUpdater.downloadedApk(context)
                    // 下载广播可能已先弹过安装，shouldPromptInstall 防重复
                    if (apk != null && OtaUpdater.shouldPromptInstall(context)) {
                        if (OtaUpdater.promptInstall(context, apk)) {
                            OtaUpdater.markInstallPrompted(context)
                        }
                    }
                    onDismiss()
                    break
                }
                val p = OtaUpdater.downloadProgress(context, downloadId)
                if (p == null) {
                    failed = true
                    break
                }
                progress = p
            }
        }
    }

    val downloading = downloadId >= 0 && !failed
    AlertDialog(
        onDismissRequest = {
            // 下载中非强制更新时允许收起（后台继续下，完成后兜底弹安装）
            if (!downloading && !info.forceUpdate) onDismiss()
        },
        title = {
            Text(
                when {
                    failed -> "下载失败"
                    downloading -> "正在下载 v${info.versionName}"
                    else -> "发现新版本 v${info.versionName}"
                }
            )
        },
        text = {
            when {
                failed -> Text("请检查网络后重试")
                downloading -> Column(modifier = Modifier.fillMaxWidth()) {
                    Text(info.changelog)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("已下载 ${(progress * 100).toInt()}%")
                }
                else -> Text(info.changelog)
            }
        },
        confirmButton = {
            when {
                failed -> TextButton(onClick = { startDownload() }) {
                    Text("重试")
                }
                downloading -> {
                    // 下载中：主按钮隐藏，只留"后台下载"
                }
                else -> TextButton(onClick = {
                    if (busy) return@TextButton
                    // 只有已下载的包正是这个新版本时才直接安装，否则重新下载
                    //（旧版本残留包不能复用，否则会"升级"成旧版）
                    if (OtaUpdater.isDownloadedVersion(context, info.versionCode) &&
                        OtaUpdater.isDownloadComplete(context, OtaUpdater.pendingDownloadId(context))
                    ) {
                        busy = true
                        OtaUpdater.downloadedApk(context)?.let {
                            if (OtaUpdater.promptInstall(context, it)) {
                                OtaUpdater.markInstallPrompted(context)
                            }
                        }
                        onDismiss()
                    } else {
                        startDownload()
                    }
                }) {
                    Text("立即更新")
                }
            }
        },
        dismissButton = {
            if (!info.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(if (downloading) "后台下载" else "稍后")
                }
            }
        }
    )
}
