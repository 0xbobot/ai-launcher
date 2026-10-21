package com.bobot.ailauncher.data

/**
 * 设置页「测试连接」的 data 层入口（批B ②）。
 *
 * 原 SettingsScreen 直接调 LlmRouter.testConnection，违反「UI 不直接调 LLM」铁律；
 * UI 改经此封装，签名与返回语义与 [LlmRouter.testConnection] 完全一致
 * （null = 连接成功，否则为可直接展示的错误文案）。
 */
object LlmConnectionTester {
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): String? =
        LlmRouter.testConnection(baseUrl, apiKey, model)
}
