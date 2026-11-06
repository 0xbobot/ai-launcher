package com.bobot.ailauncher.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 反馈闭环·用户教的映射（Bob 2026-11-06：识别不到时，用户在选择器里点了一个
 * 应用，把这次的映射存下来，以后同句能认出来）单测：存取、归一化、空值守卫。
 * prefs 用内存假实现（ActionUsageTrackerTest 同款），不依赖 Android Context。
 */
class UserIntentMappingTest {

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

    @Test
    fun `save then lookup returns the mapped package`() {
        val prefs = FakePrefs()
        UserIntentMapping.save(prefs, "我想看《狂飙》", "com.iqiyi", 1000L)
        assertEquals("com.iqiyi", UserIntentMapping.lookup(prefs, "我想看《狂飙》"))
    }

    @Test
    fun `lookup normalizes the query the same way as save`() {
        val prefs = FakePrefs()
        UserIntentMapping.save(prefs, "我想看《狂飙》", "com.iqiyi", 1000L)
        // 大小写/空格/首尾空白不同，仍是同一句。
        assertEquals("com.iqiyi", UserIntentMapping.lookup(prefs, " 我想看 《狂飙》 "))
        assertEquals("com.iqiyi", UserIntentMapping.lookup(prefs, "我想看《狂飙》"))
    }

    @Test
    fun `lookup returns null when nothing was taught`() {
        val prefs = FakePrefs()
        assertNull(UserIntentMapping.lookup(prefs, "我想看《狂飙》"))
    }

    @Test
    fun `save overwrites the previous mapping for the same query`() {
        val prefs = FakePrefs()
        UserIntentMapping.save(prefs, "我想看《狂飙》", "com.iqiyi", 1000L)
        UserIntentMapping.save(prefs, "我想看《狂飙》", "com.bilibili", 2000L)
        assertEquals("com.bilibili", UserIntentMapping.lookup(prefs, "我想看《狂飙》"))
    }

    @Test
    fun `save ignores blank query and blank package`() {
        val prefs = FakePrefs()
        UserIntentMapping.save(prefs, "   ", "com.iqiyi", 1000L)
        UserIntentMapping.save(prefs, "我想看《狂飙》", "  ", 1000L)
        assertNull(UserIntentMapping.lookup(prefs, "   "))
        assertNull(UserIntentMapping.lookup(prefs, "我想看《狂飙》"))
    }

    @Test
    fun `normalizeQuery trims lowercases and removes spaces`() {
        assertEquals("我想看《狂飙》", UserIntentMapping.normalizeQuery(" 我想看 《狂飙》 "))
        assertEquals("abc", UserIntentMapping.normalizeQuery(" A B C "))
    }
}
