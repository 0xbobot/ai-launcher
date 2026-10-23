package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class MigratingSecureStoreTest {

    /** 内存假存储（可指定读/写抛异常，模拟新格式密钥不可用等故障） */
    private class FakeStore(
        initial: Map<String, String> = emptyMap(),
        private val failRead: Boolean = false,
        private val failWrite: Boolean = false
    ) : LlmKeyStore.Store {
        val map = initial.toMutableMap()
        override fun read(key: String): String? {
            if (failRead) throw RuntimeException("keystore broken")
            return map[key]
        }
        override fun write(key: String, value: String) {
            if (failWrite) throw RuntimeException("keystore broken")
            map[key] = value
        }
        override fun delete(key: String) {
            map.remove(key)
        }
    }

    /**
     * 用真 [AesGcmBox] 背书的假新存储：落盘值必须是密文，
     * 迁移测试据此断言「迁过去的是真加密格式，不是明文搬家」。
     */
    private class BoxBackedStore : LlmKeyStore.Store {
        val box = AesGcmBox { SecretKeySpec(ByteArray(32) { 0x2A }, "AES") }
        val raw = mutableMapOf<String, String>()
        override fun read(key: String): String? = raw[key]?.let { box.decrypt(it, key) }
        override fun write(key: String, value: String) {
            raw[key] = box.encrypt(value, key)
        }
        override fun delete(key: String) {
            raw.remove(key)
        }
    }

    @Test
    fun `legacy value migrates into new store as real ciphertext and legacy entry is deleted`() {
        val newStore = BoxBackedStore()
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-from-alpha06"))
        var migrated = false
        val store = MigratingSecureStore(newStore, legacy, onMigrated = { migrated = true })

        assertEquals("sk-from-alpha06", store.read(LlmKeyStore.KEY_API))
        // 新格式里躺的是 v1 密文、不是明文搬家，且能独立解回
        val stored = newStore.raw[LlmKeyStore.KEY_API]
        assertTrue(stored!!.startsWith("v1:"))
        assertTrue(!stored.contains("sk-from-alpha06"))
        assertEquals("sk-from-alpha06", newStore.box.decrypt(stored, LlmKeyStore.KEY_API))
        // 旧条目已删、迁移回调已触发
        assertFalse(legacy.map.containsKey(LlmKeyStore.KEY_API))
        assertTrue(migrated)
        // 再读走新格式，不再触碰旧格式
        assertEquals("sk-from-alpha06", store.read(LlmKeyStore.KEY_API))
    }

    @Test
    fun `failed migration returns legacy value and keeps legacy entry for next attempt`() {
        val brokenNew = FakeStore(failWrite = true)
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-keepme"))
        var migrated = false
        var failed = false
        val store = MigratingSecureStore(
            brokenNew, legacy,
            onMigrated = { migrated = true },
            onMigrationFailed = { failed = true }
        )
        assertEquals("sk-keepme", store.read(LlmKeyStore.KEY_API))
        // Key 不丢：旧条目原样保留，未报迁移成功
        assertEquals("sk-keepme", legacy.map[LlmKeyStore.KEY_API])
        assertFalse(migrated)
        assertTrue(failed)
    }

    @Test
    fun `new store value wins over stale legacy value`() {
        val newStore = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-new"))
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-old"))
        val store = MigratingSecureStore(newStore, legacy)
        assertEquals("sk-new", store.read(LlmKeyStore.KEY_API))
    }

    @Test
    fun `new store read failure falls back to legacy value`() {
        val brokenNew = FakeStore(failRead = true)
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-rescue"))
        val store = MigratingSecureStore(brokenNew, legacy)
        assertEquals("sk-rescue", store.read(LlmKeyStore.KEY_API))
        // 迁移写同样会失败（failRead 的假存储 write 不抛，但回读核对必然失败）：
        // 旧条目必须仍在
        assertEquals("sk-rescue", legacy.map[LlmKeyStore.KEY_API])
    }

    @Test
    fun `write goes to new store and clears stale legacy entry`() {
        val newStore = FakeStore()
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-old"))
        val store = MigratingSecureStore(newStore, legacy)
        store.write(LlmKeyStore.KEY_API, "sk-fresh")
        assertEquals("sk-fresh", newStore.map[LlmKeyStore.KEY_API])
        assertFalse(legacy.map.containsKey(LlmKeyStore.KEY_API))
    }

    @Test
    fun `write failure propagates so caller can fall back with notice`() {
        val brokenNew = FakeStore(failWrite = true)
        val store = MigratingSecureStore(brokenNew, FakeStore())
        var threw = false
        try {
            store.write(LlmKeyStore.KEY_API, "sk-x")
        } catch (e: RuntimeException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `delete removes from both stores`() {
        val newStore = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-new"))
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-old"))
        val store = MigratingSecureStore(newStore, legacy)
        store.delete(LlmKeyStore.KEY_API)
        assertFalse(newStore.map.containsKey(LlmKeyStore.KEY_API))
        assertFalse(legacy.map.containsKey(LlmKeyStore.KEY_API))
    }

    @Test
    fun `no legacy store behaves like plain new store`() {
        val newStore = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-new"))
        val store = MigratingSecureStore(newStore, null)
        assertEquals("sk-new", store.read(LlmKeyStore.KEY_API))
        assertNull(store.read("missing"))
    }

    @Test
    fun `empty everywhere returns null`() {
        val store = MigratingSecureStore(FakeStore(), FakeStore())
        assertNull(store.read(LlmKeyStore.KEY_API))
    }

    @Test
    fun `end to end with LlmKeyStore - plaintext and legacy both converge to new store`() {
        // 模拟升级用户：Key 只在旧 alpha06 格式里（用 FakeStore 代表），
        // 明文层为空。经 LlmKeyStore.readApiKey 读一次后：值读出、
        // 新格式持有密文、旧格式已清、probe 报 ENCRYPTED。
        val plain = FakeStore()
        val newStore = BoxBackedStore()
        val legacy = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-upgrade-user"))
        val composite = MigratingSecureStore(newStore, legacy)

        assertEquals("sk-upgrade-user", LlmKeyStore.readApiKey(plain, composite))
        assertEquals(
            LlmKeyStore.StorageMode.ENCRYPTED,
            LlmKeyStore.probeMode(plain, composite)
        )
        assertFalse(legacy.map.containsKey(LlmKeyStore.KEY_API))
        assertTrue(newStore.raw[LlmKeyStore.KEY_API]!!.startsWith("v1:"))
    }
}
