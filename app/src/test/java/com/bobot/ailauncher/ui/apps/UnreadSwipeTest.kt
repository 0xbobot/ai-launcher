package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未读组左滑清理纯逻辑单测（2026-11-08 应用中心崩溃 hotfix：
 * SwipeToDismissBox 换成 detectHorizontalDragGestures 后，位移钳制 /
 * 进度 / 触发判定是纯函数，锁死在这里）。
 *
 * 465（Bob 2026-11-08 19:29 左滑清理沿用字母组行收藏/隐藏浮层设计）：
 * 阈值与 AppRow 两段阈值对齐——56dp 现形、120dp 到底触发清理、
 * 最大位移 160dp；单测按新阈值改，三个常量值锁死在下面。
 */
class UnreadSwipeTest {

    // ---------- 阈值常量（与 AppRow 两段阈值对齐） ----------

    @Test
    fun `thresholds match AppRow two-stage design`() {
        assertEquals(56f, UNREAD_SWIPE_REVEAL_DP)
        assertEquals(120f, UNREAD_SWIPE_CLEAR_THRESHOLD_DP)
        assertEquals(160f, UNREAD_SWIPE_MAX_DP)
    }

    // ---------- 位移钳制 ----------

    @Test
    fun `coerceSwipeOffset - leftward drag stays`() {
        assertEquals(-50f, coerceSwipeOffset(-50f, 160f))
    }

    @Test
    fun `coerceSwipeOffset - rightward drag is clamped to zero`() {
        assertEquals(0f, coerceSwipeOffset(30f, 160f))
        assertEquals(0f, coerceSwipeOffset(0f, 160f))
    }

    @Test
    fun `coerceSwipeOffset - beyond max swipe is clamped`() {
        assertEquals(-160f, coerceSwipeOffset(-200f, 160f))
        assertEquals(-160f, coerceSwipeOffset(-160f, 160f))
    }

    // ---------- 清理进度（现形阈值 56dp：蒙层/内容弱化进度） ----------

    @Test
    fun `swipeClearProgress - zero at rest, one at threshold`() {
        assertEquals(0f, swipeClearProgress(0f, 56f))
        assertEquals(0.5f, swipeClearProgress(-28f, 56f))
        assertEquals(1f, swipeClearProgress(-56f, 56f))
    }

    @Test
    fun `swipeClearProgress - beyond threshold stays at one`() {
        assertEquals(1f, swipeClearProgress(-200f, 56f))
    }

    @Test
    fun `swipeClearProgress - rightward offset stays at zero`() {
        assertEquals(0f, swipeClearProgress(20f, 56f))
    }

    // ---------- 松手触发判定（触发阈值 120dp） ----------

    @Test
    fun `shouldClearOnRelease - past threshold clears`() {
        assertTrue(shouldClearOnRelease(-121f, 120f))
        assertTrue(shouldClearOnRelease(-200f, 120f))
    }

    @Test
    fun `shouldClearOnRelease - at or before threshold does not clear`() {
        assertFalse(shouldClearOnRelease(-120f, 120f))
        assertFalse(shouldClearOnRelease(-10f, 120f))
        assertFalse(shouldClearOnRelease(0f, 120f))
    }

    // ---------- 右滑关闭通知（Bob 2026-11-14 09:55 亲定右滑） ----------
    // 与左滑同款阈值：56dp 现形、120dp 触发、160dp 最大位移。

    @Test
    fun `coerceRightSwipeOffset - rightward drag stays`() {
        assertEquals(50f, coerceRightSwipeOffset(50f, 160f))
    }

    @Test
    fun `coerceRightSwipeOffset - leftward drag is clamped to zero`() {
        assertEquals(0f, coerceRightSwipeOffset(-30f, 160f))
        assertEquals(0f, coerceRightSwipeOffset(0f, 160f))
    }

    @Test
    fun `coerceRightSwipeOffset - beyond max swipe is clamped`() {
        assertEquals(160f, coerceRightSwipeOffset(200f, 160f))
        assertEquals(160f, coerceRightSwipeOffset(160f, 160f))
    }

    @Test
    fun `swipeDisableProgress - zero at rest, one at threshold`() {
        assertEquals(0f, swipeDisableProgress(0f, 56f))
        assertEquals(0.5f, swipeDisableProgress(28f, 56f))
        assertEquals(1f, swipeDisableProgress(56f, 56f))
    }

    @Test
    fun `swipeDisableProgress - beyond threshold stays at one`() {
        assertEquals(1f, swipeDisableProgress(200f, 56f))
    }

    @Test
    fun `swipeDisableProgress - leftward offset stays at zero`() {
        assertEquals(0f, swipeDisableProgress(-20f, 56f))
    }

    @Test
    fun `shouldDisableOnRelease - past threshold disables`() {
        assertTrue(shouldDisableOnRelease(121f, 120f))
        assertTrue(shouldDisableOnRelease(200f, 120f))
    }

    @Test
    fun `shouldDisableOnRelease - at or before threshold does not disable`() {
        assertFalse(shouldDisableOnRelease(120f, 120f))
        assertFalse(shouldDisableOnRelease(10f, 120f))
        assertFalse(shouldDisableOnRelease(0f, 120f))
        assertFalse(shouldDisableOnRelease(-200f, 120f))
    }

    // ---------- 系统通知设置页打开目标 ----------
    // action 与 extra key 是 Settings 的编译期常量（纯字符串，JVM 单测可直接读）。

    @Test
    fun `notificationSettingsTarget - action is app notification settings`() {
        val target = notificationSettingsTarget("com.tencent.mm")
        assertEquals(
            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            target.action
        )
    }

    @Test
    fun `notificationSettingsTarget - extra key is app package key`() {
        val target = notificationSettingsTarget("com.tencent.mm")
        assertEquals(
            android.provider.Settings.EXTRA_APP_PACKAGE,
            target.packageExtraKey
        )
    }

    @Test
    fun `notificationSettingsTarget - package passes through`() {
        assertEquals(
            "com.tencent.mm",
            notificationSettingsTarget("com.tencent.mm").packageName
        )
    }
}
