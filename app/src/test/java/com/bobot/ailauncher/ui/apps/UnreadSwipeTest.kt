package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未读组左滑清理纯逻辑单测（2026-11-08 应用中心崩溃 hotfix：
 * SwipeToDismissBox 换成 detectHorizontalDragGestures 后，位移钳制 /
 * 进度 / 触发判定是纯函数，锁死在这里）。
 */
class UnreadSwipeTest {

    // ---------- 位移钳制 ----------

    @Test
    fun `coerceSwipeOffset - leftward drag stays`() {
        assertEquals(-50f, coerceSwipeOffset(-50f, 120f))
    }

    @Test
    fun `coerceSwipeOffset - rightward drag is clamped to zero`() {
        assertEquals(0f, coerceSwipeOffset(30f, 120f))
        assertEquals(0f, coerceSwipeOffset(0f, 120f))
    }

    @Test
    fun `coerceSwipeOffset - beyond max swipe is clamped`() {
        assertEquals(-120f, coerceSwipeOffset(-200f, 120f))
        assertEquals(-120f, coerceSwipeOffset(-120f, 120f))
    }

    // ---------- 清理进度 ----------

    @Test
    fun `swipeClearProgress - zero at rest, one at threshold`() {
        assertEquals(0f, swipeClearProgress(0f, 72f))
        assertEquals(0.5f, swipeClearProgress(-36f, 72f))
        assertEquals(1f, swipeClearProgress(-72f, 72f))
    }

    @Test
    fun `swipeClearProgress - beyond threshold stays at one`() {
        assertEquals(1f, swipeClearProgress(-200f, 72f))
    }

    @Test
    fun `swipeClearProgress - rightward offset stays at zero`() {
        assertEquals(0f, swipeClearProgress(20f, 72f))
    }

    // ---------- 松手触发判定 ----------

    @Test
    fun `shouldClearOnRelease - past threshold clears`() {
        assertTrue(shouldClearOnRelease(-73f, 72f))
        assertTrue(shouldClearOnRelease(-200f, 72f))
    }

    @Test
    fun `shouldClearOnRelease - at or before threshold does not clear`() {
        assertFalse(shouldClearOnRelease(-72f, 72f))
        assertFalse(shouldClearOnRelease(-10f, 72f))
        assertFalse(shouldClearOnRelease(0f, 72f))
    }
}
