package com.bobot.ailauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min

/**
 * 分片并行下载器（纯逻辑：只依赖 OkHttp + JDK + coroutines，不碰 Android API，可 JVM 单测）。
 *
 * 为什么需要它：旧 OtaDownloader 是单个 OkHttp 连接顺序读流下载 GitHub Release 包，
 * 跨境链路上单条 TCP 流吃不满带宽（慢启动/拥塞窗口/单流丢包重传），且失败即删文件
 * 从零重下，慢网络下体感成倍放大。Chrome 靠多连接并行 + 续传把这两个问题都绕开了。
 *
 * 本类的做法：
 * - 先以 `Range: bytes=0-0` 探测：服务端回 206 且给出 Content-Range 总长 → 分片路径；
 *   回 200（忽略 Range）→ 该响应体就是全文件，直接当单流写完，不浪费已发请求；
 *   总长未知或文件小于 [minChunkedBytes] → 另发普通 GET 单流下载。
 * - 分片路径：文件切成 [chunkCount] 片并行拉（同步 execute()，不受 OkHttp 异步
 *   Dispatcher 的 per-host=5 限制），每片先写 `part-N.tmp`、长度核对后 rename 为
 *   `part-N`。`part-N` 存在且长度正确 = 该片完整，是续传的唯一凭据。
 * - 续传：workDir 下 `meta.properties` 记录 url/total/chunkSize；再次 download 同一
 *   url 时完整的片直接跳过，只补缺失/不完整的片。url 变了（新版本）自动清盘重下。
 * - 拼装：全部片就绪后按序拼到 `targetFile.tmp`，长度与 total 核对一致才 rename
 *   为 targetFile 并清掉 workDir——targetFile 出现即完整，半成品永不以其面貌出现。
 * - 取消/失败：正在写的 tmp 片保留在盘上（下次重下该片时覆盖），已完成的 part
 *   保留供续传；调用方决定何时清盘（换版本时由 meta 校验自动清）。
 */
class ChunkedDownloader(
    private val client: OkHttpClient,
    private val chunkCount: Int = DEFAULT_CHUNK_COUNT,
    private val minChunkedBytes: Long = DEFAULT_MIN_CHUNKED_BYTES
) {

    /** 下载进度：total 为 -1 表示未知（单流且无 Content-Length 时） */
    data class Progress(val downloaded: Long, val total: Long)

    /**
     * 下载 [url] 到 [targetFile]。[workDir] 是分片与 meta 的存放目录（调用方私有目录）。
     * 成功返回 targetFile；失败抛 IOException（协程取消抛 CancellationException）。
     */
    suspend fun download(
        url: String,
        userAgent: String,
        workDir: File,
        targetFile: File,
        onProgress: (Progress) -> Unit = {}
    ): File {
        workDir.mkdirs()
        targetFile.parentFile?.mkdirs()

        // ---- 探测：1 字节 Range 请求 ----
        val probeReq = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-0")
            .build()
        val decision = withContext(Dispatchers.IO) {
            client.newCall(probeReq).execute().use { resp ->
                when {
                    resp.code == 206 -> {
                        val total = parseContentRangeTotal(resp.header("Content-Range"))
                        if (total != null && total > minChunkedBytes) {
                            ProbeDecision.Chunked(total)
                        } else {
                            ProbeDecision.RetryPlain
                        }
                    }
                    resp.code == 200 -> {
                        // 服务端忽略 Range：这个响应体就是完整文件，直接写完
                        val total = resp.body?.contentLength() ?: -1L
                        val body = resp.body ?: throw IOException("empty body")
                        writeStreamToFile(body.byteStream(), File(workDir, TMP_TARGET), total, onProgress)
                        ProbeDecision.Done
                    }
                    else -> throw IOException("probe failed: HTTP ${resp.code}")
                }
            }
        }

        when (decision) {
            is ProbeDecision.Done -> {
                promoteTmpTarget(workDir, targetFile, expected = -1L)
                return targetFile
            }
            is ProbeDecision.RetryPlain -> {
                downloadPlain(url, userAgent, workDir, targetFile, onProgress)
                return targetFile
            }
            is ProbeDecision.Chunked -> {
                downloadChunked(url, userAgent, decision.total, workDir, targetFile, onProgress)
                return targetFile
            }
        }
    }

    /** 单流下载（不支持 Range / 小文件）：普通 GET 写 tmp 再 rename */
    private suspend fun downloadPlain(
        url: String,
        userAgent: String,
        workDir: File,
        targetFile: File,
        onProgress: (Progress) -> Unit
    ) {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("download failed: HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty body")
                val total = body.contentLength()
                writeStreamToFile(body.byteStream(), File(workDir, TMP_TARGET), total, onProgress)
            }
        }
        promoteTmpTarget(workDir, targetFile, expected = -1L)
    }

    private suspend fun downloadChunked(
        url: String,
        userAgent: String,
        total: Long,
        workDir: File,
        targetFile: File,
        onProgress: (Progress) -> Unit
    ) {
        val chunks = chunkCount.coerceAtLeast(1)
        val chunkSize = ceil(total.toDouble() / chunks).toLong().coerceAtLeast(1L)
        val partTotal = ceil(total.toDouble() / chunkSize).toInt()

        // ---- 续传状态：meta 与本次任务不一致则清盘 ----
        val meta = readMeta(workDir)
        if (meta == null || meta.url != url || meta.total != total || meta.chunkSize != chunkSize) {
            clearWorkDir(workDir)
            writeMeta(workDir, url, total, chunkSize)
        }

        // 每片已落盘字节数（进度汇总用）；完整的片直接记满
        val perChunk = LongArray(partTotal)
        var resumedBytes = 0L
        for (i in 0 until partTotal) {
            val len = expectedChunkLength(i, chunkSize, total)
            val part = File(workDir, partName(i))
            if (part.isFile && part.length() == len) {
                perChunk[i] = len
                resumedBytes += len
            }
        }
        onProgress(Progress(resumedBytes, total))
        val progressLock = Any()
        fun emitProgress() {
            val sum = synchronized(progressLock) { perChunk.sum() }
            onProgress(Progress(sum, total))
        }

        // ---- 并行拉缺失片 ----
        coroutineScope {
            val jobs = (0 until partTotal).map { i ->
                async(Dispatchers.IO) {
                    val len = expectedChunkLength(i, chunkSize, total)
                    val part = File(workDir, partName(i))
                    if (part.isFile && part.length() == len) return@async // 已有完整片，续传跳过
                    val start = i * chunkSize
                    val end = min(start + chunkSize, total) - 1
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Range", "bytes=$start-$end")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.code != 206) {
                            throw IOException("chunk $i: expected 206, got ${resp.code}")
                        }
                        val body = resp.body ?: throw IOException("chunk $i: empty body")
                        val tmp = File(workDir, "${partName(i)}.tmp")
                        body.byteStream().use { input ->
                            FileOutputStream(tmp).use { out ->
                                val buf = ByteArray(BUFFER_SIZE)
                                var written = 0L
                                var lastEmit = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buf)
                                    if (n == -1) break
                                    out.write(buf, 0, n)
                                    written += n
                                    synchronized(progressLock) { perChunk[i] = written }
                                    if (written - lastEmit >= BUFFER_SIZE * 4) {
                                        lastEmit = written
                                        emitProgress()
                                    }
                                }
                                out.flush()
                            }
                        }
                        if (tmp.length() != len) {
                            throw IOException("chunk $i incomplete: ${tmp.length()}/$len")
                        }
                        if (!tmp.renameTo(part)) {
                            tmp.copyTo(part, overwrite = true)
                            tmp.delete()
                        }
                        synchronized(progressLock) { perChunk[i] = len }
                        emitProgress()
                    }
                }
            }
            jobs.forEach { it.await() }
        }

        // ---- 拼装：按序拼到 tmp，长度核对后 rename 为正式文件，清盘 ----
        val tmpTarget = File(workDir, TMP_TARGET)
        FileOutputStream(tmpTarget).use { out ->
            val buf = ByteArray(BUFFER_SIZE)
            for (i in 0 until partTotal) {
                FileInputStream(File(workDir, partName(i))).use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                    }
                }
            }
            out.flush()
        }
        if (tmpTarget.length() != total) {
            throw IOException("assembled size mismatch: ${tmpTarget.length()}/$total")
        }
        promoteTmpTarget(workDir, targetFile, expected = total)
        clearWorkDir(workDir)
        onProgress(Progress(total, total))
    }

    /** 把 workDir 里的 tmp 成品提升为 targetFile（同盘 rename，失败则拷贝） */
    private fun promoteTmpTarget(workDir: File, targetFile: File, expected: Long) {
        val tmp = File(workDir, TMP_TARGET)
        if (!tmp.isFile) throw IOException("assembled file missing")
        if (expected > 0 && tmp.length() != expected) {
            throw IOException("size mismatch: ${tmp.length()}/$expected")
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tmp.renameTo(targetFile)) {
            tmp.copyTo(targetFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun writeStreamToFile(
        input: java.io.InputStream,
        dest: File,
        total: Long,
        onProgress: (Progress) -> Unit
    ) {
        var downloaded = 0L
        var lastEmit = 0L
        input.use { inp ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = inp.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    downloaded += n
                    if (downloaded - lastEmit >= BUFFER_SIZE * 4) {
                        lastEmit = downloaded
                        onProgress(Progress(downloaded, total))
                    }
                }
                out.flush()
            }
        }
        if (total > 0 && downloaded != total) {
            throw IOException("incomplete stream: $downloaded/$total")
        }
        onProgress(Progress(downloaded, total))
    }

    // ---- meta（续传凭据）：格式刻意简单，读坏/字段缺失一律当无 meta 清盘重下 ----

    private data class Meta(val url: String, val total: Long, val chunkSize: Long)

    private fun readMeta(workDir: File): Meta? {
        val f = File(workDir, META_NAME)
        if (!f.isFile) return null
        return try {
            val props = Properties()
            FileInputStream(f).use { props.load(it) }
            val url = props.getProperty("url") ?: return null
            val total = props.getProperty("total")?.toLongOrNull() ?: return null
            val chunkSize = props.getProperty("chunkSize")?.toLongOrNull() ?: return null
            Meta(url, total, chunkSize)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMeta(workDir: File, url: String, total: Long, chunkSize: Long) {
        val props = Properties().apply {
            setProperty("url", url)
            setProperty("total", total.toString())
            setProperty("chunkSize", chunkSize.toString())
        }
        val tmp = File(workDir, "$META_NAME.tmp")
        FileOutputStream(tmp).use { props.store(it, "chunked-download") }
        val dest = File(workDir, META_NAME)
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun clearWorkDir(workDir: File) {
        workDir.listFiles()?.forEach { it.delete() }
    }

    private fun partName(index: Int) = "part-$index"

    private fun expectedChunkLength(index: Int, chunkSize: Long, total: Long): Long {
        val start = index * chunkSize
        return min(chunkSize, total - start)
    }

    private sealed interface ProbeDecision {
        data class Chunked(val total: Long) : ProbeDecision
        data object RetryPlain : ProbeDecision
        data object Done : ProbeDecision
    }

    companion object {
        const val DEFAULT_CHUNK_COUNT = 6
        const val DEFAULT_MIN_CHUNKED_BYTES = 1L shl 20 // 1MB 以下不值得分片
        private const val BUFFER_SIZE = 64 * 1024
        private const val META_NAME = "meta.properties"
        private const val TMP_TARGET = "assembled.tmp"

        /** 从 `Content-Range: bytes 0-0/18080488` 里解析总长；格式不符返回 null */
        fun parseContentRangeTotal(header: String?): Long? {
            if (header == null) return null
            val slash = header.lastIndexOf('/')
            if (slash < 0) return null
            return header.substring(slash + 1).trim().toLongOrNull()
        }
    }
}
