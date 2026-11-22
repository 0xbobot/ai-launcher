package com.bobot.ailauncher.ui.apps

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-11-22（Bob 实拍：宋体字母底边被截）：rail 行高锁死——
 * 12sp 字配 18sp、14sp 字配 20sp。改值须同步改四处调用点
 *（WaveRail/AppRail 默认值、三处显式传参）与本断言。
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
}
