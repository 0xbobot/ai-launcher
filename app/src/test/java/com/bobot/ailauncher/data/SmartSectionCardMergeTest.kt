package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.settings.smartSectionRowsVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能区合并卡两行展开态（2026-11-14 Bob 定：来源卡与大模型卡合成一张卡、
 * 两行、中间细分隔线；两行各展开各的，互不干扰）。
 * 锁纯函数状态机 smartSectionRowsVisible：来源行收起与否走
 * effectiveSourcesCardCollapsed（没接满强制展开），大模型行展开与否就是
 * llmExpanded 原值；一行点按只翻转本行，另一行原样不动。
 * 组合行为（真机上点一行、看另一行不动）无 Compose UI harness，见收据验收项。
 */
class SmartSectionCardMergeTest {

    // 来源行点按：只翻转来源行，大模型行不动

    @Test
    fun togglingSourcesOnlyFlipsSourcesRow() {
        // 4/4 + 从未点过 + 大模型收起 → 来源收起、大模型收起
        var rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = null, llmExpanded = false
        )
        assertTrue(rows.sourcesCollapsed)
        assertFalse(rows.llmExpanded)
        // 点来源标题行展开（stored: null → false），大模型仍收起
        rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = false, llmExpanded = false
        )
        assertFalse(rows.sourcesCollapsed)
        assertFalse(rows.llmExpanded)
        // 同样点来源行，但大模型正展开着 → 大模型仍展开
        rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = false, llmExpanded = true
        )
        assertFalse(rows.sourcesCollapsed)
        assertTrue(rows.llmExpanded)
    }

    // 大模型行点按：只翻转大模型行，来源行不动

    @Test
    fun togglingLlmOnlyFlipsLlmRow() {
        // 4/4 + 从未点过 + 大模型收起 → 来源收起、大模型收起
        var rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = null, llmExpanded = false
        )
        assertTrue(rows.sourcesCollapsed)
        assertFalse(rows.llmExpanded)
        // 点大模型标题行展开，大模型展开、来源仍收起
        rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = null, llmExpanded = true
        )
        assertTrue(rows.sourcesCollapsed)
        assertTrue(rows.llmExpanded)
        // 再点大模型标题行收起，大模型收起、来源仍收起
        rows = smartSectionRowsVisible(
            allConnected = true, sourcesStoredCollapsed = null, llmExpanded = false
        )
        assertTrue(rows.sourcesCollapsed)
        assertFalse(rows.llmExpanded)
    }

    // 没接满：来源行强制展开，大模型行不受影响

    @Test
    fun notAllConnectedForcesSourcesExpandedButLlmUnaffected() {
        val rows = smartSectionRowsVisible(
            allConnected = false, sourcesStoredCollapsed = true, llmExpanded = true
        )
        assertFalse(rows.sourcesCollapsed)
        assertTrue(rows.llmExpanded)
    }

    // 穷举：任意组合下，来源行可见态恒等于 effectiveSourcesCardCollapsed，
    // 大模型行可见态恒等于传入的 llmExpanded——两行永不互相影响

    @Test
    fun bothRowsIndependentAcrossAllCombinations() {
        val storedValues: List<Boolean?> = listOf(null, true, false)
        for (allConnected in listOf(true, false)) {
            for (stored in storedValues) {
                for (llmExpanded in listOf(true, false)) {
                    val rows = smartSectionRowsVisible(allConnected, stored, llmExpanded)
                    assertEquals(
                        UiPrefs.effectiveSourcesCardCollapsed(allConnected, stored),
                        rows.sourcesCollapsed
                    )
                    assertEquals(llmExpanded, rows.llmExpanded)
                }
            }
        }
    }
}
