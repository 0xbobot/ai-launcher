package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.ui.pet.dragHeadRiseDp
import com.bobot.ailauncher.ui.pet.dragHeadXDps
import com.bobot.ailauncher.ui.pet.dragShoulderRiseDp
import com.bobot.ailauncher.ui.pet.dragShoulderXDps
import com.bobot.ailauncher.ui.pet.dragStretchScale
import com.bobot.ailauncher.ui.pet.dragTiltDeg
import com.bobot.ailauncher.ui.theme.PetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 意图拖拽复用锁定（2026-10-27）：意图宠物传专属 DragBodyState 后走的
 * 必须是 Dock 同一套纯函数（FloatingPet dragBody 分支的调用集合），
 * 且意图几何组合与 Dock 同款构造（盒不动、头在盒内升、肩形升锁定）。
 * 其中压入不变量是关键回归锁：盒若再升肩升，满拉伸时头顶出肩峰
 * 27dp 的缝（32 − 5），违背“任何拖拽帧都不许头身分开”。
 */
class IntentDragReuseTest {

    private val eps = 0.05f

    private fun approx(expected: Float, actual: Float) {
        assertTrue("expected ≈ $expected but was $actual", kotlin.math.abs(expected - actual) <= eps)
    }

    // ---- 与 Dock 同一套纯函数（FloatingPet dragBody 分支逐项调用） ----

    @Test
    fun `intent drag uses same tanh rise curve with 32dp cap`() {
        assertEquals(0f, dragHeadRiseDp(0f), 0f)
        assertEquals(0f, dragHeadRiseDp(-10f), 0f)
        approx(1.0f, dragHeadRiseDp(1f))
        approx(29.37f, dragHeadRiseDp(40f))
        assertEquals(32f, dragHeadRiseDp(100f), 0f)
        assertEquals(32f, dragHeadRiseDp(1000f), 0f)
    }

    @Test
    fun `intent drag uses same lateral clamp 0 8 head and 0 45 shoulder`() {
        approx(32f, dragHeadXDps(40f))
        approx(-32f, dragHeadXDps(-40f))
        approx(32f, dragHeadXDps(100f))
        approx(18f, dragShoulderXDps(40f))
        approx(-18f, dragShoulderXDps(-40f))
        approx(18f, dragShoulderXDps(100f))
    }

    @Test
    fun `intent drag shoulder locks one to one to head rise`() {
        assertEquals(0f, dragShoulderRiseDp(0f), 0f)
        assertEquals(17.5f, dragShoulderRiseDp(17.5f), 0f)
        assertEquals(32f, dragShoulderRiseDp(32f), 0f)
    }

    @Test
    fun `intent drag tilt reaches 5 degrees at full shoulder travel and clamps`() {
        assertEquals(0f, dragTiltDeg(0f), 0f)
        assertEquals(5f, dragTiltDeg(18f), 0f)
        assertEquals(-5f, dragTiltDeg(-18f), 0f)
        assertEquals(5f, dragTiltDeg(36f), 0f)
    }

    @Test
    fun `intent drag stretch scale and release squash match dock`() {
        val (sx, sy) = dragStretchScale(32f, 32f)
        approx(0.97f, sx)
        approx(1.06f, sy)
        val (ox, oy) = dragStretchScale(-6.4f, 32f)
        approx(1.03f, ox)
        approx(0.94f, oy)
    }

    @Test
    fun `intent drag release spring is the shared DragRelease family spec`() {
        assertEquals(0.45f, PetMotion.DragRelease.dampingRatio, 0f)
        assertEquals(90f, PetMotion.DragRelease.stiffness, 0f)
    }

    // ---- 意图几何组合（Dock 同款构造） ----

    @Test
    fun `intent card shape rise is base plus drag shoulder rise`() {
        assertEquals(18f, intentCardShapeRiseDp(18f, 0f), 0f)
        assertEquals(50f, intentCardShapeRiseDp(18f, 32f), 0f)
    }

    @Test
    fun `intent pet box offset does not move with drag`() {
        // 盒偏移与拖拽无关：400 面板＋18 基底 rise－5 压入 = −413，
        // 拖拽前后同一值（盒动了才是 bug，见压入不变量）。
        assertEquals(-413f, intentPetBoxOffsetYDp(400f, 18f, 5f), 0f)
    }

    @Test
    fun `intent press stays 5dp at full drag by construction`() {
        // 足（盒底 − 头升）与肩峰（面板底 − 基底 rise − 肩升）之距：
        // 盒底 = −(400＋18−5) = −413；足 = −413 − 32 = −445；
        // 峰 = −(400＋18＋32) = −450；足 − 峰 = 5（压入恒定）。
        // 肩升 = 头升（FloatingPet 锁定 1:1）是前提。
        val panel = 400f
        val base = 18f
        val press = 5f
        val headRise = 32f
        val dragShoulder = dragShoulderRiseDp(headRise)
        val foot = intentPetBoxOffsetYDp(panel, base, press) - headRise
        val peak = -(panel + base + dragShoulder)
        assertEquals(press, foot - peak, 0f)
    }

    @Test
    fun `intent box rising with shoulder would open a 27dp gap`() {
        // 反向锁：若盒也升肩升（字面误读），足 = −413 − 32 − 32 = −477，
        // 峰 = −450，足 − 峰 = −27，即头顶出肩峰 27dp 的缝。此组合
        // 永不许出现在生产公式里；本用例把“为什么盒不动”钉死。
        val panel = 400f
        val base = 18f
        val press = 5f
        val headRise = 32f
        val dragShoulder = 32f
        val footIfBoxMoved = intentPetBoxOffsetYDp(panel, base, press) - headRise - dragShoulder
        val peak = -(panel + base + dragShoulder)
        assertEquals(-27f, footIfBoxMoved - peak, 0f)
    }

    @Test
    fun `intent bubble follows head exactly once not twice`() {
        // 基底 = −(400＋18＋100−5＋8) = −521；头升 32 → −553，
        // 恰好跟 32，不含肩升（肩升若再加一次会是 −585）。
        assertEquals(-521f, intentBubbleOffsetYDp(400f, 18f, 100f, 5f, 0f, 0f, 0f, 1f), 0f)
        assertEquals(-553f, intentBubbleOffsetYDp(400f, 18f, 100f, 5f, 32f, 0f, 0f, 1f), 0f)
        // 头沉 10 → 下移 10：−543。
        assertEquals(-543f, intentBubbleOffsetYDp(400f, 18f, 100f, 5f, 32f, 10f, 0f, 1f), 0f)
    }

    @Test
    fun `intent bubble slides in with pet travel`() {
        // travel=0 时全量滑入位移，travel=1 时归零；横向同理。
        assertEquals(-421f, intentBubbleOffsetYDp(400f, 18f, 100f, 5f, 0f, 0f, 100f, 0f), 0f)
        assertEquals(-521f, intentBubbleOffsetYDp(400f, 18f, 100f, 5f, 0f, 0f, 100f, 1f), 0f)
        assertEquals(70f, intentBubbleOffsetXDps(20f, 50f, 0f), 0f)
        assertEquals(20f, intentBubbleOffsetXDps(20f, 50f, 1f), 0f)
        assertEquals(45f, intentBubbleOffsetXDps(20f, 50f, 0.5f), 0f)
    }

    @Test
    fun `intent list follows head rise like dock list`() {
        // Dock 同款：清单只跟头升。静止 −521，头升 32 → −553。
        assertEquals(-521f, intentListOffsetYDp(400f, 18f, 100f, 5f, 0f), 0f)
        assertEquals(-553f, intentListOffsetYDp(400f, 18f, 100f, 5f, 32f), 0f)
    }
}
