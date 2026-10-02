package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.net.URLEncoder
import org.json.JSONObject

/**
 * 能力注册表：从 assets/capabilities.json 加载。
 *
 * 每个能力 = { id, label, iconName, packageNames[], deeplinkTemplate, fallbackToLauncher, note }
 * packageNames 是该意图对应的已知 App 包名列表（按优先级排序）。
 * deeplinkTemplate 支持 {param} 占位符，调用时用 params 替换。
 *
 * 两条使用路径：
 * 1. 能力页展示：installedCapabilities() —— 按包名匹配本机已安装应用，
 *    只返回"装了的"真实应用（真实图标/名称），空分组直接丢弃。
 * 2. 意图路由（关键词/LLM）：resolveAndLaunch(capabilityId) —— 取该能力
 *    第一个已安装的包名，优先走 deep link，失败降级到 App 主页。
 */
data class Capability(
    val id: String,
    val label: String,
    val iconName: String,
    val packageNames: List<String>,
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

/** 本机已安装并解析出的真实应用 */
data class ResolvedCapability(
    val capability: Capability,
    val packageName: String,
    val label: CharSequence,
    val icon: Drawable
)

data class ResolvedGroup(
    val id: String,
    val label: String,
    val iconName: String,
    val apps: List<ResolvedCapability>
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
                val pkgArr = c.getJSONArray("packageNames")
                val pkgs = (0 until pkgArr.length()).map { pkgArr.getString(it) }
                caps += Capability(
                    id = c.getString("id"),
                    label = c.getString("label"),
                    iconName = c.optString("icon", ""),
                    packageNames = pkgs,
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

    /** 全部 capability id，供 LLM 路由做白名单校验 */
    fun validIds(): Set<String> =
        groups.flatMap { it.capabilities }.map { it.id }.toSet()

    private fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }

    private fun resolveOne(
        context: Context,
        cap: Capability,
        packageName: String
    ): ResolvedCapability? =
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            ResolvedCapability(
                capability = cap,
                packageName = packageName,
                label = pm.getApplicationLabel(ai),
                icon = pm.getApplicationIcon(ai)
            )
        } catch (_: Exception) {
            null
        }

    /**
     * 能力页用：返回每个分组下"本机已安装"的真实应用。
     * 同一分组内同一包名只出现一次（取排在前面的 capability）；空分组丢弃。
     */
    fun installedCapabilities(context: Context): List<ResolvedGroup> {
        load(context)
        return groups.mapNotNull { g ->
            val seen = mutableSetOf<String>()
            val apps = g.capabilities.flatMap { cap ->
                cap.packageNames.mapNotNull { pkg ->
                    if (!seen.add(pkg)) return@mapNotNull null
                    if (!isInstalled(context, pkg)) return@mapNotNull null
                    resolveOne(context, cap, pkg)
                }
            }
            if (apps.isEmpty()) null else ResolvedGroup(g.id, g.label, g.iconName, apps)
        }
    }

    /** 意图路由用：取该能力第一个已安装的包 */
    fun resolveFirstInstalled(context: Context, capabilityId: String): ResolvedCapability? {
        load(context)
        val cap = find(capabilityId) ?: return null
        val pkg = cap.packageNames.firstOrNull { isInstalled(context, it) } ?: return null
        return resolveOne(context, cap, pkg)
    }

    fun resolveAndLaunch(
        context: Context,
        capabilityId: String,
        params: Map<String, String> = emptyMap()
    ): Boolean {
        val resolved = resolveFirstInstalled(context, capabilityId) ?: return false
        return launchResolved(context, resolved, params)
    }

    /** 启动一个已解析的真实应用：有 deep link 模板先走模板，无则/失败则打开 App 主页 */
    fun launchResolved(
        context: Context,
        resolved: ResolvedCapability,
        params: Map<String, String> = emptyMap()
    ): Boolean {
        val cap = resolved.capability

        // 1) Deep Link 直达 App 内页面（限定到解析出的包，避免跳错 App）
        cap.deeplinkTemplate?.let { template ->
            try {
                var uri = template
                params.forEach { (k, v) ->
                    uri = uri.replace("{$k}", URLEncoder.encode(v, "UTF-8"))
                }
                uri = uri.replace(Regex("\\{[^}]*\\}"), "") // 清掉未传入的参数占位符
                val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                intent.setPackage(resolved.packageName)
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
                val launch = context.packageManager.getLaunchIntentForPackage(resolved.packageName)
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
