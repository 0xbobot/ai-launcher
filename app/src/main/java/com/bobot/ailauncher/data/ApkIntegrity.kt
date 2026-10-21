package com.bobot.ailauncher.data

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** APK 完整性校验（纯 JVM 逻辑，可单测） */
object ApkIntegrity {

    /** 计算文件 SHA-256（小写 hex）；读失败返回 null */
    fun sha256Hex(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    /**
     * 与 update.json 的 sha256 期望值比对：
     * - 期望为空（旧 manifest 没有这个字段）：跳过校验返回 true，防自锁；
     * - 期望非空：算得出且完全一致才 true，对不上/算不出都是 false。
     */
    fun matchesSha256(file: File, expected: String?): Boolean {
        val exp = expected?.trim()?.lowercase().orEmpty()
        if (exp.isEmpty()) return true
        val actual = sha256Hex(file) ?: return false
        return actual == exp
    }
}
