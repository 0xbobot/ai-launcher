package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color
import com.bobot.ailauncher.ui.glass.BackdropKind
import com.bobot.ailauncher.ui.smartcard.carouselDotColor
import com.bobot.ailauncher.ui.smartcard.smartCardTintAlpha
import com.bobot.ailauncher.ui.theme.AILauncherColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stageshow 桌面优化单测（Bob 2026-11-21 拍板，设计文档
 * desktop-stageshow-optimization-design.md §3）：
 * ①圆点当前张实心深灰（Title）、其余 Hint 40%——金色只留主操作；
 * ②卡片无抬起阴影（第五十六批，Bob 2026-11-27 定：阴影不跟圆角，尖角从
 * 20dp 圆角后露出来、卡片底部两角各一个三角尖；卡片改和 Dock 同口径，
 * 全程没有 .shadow()，只靠玻璃本身立住）。
 * 呼吸边已删的锁死靠编译（SmartCardHalo*／breathingBorderModifier 残留
 * 引用即编不过）；抬起阴影同理（SmartCardLiftShadow 常量已删、SmartCardView
 * 修饰链里的 .shadow() 调用已删，残留引用即编不过）；安静版 hero 与三模板
 * 收敛见 CoverTimeTextTest（isQuietHero）。
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

    // ---------- 卡片无抬起阴影（第五十六批，Dock 同口径） ----------

    @Test
    fun cardStandsOnGlassOnly_noLiftShadow() {
        // 阴影拿掉后卡片靠玻璃本身立住：衬底全档 0.95（第五十五批），三档同值；
        // 圆角 20dp 不动（见 SmartCardStack.kt SmartCardView 修饰链）。
        // .shadow() 调用与 SmartCardLiftShadow 常量已删，残留引用即编不过。
        assertEquals(0.95f, smartCardTintAlpha(BackdropKind.TEXTURED), 0.001f)
        assertEquals(0.95f, smartCardTintAlpha(BackdropKind.FLAT), 0.001f)
        assertEquals(0.95f, smartCardTintAlpha(null), 0.001f)
    }
}
