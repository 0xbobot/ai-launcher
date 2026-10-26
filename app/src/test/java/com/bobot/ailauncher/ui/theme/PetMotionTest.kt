package com.bobot.ailauncher.ui.theme

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 动效统一家族（2026-10-26 本批，Bob 21:56：动画节奏惯性都不一样，要修）
 * 参数锁定单测：改参数只改 PetMotion.kt，此处防漂移。
 * 统一不是同参数——不同质量的身体就该有不同的弹簧；锁的是“各处取自
 * 家族、值与批复一字不差”，不是“全改成同一套参数”。
 */
class PetMotionTest {

    @Test
    fun `spring family keeps approved parameters`() {
        // 头弹簧（活感）：k≈25、阻尼比 0.3，可见 2–3 次过冲。
        assertEquals(0.3f, PetMotion.HeadSoft.dampingRatio, 0f)
        assertEquals(25f, PetMotion.HeadSoft.stiffness, 0f)
        // 进出过渡（355 值不动）。
        assertEquals(0.75f, PetMotion.EnterStandard.dampingRatio, 0f)
        assertEquals(Spring.StiffnessMediumLow, PetMotion.EnterStandard.stiffness, 0f)
        // 拖拽松手（359 值不动，约 20% 过冲、约 0.9s 落定）。
        assertEquals(0.45f, PetMotion.DragRelease.dampingRatio, 0f)
        assertEquals(90f, PetMotion.DragRelease.stiffness, 0f)
        // 拖拽肩水平跟随（临界阻尼，约 90ms 达目标 63%；垂直方向已锁定，
        // 不再用弹簧）。
        assertEquals(1f, PetMotion.ShoulderLag.dampingRatio, 0f)
        assertEquals(550f, PetMotion.ShoulderLag.stiffness, 0f)
        // 垫高滑动（临界阻尼、无过冲；接替原 tween 250／150）。
        assertEquals(1f, PetMotion.PadStandard.dampingRatio, 0f)
        assertEquals(Spring.StiffnessMedium, PetMotion.PadStandard.stiffness, 0f)
        assertEquals(1f, PetMotion.PadFast.dampingRatio, 0f)
        assertEquals(3000f, PetMotion.PadFast.stiffness, 0f)
        // Dock 卡片进出滑入滑出（值不动）。
        assertEquals(0.86f, PetMotion.DockEnter.dampingRatio, 0f)
        assertEquals(Spring.StiffnessMediumLow, PetMotion.DockEnter.stiffness, 0f)
        assertEquals(0.9f, PetMotion.DockExit.dampingRatio, 0f)
        assertEquals(Spring.StiffnessMedium, PetMotion.DockExit.stiffness, 0f)
    }

    @Test
    fun `fade family keeps approved durations`() {
        assertEquals(250, PetMotion.FadeStandard)
        assertEquals(180, PetMotion.FadeHandoff)
        assertEquals(150, PetMotion.FadeQuick)
    }
}
