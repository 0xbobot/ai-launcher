package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** AI 智能分类结果 */
sealed interface ClassifyResult {
    data class Ok(val mapping: Map<String, String>, val classifiedCount: Int) : ClassifyResult
    data class Err(val message: String) : ClassifyResult
}

/**
 * AI 智能分类：借助用户在设置页配置的大模型（DeepSeek），
 * 把本机全部已安装应用一次性归入能力分组，补全关键词规则覆盖不到的。
 * 结果写入 CustomCategories 的包名→分类映射，与用户手动长按整理走同一存储。
 */
object AppClassifier {
    private const val BATCH_SIZE = 40

    private val GROUPS = listOf(
        "ai" to "AI",
        "social" to "社交",
        "travel" to "出行",
        "pay" to "支付",
        "work" to "办公",
        "life" to "生活",
        "shop" to "购物"
    )

    private fun systemPrompt(): String = """
        你是一个手机应用分类助手。把用户手机上的应用归入以下分组，只能使用这些分组 id：
        ${GROUPS.joinToString("\n") { (id, name) -> "$id($name)" }}
        规则：
        1. 对用户列出的每一个应用（应用名和包名）都必须分类，一个不许漏。
        2. 只返回严格 JSON，格式为 {"包名":"分组id"}，不要任何其他文字。
        3. 分组 id 只能从上面的列表里选，不确定的选最接近的，绝不自创 id。
        4. 系统应用（如设置、相机、相册、电话、短信、文件管理、应用商店）按实际功能归入最接近的分组。
    """.trimIndent()

    /**
     * 全量智能分类。onProgress(doneApps, totalApps) 在每批完成后回调（IO 线程）。
     * 返回 Ok(packageName->groupId) 或 Err(可直接展示的错误文案)。
     */
    suspend fun classifyAll(
        context: Context,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ClassifyResult = withContext(Dispatchers.IO) {
        if (!LlmConfig.hasKey(context)) {
            return@withContext ClassifyResult.Err("请先在设置页配置大模型")
        }
        val baseUrl = LlmConfig.getBaseUrl(context)
        val apiKey = LlmConfig.getApiKey(context)
        val model = LlmConfig.getModel(context)

        val apps = try {
            listLaunchableApps(context)
        } catch (e: Exception) {
            return@withContext ClassifyResult.Err("读取应用列表失败：${e.message.orEmpty().ifBlank { "未知错误" }}")
        }
        if (apps.isEmpty()) return@withContext ClassifyResult.Err("没有找到可分类的应用")

        val merged = mutableMapOf<String, String>()
        val batches = apps.chunked(BATCH_SIZE)
        batches.forEachIndexed { idx, batch ->
            val pairs = batch.map { it.packageName to it.label.toString() }
            when (val r = classifyBatch(baseUrl, apiKey, model, pairs)) {
                is ClassifyResult.Ok -> {
                    merged.putAll(r.mapping)
                    onProgress(merged.size, apps.size)
                }
                is ClassifyResult.Err ->
                    return@withContext ClassifyResult.Err("第${idx + 1}/${batches.size}批失败：${r.message}")
            }
        }
        // 完整性校验：每个应用都必须有归属
        val missing = apps.count { it.packageName !in merged }
        if (missing > 0) {
            return@withContext ClassifyResult.Err("模型漏掉了 $missing 个应用，请重试")
        }
        ClassifyResult.Ok(merged, merged.size)
    }

    /**
     * 单批分类。走与意图框同一套已验证的底层请求链路（LlmRouter.postChatCompletions）：
     * 同一 URL 拼法、同一请求体风格、同一 OkHttpClient、同一 header。
     * 关键差异修复：不再发送 response_format=json_object（部分中转端点不支持，
     * 会返回 HTTP 200 + 空 body，导致"模型返回无法解析"）；JSON 解析靠
     * content → reasoning_content → 正则抽 JSON 三级兜底。
     */
    private suspend fun classifyBatch(
        baseUrl: String,
        apiKey: String,
        model: String,
        apps: List<Pair<String, String>>
    ): ClassifyResult {
        return try {
            val userContent = apps.joinToString("\n") { (pkg, label) -> "$label|$pkg" }
            val r = LlmRouter.postChatCompletions(
                baseUrl, apiKey, model,
                systemPrompt(), userContent,
                temperature = 0.2, maxTokens = 4000,
                tag = "AppClassifier"
            )
            if (r.networkError != null) {
                return ClassifyResult.Err("网络超时，请检查网络")
            }
            if (r.httpCode !in 200..299) {
                return ClassifyResult.Err(extractHttpError(r.httpCode, r.rawBody))
            }
            val rawBody = r.rawBody
            // 200 但包络里藏着 error（如被拦截/降级/余额不足）
            val envelopeErr = try {
                JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
            } catch (_: Exception) { "" }
            if (envelopeErr.isNotBlank()) {
                return ClassifyResult.Err("服务端错误：$envelopeErr".take(300))
            }
            val content = extractContent(rawBody)
                ?: return ClassifyResult.Err(
                    "模型返回无法解析" + rawBody.take(200).let {
                        if (it.isBlank()) "" else "，原始返回：$it"
                    }
                )
            val parsed = parseMapping(content)
                ?: return ClassifyResult.Err("模型返回的 JSON 无法解析")
            val validIds = GROUPS.map { it.first }.toSet()
            val filtered = parsed.filter { (pkg, gid) ->
                pkg.isNotBlank() && gid in validIds
            }
            if (filtered.isEmpty()) return ClassifyResult.Err("模型没有返回有效分类")
            ClassifyResult.Ok(filtered, filtered.size)
        } catch (e: Exception) {
            ClassifyResult.Err("请求失败：${e.message.orEmpty().ifBlank { "未知错误" }}")
        }
    }

    /**
     * 从原始响应里抠出模型正文，三级兜底：
     * 1) message.content（标准）；2) message.reasoning_content（推理模型）；
     * 3) 正则从全文抠第一个 JSON 对象。都拿不到返回 null。
     */
    private fun extractContent(rawBody: String): String? {
        try {
            val c = JSONObject(rawBody)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content")
            if (c.isNotBlank()) return c
        } catch (_: Exception) { }
        try {
            val c = JSONObject(rawBody)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("reasoning_content")
            if (c.isNotBlank()) return c
        } catch (_: Exception) { }
        return Regex("""\{[\s\S]*\}""").find(rawBody)?.value
    }

    private fun parseMapping(raw: String): Map<String, String>? {
        return tryParseMapping(raw)
            ?: extractJsonObject(raw)?.let { tryParseMapping(it) }
    }

    private fun tryParseMapping(json: String): Map<String, String>? {
        return try {
            val clean = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = JSONObject(clean)
            val out = mutableMapOf<String, String>()
            obj.keys().forEach { k -> out[k] = obj.optString(k, "") }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonObject(text: String): String? =
        Regex("""\{[\s\S]*\}""").find(text)?.value

    /** 把 HTTP 错误拼成 "状态码: 服务端 error.message" 的可读文案（body 已预读传入） */
    private fun extractHttpError(code: Int, rawBody: String): String {
        val serverMsg = try {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }.ifBlank { rawBody.take(200).ifBlank { "请求被拒绝" } }
        return "$code: $serverMsg".take(300)
    }
}
