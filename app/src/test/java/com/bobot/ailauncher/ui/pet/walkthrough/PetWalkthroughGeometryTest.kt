package com.bobot.ailauncher.ui.pet.walkthrough

import com.bobot.ailauncher.ui.pet.BlinkKind
import com.bobot.ailauncher.ui.pet.BlinkVariety
import com.bobot.ailauncher.ui.pet.PetBlinkState
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 宠物走查 Loop P0：几何量测（纯 JVM，不渲染）。
 *
 * 量测口径与生产代码同源（括号内为生产侧锚点）：
 * - 肩线：PullUpDock 圆肩批——dockPetSize = panelH × 0.52（紧配置 ×0.8），
 *   floatPetWidth = dockPetSize × 1.224，chord = 宽 + 70dp，
 *   rise = chord × 18/156，压入 5dp。
 * - 宠物盒高：FloatingPet——盒高 = 宽 × 1318/1043（玻璃头长宽比）。
 * - 气泡高：PetActionList——标题行（4dp 顶垫 + 48dp 关闭钮）+ 内容区
 *   min(8 行 × 行高, 340dp 上限）+ 8dp 底垫 + 10dp 尾巴；8 行 × 行高
 *   （15sp 字 + 28dp 纵向内边距 ≥ 43dp）恒超 340dp，故上限恒绑定，
 *   气泡高恒为 52 + 340 + 8 + 10 = 410dp（字号 1.0/1.3 皆然）。
 * - Dock  perch 净空：气泡顶 y = 屏高 −（面板高 + rise − 压入 + 宠物盒高）
 *   − 8dp − 气泡高；净空 = 气泡顶 y − 状态栏 24dp（假设值，见矩阵文档）；
 *   净空为负＝气泡探出屏顶，记 attention（P0 只量测、不判好坏）。
 * - 意图 perch：气泡顶相对容器底 = 面板高 + 18dp 肩升 + 109.18dp 宠物高
 *   − 5dp 压入 + 8dp + 410dp；卡片容器底 y 需渲染树实测，P1 量测，
 *   P0 只锁气泡高与偏移公式（见矩阵文档 P1 项）。
 *
 * 每个用例一个矩阵项：JUnit XML 里即按项分行，报告脚本直接取用。
 * 漂移判定走 WalkthroughGoldens（无基线记 new；仅走查工作流 enforce 时失败）。
 */
class PetWalkthroughGeometryTest {

    private val tag = "geometry"

    // ---- 肩线（Dock perch，静态 peak=0） ----

    private fun dockWidths(panelH: Float, compact: Boolean): Float {
        val pet = panelH * 0.52f * (if (compact) 0.8f else 1f)
        return pet * 1.224f
    }

    private fun chord(width: Float): Float = width + 70f
    private fun rise(chord: Float): Float = chord * 18f / 156f

    @Test
    fun shoulderD1Chord() {
        val w = dockWidths(136f, false)
        WalkthroughGoldens.check(tag, "shoulder-d1-chord", chord(w).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderD1Rise() {
        val w = dockWidths(136f, false)
        WalkthroughGoldens.check(tag, "shoulder-d1-rise", rise(chord(w)).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderD2Chord() {
        val w = dockWidths(184f, false)
        WalkthroughGoldens.check(tag, "shoulder-d2-chord", chord(w).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderD2Rise() {
        val w = dockWidths(184f, false)
        WalkthroughGoldens.check(tag, "shoulder-d2-rise", rise(chord(w)).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderCompactD1Chord() {
        val w = dockWidths(136f, true)
        WalkthroughGoldens.check(tag, "shoulder-compact-d1-chord", chord(w).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderCompactD1Rise() {
        val w = dockWidths(136f, true)
        WalkthroughGoldens.check(tag, "shoulder-compact-d1-rise", rise(chord(w)).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderCompactD2Chord() {
        val w = dockWidths(184f, true)
        WalkthroughGoldens.check(tag, "shoulder-compact-d2-chord", chord(w).toDouble(), 0.1, "dp")
    }

    @Test
    fun shoulderCompactD2Rise() {
        val w = dockWidths(184f, true)
        WalkthroughGoldens.check(tag, "shoulder-compact-d2-rise", rise(chord(w)).toDouble(), 0.1, "dp")
    }

    // ---- 宠物盒高 ----

    private fun petHeight(width: Float): Float = width * 1318f / 1043f

    @Test
    fun petboxD1Height() {
        WalkthroughGoldens.check(tag, "petbox-d1-height", petHeight(dockWidths(136f, false)).toDouble(), 0.1, "dp")
    }

    @Test
    fun petboxD2Height() {
        WalkthroughGoldens.check(tag, "petbox-d2-height", petHeight(dockWidths(184f, false)).toDouble(), 0.1, "dp")
    }

    @Test
    fun petboxCompactD1Height() {
        WalkthroughGoldens.check(tag, "petbox-compact-d1-height", petHeight(dockWidths(136f, true)).toDouble(), 0.1, "dp")
    }

    @Test
    fun petboxCompactD2Height() {
        WalkthroughGoldens.check(tag, "petbox-compact-d2-height", petHeight(dockWidths(184f, true)).toDouble(), 0.1, "dp")
    }

    @Test
    fun petboxIntentHeight() {
        // INTENT_PET_WIDTH = 108dp × 0.8（IntentEntry）。
        WalkthroughGoldens.check(tag, "petbox-intent-height", petHeight(108f * 0.8f).toDouble(), 0.1, "dp")
    }

    // ---- 气泡高与 Dock perch 净空 ----

    private fun bubbleHeight(): Float = 52f + 340f + 8f + 10f

    @Test
    fun bubbleHeightValue() {
        WalkthroughGoldens.check(tag, "bubble-height", bubbleHeight().toDouble(), 1.0, "dp")
    }

    private fun dockClearance(screenH: Float): Float {
        val stack = 136f + rise(chord(dockWidths(136f, false))) - 5f +
            petHeight(dockWidths(136f, false))
        return screenH - stack - 8f - bubbleHeight() - 24f
    }

    @Test
    fun dockClearance700dp() {
        val c = dockClearance(700f)
        WalkthroughGoldens.check(tag, "dock-clearance-700dp", c.toDouble(), 0.5, "dp", attention = c < 0f)
    }

    @Test
    fun dockClearance800dp() {
        val c = dockClearance(800f)
        WalkthroughGoldens.check(tag, "dock-clearance-800dp", c.toDouble(), 0.5, "dp", attention = c < 0f)
    }

    @Test
    fun dockClearance900dp() {
        val c = dockClearance(900f)
        WalkthroughGoldens.check(tag, "dock-clearance-900dp", c.toDouble(), 0.5, "dp", attention = c < 0f)
    }

    // ---- 眨眼时序（BlinkVariety 纯函数值＋状态机不变量） ----

    @Test
    fun blinkFullTiming() {
        val t = BlinkVariety.timing(BlinkKind.FULL)
        WalkthroughGoldens.check(tag, "blink-full-close-ms", t.closeMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-full-hold-ms", t.holdMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-full-open-ms", t.openMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-full-depth", t.depth.toDouble(), 0.001, "")
    }

    @Test
    fun blinkHalfTiming() {
        val t = BlinkVariety.timing(BlinkKind.HALF)
        WalkthroughGoldens.check(tag, "blink-half-close-ms", t.closeMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-half-hold-ms", t.holdMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-half-open-ms", t.openMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-half-depth", t.depth.toDouble(), 0.001, "")
    }

    @Test
    fun blinkSlowHalfTiming() {
        val t = BlinkVariety.timing(BlinkKind.SLOW_HALF)
        WalkthroughGoldens.check(tag, "blink-slow-half-close-ms", t.closeMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-slow-half-hold-ms", t.holdMs.toDouble(), 0.0, "ms")
        WalkthroughGoldens.check(tag, "blink-slow-half-open-ms", t.openMs.toDouble(), 0.0, "ms")
    }

    @Test
    fun blinkDoubleGap() {
        WalkthroughGoldens.check(tag, "blink-double-gap-ms", BlinkVariety.DOUBLE_GAP_MS.toDouble(), 0.0, "ms")
    }

    @Test
    fun blinkSettlesAfterAnyKind() = runTest {
        // 随机种类：最长双眨 240+120+240=600ms；推进 2000ms 后必复位
        //（blinking／closedPhase／depth 全清），与种类无关。
        WalkthroughGoldens.requireIncluded("blink-settles")
        val state = PetBlinkState()
        state.requestBlink(this)
        advanceTimeBy(2000)
        assertFalse(state.blinking)
        assertFalse(state.closedPhase)
        assertEquals(0f, state.blinkDepth)
        WalkthroughGoldens.note(tag, "blink-settles", "any kind settles within 2000ms")
    }

    // ---- 触控（P0：代码位置清单；渲染树实测是 P1） ----

    @Test
    fun touchTargetsChecklist() {
        // 以下触控尺寸以代码为准、P0 只做位置清单（不进 fail 判定）：
        // - 气泡关闭钮 48dp：PetActionList.kt（Box size 48dp，testTag pet_action_close）
        // - 意图卡片 ✕ 一键退出 48dp 热区：IntentEntry.kt（卡片角 ✕）
        // - 能力问题示例行 min 48dp：InputDeck.kt（DeckExamplesRow heightIn min 48dp）
        // - Dock 图标 56dp（D1）／46dp（D2）：PullUpDock.kt（DockRow 盒）
        // 渲染树上的实测（onGloballyPositioned／语义树）列入 P1。
        WalkthroughGoldens.note(
            tag,
            "touch-targets-checklist",
            "close 48dp / intent exit 48dp / example rows min 48dp / dock icons 56dp(D1) 46dp(D2); rendered-tree measurement is P1",
        )
    }
}
