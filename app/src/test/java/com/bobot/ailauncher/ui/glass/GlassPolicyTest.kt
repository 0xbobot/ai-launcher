package com.bobot.ailauncher.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 玻璃试点批纯逻辑单测：分级降级链、文档参数标定、饱和矩阵、背景分类。 */
class GlassPolicyTest {

    private fun tier(
        sdk: Int,
        kind: GlassSurfaceKind = GlassSurfaceKind.STRONG,
        active: Boolean = true,
        powerSave: Boolean = false,
        lowBattery: Boolean = false,
        lowRam: Boolean = false,
    ) = GlassPolicy.resolveTier(sdk, kind, active, powerSave, lowBattery, lowRam)

    @Test
    fun `api below 31 always simulated`() {
        assertEquals(GlassTier.SIMULATED, tier(29))
        assertEquals(GlassTier.SIMULATED, tier(30, GlassSurfaceKind.MEDIUM))
    }

    @Test
    fun `api 31 and 32 strong surfaces fall back to medium`() {
        assertEquals(GlassTier.MEDIUM, tier(31))
        assertEquals(GlassTier.MEDIUM, tier(32))
        assertEquals(GlassTier.MEDIUM, tier(31, GlassSurfaceKind.MEDIUM))
    }

    @Test
    fun `api 33 strong surface gets strong, medium surface stays medium`() {
        assertEquals(GlassTier.STRONG, tier(33))
        assertEquals(GlassTier.STRONG, tier(35))
        assertEquals(GlassTier.MEDIUM, tier(34, GlassSurfaceKind.MEDIUM))
    }

    @Test
    fun `inactive desktop forces simulated (power saving gate)`() {
        assertEquals(GlassTier.SIMULATED, tier(34, active = false))
        assertEquals(GlassTier.SIMULATED, tier(34, GlassSurfaceKind.MEDIUM, active = false))
    }

    @Test
    fun `power save or low battery steps down one tier`() {
        assertEquals(GlassTier.MEDIUM, tier(34, powerSave = true))
        assertEquals(GlassTier.MEDIUM, tier(34, lowBattery = true))
        assertEquals(GlassTier.SIMULATED, tier(34, GlassSurfaceKind.MEDIUM, powerSave = true))
    }

    @Test
    fun `low ram steps down one tier, combined pressures reach simulated`() {
        assertEquals(GlassTier.MEDIUM, tier(34, lowRam = true))
        assertEquals(GlassTier.SIMULATED, tier(34, lowRam = true, powerSave = true))
        // 已是最低档不再下沉
        assertEquals(GlassTier.SIMULATED, GlassPolicy.run { GlassTier.SIMULATED.stepDown() })
    }

    @Test
    fun `strong textured params follow the design doc calibration`() {
        val p = GlassPolicy.paramsFor(GlassTier.STRONG, BackdropKind.TEXTURED)!!
        assertTrue("blur 24..28dp", p.blurRadiusDp in 24f..28f)
        assertTrue("saturation 130..140%", p.saturation in 1.30f..1.40f)
        assertTrue("tint 12..15%", p.tintAlpha in 0.12f..0.15f)
        assertTrue("refraction present", p.refractionDp > 0f)
        assertTrue("specular present", p.specularStrength > 0f)
    }

    @Test
    fun `flat backdrop is light frost - weaker refraction, heavier tint`() {
        val photo = GlassPolicy.paramsFor(GlassTier.STRONG, BackdropKind.TEXTURED)!!
        val paper = GlassPolicy.paramsFor(GlassTier.STRONG, BackdropKind.FLAT)!!
        assertTrue(paper.refractionDp < photo.refractionDp)
        assertTrue(paper.specularStrength < photo.specularStrength)
        assertTrue(paper.tintAlpha > photo.tintAlpha)
        assertTrue(paper.saturation < photo.saturation)
    }

    @Test
    fun `medium never refracts and simulated has no params`() {
        val m = GlassPolicy.paramsFor(GlassTier.MEDIUM, BackdropKind.TEXTURED)!!
        assertEquals(0f, m.refractionDp)
        assertTrue(m.blurRadiusDp in 24f..28f)
        assertNull(GlassPolicy.paramsFor(GlassTier.SIMULATED, BackdropKind.TEXTURED))
        assertNull(GlassPolicy.paramsFor(GlassTier.SIMULATED, BackdropKind.FLAT))
    }

    @Test
    fun `saturation matrix identity at 1 and gray at 0`() {
        val id = GlassPolicy.saturationMatrix(1f)
        assertEquals(1f, id[0], 1e-6f)
        assertEquals(0f, id[1], 1e-6f)
        assertEquals(1f, id[6], 1e-6f)
        assertEquals(1f, id[12], 1e-6f)
        assertEquals(1f, id[18], 1e-6f)
        val gray = GlassPolicy.saturationMatrix(0f)
        // s=0：三行相同（都取亮度），输出与输入颜色无关只看亮度
        assertEquals(gray[0], gray[5], 1e-6f)
        assertEquals(gray[1], gray[6], 1e-6f)
        assertEquals(gray[2], gray[12], 1e-6f)
        // 1.35 时对角增益 > 1（增色）
        assertTrue(GlassPolicy.saturationMatrix(1.35f)[0] > 1f)
    }

    @Test
    fun `backdrop stats - solid color has zero variance`() {
        val solid = IntArray(64) { 0xFFF4F2EE.toInt() }
        val (std, sat) = GlassPolicy.backdropStats(solid)
        assertEquals(0f, std, 1e-3f)
        assertTrue(sat < 0.06f)
        assertEquals(BackdropKind.FLAT, GlassPolicy.classifyBackdrop(std, sat))
    }

    @Test
    fun `backdrop stats - two tone pattern is textured`() {
        val checker = IntArray(64) { i -> if (i % 2 == 0) 0xFF203040.toInt() else 0xFFF0D090.toInt() }
        val (std, sat) = GlassPolicy.backdropStats(checker)
        assertTrue(std > 8f)
        assertEquals(BackdropKind.TEXTURED, GlassPolicy.classifyBackdrop(std, sat))
    }

    @Test
    fun `backdrop stats - vivid single color photo-like patch is textured`() {
        // 纯色但高饱和（晚霞色块）：亮度无起伏但色彩强，不该判纸面
        val vivid = IntArray(64) { 0xFFE64A19.toInt() }
        val (std, sat) = GlassPolicy.backdropStats(vivid)
        assertTrue(sat > 0.06f)
        assertEquals(BackdropKind.TEXTURED, GlassPolicy.classifyBackdrop(std, sat))
    }

    @Test
    fun `empty pixels classify as flat`() {
        val (std, sat) = GlassPolicy.backdropStats(IntArray(0))
        assertEquals(BackdropKind.FLAT, GlassPolicy.classifyBackdrop(std, sat))
    }
}
