package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D1/D2 提拉换档纯逻辑（2026-10-27，Bob 07:50 确认）：
 * 阈值 48dp（手指净行程）、翻转（过阈值即切、拖回即翻回由进度
 * 函数保证）、morph 进度（上提 0→1、下压 1→0、反向不超调）。
 */
class LiftTierSwitchTest {

    @Test
    fun `D1 上提过阈值切到 D2`() {
        assertEquals(DockState.D2, resolveLiftTierTarget(DockState.D1, 48f, 0f, 48f))
        assertEquals(DockState.D2, resolveLiftTierTarget(DockState.D1, 200f, 0f, 48f))
    }

    @Test
    fun `D1 上提未过阈值留原档`() {
        assertNull(resolveLiftTierTarget(DockState.D1, 47.9f, 0f, 48f))
        assertNull(resolveLiftTierTarget(DockState.D1, 0f, 0f, 48f))
    }

    @Test
    fun `D1 再往下压不切换只拉伸`() {
        assertNull(resolveLiftTierTarget(DockState.D1, 0f, 100f, 48f))
    }

    @Test
    fun `D2 下压过阈值切到 D1`() {
        assertEquals(DockState.D1, resolveLiftTierTarget(DockState.D2, 0f, 48f, 48f))
        assertEquals(DockState.D1, resolveLiftTierTarget(DockState.D2, 0f, 200f, 48f))
    }

    @Test
    fun `D2 下压未过阈值留原档`() {
        assertNull(resolveLiftTierTarget(DockState.D2, 0f, 47.9f, 48f))
        assertNull(resolveLiftTierTarget(DockState.D2, 0f, 0f, 48f))
    }

    @Test
    fun `D2 再往上提不切换只拉伸`() {
        assertNull(resolveLiftTierTarget(DockState.D2, 100f, 0f, 48f))
    }

    @Test
    fun `D3 与 Hidden 不换档`() {
        assertNull(resolveLiftTierTarget(DockState.D3, 100f, 0f, 48f))
        assertNull(resolveLiftTierTarget(DockState.Hidden, 0f, 100f, 48f))
    }

    @Test
    fun `D1 进度随上提连续走`() {
        assertEquals(0f, liftMorphProgress(DockState.D1, 0f, 0f, 48f), 0.001f)
        assertEquals(0.5f, liftMorphProgress(DockState.D1, 24f, 0f, 48f), 0.001f)
        assertEquals(1f, liftMorphProgress(DockState.D1, 48f, 0f, 48f), 0.001f)
        assertEquals(1f, liftMorphProgress(DockState.D1, 200f, 0f, 48f), 0.001f)
    }

    @Test
    fun `D1 进度不理下压`() {
        assertEquals(0f, liftMorphProgress(DockState.D1, 0f, 60f, 48f), 0.001f)
    }

    @Test
    fun `D2 进度随下压连续走`() {
        assertEquals(1f, liftMorphProgress(DockState.D2, 0f, 0f, 48f), 0.001f)
        assertEquals(0.5f, liftMorphProgress(DockState.D2, 0f, 24f, 48f), 0.001f)
        assertEquals(0f, liftMorphProgress(DockState.D2, 0f, 48f, 48f), 0.001f)
        assertEquals(0f, liftMorphProgress(DockState.D2, 0f, 200f, 48f), 0.001f)
    }

    @Test
    fun `D2 进度不理上提`() {
        assertEquals(1f, liftMorphProgress(DockState.D2, 60f, 0f, 48f), 0.001f)
    }

    @Test
    fun `静止态进度`() {
        assertEquals(0f, liftRestProgress(DockState.D1), 0.001f)
        assertEquals(1f, liftRestProgress(DockState.D2), 0.001f)
        assertEquals(0f, liftRestProgress(DockState.D3), 0.001f)
    }
}
