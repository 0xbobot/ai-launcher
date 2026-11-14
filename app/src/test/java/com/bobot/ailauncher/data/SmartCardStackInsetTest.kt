package com.bobot.ailauncher.data

import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.smartcard.SmartCardStackSideMargin
import com.bobot.ailauncher.ui.smartcard.peekCardTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 智能卡片第十批单测（Bob 2026-11-08 真机 verdict：卡片是全宽，和桌面
 * 风格不协调；设计稿 §4.7）。
 *
 * 布局本身 JVM 测不到（Compose 排版），锁死的是数值一级：
 * ①两侧边距＝设置页卡片同一值 24.dp（SettingsScreen 全页
 * `padding(horizontal = 24.dp)` ＋ `RoundedCornerShape(20.dp)`；
 * 智能卡片圆角原本就是 20dp，所以只收边距这一处）；
 * ②露边卡形态（缩放/透明度/下移量）与改前逐字一致——露边方式不变，
 * 只是宽度跟着收边。
 * 按钮的落点、触控尺寸（≥48dp）是 SmartCardView 既有结构
 * （× 48dp、按钮 heightIn(min = 48.dp)），本批一字未动，
 * 见收据的 diff 复核＋Bob 真机验收。
 */
class SmartCardStackInsetTest {

    // ---------- 两侧边距：设置页卡片同一值 ----------

    @Test
    fun sideMargin_is24dp() {
        assertEquals(24.dp, SmartCardStackSideMargin)
    }

    @Test
    fun sideMargin_isOn8dpGrid() {
        // 24dp 是 8dp 网格的倍数（去 AI 味四条：新增间距优先取 8dp 倍数）。
        assertEquals(0, SmartCardStackSideMargin.value.toInt() % 8)
    }

    // ---------- 露边卡形态：与改前逐字一致 ----------

    @Test
    fun peekTransform_firstPeek() {
        val t = peekCardTransform(0)
        assertEquals(0.97f, t.scale, 0.001f)
        assertEquals(0.65f, t.alpha, 0.001f)
        assertEquals(8.dp, t.yOffset)
    }

    @Test
    fun peekTransform_secondPeek() {
        val t = peekCardTransform(1)
        assertEquals(0.94f, t.scale, 0.001f)
        assertEquals(0.40f, t.alpha, 0.001f)
        assertEquals(16.dp, t.yOffset)
    }

    @Test
    fun peekTransform_yOffsetStepsByOneGridPerCard() {
        // 每多露一张，下移量多一格 8dp；摞高＝顶卡高＋8dp×露边张数，不变。
        assertEquals(
            8.dp,
            peekCardTransform(1).yOffset - peekCardTransform(0).yOffset
        )
    }
}
