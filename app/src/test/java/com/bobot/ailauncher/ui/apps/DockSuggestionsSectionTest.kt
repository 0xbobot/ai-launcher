package com.bobot.ailauncher.ui.apps

import com.bobot.ailauncher.data.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 说明页「建议」区文案单测（Bob 2026-11-15 亲定新版：放顶、加白话、
 * 类别改名）。仓库没有 Compose UI 测试 harness，这里锁死驱动文案的
 * 纯函数/常量；区段排布（建议区在应用行之上）靠代码结构——建议区已
 * 抽成 DockSuggestionsSection，说明页 Column 里它在 top10 应用行
 * 之前调用——+ 真机验收。
 */
class DockSuggestionsSectionTest {

    // ---------- 类别展示名 ----------

    @Test
    fun `kind labels are plain words`() {
        assertEquals("固定", suggestionKindLabel(SuggestionKind.PIN))
        assertEquals("取消固定", suggestionKindLabel(SuggestionKind.UNPIN))
        assertEquals("习惯提醒", suggestionKindLabel(SuggestionKind.OBSERVE))
    }

    @Test
    fun `no kind is ever called mode observation`() {
        SuggestionKind.entries.forEach { kind ->
            assertFalse(suggestionKindLabel(kind).contains("模式观察"))
        }
    }

    // ---------- 白话说明与静默引导语 ----------

    @Test
    fun `plain intro is locked verbatim`() {
        assertEquals(
            "点一下，我看看你最近怎么用这些应用，告诉你哪个值得固定上来、哪个可以拿下去，采不采纳你说了算。",
            SUGGESTION_PLAIN_INTRO
        )
    }

    @Test
    fun `mute lead is locked verbatim and drops the old wording`() {
        assertEquals(
            "点一下，这类建议以后就不出现了，再点恢复。",
            SUGGESTION_MUTE_LEAD
        )
        assertFalse(SUGGESTION_MUTE_LEAD.contains("不再提示"))
    }
}
