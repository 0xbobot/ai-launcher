package com.bobot.ailauncher.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * [KeystoreCipherStore] 适配层单测：手写 SharedPreferences 假实现（记录
 * Editor 的落盘方式），验三件事——往返一致、落盘的是 v1 密文、
 * 写/删走 commit() 同步落盘（迁移终态靠磁盘文件判定，apply() 的异步
 * 落盘曾被 force-stop 抢死，见 §25 冒烟 ks-evidence 取证）。
 */
class KeystoreCipherStoreTest {

    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        var commits = 0
        var applies = 0

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String): SharedPreferences.Editor =
                apply { removals += key }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

            private fun flush() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }

            override fun commit(): Boolean {
                commits++
                flush()
                return true
            }

            override fun apply() {
                applies++
                flush()
            }
        }
    }

    private fun store(prefs: FakePrefs) = KeystoreCipherStore(
        prefs,
        AesGcmBox { SecretKeySpec(ByteArray(32) { 0x2A }, "AES") }
    )

    @Test
    fun `write then read round trips and raw value is v1 ciphertext`() {
        val prefs = FakePrefs()
        val store = store(prefs)
        store.write(LlmKeyStore.KEY_API, "sk-adapter-1")
        val raw = prefs.map[LlmKeyStore.KEY_API] as String
        assertTrue(raw.startsWith("v1:"))
        assertFalse(raw.contains("sk-adapter-1"))
        assertEquals("sk-adapter-1", store.read(LlmKeyStore.KEY_API))
    }

    @Test
    fun `write and delete use synchronous commit not apply`() {
        val prefs = FakePrefs()
        val store = store(prefs)
        store.write(LlmKeyStore.KEY_API, "sk-adapter-2")
        store.delete(LlmKeyStore.KEY_API)
        assertEquals(2, prefs.commits)
        assertEquals(0, prefs.applies)
        assertNull(store.read(LlmKeyStore.KEY_API))
    }

    @Test
    fun `missing key reads null and corrupted blob throws`() {
        val prefs = FakePrefs()
        val store = store(prefs)
        assertNull(store.read(LlmKeyStore.KEY_API))
        prefs.map[LlmKeyStore.KEY_API] = "v1:not-valid-base64!!!"
        assertThrows(Exception::class.java) { store.read(LlmKeyStore.KEY_API) }
    }
}
