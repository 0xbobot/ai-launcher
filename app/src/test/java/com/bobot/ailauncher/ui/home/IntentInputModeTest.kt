package com.bobot.ailauncher.ui.home

import androidx.compose.ui.unit.dp
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

    // ---- PetPerchTransition（输入态专注·进出过渡 C，2026-10-26） ----
    // 退态 = 进态位移取负：travel 1→0 对应行程进度 0→1；Dock 淡入／首页
    // 淡入触发点 = 行程过 70%（travel ≤ 0.30），与宠物落地交叠。

    @Test
    fun `退态进度 - travel 从 1 到 0 对应进度 0 到 1`() {
        org.junit.Assert.assertEquals(0f, exitTravelProgress(1f), 0.001f)
        org.junit.Assert.assertEquals(0.70f, exitTravelProgress(0.30f), 0.001f)
        org.junit.Assert.assertEquals(1f, exitTravelProgress(0f), 0.001f)
    }

    @Test
    fun `退态进度 - 越界值钳制在 0 到 1`() {
        org.junit.Assert.assertEquals(0f, exitTravelProgress(1.2f), 0.001f)
        org.junit.Assert.assertEquals(1f, exitTravelProgress(-0.2f), 0.001f)
    }

    @Test
    fun `Dock 淡入触发 - 行程过 70 才触发`() {
        assertFalse(exitDockFadeShouldStart(1f))
        assertFalse(exitDockFadeShouldStart(0.31f))
        assertTrue(exitDockFadeShouldStart(0.30f))
        assertTrue(exitDockFadeShouldStart(0f))
    }

    @Test
    fun `滞留不收 - 聚焦过、有文本、有在场任一成立`() {
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

    // ---- 输入态专注·修复（2026-10-26，356 真机反馈）：专注组底边垫高四路分流 ----
    private val padNav = 16.dp
    private val padWindow = 700.dp

    @Test
    fun `底边垫高 - 停靠键盘未收缩时垫等于键盘高`() {
        // 停靠键盘、窗口未收缩：卡片精确坐键盘顶边，0dp 贴合
        org.junit.Assert.assertEquals(
            300.dp,
            resolveFocusBottomPad(
                imeBottom = 300.dp, windowResized = false, keyboardExpected = true,
                isPortrait = true, windowHeight = padWindow, navClearance = padNav
            )
        )
    }

    @Test
    fun `底边垫高 - 窗口已收缩时垫为零且不误加导航垫`() {
        // 356 回归锁：窗口已收缩时 imeBottom 归零，旧逻辑误加导航垫、卡片浮在键盘上方
        org.junit.Assert.assertEquals(
            0.dp,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = true, keyboardExpected = true,
                isPortrait = true, windowHeight = 400.dp, navClearance = padNav
            )
        )
    }

    @Test
    fun `底边垫高 - 悬浮键盘竖屏兜底为窗口高一半`() {
        // 悬浮键盘：窗口未收缩、imeBottom = 0、键盘在场预期成立 → 组顶到上半区
        org.junit.Assert.assertEquals(
            350.dp,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = false, keyboardExpected = true,
                isPortrait = true, windowHeight = padWindow, navClearance = padNav
            )
        )
    }

    @Test
    fun `底边垫高 - 悬浮键盘横屏不走竖屏兜底`() {
        // 横屏不走竖屏 50% 兜底（isPortrait 门只管竖屏），垫回导航垫；
        // 横屏不推的显式分支见下一测试
        org.junit.Assert.assertEquals(
            padNav,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = false, keyboardExpected = true,
                isPortrait = false, windowHeight = 360.dp, navClearance = padNav
            )
        )
    }

    @Test
    fun `底边垫高 - 横屏恒为零不钉导航垫`() {
        // Bob 横屏不推＋底边不钉导航垫口径（本批）：横屏专注态不做任何
        // 键盘避让——停靠 inset、悬浮、已收缩信号一律不抬垫，垫恒为 0dp
        // （停靠键盘靠窗口收缩自然顶起，悬浮键盘由用户自己放位置）
        org.junit.Assert.assertEquals(
            0.dp,
            resolveFocusBottomPad(
                imeBottom = 300.dp, windowResized = false, keyboardExpected = true,
                isPortrait = false, windowHeight = 360.dp, navClearance = padNav,
                isLandscape = true
            )
        )
        org.junit.Assert.assertEquals(
            0.dp,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = false, keyboardExpected = true,
                isPortrait = false, windowHeight = 360.dp, navClearance = padNav,
                isLandscape = true
            )
        )
        org.junit.Assert.assertEquals(
            0.dp,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = true, keyboardExpected = true,
                isPortrait = false, windowHeight = 360.dp, navClearance = padNav,
                isLandscape = true
            )
        )
    }

    @Test
    fun `底边垫高 - 键盘收起与结果态垫导航垫`() {
        // 键盘收起／结果态（在场预期不成立）：沿用 356 原口径
        org.junit.Assert.assertEquals(
            padNav,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = false, keyboardExpected = false,
                isPortrait = true, windowHeight = padWindow, navClearance = padNav
            )
        )
    }

    @Test
    fun `底边垫高 - 停靠键盘优先于窗口收缩信号`() {
        // imeBottom > 0 恒优先（窗口收缩与否不影响停靠键盘的精确贴合）
        org.junit.Assert.assertEquals(
            280.dp,
            resolveFocusBottomPad(
                imeBottom = 280.dp, windowResized = true, keyboardExpected = true,
                isPortrait = true, windowHeight = 420.dp, navClearance = padNav
            )
        )
    }

    // ---- 输入态专注·弹闪修复（2026-10-26，357 真机反馈）：卡片外边距容器相对口径 ----
    @Test
    fun `卡片外边距 - 容器相对口径`() {
        // 竖屏容器即屏宽：360 → 20（卡片 320）、800 → 140（卡片 520 居中）
        org.junit.Assert.assertEquals(20.dp, resolveCardHorizontalPadding(360.dp))
        org.junit.Assert.assertEquals(140.dp, resolveCardHorizontalPadding(800.dp))
        // 横屏容器即左区槽宽：840 → 160（卡片 520 居于槽内）、560 → 20（卡片 520）
        org.junit.Assert.assertEquals(160.dp, resolveCardHorizontalPadding(840.dp))
        org.junit.Assert.assertEquals(20.dp, resolveCardHorizontalPadding(560.dp))
    }

    @Test
    fun `卡片外边距 - 357 横屏塌卡回归锁`() {
        // 357 回归（崩溃记录已定性，2026-10-26 19:41）：屏宽 800dp 时左区槽宽
        // 440dp（coverLeftFrac 0.55），旧屏宽公式在槽内二次扣减致卡片 40dp；
        // 屏宽 ≤ 720dp 时卡片恰好塌到 0，玻璃暖核半径 = 0.35 × 0 = 0，
        // RadialGradient 要求 radius > 0，主线程炸 IllegalArgumentException
        // （Glass.kt:378）。容器相对口径下卡片 = 440 − 2×20 = 400dp，不塌。
        val padding = resolveCardHorizontalPadding(440.dp)
        org.junit.Assert.assertEquals(20.dp, padding)
        org.junit.Assert.assertEquals(400.dp, 440.dp - padding * 2f)
    }

    @Test
    fun `卡片外边距 - 全宽扫卡片恒大于零`() {
        // 357 崩溃宽：屏宽 720dp 时左区槽 360dp，旧公式卡片恰好为 0；
        // 容器相对口径下 360dp 容器 → 外边距 20dp、卡片 320dp，恢复健康。
        val padding360 = resolveCardHorizontalPadding(360.dp)
        org.junit.Assert.assertEquals(20.dp, padding360)
        org.junit.Assert.assertEquals(320.dp, 360.dp - padding360 * 2f)
        // 全宽扫一遍：不存在让卡片塌到 0 的容器宽。
        for (w in 200..1200 step 40) {
            val pad = resolveCardHorizontalPadding(w.dp)
            val card = w.dp - pad * 2f
            assertTrue("container ${w}dp -> card $card must stay > 0", card > 0.dp)
        }
    }
}
