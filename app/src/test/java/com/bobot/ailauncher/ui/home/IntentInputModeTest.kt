package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.intent.IntentUiState
import com.bobot.ailauncher.data.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.99（主页重构 R-1）：输入态状态矩阵——在场状态判定。
 * v0.68.101（D-2 重构）：退出判定换代——旧「失焦边沿推断」
 * （intentInputShouldExit）已删除，新矩阵锁两条纯函数通道：
 *  - intentSettleShouldExit：提交后落定退出（在场→不在场 + 未聚焦）；
 *  - intentInputStranded：滞留兜底（从未聚焦 + 空文本 + 无在场）。
 * 关键回归锁：打字会话中的事件——切 IME 面板/删到空造成的失焦，
 * 在任何状态下都不构成退出（退出只走点外事件/Back/落定/显式请求）。
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

    // ---- intentSettleShouldExit（落定通道） ----

    @Test
    fun `落定退出 - 在场退场且未聚焦`() {
        assertTrue(
            intentSettleShouldExit(
                inputMode = true, prevHolds = true, holdsNow = false, focused = false
            )
        )
    }

    @Test
    fun `落定不退 - 仍在聚焦中（用户接管追问）`() {
        assertFalse(
            intentSettleShouldExit(
                inputMode = true, prevHolds = true, holdsNow = false, focused = true
            )
        )
    }

    @Test
    fun `落定不退 - 状态未落定或本来就没在场`() {
        // 仍在场
        assertFalse(
            intentSettleShouldExit(
                inputMode = true, prevHolds = true, holdsNow = true, focused = false
            )
        )
        // 从没在场过（纯打字会话——D-2 回归锁：打字/切面板/删空都不走此通道）
        assertFalse(
            intentSettleShouldExit(
                inputMode = true, prevHolds = false, holdsNow = false, focused = false
            )
        )
        // 非输入态
        assertFalse(
            intentSettleShouldExit(
                inputMode = false, prevHolds = true, holdsNow = false, focused = false
            )
        )
    }

    // ---- intentInputStranded（滞留兜底通道） ----

    @Test
    fun `滞留判定 - 三重门槛全中才收`() {
        assertTrue(
            intentInputStranded(hasFocused = false, textEmpty = true, holdsNow = false)
        )
    }

    @Test
    fun `滞留不收 - 聚焦过/有文本/有在场任一成立`() {
        // 聚焦过（真实会话开始过——D-2 两幕场景的锁存状态）
        assertFalse(
            intentInputStranded(hasFocused = true, textEmpty = true, holdsNow = false)
        )
        // 有文本（删到空之前/恢复的文本）
        assertFalse(
            intentInputStranded(hasFocused = false, textEmpty = false, holdsNow = false)
        )
        // 有在场状态（自动带起呈现，不弹键盘是正道）
        assertFalse(
            intentInputStranded(hasFocused = false, textEmpty = true, holdsNow = true)
        )
    }
}
