package com.bobot.ailauncher.data

import android.content.SharedPreferences
import com.bobot.ailauncher.ui.home.SlotTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 智能滚动关键字（Bob 2026-11-05：记用户成功打开应用的动作，按频次、时间
 * 习惯排进输入框滚动清单）单测：动作记录（成功才记）＋ v3 打分排序 ＋
 * 文案还原 ＋ 清单混合。prefs 用内存假实现，不依赖 Android Context。
 */
class ActionUsageTrackerTest {

    /** 内存 SharedPreferences 假实现（只实现本批用到的读写口径）。 */
    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): Map<String, *> = map.toMap()
        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float =
            map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
                apply { pending[key] = values }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor =
                apply { removals += key }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
    }

    private fun ts(year: Int, month: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // 2026-11-02 是周一（dow=1），2026-12-02 是周三（dow=3）
    private val nov2_8am = ts(2026, Calendar.NOVEMBER, 2, 8)
    private val dec2_8am = ts(2026, Calendar.DECEMBER, 2, 8)

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
    fun `current hour bucket 3 times ranks first and rare action stays out`() {
        val prefs = FakePrefs()
        repeat(3) {
            ActionUsageTracker.recordIfOpened(prefs, true, "daohang", "机场", 1, 8, nov2_8am)
        }
        // 只做过 1 次的动作：很少用，不进前排
        ActionUsageTracker.recordIfOpened(prefs, true, "dache", "公司", 1, 8, nov2_8am)
        val top = ActionUsageTracker.topActions(prefs, 1, 8)
        assertEquals(1, top.size)
        assertEquals("daohang", top[0].capabilityId)
        assertEquals("机场", top[0].destination)
        assertTrue(top[0].score > 30000.0)
    }

    @Test
    fun `total 3 across hours is a habit and enters front`() {
        val prefs = FakePrefs()
        ActionUsageTracker.recordIfOpened(prefs, true, "shipin", null, 1, 8, nov2_8am)
        ActionUsageTracker.recordIfOpened(prefs, true, "shipin", null, 1, 12, nov2_8am)
        ActionUsageTracker.recordIfOpened(prefs, true, "shipin", null, 1, 20, nov2_8am)
        // 当前小时桶只有 1 次，但总次数 3 次是跨时段习惯，仍进前排
        val top = ActionUsageTracker.topActions(prefs, 1, 8)
        assertEquals(1, top.size)
        assertEquals("shipin", top[0].capabilityId)
        assertEquals(3.0, top[0].score, 0.0)
    }

    @Test
    fun `no history returns empty front`() {
        val prefs = FakePrefs()
        assertTrue(ActionUsageTracker.topActions(prefs, 1, 8).isEmpty())
    }

    @Test
    fun `failed open is not recorded, success is`() {
        val prefs = FakePrefs()
        ActionUsageTracker.recordIfOpened(prefs, false, "daohang", "机场", 1, 8, nov2_8am)
        assertTrue(ActionUsageTracker.topActions(prefs, 1, 8).isEmpty())
        assertEquals(0, prefs.getInt("count_daohang\u0001机场", 0))
        ActionUsageTracker.recordIfOpened(prefs, true, "daohang", "机场", 1, 8, nov2_8am)
        assertEquals(1, prefs.getInt("count_daohang\u0001机场", 0))
    }

    @Test
    fun `same score breaks tie by most recent`() {
        val prefs = FakePrefs()
        repeat(3) {
            ActionUsageTracker.recordIfOpened(prefs, true, "daohang", "机场", 1, 8, nov2_8am)
        }
        repeat(3) {
            ActionUsageTracker.recordIfOpened(prefs, true, "dache", "机场", 1, 8, nov2_8am + 1000)
        }
        val top = ActionUsageTracker.topActions(prefs, 1, 8)
        assertEquals(2, top.size)
        assertEquals("dache", top[0].capabilityId)
        assertEquals("daohang", top[1].capabilityId)
    }

    @Test
    fun `monthly decay halves counts`() {
        val prefs = FakePrefs()
        repeat(4) {
            ActionUsageTracker.recordIfOpened(prefs, true, "daohang", "机场", 1, 8, nov2_8am)
        }
        // 下个月再记一条：先衰减（总数 4→2），再 +1，总数 3
        ActionUsageTracker.recordIfOpened(prefs, true, "daohang", "机场", 3, 8, dec2_8am)
        val top = ActionUsageTracker.topActions(prefs, 3, 8)
        assertEquals(1, top.size)
        assertEquals(3.0, top[0].score, 0.0)
    }

    @Test
    fun `action key round trip`() {
        assertEquals(
            "daohang" to "机场",
            ActionUsageTracker.parseActionKey(ActionUsageTracker.actionKey("daohang", "机场"))
        )
        assertEquals(
            "shipin" to null,
            ActionUsageTracker.parseActionKey(ActionUsageTracker.actionKey("shipin", null))
        )
        assertEquals(
            "shipin" to null,
            ActionUsageTracker.parseActionKey(ActionUsageTracker.actionKey("shipin", ""))
        )
    }

    @Test
    fun `phrase for nav template fills destination slot`() {
        val template = SlotTemplates.navTemplate(null)
        assertEquals("导航去机场", SlotTemplates.actionPhrase(template, "导航", "机场"))
        assertEquals("导航去公司", SlotTemplates.actionPhrase(template, "导航", "公司"))
        // 无目的地时用模板自带默认值（机场），不是槽位 label
        assertEquals("导航去机场", SlotTemplates.actionPhrase(template, "导航", null))
    }

    @Test
    fun `phrase for ditie substitutes default destination`() {
        val template = SlotTemplates.fixedTemplate("ditie", "ditie", "查去机场的地铁")
        assertEquals("查去公司的地铁", SlotTemplates.actionPhrase(template, "地铁", "公司"))
        assertEquals("查去机场的地铁", SlotTemplates.actionPhrase(template, "地铁", "机场"))
        assertEquals("查去机场的地铁", SlotTemplates.actionPhrase(template, "地铁", null))
    }

    @Test
    fun `phrase without template falls back to label`() {
        assertEquals("导航去机场", SlotTemplates.actionPhrase(null, "导航", "机场"))
        assertEquals("聊天", SlotTemplates.actionPhrase(null, "聊天", null))
    }

    @Test
    fun `phrase for fixed sentence without destination stays as is`() {
        val template = SlotTemplates.fixedTemplate("shipin", "shipin", "我想刷视频")
        assertEquals("我想刷视频", SlotTemplates.actionPhrase(template, "视频", null))
    }

    @Test
    fun `tapAction matches capability destination and provider`() {
        val caps = listOf(daohang, ditie)
        val result = CapabilityResolution.tapAction(caps, "导航去机场", "com.autonavi.minimap")
        assertEquals("daohang" to "机场", result?.let { it.first.id to it.second })
        val metro = CapabilityResolution.tapAction(caps, "查去机场的地铁", "com.autonavi.minimap")
        assertEquals("ditie" to "机场", metro?.let { it.first.id to it.second })
    }

    @Test
    fun `tapAction returns null for non-provider app`() {
        val caps = listOf(daohang, ditie)
        assertNull(CapabilityResolution.tapAction(caps, "导航去机场", "com.tencent.mm"))
    }

    @Test
    fun `tapAction returns null without destination or with label as destination`() {
        val caps = listOf(daohang, ditie)
        assertNull(CapabilityResolution.tapAction(caps, "导航", "com.autonavi.minimap"))
        assertNull(CapabilityResolution.tapAction(caps, "导航去导航", "com.autonavi.minimap"))
    }

    @Test
    fun `merge puts personalized first and dedupes static`() {
        val personalized = listOf(
            SlotTemplates.fixedTemplate("action_daohang_机场", "daohang", "导航去机场")
        )
        val static = listOf(
            SlotTemplates.fixedTemplate("nav", "daohang", "导航去机场"),
            SlotTemplates.fixedTemplate("shipin", "shipin", "我想刷视频")
        )
        val merged = SlotTemplates.mergeKeywordPools(personalized, static)
        assertEquals(listOf("导航去机场", "我想刷视频"), merged.map { it.exampleFillText() })
    }

    @Test
    fun `merge with no personalized returns static unchanged`() {
        val static = listOf(SlotTemplates.fixedTemplate("nav", "daohang", "导航去机场"))
        assertEquals(static, SlotTemplates.mergeKeywordPools(emptyList(), static))
    }
}
