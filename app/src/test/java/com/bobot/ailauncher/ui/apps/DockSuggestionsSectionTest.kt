package com.bobot.ailauncher.ui.apps

import com.bobot.ailauncher.data.AnalysisFailure
import com.bobot.ailauncher.data.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 说明页「建议」区文案单测（Bob 2026-11-15 亲定新版：放顶、加白话、
 * 类别改名；同日真机 verdict 精炼：一句话介绍、静默引导语删了、点完
 * 失败/无候选要一眼看懂）。仓库没有 Compose UI 测试 harness，这里锁死
 * 驱动文案的纯函数/常量；区段排布（建议区在应用行之上）靠代码结构——
 * 建议区已抽成 DockSuggestionsSection，说明页 Column 里它在 top10
 * 应用行之前调用——+ 真机验收。
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

    // ---------- 白话说明（一句话）与失败文案 ----------

    @Test
    fun `plain intro is one sentence locked verbatim`() {
        assertEquals(
            "看看你最近怎么用这些应用，告诉你哪个值得固定、哪个可以拿下去。",
            SUGGESTION_PLAIN_INTRO
        )
    }

    @Test
    fun `plain intro does not tell the user to tap`() {
        // 按钮本身就是「分析一下」，不用再写「点一下」；也不加
        // 「采不采纳你说了算」这种尾巴（Bob 2026-11-15 真机 verdict：精炼）。
        assertFalse(SUGGESTION_PLAIN_INTRO.contains("点一下"))
        assertFalse(SUGGESTION_PLAIN_INTRO.contains("采不采纳"))
    }

    @Test
    fun `failure text says plainly when it cannot connect`() {
        assertEquals(
            "没连上，检查一下网络再点一次",
            analysisFailureText(AnalysisFailure.NETWORK)
        )
    }

    @Test
    fun `failure text for other failures stays plain`() {
        assertEquals(
            "这次没分析出来，稍后再试",
            analysisFailureText(AnalysisFailure.OTHER)
        )
    }
}
