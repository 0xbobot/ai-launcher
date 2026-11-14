package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.settings.isVersionUpdateRowClickable
import com.bobot.ailauncher.ui.settings.versionUpdateHint
import com.bobot.ailauncher.ui.settings.versionUpdateTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本卡合并单测（2026-11-14 Bob 定：检查更新 + 加入测试计划合成一张卡，
 * 两行、中间一条细分隔线）。仓库没有 Compose UI 测试 harness，这里锁死
 * 驱动上行文案与点按的纯函数；行结构（上行点按只走更新、下行开关只走
 * 测试计划、下载中开关照常用）靠代码结构 + 真机验收。
 * 文案与原来检查更新卡逐字一致——合并只改排布，不改说法。
 */
class VersionCardTest {

    // 主文六态

    @Test
    fun `idle title is check update`() {
        assertEquals(
            "检查更新",
            versionUpdateTitle(
                checking = false, downloading = false,
                downloadDone = false, downloadFailed = false,
                updateVersionName = null
            )
        )
    }

    @Test
    fun `checking title`() {
        assertEquals(
            "正在检查...",
            versionUpdateTitle(
                checking = true, downloading = false,
                downloadDone = false, downloadFailed = false,
                updateVersionName = null
            )
        )
    }

    @Test
    fun `downloading title carries version`() {
        assertEquals(
            "正在下载 v1.3.54",
            versionUpdateTitle(
                checking = false, downloading = true,
                downloadDone = false, downloadFailed = false,
                updateVersionName = "1.3.54"
            )
        )
    }

    @Test
    fun `downloaded title`() {
        assertEquals(
            "下载完成",
            versionUpdateTitle(
                checking = false, downloading = false,
                downloadDone = true, downloadFailed = false,
                updateVersionName = "1.3.54"
            )
        )
    }

    @Test
    fun `failed title`() {
        assertEquals(
            "下载失败",
            versionUpdateTitle(
                checking = false, downloading = false,
                downloadDone = false, downloadFailed = true,
                updateVersionName = "1.3.54"
            )
        )
    }

    @Test
    fun `update available title carries version`() {
        assertEquals(
            "发现新版本 v1.3.54",
            versionUpdateTitle(
                checking = false, downloading = false,
                downloadDone = false, downloadFailed = false,
                updateVersionName = "1.3.54"
            )
        )
    }

    // 副文四态（下载中除外）

    @Test
    fun `idle hint is current version`() {
        assertEquals(
            "当前版本 v1.3.53",
            versionUpdateHint(
                downloading = false, downloadDone = false, downloadFailed = false,
                updateVersionName = null, changelog = null,
                currentVersionName = "1.3.53"
            )
        )
    }

    @Test
    fun `downloaded hint is tap to install`() {
        assertEquals(
            "点击安装 v1.3.54",
            versionUpdateHint(
                downloading = false, downloadDone = true, downloadFailed = false,
                updateVersionName = "1.3.54", changelog = "修复若干问题",
                currentVersionName = "1.3.53"
            )
        )
    }

    @Test
    fun `failed hint is tap to retry`() {
        assertEquals(
            "点击重试",
            versionUpdateHint(
                downloading = false, downloadDone = false, downloadFailed = true,
                updateVersionName = "1.3.54", changelog = "修复若干问题",
                currentVersionName = "1.3.53"
            )
        )
    }

    @Test
    fun `update available hint is changelog`() {
        assertEquals(
            "修复若干问题",
            versionUpdateHint(
                downloading = false, downloadDone = false, downloadFailed = false,
                updateVersionName = "1.3.54", changelog = "修复若干问题",
                currentVersionName = "1.3.53"
            )
        )
    }

    @Test
    fun `downloading hint is null progress bar instead`() {
        assertNull(
            versionUpdateHint(
                downloading = true, downloadDone = false, downloadFailed = false,
                updateVersionName = "1.3.54", changelog = "修复若干问题",
                currentVersionName = "1.3.53"
            )
        )
    }

    // 点按：检查中 / 下载中无反应，其余各态都有动作

    @Test
    fun `row not clickable while checking or downloading`() {
        assertFalse(isVersionUpdateRowClickable(checking = true, downloading = false))
        assertFalse(isVersionUpdateRowClickable(checking = false, downloading = true))
    }

    @Test
    fun `row clickable when idle`() {
        assertTrue(isVersionUpdateRowClickable(checking = false, downloading = false))
    }
}
