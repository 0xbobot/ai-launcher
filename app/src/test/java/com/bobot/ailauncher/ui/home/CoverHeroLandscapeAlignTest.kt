package com.bobot.ailauncher.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 横屏首页卡片顶边对齐单测（Bob 2026-11-22 实拍，563/v1.4.19 横屏截图）：
 * 横屏 D2 Dock 顶边比卡片顶边高约 20dp（无宠物线实测 55/1000 屏高）。
 * 根因：Dock 是 MainScreen 悬浮层、底边锚定（顶边＝屏高－184dp，D2）；
 * 卡片在 HomeScreen Column 里顺着 hero 往下排（顶边＝顶距＋hero 高＋8dp
 * 间隙），两边没有共同锚点；hero 底边已顶到 Dock 顶边附近，卡片不能直接
 * 上移（会压 hero），须连 hero 一起收。
 * 修法：横屏 hero 顶距收 20dp（32→12），卡片随之上移 20dp，顶边落到
 * D2 Dock 顶边。只动横屏 hero；竖屏顶距、卡种、复盘都不动。
 */
class CoverHeroLandscapeAlignTest {

    // ---------- 横屏 hero 顶距：32→12dp ----------

    @Test
    fun `landscape hero top padding is 12dp`() {
        assertEquals(12.dp, CoverHeroLandscapeTopPadding)
    }

    @Test
    fun `landscape hero top reduction is exactly the measured gap 20dp`() {
        assertEquals(20f, CoverHeroLandscapeTopReductionDp, 0.001f)
        // 32（修前）－12（修后）＝20，与实拍 gap 一致。
        assertEquals(20.dp, 32.dp - CoverHeroLandscapeTopPadding)
    }

    // ---------- 对齐：修后卡片顶边＝D2 Dock 顶边 ----------

    @Test
    fun `card top after fix equals D2 dock top`() {
        // 实拍：修前卡片顶边＝Dock 顶边＋20dp；修后上移 20dp，正好落到 Dock 顶边。
        // 例：屏高 360dp 时 D2 Dock 顶边＝360－184＝176，修前卡片顶边 196。
        val dockTopD2 = 176f
        val cardTopBefore = dockTopD2 + 20f
        assertEquals(dockTopD2, landscapeCardTopAfterFix(cardTopBefore), 0.001f)
    }

    @Test
    fun `card top after fix equals D2 dock top for another screen height`() {
        // 屏高 400dp 时 D2 Dock 顶边＝400－184＝216；修前卡片顶边 236，修后 216。
        assertEquals(216f, landscapeCardTopAfterFix(236f), 0.001f)
    }

    // ---------- 已知局限：D1 Dock 顶边低 48dp，本批不对齐 D1 ----------

    @Test
    fun `D1 dock top is 48dp below the aligned card top`() {
        // D1 Dock 高 136dp、D2 高 184dp，相差 48dp；卡片顶边对齐 D2 后，
        // D1 Dock 顶边（屏高－136）比卡片顶边（屏高－184）低 48dp。
        // D1 也对齐须把卡片再下移 48dp，卡片底边将超出屏底 56dp、动作按钮
        // 被切掉——须另定机制，本批不做（见收据已知局限）。
        val screenH = 360f
        val cardTopAfterFix = screenH - 184f
        val dockTopD1 = screenH - 136f
        assertEquals(48f, dockTopD1 - cardTopAfterFix, 0.001f)
    }

    // ---------- 回归：竖屏 hero 顶距不动 ----------

    @Test
    fun `portrait hero top padding unchanged`() {
        assertEquals(56.dp, CoverHeroGiantTimeTopPadding)
        assertEquals(16.dp, CoverHeroGiantTimeTopPaddingCompact)
    }
}
