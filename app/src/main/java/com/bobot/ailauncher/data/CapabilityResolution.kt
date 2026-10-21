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
    val deeplinkTemplate: String? = null
)

/** 能力的 geo 意图兜底方式（capabilities.json 的 "geoFallback" 字段） */
enum class GeoFallback {
    /** 不退 geo */
    NONE,

    /** 用目的地名称：geo:0,0?q=<名称>（daohang，参数 to） */
    NAME,

    /** 用经纬度：geo:<lat>,<lon>?q=<lat>,<lon>（ditie，参数 lat/lon） */
    COORD
}

/**
 * 一步可尝试的启动动作。执行器按 [CapabilityResolution.steps] 给出的顺序
 * 逐个尝试，第一个成功即停（「候选链」）；全部失败才算执行失败。
 */
sealed interface LaunchStep {
    /** 用深链直达提供方应用内页面（意图限定到该包） */
    data class Deeplink(val providerName: String, val packageName: String, val uri: String) : LaunchStep

    /** 通用 geo 意图（不限定包，系统里任一地图类应用都能接） */
    data class Geo(val uri: String) : LaunchStep

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
     * 1. 每个已装提供方（按声明优先序）一个深链步——模板缺参填不出时该提供方
     *    本轮不出深链步（绝不打开残缺链接，与二批防线同规矩）；
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
            val template = provider.deeplinkTemplate ?: continue
            val uri = DeeplinkTemplate.fill(template, params) ?: continue
            steps += LaunchStep.Deeplink(provider.name, pkg, uri)
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
