package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.RiskLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
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
        var lastInput: String = ""
        var lastValidIds: Set<String> = emptySet()
        var lastHints: String = ""
        var lastContextPack: String = ""

        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String,
            contextPack: String
        ): PlanOutcome {
            calls++
            lastInput = input
            lastValidIds = validIds
            lastHints = hints
            lastContextPack = contextPack
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

    /** 挂起型 planner（意图改进一批·防重测试用）：进 plan 后停在 gate 上，
     * 让协调器稳定停留在 Planning 态，等测试放行才返回。 */
    private class BlockingPlanner(
        private val gate: CompletableDeferred<PlanOutcome>
    ) : IntentPlanner {
        var calls = 0

        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String,
            contextPack: String
        ): PlanOutcome {
            calls++
            return gate.await()
        }
    }

    /** 可换 gate 的挂起型 planner（F-06 取消测试用）：每轮提交换一只新 gate，
     * 模拟「取消后重新提交」时上一轮的迟到结果与新一轮互不干扰。 */
    private class SwitchableBlockingPlanner : IntentPlanner {
        var gate = CompletableDeferred<PlanOutcome>()
        var calls = 0

        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String,
            contextPack: String
        ): PlanOutcome {
            calls++
            return gate.await()
        }
    }

    private class FakeSpeaker : IntentSpeaker {
        val said = mutableListOf<String>()
        override fun say(text: String) {
            said += text
        }
    }

    private fun cap(
        id: String,
        risk: RiskLevel,
        params: List<com.bobot.ailauncher.data.CapabilityParam> = emptyList()
    ) = Capability(
        id = id,
        label = if (id == "daohang") "导航" else "打车",
        iconName = "",
        packageNames = listOf("com.example.taxi"),
        deeplinkTemplate = null,
        riskLevel = risk,
        params = params
    )

    private fun coordinator(
        config: FakeConfig = FakeConfig(),
        planner: IntentPlanner,
        capabilities: FakeCapabilities = FakeCapabilities(emptyMap()),
        executor: FakeExecutor = FakeExecutor(),
        speaker: FakeSpeaker = FakeSpeaker(),
        contextProvider: suspend () -> String = { "" }
    ) = IntentCoordinator(
        config, planner, capabilities, executor, speaker,
        contextProvider = contextProvider
    )

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

    // ---------- 8. 防重（意图改进一批）：Planning 在途的连点提交只规划一次 ----------

    @Test
    fun `submit while planning is ignored - planner called once`() = runTest {
        val gate = CompletableDeferred<PlanOutcome>()
        val planner = BlockingPlanner(gate)
        val c = coordinator(planner = planner)

        val first = async { c.submit("打开微信") }
        runCurrent()
        assertEquals(1, planner.calls)
        assertTrue(c.state.value is IntentUiState.Planning)

        // 在途期间连点第二次：守卫直接挡下——不进 planner、不改状态
        c.submit("再打开一次")
        assertEquals(1, planner.calls)
        assertTrue(c.state.value is IntentUiState.Planning)

        gate.complete(PlanOutcome.Chat("好的"))
        first.await()
        assertEquals(IntentUiState.Reply("好的"), c.state.value)
        assertEquals(1, planner.calls)
    }

    // ---------- 9. 防重（意图改进一批）：confirm 连点只执行一次 ----------

    @Test
    fun `confirm twice executes only once`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("dache", emptyMap(), null))
        val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.HIGH_RISK)))
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("帮我打车")
        val pending = (c.state.value as IntentUiState.NeedsConfirmation).request

        // 不是同一单的 confirm（旧弹窗迟到回调/冒充）：挡下，确认态保持
        c.confirm(pending.copy(intent = "别的事"))
        assertEquals(0, executor.realExecutions)
        assertTrue(c.state.value is IntentUiState.NeedsConfirmation)

        c.confirm(pending)
        // 连点第二次「执行」：确认态已离开（一次性守卫），不得重复执行
        c.confirm(pending)

        assertEquals(1, executor.realExecutions)
        assertEquals(2, executor.submitted.size)
        assertEquals(IntentUiState.Reply("已为你打开打车"), c.state.value)
    }

    // ---------- 10. 缺参追问（二批）：必填缺失/空白 → NeedsInput 且零执行 ----------

    private val navParam = com.bobot.ailauncher.data.CapabilityParam(
        name = "to", label = "目的地", required = true, ask = "导航去哪儿？"
    )

    @Test
    fun `missing required param asks and never executes`() = runTest {
        // 模型一个参数都没给
        run {
            val planner = FakePlanner(PlanOutcome.Action("daohang", emptyMap(), null))
            val caps = FakeCapabilities(
                mapOf("daohang" to cap("daohang", RiskLevel.LOW_RISK, listOf(navParam)))
            )
            val executor = FakeExecutor()
            val speaker = FakeSpeaker()
            val c = coordinator(planner = planner, capabilities = caps, executor = executor, speaker = speaker)

            c.submit("导航")
            val s = c.state.value
            assertTrue(s is IntentUiState.NeedsInput)
            s as IntentUiState.NeedsInput
            assertEquals("导航去哪儿？", s.question)
            assertEquals("daohang", s.capabilityId)
            assertEquals("to", s.paramName)
            assertEquals("导航", s.originalText)
            // 一次都没碰执行器——假成功从源头断掉；七仔把问题说出来了
            assertTrue(executor.submitted.isEmpty())
            assertTrue(speaker.said.contains("导航去哪儿？"))
        }
        // 模型给了空串参数：同样算缺失（旧链路会抹占位符开空目的地）
        run {
            val planner = FakePlanner(PlanOutcome.Action("daohang", mapOf("to" to "  "), null))
            val caps = FakeCapabilities(
                mapOf("daohang" to cap("daohang", RiskLevel.LOW_RISK, listOf(navParam)))
            )
            val executor = FakeExecutor()
            val c = coordinator(planner = planner, capabilities = caps, executor = executor)

            c.submit("导航一下")
            assertTrue(c.state.value is IntentUiState.NeedsInput)
            assertTrue(executor.submitted.isEmpty())
        }
    }

    // ---------- 11. 补参（二批）：下一句合并执行一次且参数正确，不再过 planner ----------

    @Test
    fun `filling the param merges and executes once without replanning`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("daohang", emptyMap(), null))
        val caps = FakeCapabilities(
            mapOf("daohang" to cap("daohang", RiskLevel.LOW_RISK, listOf(navParam)))
        )
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("导航")
        assertTrue(c.state.value is IntentUiState.NeedsInput)
        assertEquals(1, planner.calls)

        c.submit("机场")
        // 补参不重新规划；合并后执行一次，参数就是用户补的这句
        assertEquals(1, planner.calls)
        assertEquals(1, executor.submitted.size)
        val req = executor.submitted.single()
        assertEquals("daohang", req.capabilityId)
        assertEquals("机场", req.params["to"])
        assertEquals("导航", req.intent)
        assertEquals(1, executor.realExecutions)
        assertTrue(c.state.value is IntentUiState.Reply)
    }

    // ---------- 12. 多参（二批）：逐个问，齐了才执行 ----------

    @Test
    fun `multiple missing params are asked one by one`() = runTest {
        val lat = com.bobot.ailauncher.data.CapabilityParam(
            name = "lat", label = "目的地纬度", required = true, ask = "目的地纬度是多少？"
        )
        val lon = com.bobot.ailauncher.data.CapabilityParam(
            name = "lon", label = "目的地经度", required = true, ask = "目的地经度是多少？"
        )
        val planner = FakePlanner(PlanOutcome.Action("ditie", emptyMap(), null))
        val caps = FakeCapabilities(
            mapOf("ditie" to cap("ditie", RiskLevel.LOW_RISK, listOf(lat, lon)))
        )
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("坐地铁去机场")
        var s = c.state.value
        assertTrue(s is IntentUiState.NeedsInput)
        assertEquals("lat", (s as IntentUiState.NeedsInput).paramName)

        c.submit("31.2")
        s = c.state.value
        assertTrue(s is IntentUiState.NeedsInput)
        assertEquals("lon", (s as IntentUiState.NeedsInput).paramName)
        // 还差一个：仍然零执行
        assertTrue(executor.submitted.isEmpty())

        c.submit("121.5")
        assertEquals(1, executor.submitted.size)
        val req = executor.submitted.single()
        assertEquals("31.2", req.params["lat"])
        assertEquals("121.5", req.params["lon"])
        assertTrue(c.state.value is IntentUiState.Reply)
    }

    // ---------- 13. dismiss 清 pending（二批）：关掉追问后下一句是全新提交 ----------

    @Test
    fun `dismiss clears pending input - next submit is a fresh plan`() = runTest {
        val planner = FakePlanner(PlanOutcome.Action("daohang", emptyMap(), null))
        val caps = FakeCapabilities(
            mapOf("daohang" to cap("daohang", RiskLevel.LOW_RISK, listOf(navParam)))
        )
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        c.submit("导航")
        assertTrue(c.state.value is IntentUiState.NeedsInput)

        c.dismiss()
        // pending 已清：下一句走 planner 全新规划，而不是被当成目的地补进导航
        planner.outcome = PlanOutcome.Chat("好的")
        c.submit("今天天气怎么样")
        assertEquals(2, planner.calls)
        assertEquals("今天天气怎么样", planner.lastInput)
        assertEquals(IntentUiState.Reply("好的"), c.state.value)
        assertTrue(executor.submitted.isEmpty())
    }

    // ---------- 14. 上下文包（二批）：contextProvider 的产物原样传给 planner ----------

    @Test
    fun `context pack from provider is passed to planner`() = runTest {
        val planner = FakePlanner(PlanOutcome.Chat("你好"))
        val c = coordinator(
            planner = planner,
            contextProvider = { "【现在】2026年10月21日 星期三 22:20（晚上）" }
        )
        c.submit("你好")
        assertEquals("【现在】2026年10月21日 星期三 22:20（晚上）", planner.lastContextPack)

        // 采集抛异常也不拖垮提交：空包照常规划
        val planner2 = FakePlanner(PlanOutcome.Chat("你好"))
        val c2 = coordinator(
            planner = planner2,
            contextProvider = { throw RuntimeException("采集失败") }
        )
        c2.submit("你好")
        assertEquals("", planner2.lastContextPack)
        assertEquals(IntentUiState.Reply("你好"), c2.state.value)
    }

    // ---------- 15. 取消（F-06）：Planning 中取消 -> 回 Idle，迟到的回复被丢弃 ----------

    @Test
    fun `cancel planning returns to idle and drops late reply`() = runTest {
        val planner = SwitchableBlockingPlanner()
        val speaker = FakeSpeaker()
        val c = coordinator(planner = planner, speaker = speaker)

        val inFlight = async { c.submit("打开微信") }
        runCurrent()
        assertTrue(c.state.value is IntentUiState.Planning)

        c.cancelPlanning()
        // 立即收回 Idle（结果条收起），不等 planner 回来
        assertEquals(IntentUiState.Idle, c.state.value)

        // planner 的迟到结果到达：不得上屏、不得让七仔说出来
        planner.gate.complete(PlanOutcome.Chat("迟到的回复"))
        inFlight.await()
        assertEquals(IntentUiState.Idle, c.state.value)
        assertTrue(speaker.said.isEmpty())
    }

    // ---------- 16. 取消（F-06）：迟到的动作结果被丢弃，执行器一次都不碰 ----------

    @Test
    fun `cancel planning drops late action - executor never called`() = runTest {
        val planner = SwitchableBlockingPlanner()
        val caps = FakeCapabilities(mapOf("dache" to cap("dache", RiskLevel.READ)))
        val executor = FakeExecutor()
        val c = coordinator(planner = planner, capabilities = caps, executor = executor)

        val inFlight = async { c.submit("打车去机场") }
        runCurrent()
        assertTrue(c.state.value is IntentUiState.Planning)
        c.cancelPlanning()

        // 迟到的是一个可执行动作：取消后绝不能因为它的到达而真执行
        planner.gate.complete(PlanOutcome.Action("dache", mapOf("to" to "机场"), null))
        inFlight.await()
        assertEquals(IntentUiState.Idle, c.state.value)
        assertTrue(executor.submitted.isEmpty())
        assertEquals(0, executor.realExecutions)
    }

    // ---------- 17. 取消（F-06）：非 Planning 态取消是无操作；取消后可正常重提 ----------

    @Test
    fun `cancel outside planning is no-op and resubmit after cancel works`() = runTest {
        val planner = SwitchableBlockingPlanner()
        val c = coordinator(planner = planner)

        // Idle 态点取消：无操作
        c.cancelPlanning()
        assertEquals(IntentUiState.Idle, c.state.value)

        // 第一轮：提交 -> 取消 -> 迟到结果丢弃
        val first = async { c.submit("第一句") }
        runCurrent()
        c.cancelPlanning()
        planner.gate.complete(PlanOutcome.Chat("第一句的迟到回复"))
        first.await()
        assertEquals(IntentUiState.Idle, c.state.value)

        // 第二轮：换新 gate 正常提交，结果照常落地（代际只作废被取消的那一轮）
        planner.gate = CompletableDeferred()
        val second = async { c.submit("第二句") }
        runCurrent()
        assertTrue(c.state.value is IntentUiState.Planning)
        planner.gate.complete(PlanOutcome.Chat("第二句的回复"))
        second.await()
        assertEquals(IntentUiState.Reply("第二句的回复"), c.state.value)

        // Reply 态点取消：不得把已呈现的回复误清掉
        c.cancelPlanning()
        assertEquals(IntentUiState.Reply("第二句的回复"), c.state.value)
    }
}
