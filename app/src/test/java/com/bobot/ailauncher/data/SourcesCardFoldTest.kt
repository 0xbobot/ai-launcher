package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.settings.sourcesCardCollapsedHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能区来源卡收起态（2026-11-14 Bob 定：4 个源全接上时默认收起，只留
 * 一行摘要；没接满保持展开，方便接上剩下的。收起/展开记下来，下次进
 * 设置页还按上次的来；4/4 首次接满（从未点过）默认收起）。
 * 纯函数、JVM 可跑；持久化读写（get/setSourcesCardCollapsed）是
 * SharedPreferences 直通，照 UiPrefs 其余 getter/setter 同口径。
 */
class SourcesCardFoldTest {

    // 4/4 + 从未点过 → 默认收起

    @Test
    fun allConnectedNeverToggledDefaultsCollapsed() {
        assertTrue(UiPrefs.effectiveSourcesCardCollapsed(allConnected = true, storedCollapsed = null))
    }

    // 4/4 + 用户点过 → 按上次的来

    @Test
    fun allConnectedRespectsStoredChoice() {
        assertFalse(UiPrefs.effectiveSourcesCardCollapsed(allConnected = true, storedCollapsed = false))
        assertTrue(UiPrefs.effectiveSourcesCardCollapsed(allConnected = true, storedCollapsed = true))
    }

    // 没接满 → 一律展开，不管存过什么

    @Test
    fun notAllConnectedAlwaysExpanded() {
        assertFalse(UiPrefs.effectiveSourcesCardCollapsed(allConnected = false, storedCollapsed = null))
        assertFalse(UiPrefs.effectiveSourcesCardCollapsed(allConnected = false, storedCollapsed = true))
        assertFalse(UiPrefs.effectiveSourcesCardCollapsed(allConnected = false, storedCollapsed = false))
    }

    // 收起行描述（2026-11-14 Bob 定：收起加一行，告诉用户接上这些来源
    // 会起什么作用；一行写完，不带数字——数字已在标题行）

    @Test
    fun collapsedHintStatesEffectOfConnectedSources() {
        assertEquals("接上之后，桌面会主动提醒你该做的事。", sourcesCardCollapsedHint())
    }
}
