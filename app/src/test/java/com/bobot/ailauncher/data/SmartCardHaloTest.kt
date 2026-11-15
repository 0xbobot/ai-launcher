package com.bobot.ailauncher.data

import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloCornerRadius
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloInset
import com.bobot.ailauncher.ui.smartcard.SmartCardHaloStroke
import com.bobot.ailauncher.ui.smartcard.haloStrokeClearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 光晕描边几何单测（第三十七批，Bob 2026-11-15：光晕有毛边）。
 *
 * 根因（见 SmartCardHaloInset）：旧内收 1dp 小于半个线宽（3dp/2＝1.5dp），
 * 描边外缘探出卡片边界 0.5dp，被 .clip(shape) 的无抗锯齿 clipPath 切掉，
 * 圆角处成台阶。修法：内收 2dp（半线宽＋0.5dp 安全余量）、圆角 18dp
 * （卡片圆角 20dp－内收 2dp，同心）。描边宽度／颜色／呼吸下限／一圈时长
 * 不动（SmartCardRedesignTest 已锁死）。
 * 描边本身的抗锯齿渲染与 clipPath 的裁剪是 Compose／Android 运行时，
 * JVM 测不到，靠几何算式＋Bob 真机验（边缘顺滑、无台阶）。
 */
class SmartCardHaloTest {

    @Test
    fun haloInset_is2dp() {
        assertEquals(2.dp, SmartCardHaloInset)
    }

    @Test
    fun haloCornerRadius_is18dp() {
        assertEquals(18.dp, SmartCardHaloCornerRadius)
    }

    @Test
    fun haloStrokeClearance_newGeometry_isHalfDp() {
        // 新几何：2dp − 3dp/2 ＝ 0.5dp，整条描边落在裁剪内。
        assertEquals(0.5.dp, haloStrokeClearance(SmartCardHaloInset, SmartCardHaloStroke))
    }

    @Test
    fun haloStrokeClearance_oldGeometry_wasNegative() {
        // 旧几何：1dp − 3dp/2 ＝ −0.5dp，外缘探出裁剪外——毛边的根因。
        // 锁死这个算式，防有人把内收改回 1dp。
        assertTrue(haloStrokeClearance(1.dp, 3.dp) < 0.dp)
    }

    @Test
    fun haloCornerRadius_plusInset_equalsCardRadius() {
        // 描边圆角与裁剪圆角同心：18dp ＋ 2dp ＝ 卡片圆角 20dp。
        assertEquals(20.dp, SmartCardHaloCornerRadius + SmartCardHaloInset)
    }
}
