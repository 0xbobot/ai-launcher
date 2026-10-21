package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ApkIntegrityTest {

    private fun tempFileWith(content: ByteArray): File {
        val f = Files.createTempFile("apk-integrity", ".bin").toFile()
        f.writeBytes(content)
        f.deleteOnExit()
        return f
    }

    @Test
    fun `sha256 of known content`() {
        // "abc" 的 SHA-256 是公开已知的测试向量
        val f = tempFileWith("abc".toByteArray(Charsets.UTF_8))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkIntegrity.sha256Hex(f)
        )
    }

    @Test
    fun `sha256 of missing file is null`() {
        assertNull(ApkIntegrity.sha256Hex(File("/nonexistent/definitely-not-here.apk")))
    }

    @Test
    fun `matches when digest equal, case and whitespace insensitive`() {
        val f = tempFileWith("hello ota".toByteArray(Charsets.UTF_8))
        val digest = ApkIntegrity.sha256Hex(f)!!
        assertTrue(ApkIntegrity.matchesSha256(f, digest))
        assertTrue(ApkIntegrity.matchesSha256(f, "  ${digest.uppercase()} "))
    }

    @Test
    fun `mismatch when content differs`() {
        val f = tempFileWith("tampered".toByteArray(Charsets.UTF_8))
        val other = tempFileWith("original".toByteArray(Charsets.UTF_8))
        assertFalse(ApkIntegrity.matchesSha256(f, ApkIntegrity.sha256Hex(other)))
    }

    @Test
    fun `blank expected skips verification (legacy manifest compat)`() {
        val f = tempFileWith("anything".toByteArray(Charsets.UTF_8))
        assertTrue(ApkIntegrity.matchesSha256(f, ""))
        assertTrue(ApkIntegrity.matchesSha256(f, null))
        assertTrue(ApkIntegrity.matchesSha256(f, "   "))
    }

    @Test
    fun `missing file with expected digest fails`() {
        assertFalse(
            ApkIntegrity.matchesSha256(
                File("/nonexistent/definitely-not-here.apk"),
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            )
        )
    }
}
