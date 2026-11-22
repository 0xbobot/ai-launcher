package com.bobot.ailauncher.ui.apps

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-22（Bob 实拍：rail 字母底边被截，真修）：
 * 565 锁错了变量——它断言行高值 18/20sp，以为“行高给足”就是修法，
 * 结果行高 16→20sp 反而让字更往下移、裁得更深（分析报告
 * ~/workspace/ai-launcher/rail-letter-clip-analysis.md §3、§5）。
 * 真正的裁剪层是 Text 自身的实测高度：weight(1f) 槽位把 Text 节点
 * 压到槽位高（横屏约 11dp），段落高 = 行高，字形下半部分落在节点
 * 之外被裁。修法是给字母 Text 一个显式高度 Modifier.height(lineHeight)，
 * 让节点高 = 行高（WaveRail.kt 字母 Text 处），不要再动行高。
 *
 * 本文件锁的是修法依赖的不变量（行高值 + 行高须够宋体自然行高），
 * 不是修法本身。布局级断言（节点实测高 == 行高、didOverflowHeight
 * == false） 在本仓库的单测源集里做不了：无 Robolectric、无 compose
 * ui-test 依赖、无 app/src/androidTest，CI 只跑 JVM 单测；显式高度
 * 修饰符的存在性由代码检查锁定，见 WaveRail.kt 字母 Text 处。
 * 另注：曾试过用 Modifier.foldIn 找 LayoutModifier 来断言“显式高度
 * 修饰符存在”，在 Compose 1.7.2 下测不到（height() 的实现不以
 * LayoutModifier 元素暴露给 foldIn），断言恒失败，已删——不要再加。
 */
class RailLetterLineHeightTest {

    @Test
    fun `12sp font pairs with 18sp line height`() {
        assertEquals(18.sp, RAIL_LETTER_LINE_HEIGHT_12SP)
    }

    @Test
    fun `14sp font pairs with 20sp line height`() {
        assertEquals(20.sp, RAIL_LETTER_LINE_HEIGHT_14SP)
    }

    @Test
    fun `line height covers Noto Serif natural line height for 12sp font`() {
        // 宋体自然行高 1.362em（fonttools 读 NotoSerif-Regular.ttf，
        // 见分析报告 §4）：12sp 字须 ≥ 16.34sp 行高，字形才装得进
        // 段落；18sp 有余量。行高低于此值会重新在段落内裁字。
        assertTrue(RAIL_LETTER_LINE_HEIGHT_12SP.value >= 12f * 1.362f)
    }

    @Test
    fun `line height covers Noto Serif natural line height for 14sp font`() {
        // 14sp 字须 ≥ 19.07sp 行高；20sp 有余量。
        assertTrue(RAIL_LETTER_LINE_HEIGHT_14SP.value >= 14f * 1.362f)
    }
}
