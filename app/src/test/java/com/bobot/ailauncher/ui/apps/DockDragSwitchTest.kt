package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D1/D2 拖拽切换纯逻辑（2026-10-27，拖拽代滑动批）：
 * 方向（左＝多、右＝少）、阈值（48dp 沿用旧滑动阈值）、
 * 跟手（阻尼 0.8、钳制 ±32dp、仅可切换方向）。
 */
class DockDragSwitchTest {

    @Test
    fun `D1 左拖过阈值确认到 D2`() {
        assertEquals(DockState.D2, resolveDragTierTarget(DockState.D1, -60f, 48f))
    }

    @Test
    fun `D2 右拖过阈值确认到 D1`() {
        assertEquals(DockState.D1, resolveDragTierTarget(DockState.D2, 60f, 48f))
    }

    @Test
    fun `低于阈值不切换`() {
        assertNull(resolveDragTierTarget(DockState.D1, -47f, 48f))
        assertNull(resolveDragTierTarget(DockState.D2, 47f, 48f))
    }

    @Test
    fun `恰达阈值确认切换`() {
        assertEquals(DockState.D2, resolveDragTierTarget(DockState.D1, -48f, 48f))
        assertEquals(DockState.D1, resolveDragTierTarget(DockState.D2, 48f, 48f))
    }

    @Test
    fun `D2 左拖与 D1 右拖不归拖拽管`() {
        // D2 左拖 → D3、D1 右拖 → 隐藏确认，仍走旧中途触发，拖拽返回 null。
        assertNull(resolveDragTierTarget(DockState.D2, -100f, 48f))
        assertNull(resolveDragTierTarget(DockState.D1, 100f, 48f))
    }

    @Test
    fun `D3 与隐藏态拖拽无目标`() {
        assertNull(resolveDragTierTarget(DockState.D3, -100f, 48f))
        assertNull(resolveDragTierTarget(DockState.Hidden, 100f, 48f))
    }

    @Test
    fun `可切换方向跟手阻尼与钳制`() {
        // D1 左拖 20px → 0.8 阻尼＝-16px；拖 100px → 钳制 -32px。
        assertEquals(-16f, dragTierFollowPx(DockState.D1, -20f, 32f), 0.001f)
        assertEquals(-32f, dragTierFollowPx(DockState.D1, -100f, 32f), 0.001f)
        assertEquals(16f, dragTierFollowPx(DockState.D2, 20f, 32f), 0.001f)
        assertEquals(32f, dragTierFollowPx(DockState.D2, 100f, 32f), 0.001f)
    }

    @Test
    fun `不可切换方向跟手为零`() {
        assertEquals(0f, dragTierFollowPx(DockState.D2, -100f, 32f), 0.001f)
        assertEquals(0f, dragTierFollowPx(DockState.D1, 100f, 32f), 0.001f)
        assertEquals(0f, dragTierFollowPx(DockState.D3, -100f, 32f), 0.001f)
    }
}
