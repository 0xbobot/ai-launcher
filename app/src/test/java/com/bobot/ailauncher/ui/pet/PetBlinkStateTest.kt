package com.bobot.ailauncher.ui.pet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PetBlinkState 回归：G1——眨眼中途被取消必须复位，不能永久卡住。
 * 时序（85/70/85）与节律一律不动，本文件只断言取消恢复；
 * G2（绘制顺序）是 Canvas 图层顺序，单测无法断言，见代码级复核。
 */
class PetBlinkStateTest {

    @Test
    fun cancelledBlinkResetsStateAndAllowsNextBlink() = runTest {
        val state = PetBlinkState()
        // 独立 Job 的 scope 模拟 effect scope：effect 重启 = 取消该 Job。
        val effectJob = Job()
        val effectScope = CoroutineScope(coroutineContext + effectJob)
        state.requestBlink(effectScope)
        runCurrent()
        assertTrue(state.blinking)

        // 闭合期内取消在途眨眼（模拟 effect 重启）。
        effectJob.cancel()
        runCurrent()
        assertFalse(state.blinking)
        assertFalse(state.closedPhase)

        // 取消后下一次眨眼仍可触发（G1 的另一面：卡住后一切请求被丢弃）。
        val effectJob2 = Job()
        state.requestBlink(CoroutineScope(coroutineContext + effectJob2))
        runCurrent()
        assertTrue(state.blinking)
        // 跑完一次完整眨眼（含 10% 双眨追加段在内至多 600ms）。
        advanceUntilIdle()
        assertFalse(state.blinking)
        assertFalse(state.closedPhase)
    }

    @Test
    fun resetCancelsInFlightBlink() = runTest {
        val state = PetBlinkState()
        val effectJob = Job()
        state.requestBlink(CoroutineScope(coroutineContext + effectJob))
        runCurrent()
        assertTrue(state.blinking)

        state.reset()
        runCurrent()
        assertFalse(state.blinking)
        assertFalse(state.closedPhase)
    }
}
