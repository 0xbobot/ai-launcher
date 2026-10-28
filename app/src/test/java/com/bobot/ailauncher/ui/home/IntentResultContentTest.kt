package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.intent.IntentUiState
import com.bobot.ailauncher.data.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 意图结果槽位在场判定（2026-10-28 意图页设计评审第 2 轮）：
 * [intentHasResultContent] 必须与 IntentEntry 内结果槽位 when() 逐项镜像——
 * 槽位渲染什么就返回 true，槽位空渲染就返回 false。收起并入与面板收短
 * 全靠这一判定：判错会在空输入态多出一段，或在结果在场时丢掉退出入口。
 */
class IntentResultContentTest {

    private fun has(
        state: IntentUiState,
        showDeckApps: Boolean = false,
        idleOrEditing: Boolean = true,
        committed: String = "",
        submitted: String = "",
        indexReady: Boolean = false,
        searching: Boolean = false,
    ): Boolean = intentHasResultContent(
        state = state,
        showDeckApps = showDeckApps,
        idleOrEditing = idleOrEditing,
        committedLiteral = committed,
        submittedText = submitted,
        localIndexReady = indexReady,
        showSearching = searching
    )

    @Test
    fun `权限追问思考三态恒有内容`() {
        assertTrue(has(IntentUiState.NeedsPermission("android.permission.CALL_PHONE", "需要电话权限")))
        assertTrue(has(IntentUiState.NeedsInput("去哪儿？", "nav.go", "destination", "导航")))
        assertTrue(has(IntentUiState.Planning("导航去机场")))
    }

    @Test
    fun `回复与错误恒有内容`() {
        assertTrue(has(IntentUiState.Reply("已为你打开地图")))
        assertTrue(has(IntentUiState.Error("网络不可用")))
    }

    @Test
    fun `本地结果在场时有内容`() {
        assertTrue(has(IntentUiState.Editing("微信"), showDeckApps = true, committed = "微信"))
    }

    @Test
    fun `空输入态无内容`() {
        assertFalse(has(IntentUiState.Idle))
        assertFalse(has(IntentUiState.Editing(""), committed = ""))
    }

    @Test
    fun `打字无结果时按索引与查找中判定`() {
        // 索引建好且确无匹配 → “没找到”行在场。
        assertTrue(has(IntentUiState.Editing("不存在的应用"), committed = "不存在的应用", indexReady = true))
        // 索引未建且延迟已到 → “正在查找应用”行在场。
        assertTrue(has(IntentUiState.Editing("微信"), committed = "微信", searching = true))
        // 索引未建、延迟未到 → 槽位空渲染。
        assertFalse(has(IntentUiState.Editing("微信"), committed = "微信"))
        // 提交后抑制态（keys == submittedText）→ 槽位空渲染。
        assertFalse(has(IntentUiState.Editing("微信"), committed = "微信", submitted = "微信", indexReady = true))
    }

    @Test
    fun `确认态不走本地结果分支`() {
        // NeedsConfirmation 走 AlertDialog，不占槽位；槽位本身只看状态行条件。
        val confirm = IntentUiState.NeedsConfirmation(
            ActionRequest(
                intent = "打电话给妈妈",
                capabilityId = "phone.call",
                riskLevel = RiskLevel.MEDIUM_RISK
            ),
            "确认打电话？"
        )
        assertFalse(has(confirm, showDeckApps = true, idleOrEditing = false))
        assertFalse(has(confirm, showDeckApps = true, committed = "打电话"))
    }
}
