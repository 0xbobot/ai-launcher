package com.bobot.ailauncher.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** shareFriendsText（分享给朋友，2026-11-18 Bob 定放关于区）：分享文本逐字锁死。 */
class ShareFriendsTextTest {

    @Test
    fun containsDownloadPageUrl() {
        // 分享的是下载页地址，逐字锁死
        assertTrue(shareFriendsText().contains("https://bobot.is-a.dev/ai-launcher/"))
    }

    @Test
    fun doesNotContainApkDirectLink() {
        // 不是 APK 直链：不含 .apk、不含 GitHub 直链、不含 releases 路径
        val text = shareFriendsText()
        assertFalse(text.contains(".apk"))
        assertFalse(text.contains("github.com"))
        assertFalse(text.contains("releases/download"))
        assertFalse(text.contains("releases/"))
    }

    @Test
    fun mentionsAiDesktop() {
        assertTrue(shareFriendsText().contains("AI 桌面"))
    }
}
