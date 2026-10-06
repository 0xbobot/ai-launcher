package com.bobot.ailauncher.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bobot.ailauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OTA 版本信息（来自 https://bobot.is-a.dev/ai-launcher/update.json） */
data class OtaInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false
)

/** OTA 检查结果：设置页手动检查需要区分"无更新"和"检查失败" */
sealed interface OtaCheckResult {
    data class UpdateAvailable(val info: OtaInfo) : OtaCheckResult
    data object UpToDate : OtaCheckResult
    data object Failed : OtaCheckResult
}

/**
 * 自动 OTA 升级：版本 manifest 托管在 Bob 的 GitHub Pages
 *（https://bobot.is-a.dev/ai-launcher/update.json），安装包同目录。
 * 全部网络请求在 Dispatchers.IO，异常一律吞掉（检查更新失败不打扰用户）。
 */
object OtaUpdater {
    private const val MANIFEST_URL = "https://bobot.is-a.dev/ai-launcher/update.json"
    private const val PREFS = "ota"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_INSTALL_PROMPTED = "install_prompted"
    private const val KEY_TARGET_VERSION = "target_version"
    private const val KEY_LAST_RUN_VERSION = "last_run_version"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val APK_FILE_NAME = "ai-launcher-update.apk"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 有新版返回 OtaInfo，否则 null（无更新或任何失败） */
    suspend fun checkForUpdate(context: Context): OtaInfo? =
        (checkForUpdateResult(context) as? OtaCheckResult.UpdateAvailable)?.info

    suspend fun checkForUpdateResult(context: Context): OtaCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(MANIFEST_URL).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext OtaCheckResult.Failed
                    val body = resp.body?.string() ?: return@withContext OtaCheckResult.Failed
                    val json = JSONObject(body)
                    val remoteCode = json.optInt("versionCode", 0)
                    if (remoteCode <= 0 || remoteCode <= BuildConfig.VERSION_CODE) {
                        return@withContext OtaCheckResult.UpToDate
                    }
                    val apkUrl = json.optString("apkUrl", "")
                    if (apkUrl.isBlank()) return@withContext OtaCheckResult.Failed
                    OtaCheckResult.UpdateAvailable(
                        OtaInfo(
                            versionCode = remoteCode,
                            versionName = json.optString("versionName", ""),
                            apkUrl = apkUrl,
                            changelog = json.optString("changelog", "")
                                .ifBlank { "常规更新与问题修复" },
                            forceUpdate = json.optBoolean("forceUpdate", false)
                        )
                    )
                }
            } catch (e: IOException) {
                OtaCheckResult.Failed
            } catch (e: Exception) {
                OtaCheckResult.Failed
            }
        }

    /** 每天最多自动检查一次 */
    fun shouldAutoCheck(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** 用系统 DownloadManager 下载 APK，返回 downloadId */
    fun startDownload(context: Context, info: OtaInfo): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // v0.40.0：先清掉我们之前发起、还没跑完的下载任务——之前"等待下载 2 个文件"
        // 一直卡住，就是"立即更新"被点了两次（或自动检查+手动检查各弹一次），
        // 两个任务抢同一个目标文件互相打架。按标题前缀只清我们自己的任务。
        sweepStaleDownloads(dm)
        // 先删掉旧残留包，避免 DownloadManager 目标文件冲突
        downloadedApk(context)?.delete()
        val req = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("AI桌面 v${info.versionName} 更新")
            setDescription("正在下载新版本安装包")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME
            )
            setMimeType("application/vnd.android.package-archive")
        }
        val id = dm.enqueue(req)
        prefs(context).edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putInt(KEY_TARGET_VERSION, info.versionCode)
            .putBoolean(KEY_INSTALL_PROMPTED, false)
            .apply()
        return id
    }

    /**
     * v0.40.0：清掉我们之前发起、还没结束的下载任务（标题前缀"AI桌面 v"）。
     * 只动我们自己的任务，不碰用户别的下载。
     */
    private fun sweepStaleDownloads(dm: DownloadManager) {
        try {
            dm.query(DownloadManager.Query()).use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val titleCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val stale = mutableListOf<Long>()
                while (c.moveToNext()) {
                    val title = c.getString(titleCol) ?: ""
                    val status = c.getInt(statusCol)
                    val running = status == DownloadManager.STATUS_PENDING ||
                            status == DownloadManager.STATUS_PAUSED ||
                            status == DownloadManager.STATUS_RUNNING
                    if (running && title.startsWith("AI桌面 v")) {
                        stale.add(c.getLong(idCol))
                    }
                }
                if (stale.isNotEmpty()) dm.remove(*stale.toLongArray())
            }
        } catch (_: Exception) {
        }
    }

    fun pendingDownloadId(context: Context): Long =
        prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)

    fun isDownloadComplete(context: Context, downloadId: Long): Boolean {
        if (downloadId < 0) return false
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                c.moveToFirst() &&
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 下载进度 0..1；下载完成返回 1f，查不到/失败返回 null */
    fun downloadProgress(context: Context, downloadId: Long): Float? {
        if (downloadId < 0) return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (!c.moveToFirst()) return null
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) return 1f
                if (status == DownloadManager.STATUS_FAILED) return null
                val soFar = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val total = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                if (total <= 0) null else (soFar.toFloat() / total).coerceIn(0f, 1f)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 已下载好的 APK 文件（不存在或为空返回 null） */
    fun downloadedApk(context: Context): File? {
        val f = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME
        )
        return if (f.exists() && f.length() > 0) f else null
    }

    /** 已下载的包是否就是指定版本：防止"直接安装"命中旧包 */
    fun isDownloadedVersion(context: Context, versionCode: Int): Boolean =
        prefs(context).getInt(KEY_TARGET_VERSION, 0) == versionCode &&
            downloadedApk(context) != null

    /** 下载已完成、是比当前更新的版本、且还没弹过安装提示 → 应该弹安装 */
    fun shouldPromptInstall(context: Context): Boolean =
        shouldPromptInstall(context, pendingDownloadId(context))

    /**
     * v0.40.0：下载完成广播用，completedId 是刚完成的任务 id。
     * 用"完成的是我们自己的更新包文件"来判定，不再死磕 id 等于记录值——
     * 之前重复下载导致旧任务先完成时 id 对不上，安装提醒就永远没弹出来。
     */
    fun shouldPromptInstall(context: Context, completedId: Long): Boolean {
        if (prefs(context).getBoolean(KEY_INSTALL_PROMPTED, false)) return false
        // 只为新版本弹安装：已装过的版本的残留包不再提示
        if (prefs(context).getInt(KEY_TARGET_VERSION, 0) <= BuildConfig.VERSION_CODE) return false
        if (!isOurDownload(context, completedId)) return false
        if (!isDownloadComplete(context, completedId)) return false
        return downloadedApk(context) != null
    }

    /** 完成的下载是否写的是我们的更新包文件（防串到别的下载任务） */
    private fun isOurDownload(context: Context, downloadId: Long): Boolean {
        if (downloadId < 0) return false
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (!c.moveToFirst()) return false
                val uriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val uri = if (uriIdx >= 0) c.getString(uriIdx) else null
                uri != null && uri.endsWith(APK_FILE_NAME)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun markInstallPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_INSTALL_PROMPTED, true).apply()
    }

    /**
     * App 版本变化（升级/重装）后调用：清理旧安装包与旧下载状态，
     * 避免下次更新时"直接安装"命中旧包。建议在 MainActivity.onCreate 调用。
     */
    fun onAppUpgraded(context: Context) {
        val p = prefs(context)
        if (p.getInt(KEY_LAST_RUN_VERSION, 0) != BuildConfig.VERSION_CODE) {
            downloadedApk(context)?.delete()
            p.edit()
                .putInt(KEY_LAST_RUN_VERSION, BuildConfig.VERSION_CODE)
                .putLong(KEY_DOWNLOAD_ID, -1L)
                .putBoolean(KEY_INSTALL_PROMPTED, false)
                .apply()
        }
    }

    /**
     * 弹安装：有"安装未知应用"权限走 FileProvider 安装，否则先引导去开权限。
     * v0.40.0：返回是否真的拉起了安装器——没权限只跳了设置页时返回 false，
     * 调用方此时不要 markInstallPrompted，用户开完权限回来 onResume 会再弹。
     */
    fun promptInstall(context: Context, apkFile: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "请允许安装未知应用，开完回来会自动弹出安装", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return true
    }
}
