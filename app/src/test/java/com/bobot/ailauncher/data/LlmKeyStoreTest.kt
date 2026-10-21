package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmKeyStoreTest {

    /** 内存假存储；可指定某些操作抛异常，模拟加密层失败 */
    private class FakeStore(
        initial: Map<String, String> = emptyMap(),
        private val failRead: Boolean = false,
        private val failWrite: Boolean = false
    ) : LlmKeyStore.Store {
        val map = initial.toMutableMap()
        override fun read(key: String): String? {
            if (failRead) throw RuntimeException("decrypt failed")
            return map[key]
        }
        override fun write(key: String, value: String) {
            if (failWrite) throw RuntimeException("encrypt failed")
            map[key] = value
        }
        override fun delete(key: String) {
            map.remove(key)
        }
    }

    @Test
    fun `migrates legacy plaintext key into secure store and deletes plaintext`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-legacy"))
        val secure = FakeStore()
        assertEquals("sk-legacy", LlmKeyStore.readApiKey(plain, secure))
        assertEquals("sk-legacy", secure.map[LlmKeyStore.KEY_API])
        assertFalse(plain.map.containsKey(LlmKeyStore.KEY_API))
    }

    @Test
    fun `secure value wins and leftover plaintext is cleaned`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-old"))
        val secure = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-new"))
        assertEquals("sk-new", LlmKeyStore.readApiKey(plain, secure))
        assertFalse(plain.map.containsKey(LlmKeyStore.KEY_API))
    }

    @Test
    fun `secure unavailable falls back to plaintext without migration`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-plain"))
        assertEquals("sk-plain", LlmKeyStore.readApiKey(plain, null))
        assertEquals("sk-plain", plain.map[LlmKeyStore.KEY_API])
    }

    @Test
    fun `failed migration keeps plaintext key for next attempt`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-legacy"))
        val brokenSecure = FakeStore(failWrite = true)
        assertEquals("sk-legacy", LlmKeyStore.readApiKey(plain, brokenSecure))
        // 写入加密失败：明文必须原样保留，Key 不丢
        assertEquals("sk-legacy", plain.map[LlmKeyStore.KEY_API])
    }

    @Test
    fun `secure read failure falls back to plaintext once`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-legacy"))
        val brokenSecure = FakeStore(failRead = true)
        assertEquals("sk-legacy", LlmKeyStore.readApiKey(plain, brokenSecure))
        assertEquals("sk-legacy", plain.map[LlmKeyStore.KEY_API])
    }

    @Test
    fun `empty everywhere returns empty`() {
        assertEquals("", LlmKeyStore.readApiKey(FakeStore(), FakeStore()))
        assertEquals("", LlmKeyStore.readApiKey(FakeStore(), null))
    }

    @Test
    fun `write goes to secure store and clears plaintext`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-old"))
        val secure = FakeStore()
        LlmKeyStore.writeApiKey(plain, secure, "sk-fresh")
        assertEquals("sk-fresh", secure.map[LlmKeyStore.KEY_API])
        assertFalse(plain.map.containsKey(LlmKeyStore.KEY_API))
    }

    @Test
    fun `write falls back to plaintext when secure store fails`() {
        val plain = FakeStore()
        val brokenSecure = FakeStore(failWrite = true)
        LlmKeyStore.writeApiKey(plain, brokenSecure, "sk-fresh")
        assertEquals("sk-fresh", plain.map[LlmKeyStore.KEY_API])
    }

    @Test
    fun `write with no secure store writes plaintext`() {
        val plain = FakeStore()
        LlmKeyStore.writeApiKey(plain, null, "sk-fresh")
        assertTrue(plain.map[LlmKeyStore.KEY_API] == "sk-fresh")
    }
}
