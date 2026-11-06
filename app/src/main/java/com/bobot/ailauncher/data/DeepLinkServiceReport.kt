package com.bobot.ailauncher.data

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 深链服务上报（Bob 2026-11-06：识别不到时的反馈闭环——用户在选择器里点了一个
 * 应用后，把这次的映射同步上报给深链服务，让更多人用上）。
 *
 * 调服务的 POST /v1/report（调用规范 v1 的回传口）。best-effort：
 * 服务还没部署（Bob 还没登录 Cloudflare），地址未配时直接跳过、不发任何请求；
 * 配了也只在后台线程发，5 秒超时、全异常吞掉，绝不卡住、不报错、
 * 不影响本机映射（本机映射先存、上报后发，两者互不阻塞）。
 *
 * 口径差（收据里写明，服务那条线定）：服务 v1 的 /v1/report 目前只校验
 * request_id + outcome（opened/fallback_used/not_installed/failed），
 * 不存用户内容；本批把映射字段（type/intent/query）随包带上，
 * 服务侧要不要收、怎么收，由服务那条线决定。
 *
 * JSON 串手工拼（不用 org.json.JSONObject：本地单测跑在纯 JVM 上，
 * android.jar 里的 JSONObject 方法会抛 Stub!，手工拼串才能单测）。
 */
object DeepLinkServiceReport {
    /** 深链服务地址（Cloudflare Workers）。未部署时为空，上报直接跳过。部署后填入。 */
    const val SERVICE_BASE_URL: String = ""

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    /** 上报体（纯函数，可单测）：request_id + outcome（服务 v1 校验要的）＋映射字段。 */
    fun buildReportBody(
        requestId: String,
        opened: Boolean,
        packageName: String,
        capabilityId: String?,
        query: String
    ): String {
        val intent = capabilityId?.ifBlank { null } ?: "unknown"
        return "{" +
            "\"request_id\":\"${jsonEscape(requestId)}\"," +
            "\"outcome\":\"${if (opened) "opened" else "failed"}\"," +
            "\"provider\":\"${jsonEscape(packageName)}\"," +
            "\"type\":\"user_mapping\"," +
            "\"intent\":\"${jsonEscape(intent)}\"," +
            "\"query\":\"${jsonEscape(query)}\"," +
            "\"source\":\"app-center-feedback\"" +
            "}"
    }

    /**
     * best-effort 上报（调用方在 IO 线程调；地址未配直接返回）。
     * 只在用户在选择器里显式点了一个应用后调一次；只发这一次的查询，
     * 不发别的。任何异常都吞掉，绝不抛。
     */
    fun reportUserMapping(
        query: String,
        packageName: String,
        capabilityId: String?,
        opened: Boolean
    ) {
        if (SERVICE_BASE_URL.isBlank()) return
        try {
            val body = buildReportBody(
                UUID.randomUUID().toString(),
                opened,
                packageName,
                capabilityId,
                query
            )
            postReport(SERVICE_BASE_URL.trimEnd('/') + "/v1/report", body)
        } catch (_: Exception) {
        }
    }

    /** 同步 POST（测试只验 build 与空地址短路，不发真请求）。 */
    internal fun postReport(url: String, body: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            // 只把请求发出去、读掉响应码即收尾；2xx/4xx/5xx 一律不报错（best-effort）。
            try {
                conn.responseCode
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }
}
