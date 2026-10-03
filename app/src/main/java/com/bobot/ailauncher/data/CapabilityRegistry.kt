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
    val note: String? = null,
    // v0.17.0（PRD §六十一/§二十三）：风险分级与执行方式，JSON 可选字段，缺省安全值
    val riskLevel: RiskLevel = RiskLevel.LOW_RISK,
    val executionMethod: ExecutionMethod = ExecutionMethod.LAUNCH,
    val permission: String? = null
)

/** Action 风险分级（PRD §二十三）：A=读，B=导航，C=低风险，D=中风险，E=高风险（必须用户确认） */
enum class RiskLevel { READ, NAVIGATE, LOW_RISK, MEDIUM_RISK, HIGH_RISK }

/**
 * 执行方式优先级（PRD §二十一）：APP_FUNCTION > DEEP_LINK > SHORTCUT > INTENT > LAUNCH；
 * ACCESSIBILITY 永远只是兼容层，不做核心路径（PRD §六十评审结论 2）。
 */
enum class ExecutionMethod { LAUNCH, INTENT, DEEP_LINK, SHORTCUT, APP_FUNCTION, ACCESSIBILITY }

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

/** 内部可变分组（installedCapabilities 组装时用） */
private data class MutableResolvedGroup(
    val id: String,
    val label: String,
    val iconName: String,
    val apps: MutableList<ResolvedCapability>
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
                    note = c.optString("note").ifBlank { null },
                    // 可选字段：缺省 LOW_RISK / LAUNCH（老 JSON 不用改）
                    riskLevel = runCatching {
                        RiskLevel.valueOf(c.optString("risk", "LOW_RISK"))
                    }.getOrDefault(RiskLevel.LOW_RISK),
                    executionMethod = runCatching {
                        ExecutionMethod.valueOf(c.optString("execution", "LAUNCH"))
                    }.getOrDefault(ExecutionMethod.LAUNCH),
                    permission = c.optString("permission").ifBlank { null }
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
     * 用户自定义 mapping 会覆盖自动匹配：被指定的应用从自动分组移出，
     * 追加到目标分组；自定义分组排在内置分组之后。
     */
    fun installedCapabilities(context: Context): List<ResolvedGroup> {
        load(context)
        val auto = groups.mapNotNull { g ->
            val seen = mutableSetOf<String>()
            val apps = g.capabilities.flatMap { cap ->
                cap.packageNames.mapNotNull { pkg ->
                    if (!seen.add(pkg)) return@mapNotNull null
                    if (!isInstalled(context, pkg)) return@mapNotNull null
                    resolveOne(context, cap, pkg)
                }
            }
            if (apps.isEmpty()) null
            else MutableResolvedGroup(g.id, g.label, g.iconName, apps.toMutableList())
        }.toMutableList()

        // 自定义分组占位（按定义顺序排在内置分组之后）
        val customDefs = CustomCategories.getCustomGroups(context)
        customDefs.forEach { def ->
            if (auto.none { it.id == def.id }) {
                auto += MutableResolvedGroup(def.id, def.label, "", mutableListOf())
            }
        }

        val allGroupIds = auto.map { it.id }.toSet()
        CustomCategories.getMapping(context).forEach { (pkg, groupId) ->
            if (groupId !in allGroupIds) return@forEach
            if (!isInstalled(context, pkg)) return@forEach
            auto.forEach { it.apps.removeAll { a -> a.packageName == pkg } }
            val resolved = resolveCustom(context, pkg) ?: return@forEach
            auto.first { it.id == groupId }.apps += resolved
        }

        return auto.filter { it.apps.isNotEmpty() }
            .map { ResolvedGroup(it.id, it.label, it.iconName, it.apps) }
    }

    /** 用户手动归类的应用：用真实 label/icon 构造解析结果 */
    private fun resolveCustom(context: Context, packageName: String): ResolvedCapability? =
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(ai).toString()
            ResolvedCapability(
                capability = Capability(
                    id = "custom_$packageName",
                    label = label,
                    iconName = "",
                    packageNames = listOf(packageName),
                    deeplinkTemplate = null
                ),
                packageName = packageName,
                label = label,
                icon = pm.getApplicationIcon(ai)
            )
        } catch (_: Exception) {
            null
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
