package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.ui.apps.dockPetVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 竖屏切换闪烁修复（2026-10-27，Bob 366 视频 verdict）：两场景重叠
 * 复影的三处根因各由一处纯函数兜底，本文件锁死它们的判定矩阵。
 * 组合动画本身（位移弹簧、淡入淡出、垫高弹簧）仍由 CI 构建＋代码级
 * 多腿复核，不在本文件伪造运行时断言。
 *
 *  ① 垫高跳变补偿 jumpCompensationShift：进出翻转时权重 Spacer
 *     出现／消失致整列瞬移，绘制位移先钉回旧位再滑向新位。
 *  ② Dock 宠物挂载门 dockPetVisible：退出 funnel 进行中 Dock 宠物
 *     不挂载，落地后才与意图宠物同位置溶解，不出现两只不同高度
 *     的宠物（70% 翻转点双影）。
 */
class EnterExitFlashTest {

    // ---- ① 跳变补偿量：符号＝滑回方向，进态为负、退态为正 ----

    @Test
    fun compensation_enterColumnMovesDown_shiftIsNegative() {
        // 进态：列从顶部锚定（top=100）瞬移到下部锚定（top=900），
        // 绘制位移先取 −800 把视觉钉回旧位，再滑回 0。
        assertEquals(-800f, jumpCompensationShift(100, 900, 0f), 0.001f)
    }

    @Test
    fun compensation_exitColumnMovesUp_shiftIsPositive() {
        // 退态：列从下部锚定（top=900）瞬移回顶部锚定（top=100），
        // 绘制位移先取 +800 把视觉钉回旧位，再滑回 0。
        assertEquals(800f, jumpCompensationShift(900, 100, 0f), 0.001f)
    }

    @Test
    fun compensation_noJump_shiftIsZero() {
        assertEquals(0f, jumpCompensationShift(300, 300, 0f), 0.001f)
    }

    @Test
    fun compensation_overlappingGlide_carriesResidualShift() {
        // 重叠补偿进行中（残值 −100，即视觉仍在旧位上方 100px 处
        // 滑回途中）又发生一次退态跳变（laid-out 500→100）：新位移
        // = (500−100)＋(−100) = +300，视觉不跳变、直接续滑。
        assertEquals(300f, jumpCompensationShift(500, 100, -100f), 0.001f)
    }

    // ---- ② Dock 宠物挂载门：四格矩阵 ----

    @Test
    fun dockPet_homeSteady_isVisible() {
        assertTrue(dockPetVisible(intentInputMode = false, exitInProgress = false))
    }

    @Test
    fun dockPet_focusMode_isHidden() {
        assertFalse(dockPetVisible(intentInputMode = true, exitInProgress = false))
    }

    @Test
    fun dockPet_exitFunnelInFlight_isHidden() {
        // 关键回归锁：70% 翻转点 mode 已回 false，但 funnel 未完成
        // （isExiting）时 Dock 宠物仍不挂载，否则与在途意图宠物双影。
        assertFalse(dockPetVisible(intentInputMode = false, exitInProgress = true))
    }

    @Test
    fun dockPet_focusAndExit_isHidden() {
        assertFalse(dockPetVisible(intentInputMode = true, exitInProgress = true))
    }
}
