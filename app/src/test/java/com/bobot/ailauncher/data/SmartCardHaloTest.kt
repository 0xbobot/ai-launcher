package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloColor
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloCornerRadius
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloInset
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloMaxAlpha
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloMinAlpha
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloStroke
import com.bobot.ailauncher.ui.smartcard.haloStrokeClearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 光晕描边几何单测（第三十八批，Bob 2026-11-15：光晕显土——收敛）。
 *
 * 选①极淡单色：三色渐变整条删掉，只留一色暖金、1dp 发丝边、透明度
 * 0.15↔0.35 轻呼吸。几何仍守第三十七批修毛边的同一条规则：内收＝
 * 半线宽＋0.5dp 安全余量（1dp ＝ 0.5dp＋0.5dp）、圆角 19dp（卡片圆角
 * 20dp－内收 1dp，同心）。描边本身的抗锯齿渲染是 Compose／Android
 * 运行时，JVM 测不到，靠几何算式＋Bob 真机验（边缘顺滑、不显土）。
 */
class SmartCardHaloTest {

    @Test
    fun haloColor_isSingleGold() {
        assertEquals(Color(0xFFD4A24E), SmartCardHaloColor)
    }

    @Test
    fun haloStroke_is1dp() {
        assertEquals(1.dp, SmartCardHaloStroke)
    }

    @Test
    fun haloInset_is1dp() {
        assertEquals(1.dp, SmartCardHaloInset)
    }

    @Test
    fun haloCornerRadius_is19dp() {
        assertEquals(19.dp, SmartCardHaloCornerRadius)
    }

    @Test
    fun haloStrokeClearance_newGeometry_isHalfDp() {
        // 新几何：1dp − 1dp/2 ＝ 0.5dp，整条描边落在裁剪内。
        assertEquals(0.5.dp, haloStrokeClearance(SmartCardHaloInset, SmartCardHaloStroke))
    }

    @Test
    fun haloStrokeClearance_insetBelowHalfStroke_isNegative() {
        // 规则锁死：内收小于半线宽时外缘探出裁剪外（第三十七批毛边的根因）——
        // 内收 0dp、线宽 1dp 时余量 −0.5dp。内收必须 ≥ 半线宽＋0.5dp。
        assertTrue(haloStrokeClearance(0.dp, 1.dp) < 0.dp)
    }

    @Test
    fun haloCornerRadius_plusInset_equalsCardRadius() {
        // 描边圆角与裁剪圆角同心：19dp ＋ 1dp ＝ 卡片圆角 20dp。
        assertEquals(20.dp, SmartCardHaloCornerRadius + SmartCardHaloInset)
    }

    @Test
    fun haloAlphaRange_isFaint() {
        // 极淡：下限 0.15、上限 0.35，峰值也只有三分之一不透明。
        assertEquals(0.15f, SmartCardHaloMinAlpha, 0.001f)
        assertEquals(0.35f, SmartCardHaloMaxAlpha, 0.001f)
        assertTrue(SmartCardHaloMinAlpha < SmartCardHaloMaxAlpha)
    }
}
