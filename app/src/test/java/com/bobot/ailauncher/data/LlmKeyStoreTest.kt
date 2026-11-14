package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.settings.llmCardCollapsedHint
import com.bobot.ailauncher.ui.settings.llmCardTitle
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

    // ---- probeMode（v0.68.100：存放形态探测，供设置页明示/降级记账）----

    @Test
    fun `probe reports encrypted when key lives in secure store`() {
        val plain = FakeStore()
        val secure = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-x"))
        assertEquals(
            LlmKeyStore.StorageMode.ENCRYPTED,
            LlmKeyStore.probeMode(plain, secure)
        )
    }

    @Test
    fun `probe reports plaintext fallback when secure store unavailable`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-x"))
        assertEquals(
            LlmKeyStore.StorageMode.PLAIN_FALLBACK,
            LlmKeyStore.probeMode(plain, null)
        )
    }

    @Test
    fun `probe reports plaintext fallback when key stranded in plain despite secure store`() {
        // 加密可用但 Key 还躺在明文里（写入回退过）——同样是降级，必须明示
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-x"))
        val secure = FakeStore()
        assertEquals(
            LlmKeyStore.StorageMode.PLAIN_FALLBACK,
            LlmKeyStore.probeMode(plain, secure)
        )
    }

    @Test
    fun `probe reports plaintext fallback when secure store read throws`() {
        val plain = FakeStore(mapOf(LlmKeyStore.KEY_API to "sk-x"))
        val brokenSecure = FakeStore(failRead = true)
        assertEquals(
            LlmKeyStore.StorageMode.PLAIN_FALLBACK,
            LlmKeyStore.probeMode(plain, brokenSecure)
        )
    }

    @Test
    fun `probe reports empty when no key anywhere`() {
        assertEquals(
            LlmKeyStore.StorageMode.EMPTY,
            LlmKeyStore.probeMode(FakeStore(), FakeStore())
        )
        assertEquals(
            LlmKeyStore.StorageMode.EMPTY,
            LlmKeyStore.probeMode(FakeStore(), null)
        )
        // 加密层坏了且明文也没有 Key：无 Key 可丢，报 EMPTY 而非降级
        assertEquals(
            LlmKeyStore.StorageMode.EMPTY,
            LlmKeyStore.probeMode(FakeStore(), FakeStore(failRead = true))
        )
    }
}

/**
 * 「让助手更聪明」卡收起行文案（2026-11-14 Bob 定：与来源卡同一口径，收起
 * 只留一行 + chevron；说明文字只在展开态出现，见 SettingsScreen 卡内
 * `if (llmExpanded)` 块——Compose 分支无 JVM 测试 harness，此处锁死驱动
 * 它的纯函数 llmCardTitle）。
 */
class LlmCardFoldTest {

    // 收起 + 没填 Key → 卡名

    @Test
    fun collapsedWithoutKeyShowsCardName() {
        assertEquals("让助手更聪明", llmCardTitle(expanded = false, apiKeyBlank = true))
    }

    // 收起 + 已填 Key → 状态句

    @Test
    fun collapsedWithKeyShowsConfiguredState() {
        assertEquals("大模型已经配好了", llmCardTitle(expanded = false, apiKeyBlank = false))
    }

    // 展开态标题恒为卡名（有无 Key 都一样）

    @Test
    fun expandedAlwaysShowsCardName() {
        assertEquals("让助手更聪明", llmCardTitle(expanded = true, apiKeyBlank = true))
        assertEquals("让助手更聪明", llmCardTitle(expanded = true, apiKeyBlank = false))
    }

    // 收起行描述（2026-11-14 Bob 定：收起加一行，告诉用户配好大模型会起
    // 什么作用）：没填 Key 写配好之后的效果，填了写已经配好的效果

    @Test
    fun collapsedHintWithoutKeyStatesEffectAfterSetup() {
        assertEquals("配好之后，助手能听懂你说话、帮你办事。", llmCardCollapsedHint(apiKeyBlank = true))
    }

    @Test
    fun collapsedHintWithKeyStatesEffectNow() {
        assertEquals("配好了，助手能听懂你说话、帮你办事。", llmCardCollapsedHint(apiKeyBlank = false))
    }
}
