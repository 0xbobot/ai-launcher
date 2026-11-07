package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 443·应用市场指引纯逻辑单测。
 * 覆盖三支分流（认出+有已装→列应用 / 认出+无已装→市场指引 /
 * 认不出→进选择器）、搜索词取法（内容名 > 能力关键词 > 原句）、
 * 市场链接拼装（market:// + Play 网页回退）与回退选择。
 * openMarketSearch 本身要 Context/PackageManager（resolveActivity +
 * startActivity），纯 JVM 跑不了——回退选择由 pickSearchUri 覆盖，
 * 真机商店行为以 Bob 真机为准。
 */
class MarketGuideTest {

    private fun cap(
        id: String,
        label: String,
        packageNames: List<String> = emptyList(),
        providers: List<CapabilityProvider> = emptyList(),
        params: List<CapabilityParam> = emptyList()
    ) = Capability(
        id = id,
        label = label,
        iconName = "icon",
        packageNames = packageNames,
        deeplinkTemplate = null,
        providers = providers,
        params = params
    )

    private val queryParam = listOf(CapabilityParam(name = "query", label = "内容"))

    // ---------- 搜索词取法 ----------

    @Test
    fun `searchTerm - content capability uses parsed content name`() {
        val music = cap("yinyue", "音乐", params = queryParam)
        assertEquals("周杰伦", MarketGuide.searchTerm(music, "想听周杰伦的歌"))
        val video = cap("shipin", "视频", params = queryParam)
        assertEquals("狂飙", MarketGuide.searchTerm(video, "想看《狂飙》"))
    }

    @Test
    fun `searchTerm - junk content falls back to capability label`() {
        val music = cap("yinyue", "音乐", params = queryParam)
        // parseContent("想听歌") 抠出的是 junk“歌”，不用，落到 label。
        assertEquals("音乐", MarketGuide.searchTerm(music, "想听歌"))
    }

    @Test
    fun `searchTerm - non-content capability uses label`() {
        val dache = cap("dache", "打车")
        assertEquals("打车", MarketGuide.searchTerm(dache, "打车去机场"))
        val daohang = cap("daohang", "导航")
        assertEquals("导航", MarketGuide.searchTerm(daohang, "导航去机场"))
    }

    @Test
    fun `searchTerm - empty label falls back to trimmed query`() {
        val unknown = cap("x", "")
        assertEquals("打车去机场", MarketGuide.searchTerm(unknown, "  打车去机场  "))
    }

    @Test
    fun `searchTerm - LLM params from-to are not used`() {
        // LLM params 只有 from/to（地点），搜市场没有意义——取词只看
        // parseContent/label/原句，不看 params（本函数根本不收 params）。
        val dache = cap("dache", "打车")
        assertEquals("打车", MarketGuide.searchTerm(dache, "打车去机场"))
    }

    // ---------- 三支分流 ----------

    @Test
    fun `decideLlmRetry - recognized with installed provider lists apps`() {
        val dache = cap("dache", "打车", packageNames = listOf("com.sdu.didi.psnger", "com.t3go"))
        val outcome = MarketGuide.decideLlmRetry(
            dache, setOf("com.sdu.didi.psnger", "com.tencent.mm"), "打车去机场"
        )
        assertTrue(outcome is MarketGuide.LlmRetryOutcome.InstalledApps)
        assertEquals(
            listOf("com.sdu.didi.psnger"),
            (outcome as MarketGuide.LlmRetryOutcome.InstalledApps).packageNames
        )
    }

    @Test
    fun `decideLlmRetry - installed apps keep declaration order and dedupe`() {
        val video = cap(
            "shipin", "视频",
            providers = listOf(
                CapabilityProvider("A", listOf("pkg.a", "pkg.b")),
                CapabilityProvider("B", listOf("pkg.a"))
            )
        )
        val outcome = MarketGuide.decideLlmRetry(
            video, setOf("pkg.a", "pkg.b"), "想看《狂飙》"
        )
        assertEquals(
            listOf("pkg.a", "pkg.b"),
            (outcome as MarketGuide.LlmRetryOutcome.InstalledApps).packageNames
        )
    }

    @Test
    fun `decideLlmRetry - recognized without installed provider guides to market`() {
        val dache = cap("dache", "打车", packageNames = listOf("com.sdu.didi.psnger"))
        val outcome = MarketGuide.decideLlmRetry(
            dache, setOf("com.tencent.mm"), "打车去机场"
        )
        assertTrue(outcome is MarketGuide.LlmRetryOutcome.MarketGuide)
        assertEquals(
            "打车",
            (outcome as MarketGuide.LlmRetryOutcome.MarketGuide).searchTerm
        )
    }

    @Test
    fun `decideLlmRetry - content capability without installed provider uses content term`() {
        val video = cap(
            "shipin", "视频",
            packageNames = listOf("tv.danmaku.bili", "com.google.android.youtube"),
            params = queryParam
        )
        val outcome = MarketGuide.decideLlmRetry(
            video, setOf("com.tencent.mm"), "想看《狂飙》"
        )
        assertEquals(
            "狂飙",
            (outcome as MarketGuide.LlmRetryOutcome.MarketGuide).searchTerm
        )
    }

    @Test
    fun `decideLlmRetry - unrecognized capability goes to chooser`() {
        val outcome = MarketGuide.decideLlmRetry(null, setOf("com.tencent.mm"), "想看新闻")
        assertEquals(MarketGuide.LlmRetryOutcome.ChooseApp, outcome)
    }

    // ---------- 市场链接拼装与回退 ----------

    @Test
    fun `marketSearchUri encodes term`() {
        assertEquals("market://search?q=%E9%9F%B3%E4%B9%90", MarketGuide.marketSearchUri("音乐"))
        assertEquals("market://search?q=a+b", MarketGuide.marketSearchUri("a b"))
    }

    @Test
    fun `webSearchUri is Play web search with apps category`() {
        assertEquals(
            "https://play.google.com/store/search?q=%E6%89%93%E8%BD%A6&c=apps",
            MarketGuide.webSearchUri("打车")
        )
    }

    @Test
    fun `pickSearchUri - market first, web fallback, null when neither resolves`() {
        assertEquals(
            MarketGuide.marketSearchUri("音乐"),
            MarketGuide.pickSearchUri("音乐", marketAvailable = true, webAvailable = true)
        )
        assertEquals(
            MarketGuide.webSearchUri("音乐"),
            MarketGuide.pickSearchUri("音乐", marketAvailable = false, webAvailable = true)
        )
        assertNull(
            MarketGuide.pickSearchUri("音乐", marketAvailable = false, webAvailable = false)
        )
    }
}
