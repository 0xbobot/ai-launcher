package com.bobot.ailauncher.data

import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.smartcard.SmartCardStackSideMargin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 智能卡片第十批单测（Bob 2026-11-08 真机 verdict：卡片是全宽，和桌面
 * 风格不协调；设计稿 §4.7）。
 *
 * 布局本身 JVM 测不到（Compose 排版），锁死的是数值一级：
 * 两侧边距＝设置页卡片同一值 24.dp（SettingsScreen 全页
 * `padding(horizontal = 24.dp)` ＋ `RoundedCornerShape(20.dp)`；
 * 智能卡片圆角原本就是 20dp，所以只收边距这一处）。
 * 露边卡形态（peekCardTransform）已随第三十三批单卡槽位作废——
 * 叠层不再叠放、不再露边，露边三项单测一并删掉。
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
}
