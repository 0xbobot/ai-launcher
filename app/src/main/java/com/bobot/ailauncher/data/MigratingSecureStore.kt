package com.bobot.ailauncher.data

/**
 * 一次性迁移组合存储（并行线 B）：对外冒充 [LlmKeyStore] 眼里的「加密层」，
 * 内部 = 新格式（[KeystoreCipherStore]，Android Keystore AES/GCM）为主 +
 * 旧格式（security-crypto alpha06 的 EncryptedSharedPreferences）为迁移源。
 *
 * 读取顺序：新格式有值 → 用新值；新格式没有、旧格式有值 → 触发一次性迁移：
 * 写新格式 → 回读核对一致 → 才删旧条目 → 回调 [onMigrated]。迁移任何一步
 * 失败：旧数据原样保留、照样返回旧值（Key 绝不因迁移丢），回调
 * [onMigrationFailed]，下次读再试。迁移逻辑本身只依赖 [LlmKeyStore.Store]
 * 接口，JVM 可测（假存储 + 真 [AesGcmBox]）。
 *
 * 写入只走新格式（失败原样上抛，[LlmKeyStore.writeApiKey] 按既有契约回退
 * 明文 + 设置页明示）；写成功后顺手清旧条目，防止旧值残留被误读为现行 Key。
 * 删除两边都删。
 *
 * 本类与 [LlmConfig.securePrefs] 是 alpha06 依赖仅存的两个使用点：迁移随
 * 版本铺满后的**下一批**删除 legacy 分支与该依赖（本批保留，见回执后续项）。
 */
class MigratingSecureStore(
    private val newStore: LlmKeyStore.Store,
    private val legacyStore: LlmKeyStore.Store?,
    private val onMigrated: () -> Unit = {},
    private val onMigrationFailed: () -> Unit = {}
) : LlmKeyStore.Store {

    override fun read(key: String): String? {
        // 新格式读抛异常（密钥被作废等）按「新格式不可用」继续走旧格式兜底，
        // 与 LlmKeyStore 对 secure 读失败的既有处理口径一致。
        val fromNew = runCatching { newStore.read(key) }.getOrNull()
        if (!fromNew.isNullOrBlank()) return fromNew
        val legacy = legacyStore ?: return null
        val fromLegacy = runCatching { legacy.read(key) }.getOrNull() ?: return null
        if (fromLegacy.isBlank()) return fromLegacy
        val migrated = runCatching {
            newStore.write(key, fromLegacy)
            newStore.read(key) == fromLegacy
        }.getOrDefault(false)
        if (migrated) {
            runCatching { legacy.delete(key) }
            runCatching { onMigrated() }
        } else {
            runCatching { onMigrationFailed() }
        }
        return fromLegacy
    }

    override fun write(key: String, value: String) {
        newStore.write(key, value)
        runCatching { legacyStore?.delete(key) }
    }

    override fun delete(key: String) {
        runCatching { newStore.delete(key) }
        runCatching { legacyStore?.delete(key) }
    }
}
