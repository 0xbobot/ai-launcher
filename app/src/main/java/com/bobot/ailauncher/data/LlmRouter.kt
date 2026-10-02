package com.bobot.ailauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 大模型意图路由结果 */
data class LlmRouteResult(
    val capabilityId: String,
    val params: Map<String, String>,
    val reply: String
)

/** 大模型配置：存 SharedPreferences "llm" */
object LlmConfig {
    private const val PREFS = "llm"
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-chat"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        prefs(context).getString("api_key", "").orEmpty()

    fun getBaseUrl(context: Context): String =
        prefs(context).getString("base_url", DEFAULT_BASE_URL).orEmpty()
            .ifBlank { DEFAULT_BASE_URL }

    fun getModel(context: Context): String =
        prefs(context).getString("model", DEFAULT_MODEL).orEmpty()
            .ifBlank { DEFAULT_MODEL }

    fun hasKey(context: Context): Boolean = getApiKey(context).isNotBlank()

    fun save(context: Context, apiKey: String, baseUrl: String, model: String) {
        prefs(context).edit()
            .putString("api_key", apiKey.trim())
            .putString("base_url", baseUrl.trim().trimEnd('/'))
            .putString("model", model.trim())
            .apply()
    }
}

/**
 * LLM 意图路由：OpenAI 兼容的 /chat/completions 接口。
 * 要求模型只返回 JSON：{"capability_id":"...","params":{"from":"","to":""},"reply":"..."}，
 * capability_id 不在白名单内会被强制改写为 "none"。
 * 网络请求固定在 Dispatchers.IO，15 秒超时，所有异常吞掉返回 null（调用方降级）。
 */
object LlmRouter {

    private val SYSTEM_PROMPT = """
        你是一个手机桌面助手的意图路由模块。用户说一句话，你判断它对应哪个能力。
        能力 id 列表：
        ditie(地铁), dache(打车), hangban(航班), zuche(租车), huoche(火车), jiudian(酒店),
        zhifubao(支付宝), wechatpay(微信支付), bank(银行),
        feishu(飞书), calendar(日历), mail(邮箱),
        meituan(美团), eleme(饿了么), dianping(大众点评),
        jd(京东), taobao(淘宝), pdd(拼多多)。
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
    ): LlmRouteResult? = withContext(Dispatchers.IO) {
        try {
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
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                parseResult(resp.body?.string().orEmpty(), validIds)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 设置页「测试连接」：发一个极简请求，能解析即算通 */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                route(baseUrl, apiKey, model, "你好", setOf("none")) != null
            } catch (_: Exception) {
                false
            }
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
