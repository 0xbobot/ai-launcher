package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class AesGcmBoxTest {

    // 固定测试密钥（假 Key 材料，仅单测用）：32 字节全 0x2A
    private val fixedKey: SecretKey =
        SecretKeySpec(ByteArray(32) { 0x2A }, "AES")
    private val otherKey: SecretKey =
        SecretKeySpec(ByteArray(32) { 0x11 }, "AES")

    private fun box(key: SecretKey? = fixedKey) = AesGcmBox { key }

    @Test
    fun `encrypt decrypt round trip`() {
        val b = box()
        val blob = b.encrypt("sk-test-123", "api_key")
        assertTrue(blob.startsWith("v1:"))
        assertEquals("sk-test-123", b.decrypt(blob, "api_key"))
    }

    @Test
    fun `stored blob never contains the plaintext`() {
        val blob = box().encrypt("sk-visible-check", "api_key")
        assertTrue(!blob.contains("sk-visible-check"))
    }

    @Test
    fun `each encrypt uses a fresh iv so blobs differ but both decrypt`() {
        val b = box()
        val a = b.encrypt("sk-same", "api_key")
        val c = b.encrypt("sk-same", "api_key")
        assertNotEquals(a, c)
        assertEquals("sk-same", b.decrypt(a, "api_key"))
        assertEquals("sk-same", b.decrypt(c, "api_key"))
    }

    @Test
    fun `blob layout interoperates with a raw cipher that generated its own iv`() {
        // 按「无参 init、IV 由 Cipher 生成、从 cipher.iv 读回」手工造 v1 blob，
        // box 必须能解；反方向 box 的 blob 裸 Cipher 按 IV(12B) 前缀切分也必须
        // 能解——锁住 v1 格式与「IV 来自 Cipher」构造的互操作。
        // 覆盖边界：JVM 软件 provider 不执行 Keystore 的
        // RandomizedEncryptionRequired 规则（加密侧带调用方 IV 也不会抛
        // InvalidAlgorithmParameterException），本测只能锁格式与布局；
        // Keystore 规则本身只能靠 §25 冒烟（模拟器真 Keystore）网住。
        val raw = Cipher.getInstance("AES/GCM/NoPadding")
        raw.init(Cipher.ENCRYPT_MODE, fixedKey)
        raw.updateAAD("api_key".toByteArray(Charsets.UTF_8))
        val ct = raw.doFinal("sk-iv-source".toByteArray(Charsets.UTF_8))
        assertEquals(12, raw.iv.size)
        val manualBlob = "v1:" + Base64.getEncoder().encodeToString(raw.iv + ct)
        assertEquals("sk-iv-source", box().decrypt(manualBlob, "api_key"))

        val blob = box().encrypt("sk-iv-source", "api_key")
        val bytes = Base64.getDecoder().decode(blob.removePrefix("v1:"))
        val back = Cipher.getInstance("AES/GCM/NoPadding")
        back.init(
            Cipher.DECRYPT_MODE, fixedKey,
            javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12))
        )
        back.updateAAD("api_key".toByteArray(Charsets.UTF_8))
        assertEquals(
            "sk-iv-source",
            String(back.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        )
    }

    @Test
    fun `tampered ciphertext fails gcm verification instead of returning wrong plaintext`() {
        val b = box()
        val blob = b.encrypt("sk-tamper", "api_key")
        // 翻转 Base64 中段一个字符（落在密文区，保证解码字节真的变了，
        // 失败必须来自 GCM tag 校验而不是 Base64 格式错误）
        val idx = 3 + 20
        val repl = if (blob[idx] == 'A') 'B' else 'A'
        val flipped = blob.substring(0, idx) + repl + blob.substring(idx + 1)
        assertThrows(Exception::class.java) { b.decrypt(flipped, "api_key") }
    }

    @Test
    fun `tampered iv fails decryption`() {
        val b = box()
        val blob = b.encrypt("sk-tamper-iv", "api_key")
        // v1: 之后第 2 个字符落在 IV 区
        val idx = 4
        val repl = if (blob[idx] == 'A') 'B' else 'A'
        val flipped = blob.substring(0, idx) + repl + blob.substring(idx + 1)
        assertThrows(Exception::class.java) { b.decrypt(flipped, "api_key") }
    }

    @Test
    fun `aad binds the blob to its entry name`() {
        val b = box()
        val blob = b.encrypt("sk-aad", "api_key")
        assertThrows(Exception::class.java) { b.decrypt(blob, "other_entry") }
    }

    @Test
    fun `wrong key cannot decrypt`() {
        val blob = box(fixedKey).encrypt("sk-key", "api_key")
        assertThrows(Exception::class.java) { box(otherKey).decrypt(blob, "api_key") }
    }

    @Test
    fun `foreign blob without v1 prefix is rejected`() {
        assertThrows(Exception::class.java) {
            box().decrypt("aGVsbG8gd29ybGQ=", "api_key")
        }
    }

    @Test
    fun `unavailable key makes encrypt and decrypt throw, never silently succeed`() {
        val b = box(null)
        assertThrows(Exception::class.java) { b.encrypt("sk-x", "api_key") }
        val blob = box().encrypt("sk-x", "api_key")
        assertThrows(Exception::class.java) { b.decrypt(blob, "api_key") }
    }

    @Test
    fun `empty and unicode plaintext round trip`() {
        val b = box()
        assertEquals("", b.decrypt(b.encrypt("", "api_key"), "api_key"))
        assertEquals("sk-中文-🔑", b.decrypt(b.encrypt("sk-中文-🔑", "api_key"), "api_key"))
    }
}
