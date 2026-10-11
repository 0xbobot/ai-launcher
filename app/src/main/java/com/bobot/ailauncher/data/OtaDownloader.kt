package com.bobot.ailauncher.data

import android.content.Context
import android.net.ConnectivityManager
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
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
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

    // v0.41.22：client 需要 Context 来读系统代理，改为按需构建（缓存）
    private var cachedClient: OkHttpClient? = null
    private fun getClient(context: Context): OkHttpClient {
        cachedClient?.let { return it }
        val proxy = getSystemProxy(context)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            // v0.41.22（Bob：下载比 Chrome 慢百倍）——显式走系统代理。
            // 部分代理 App（VPN 模式）不走系统 ProxySelector，导致 App 直连
            // GitHub（国内慢），而 Chrome 走代理快。这里用 ConnectivityManager
            // 显式读取当前网络的代理。
            .apply {
                if (proxy != null) proxy(proxy)
            }
            // 国内 IPv6 到 GitHub 常绕路，优先 IPv4
            .dns(PreferIPv4Dns())
            .build()
        cachedClient = client
        return client
    }

    /**
     * 读取系统代理：优先 ConnectivityManager.defaultProxy（API 23+，
     * 反映当前默认网络的代理设置），兜底 ProxySelector。
     */
    private fun getSystemProxy(context: Context): Proxy? {
        // 方法1：ConnectivityManager（最可靠，反映当前网络）
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val proxyInfo = cm.defaultProxy
            if (proxyInfo != null && proxyInfo.host != null) {
                return Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyInfo.host, proxyInfo.port))
            }
        } catch (_: Exception) {}
        // 方法2：系统 ProxySelector
        try {
            val proxies = ProxySelector.getDefault()?.select(URI("https://github.com"))
            return proxies?.firstOrNull { it.type() != Proxy.Type.DIRECT }
        } catch (_: Exception) {}
        return null
    }

    /** 优先 IPv4 的 DNS（国内 IPv6 到 GitHub 慢） */
    private class PreferIPv4Dns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            return if (v4.isNotEmpty()) v4 else all
        }
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
            // v0.65.6：partial 文件引用外提——失败/中断时删掉，不留残缺包被误装
            var file: File? = null
            try {
                val req = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "AI-Launcher-OTA")
                    .build()
                getClient(appContext).newCall(req).execute().use { resp ->
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
                    // v0.65.6：记录期望长度，用于完整性校验
                    OtaUpdater.markDownloadSize(appContext, total)
                    file = File(
                        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        OtaUpdater.APK_FILE_NAME
                    )
                    val outFile = file!!
                    body.byteStream().use { input ->
                        outFile.outputStream().use { output ->
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
                            // v0.65.6：长度校验——流提前结束（锁屏断网等）算失败，不装坏包
                            if (total > 0 && downloaded != total) {
                                throw IOException(
                                    "incomplete download: $downloaded/$total"
                                )
                            }
                        }
                    }
                    // v0.65.6：APK 可解析校验——截断/损坏包直接判失败
                    if (!OtaUpdater.isValidApk(appContext, outFile)) {
                        throw IOException("downloaded apk failed to parse")
                    }
                    _state.value = State.Downloading(1f)
                    _state.value = State.Success(outFile)
                    // 下载完成直接弹安装（Bob 要求）
                    OtaUpdater.markDownloadComplete(appContext, info.versionCode)
                    if (OtaUpdater.promptInstall(appContext, outFile)) {
                        OtaUpdater.markInstallPrompted(appContext)
                    }
                }
            } catch (e: CancellationException) {
                // v0.65.6：取消也删 partial，下次 start() 会重下
                file?.delete()
                _state.value = State.Idle
                throw e
            } catch (_: Exception) {
                // v0.65.6：任何失败都删 partial——不完整的文件绝不留给安装流程
                file?.delete()
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
