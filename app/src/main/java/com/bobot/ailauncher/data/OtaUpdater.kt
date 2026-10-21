package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bobot.ailauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.bobot.ailauncher.ui.island.IslandToasts

/** OTA 版本信息（来自 https://bobot.is-a.dev/ai-launcher/update.json） */
data class OtaInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false,
    val prerelease: Boolean = false, // v0.68.80：测试版标记（publish-ota 的 prerelease 输入写入）
    /** v0.68.89：发布时对最终 APK 算出的 SHA-256（小写 hex）；旧 manifest 无此字段时为空串，下载端跳过该项校验 */
    val sha256: String = ""
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
    private const val KEY_INSTALL_PROMPTED = "install_prompted"
    private const val KEY_TARGET_VERSION = "target_version"
    private const val KEY_LAST_RUN_VERSION = "last_run_version"
    // v0.68.80：测试版开关（prerelease 包只推给 opt-in 用户）
    private const val KEY_BETA_OPT_IN = "beta_opt_in"
    // v0.65.6：下载完整性——只有 verified 完整的包才允许安装。
    // 背景：锁屏中断下载后残留 partial 文件，shouldPromptInstall 误判为已下好，
    // onResume 直接弹安装 → "解析程序包时出现问题"。
    private const val KEY_DOWNLOAD_COMPLETE = "download_complete"
    private const val KEY_EXPECTED_SIZE = "expected_size"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val APK_FILE_NAME = "ai-launcher-update.apk"

    /**
     * v0.64.2：最新发现的可更新版本（自动检查写入），TODAY 卡片底部的"有新版本"行读取展示。
     * 更新后（versionCode 追上）调用 clearAvailable() 清掉。
     */
    val latestAvailable = MutableStateFlow<OtaInfo?>(null)

    /**
     * v0.64.2：请求弹出更新对话框（TODAY 行点击 / 自动检查），MainScreen 收集后展示 UpdateDialog。
     */
    val updatePrompt = MutableStateFlow<OtaInfo?>(null)

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
                    evaluateManifest(body, BuildConfig.VERSION_CODE, isBetaOptIn(context))
                }
            } catch (e: IOException) {
                OtaCheckResult.Failed
            } catch (e: Exception) {
                OtaCheckResult.Failed
            }
        }

    /**
     * 体检批D ⑥：manifest 判定纯函数接缝——把 update.json 文本判成检查结果，
     * 网络与 Context 读开关留在 [checkForUpdateResult] 外层，本函数只做
     * 解析 + 版本比较 + prerelease 门控，供 JVM 单测覆盖（行为与原内联实现等价：
     * JSON 坏 → Failed；版本号不大于当前 → UpToDate；有新版但 apkUrl 空 → Failed）。
     */
    internal fun evaluateManifest(
        body: String,
        currentVersionCode: Int,
        betaOptIn: Boolean
    ): OtaCheckResult {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return OtaCheckResult.Failed
        }
        val remoteCode = json.optInt("versionCode", 0)
        if (remoteCode <= 0 || remoteCode <= currentVersionCode) {
            return OtaCheckResult.UpToDate
        }
        val apkUrl = json.optString("apkUrl", "")
        if (apkUrl.isBlank()) return OtaCheckResult.Failed
        val info = OtaInfo(
            versionCode = remoteCode,
            versionName = json.optString("versionName", ""),
            apkUrl = apkUrl,
            changelog = json.optString("changelog", "")
                .ifBlank { "常规更新与问题修复" },
            forceUpdate = json.optBoolean("forceUpdate", false),
            prerelease = json.optBoolean("prerelease", false),
            sha256 = json.optString("sha256", "")
        )
        // v0.68.80：测试版只推给开了"接收测试版"开关的用户；
        // 未开启则视为无更新（正式版用户不受测试包打扰）
        if (info.prerelease && !betaOptIn) {
            return OtaCheckResult.UpToDate
        }
        return OtaCheckResult.UpdateAvailable(info)
    }

    /** 每天最多自动检查一次 */
    fun shouldAutoCheck(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    /**
     * v0.68.80：是否接收测试版（prerelease）更新，默认 false。
     * 开启后，update.json 里 prerelease=true 的包才会进入更新流程；
     * 关闭后自动清掉当前展示的测试版（TODAY 卡片行不残留）。
     */
    fun isBetaOptIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BETA_OPT_IN, false)

    fun setBetaOptIn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BETA_OPT_IN, value).apply()
        if (!value && latestAvailable.value?.prerelease == true) {
            latestAvailable.value = null
        }
    }

    fun markChecked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    // v0.41.12：DownloadManager 已废弃，下载走 OtaDownloader（App 内 OkHttp）。
    // 旧方法 startDownload/downloadProgress/isDownloadComplete/sweepStaleDownloads
    // 及 KEY_DOWNLOAD_ID 已删除。

    /** 已下载好的 APK 文件（不存在或为空返回 null） */
    fun downloadedApk(context: Context): File? {
        val f = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME
        )
        return if (f.exists() && f.length() > 0) f else null
    }

    /** 已下载的包是否就是指定版本：防止"直接安装"命中旧包/残缺包 */
    fun isDownloadedVersion(context: Context, versionCode: Int): Boolean =
        isDownloadComplete(context, versionCode)

    /**
     * v0.65.6：下载是否真正完成——complete 标记 + 版本号对上 + 文件存在
     * + 长度与服务端 Content-Length 一致（已知时）。partial 文件一律不算。
     */
    fun isDownloadComplete(context: Context, versionCode: Int): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_DOWNLOAD_COMPLETE, false)) return false
        if (p.getInt(KEY_TARGET_VERSION, 0) != versionCode) return false
        val f = downloadedApk(context) ?: return false
        val expected = p.getLong(KEY_EXPECTED_SIZE, -1L)
        if (expected > 0 && f.length() != expected) return false
        return true
    }

    /** 下载已完成、是比当前更新的版本、且还没弹过安装提示 → 应该弹安装 */
    fun shouldPromptInstall(context: Context): Boolean {
        val p = prefs(context)
        val target = p.getInt(KEY_TARGET_VERSION, 0)
        return !p.getBoolean(KEY_INSTALL_PROMPTED, false) &&
            target > BuildConfig.VERSION_CODE &&
            isDownloadComplete(context, target)
    }

    // v0.41.12：shouldPromptInstall(context, completedId) 与 isOurDownload 已删除
    //（DownloadManager 废弃，下载走 OtaDownloader）。

    fun markInstallPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_INSTALL_PROMPTED, true).apply()
    }

    /**
     * v0.65.6：OkHttp 下载器用——标记开始下载某版本。complete 标记清掉，
     * 之前的包无论是否完整都不再视为可安装（防 partial 残留被误装）。
     */
    fun markDownloadStart(context: Context, versionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_TARGET_VERSION, versionCode)
            .putBoolean(KEY_INSTALL_PROMPTED, false)
            .putBoolean(KEY_DOWNLOAD_COMPLETE, false)
            .putLong(KEY_EXPECTED_SIZE, -1L)
            .apply()
    }

    /** v0.65.6：记录服务端 Content-Length（>0 时），用于完整性校验 */
    fun markDownloadSize(context: Context, size: Long) {
        if (size > 0) prefs(context).edit().putLong(KEY_EXPECTED_SIZE, size).apply()
    }

    /**
     * v0.65.6：标记下载完成——只有下载器校验通过（长度一致 + APK 可解析）
     * 后才调用。之前版本此方法与 markDownloadStart 同效，complete 标记从未真正使用。
     */
    fun markDownloadComplete(context: Context, versionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_TARGET_VERSION, versionCode)
            .putBoolean(KEY_DOWNLOAD_COMPLETE, true)
            .apply()
    }

    /**
     * v0.65.6：APK 是否可解析（防截断/损坏包）。下载完成时在 IO 线程校验一次；
     * 调用方在安装前兜底复查。返回 false 说明包坏了，不要安装。
     */
    fun isValidApk(context: Context, file: File): Boolean {
        return try {
            if (!file.exists() || file.length() <= 0) return false
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            pi != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 读下载包内的 versionCode（读不到/不可解析返回 null）。
     * 下载完成时与 update.json 声称的 versionCode 比对，防错包被当成功。
     */
    fun apkVersionCode(context: Context, file: File): Int? {
        return try {
            if (!file.exists() || file.length() <= 0) return null
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            pi?.longVersionCode?.toInt()
        } catch (_: Exception) {
            null
        }
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
                .putBoolean(KEY_INSTALL_PROMPTED, false)
                .putBoolean(KEY_DOWNLOAD_COMPLETE, false)
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
            IslandToasts.show("请允许安装未知应用，开完回来会自动弹出安装")
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
