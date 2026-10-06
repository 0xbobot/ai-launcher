package com.bobot.ailauncher.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v0.41.12：App 内 OkHttp 下载器，替代系统 DownloadManager。
 *
 * 背景：DownloadManager 是系统服务，Bob 手机开 VPN 时下载直接失败（0% 报错），
 * 而浏览器（走 App 自己的网络栈）可以正常下载。改用 OkHttp 在 App 进程内下载，
 * 走 App 的网络栈，VPN 下正常，且能跟随重定向、支持取消、进度回调更及时。
 *
 * - 下载跑在 applicationScope（与对话框生命周期解耦），点"后台下载"收起对话框
 *   后继续下；Launcher 常驻，进程不会被杀。
 * - 完成自动弹安装（promptInstall 带 FLAG_ACTIVITY_NEW_TASK，可用 applicationContext）。
 * - 对话框通过 state StateFlow 显示进度；失败置 Failed，对话框显示重试。
 */
object OtaDownloader {

    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Float) : State
        data class Success(val file: File) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 开始下载。已在下载中则忽略（防双击）。 */
    fun start(context: Context, info: OtaInfo) {
        if (_state.value is State.Downloading) return
        val appContext = context.applicationContext
        // 清旧残留包
        OtaUpdater.downloadedApk(appContext)?.delete()
        OtaUpdater.markDownloadStart(appContext, info.versionCode)
        _state.value = State.Downloading(0f)
        job = scope.launch {
            try {
                val req = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "AI-Launcher-OTA")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _state.value = State.Failed
                        return@launch
                    }
                    val body = resp.body
                    if (body == null) {
                        _state.value = State.Failed
                        return@launch
                    }
                    val total = body.contentLength() // 可能为 -1（未知）
                    val file = File(
                        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        OtaUpdater.APK_FILE_NAME
                    )
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buf = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var lastEmit = 0L
                            while (true) {
                                ensureActive() // 支持取消
                                val n = input.read(buf)
                                if (n == -1) break
                                output.write(buf, 0, n)
                                downloaded += n
                                // 进度节流：每 200ms 或每 2% 更新一次，避免 StateFlow 刷太频
                                val now = System.currentTimeMillis()
                                if (total > 0 && (now - lastEmit > 200 || downloaded == total)) {
                                    lastEmit = now
                                    _state.value = State.Downloading(
                                        (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                    _state.value = State.Downloading(1f)
                    _state.value = State.Success(file)
                    // 下载完成直接弹安装（Bob 要求）
                    OtaUpdater.markDownloadComplete(appContext, info.versionCode)
                    if (OtaUpdater.promptInstall(appContext, file)) {
                        OtaUpdater.markInstallPrompted(appContext)
                    }
                }
            } catch (e: CancellationException) {
                _state.value = State.Idle
                throw e
            } catch (_: Exception) {
                _state.value = State.Failed
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    /** 重置为 Idle（对话框关闭时调用，不中断后台下载）。 */
    fun resetIfNotDownloading() {
        if (_state.value !is State.Downloading) {
            _state.value = State.Idle
        }
    }
}
