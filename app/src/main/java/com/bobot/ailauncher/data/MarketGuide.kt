package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * 应用市场指引（443，Bob 2026-11-07 下午原话：“如果是应用关键字没有命中，
 * 可以让它，可以让大模型识别，然后给它指引应用市场。这样用户可以自己去安装。”）
 *
 * 触发口径（反馈闭环 440–442 的第三支，拆自 onRetryClick 里原来“认不出”和
 * “认出但无已装应用”合在一起进选择器的那条路）：
 * 大模型重试认出了意图（capabilityId != "none"），但该能力在本机没有任何
 * 已装提供方应用 → 不进选择器（选择器只留给“大模型认不出意图”那一支），
 * 在应用中心空态出现一行“去应用市场搜「词」”，点一下打开应用市场搜到
 * 相关应用，用户自己安装。
 *
 * 搜索词取法（LlmRouter.route() 的返回里没有应用名字段——核过：系统提示词
 * 只让模型在 params 里提 from/to，parseResult 原样抄进 params；
 * from/to 都是地点/行程信息，搜市场没有意义，所以不用）：
 * ① 内容型能力（声明了 query 参数：音乐/视频）先用内容名——
 *    CapabilityResolution.parseContent 从原句抠（“想听周杰伦的歌”→“周杰伦”、
 *    “想看《狂飙》”→“狂飙”）；抠不出或抠出的是 junk（“想听歌”→“歌”）
 *    时落到下一档；
 * ② 能力关键词——能力的 label（“音乐”/“视频”/“打车”/“导航”/“微信”…），
 *    即 capabilities.json 里该能力的中文名；
 * ③ 兜底：原句本身（trim 后）。
 *
 * 打开应用市场的回退链（Bob 的手机是 nubia，不一定有 Google Play，
 * 不只写死 Play 一条路）：
 * ① market://search?q=…（由系统解析到本机已装的应用商店——华为/小米/
 *    nubia 等各家商店都接这个 scheme）；
 * ② 解析不到时回退 Play 商店网页
 *    https://play.google.com/store/search?q=…&c=apps（浏览器打开）；
 * ③ 两条都解析不到 → 返回 false，调用方 toast“打不开应用市场”。
 * 链接拼装与回退选择都是纯函数（[marketSearchUri]、[webSearchUri]、
 * [pickSearchUri]），可 JVM 单测；真机商店行为 CI 验不了，以 Bob 真机为准。
 */
object MarketGuide {

    /** LLM 重试的三支去向（onRetryClick 按此分流）。 */
    sealed interface LlmRetryOutcome {
        /** 大模型认出意图且有已装提供方：列出这些应用（不自动打开）。 */
        data class InstalledApps(val packageNames: List<String>) : LlmRetryOutcome

        /** 大模型认出意图但无已装提供方：指引去应用市场，带搜索词。 */
        data class MarketGuide(val searchTerm: String) : LlmRetryOutcome

        /** 大模型认不出（capabilityId 为 "none" / 能力未知）：进选择器。 */
        data object ChooseApp : LlmRetryOutcome
    }

    /**
     * 三支分流（纯函数，可单测）：能力为空 → 选应用；有已装提供方 →
     * 列已装应用（声明序、去重）；认出意图但一个都没装 → 市场指引。
     * @param installedPackages 本机已装包名集合（调用方取自搜索索引里的包名）。
     */
    fun decideLlmRetry(
        capability: Capability?,
        installedPackages: Set<String>,
        query: String
    ): LlmRetryOutcome {
        if (capability == null) return LlmRetryOutcome.ChooseApp
        val installed = CapabilityResolution.effectiveProviders(capability)
            .flatMap { it.packageNames }
            .distinct()
            .filter { it in installedPackages }
        if (installed.isNotEmpty()) return LlmRetryOutcome.InstalledApps(installed)
        return LlmRetryOutcome.MarketGuide(searchTerm(capability, query))
    }

    /**
     * 市场搜索词（纯函数，可单测）：内容名 > 能力关键词（label）> 原句。
     * LLM params 里的 from/to 是地点/行程，不用作搜索词（见文件头注释）。
     */
    fun searchTerm(capability: Capability, query: String): String {
        val q = query.trim()
        // 内容型能力（音乐/视频）先用内容名；parseContent 抠不出或
        // 抠出 junk 时返回 null，落到能力关键词。
        if (capability.params.any { it.name == "query" }) {
            CapabilityResolution.parseContent(q)?.let { return it }
        }
        val label = capability.label.trim()
        if (label.isNotEmpty()) return label
        return q
    }

    /** 通用应用商店搜索链接（系统按本机已装商店解析）。 */
    fun marketSearchUri(term: String): String =
        "market://search?q=" + URLEncoder.encode(term, "UTF-8")

    /** Play 商店网页搜索链接（market:// 无人接时的回退）。 */
    fun webSearchUri(term: String): String =
        "https://play.google.com/store/search?q=" +
            URLEncoder.encode(term, "UTF-8") + "&c=apps"

    /**
     * 回退选择（纯函数，可单测）：有商店接 market:// 就走商店；
     * 否则有浏览器接网页就走网页；两条都没人接返回 null。
     */
    fun pickSearchUri(term: String, marketAvailable: Boolean, webAvailable: Boolean): String? =
        when {
            marketAvailable -> marketSearchUri(term)
            webAvailable -> webSearchUri(term)
            else -> null
        }

    /**
     * 打开应用市场搜相关应用（回退链见文件头）：先试 market://，解析不到
     * 再试 Play 网页；都打不开返回 false（调用方 toast 提示）。
     */
    fun openMarketSearch(context: Context, term: String): Boolean {
        val pm = context.packageManager
        val marketAvailable = Intent(Intent.ACTION_VIEW, Uri.parse(marketSearchUri(term)))
            .resolveActivity(pm) != null
        val webAvailable = Intent(Intent.ACTION_VIEW, Uri.parse(webSearchUri(term)))
            .resolveActivity(pm) != null
        val uri = pickSearchUri(term, marketAvailable, webAvailable) ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            true
        } catch (_: Exception) {
            false
        }
    }
}
