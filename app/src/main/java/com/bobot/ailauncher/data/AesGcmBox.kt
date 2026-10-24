package com.bobot.ailauncher.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * AES-256/GCM 密封盒（纯 JVM 密码学逻辑，可单测）：把 API Key 这类小秘密
 * 加密成可放进普通 SharedPreferences 的字符串，取代骑在
 * androidx.security:security-crypto alpha06 之上的 EncryptedSharedPreferences
 * （谷歌多年未更新的 alpha 库；旧数据的一次性迁移见 [MigratingSecureStore]）。
 *
 * 存储格式："v1:" + Base64( IV(12B) || 密文 || GCM tag(16B) )。
 * AAD 绑定条目名——密文被搬到别的条目下解不开；IV/密文/tag 任一处被篡改，
 * GCM 校验都会抛 AEADBadTagException，绝不静默返回错误明文。
 *
 * IV 来源是硬约束：加密侧必须让 Cipher 自生成、再从 `cipher.iv` 读回存盘，
 * 不许调用方经 GCMParameterSpec 自带——Android Keystore 密钥默认
 * RandomizedEncryptionRequired=true，加密 init 带调用方 IV 会抛
 * InvalidAlgorithmParameterException（迁移写新格式实测中招，§25 冒烟
 * ks-evidence 定因；JVM 软件 provider 不执行此规则，单测照不出）。
 * 解密侧自带 IV 不受此限，仍按存盘 IV 构造 GCMParameterSpec。
 *
 * 密钥由 [keyProvider] 提供（生产 = [AndroidKeystoreKeys]；单测 = 固定
 * SecretKeySpec）。provider 返回 null 或 Cipher 使用时抛异常（密钥被系统
 * 作废等）都按「加密层不可用」原样上抛——与旧 EncryptedSharedPreferences
 * 解密失败抛异常的语义一致，[LlmKeyStore] 的回退/明示契约不需要改。
 */
class AesGcmBox(private val keyProvider: () -> SecretKey?) {

    /** 加密；密钥不可用时抛 IllegalStateException（或 Cipher 的原生异常） */
    fun encrypt(plaintext: String, aad: String): String {
        val key = keyProvider()
            ?: throw IllegalStateException("keystore key unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // 不带参数 init：IV 由 Cipher 生成（见类头注，Keystore 随机化硬约束）
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "unexpected gcm iv size: ${iv.size}" }
        return PREFIX + Base64.getEncoder().encodeToString(iv + ct)
    }

    /**
     * 解密；blob 不是 v1 格式、密钥不可用、密文被篡改一律抛异常
     * （调用方以「加密层读失败」处理，不存在「解出空/错值」的静默路径）。
     */
    fun decrypt(blob: String, aad: String): String {
        if (!blob.startsWith(PREFIX)) {
            throw IllegalStateException("not a v1 keystore blob")
        }
        val key = keyProvider()
            ?: throw IllegalStateException("keystore key unavailable")
        val raw = Base64.getDecoder().decode(blob.substring(PREFIX.length))
        require(raw.size > IV_BYTES) { "keystore blob too short" }
        val iv = raw.copyOfRange(0, IV_BYTES)
        val ct = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "v1:"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * 生产密钥提供者：Android Keystore 里的 AES-256 密钥（别名固定，密钥不出
 * Keystore、不可导出）。取不到且生成也失败（设备 Keystore 异常）返回 null，
 * 上层按加密层不可用处理（回退明文 + 设置页明示，v0.68.100 契约）。
 */
object AndroidKeystoreKeys {
    private const val ALIAS = "ailauncher_llm_aes_v1"
    private const val STORE = "AndroidKeyStore"

    @Volatile
    private var cached: SecretKey? = null

    fun loadOrCreate(): SecretKey? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                val ks = KeyStore.getInstance(STORE).apply { load(null) }
                (ks.getKey(ALIAS, null) as? SecretKey) ?: generate()
            }.getOrNull()?.also { cached = it }
        }
    }

    private fun generate(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                // 不关 RandomizedEncryptionRequired（默认 true，更严）：
                // 加密侧 IV 由 Cipher 生成与之兼容，见 AesGcmBox 类头注。
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}

/**
 * [LlmKeyStore.Store] 适配：值经 [AesGcmBox] 加密后存普通 SharedPreferences
 * （文件名 llm_keystore）。读不到条目返回 null；条目在但解密失败抛异常
 * （与 EncryptedSharedPreferences 的失败语义对齐，[LlmKeyStore] 不用改）。
 *
 * 写/删一律 commit() 同步落盘：本存储是迁移的终态与 Key 的唯一现行副本，
 * 「写成功」的语义必须包含「进程此刻被杀也不丢」。apply() 只保证内存
 * 更新 + 异步刷盘，刷盘完成前进程被杀（force-stop/崩溃/低内存回收）
 * 写入即蒸发——而上层 [LlmKeyStore] 的回读核对只看内存，会误报成功
 * （§25 冒烟取证：迁移后 1 秒内 cat 不到文件、force-stop 后新格式为空）。
 * commit() 返回 false（磁盘写失败）直接上抛，让迁移按失败处理、保留
 * 旧格式副本，绝不出现「以为存好了其实没落盘」。
 */
class KeystoreCipherStore(
    private val prefs: SharedPreferences,
    private val box: AesGcmBox
) : LlmKeyStore.Store {
    override fun read(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return box.decrypt(blob, key)
    }

    override fun write(key: String, value: String) {
        val blob = box.encrypt(value, key)
        if (!prefs.edit().putString(key, blob).commit()) {
            throw IllegalStateException("keystore prefs commit failed")
        }
    }

    override fun delete(key: String) {
        if (!prefs.edit().remove(key).commit()) {
            throw IllegalStateException("keystore prefs commit failed")
        }
    }
}
