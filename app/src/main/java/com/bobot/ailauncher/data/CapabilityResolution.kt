package com.bobot.ailauncher.data

import java.net.URLEncoder

/**
 * 能力候选提供方（v0.68.88·能力按已装应用动态绑定）。
 *
 * 一个能力可以有多家提供方（如「导航」= 高德 / 百度 / 腾讯地图），按声明顺序
 * 优先。运行时按**本机实际已装**的包解析：装了哪家就绑哪家；都没装时深链类
 * 能力再退通用 geo 意图（[Capability.geoFallback]）；最后才允许诚实报错。
 * 本文件全部是纯逻辑（输入 = 能力定义 + 已装包名集合 + 参数），可 JVM 单测。
 */
data class CapabilityProvider(
    /** 提供方显示名（如「腾讯地图」），仅用于日志/调试 */
    val name: String,
    /** 该提供方的包名（同一提供方多包时按序取第一个已装的，如滴滴双包） */
    val packageNames: List<String>,
    /**
     * 该提供方的深链模板（{param} 占位，与 [DeeplinkTemplate] 同规则）；
     * null = 这家没有可用的深链（或只有需要坐标的深链而本能力只有名称参数），
     * 它的执行步骤直接是「打开应用本体」。
     */
    val deeplinkTemplate: String? = null,
    /**
     * 该提供方的「名称目的地」深链模板（ditie：只有名字、没有坐标时用；
     * 与 [deeplinkTemplate] 互为补充，同一家先试坐标模板、再试名称模板）。
     * null = 这家没有按名字直达的深链（腾讯 routeplan 官方要求 tocoord 坐标，
     * 名称只能走 geo 兜底），名称路径下它不出深链步。
     */
    val deeplinkNameTemplate: String? = null
)

/** 能力的 geo 意图兜底方式（capabilities.json 的 "geoFallback" 字段） */
enum class GeoFallback {
    /** 不退 geo */
    NONE,

    /** 用目的地名称：geo:0,0?q=<名称>（daohang，参数 to） */
    NAME,

    /** 用经纬度：geo:<lat>,<lon>?q=<lat>,<lon>（ditie，参数 lat/lon） */
    COORD,

    /**
     * 先用经纬度、没有再用名称（ditie：有坐标走坐标点，没坐标有名字时
     * 走名称查询；两条都不齐备返回 null）。
     */
    COORD_OR_NAME
}

/**
 * 一步可尝试的启动动作。执行器按 [CapabilityResolution.steps] 给出的顺序
 * 逐个尝试，第一个成功即停（「候选链」）；全部失败才算执行失败。
 */
sealed interface LaunchStep {
    /** 用深链直达提供方应用内页面（意图限定到该包） */
    data class Deeplink(val providerName: String, val packageName: String, val uri: String) : LaunchStep

    /**
     * 通用 geo 意图；packageName 为 null 时不限定包（问助手老口径，系统里
     * 任一地图类应用都能接），非 null 时限定到该包（432 点按路径，不跨应用跳）。
     */
    data class Geo(val uri: String, val packageName: String? = null) : LaunchStep

    /** 打开提供方应用本体（主页） */
    data class OpenApp(val providerName: String, val packageName: String) : LaunchStep
}

object CapabilityResolution {

    /**
     * 能力的有效提供方：显式声明了 providers 就用声明的；否则把老的
     * (packageNames + deeplinkTemplate) 合成一个提供方（向后兼容老 JSON）。
     */
    fun effectiveProviders(cap: Capability): List<CapabilityProvider> =
        if (cap.providers.isNotEmpty()) cap.providers
        else listOf(CapabilityProvider(cap.label, cap.packageNames, cap.deeplinkTemplate))

    /** 第一个「本机装了」的提供方及其命中的包名；都没有返回 null */
    fun firstInstalledProvider(
        cap: Capability,
        installed: Set<String>
    ): Pair<CapabilityProvider, String>? {
        for (provider in effectiveProviders(cap)) {
            val pkg = provider.packageNames.firstOrNull { it in installed }
            if (pkg != null) return provider to pkg
        }
        return null
    }

    /**
     * 本机可解析（有提供方装了）——hints 过滤的纯函数部分。
     * geo 处理者是否存在是平台事实，由注册表在此基础上再放宽（见
     * CapabilityRegistry.executableCapabilities）。
     */
    fun isResolvable(cap: Capability, installed: Set<String>): Boolean =
        firstInstalledProvider(cap, installed) != null

    /**
     * 构建有序候选链：
     * 1. 每个已装提供方（按声明优先序）的深链步——坐标模板先试、名称模板
     *    再试（ditie），模板缺参填不出时该提供方本轮不出对应深链步
     *    （绝不打开残缺链接，与二批防线同规矩）；
     * 2. geo 兜底步（能力声明了 geoFallback 且参数齐备时）；
     * 3. 第一个已装提供方的「打开应用本体」步（最终兜底）。
     * 没有任何提供方装了时：只剩 geo 步（若可构造），否则空链。
     */
    fun steps(
        cap: Capability,
        installed: Set<String>,
        params: Map<String, String>
    ): List<LaunchStep> {
        val steps = mutableListOf<LaunchStep>()
        val installedProviders = effectiveProviders(cap).mapNotNull { provider ->
            val pkg = provider.packageNames.firstOrNull { it in installed }
            if (pkg == null) null else provider to pkg
        }
        for ((provider, pkg) in installedProviders) {
            provider.deeplinkTemplate?.let { template ->
                DeeplinkTemplate.fill(template, params)?.let { uri ->
                    steps += LaunchStep.Deeplink(provider.name, pkg, uri)
                }
            }
            // 名称深链（ditie：只有名字时用地铁/公交方式直达；坐标模板
            // 填不出时才到这里，同一家坐标步在前、名称步在后）。
            provider.deeplinkNameTemplate?.let { template ->
                DeeplinkTemplate.fill(template, params)?.let { uri ->
                    steps += LaunchStep.Deeplink(provider.name, pkg, uri)
                }
            }
        }
        geoUri(cap, params)?.let { steps += LaunchStep.Geo(it) }
        // 打开应用本体是最终兜底；能力关掉 fallbackToLauncher 时不出这一步（老语义）
        if (cap.fallbackToLauncher) {
            installedProviders.firstOrNull()?.let { (provider, pkg) ->
                steps += LaunchStep.OpenApp(provider.name, pkg)
            }
        }
        return steps
    }

    /** 432：从整句抠目的地——第一个“去/到”后面、到分隔符为止的一段。 */
    private val DESTINATION_AFTER_MARKER = Regex("[去到]([^去到，,、；:！？。\\s的]+)")
    /** 问句里的空目的地（“导航去哪儿”没有真目的地，不许拼进深链） */
    private val JUNK_DESTINATIONS = setOf("哪儿", "哪里", "哪", "那里", "这儿", "这里", "那儿")

    fun parseDestination(query: String): String? {
        val match = DESTINATION_AFTER_MARKER.find(query) ?: return null
        val dest = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (dest.isEmpty() || dest in JUNK_DESTINATIONS) return null
        return dest
    }

    /**
     * 只给一家提供方的候选链（432 点按路径）：深链（坐标模板先试、名称模板
     * 再试，模板填得出时）→ 定向 geo（能力声明了 geoFallback 且参数齐备时，
     * 限定到这家包，不跨应用跳）→ 打开这家应用本体。模板缺参填不出时该家
     * 不出深链步，与 [steps] 同规矩，绝不打开残缺链接。
     */
    fun stepsForProvider(
        cap: Capability,
        provider: CapabilityProvider,
        packageName: String,
        params: Map<String, String>
    ): List<LaunchStep> {
        val steps = mutableListOf<LaunchStep>()
        provider.deeplinkTemplate?.let { template ->
            DeeplinkTemplate.fill(template, params)?.let { uri ->
                steps += LaunchStep.Deeplink(provider.name, packageName, uri)
            }
        }
        provider.deeplinkNameTemplate?.let { template ->
            DeeplinkTemplate.fill(template, params)?.let { uri ->
                steps += LaunchStep.Deeplink(provider.name, packageName, uri)
            }
        }
        geoUri(cap, params)?.let { steps += LaunchStep.Geo(it, packageName) }
        if (cap.fallbackToLauncher) {
            steps += LaunchStep.OpenApp(provider.name, packageName)
        }
        return steps
    }

    /**
     * 点按动作解析（纯函数，可单测）：整句含带参能力的 label（如“导航”）且
     * 能抠出目的地、目的地不是能力名本身、且被点应用是该能力的提供方时，
     * 返回（能力，目的地）；否则 null。供 [tapSteps] 与动作记录（智能滚动
     * 关键字：只记真实能力动作）共用同一匹配口径，不各写一套。
     */
    fun tapAction(
        capabilities: List<Capability>,
        query: String,
        packageName: String
    ): Pair<Capability, String>? {
        // 只考虑带参能力（能带目的地的才值得解析；无参能力点按永远直接打开）
        val cap = capabilities
            .filter { it.params.isNotEmpty() && query.contains(it.label) }
            .maxByOrNull { it.label.length } ?: return null
        val dest = parseDestination(query) ?: return null
        if (dest == cap.label) return null
        if (effectiveProviders(cap).none { packageName in it.packageNames }) return null
        return cap to dest
    }

    /**
     * 432：应用中心点搜索结果的“带目的地”解析（纯函数，可单测）。
     * 整句含带参能力的 label（如“导航”）且能抠出目的地、且被点应用是该
     * 能力的提供方时，返回只给这家用的候选链（深链→定向 geo→打开应用）；
     * 任一条件不满足（没关键字、没目的地、被点应用不是该能力提供方、
     * 深链与 geo 都建不出）返回 null，调用方退回直接打开。
     * 名字型目的地只填声明了 "to" 的参数（ditie 也声明了 "to"：有名字时
     * 走名称深链/名称 geo，没名字有坐标时走坐标深链/坐标 geo）。
     */
    fun tapSteps(
        capabilities: List<Capability>,
        query: String,
        packageName: String
    ): List<LaunchStep>? {
        val (cap, dest) = tapAction(capabilities, query, packageName) ?: return null
        val provider = effectiveProviders(cap)
            .firstOrNull { packageName in it.packageNames } ?: return null
        val params =
            if (cap.params.any { it.name == "to" }) mapOf("to" to dest) else emptyMap()
        val steps = stepsForProvider(cap, provider, packageName, params)
        // 深链与 geo 都建不出 = 没东西可带（地铁只有名无坐标、打车没深链没
        // geo），退回直接打开；只剩 OpenApp 的链与直接打开等价，不必绕注册表。
        if (steps.none { it is LaunchStep.Deeplink || it is LaunchStep.Geo }) return null
        return steps
    }

    /** 按能力声明的 geoFallback 构造 geo URI；参数缺失/非法返回 null */
    fun geoUri(cap: Capability, params: Map<String, String>): String? =
        when (cap.geoFallback) {
            GeoFallback.NONE -> null
            GeoFallback.NAME -> {
                val to = params["to"]?.trim().orEmpty()
                if (to.isEmpty()) null else geoUriForQuery(to)
            }
            GeoFallback.COORD -> {
                val lat = params["lat"]?.trim().orEmpty()
                val lon = params["lon"]?.trim().orEmpty()
                geoUriForCoord(lat, lon)
            }
            GeoFallback.COORD_OR_NAME -> {
                val lat = params["lat"]?.trim().orEmpty()
                val lon = params["lon"]?.trim().orEmpty()
                geoUriForCoord(lat, lon) ?: run {
                    val to = params["to"]?.trim().orEmpty()
                    if (to.isEmpty()) null else geoUriForQuery(to)
                }
            }
        }

    /** geo:0,0?q=<名称>（RFC 3986 编码：空格 %20，不用表单式 +） */
    fun geoUriForQuery(query: String): String =
        "geo:0,0?q=" + rfc3986Encode(query)

    /**
     * geo:<lat>,<lon>?q=<lat>,<lon>；经纬度必须是合法数字且在范围内，
     * 否则返回 null（模型给的脏坐标不许拼进链接）。
     */
    fun geoUriForCoord(latRaw: String, lonRaw: String): String? {
        val lat = latRaw.toDoubleOrNull() ?: return null
        val lon = lonRaw.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return "geo:$latRaw,$lonRaw?q=$latRaw,$lonRaw"
    }

    /** URLEncoder 是表单编码（空格→+）；geo 查询串按 RFC 3986 改 %20，其余转义一致可沿用 */
    private fun rfc3986Encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
