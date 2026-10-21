package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
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
/**
 * 能力参数声明（意图改进二批）：与 deeplinkTemplate 里的 {占位符} 一一对应，
 * 是「模型该提取什么 / 执行前必须有什么」的唯一事实源。
 * - [required] 为 true 且执行时缺失/空白 → 不执行，由七仔按 [ask] 追问补参；
 * - [ask] 是缺参时七仔的问句，缺省由协调器按「label 需要 name」拼兜底问句。
 */
data class CapabilityParam(
    val name: String,
    val label: String,
    val required: Boolean = false,
    val ask: String? = null
)

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
    val permission: String? = null,
    // 意图改进二批：参数 schema（JSON 可选字段 "params"，缺省无参）
    val params: List<CapabilityParam> = emptyList(),
    // v0.68.88：候选提供方（JSON 可选字段 "providers"）；空 = 用
    // (packageNames + deeplinkTemplate) 合成单个提供方，见 CapabilityResolution
    val providers: List<CapabilityProvider> = emptyList(),
    // v0.68.88：深链全灭时的 geo 意图兜底方式（JSON 可选字段 "geoFallback"）
    val geoFallback: GeoFallback = GeoFallback.NONE
) {
    /** 执行前校验：返回缺失（未传或空白）的必填参数，按声明顺序 */
    fun missingRequiredParams(filled: Map<String, String>): List<CapabilityParam> =
        params.filter { it.required && filled[it.name].isNullOrBlank() }
}

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

    /** 解析能力 JSON 的可选 "params" 数组；字段缺失/格式不对一律按无参处理（不让坏数据拖垮整表加载） */
    private fun parseParams(c: JSONObject): List<CapabilityParam> {
        val arr = c.optJSONArray("params") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = p.optString("name", "").trim()
            if (name.isEmpty()) return@mapNotNull null
            CapabilityParam(
                name = name,
                label = p.optString("label", name).ifBlank { name },
                required = p.optBoolean("required", false),
                ask = p.optString("ask").ifBlank { null }
            )
        }
    }

    /** 解析能力 JSON 的可选 "providers" 数组；坏数据按未声明处理（合成提供方兜底） */
    private fun parseProviders(c: JSONObject, label: String): List<CapabilityProvider> {
        val arr = c.optJSONArray("providers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val pkgArr = p.optJSONArray("packageNames") ?: return@mapNotNull null
            val pkgs = (0 until pkgArr.length()).map { pkgArr.getString(it) }
            if (pkgs.isEmpty()) return@mapNotNull null
            CapabilityProvider(
                name = p.optString("name", label).ifBlank { label },
                packageNames = pkgs,
                deeplinkTemplate = p.optString("deeplink").ifBlank { null }
            )
        }
    }

    /** 解析能力 JSON 的可选 "geoFallback" 字段（"name"/"coord"，其余一律 NONE） */
    private fun parseGeoFallback(c: JSONObject): GeoFallback =
        when (c.optString("geoFallback", "").trim().lowercase()) {
            "name" -> GeoFallback.NAME
            "coord" -> GeoFallback.COORD
            else -> GeoFallback.NONE
        }

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
                    permission = c.optString("permission").ifBlank { null },
                    // 意图改进二批：参数 schema（可选字段，老 JSON 不写即无参）
                    params = parseParams(c),
                    // v0.68.88：候选提供方 + geo 兜底（可选字段，老 JSON 不写即合成/NONE）
                    providers = parseProviders(c, c.getString("label")),
                    geoFallback = parseGeoFallback(c)
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
                effectivePackageNames(cap).mapNotNull { pkg ->
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

    /** 能力的全部候选包名（显式 providers 的并集；未声明时即老 packageNames），保持声明顺序 */
    private fun effectivePackageNames(cap: Capability): List<String> =
        CapabilityResolution.effectiveProviders(cap).flatMap { it.packageNames }.distinct()

    /** 意图路由用：取该能力第一个已安装的包 */
    fun resolveFirstInstalled(context: Context, capabilityId: String): ResolvedCapability? {
        load(context)
        val cap = find(capabilityId) ?: return null
        val pkg = effectivePackageNames(cap).firstOrNull { isInstalled(context, it) } ?: return null
        return resolveOne(context, cap, pkg)
    }

    fun resolveAndLaunch(
        context: Context,
        capabilityId: String,
        params: Map<String, String> = emptyMap()
    ): Boolean {
        val cap = find(capabilityId) ?: return false
        return launchCapability(context, cap, params) is LaunchOutcome.Opened
    }

    // ---------- v0.68.88：按已装应用动态绑定 ----------

    /** 能力执行结果三态，供 [com.bobot.ailauncher.core.action.ActionEngine] 选诚实文案 */
    sealed interface LaunchOutcome {
        /** 成功打开了某个真实应用（label = 该应用的真实名称） */
        data class Opened(val label: String) : LaunchOutcome

        /** 一步都没得试：没有提供方装了、本机也没有 geo 处理者 */
        object NoProvider : LaunchOutcome

        /** 有步可试但全部失败（意图无处理者/分发异常） */
        object Failed : LaunchOutcome
    }

    @Volatile
    private var executableCache: Pair<Int, List<Capability>>? = null

    /**
     * 本机当前真正能执行的能力（planner hints 的过滤源）：任一提供方已装，
     * 或声明了 geo 兜底且本机有 geo 意图处理者。按 [InstalledApps.epoch]
     * 缓存——装/卸应用后 epoch 变化，下一次调用自动重算（不轮询）。
     */
    fun executableCapabilities(context: Context): List<Capability> {
        load(context)
        InstalledApps.ensureRegistered(context)
        val epoch = InstalledApps.epoch.value
        executableCache?.let { if (it.first == epoch) return it.second }
        val all = groups.flatMap { it.capabilities }
        val installed = InstalledApps.installedAmong(
            context,
            all.flatMap { cap -> effectivePackageNames(cap) }
        )
        val anyGeoHandler = hasGeoHandler(context)
        val result = all.filter { cap ->
            CapabilityResolution.isResolvable(cap, installed) ||
                (cap.geoFallback != GeoFallback.NONE && anyGeoHandler)
        }
        executableCache = epoch to result
        return result
    }

    private fun hasGeoHandler(context: Context): Boolean =
        try {
            val probe = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=x"))
            context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private fun hasGeoHandler(context: Context, uri: String): Boolean =
        try {
            val probe = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
            context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    /**
     * 按候选链执行一个能力：已装提供方深链（优先序）→ geo 兜底 → 打开绑定的
     * 应用本体，第一个成功即停。链由 [CapabilityResolution.steps] 纯函数构建，
     * 这里只做平台分发。不再「一站死胡同」：单家失败会继续试下一家。
     */
    fun launchCapability(
        context: Context,
        cap: Capability,
        params: Map<String, String> = emptyMap()
    ): LaunchOutcome {
        load(context)
        InstalledApps.ensureRegistered(context)
        val installed = InstalledApps.installedAmong(context, effectivePackageNames(cap))
        val steps = CapabilityResolution.steps(cap, installed, params)
        if (steps.isEmpty()) return LaunchOutcome.NoProvider
        // 只剩 geo 步且本机没人能接 geo 时，等于一步都没得试——按 NoProvider 报
        // 「没有安装支持的应用」，不能混成「试了但没打开」
        val geoStep = steps.filterIsInstance<LaunchStep.Geo>().firstOrNull()
        if (geoStep != null && steps.none { it !is LaunchStep.Geo } && !hasGeoHandler(context, geoStep.uri)) {
            return LaunchOutcome.NoProvider
        }
        for (step in steps) {
            val label = tryStep(context, step)
            if (label != null) return LaunchOutcome.Opened(label)
        }
        return LaunchOutcome.Failed
    }

    /** 尝试一步启动；成功返回被打开应用的名称，失败返回 null（继续下一家） */
    private fun tryStep(context: Context, step: LaunchStep): String? = when (step) {
        is LaunchStep.Deeplink -> try {
            val intent = Intent.parseUri(step.uri, Intent.URI_INTENT_SCHEME)
            intent.setPackage(step.packageName)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            appLabel(context, step.packageName)
        } catch (_: Exception) {
            null
        }

        is LaunchStep.Geo -> try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(step.uri))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val handler = context.packageManager.queryIntentActivities(intent, 0).firstOrNull()
            if (handler == null) {
                null
            } else {
                context.startActivity(intent)
                context.packageManager
                    .getApplicationLabel(handler.activityInfo.applicationInfo).toString()
            }
        } catch (_: Exception) {
            null
        }

        is LaunchStep.OpenApp -> try {
            val launch = context.packageManager.getLaunchIntentForPackage(step.packageName)
            if (launch == null) {
                null
            } else {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                appLabel(context, step.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun appLabel(context: Context, packageName: String): String =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

    /**
     * 旧签名保留（能力页等调用方）：内部已换成候选链执行——解析出的应用是
     * 优先序第一家，深链失败会顺链降级（geo / 其他提供方 / 应用本体）。
     */
    fun launchResolved(
        context: Context,
        resolved: ResolvedCapability,
        params: Map<String, String> = emptyMap()
    ): Boolean =
        launchCapability(context, resolved.capability, params) is LaunchOutcome.Opened
}
