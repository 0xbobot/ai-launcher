package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.ui.theme.PetMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 扇形淡化（Bob 2026-11-14 10:43）：扇形唤起时桌面所有元素淡化，智能卡片
 * 与杂志 hero 与 Dock 同口径——同信号 blindActive（Selecting || Editing）、
 * 同目标透明度 0.3、同 180ms tween（MainScreen.kt v0.68.32 Dock 实现）。
 * 锁的是目标值与动画口径，不测 Compose 动画本身。
 */
class FanDimTest {

    @Test
    fun `fan open dims to Dock value 0_3`() {
        assertEquals(0.3f, fanDimTargetAlpha(true), 0f)
    }

    @Test
    fun `fan closed restores to 1`() {
        assertEquals(1f, fanDimTargetAlpha(false), 0f)
    }

    @Test
    fun `animation spec is the Dock handoff duration 180ms`() {
        assertEquals(180, PetMotion.FadeHandoff)
    }
}
