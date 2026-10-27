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
 *     出现／消失致整列瞬移，绘制位移先钉回旧位再滑向新位
 *     （竖屏区块切换后仅横屏沿用）。
 *  ② Dock 宠物挂载门 dockPetVisible：退出进行中 Dock 宠物不挂载
 *     （横屏场景换先出后进；竖屏离散切换不置退出位、翻转即挂载）。
 *  ③ 聚焦槽位可见门 focusSlotVisible（竖屏区块切换，2026-10-27）：
 *     竖屏可见性即 mode——离散切换无退态滞留挂载；横屏沿用
 *     mode && 进场延迟走完（先出后进）。
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
        // 关键回归锁：横屏退出进行中（isExiting）时 Dock 宠物仍不挂载，
        // 否则与在途意图宠物双影；竖屏离散切换不置此位。
        assertFalse(dockPetVisible(intentInputMode = false, exitInProgress = true))
    }

    @Test
    fun dockPet_focusAndExit_isHidden() {
        assertFalse(dockPetVisible(intentInputMode = true, exitInProgress = true))
    }

    // ---- ③ 聚焦槽位可见门：竖屏离散、横屏先出后进 ----

    @Test
    fun focusSlot_portrait_visibleIsExactlyMode() {
        // 竖屏：可见性即 mode，无退态滞留挂载——退出翻转当帧槽位即
        // 消失（旧口径 mode || isExiting 会让槽位在退态中滞留、与回场
        // 的信息区／Dock 区半透明重叠）。
        assertTrue(
            focusSlotVisible(intentInputMode = true, isLandscape = false, landscapeSlotReady = false)
        )
        assertTrue(
            focusSlotVisible(intentInputMode = true, isLandscape = false, landscapeSlotReady = true)
        )
        assertFalse(
            focusSlotVisible(intentInputMode = false, isLandscape = false, landscapeSlotReady = false)
        )
        assertFalse(
            focusSlotVisible(intentInputMode = false, isLandscape = false, landscapeSlotReady = true)
        )
    }

    @Test
    fun focusSlot_landscape_needsModeAndReady() {
        // 横屏：家场景先出、聚焦场景后进——mode 为真且进场延迟走完
        // 才挂载；任一不满足即不挂载。
        assertTrue(
            focusSlotVisible(intentInputMode = true, isLandscape = true, landscapeSlotReady = true)
        )
        assertFalse(
            focusSlotVisible(intentInputMode = true, isLandscape = true, landscapeSlotReady = false)
        )
        assertFalse(
            focusSlotVisible(intentInputMode = false, isLandscape = true, landscapeSlotReady = true)
        )
        assertFalse(
            focusSlotVisible(intentInputMode = false, isLandscape = true, landscapeSlotReady = false)
        )
    }
}
