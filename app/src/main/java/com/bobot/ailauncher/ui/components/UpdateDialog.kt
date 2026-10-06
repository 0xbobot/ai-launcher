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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater

/**
 * OTA 更新对话框：首页自动检查与设置页手动检查共用。
 * v0.41.6（Bob）：点"立即更新"后进度条直接在对话框里显示，下载完成自动弹安装。
 * v0.41.12：下载器从系统 DownloadManager 换成 App 内 OkHttp（OtaDownloader）——
 *   DownloadManager 在 Bob 手机开 VPN 时直接失败（0% 报错），OkHttp 走 App 网络栈正常。
 * - 安装包已下载好 → 直接弹安装
 * - 下载中 → 对话框内进度条（StateFlow 实时）；可"后台下载"收起（下载在 application
 *   作用域继续，完成后直接弹安装；onResume 兜底）
 * - 下载失败 → 显示重试
 * forceUpdate=true 时不提供"稍后"/"后台下载"按钮。
 */
@Composable
fun UpdateDialog(info: OtaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // v0.40.0：防双击
    var busy by remember { mutableStateOf(false) }
    val dlState by OtaDownloader.state.collectAsState()

    val downloading = dlState is OtaDownloader.State.Downloading
    val failed = dlState is OtaDownloader.State.Failed
    val progress = (dlState as? OtaDownloader.State.Downloading)?.progress ?: 0f

    // 下载成功 → OtaDownloader 已直接弹安装，对话框收起
    if (dlState is OtaDownloader.State.Success) {
        onDismiss()
    }

    fun startDownload() {
        busy = true
        OtaDownloader.start(context, info)
    }

    AlertDialog(
        onDismissRequest = {
            // 下载中非强制更新时允许收起（后台继续下，完成后直接弹安装）
            if (!downloading && !info.forceUpdate) {
                OtaDownloader.resetIfNotDownloading()
                onDismiss()
            }
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
                    if (OtaUpdater.isDownloadedVersion(context, info.versionCode)) {
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
                TextButton(onClick = {
                    OtaDownloader.resetIfNotDownloading()
                    onDismiss()
                }) {
                    Text(if (downloading) "后台下载" else "稍后")
                }
            }
        }
    )
}
