package com.bobot.ailauncher.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * ChunkedDownloader 单测：本地假 HTTP 服务端（原生 ServerSocket，零额外依赖），
 * 可精确控制 Range 行为并记录收到的请求。
 *
 * 覆盖：分片拼装字节一致 / 不支持 Range 时退回单流且不浪费请求 /
 * 续传只补缺失片 / 小文件走单流 / Content-Range 解析。
 */
class ChunkedDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()

    private fun makeData(size: Int): ByteArray =
        ByteArray(size) { i -> ((i * 31 + 7) % 256).toByte() }

    @Test(timeout = 60_000)
    fun chunkedAssemblyMatchesSource() = runBlocking {
        val data = makeData(200_003)
        FakeHttpServer(data, supportRange = true).use { server ->
            val workDir = tmp.newFolder("work")
            val target = File(tmp.newFolder("out"), "app.apk")
            val dl = ChunkedDownloader(client, chunkCount = 4, minChunkedBytes = 1024)
            val result = dl.download(server.url, "test-agent", workDir, target)
            assertArrayEquals(data, result.readBytes())
            // 除探测请求（bytes=0-0）外，应有 4 个分片 Range 请求且恰好平铺全文件
            val chunkRanges = server.rangeRequests.filterNotNull().toSet() - "bytes=0-0"
            assertEquals(
                setOf(
                    "bytes=0-50000", "bytes=50001-100001",
                    "bytes=100002-150002", "bytes=150003-200002"
                ),
                chunkRanges
            )
            // 拼装成功后分片目录应已清盘
            assertTrue(workDir.listFiles()?.isEmpty() == true)
        }
    }

    @Test(timeout = 60_000)
    fun fallsBackToSingleStreamWhenRangeUnsupported() = runBlocking {
        val data = makeData(50_000)
        FakeHttpServer(data, supportRange = false).use { server ->
            val workDir = tmp.newFolder("work")
            val target = File(tmp.newFolder("out"), "app.apk")
            val dl = ChunkedDownloader(client, chunkCount = 4, minChunkedBytes = 1024)
            val result = dl.download(server.url, "test-agent", workDir, target)
            assertArrayEquals(data, result.readBytes())
            // 探测请求拿到 200 后直接把响应体写完：总共只应有 1 个请求
            assertEquals(1, server.requestCount.get())
        }
    }

    @Test(timeout = 60_000)
    fun resumeOnlyFetchesMissingChunks() = runBlocking {
        val data = makeData(200_003)
        FakeHttpServer(data, supportRange = true).use { server ->
            val workDir = tmp.newFolder("work")
            val target = File(tmp.newFolder("out"), "app.apk")
            // 预置续传状态：meta 与前两片完整文件（chunkCount=4 → chunkSize=50001）
            val props = Properties().apply {
                setProperty("url", server.url)
                setProperty("total", data.size.toString())
                setProperty("chunkSize", "50001")
            }
            File(workDir, "meta.properties").outputStream().use { props.store(it, "test") }
            File(workDir, "part-0").writeBytes(data.copyOfRange(0, 50001))
            File(workDir, "part-1").writeBytes(data.copyOfRange(50001, 100002))

            val dl = ChunkedDownloader(client, chunkCount = 4, minChunkedBytes = 1024)
            val progresses = mutableListOf<ChunkedDownloader.Progress>()
            val result = dl.download(server.url, "test-agent", workDir, target) {
                progresses.add(it)
            }
            assertArrayEquals(data, result.readBytes())
            // 只应补拉第 2、3 片（外加探测请求 bytes=0-0）
            val fetched = server.rangeRequests.filterNotNull().toSet() - "bytes=0-0"
            assertEquals(setOf("bytes=100002-150002", "bytes=150003-200002"), fetched)
            // 首个进度回调应已包含续传的两片字节（100002）
            assertTrue(progresses.first().downloaded == 100_002L)
        }
    }

    @Test(timeout = 60_000)
    fun smallFileUsesPlainGet() = runBlocking {
        val data = makeData(500)
        FakeHttpServer(data, supportRange = true).use { server ->
            val workDir = tmp.newFolder("work")
            val target = File(tmp.newFolder("out"), "app.apk")
            val dl = ChunkedDownloader(client, chunkCount = 4, minChunkedBytes = 1024)
            val result = dl.download(server.url, "test-agent", workDir, target)
            assertArrayEquals(data, result.readBytes())
            // 探测（206 但总量太小）+ 一次无 Range 的普通 GET
            assertEquals(2, server.requestCount.get())
            assertTrue(server.rangeRequests.contains(null))
        }
    }

    @Test
    fun parsesContentRangeTotal() {
        assertEquals(18_080_488L, ChunkedDownloader.parseContentRangeTotal("bytes 0-0/18080488"))
        assertEquals(null, ChunkedDownloader.parseContentRangeTotal("bytes 0-0/*"))
        assertEquals(null, ChunkedDownloader.parseContentRangeTotal(null))
        assertEquals(null, ChunkedDownloader.parseContentRangeTotal("garbage"))
    }

    /** 假 HTTP 服务端：supportRange=true 时按 Range 头回 206 切片，否则永远 200 全量 */
    private class FakeHttpServer(
        private val data: ByteArray,
        private val supportRange: Boolean
    ) : Closeable {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val url: String = "http://127.0.0.1:${serverSocket.localPort}/app.apk"
        val rangeRequests: MutableList<String?> =
            Collections.synchronizedList(mutableListOf())
        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        private val executor = Executors.newCachedThreadPool()
        @Volatile private var running = true

        init {
            thread(isDaemon = true) {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        executor.execute { handle(socket) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        private fun handle(socket: Socket) {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                reader.readLine() ?: return // request line
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                }
                requestCount.incrementAndGet()
                val range = headers["range"]
                rangeRequests.add(range)
                val out = it.getOutputStream()
                if (supportRange && range != null) {
                    val m = Regex("bytes=(\\d+)-(\\d*)").find(range)!!
                    val start = m.groupValues[1].toInt()
                    val end = if (m.groupValues[2].isEmpty()) data.size - 1
                              else m.groupValues[2].toInt()
                    val len = end - start + 1
                    val head = "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Length: $len\r\n" +
                        "Content-Range: bytes $start-$end/${data.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.US_ASCII))
                    out.write(data, start, len)
                } else {
                    val head = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Length: ${data.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.US_ASCII))
                    out.write(data)
                }
                out.flush()
            }
        }

        override fun close() {
            running = false
            serverSocket.close()
            executor.shutdownNow()
        }
    }
}
