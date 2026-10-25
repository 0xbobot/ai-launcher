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
        // decide() 现有两个 List 重载，emptyList() 须显式给元素类型，否则推断失败。
        assertEquals(
            BriefingTextMode.FALLBACK_SCRIM,
            BriefingAdaptiveText.decide(emptyList<GlassPolicy.BandLuminance>(), null)
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
    fun `zone bands for zone weights fraction continuously instead of rounding the count`() {
        val all = (0 until 6).map { GlassPolicy.BandLuminance(0.5f, 0f) }
        assertTrue(BriefingAdaptiveText.zoneBandsForZone(all, 0f).isEmpty())
        // 0.34 * 6 = 2.04：前两带权重 1，第三带权重 0.04（旧取整版此处取 2 带）。
        val partial = BriefingAdaptiveText.zoneBandsForZone(all, 0.34f)
        assertEquals(3, partial.size)
        assertEquals(1.0, partial[0].weight, 1e-9)
        assertEquals(1.0, partial[1].weight, 1e-9)
        assertEquals(0.04, partial[2].weight, 1e-6)
        assertEquals(6, BriefingAdaptiveText.zoneBandsForZone(all, 1f).size)
        assertEquals(6, BriefingAdaptiveText.zoneBandsForZone(all, 2f).size)
        assertTrue(BriefingAdaptiveText.zoneBandsForZone(emptyList(), 0.5f).isEmpty())
    }

    @Test
    fun `mixed 23-14 wallpaper left region picks dark text`() {
        // 23:14 实拍复算：字实际所在的左半区顶部带均值 0.3037／0.4246／0.4345
        // （整幅宽带均值 0.1902 曾以 0.06 之差误判白字）；左区取样应判深墨字。
        val zone = listOf(
            BriefingAdaptiveText.ZoneBand(0.3037, 0.08, 1.0),
            BriefingAdaptiveText.ZoneBand(0.4246, 0.08, 1.0),
            BriefingAdaptiveText.ZoneBand(0.4345, 0.08, 1.0),
        )
        assertEquals(BriefingTextMode.DARK_TEXT, BriefingAdaptiveText.decide(zone, null))
    }

    @Test
    fun `weighted mean moves continuously across a band boundary`() {
        // 连续加权：占比 0.16→0.17 时第二带权重只从 0 增到 0.02，加权均值移动
        // 不超过 0.02 × 带间亮度差（< 0.01）；旧取整版在此处取带数 1→2，
        // 均值跳变约带间差的一半（23:14 案放大器之一）。底线安全的构造下
        // 结论也不翻转（带内底线否决是另一回事，不由本测试覆盖）。
        val all = listOf(
            GlassPolicy.BandLuminance(0.30f, 0f),
            GlassPolicy.BandLuminance(0.20f, 0f),
            GlassPolicy.BandLuminance(0.20f, 0f),
            GlassPolicy.BandLuminance(0.20f, 0f),
            GlassPolicy.BandLuminance(0.20f, 0f),
            GlassPolicy.BandLuminance(0.20f, 0f),
        )
        val below = BriefingAdaptiveText.zoneBandsForZone(all, 0.16f)
        val above = BriefingAdaptiveText.zoneBandsForZone(all, 0.17f)
        fun wmean(bs: List<BriefingAdaptiveText.ZoneBand>) =
            bs.sumOf { it.mean * it.weight } / bs.sumOf { it.weight }
        assertTrue(kotlin.math.abs(wmean(above) - wmean(below)) < 0.01)
        assertEquals(
            BriefingAdaptiveText.decide(below, null),
            BriefingAdaptiveText.decide(above, null),
        )
        assertEquals(BriefingTextMode.DARK_TEXT, BriefingAdaptiveText.decide(above, null))
    }

    @Test
    fun `reported 23-14 full-width band does not yield a confident pick`() {
        // 23:14 整幅宽带均值 0.1902：白字 3.91、墨字 3.85，都不到 4.5，
        // 均值门已让它回退信号（旧版 PetZone 映射曾以 0.06 之差猜成白字）。
        val zone = listOf(BriefingAdaptiveText.ZoneBand(0.1902, 0.206, 1.0))
        assertEquals(BriefingTextMode.FALLBACK_SCRIM, BriefingAdaptiveText.decide(zone, null))
    }

    @Test
    fun `bimodal guard vetoes a palette that passes on the mean alone`() {
        // 孤立 guard 的构造：均值 0.30 时深墨字均值对比 5.61（本已达标），
        // 但标准差 0.30 时逆境极端（0.30 - 0.30 = 0）上对比仅 0.80 < 3.0，
        // guard 让它出局、回退信号——双峰带下均值不可信，宁可不猜。
        val zone = listOf(BriefingAdaptiveText.ZoneBand(0.30, 0.30, 1.0))
        assertEquals(BriefingTextMode.FALLBACK_SCRIM, BriefingAdaptiveText.decide(zone, null))
    }
}
