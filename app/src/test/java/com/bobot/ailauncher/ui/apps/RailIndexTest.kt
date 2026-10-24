package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 应用中心升级（Bob 2026-10-24 定稿 P0-3/P0-5）：rail 触控行映射。
 * 背景：分隔线 weight 0.5f 时布局行高不均、而旧 indexAt 按均匀行高算，
 * 误差随分隔线累积到底部约 1.5 行（~16dp），足以选错字母；weight 改 1f
 * 后布局行高均匀，本纯函数即视觉行高。另锁 4dp 死区滞后语义。
 */
class RailIndexTest {

    // rail：顶边 100px、高 1000px、20 项 → 行高 50px

    @Test
    fun railIndexAt_uniformMapping() {
        assertEquals(0, railIndexAt(100f, 100f, 1000f, 20))
        assertEquals(0, railIndexAt(149f, 100f, 1000f, 20))
        assertEquals(1, railIndexAt(150f, 100f, 1000f, 20))
        assertEquals(9, railIndexAt(599f, 100f, 1000f, 20))
        assertEquals(10, railIndexAt(600f, 100f, 1000f, 20))
        assertEquals(19, railIndexAt(1099f, 100f, 1000f, 20))
    }

    @Test
    fun railIndexAt_clampsOutOfRange() {
        assertEquals(0, railIndexAt(50f, 100f, 1000f, 20))
        assertEquals(19, railIndexAt(5000f, 100f, 1000f, 20))
    }

    @Test
    fun railIndexAt_emptyOrZeroSize() {
        assertEquals(0, railIndexAt(200f, 100f, 1000f, 0))
        assertEquals(0, railIndexAt(200f, 100f, 0f, 20))
        assertEquals(0, railIndexAt(200f, 100f, -10f, 20))
    }

    @Test
    fun deadZone_holdsWithinBoundary() {
        // 从行 1 下滑到行 2 边界附近：边界 200px，±4px 内不切换
        assertEquals(1, railIndexAtWithDeadZone(202f, 100f, 1000f, 20, 1, 4f))
        assertEquals(2, railIndexAtWithDeadZone(197f, 100f, 1000f, 20, 2, 4f))
    }

    @Test
    fun deadZone_switchesBeyondBoundary() {
        // 越过边界 4px 才切换
        assertEquals(2, railIndexAtWithDeadZone(205f, 100f, 1000f, 20, 1, 4f))
        assertEquals(1, railIndexAtWithDeadZone(195f, 100f, 1000f, 20, 2, 4f))
    }

    @Test
    fun deadZone_sameRowAndNoLastIndex() {
        // 同行内晃动不触发；lastIndex < 0 时无滞后、直接给 raw
        assertEquals(3, railIndexAtWithDeadZone(260f, 100f, 1000f, 20, 3, 4f))
        assertEquals(5, railIndexAtWithDeadZone(352f, 100f, 1000f, 20, -1, 4f))
    }
}
