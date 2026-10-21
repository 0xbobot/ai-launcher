package com.bobot.ailauncher.data

/**
 * API Key 的明文到加密迁移纯逻辑（存储以接口抽象，JVM 可测）。
 *
 * 背景：v0.68.89 前 api_key 明文存 SharedPreferences "llm"。升级后 Key 只存
 * EncryptedSharedPreferences，但用户已存的 Key 不能因为升级消失：
 * - 加密存储已有值：用它，并顺手清掉可能残留的明文副本；
 * - 加密存储无值、明文有值：迁移（写入加密、回读核对一致后才删明文），
 *   本次仍返回明文值；任何一步失败都保留明文，下次启动再迁；
 * - 加密不可用（secure == null，初始化/解密失败）：回退读明文，保可用性。
 * 迁移与解密的异常一律吞掉并回退，Key 绝不因升级丢失。
 */
object LlmKeyStore {
    const val KEY_API = "api_key"

    /** 存储抽象：生产环境由 SharedPreferences 适配，单测用内存假实现 */
    interface Store {
        fun read(key: String): String?
        fun write(key: String, value: String)
        fun delete(key: String)
    }

    /** 读取 API Key（必要时先做明文到加密的迁移） */
    fun readApiKey(plain: Store, secure: Store?): String {
        if (secure == null) return plain.read(KEY_API).orEmpty()
        val encrypted = runCatching { secure.read(KEY_API) }.getOrNull()
        if (!encrypted.isNullOrBlank()) {
            // 已迁完：清掉明文残留（失败无所谓，下次再清）
            if (runCatching { plain.read(KEY_API) }.getOrNull() != null) {
                runCatching { plain.delete(KEY_API) }
            }
            return encrypted
        }
        val legacy = runCatching { plain.read(KEY_API) }.getOrNull().orEmpty()
        if (legacy.isBlank()) return ""
        runCatching {
            secure.write(KEY_API, legacy)
            if (secure.read(KEY_API) == legacy) plain.delete(KEY_API)
        }
        return legacy
    }

    /** 保存 API Key：优先加密存储（成功后清明文），失败回退明文（保可用性、不丢 Key） */
    fun writeApiKey(plain: Store, secure: Store?, value: String) {
        if (secure != null) {
            val ok = runCatching {
                secure.write(KEY_API, value)
                secure.read(KEY_API) == value
            }.getOrDefault(false)
            if (ok) {
                runCatching { plain.delete(KEY_API) }
                return
            }
        }
        plain.write(KEY_API, value)
    }
}
