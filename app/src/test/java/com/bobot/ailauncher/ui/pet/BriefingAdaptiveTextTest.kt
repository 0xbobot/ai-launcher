package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.ui.glass.GlassPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 简报自适应字色决策单测：档位选择、死区回退、迟滞保持、带内底线、取带规则。 */
class BriefingAdaptiveTextTest {

    private fun bands(vararg means: Float) =
        means.map { GlassPolicy.BandLuminance(it, 0f) }

    @Test
    fun `dark wallpaper picks light text`() {
        assertEquals(
            BriefingTextMode.LIGHT_TEXT,
            BriefingAdaptiveText.decide(bands(0.05f, 0.06f), null)
        )
    }

    @Test
    fun `bright wallpaper picks dark text`() {
        assertEquals(
            BriefingTextMode.DARK_TEXT,
            BriefingAdaptiveText.decide(bands(0.85f, 0.80f), null)
        )
    }

    @Test
    fun `mid tone wallpaper falls back to scrim when neither palette reaches 4_5`() {
        // 均值 0.19：白字约 3.9、墨字约 3.8，都不到 4.5 → 不猜字色，回退纱。
        assertEquals(
            BriefingTextMode.FALLBACK_SCRIM,
            BriefingAdaptiveText.decide(bands(0.19f), null)
        )
    }

    @Test
    fun `empty bands fall back to scrim`() {
        assertEquals(
            BriefingTextMode.FALLBACK_SCRIM,
            BriefingAdaptiveText.decide(emptyList(), null)
        )
    }

    @Test
    fun `previous light text is kept inside the relaxed keep zone`() {
        // 均值 0.20：白字严格对比约 3.8（不到 4.5），但高于宽松保持线 3.5 → 不翻回纱。
        assertEquals(
            BriefingTextMode.LIGHT_TEXT,
            BriefingAdaptiveText.decide(bands(0.20f), BriefingTextMode.LIGHT_TEXT)
        )
    }

    @Test
    fun `previous fallback needs strict contrast to leave the scrim`() {
        // 同样均值 0.20：无历史／回退态时 3.8 不到 4.5 → 仍回退纱（不对称阈值，防抖）。
        assertEquals(
            BriefingTextMode.FALLBACK_SCRIM,
            BriefingAdaptiveText.decide(bands(0.20f), BriefingTextMode.FALLBACK_SCRIM)
        )
    }

    @Test
    fun `previous light text is released once it drops below the keep floor`() {
        // 均值 0.30：白字约 2.7（跌破 3.5），墨字约 5.6（严格达标）→ 切墨字。
        assertEquals(
            BriefingTextMode.DARK_TEXT,
            BriefingAdaptiveText.decide(bands(0.30f), BriefingTextMode.LIGHT_TEXT)
        )
    }

    @Test
    fun `a dark band inside a bright zone forces fallback via the per-band floor`() {
        // 均值被亮带拉高、墨字均值达标，但暗带上墨字对比不到 3.0 → 均值撒谎，回退纱。
        assertEquals(
            BriefingTextMode.FALLBACK_SCRIM,
            BriefingAdaptiveText.decide(bands(0.85f, 0.85f, 0.02f), null)
        )
    }

    @Test
    fun `bands for zone follows the briefing height fraction`() {
        val all = (0 until 6).map { GlassPolicy.BandLuminance(0.5f, 0f) }
        assertTrue(BriefingAdaptiveText.bandsForZone(all, 0f).isEmpty())
        assertEquals(2, BriefingAdaptiveText.bandsForZone(all, 0.34f).size)
        assertEquals(6, BriefingAdaptiveText.bandsForZone(all, 1f).size)
        assertEquals(6, BriefingAdaptiveText.bandsForZone(all, 2f).size)
        assertTrue(BriefingAdaptiveText.bandsForZone(emptyList(), 0.5f).isEmpty())
    }
}
