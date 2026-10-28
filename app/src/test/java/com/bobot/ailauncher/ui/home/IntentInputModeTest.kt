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

    // 注：竖屏区块切换（2026-10-27）删除了 PetPerchTransition 的退态
    // 行程函数（exitTravelProgress／exitDockFadeShouldStart）及其测试；
    // 竖屏槽位可见性改由 focusSlotVisible 锁定（见 EnterExitFlashTest）。

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
    fun `底边垫高 - 键盘落下态空输入卡片留在屏底垫导航垫`() {
        // 2026-10-29 意图页设计评审第 3 轮·落下态规则锁：键盘已收起且意图态
        // 仍在场（imeBottom 归零、窗口未收缩、在场预期不成立）→ 垫＝导航栏
        // clearance；权重 Spacer 把卡片推到窗口底边，修撑高后面板内容定高
        // （空输入态竖屏 158dp），底边可见、不悬空。卡片去留不由本函数定：
        // 376 适用时跟随自动收起，不适用时留在屏底等返回键／点外（见
        // resolveFocusBottomPad 与 IntentEntry 退出兜底注释）。数值口径与
        // “键盘收起与结果态垫导航垫”同款，此测试以落下态语义单独锁定，
        // 防后续重构把落下态误并入悬浮 50% 兜底或垫 0。
        org.junit.Assert.assertEquals(
            padNav,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = false, keyboardExpected = false,
                isPortrait = true, windowHeight = padWindow, navClearance = padNav
            )
        )
        // 落下态的另一信号组合：窗口已收缩后键盘落下（imeBottom 归零）同样
        // 不悬空——收缩分支垫 0，卡片坐窗口底边即屏底（与落下态规则一致，
        // 只是底边贴合方式不同：收缩时窗口底边即屏底，无需导航垫）。
        org.junit.Assert.assertEquals(
            0.dp,
            resolveFocusBottomPad(
                imeBottom = 0.dp, windowResized = true, keyboardExpected = false,
                isPortrait = true, windowHeight = 400.dp, navClearance = padNav
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

    // ---- 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：槽位可见门 ----
    @Test
    fun `槽位可见门 - 横屏只在 mode 真且进场延迟走完时挂载`() {
        // 家场景先出、聚焦场景后进：进场延迟未走完（Dock 退出淡出未完成）
        // 时槽位不挂载，不与 Dock 重叠。
        assertTrue(focusSlotVisible(true, true, true))
        assertFalse(focusSlotVisible(true, true, false))
        assertFalse(focusSlotVisible(false, true, true))
        assertFalse(focusSlotVisible(false, true, false))
    }

    @Test
    fun `槽位可见门 - 竖屏区块切换后可见性即 mode`() {
        // 离散切换无退态滞留挂载：mode 回落当帧槽位即消失，不与回场
        // 的信息区／Dock 区半透明重叠；旧口径（mode || 退态进行中）
        // 已随竖屏 funnel 删除而作废。
        assertTrue(focusSlotVisible(true, false, false))
        assertTrue(focusSlotVisible(true, false, true))
        assertFalse(focusSlotVisible(false, false, false))
        assertFalse(focusSlotVisible(false, false, true))
    }

    // ---- 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：进态位移量 ----
    @Test
    fun `进态位移 - 横屏恒为零与锚点无关`() {
        // 横屏不移头：锚点量到与否、落差多大，位移一律为零。
        org.junit.Assert.assertEquals(
            0.dp to 0.dp,
            resolveEnterTravelDelta(true, 100.dp, 500.dp, 300.dp, 800.dp)
        )
        org.junit.Assert.assertEquals(
            0.dp to 0.dp,
            resolveEnterTravelDelta(true, null, 500.dp, 300.dp, 800.dp)
        )
    }

    @Test
    fun `进态位移 - 竖屏锚点未量到时返回 null 保持初值`() {
        // 原行为：锚点未量到时不写位移量（保持 funnel 残值或兜底），不归零。
        org.junit.Assert.assertNull(
            resolveEnterTravelDelta(false, null, 500.dp, 300.dp, 800.dp)
        )
    }

    @Test
    fun `进态位移 - 竖屏按脚底落差公式且钳制`() {
        // 落差 200dp：横向恒为零，纵向 = dockFoot − intentFoot。
        org.junit.Assert.assertEquals(
            0.dp to 200.dp,
            resolveEnterTravelDelta(false, 100.dp, 500.dp, 300.dp, 800.dp)
        )
        // 负落差钳制到 0（意图脚底已低于 Dock 脚底，不反向位移）。
        org.junit.Assert.assertEquals(
            0.dp to 0.dp,
            resolveEnterTravelDelta(false, 100.dp, 100.dp, 300.dp, 800.dp)
        )
        // 超屏高钳制到屏高。
        org.junit.Assert.assertEquals(
            0.dp to 800.dp,
            resolveEnterTravelDelta(false, 100.dp, 900.dp, 100.dp, 800.dp)
        )
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

    // ---- intentBackStep（返回键落点，2026-10-27 真机报障） ----

    @Test
    fun `返回落点 - 规划中走真取消`() {
        org.junit.Assert.assertEquals(
            IntentBackStep.CancelPlanning,
            intentBackStep(IntentUiState.Planning("导航去机场"))
        )
    }

    @Test
    fun `返回落点 - 其余在场态走内层 dismiss`() {
        busyStates.filterNot { it is IntentUiState.Planning }.forEach {
            org.junit.Assert.assertEquals(
                "$it 应先关内层",
                IntentBackStep.DismissInner,
                intentBackStep(it)
            )
        }
    }

    @Test
    fun `返回落点 - 空闲与编辑直接退出意图`() {
        freeStates.forEach {
            org.junit.Assert.assertEquals(
                "$it 应直接退出",
                IntentBackStep.ExitIntent,
                intentBackStep(it)
            )
        }
    }
}
