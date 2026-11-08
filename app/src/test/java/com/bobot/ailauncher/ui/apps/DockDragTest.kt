package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dock 拖动排序步进纯逻辑单测（2026-11-08 收藏区拖动又失效：
 * 463 拆分把 dockDragOffsetY 从委托属性改成 Float 快照参数，
 * pointerInput 闭包读到陈旧 0f，targetIdx 恒等于 curIdx、永远换不了位。
 * 步进算式抽成纯函数 dockDragStep 锁死在这里；累计偏移本身由手势域内
 * 局部变量持有，不再读快照参数）。
 *
 * 行高 64f（60dp 行 + 4dp 间距），与生产代码同值。
 */
class DockDragTest {

    // ---------- 不够一行不动 ----------

    @Test
    fun `step - offset below one row stays`() {
        assertNull(dockDragStep(curIdx = 1, offsetY = 30f, rowH = 64f, size = 3))
        assertNull(dockDragStep(curIdx = 1, offsetY = -30f, rowH = 64f, size = 3))
        assertNull(dockDragStep(curIdx = 1, offsetY = 0f, rowH = 64f, size = 3))
    }

    @Test
    fun `step - already at end with outward offset stays`() {
        assertNull(dockDragStep(curIdx = 2, offsetY = 200f, rowH = 64f, size = 3))
        assertNull(dockDragStep(curIdx = 0, offsetY = -200f, rowH = 64f, size = 3))
    }

    // ---------- 下移 ----------

    @Test
    fun `step - one row down moves and rebates remainder`() {
        val (target, rebated) = dockDragStep(curIdx = 0, offsetY = 70f, rowH = 64f, size = 3)!!
        assertEquals(1, target)
        assertEquals(6f, rebated)
    }

    @Test
    fun `step - two rows down in one move`() {
        val (target, rebated) = dockDragStep(curIdx = 0, offsetY = 130f, rowH = 64f, size = 3)!!
        assertEquals(2, target)
        assertEquals(2f, rebated)
    }

    @Test
    fun `step - down clamps at last row, rebate keeps remainder`() {
        val (target, rebated) = dockDragStep(curIdx = 1, offsetY = 200f, rowH = 64f, size = 3)!!
        assertEquals(2, target)
        assertEquals(136f, rebated)
    }

    // ---------- 上移 ----------

    @Test
    fun `step - one row up moves and rebates remainder`() {
        val (target, rebated) = dockDragStep(curIdx = 1, offsetY = -70f, rowH = 64f, size = 3)!!
        assertEquals(0, target)
        assertEquals(-6f, rebated)
    }

    @Test
    fun `step - two rows up in one move`() {
        val (target, rebated) = dockDragStep(curIdx = 2, offsetY = -130f, rowH = 64f, size = 3)!!
        assertEquals(0, target)
        assertEquals(-2f, rebated)
    }

    @Test
    fun `step - up clamps at first row, rebate keeps remainder`() {
        val (target, rebated) = dockDragStep(curIdx = 1, offsetY = -200f, rowH = 64f, size = 3)!!
        assertEquals(0, target)
        assertEquals(-136f, rebated)
    }

    // ---------- 单行列表 ----------

    @Test
    fun `step - single row list never moves`() {
        assertNull(dockDragStep(curIdx = 0, offsetY = 500f, rowH = 64f, size = 1))
        assertNull(dockDragStep(curIdx = 0, offsetY = -500f, rowH = 64f, size = 1))
    }
}
