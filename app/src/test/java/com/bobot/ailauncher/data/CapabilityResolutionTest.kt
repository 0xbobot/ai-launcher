package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能力按已装应用动态绑定（v0.68.88）纯逻辑单测：
 * 提供方优先序、候选链顺序、geo URI 构造/编码、hints 过滤口径。
 */
class CapabilityResolutionTest {

    private val daohang = Capability(
        id = "daohang",
        label = "导航",
        iconName = "",
        packageNames = listOf("com.autonavi.minimap", "com.baidu.BaiduMap", "com.tencent.map"),
        deeplinkTemplate = "amap://route?dname={to}",
        providers = listOf(
            CapabilityProvider(
                "高德地图", listOf("com.autonavi.minimap"),
                "amap://route?sourceApplication=ai-launcher&dname={to}&dev=0&t=1"
            ),
            CapabilityProvider(
                "百度地图", listOf("com.baidu.BaiduMap"),
                "baidumap://map/direction?destination={to}&mode=driving&src=ai-launcher"
            ),
            CapabilityProvider("腾讯地图", listOf("com.tencent.map"), null)
        ),
        geoFallback = GeoFallback.NAME,
        params = listOf(CapabilityParam("to", "目的地", required = true))
    )

    private val ditie = Capability(
        id = "ditie",
        label = "地铁",
        iconName = "",
        packageNames = listOf("com.autonavi.minimap", "com.tencent.map", "com.baidu.BaiduMap"),
        deeplinkTemplate = null,
        providers = listOf(
            CapabilityProvider(
                "高德地图", listOf("com.autonavi.minimap"),
                "amap://route?dlat={lat}&dlon={lon}&t=1",
                "amap://route?sourceApplication=ai-launcher&dname={to}&dev=0&t=1"
            ),
            CapabilityProvider(
                "腾讯地图", listOf("com.tencent.map"),
                "qqmap://map/routeplan?type=bus&tocoord={lat},{lon}&referer=ai-launcher",
                null
            ),
            CapabilityProvider(
                "百度地图", listOf("com.baidu.BaiduMap"),
                "baidumap://map/direction?destination=latlng:{lat},{lon}&mode=transit&coord_type=gcj02&src=ai-launcher",
                "baidumap://map/direction?destination={to}&mode=transit&src=ai-launcher"
            )
        ),
        geoFallback = GeoFallback.COORD_OR_NAME,
        params = listOf(
            CapabilityParam("lat", "目的地纬度", required = false),
            CapabilityParam("lon", "目的地经度", required = false),
            CapabilityParam("to", "目的地", required = false)
        )
    )

    @Test
    fun `first installed provider wins by declaration order`() {
        val installed = setOf("com.tencent.map", "com.baidu.BaiduMap")
        val (provider, pkg) = CapabilityResolution.firstInstalledProvider(daohang, installed)!!
        assertEquals("百度地图", provider.name)
        assertEquals("com.baidu.BaiduMap", pkg)
    }

    @Test
    fun `no provider installed is not resolvable`() {
        assertNull(CapabilityResolution.firstInstalledProvider(daohang, emptySet()))
        assertFalse(CapabilityResolution.isResolvable(daohang, emptySet()))
    }

    @Test
    fun `tencent-only phone binds tencent and falls back geo then open-app`() {
        val steps = CapabilityResolution.steps(
            daohang, setOf("com.tencent.map"), mapOf("to" to "机场")
        )
        // 腾讯没有名称深链（routeplan 要坐标）：链 = geo 兜底 -> 打开腾讯地图本体
        assertEquals(2, steps.size)
        assertEquals(LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA"), steps[0])
        assertEquals(LaunchStep.OpenApp("腾讯地图", "com.tencent.map"), steps[1])
    }

    @Test
    fun `amap installed leads with its deeplink then geo then open-app`() {
        val steps = CapabilityResolution.steps(
            daohang, setOf("com.autonavi.minimap"), mapOf("to" to "机场")
        )
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1"
            ),
            steps[0]
        )
        assertTrue(steps[1] is LaunchStep.Geo)
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[2])
    }

    @Test
    fun `unfillable template yields no deeplink step and no geo without param`() {
        val steps = CapabilityResolution.steps(
            daohang, setOf("com.autonavi.minimap"), emptyMap()
        )
        assertEquals(listOf(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap")), steps)
    }

    @Test
    fun `nothing installed leaves only the geo step for name navigation`() {
        val steps = CapabilityResolution.steps(daohang, emptySet(), mapOf("to" to "机场"))
        assertEquals(listOf(LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA")), steps)
    }

    @Test
    fun `ditie tencent step uses official routeplan with tocoord`() {
        val steps = CapabilityResolution.steps(
            ditie, setOf("com.tencent.map"),
            mapOf("lat" to "39.9042", "lon" to "116.4074")
        )
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "腾讯地图", "com.tencent.map",
                "qqmap://map/routeplan?type=bus&tocoord=39.9042,116.4074&referer=ai-launcher"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.Geo("geo:39.9042,116.4074?q=39.9042,116.4074"), steps[1])
        assertEquals(LaunchStep.OpenApp("腾讯地图", "com.tencent.map"), steps[2])
    }

    @Test
    fun `legacy capability without providers synthesizes one provider`() {
        val legacy = Capability(
            id = "wechat", label = "微信", iconName = "",
            packageNames = listOf("com.tencent.mm"),
            deeplinkTemplate = null
        )
        val providers = CapabilityResolution.effectiveProviders(legacy)
        assertEquals(1, providers.size)
        assertEquals(listOf("com.tencent.mm"), providers[0].packageNames)
        val steps = CapabilityResolution.steps(legacy, setOf("com.tencent.mm"), emptyMap())
        assertEquals(listOf(LaunchStep.OpenApp("微信", "com.tencent.mm")), steps)
    }

    @Test
    fun `fallbackToLauncher off drops the open-app step`() {
        val cap = daohang.copy(fallbackToLauncher = false)
        val steps = CapabilityResolution.steps(cap, setOf("com.tencent.map"), mapOf("to" to "机场"))
        assertEquals(listOf(LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA")), steps)
    }

    @Test
    fun `geo query encodes space as percent-20 not plus`() {
        assertEquals("geo:0,0?q=a%20b", CapabilityResolution.geoUriForQuery("a b"))
    }

    @Test
    fun `geo coord rejects garbage and out-of-range values`() {
        assertNull(CapabilityResolution.geoUriForCoord("abc", "116.4"))
        assertNull(CapabilityResolution.geoUriForCoord("91.0", "116.4"))
        assertNull(CapabilityResolution.geoUriForCoord("39.9", "181.0"))
        assertNull(CapabilityResolution.geoUriForCoord("", "116.4"))
    }

    // ---------- 432：点搜索结果带目的地（parseDestination + tapSteps） ----------

    private val dache = Capability(
        id = "dache",
        label = "打车",
        iconName = "",
        packageNames = listOf("com.sdu.didi.psnger", "com.didapassenger"),
        deeplinkTemplate = null,
        geoFallback = GeoFallback.NONE,
        params = listOf(CapabilityParam("to", "目的地", required = true))
    )

    private val carryingCaps = listOf(daohang, ditie, dache)

    // ---------- 内容深链：音乐/视频（query 参数 + 名称搜索模板） ----------

    private val yinyue = Capability(
        id = "yinyue",
        label = "音乐",
        iconName = "",
        packageNames = listOf(
            "com.tencent.qqmusic", "com.netease.cloudmusic", "com.kugou.android",
            "com.spotify.music", "com.apple.android.music",
            "com.google.android.apps.youtube.music"
        ),
        deeplinkTemplate = null,
        providers = listOf(
            CapabilityProvider("QQ音乐", listOf("com.tencent.qqmusic"), null, null),
            CapabilityProvider("网易云音乐", listOf("com.netease.cloudmusic"), null, null),
            CapabilityProvider("酷狗音乐", listOf("com.kugou.android"), null, null),
            CapabilityProvider("Spotify", listOf("com.spotify.music"), null, "spotify:search:{query}"),
            CapabilityProvider("Apple Music", listOf("com.apple.android.music"), null, null),
            CapabilityProvider(
                "YouTube Music", listOf("com.google.android.apps.youtube.music"),
                null, "https://music.youtube.com/search?q={query}"
            )
        ),
        geoFallback = GeoFallback.NONE,
        params = listOf(CapabilityParam("query", "内容", required = false))
    )

    private val shipin = Capability(
        id = "shipin",
        label = "视频",
        iconName = "",
        packageNames = listOf(
            "com.ss.android.ugc.aweme", "com.smile.gifmaker",
            "tv.danmaku.bili", "com.google.android.youtube"
        ),
        deeplinkTemplate = null,
        providers = listOf(
            CapabilityProvider("抖音", listOf("com.ss.android.ugc.aweme"), null, null),
            CapabilityProvider("快手", listOf("com.smile.gifmaker"), null, null),
            CapabilityProvider(
                "哔哩哔哩", listOf("tv.danmaku.bili"),
                null, "https://search.bilibili.com/all?keyword={query}"
            ),
            CapabilityProvider(
                "YouTube", listOf("com.google.android.youtube"),
                null, "https://www.youtube.com/results?search_query={query}"
            )
        ),
        geoFallback = GeoFallback.NONE,
        params = listOf(CapabilityParam("query", "内容", required = false))
    )

    private val contentCaps = listOf(yinyue, shipin)

    @Test
    fun `parseDestination takes text after qu or dao`() {
        assertEquals("机场", CapabilityResolution.parseDestination("导航去机场"))
        assertEquals("机场", CapabilityResolution.parseDestination("查去机场的地铁"))
        assertEquals("机场", CapabilityResolution.parseDestination("导航到机场"))
        assertEquals("公司", CapabilityResolution.parseDestination("从机场去公司"))
        assertEquals("首都机场T3", CapabilityResolution.parseDestination("导航去首都机场T3"))
    }

    @Test
    fun `parseDestination returns null without a real destination`() {
        assertNull(CapabilityResolution.parseDestination("只有导航"))
        assertNull(CapabilityResolution.parseDestination("导航"))
        assertNull(CapabilityResolution.parseDestination("导航去哪儿"))
        assertNull(CapabilityResolution.parseDestination("导航去哪里"))
    }

    @Test
    fun `tap nav to airport on amap carries destination in deeplink`() {
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "导航去机场", "com.autonavi.minimap"
        )!!
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.autonavi.minimap"),
            steps[1]
        )
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[2])
    }

    @Test
    fun `tap nav to airport on baidu carries destination in deeplink`() {
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "导航去机场", "com.baidu.BaiduMap"
        )!!
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "百度地图", "com.baidu.BaiduMap",
                "baidumap://map/direction?destination=%E6%9C%BA%E5%9C%BA&mode=driving&src=ai-launcher"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.baidu.BaiduMap"),
            steps[1]
        )
        assertEquals(LaunchStep.OpenApp("百度地图", "com.baidu.BaiduMap"), steps[2])
    }

    @Test
    fun `tap nav to airport on tencent falls back to geo targeted at tencent`() {
        // 腾讯没有名称深链（routeplan 要坐标）：链 = 定向 geo -> 打开腾讯地图本体
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "导航去机场", "com.tencent.map"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.tencent.map"),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("腾讯地图", "com.tencent.map"), steps[1])
    }

    @Test
    fun `tap returns null when nothing can be carried`() {
        // 点非地图应用：不是该能力的提供方
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "导航去机场", "com.tencent.mm")
        )
        // 只有“导航”没目的地
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "导航", "com.autonavi.minimap")
        )
        // 只有“地铁”没目的地
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "查地铁", "com.autonavi.minimap")
        )
        // 打车：没深链也没 geo，没东西可带
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "打车去机场", "com.sdu.didi.psnger")
        )
        // 目的地就是能力名本身（“出去导航”）：不是真目的地
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "出去导航", "com.autonavi.minimap")
        )
    }

    @Test
    fun `tap steps drop open-app when fallbackToLauncher off`() {
        val noFallback = daohang.copy(fallbackToLauncher = false)
        val steps = CapabilityResolution.tapSteps(
            listOf(noFallback), "导航去机场", "com.autonavi.minimap"
        )!!
        assertEquals(2, steps.size)
        assertTrue(steps[0] is LaunchStep.Deeplink)
        assertTrue(steps[1] is LaunchStep.Geo)
    }

    // ---------- 地铁带名：只有名字、没有坐标时也用地铁方式直达 ----------

    @Test
    fun `tap ditie to airport on amap carries name in transit deeplink`() {
        // 高德 route 官方格式：dname=终点名称，t=1 公交；与 daohang 的高德模板同一条
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "查去机场的地铁", "com.autonavi.minimap"
        )!!
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.autonavi.minimap"),
            steps[1]
        )
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[2])
    }

    @Test
    fun `tap ditie to airport on baidu carries name in transit deeplink`() {
        // 百度 direction 官方格式：destination 可填名称，mode=transit 公交
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "查去机场的地铁", "com.baidu.BaiduMap"
        )!!
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "百度地图", "com.baidu.BaiduMap",
                "baidumap://map/direction?destination=%E6%9C%BA%E5%9C%BA&mode=transit&src=ai-launcher"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.baidu.BaiduMap"),
            steps[1]
        )
        assertEquals(LaunchStep.OpenApp("百度地图", "com.baidu.BaiduMap"), steps[2])
    }

    @Test
    fun `tap ditie to airport on tencent falls back to geo targeted at tencent`() {
        // 腾讯 routeplan 官方要求 tocoord 坐标、to 只是展示名：没有名称深链，
        // 退 geo 名称查询（与 daohang 的腾讯处理同一口径），不打开残缺链接
        val steps = CapabilityResolution.tapSteps(
            carryingCaps, "查去机场的地铁", "com.tencent.map"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Geo("geo:0,0?q=%E6%9C%BA%E5%9C%BA", "com.tencent.map"),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("腾讯地图", "com.tencent.map"), steps[1])
    }

    @Test
    fun `ditie name steps via assistant path carry transit deeplink`() {
        // 问助手路径（steps）同样受益：名字目的地 → 地铁方式深链在前
        val steps = CapabilityResolution.steps(
            ditie, setOf("com.autonavi.minimap"), mapOf("to" to "机场")
        )
        assertEquals(3, steps.size)
        assertTrue(steps[0] is LaunchStep.Deeplink)
        assertEquals(
            "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1",
            (steps[0] as LaunchStep.Deeplink).uri
        )
        assertTrue(steps[1] is LaunchStep.Geo)
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[2])
    }

    @Test
    fun `ditie coord steps unchanged when coords given`() {
        // 有坐标时行为不变：坐标深链在前、geo 坐标点在后
        val steps = CapabilityResolution.steps(
            ditie, setOf("com.autonavi.minimap"),
            mapOf("lat" to "39.9042", "lon" to "116.4074")
        )
        assertEquals(3, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?dlat=39.9042&dlon=116.4074&t=1"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.Geo("geo:39.9042,116.4074?q=39.9042,116.4074"), steps[1])
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[2])
    }

    @Test
    fun `ditie coord deeplink wins over name when both given`() {
        val steps = CapabilityResolution.steps(
            ditie, setOf("com.autonavi.minimap"),
            mapOf("lat" to "39.9042", "lon" to "116.4074", "to" to "机场")
        )
        assertEquals(4, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?dlat=39.9042&dlon=116.4074&t=1"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.Deeplink(
                "高德地图", "com.autonavi.minimap",
                "amap://route?sourceApplication=ai-launcher&dname=%E6%9C%BA%E5%9C%BA&dev=0&t=1"
            ),
            steps[1]
        )
        assertEquals(LaunchStep.Geo("geo:39.9042,116.4074?q=39.9042,116.4074"), steps[2])
        assertEquals(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap"), steps[3])
    }

    @Test
    fun `ditie geo fallback prefers coord then name`() {
        assertEquals(
            "geo:39.9042,116.4074?q=39.9042,116.4074",
            CapabilityResolution.geoUri(ditie, mapOf("lat" to "39.9042", "lon" to "116.4074"))
        )
        assertEquals(
            "geo:0,0?q=%E6%9C%BA%E5%9C%BA",
            CapabilityResolution.geoUri(ditie, mapOf("to" to "机场"))
        )
        assertNull(CapabilityResolution.geoUri(ditie, emptyMap()))
        // 坐标非法时退名称查询，不拼脏坐标
        assertEquals(
            "geo:0,0?q=%E6%9C%BA%E5%9C%BA",
            CapabilityResolution.geoUri(
                ditie, mapOf("lat" to "abc", "lon" to "116.4074", "to" to "机场")
            )
        )
    }

    @Test
    fun `ditie with no destination opens app only`() {
        val steps = CapabilityResolution.steps(
            ditie, setOf("com.autonavi.minimap"), emptyMap()
        )
        assertEquals(listOf(LaunchStep.OpenApp("高德地图", "com.autonavi.minimap")), steps)
    }

    // ---------- 内容深链：音乐“周杰伦” / 视频“狂飙” ----------

    @Test
    fun `parseContent extracts singer from listen sentence`() {
        assertEquals("周杰伦", CapabilityResolution.parseContent("我想听周杰伦的歌"))
        assertEquals("周杰伦", CapabilityResolution.parseContent("我想听周杰伦"))
        assertEquals("周杰伦", CapabilityResolution.parseContent("播放周杰伦"))
        assertEquals("告白气球", CapabilityResolution.parseContent("我想听《告白气球》"))
    }

    @Test
    fun `parseContent extracts show title from watch sentence`() {
        assertEquals("狂飙", CapabilityResolution.parseContent("我想看《狂飙》"))
        assertEquals("狂飙", CapabilityResolution.parseContent("我想看狂飙"))
        assertEquals("狂飙", CapabilityResolution.parseContent("追狂飙"))
    }

    @Test
    fun `parseContent returns null without real content`() {
        assertNull(CapabilityResolution.parseContent("我想听歌"))
        assertNull(CapabilityResolution.parseContent("我想看视频"))
        assertNull(CapabilityResolution.parseContent("导航去机场"))
        assertNull(CapabilityResolution.parseContent("周杰伦"))
    }

    @Test
    fun `tap music on spotify carries singer in official search deeplink`() {
        val steps = CapabilityResolution.tapSteps(
            contentCaps, "我想听周杰伦的歌", "com.spotify.music"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "Spotify", "com.spotify.music",
                "spotify:search:%E5%91%A8%E6%9D%B0%E4%BC%A6"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("Spotify", "com.spotify.music"), steps[1])
    }

    @Test
    fun `tap music on youtube music carries singer in search app link`() {
        val steps = CapabilityResolution.tapSteps(
            contentCaps, "我想听周杰伦的歌", "com.google.android.apps.youtube.music"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "YouTube Music", "com.google.android.apps.youtube.music",
                "https://music.youtube.com/search?q=%E5%91%A8%E6%9D%B0%E4%BC%A6"
            ),
            steps[0]
        )
        assertEquals(
            LaunchStep.OpenApp("YouTube Music", "com.google.android.apps.youtube.music"),
            steps[1]
        )
    }

    @Test
    fun `tap music on qq music without template returns null and opens app`() {
        // QQ音乐无已核实官方深链：没东西可带，tapSteps 返回 null，调用方直接打开
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想听周杰伦的歌", "com.tencent.qqmusic"
            )
        )
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想听周杰伦的歌", "com.netease.cloudmusic"
            )
        )
    }

    @Test
    fun `tap video on bilibili carries show title in search app link`() {
        val steps = CapabilityResolution.tapSteps(
            contentCaps, "我想看《狂飙》", "tv.danmaku.bili"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "哔哩哔哩", "tv.danmaku.bili",
                "https://search.bilibili.com/all?keyword=%E7%8B%82%E9%A3%99"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("哔哩哔哩", "tv.danmaku.bili"), steps[1])
    }

    @Test
    fun `tap video on youtube carries show title in search app link`() {
        val steps = CapabilityResolution.tapSteps(
            contentCaps, "我想看《狂飙》", "com.google.android.youtube"
        )!!
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "YouTube", "com.google.android.youtube",
                "https://www.youtube.com/results?search_query=%E7%8B%82%E9%A3%99"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("YouTube", "com.google.android.youtube"), steps[1])
    }

    @Test
    fun `tap video on douyin without template returns null and opens app`() {
        // 抖音/快手无已核实官方搜索深链：没东西可带，调用方直接打开
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想看《狂飙》", "com.ss.android.ugc.aweme"
            )
        )
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想看《狂飙》", "com.smile.gifmaker"
            )
        )
    }

    @Test
    fun `tap content returns null for wrong provider or no content`() {
        // 点视频应用听音乐：不是该能力的提供方
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想听周杰伦的歌", "tv.danmaku.bili"
            )
        )
        // 点音乐应用看视频：不是该能力的提供方
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想看《狂飙》", "com.spotify.music"
            )
        )
        // 只有“想听歌”没具体内容
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想听歌", "com.spotify.music"
            )
        )
        // 只有“想看视频”没具体内容
        assertNull(
            CapabilityResolution.tapSteps(
                contentCaps, "我想看视频", "tv.danmaku.bili"
            )
        )
    }

    @Test
    fun `music query steps via assistant path carry search deeplink`() {
        // 问助手路径（steps）同样受益：query 参数 → 名称搜索深链在前
        val steps = CapabilityResolution.steps(
            yinyue, setOf("com.spotify.music"), mapOf("query" to "周杰伦")
        )
        assertEquals(2, steps.size)
        assertEquals(
            LaunchStep.Deeplink(
                "Spotify", "com.spotify.music", "spotify:search:%E5%91%A8%E6%9D%B0%E4%BC%A6"
            ),
            steps[0]
        )
        assertEquals(LaunchStep.OpenApp("Spotify", "com.spotify.music"), steps[1])
    }

    @Test
    fun `music with no query opens app only`() {
        val steps = CapabilityResolution.steps(
            yinyue, setOf("com.spotify.music"), emptyMap()
        )
        assertEquals(listOf(LaunchStep.OpenApp("Spotify", "com.spotify.music")), steps)
    }

    // ---------- 内容深链标识：supportsDeepLink ----------

    @Test
    fun `badge shows for bilibili on show title but not iqiyi or douyin`() {
        assertTrue(CapabilityResolution.supportsDeepLink(contentCaps, "我想看《狂飙》", "tv.danmaku.bili"))
        assertTrue(CapabilityResolution.supportsDeepLink(contentCaps, "我想看《狂飙》", "com.google.android.youtube"))
        // 爱奇艺不是 shipin 提供方、抖音无搜索深链模板：点按只打开首页，不标
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想看《狂飙》", "com.qiyi.video"))
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想看《狂飙》", "com.ss.android.ugc.aweme"))
    }

    @Test
    fun `badge shows for spotify on singer but not qq music`() {
        assertTrue(CapabilityResolution.supportsDeepLink(contentCaps, "我想听周杰伦的歌", "com.spotify.music"))
        assertTrue(CapabilityResolution.supportsDeepLink(contentCaps, "我想听周杰伦的歌", "com.google.android.apps.youtube.music"))
        // QQ音乐/网易云无搜索深链模板：点按只打开首页，不标
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想听周杰伦的歌", "com.tencent.qqmusic"))
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想听周杰伦的歌", "com.netease.cloudmusic"))
    }

    @Test
    fun `badge hidden when query has no real content`() {
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想听歌", "com.spotify.music"))
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "我想看视频", "tv.danmaku.bili"))
    }

    @Test
    fun `badge hidden for plain search without capability`() {
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "微信", "com.tencent.mm"))
        assertFalse(CapabilityResolution.supportsDeepLink(contentCaps, "微信", "tv.danmaku.bili"))
    }

    @Test
    fun `badge counts deeplink only geo-only tencent gets no badge`() {
        // 高德有名称深链模板：标；腾讯只有名称 geo 兜底（不是深链）：不标
        assertTrue(CapabilityResolution.supportsDeepLink(carryingCaps, "查去机场的地铁", "com.autonavi.minimap"))
        assertFalse(CapabilityResolution.supportsDeepLink(carryingCaps, "查去机场的地铁", "com.tencent.map"))
        assertTrue(CapabilityResolution.supportsDeepLink(carryingCaps, "导航去机场", "com.autonavi.minimap"))
        assertFalse(CapabilityResolution.supportsDeepLink(carryingCaps, "导航去机场", "com.tencent.map"))
    }
}
