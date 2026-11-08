package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导换版重看单测（456/v1.2.9，设计稿 §4.7，Bob 2026-11-08 定）：
 * 引导内容带版本号，换版后自动重看一次——每版引导只出现一次，
 * 不是终身一次。纯函数、JVM 可跑。
 */
class GuideVersionTest {

    @Test
    fun guideVersion_currentIsV3() {
        // 当前引导内容是 v3（455 的五张按权限卡）；换版时只改这一个数。
        assertEquals(3, GUIDE_VERSION)
    }

    @Test
    fun legacySeenTrue_migratesToV2AndDoesNotBlockV3() {
        // 旧版已看（存量布尔 true，Bob 设备上 452 时记下的就是这个）
        // 不挡新版（当前 3）——要展示。
        val seen = migrateLegacyGuideSeen(legacySeen = true, storedVersion = null)
        assertEquals(LEGACY_GUIDE_SEEN_VERSION, seen)
        assertEquals(2, seen)
        assertTrue(shouldShowGuideVersion(seen))
    }

    @Test
    fun storedVersion2_doesNotBlockV3() {
        // 存的版本 2（旧版已看）不挡新版（当前 3）——要展示。
        assertTrue(shouldShowGuideVersion(2))
    }

    @Test
    fun noRecord_showsV3() {
        // 无记录的新用户直接看 v3。
        assertNull(migrateLegacyGuideSeen(legacySeen = false, storedVersion = null))
        assertTrue(shouldShowGuideVersion(null))
    }

    @Test
    fun storedVersionWinsOverLegacyBool() {
        // 整数在就以整数为准，旧布尔不再左右判断。
        assertEquals(3, migrateLegacyGuideSeen(legacySeen = true, storedVersion = 3))
        assertEquals(2, migrateLegacyGuideSeen(legacySeen = false, storedVersion = 2))
    }

    @Test
    fun newVersionSeen_doesNotShowAgain() {
        // 新版看完（展示列表走空后记当前版本 3），之后再进不再出现。
        assertFalse(shouldShowGuideVersion(3))
    }

    @Test
    fun sameVersion_doesNotRepeat() {
        // 同一版本不重复出现（已记 3 时不再展示）。
        assertFalse(shouldShowGuideVersion(GUIDE_VERSION))
    }

    @Test
    fun futureVersion_showsAgainOnce() {
        // 未来换版（常量改 4）会再出现一次：已看 3、当前 4，就展示。
        assertTrue(shouldShowGuideVersion(seenVersion = 3, currentVersion = 4))
        assertFalse(shouldShowGuideVersion(seenVersion = 4, currentVersion = 4))
    }

    @Test
    fun storedNewerThanCurrent_doesNotShow() {
        // 存的版本比当前新（降级回旧版），不展示、不回放。
        assertFalse(shouldShowGuideVersion(4))
    }
}
