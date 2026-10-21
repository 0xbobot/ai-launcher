package com.bobot.ailauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.OkHttpClient
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
 *
 * 下载提速（本次改动，Bob：App 内下载明显比 Chrome 慢）：
 * 旧实现是单连接单流顺序下载——跨境到 GitHub 资产 CDN 的链路上单条 TCP 流吃不满
 * 带宽，且失败/取消即删文件从零重下。现在改走 [ChunkedDownloader]：Range 分片
 * 并行拉取（服务端不支持 Range 自动退回单流）+ 片级断点续传；State 带实时速度，
 * 对话框显示 MB/s，用户能直接和 Chrome 对比。
 * 失败/取消不再删已下载分片（续传用）；半成品永不以正式包名出现（拼装校验通过
 * 才落为 APK_FILE_NAME），且 OtaUpdater 的 complete 标记未置位前 shouldPromptInstall
 * 不会命中，所以保留分片不破坏 v0.65.6 的防误装设计。
 */
object OtaDownloader {

    sealed interface State {
        data object Idle : State
        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long = 0L,
            val totalBytes: Long = -1L,
            /** 实时速度（字节/秒，滑动窗口估算）；刚开始或无法估算时为 0 */
            val speedBps: Long = 0L
        ) : State
        data class Success(val file: File) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val USER_AGENT = "AI-Launcher-OTA"
    private const val PARTS_DIR_NAME = "ota-parts"

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

    /** 开始下载。已在下载中则忽略（防双击）。同版本重试会自动续传未完成的分片。 */
    fun start(context: Context, info: OtaInfo) {
        if (_state.value is State.Downloading) return
        val appContext = context.applicationContext
        // 清旧残留包（正式包名只允许出现完整包；分片在 ota-parts 里，由 meta 按 URL 自洽）
        OtaUpdater.downloadedApk(appContext)?.delete()
        OtaUpdater.markDownloadStart(appContext, info.versionCode)
        _state.value = State.Downloading(0f)
        job = scope.launch {
            try {
                val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("downloads dir unavailable")
                val target = File(downloadsDir, OtaUpdater.APK_FILE_NAME)
                val workDir = File(downloadsDir, PARTS_DIR_NAME)

                // 速度估算：保留最近的采样点（时间, 累计字节），用最近 ≤3 秒窗口算速率
                val samples = ArrayDeque<Pair<Long, Long>>()
                var lastEmitAt = 0L
                val downloader = ChunkedDownloader(getClient(appContext))
                val outFile = downloader.download(
                    url = info.apkUrl,
                    userAgent = USER_AGENT,
                    workDir = workDir,
                    targetFile = target
                ) { p ->
                    val now = System.currentTimeMillis()
                    synchronized(samples) {
                        samples.addLast(now to p.downloaded)
                        while (samples.size > 2 && now - samples.first().first > 3_000) {
                            samples.removeFirst()
                        }
                    }
                    // 进度节流：每 200ms 更新一次状态（与旧实现的节流一致）
                    if (now - lastEmitAt >= 200 || (p.total > 0 && p.downloaded == p.total)) {
                        lastEmitAt = now
                        val speed = synchronized(samples) {
                            val first = samples.firstOrNull()
                            val last = samples.lastOrNull()
                            if (first != null && last != null && last.first > first.first) {
                                val dt = (last.first - first.first) / 1000.0
                                if (dt > 0.3) ((last.second - first.second) / dt).toLong() else 0L
                            } else 0L
                        }
                        val progress = if (p.total > 0) {
                            (p.downloaded.toFloat() / p.total).coerceIn(0f, 1f)
                        } else 0f
                        _state.value = State.Downloading(progress, p.downloaded, p.total, speed)
                    }
                }

                // 完整性校验：长度以拼装后的文件为准（分片路径已按 total 核对过）
                OtaUpdater.markDownloadSize(appContext, outFile.length())
                // APK 可解析校验——截断/损坏包直接判失败
                if (!OtaUpdater.isValidApk(appContext, outFile)) {
                    throw IOException("downloaded apk failed to parse")
                }
                // 版本号校验：包内 versionCode 必须与 update.json 声称的一致，
                // 防重定向给到错包时"装了个旧版本还报成功"
                val apkVersion = OtaUpdater.apkVersionCode(appContext, outFile)
                if (apkVersion != null && apkVersion != info.versionCode) {
                    throw IOException("apk version mismatch: $apkVersion != ${info.versionCode}")
                }
                _state.value = State.Downloading(1f, outFile.length(), outFile.length(), 0L)
                _state.value = State.Success(outFile)
                // 下载完成直接弹安装（Bob 要求）
                OtaUpdater.markDownloadComplete(appContext, info.versionCode)
                if (OtaUpdater.promptInstall(appContext, outFile)) {
                    OtaUpdater.markInstallPrompted(appContext)
                }
            } catch (e: CancellationException) {
                // 取消保留已完成分片，下次 start() 同版本自动续传（不再删文件从零重下）
                _state.value = State.Idle
                throw e
            } catch (_: Exception) {
                // 失败同样保留分片供重试续传；正式包名下不会有半成品，不会被误装
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
