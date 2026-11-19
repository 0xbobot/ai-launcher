package com.bobot.ailauncher.ui.apps

import com.bobot.ailauncher.data.AnalysisFailure
import com.bobot.ailauncher.data.DockSuggestion
import com.bobot.ailauncher.data.SuggestionAction
import com.bobot.ailauncher.data.SuggestionKind
import com.bobot.ailauncher.data.SuggestionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 说明页「建议」文案与行映射单测（v6 重设计，Bob 2026-11-16 拍板：
 * 建议嵌进各应用行右侧，标题行左「调一调程序坞」右建议按钮，
 * 「不想看的」静音行从这页拿掉）。
 * 仓库没有 Compose UI 测试 harness，这里锁死驱动文案与行映射的纯函数；
 * 区段排布（标题行按钮、建议动作在各应用行右侧）靠代码结构 + 真机验收。
 * 文件名沿用旧 DockSuggestionsSection（只改实现，不改名，减少 churn）。
 */
class DockSuggestionsSectionTest {

    // ---------- 按钮文案（Bob 2026-11-16 真机 verdict：
    // 用户看不懂要做什么——按钮要说出点了能得到什么） ----------

    @Test
    fun `analyze button says what you get not the mechanism`() {
        assertEquals("看看固定哪个、拿下哪个", suggestionAnalyzeButtonText(false))
    }

    @Test
    fun `analyze button keeps in-progress text`() {
        assertEquals("正在分析，稍等一下", suggestionAnalyzeButtonText(true))
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

    // ---------- v6：标题行按钮文案（分析前 / 分析中 / 分析后） ----------

    @Test
    fun `title button says what you get before any analysis`() {
        assertEquals(
            "看看固定哪个、拿下哪个",
            suggestionTitleButtonText(analyzing = false, hasSuggestions = false)
        )
    }

    @Test
    fun `title button keeps in-progress text while analyzing`() {
        assertEquals(
            "正在分析，稍等一下",
            suggestionTitleButtonText(analyzing = true, hasSuggestions = false)
        )
    }

    @Test
    fun `title button becomes re-analyze once suggestions are showing`() {
        assertEquals(
            "再分析一次",
            suggestionTitleButtonText(analyzing = false, hasSuggestions = true)
        )
    }

    @Test
    fun `title button in-progress wins over re-analyze`() {
        assertEquals(
            "正在分析，稍等一下",
            suggestionTitleButtonText(analyzing = true, hasSuggestions = true)
        )
    }

    // ---------- v6：行第二行与右侧动作 ----------

    private fun suggestion(
        kind: SuggestionKind,
        pkg: String,
        state: SuggestionState = SuggestionState.NEW,
        key: String = "x",
    ) = DockSuggestion(
        id = "1",
        kind = kind,
        title = "t",
        body = "b",
        evidence = emptyList(),
        action = SuggestionAction(kind.name.lowercase(), pkg),
        dedupeKey = key,
        state = state,
    )

    @Test
    fun `row second line is the suggestion reason when the app has one`() {
        assertEquals(
            "b",
            rowSecondLine(suggestion(SuggestionKind.PIN, "com.example.chat"), "你固定在程序坞上")
        )
    }

    @Test
    fun `row second line falls back to dock reason without a suggestion`() {
        assertEquals("近 7 天常开 11 次", rowSecondLine(null, "近 7 天常开 11 次"))
    }

    @Test
    fun `row action is none without a suggestion`() {
        assertEquals(RowSuggestionAction.NONE, rowSuggestionAction(null))
    }

    @Test
    fun `row action is pin adopt for a new pin suggestion`() {
        assertEquals(
            RowSuggestionAction.PIN_ADOPT,
            rowSuggestionAction(suggestion(SuggestionKind.PIN, "com.example.chat"))
        )
    }

    @Test
    fun `row action is unpin adopt for a new unpin suggestion`() {
        assertEquals(
            RowSuggestionAction.UNPIN_ADOPT,
            rowSuggestionAction(suggestion(SuggestionKind.UNPIN, "com.example.chat"))
        )
    }

    @Test
    fun `row action is observe ack for a new observe suggestion`() {
        assertEquals(
            RowSuggestionAction.OBSERVE_ACK,
            rowSuggestionAction(suggestion(SuggestionKind.OBSERVE, "com.example.chat"))
        )
    }

    @Test
    fun `row action is applied undo once adopted regardless of kind`() {
        listOf(SuggestionKind.PIN, SuggestionKind.UNPIN, SuggestionKind.OBSERVE).forEach { kind ->
            assertEquals(
                RowSuggestionAction.APPLIED_UNDO,
                rowSuggestionAction(
                    suggestion(kind, "com.example.chat", SuggestionState.APPLIED)
                )
            )
        }
    }

    @Test
    fun `adopt button names the action not a bare adopt`() {
        assertEquals("固定到程序坞上", rowAdoptButtonText(SuggestionKind.PIN))
        assertEquals("取消固定", rowAdoptButtonText(SuggestionKind.UNPIN))
        assertEquals("知道了", rowAdoptButtonText(SuggestionKind.OBSERVE))
    }

    // ---------- v6：建议分流行 ----------

    @Test
    fun `split maps suggestions onto their app rows`() {
        val (mapped, unmapped) = splitSuggestionsForRows(
            listOf(
                suggestion(SuggestionKind.PIN, "com.example.chat", key = "a"),
                suggestion(SuggestionKind.UNPIN, "com.example.video", key = "b"),
            ),
            listOf("com.example.chat", "com.example.video"),
        )
        assertEquals(2, mapped.size)
        assertEquals(SuggestionKind.PIN, mapped["com.example.chat"]?.kind)
        assertEquals(SuggestionKind.UNPIN, mapped["com.example.video"]?.kind)
        assertTrue(unmapped.isEmpty())
    }

    @Test
    fun `split sends observe with empty pkg to the fallback rows`() {
        val (mapped, unmapped) = splitSuggestionsForRows(
            listOf(suggestion(SuggestionKind.OBSERVE, "", key = "a")),
            listOf("com.example.chat"),
        )
        assertTrue(mapped.isEmpty())
        assertEquals(1, unmapped.size)
    }

    @Test
    fun `split sends suggestions for apps outside top10 to the fallback rows`() {
        val (mapped, unmapped) = splitSuggestionsForRows(
            listOf(suggestion(SuggestionKind.PIN, "com.example.outside", key = "a")),
            listOf("com.example.chat"),
        )
        assertTrue(mapped.isEmpty())
        assertEquals(1, unmapped.size)
    }

    @Test
    fun `split keeps only the first suggestion per app row`() {
        val (mapped, unmapped) = splitSuggestionsForRows(
            listOf(
                suggestion(SuggestionKind.PIN, "com.example.chat", key = "a"),
                suggestion(SuggestionKind.OBSERVE, "com.example.chat", key = "b"),
            ),
            listOf("com.example.chat"),
        )
        assertEquals(1, mapped.size)
        assertEquals(SuggestionKind.PIN, mapped["com.example.chat"]?.kind)
        assertEquals(1, unmapped.size)
        assertEquals(SuggestionKind.OBSERVE, unmapped[0].kind)
    }

    @Test
    fun `split of empty suggestions is empty both ways`() {
        val (mapped, unmapped) = splitSuggestionsForRows(emptyList(), listOf("com.example.chat"))
        assertTrue(mapped.isEmpty())
        assertTrue(unmapped.isEmpty())
    }
}
