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
    fun `strong textured params after v0-68-96 recalibration for real-device feedback`() {
        // Bob .94 真机反馈「完全没有玻璃的感觉」后的再标定：叠白大降、饱和
        // 上调、折射加宽加强、亮边提亮，并新增外侧暗描边。
        val p = GlassPolicy.paramsFor(GlassTier.STRONG, BackdropKind.TEXTURED)!!
        assertTrue("blur 24..28dp", p.blurRadiusDp in 24f..28f)
        assertTrue("saturation raised to ~1.45", p.saturation in 1.40f..1.50f)
        assertTrue("tint lowered to ~0.07", p.tintAlpha in 0.05f..0.10f)
        assertTrue("refraction strengthened", p.refractionDp >= 14f)
        assertTrue("refraction band widened", p.bandDp >= 26f)
        assertTrue("edge highlight raised", p.edgeTopAlpha >= 0.70f)
        assertTrue("outer rim present on textured", p.rimAlpha > 0f)
        val m = GlassPolicy.paramsFor(GlassTier.MEDIUM, BackdropKind.TEXTURED)!!
        assertTrue("medium tint lowered too", m.tintAlpha in 0.05f..0.10f)
        assertTrue("medium saturation matches strong", m.saturation == p.saturation)
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

    // ---------- v0.68.96 区域分类 ----------

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `local detail - smooth gradient is near zero, checker is high`() {
        val w = 48
        val h = 48
        // 平滑竖直渐变（纸面）：相邻步差 < 1
        val gradient = IntArray(w * h) { i ->
            val y = i / w
            argb(200 + y / 4, 195 + y / 4, 185 + y / 4)
        }
        assertTrue(GlassPolicy.localDetail(gradient, w, h) < GlassPolicy.DETAIL_TEXTURED_MIN)
        // 棋盘纹理：每步差都大
        val checker = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if ((x + y) % 2 == 0) argb(128, 128, 128) else argb(96, 96, 96)
        }
        assertTrue(GlassPolicy.localDetail(checker, w, h) >= GlassPolicy.DETAIL_TEXTURED_MIN)
    }

    @Test
    fun `photo with real texture never classifies flat even when global stats are diluted`() {
        // 复现 Bob 壁纸的误判结构：整图大面积平滑低饱和（灰），只有底部一条带
        // 有真实纹理（灰色棋盘：饱和 0、与全局同亮度带）——整图统计 std<8、
        // sat<0.06 会判 FLAT，但底部带必须判 TEXTURED。
        val w = 48
        val h = 48
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (y >= h * 5 / 6) {
                if ((x + y) % 2 == 0) argb(128, 128, 128) else argb(112, 112, 112)
            } else {
                argb(120, 120, 120)
            }
        }
        val (gStd, gSat) = GlassPolicy.backdropStats(px)
        assertEquals(
            "precondition: global rule dilutes to FLAT",
            BackdropKind.FLAT, GlassPolicy.classifyBackdrop(gStd, gSat)
        )
        val bands = GlassPolicy.classifyBands(px, w, h)
        assertEquals(GlassPolicy.BACKDROP_BANDS, bands.size)
        assertEquals("bottom band sees the texture", BackdropKind.TEXTURED, bands.last())
        assertEquals(BackdropKind.TEXTURED, GlassPolicy.overallKind(bands))
        assertEquals(
            BackdropKind.TEXTURED,
            GlassPolicy.kindAtFraction(bands, 0.95f)
        )
    }

    @Test
    fun `paper gradient classifies flat in every band`() {
        val w = 48
        val h = 48
        // 暖纸渐变：上亮下略暗、无纹理、低饱和
        val paper = IntArray(w * h) { i ->
            val y = i / w
            val t = y * 10 / h
            argb(244 - t, 242 - t, 238 - t)
        }
        val bands = GlassPolicy.classifyBands(paper, w, h)
        assertTrue(bands.all { it == BackdropKind.FLAT })
        assertEquals(BackdropKind.FLAT, GlassPolicy.overallKind(bands))
    }

    @Test
    fun `sky over mountains - dock band textured while sky band stays calm`() {
        // Bob 的壁纸结构：上部平滑浅蓝天（低纹理），下部远山（噪点纹理）。
        val w = 48
        val h = 48
        val px = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (y < h * 3 / 5) {
                // 浅蓝天：平滑渐变
                argb(168 + y / 6, 198 + y / 6, 228)
            } else {
                // 远山：蓝绿基底 + 确定性噪点（模拟树冠/山脊纹理）
                val noise = ((x * 31 + y * 17) % 23) - 11
                argb(86 + noise, 116 + noise, 84 + noise)
            }
        }
        val bands = GlassPolicy.classifyBands(px, w, h)
        assertEquals("dock band (bottom) is textured", BackdropKind.TEXTURED, bands.last())
        assertEquals(BackdropKind.TEXTURED, GlassPolicy.kindAtFraction(bands, 0.92f))
    }

    @Test
    fun `classify bands guards bad input`() {
        val bands = GlassPolicy.classifyBands(IntArray(0), 0, 0)
        assertEquals(GlassPolicy.BACKDROP_BANDS, bands.size)
        assertTrue(bands.all { it == BackdropKind.FLAT })
        assertEquals(BackdropKind.FLAT, GlassPolicy.kindAtFraction(emptyList(), 0.5f))
    }

    // ---- 简报自适应字色用带亮度（线性光）----

    @Test
    fun `band luminance of solid colors`() {
        val black = GlassPolicy.bandLuminance(IntArray(36) { argb(0, 0, 0) }, 6, 6)
        assertEquals(GlassPolicy.BACKDROP_BANDS, black.size)
        assertTrue(black.all { it.mean == 0f && it.stdDev == 0f })
        val white = GlassPolicy.bandLuminance(IntArray(36) { argb(255, 255, 255) }, 6, 6)
        assertTrue(white.all { it.mean == 1f && it.stdDev == 0f })
    }

    @Test
    fun `band luminance splits top and bottom halves`() {
        // 高 6 行 = 6 带，每带 1 行：上半白、下半黑。
        val px = IntArray(36) { i -> if (i / 6 < 3) argb(255, 255, 255) else argb(0, 0, 0) }
        val bands = GlassPolicy.bandLuminance(px, 6, 6)
        assertTrue(bands.take(3).all { it.mean == 1f })
        assertTrue(bands.drop(3).all { it.mean == 0f })
    }

    @Test
    fun `band luminance guards bad input`() {
        val bands = GlassPolicy.bandLuminance(IntArray(0), 0, 0)
        assertEquals(GlassPolicy.BACKDROP_BANDS, bands.size)
        assertTrue(bands.all { it.mean == 0f })
    }

    @Test
    fun `relative luminance and contrast ratio follow WCAG`() {
        assertEquals(0.0, GlassPolicy.relativeLuminance(argb(0, 0, 0)), 1e-9)
        assertEquals(1.0, GlassPolicy.relativeLuminance(argb(255, 255, 255)), 1e-9)
        assertEquals(
            21.0,
            GlassPolicy.contrastRatio(1.0, 0.0),
            1e-9
        )
        // 深铜金刊头在白底上必须过 4.5（亮底字色档的金色替代）。
        val bronze = GlassPolicy.relativeLuminance(0xFF7A5F0F.toInt())
        assertTrue(
            "bronze on white must pass 4.5, was ${GlassPolicy.contrastRatio(bronze, 1.0)}",
            GlassPolicy.contrastRatio(bronze, 1.0) >= 4.5
        )
    }
}
