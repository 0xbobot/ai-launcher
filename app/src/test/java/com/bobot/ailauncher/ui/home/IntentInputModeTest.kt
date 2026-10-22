package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.intent.IntentUiState
import com.bobot.ailauncher.data.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.99（主页重构 R-1）：输入态状态矩阵——在场状态判定与退出判定。
 * 锁死语义：思考/回复/待确认/缺权限/追问在场时胶囊不许收起；
 * 只有「曾聚焦 + 已失焦 + 无事发生」才退出输入态（Dock 回位）。
 */
class IntentInputModeTest {

    private val busyStates: List<IntentUiState> = listOf(
        IntentUiState.Planning("导航去机场"),
        IntentUiState.NeedsConfirmation(
            ActionRequest(
                intent = "打电话给妈妈",
                capabilityId = "phone.call",
                riskLevel = RiskLevel.MEDIUM_RISK
            ),
            "确认打电话？"
        ),
        IntentUiState.NeedsPermission("android.permission.CALL_PHONE", "需要电话权限"),
        IntentUiState.NeedsInput("去哪儿？", "nav.go", "destination", "导航"),
        IntentUiState.Reply("已为你打开地图"),
        IntentUiState.Error("网络不可用")
    )

    private val freeStates: List<IntentUiState> = listOf(
        IntentUiState.Idle,
        IntentUiState.Editing("导航")
    )

    @Test
    fun `在场状态都算 holds`() {
        busyStates.forEach { assertTrue("$it 应为在场状态", intentStateHoldsInput(it)) }
    }

    @Test
    fun `空闲与编辑不算在场`() {
        freeStates.forEach { assertFalse("$it 不应为在场状态", intentStateHoldsInput(it)) }
    }

    @Test
    fun `失焦且无事发生才退出`() {
        freeStates.forEach {
            assertTrue(
                "$it 下失焦应退出",
                intentInputShouldExit(inputMode = true, hasFocused = true, focused = false, state = it)
            )
        }
    }

    @Test
    fun `在场状态下失焦不退出`() {
        busyStates.forEach {
            assertFalse(
                "$it 下失焦不应退出",
                intentInputShouldExit(inputMode = true, hasFocused = true, focused = false, state = it)
            )
        }
    }

    @Test
    fun `未聚焦过不退出 - 聚焦在场不退出 - 非输入态不退出`() {
        assertFalse(
            intentInputShouldExit(inputMode = true, hasFocused = false, focused = false, state = IntentUiState.Idle)
        )
        assertFalse(
            intentInputShouldExit(inputMode = true, hasFocused = true, focused = true, state = IntentUiState.Idle)
        )
        assertFalse(
            intentInputShouldExit(inputMode = false, hasFocused = true, focused = false, state = IntentUiState.Idle)
        )
    }
}
