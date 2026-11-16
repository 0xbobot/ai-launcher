package com.bobot.ailauncher.data

import com.bobot.ailauncher.BuildConfig
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 大模型意图路由结果 */
data class LlmRouteResult(
    val capabilityId: String,
    val params: Map<String, String>,
    val reply: String
)

/** LLM 调用结果：成功带路由结果，失败带可直接展示给用户的错误文案 */
sealed interface LlmCallResult {
    data class Ok(val route: LlmRouteResult) : LlmCallResult
    data class Err(val message: String) : LlmCallResult
}

/**
 * 大模型配置：base_url / model 存 SharedPreferences "llm"；
 * api_key 自 v0.68.89 起加密存储，旧明文 Key 首次读取时自动迁移
 * （迁移逻辑见 [LlmKeyStore]，失败回退明文不丢 Key）。
 * 加密层实现（并行线 B）：v0.68.89–.100 为 EncryptedSharedPreferences
 * "llm_secure"（security-crypto alpha06）；现行 = [secureStore] 的
 * Android Keystore AES/GCM 直连实现，旧格式仅供一次性迁移读取。
 */
object LlmConfig {
    private const val PREFS = "llm"
    private const val SECURE_PREFS = "llm_secure"
    private const val KEYSTORE_PREFS = "llm_keystore"
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-flash"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var securePrefsCache: SharedPreferences? = null

    /**
     * 旧 alpha06 加密层（"llm_secure"）；初始化失败（Keystore 异常等）返回 null。
     * 并行线 B 起**仅供** [MigratingSecureStore] 一次性迁移读取，新写入一律走
     * [secureStore]；迁移随版本铺满后的下一批，连同本函数与 security-crypto
     * 依赖一起删除（本批保留依赖就是为了读它）。
     */
    private fun securePrefs(context: Context): SharedPreferences? {
        securePrefsCache?.let { return it }
        val created = runCatching {
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
        if (created != null) securePrefsCache = created
        return created
    }

    private fun SharedPreferences.asStore(): LlmKeyStore.Store =
        object : LlmKeyStore.Store {
            override fun read(key: String): String? = getString(key, null)
            override fun write(key: String, value: String) {
                edit().putString(key, value).apply()
            }
            override fun delete(key: String) {
                edit().remove(key).apply()
            }
        }

    private const val KEY_MIG_FAIL_LOGGED = "keystore_mig_fail_logged"

    @Volatile
    private var secureStoreCache: LlmKeyStore.Store? = null

    /**
     * 现行加密层（并行线 B）：[KeystoreCipherStore]（Android Keystore
     * AES/GCM 直连、零三方依赖）外包 [MigratingSecureStore]，把旧格式里的
     * Key 一次性迁入新格式；[LlmKeyStore] 的读/写/probe 逻辑零改，仍以
     * 本组合为「加密层」。Keystore 不可用时其读写抛异常 → LlmKeyStore 按
     * 既有契约回退明文、设置页明示（v0.68.100 行为，不静默）。
     * 迁移失败用标志位记一次（防重复写）；失败原因不记 message、不带 Key 本身。
     */
    private fun secureStore(context: Context): LlmKeyStore.Store {
        secureStoreCache?.let { return it }
        val appContext = context.applicationContext
        val created = MigratingSecureStore(
            newStore = KeystoreCipherStore(
                appContext.getSharedPreferences(KEYSTORE_PREFS, Context.MODE_PRIVATE),
                AesGcmBox { AndroidKeystoreKeys.loadOrCreate() }
            ),
            legacyStore = securePrefs(appContext)?.asStore(),
            onMigrated = {
                prefs(appContext).edit().remove(KEY_MIG_FAIL_LOGGED).apply()
            },
            onMigrationFailed = {
                val p = prefs(appContext)
                if (!p.getBoolean(KEY_MIG_FAIL_LOGGED, false)) {
                    p.edit().putBoolean(KEY_MIG_FAIL_LOGGED, true).apply()
                }
            }
        )
        secureStoreCache = created
        return created
    }

    fun getApiKey(context: Context): String {
        val key = LlmKeyStore.readApiKey(
            prefs(context).asStore(),
            secureStore(context)
        )
        // v0.68.100（卫生批）：顺手做一次存放形态记账——降级/恢复的时点
        // 不能只靠用户打开设置页才发现，存放形态要在事件发生时就记账
        runCatching { keyStorageStatus(context) }
        return key
    }

    private const val KEY_FALLBACK_SINCE = "key_fallback_since"

    /** 设置页明示用：Key 当前存放形态 + 降级起始时间（未降级时为 0） */
    data class KeyStorageStatus(
        val mode: LlmKeyStore.StorageMode,
        val fallbackSince: Long
    )

    /**
     * 探测 Key 存放形态，并记账状态迁移：
     * 首次发现明文降级 → 记起始时间；
     * 恢复加密（或 Key 已清空）→ 清掉起始时间。幂等，重复调用不重复记账。
     */
    fun keyStorageStatus(context: Context): KeyStorageStatus {
        val mode = LlmKeyStore.probeMode(
            prefs(context).asStore(),
            secureStore(context)
        )
        val p = prefs(context)
        var since = p.getLong(KEY_FALLBACK_SINCE, 0L)
        when {
            mode == LlmKeyStore.StorageMode.PLAIN_FALLBACK && since == 0L -> {
                since = System.currentTimeMillis()
                p.edit().putLong(KEY_FALLBACK_SINCE, since).apply()
            }
            mode != LlmKeyStore.StorageMode.PLAIN_FALLBACK && since != 0L -> {
                p.edit().remove(KEY_FALLBACK_SINCE).apply()
                since = 0L
            }
        }
        return KeyStorageStatus(mode, since)
    }

    fun getBaseUrl(context: Context): String =
        prefs(context).getString("base_url", DEFAULT_BASE_URL).orEmpty()
            .ifBlank { DEFAULT_BASE_URL }

    fun getModel(context: Context): String =
        prefs(context).getString("model", DEFAULT_MODEL).orEmpty()
            .ifBlank { DEFAULT_MODEL }

    fun hasKey(context: Context): Boolean = getApiKey(context).isNotBlank()

    fun save(context: Context, apiKey: String, baseUrl: String, model: String) {
        val plain = prefs(context)
        LlmKeyStore.writeApiKey(
            plain.asStore(),
            secureStore(context),
            apiKey.trim()
        )
        plain.edit()
            .putString("base_url", normalizeBaseUrl(baseUrl).ifBlank { DEFAULT_BASE_URL })
            .putString("model", model.trim())
            .apply()
    }

    /** baseUrl 规范化：去首尾空格、去末尾斜杠，再拼 /chat/completions */
    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')
}

/**
 * LLM 意图路由：OpenAI 兼容的 /chat/completions 接口。
 * 要求模型只返回 JSON：{"capability_id":"...","params":{"from":"","to":""},"reply":"..."}，
 * capability_id 不在白名单内会被强制改写为 "none"。
 * 网络请求固定在 Dispatchers.IO，15 秒超时。
 * 失败不再吞成 null，而是返回 Err(message)：HTTP 错误带状态码+服务端 error.message，
 * 超时/网络异常提示检查网络——调用方直接 toast 给用户看。
 */
object LlmRouter {

    /** 底层 /chat/completions 调用结果：HTTP 状态码 + 响应体原文（只读一次） */
    data class RawChatResult(
        val httpCode: Int,
        val rawBody: String,
        /** 非空表示网络层失败（超时/断网等），此时 httpCode 为 -1 */
        val networkError: String?
    )

    /**
     * 底层请求链路（意图框 chat 与 AI 智能分类共用，同一套已验证的写法）：
     * 同一个 URL 拼法、同一个请求体构造风格、同一个 OkHttpClient 配置、同一个 header。
     * 注意：不发 response_format 参数（部分中转端点不支持，会回 200 空 body）。
     * 每次调用打 Log.d（TAG 可指定）：请求体字节数、HTTP code、响应体字节数＋
     * 工具调用次数——只记数量，绝不记响应体原文（正式版审计点名；查证结果
     * 进日志就泄漏了）。[safetyCheck] 为真时加 safety_check=high（DeepSeek
     * 全家桶 API，官方文档）：服务端做基础风控（审输入输出），同时忽略客户端
     * 传的 temperature 等采样参数；输入不合规返回 HTTP 400、输出违规返回
     * refusal 标记（调用方的 validatePrepLines 会把含「无法/抱歉」的整段丢弃）。
     */
    suspend fun postChatCompletions(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String,
        temperature: Double,
        maxTokens: Int,
        tag: String = "LlmRouter",
        safetyCheck: Boolean = false
    ): RawChatResult = withContext(Dispatchers.IO) {
        try {
            val url = "${LlmConfig.normalizeBaseUrl(baseUrl)}/chat/completions"
            val bodyJson = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
            if (safetyCheck) bodyJson.put("safety_check", JSONObject().put("level", "high"))
            val bodyStr = bodyJson.toString()
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    tag,
                    "POST $url model=$model temp=$temperature maxTokens=$maxTokens " +
                        "bodyBytes=${bodyStr.toByteArray().size}"
                )
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client().newCall(request).execute().use { resp ->
                // body 只读一次：ResponseBody 读第二次会是空串
                val rawBody = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        tag,
                        "HTTP ${resp.code} bodyBytes=${rawBody.toByteArray().size} " +
                            "toolCalls=${countToolCalls(rawBody)}"
                    )
                }
                RawChatResult(resp.code, rawBody, null)
            }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) android.util.Log.d(tag, "network error: ${e.message}")
            RawChatResult(-1, "", e.message ?: "网络异常")
        }
    }

    /** 数响应里的工具调用次数（只记数量，供日志用；解析失败记 0） */
    private fun countToolCalls(rawBody: String): Int = try {
        JSONObject(rawBody)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optJSONArray("tool_calls")?.length() ?: 0
    } catch (_: Exception) {
        0
    }

    // ---------- DeepSeek 独立搜索／抓取（智能卡片试点 B 路线，Bob 2026-11-15 批） ----------

    /** 独立搜索接口返回的一条结果（官方 Search & Fetch 指南：标题／链接／摘要／正文／发布时间） */
    data class SearchResultItem(
        val title: String,
        val url: String,
        val snippet: String,
        val summary: String,
        val rerankScore: Double,
        val authority: Int,
        val date: String?
    )

    /** 独立抓取接口返回的一页（标题＋Markdown 正文） */
    data class FetchResult(val title: String, val content: String)

    /** 搜索／抓取专用 client：各 10 秒超时（试点方案 5.4），与模型调用的 15 秒分开算 */
    private fun toolClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 独立搜索：POST {baseUrl}/v1/tools/search（官方文档）。
     * query 须 1–128 字符（不合规不发请求）；count 1–50，默认 5。
     * 只记数量（httpCode＋字节数＋命中条数），不记搜索词原文、不记返回正文。
     * 非 2xx／异常／空结果返回 null（调用方静默处理、不重试）。
     */
    suspend fun search(
        baseUrl: String,
        apiKey: String,
        query: String,
        count: Int = 5
    ): List<SearchResultItem>? = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length > 128 || count !in 1..50) return@withContext null
        try {
            val url = "${LlmConfig.normalizeBaseUrl(baseUrl)}/v1/tools/search"
            val bodyStr = JSONObject()
                .put("query", query)
                .put("count", count)
                .toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            toolClient().newCall(request).execute().use { resp ->
                val rawBody = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
                val items = if (resp.code in 200..299) parseSearchResults(rawBody) else null
                android.util.Log.d(
                    "LlmRouter",
                    "search HTTP ${resp.code} bodyBytes=${rawBody.toByteArray().size} " +
                        "results=${items?.size ?: -1}"
                )
                items?.ifEmpty { null }
            }
        } catch (_: IOException) {
            null
        }
    }

    /** 解析搜索响应里的 results 数组（字段缺失按空值处理，整段解析失败返回 null） */
    private fun parseSearchResults(rawBody: String): List<SearchResultItem>? = try {
        val arr = JSONObject(rawBody).optJSONArray("results") ?: return null
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            SearchResultItem(
                title = o.optString("title"),
                url = o.optString("url"),
                snippet = o.optString("snippet"),
                summary = o.optString("summary"),
                rerankScore = o.optDouble("rerank_score", 0.0),
                authority = o.optInt("authority", -1),
                date = o.optString("date").ifBlank { null }
            )
        }.filter { it.url.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /**
     * 独立抓取：POST {baseUrl}/v1/tools/fetch（官方文档）。
     * url 须 ≤8192 字符、http(s) 带 host、不含用户名密码（不合规不发请求）。
     * 只记数量（httpCode＋字节数），不记 URL、不记抓回来的正文。
     * 非 2xx／异常／空内容返回 null（调用方静默处理、不重试）。
     */
    suspend fun fetch(baseUrl: String, apiKey: String, url: String): FetchResult? =
        withContext(Dispatchers.IO) {
            if (!isFetchableUrl(url)) return@withContext null
            try {
                val endpoint = "${LlmConfig.normalizeBaseUrl(baseUrl)}/v1/tools/fetch"
                val bodyStr = JSONObject().put("url", url).toString()
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                toolClient().newCall(request).execute().use { resp ->
                    val rawBody = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
                    val result = if (resp.code in 200..299) parseFetchResult(rawBody) else null
                    android.util.Log.d(
                        "LlmRouter",
                        "fetch HTTP ${resp.code} bodyBytes=${rawBody.toByteArray().size} " +
                            "contentBytes=${result?.content?.toByteArray()?.size ?: -1}"
                    )
                    result
                }
            } catch (_: IOException) {
                null
            }
        }

    /** 抓取 URL 准入：≤8192 字符、http(s)、带 host、不含用户名密码（官方文档原样） */
    private fun isFetchableUrl(url: String): Boolean {
        if (url.isBlank() || url.length > 8192) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val host = url.substringAfter("://").substringBefore("/")
            .substringBefore("?").substringBefore("#")
        return host.isNotBlank() && "@" !in host
    }

    /** 解析抓取响应：title＋content（Markdown）；空内容返回 null */
    private fun parseFetchResult(rawBody: String): FetchResult? = try {
        val o = JSONObject(rawBody)
        val content = o.optString("content")
        if (content.isBlank()) null else FetchResult(o.optString("title"), content)
    } catch (_: Exception) {
        null
    }

    /** 把 HTTP 错误拼成 "状态码: 服务端 error.message" 的可读文案（body 已预读传入） */
    private fun httpErrorText(code: Int, rawBody: String): String {
        val serverMsg = try {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }.ifBlank { rawBody.take(200).ifBlank { "请求被拒绝" } }
        return "$code: $serverMsg".take(300)
    }

    private val SYSTEM_PROMPT = """
        你是一个手机桌面助手的意图路由模块。用户说一句话，你判断它对应哪个能力。
        能力 id 列表：
        ditie(地铁), dache(打车), hangban(航班), zuche(租车), huoche(火车), jiudian(酒店),
        daohang(导航),
        doubao(豆包), deepseek(DeepSeek), kimi(Kimi), yuanbao(元宝),
        zhifubao(支付宝),
        wechat(微信), qq(QQ), weibo(微博), xiaohongshu(小红书), douyin(抖音),
        feishu(飞书), calendar(日历), mail(邮箱),
        meituan(美团), eleme(饿了么), dianping(大众点评),
        jd(京东), taobao(淘宝), pdd(拼多多),
        yinyue(音乐), shipin(视频), liaotian(聊天)。
        无法对应任何能力时，capability_id 填 "none"。
        只返回 JSON，不要任何其他文字，格式如下：
        {"capability_id":"dache","params":{"from":"","to":""},"reply":"一句话回复"}
        params 里尽量提取 from(起点) / to(终点或目的地)，没有就填空字符串。
    """.trimIndent()

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun route(
        baseUrl: String,
        apiKey: String,
        model: String,
        input: String,
        validIds: Set<String>
    ): LlmCallResult = withContext(Dispatchers.IO) {
        try {
            val url = "${LlmConfig.normalizeBaseUrl(baseUrl)}/chat/completions"
            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", input))
                })
                .put("temperature", 0.1)
                .put("max_tokens", 300)
                .toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext LlmCallResult.Err(extractHttpError(resp.code, resp))
                }
                val parsed = parseResult(resp.body?.string().orEmpty(), validIds)
                if (parsed != null) LlmCallResult.Ok(parsed)
                else LlmCallResult.Err("没听懂，换个说法再试")
            }
        } catch (e: IOException) {
            LlmCallResult.Err("网络超时，请检查网络")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("LlmRouter", "llm call failed", e)
            LlmCallResult.Err("请求失败，请稍后再试")
        }
    }

    /**
     * 设置页「测试连接」：返回 null 表示成功，否则返回可直接展示的错误文案。
     */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): String? =
        withContext(Dispatchers.IO) {
            when (val r = route(baseUrl, apiKey, model, "你好", setOf("none"))) {
                is LlmCallResult.Ok -> null
                is LlmCallResult.Err -> r.message
            }
        }

    /** 把 HTTP 错误拼成 "状态码: 服务端 error.message" 的可读文案 */
    private fun extractHttpError(code: Int, resp: okhttp3.Response): String {
        val bodyStr = try {
            resp.body?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }
        val serverMsg = try {
            JSONObject(bodyStr).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }.ifBlank { bodyStr.take(200).ifBlank { "请求被拒绝" } }
        return "$code: $serverMsg".take(300)
    }

    private fun parseResult(raw: String, validIds: Set<String>): LlmRouteResult? {
        return try {
            val content = JSONObject(raw)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            // 模型可能包了 markdown 代码块，先清洗
            val clean = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = JSONObject(clean)
            var id = obj.optString("capability_id", "none")
            if (id !in validIds) id = "none"
            val paramsObj = obj.optJSONObject("params")
            val params = mutableMapOf<String, String>()
            paramsObj?.keys()?.forEach { k -> params[k] = paramsObj.optString(k, "") }
            LlmRouteResult(id, params, obj.optString("reply", ""))
        } catch (_: Exception) {
            null
        }
    }
}
