package com.bobot.ailauncher.ui.apps

import com.bobot.ailauncher.data.AnalysisFailure
import com.bobot.ailauncher.data.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 说明页「建议」区文案单测（Bob 2026-11-15 亲定新版：放顶、加白话、
 * 类别改名；同日真机 verdict 精炼：一句话介绍删了，只留标题 + 按钮 +
 * 状态 + 静音 chip；点完失败要说清是哪种原因、不是一句「没分析出来」；
 * 2026-11-16 真机 verdict：用户看不懂要做什么——按钮说结果不说机制，
 * 静音行加「不想看的：」标签）。
 * 仓库没有 Compose UI 测试 harness，这里锁死驱动文案的纯函数；区段排布
 * （建议区在应用行之上）靠代码结构——建议区已抽成 DockSuggestionsSection，
 * 说明页 Column 里它在 top10 应用行之前调用——+ 真机验收。
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

    // ---------- 按钮文案与静音行标签（Bob 2026-11-16 真机 verdict：
    // 用户看不懂要做什么——按钮要说出点了能得到什么，静音行要有标签） ----------

    @Test
    fun `analyze button says what you get not the mechanism`() {
        assertEquals("看看固定哪个、拿下哪个", suggestionAnalyzeButtonText(false))
    }

    @Test
    fun `analyze button keeps in-progress text`() {
        assertEquals("正在分析，稍等一下", suggestionAnalyzeButtonText(true))
    }

    @Test
    fun `mute row label says which kinds it turns off`() {
        assertEquals("不想看的：", SUGGESTION_MUTE_LABEL)
    }

    @Test
    fun `ai intro says ai analyzes usage and advises dock changes`() {
        assertEquals("AI 分析你的使用行为，给程序坞提调整建议。", SUGGESTION_AI_INTRO)
    }

    // ---------- 失败文案（每种原因各一句话） ----------

    @Test
    fun `failure text says plainly when it cannot connect`() {
        assertEquals(
            "没连上，检查一下网络再点一次",
            analysisFailureText(AnalysisFailure.Network)
        )
    }

    @Test
    fun `failure text says key is wrong for 401`() {
        assertEquals(
            "大模型 Key 不对，去设置里重新填一下",
            analysisFailureText(AnalysisFailure.Unauthorized)
        )
    }

    @Test
    fun `failure text says balance is out for 402`() {
        assertEquals(
            "大模型余额不足，充一点再试",
            analysisFailureText(AnalysisFailure.InsufficientBalance)
        )
    }

    @Test
    fun `failure text says too frequent for 429`() {
        assertEquals(
            "调用太频繁，稍后再试",
            analysisFailureText(AnalysisFailure.RateLimited)
        )
    }

    @Test
    fun `failure text for other http failures stays plain`() {
        assertEquals(
            "这次没分析出来，稍后再试",
            analysisFailureText(AnalysisFailure.Generic)
        )
    }

    @Test
    fun `failure text says model returned nothing usable on parse failure`() {
        assertEquals(
            "模型没回有效结果，稍后再试",
            analysisFailureText(AnalysisFailure.ParseFailed)
        )
    }

    @Test
    fun `failure text says suggestions did not match records when verify drops all`() {
        assertEquals(
            "模型给的建议对不上你的使用记录，稍后再试",
            analysisFailureText(AnalysisFailure.VerifiedEmpty)
        )
    }

    @Test
    fun `failure text says fill in the key when there is no key`() {
        assertEquals(
            "去设置里填好大模型 Key 再试",
            analysisFailureText(AnalysisFailure.NoKey)
        )
    }

    @Test
    fun `failure texts never mention http codes`() {
        listOf(
            AnalysisFailure.Network,
            AnalysisFailure.Unauthorized,
            AnalysisFailure.InsufficientBalance,
            AnalysisFailure.RateLimited,
            AnalysisFailure.Generic,
            AnalysisFailure.ParseFailed,
            AnalysisFailure.VerifiedEmpty,
            AnalysisFailure.NoKey,
        ).forEach { failure ->
            val text = analysisFailureText(failure)
            assertFalse(text.contains("401"))
            assertFalse(text.contains("402"))
            assertFalse(text.contains("429"))
            assertFalse(text.contains("HTTP"))
        }
    }
}
