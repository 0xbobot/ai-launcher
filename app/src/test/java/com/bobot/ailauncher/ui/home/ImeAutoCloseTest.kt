package com.bobot.ailauncher.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IME 收起自动收卡片判定矩阵（2026-10-27，锁 imeAutoCloseShouldFire）：
 * 只有“输入态下可见→隐藏边沿、无提交在途、未在退出中、mode 与方向均未变”
 * 才放行；其余一律不触发（调用方以此重置跟踪）。
 */
class ImeAutoCloseTest {

    private fun fire(
        prevVisible: Boolean = true,
        nowVisible: Boolean = false,
        inputMode: Boolean = true,
        modeChanged: Boolean = false,
        orientationChanged: Boolean = false,
        isInputState: Boolean = true,
        submitPending: Boolean = false,
        isExiting: Boolean = false,
    ): Boolean = imeAutoCloseShouldFire(
        prevVisible = prevVisible,
        nowVisible = nowVisible,
        inputMode = inputMode,
        modeChanged = modeChanged,
        orientationChanged = orientationChanged,
        isInputState = isInputState,
        submitPending = submitPending,
        isExiting = isExiting,
    )

    @Test
    fun inputStateVisibleToHiddenFires() {
        assertTrue(fire())
    }

    @Test
    fun hiddenToVisibleDoesNotFire() {
        assertFalse(fire(prevVisible = false, nowVisible = true))
    }

    @Test
    fun noEdgeDoesNotFire() {
        assertFalse(fire(prevVisible = true, nowVisible = true))
        assertFalse(fire(prevVisible = false, nowVisible = false))
    }

    @Test
    fun notInInputModeDoesNotFire() {
        assertFalse(fire(inputMode = false))
    }

    @Test
    fun modeChangeResetsWithoutFiring() {
        assertFalse(fire(modeChanged = true))
    }

    @Test
    fun orientationChangeResetsWithoutFiring() {
        assertFalse(fire(orientationChanged = true))
    }

    @Test
    fun holdingStateDoesNotFire() {
        assertFalse(fire(isInputState = false))
    }

    @Test
    fun submitPendingSuppresses() {
        assertFalse(fire(submitPending = true))
    }

    @Test
    fun exitInProgressSuppresses() {
        assertFalse(fire(isExiting = true))
    }
}
