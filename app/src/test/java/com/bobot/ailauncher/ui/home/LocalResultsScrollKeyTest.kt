package com.bobot.ailauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 结果行滚动复位 key（2026-10-30，385 实拍对齐修复）：
 * [localResultsScrollKey] 必须在结果集变化时变化、在结果集不变时
 * 稳定——LocalResultsBar 以它为 key()，结果一变整行重建、滚动归零，
 * 首项永远完整可见；同一结果集内用户手动滑动不受影响。
 */
class LocalResultsScrollKeyTest {

    @Test
    fun samePackages_sameOrder_sameKey() {
        assertEquals(
            localResultsScrollKey(listOf("com.tencent.mm", "com.microsoft.launcher")),
            localResultsScrollKey(listOf("com.tencent.mm", "com.microsoft.launcher"))
        )
    }

    @Test
    fun differentPackages_differentKey() {
        assertNotEquals(
            localResultsScrollKey(listOf("com.tencent.mm", "com.microsoft.launcher")),
            localResultsScrollKey(listOf("com.tencent.mm", "com.android.documentsui"))
        )
    }

    @Test
    fun differentOrder_differentKey() {
        assertNotEquals(
            localResultsScrollKey(listOf("a.pkg", "b.pkg")),
            localResultsScrollKey(listOf("b.pkg", "a.pkg"))
        )
    }

    @Test
    fun emptyList_emptyKey() {
        assertEquals("", localResultsScrollKey(emptyList()))
    }
}
