package com.bobot.ailauncher.data

import com.bobot.ailauncher.core.intent.CapabilityHints
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
                "amap://route?dlat={lat}&dlon={lon}&t=1"
            ),
            CapabilityProvider(
                "腾讯地图", listOf("com.tencent.map"),
                "qqmap://map/routeplan?type=bus&tocoord={lat},{lon}&referer=ai-launcher"
            ),
            CapabilityProvider(
                "百度地图", listOf("com.baidu.BaiduMap"),
                "baidumap://map/direction?destination=latlng:{lat},{lon}&mode=transit&coord_type=gcj02&src=ai-launcher"
            )
        ),
        geoFallback = GeoFallback.COORD,
        params = listOf(
            CapabilityParam("lat", "目的地纬度", required = true),
            CapabilityParam("lon", "目的地经度", required = true)
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

    @Test
    fun `hints built from resolvable subset excludes unavailable capabilities`() {
        // 与生产过滤同一口径（RegistryAdapter.hints -> executableCapabilities
        // 的纯函数部分 isResolvable）：只装腾讯地图的手机，微信不许进清单
        val wechat = Capability(
            id = "wechat", label = "微信", iconName = "",
            packageNames = listOf("com.tencent.mm"), deeplinkTemplate = null
        )
        val installed = setOf("com.tencent.map")
        val executable = listOf(wechat, daohang).filter {
            CapabilityResolution.isResolvable(it, installed)
        }
        val hints = CapabilityHints.build(executable)
        assertTrue(hints.contains("daohang: 导航"))
        assertFalse(hints.contains("wechat"))
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
        // 地铁只有名无坐标：ditie 深链与 geo 都要 lat/lon，填不出
        assertNull(
            CapabilityResolution.tapSteps(carryingCaps, "查去机场的地铁", "com.autonavi.minimap")
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
}
