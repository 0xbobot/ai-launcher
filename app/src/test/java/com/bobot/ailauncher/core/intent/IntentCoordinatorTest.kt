package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.RiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IntentCoordinator 纯逻辑单测（文档二 P0 测试方案）。
 * 全部依赖用假实现，不访问真实网络 / PackageManager / Android 运行时。
 */
class IntentCoordinatorTest {

    // ---------- 假实现 ----------

    private class FakeConfig(var key: Boolean = true) : IntentConfig {
        override fun hasKey(): Boolean = key
    }

    private class FakePlanner(var outcome: PlanOutcome) : IntentPlanner {
        var calls = 0
        var lastValidIds: Set<String> = emptySet()
        var lastHints: String = ""

        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String
        ): PlanOutcome {
            calls++
            lastValidIds = validIds
            lastHints = hints
            return outcome
        }
    }

    private class FakeCapabilities(
        private val caps: Map<String, Capability>
    ) : IntentCapabilities {
        var loadCalls = 0
        override fun ensureLoaded() {
            loadCalls++
        }

        override fun validIds(): Set<String> = caps.keys
        override fun hints(): String =
            caps.values.joinToString("\n") { "${it.id}: ${it.label}" }

        override fun find(id: String): Capability? = caps[id]
    }

    /** 复刻 ActionEngine 的安全门语义：需确认时只返回 NeedsConfirmation，不算真执行 */
    private class FakeExecutor(var forcedResult: ActionResult? = null) : IntentActionExecutor {
        val submitted = mutableListOf<ActionRequest>()
        var realExecutions = 0

        override fun submit(request: ActionRequest): ActionResult {
            submitted += request
            if (request.needsConfirmation) {
                return ActionResult.NeedsConfirmation(
                    request, "确定要执行吗？\n「${request.intent}」"
                )
            }
            forcedResult?.let { return it }
            realExecutions++
            return ActionResult.Done("已为你打开打车")
        }
    }

    private class FakeSpeaker : IntentSpeaker {
        val said = mutableListOf<String>()
        override fun say(text: String) {
            said += text
        }
    }

    private fun cap(id: String, risk: RiskLevel) = Capability(
        id = id,
        label = "打车",
        iconName = "",
        packageNames = listOf("com.example.taxi"),
        deeplinkTemplate = null,
        riskLevel = risk
    )

    private fun coordinator(
        config: FakeConfig = FakeConfig(),
        planner: FakePlanner,
        capabilities: FakeCapabilities = FakeCapabilities(emptyMap()),
        executor: FakeExecutor = FakeExecutor(),
        speaker: FakeSpeaker = FakeSpeaker()
    ) = IntentCoordinator(config, planner, capabilities, executor, speaker)

    // ---------- 1. 空输入不发请求 ----------

    @Test
    fun `empty input does not call planner`() = runTest {
        val planner = FakePlanner(PlanOutcome.Chat("不应到达"))
        val c = coordinator(planner = planner)
        c.submit("   ")
        assertEquals(0, planner.calls)
        assertEquals(IntentUiState.Idle, c.state.value)
    }

    // ---------- 2. 无 Key 进可恢复配置态 ----------

    @Test
    fun `no key goes to recoverable error state`() = runTest {
        val config = FakeConfig(key = false)
        val planner = FakePlanner(PlanOutcome.Chat("你好呀"))
        val speaker = FakeSpeaker()
        val c = coordinator(config = config, planner = planner, speaker = speaker)

        c.submit("你好")
        val s = c.state.value
        assertTrue(s is IntentUiState.Error)
        assertEquals(IntentCoordinator.NO_KEY_MESSAGE, (s as IntentUiState.Error).message)
        assertEquals(0, planner.calls)
        assertTrue(speaker.said.contains(IntentCoordinator.NO_KEY_MESSAGE))

        // 可恢复：配好 Key 后同一协调器可直接重提成功
        config.key = true
        c.submit("你好")
        assertEquals(IntentUiState.Reply("你好呀"), c.state.value)
    }

    // ---------- 3. Chat 只回复不启 App ----------

    @Test
    fun `chat replies without executing any action`() = runTest {
        val planner = FakePlanner(PlanOutcome.Chat("今天晴天"))
        val executor = FakeExecutor()
        val speaker = FakeSpeaker()
        val c = coordinator(planner = planner, executor = executor, speaker = speaker)

        c.submit("今天天气怎么样")
        assertEquals(IntentUiState.Reply("今天晴天"), c.state.value)
        assertTrue(speaker.said.contains("今天晴天"))
        assertTrue(executor.submitted.isEmpty())
    }

    // ---------- 4. 合法 Action 用注册表 riskLevel ----------

    @Test
    fun `action request uses risk level from registry`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("dache", mapOf("to" to "机场"), null))
        val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.READ)))
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("打车去机场")
        assertEquals(1, executor.submitted.size)
        val req = executor.submitted.single()
        assertEquals("dache", req.capabilityId)
        // 用注册表的 READ，而不是任何模型侧字段（模型结果里根本没有风险字段）
        assertEquals(RiskLevel.READ, req.riskLevel)
        assertEquals("机场", req.params["to"])
        assertTrue(c.state.value is IntentUiState.Reply)
    }

    // ---------- 5. 中/高风险首次提交进确认态 ----------

    @Test
    fun `medium risk first submit asks confirmation and does not execute`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("dache", emptyMap(), null))
        val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.MEDIUM_RISK)))
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("帮我打车")
        val s = c.state.value
        assertTrue(s is IntentUiState.NeedsConfirmation)
        s as IntentUiState.NeedsConfirmation
        assertEquals(RiskLevel.MEDIUM_RISK, s.request.riskLevel)
        assertEquals(false, s.request.confirmed)
        assertEquals(0, executor.realExecutions)
    }

    // ---------- 6. 确认后只执行一次且结果映射正确 ----------

    @Test
    fun `confirm executes exactly once and maps done to reply`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("dache", emptyMap(), null))
        val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.HIGH_RISK)))
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("帮我打车")
        val pending = (c.state.value as IntentUiState.NeedsConfirmation).request
        c.confirm(pending)

        assertEquals(2, executor.submitted.size)
        assertEquals(true, executor.submitted[1].confirmed)
        assertEquals(1, executor.realExecutions)
        assertEquals(IntentUiState.Reply("已为你打开打车"), c.state.value)
    }

    // ---------- 7. 非法 capability / 未安装 / deep link 失败不报成功 ----------

    @Test
    fun `illegal capability and execution failures never report success`() = runTest {
        // 7a 非法 capability：注册表里没有 → Error，执行器根本不该被调用
        run {
            val planner = FakePlanner(PlanOutcome.Action("nope", emptyMap(), null))
            val executor = FakeExecutor()
            val c = coordinator(planner = planner, executor = executor)
            c.submit("做一件不存在的事")
            assertTrue(c.state.value is IntentUiState.Error)
            assertTrue(executor.submitted.isEmpty())
        }
        // 7b 未安装：执行器如实 Failed → Error，不进 Reply
        run {
            val planner = FakePlanner(PlanOutcome.Action("dache", emptyMap(), null))
            val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.LOW_RISK)))
            val executor = FakeExecutor(
                forcedResult = ActionResult.Failed("本机没有安装支持「打车」的应用")
            )
            val c = coordinator(planner = planner, capabilities = caps, executor = executor)
            c.submit("帮我打车")
            val s = c.state.value
            assertTrue(s is IntentUiState.Error)
            assertEquals("本机没有安装支持「打车」的应用", (s as IntentUiState.Error).message)
        }
        // 7c deep link 失败：同样如实 Error，不报成功
        run {
            val planner = FakePlanner(PlanOutcome.Action("dache", emptyMap(), null))
            val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.LOW_RISK)))
            val executor = FakeExecutor(
                forcedResult = ActionResult.Failed("没能打开「打车」，换个说法试试")
            )
            val c = coordinator(planner = planner, capabilities = caps, executor = executor)
            c.submit("帮我打车去机场")
            assertTrue(c.state.value is IntentUiState.Error)
        }
    }
}
