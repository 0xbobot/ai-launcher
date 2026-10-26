package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：Dock 卡片进出是否只做
 * 淡入淡出、不滑动。调用方传不同的信号：退出（离场）传当帧 intentInputMode，
 * 重进（回场）传上一帧 intentInputMode（SideEffect 滞后一帧）。
 */
class DockFocusAnimTest {

    @Test
    fun `横屏聚焦切换时 fade-only`() {
        // 横屏＋聚焦切换信号为真 → 只淡入淡出、不滑动（头不移、场景整体换）。
        assertTrue(dockCardFocusSwapIsFadeOnly(true, true))
    }

    @Test
    fun `横屏非聚焦切换时沿用滑动加淡入淡出`() {
        // 横屏但非聚焦切换（D3 开关、状态回 D1/D2）→ 沿用斜向滑入／滑出。
        assertFalse(dockCardFocusSwapIsFadeOnly(true, false))
    }

    @Test
    fun `竖屏恒走原滑动加淡入淡出`() {
        // 竖屏一字不动：聚焦切换与否都不走 fade-only 路径。
        assertFalse(dockCardFocusSwapIsFadeOnly(false, true))
        assertFalse(dockCardFocusSwapIsFadeOnly(false, false))
    }
}
