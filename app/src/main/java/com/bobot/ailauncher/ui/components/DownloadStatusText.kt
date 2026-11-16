package com.bobot.ailauncher.ui.components

import com.bobot.ailauncher.data.OtaDownloader

/**
 * 下载状态文案（原在 UpdateDialog.kt；第四十五批 UpdateDialog 退役后
 * 搬到这里——设置页版本行下载中仍用它显示进度，升级卡上的下载进度
 * 只写「正在下载 vX.X.X（xx%）」，不走这里）。
 * 百分比 + 已下载/总量 + 实时速度（速度可估算时），方便与浏览器下载对比。
 */
internal fun downloadStatusText(state: OtaDownloader.State.Downloading): String {
    val pct = (state.progress * 100).toInt()
    val sb = StringBuilder("已下载 $pct%")
    if (state.totalBytes > 0) {
        sb.append(" · ${formatBytes(state.downloadedBytes)}/${formatBytes(state.totalBytes)}")
    }
    if (state.speedBps > 0) {
        sb.append(" · ${formatBytes(state.speedBps)}/s")
    }
    return sb.toString()
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
