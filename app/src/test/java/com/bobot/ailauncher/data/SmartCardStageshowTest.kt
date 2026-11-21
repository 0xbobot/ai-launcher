package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.smartcard.SmartCardLiftShadow
import com.bobot.ailauncher.ui.smartcard.carouselDotColor
import com.bobot.ailauncher.ui.theme.AILauncherColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stageshow 桌面优化单测（Bob 2026-11-21 拍板，设计文档
 * desktop-stageshow-optimization-design.md §3）：
 * ①圆点当前张实心深灰（Title）、其余 Hint 40%——金色只留主操作；
 * ②卡片抬起阴影 8.dp（呼吸边拿掉后靠面与阴影立住）。
 * 呼吸边已删的锁死靠编译（SmartCardHalo*／breathingBorderModifier 残留
 * 引用即编不过）；安静版 hero 与三模板收敛见 CoverTimeTextTest（isQuietHero）。
 */
class SmartCardStageshowTest {

    // ---------- 轮播圆点颜色（3.3） ----------

    @Test
    fun carouselDot_selected_isTitle() {
        // 当前张实心深灰（Light Title #FF1F1D1A），不用金。
        assertEquals(Color(0xFF1F1D1A), carouselDotColor(true, AILauncherColors.Light))
        assertEquals(Color(0xFFF4F2EE), carouselDotColor(true, AILauncherColors.Dark))
    }

    @Test
    fun carouselDot_unselected_isHint40() {
        // 其余 Hint 40% 透明度。
        assertEquals(
            Color(0xFF8A8378).copy(alpha = 0.4f),
            carouselDotColor(false, AILauncherColors.Light)
        )
        assertEquals(
            Color(0xFF6B6560).copy(alpha = 0.4f),
            carouselDotColor(false, AILauncherColors.Dark)
        )
    }

    // ---------- 卡片抬起阴影（3.2） ----------

    @Test
    fun cardLiftShadow_is8dp() {
        assertEquals(8.dp, SmartCardLiftShadow)
    }
}
