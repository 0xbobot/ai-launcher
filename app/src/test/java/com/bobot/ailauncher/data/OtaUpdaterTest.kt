package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体检批D ⑥：OTA manifest 判定纯函数单测。
 * 覆盖版本比较（新/同/旧/缺失）、prerelease 门控、update.json 解析
 * （全字段 / sha256 缺省 / changelog 缺省文案）、坏输入兜底。
 * 网络与 SharedPreferences 读开关不在覆盖内（外层 checkForUpdateResult）。
 */
class OtaUpdaterTest {

    private fun manifest(
        code: Int? = 300,
        name: String = "0.68.92",
        url: String? = "https://example.com/app.apk",
        changelog: String? = "修复若干问题",
        force: Boolean = false,
        pre: Boolean = false,
        sha: String? = "ab12cd"
    ): String {
        val sb = StringBuilder("{")
        if (code != null) sb.append("\"versionCode\": $code,")
        sb.append("\"versionName\": \"$name\",")
        if (url != null) sb.append("\"apkUrl\": \"$url\",")
        if (changelog != null) sb.append("\"changelog\": \"$changelog\",")
        sb.append("\"forceUpdate\": $force,")
        sb.append("\"prerelease\": $pre")
        if (sha != null) sb.append(",\"sha256\": \"$sha\"")
        sb.append("}")
        return sb.toString()
    }

    private fun available(body: String, current: Int = 299, beta: Boolean = false): OtaInfo {
        val r = OtaUpdater.evaluateManifest(body, current, beta)
        assertTrue("expected UpdateAvailable, got $r", r is OtaCheckResult.UpdateAvailable)
        return (r as OtaCheckResult.UpdateAvailable).info
    }

    @Test
    fun `newer version parses all fields`() {
        val info = available(manifest())
        assertEquals(300, info.versionCode)
        assertEquals("0.68.92", info.versionName)
        assertEquals("https://example.com/app.apk", info.apkUrl)
        assertEquals("修复若干问题", info.changelog)
        assertEquals(false, info.forceUpdate)
        assertEquals(false, info.prerelease)
        assertEquals("ab12cd", info.sha256)
    }

    @Test
    fun `sha256 defaults to empty when absent`() {
        // 旧 manifest 无 sha256 字段：解析为空串，下载端据此跳过校验
        val info = available(manifest(sha = null))
        assertEquals("", info.sha256)
    }

    @Test
    fun `blank changelog falls back to default text`() {
        assertEquals("修复了一些问题，体验更顺了", available(manifest(changelog = "")).changelog)
        assertEquals("修复了一些问题，体验更顺了", available(manifest(changelog = null)).changelog)
    }

    @Test
    fun `same version is up to date`() {
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(code = 299), 299, false)
        )
    }

    @Test
    fun `older version is up to date`() {
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(code = 250), 299, false)
        )
    }

    @Test
    fun `missing or zero version code is up to date`() {
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(code = null), 299, false)
        )
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(code = 0), 299, false)
        )
    }

    @Test
    fun `newer version without apk url fails`() {
        assertEquals(
            OtaCheckResult.Failed,
            OtaUpdater.evaluateManifest(manifest(url = null), 299, false)
        )
        assertEquals(
            OtaCheckResult.Failed,
            OtaUpdater.evaluateManifest(manifest(url = ""), 299, false)
        )
    }

    @Test
    fun `malformed json fails`() {
        assertEquals(OtaCheckResult.Failed, OtaUpdater.evaluateManifest("not json", 299, false))
        assertEquals(OtaCheckResult.Failed, OtaUpdater.evaluateManifest("", 299, false))
        assertEquals(OtaCheckResult.Failed, OtaUpdater.evaluateManifest("[1,2]", 299, false))
        assertEquals(OtaCheckResult.Failed, OtaUpdater.evaluateManifest("{broken", 299, false))
    }

    @Test
    fun `prerelease hidden from non beta users`() {
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(pre = true), 299, betaOptIn = false)
        )
    }

    @Test
    fun `prerelease offered to beta users`() {
        val info = available(manifest(pre = true), beta = true)
        assertTrue(info.prerelease)
    }

    @Test
    fun `force update flag parsed`() {
        assertTrue(available(manifest(force = true)).forceUpdate)
    }

    @Test
    fun `version comparison uses remote code strictly greater`() {
        // 边界：恰好大 1 才算新版
        assertTrue(
            OtaUpdater.evaluateManifest(manifest(code = 300), 299, false)
                is OtaCheckResult.UpdateAvailable
        )
        assertEquals(
            OtaCheckResult.UpToDate,
            OtaUpdater.evaluateManifest(manifest(code = 300), 300, false)
        )
    }
}
