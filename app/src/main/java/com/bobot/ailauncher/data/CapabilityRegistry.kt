package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * 能力注册表：从 assets/capabilities.json 加载。
 *
 * 每个能力 = { id, label, iconName, packageName, deeplinkTemplate, fallbackToLauncher, note }
 * deeplinkTemplate 支持 {param} 占位符，调用时用 params 替换。
 *
 * 跳转策略（resolveAndLaunch）：
 * 1. 有 deep link 模板 → 构造 Intent 跳转到 App 内具体页面
 * 2. 失败或无模板 → 用 PackageManager 打开 App 主页（launcher intent）
 * 3. 都失败 → 返回 false，由调用方 toast/降级
 */
data class Capability(
    val id: String,
    val label: String,
    val iconName: String,
    val packageName: String,
    val deeplinkTemplate: String?,
    val fallbackToLauncher: Boolean = true,
    val note: String? = null
)

data class CapabilityGroup(
    val id: String,
    val label: String,
    val iconName: String,
    val capabilities: List<Capability>
)

object CapabilityRegistry {
    private var groups: List<CapabilityGroup> = emptyList()

    fun load(context: Context) {
        if (groups.isNotEmpty()) return
        val json = context.assets.open("capabilities.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val result = mutableListOf<CapabilityGroup>()
        val gArr = root.getJSONArray("groups")
        for (i in 0 until gArr.length()) {
            val g = gArr.getJSONObject(i)
            val caps = mutableListOf<Capability>()
            val cArr = g.getJSONArray("capabilities")
            for (j in 0 until cArr.length()) {
                val c = cArr.getJSONObject(j)
                caps += Capability(
                    id = c.getString("id"),
                    label = c.getString("label"),
                    iconName = c.optString("icon", ""),
                    packageName = c.getString("packageName"),
                    deeplinkTemplate = c.optString("deeplink").ifBlank { null },
                    fallbackToLauncher = c.optBoolean("fallbackToLauncher", true),
                    note = c.optString("note").ifBlank { null }
                )
            }
            result += CapabilityGroup(
                id = g.getString("id"),
                label = g.getString("label"),
                iconName = g.optString("icon", ""),
                capabilities = caps
            )
        }
        groups = result
    }

    fun groups(): List<CapabilityGroup> = groups

    fun find(id: String): Capability? =
        groups.flatMap { it.capabilities }.find { it.id == id }

    fun resolveAndLaunch(
        context: Context,
        capabilityId: String,
        params: Map<String, String> = emptyMap()
    ): Boolean {
        val cap = find(capabilityId) ?: return false

        // 1) Deep Link 直达 App 内页面
        cap.deeplinkTemplate?.let { template ->
            try {
                var uri = template
                params.forEach { (k, v) -> uri = uri.replace("{$k}", v) }
                uri = uri.replace(Regex("\\{[^}]*\\}"), "") // 清掉未传入的参数占位符
                val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // 降级到 launcher intent
            }
        }

        // 2) 打开 App 主页兜底
        if (cap.fallbackToLauncher) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(cap.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return true
                }
            } catch (_: Exception) {
                // fallthrough
            }
        }
        return false
    }
}
