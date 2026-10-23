package com.bobot.ailauncher.ui.pet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.101（D-5）：入会防多发守卫——同一事件 2 秒内的重复触发只放行
 * 第一次；不同事件互不影响；窗口外重新放行。
 */
class JoinGuardTest {

    @Test
    fun `首次放行 - 窗口内同事件拦截`() {
        val g = JoinGuard()
        assertTrue(g.shouldFire("42|https://vc.example/j/1", 1_000L))
        assertFalse(g.shouldFire("42|https://vc.example/j/1", 1_500L))
        assertFalse(g.shouldFire("42|https://vc.example/j/1", 2_999L))
    }

    @Test
    fun `窗口外重新放行`() {
        val g = JoinGuard()
        assertTrue(g.shouldFire("42|", 1_000L))
        assertTrue(g.shouldFire("42|", 3_000L))
    }

    @Test
    fun `不同事件互不影响`() {
        val g = JoinGuard()
        assertTrue(g.shouldFire("42|a", 1_000L))
        assertTrue(g.shouldFire("43|a", 1_100L))
        assertTrue(g.shouldFire("42|b", 1_200L))
    }
}
