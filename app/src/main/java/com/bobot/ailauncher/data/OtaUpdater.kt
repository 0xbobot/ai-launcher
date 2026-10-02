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
        // 先删掉旧残留包，避免 DownloadManager 目标文件冲突
        downloadedApk(context)?.delete()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
            .putBoolean(KEY_INSTALL_PROMPTED, false)
            .apply()
        return id
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

    /** 已下载好的 APK 文件（不存在或为空返回 null） */
    fun downloadedApk(context: Context): File? {
        val f = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME
        )
        return if (f.exists() && f.length() > 0) f else null
    }

    /** 下载已完成、且还没弹过安装提示 → 应该弹安装 */
    fun shouldPromptInstall(context: Context): Boolean {
        if (prefs(context).getBoolean(KEY_INSTALL_PROMPTED, false)) return false
        val id = pendingDownloadId(context)
        return isDownloadComplete(context, id) && downloadedApk(context) != null
    }

    fun markInstallPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_INSTALL_PROMPTED, true).apply()
    }

    /** 弹安装：有"安装未知应用"权限走 FileProvider 安装，否则先引导去开权限 */
    fun promptInstall(context: Context, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "请允许安装未知应用，然后重新检查更新", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return
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
    }
}
