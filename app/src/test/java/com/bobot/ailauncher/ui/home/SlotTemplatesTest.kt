package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.CapabilityParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.101（主页重构 R-2）建；F 批收敛：槽位模板纯逻辑——模板构建
 * （schema 驱动）、示例填充文案派生（exampleFillText）、会议提醒派生、
 * 示例集顺序与上限。R-2 的 TemplateFill 填充/拦截/拼句用例随槽位 UX
 * 退役删除（发送不再有拦截路径，缺参走协调器追问）。
 */
class SlotTemplatesTest {

    private fun cap(id: String, params: List<CapabilityParam>): Capability = Capability(
        id = id,
        label = id,
        iconName = "icon",
        packageNames = emptyList(),
        deeplinkTemplate = null,
        params = params
    )

    private val daohang = cap(
        "daohang",
        listOf(CapabilityParam("to", "目的地", required = true, ask = "导航去哪儿？"))
    )
    private val dacheNoParams = cap("dache", emptyList())

    private fun event(begin: Long, location: String): CalEvent = CalEvent(
        title = "ODF项目双周会",
        begin = begin,
        end = begin + 3_600_000L,
        location = location,
        allDay = false,
        eventId = 7L
    )

    // ---------- 模板构建：槽位来自 schema ----------

    @Test
    fun navTemplate_slotComesFromSchema() {
        val t = SlotTemplates.navTemplate(daohang)
        val slot = t.slots().single()
        assertEquals("to", slot.param)
        assertEquals("目的地", slot.label)
        assertEquals(SlotKind.DESTINATION, slot.kind)
        assertTrue(slot.required)
        assertEquals("daohang", t.capabilityId)
        assertEquals("导航去机场", t.chipText())
    }

    @Test
    fun template_schemaMissing_fallsBackToSameSlotDef() {
        // schema 被改没了也不能让模板失去槽位：同名同必填兜底
        val t = SlotTemplates.taxiTemplate(dacheNoParams)
        val slot = t.slots().single()
        assertEquals("to", slot.param)
        assertEquals("目的地", slot.label)
        assertTrue(slot.required)
    }

    // ---------- 示例填充文案派生（F 批：点选即整句填入输入框） ----------

    @Test
    fun exampleFillText_navAndTaxi_useDefaultDestination() {
        // 有默认值 → 代入默认值（与行上显示的 chipText 同句）
        assertEquals("导航去机场", SlotTemplates.navTemplate(daohang).exampleFillText())
        assertEquals("打车去机场", SlotTemplates.taxiTemplate(dacheNoParams).exampleFillText())
    }

    @Test
    fun exampleFillText_meetingReminder_fullSentenceFromEvent() {
        val now = 1_700_000_000_000L
        val begin = now + 2 * 3_600_000L
        val t = SlotTemplates.meetingReminder(event(begin, "公司大楼"), now)!!
        // 时间默认（开始前 30 分钟）与地点默认都代入，拼成可直接编辑的整句
        assertEquals(
            "提醒我${SlotTemplates.fmtHHmm(begin - 30 * 60_000L)} 出发去公司大楼",
            t.exampleFillText()
        )
    }

    @Test
    fun exampleFillText_slotWithoutDefault_fallsBackToSlotLabel() {
        // 无默认值的槽位 → 以槽位 label 原文呈现（整句仍成立、用户可改字）
        val t = SlotTemplate(
            id = "bare",
            capabilityId = null,
            parts = listOf(
                TemplatePart.Text("导航去"),
                TemplatePart.Slot(
                    SlotDef("to", "目的地", SlotKind.DESTINATION, required = true)
                )
            ),
            defaults = emptyMap()
        )
        assertEquals("导航去目的地", t.exampleFillText())
        // 空白默认值同样不算有默认
        val blankDefault = t.copy(defaults = mapOf("to" to "  "))
        assertEquals("导航去目的地", blankDefault.exampleFillText())
    }

    @Test
    fun exampleFillText_matchesChipTextForAllDeckExamples() {
        // 派生与行上文案永远同句：用户看到什么、点下去填入的就是什么
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExamples(
            daohang, dacheNoParams, event(now + 7_200_000L, "机场T3"), now
        )
        ex.forEach { assertEquals(it.chipText(), it.exampleFillText()) }
    }

    // ---------- 会议提醒派生 ----------

    @Test
    fun meetingReminder_defaultsAreDepartTimeAndLocation() {
        val now = 1_700_000_000_000L
        val begin = now + 2 * 3_600_000L
        val t = SlotTemplates.meetingReminder(event(begin, " 公司大楼 "), now)!!
        assertEquals(SlotTemplates.fmtHHmm(begin - 30 * 60_000L), t.defaults["time"])
        assertEquals("公司大楼", t.defaults["dest"])
        assertEquals("提醒我${t.defaults["time"]} 出发去公司大楼", t.chipText())
    }

    @Test
    fun meetingReminder_departLeadPast_fallsBackToBeginTime() {
        val now = 1_700_000_000_000L
        val begin = now + 10 * 60_000L // 10 分钟后开始，提前 30 分钟已过
        val t = SlotTemplates.meetingReminder(event(begin, "机场"), now)!!
        assertEquals(SlotTemplates.fmtHHmm(begin), t.defaults["time"])
    }

    @Test
    fun meetingReminder_noLocationOrPastEvent_returnsNull() {
        val now = 1_700_000_000_000L
        assertNull(SlotTemplates.meetingReminder(event(now + 3_600_000L, "  "), now))
        assertNull(SlotTemplates.meetingReminder(event(now - 1_000L, "机场"), now))
    }

    // ---------- 示例集 ----------

    @Test
    fun buildExamples_withMeeting_taxiReminderNav_capped3() {
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExamples(
            daohang, dacheNoParams, event(now + 7_200_000L, "机场T3"), now
        )
        assertEquals(listOf("taxi", "meeting-reminder", "nav"), ex.map { it.id })
    }

    @Test
    fun buildExamples_withoutMeeting_fallsBackToStaticTemplates() {
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExamples(daohang, dacheNoParams, null, now)
        assertEquals(listOf("taxi", "nav"), ex.map { it.id })
        // 无地点会议同样回落
        val ex2 = SlotTemplates.buildExamples(
            daohang, dacheNoParams, event(now + 7_200_000L, ""), now
        )
        assertEquals(listOf("taxi", "nav"), ex2.map { it.id })
    }

    // ---------- v6 扩展示例池 ----------

    @Test
    fun buildExpandedExamples_coversTravelWorkLifeSocialPay() {
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExpandedExamples(daohang, dacheNoParams, null, now)
        // 出行（打车/导航/航班/地铁/酒店）+ 办公（日程）+ 生活（外卖）
        // + 通讯（微信）+ 支付（支付宝），每条都有真实能力 id
        assertEquals(
            listOf(
                "taxi", "nav", "hangban", "rili", "meituan",
                "ditie", "wechat", "zhifubao", "jiudian"
            ),
            ex.map { it.id }
        )
        assertEquals(
            listOf(
                "dache", "daohang", "hangban", "rili", "meituan",
                "ditie", "wechat", "zhifubao", "jiudian"
            ),
            ex.map { it.capabilityId }
        )
        // 行上文案与点后填入同句（固定模板整句即文案）
        ex.forEach { assertEquals(it.chipText(), it.exampleFillText()) }
        assertEquals("查明天飞上海的航班", ex[2].exampleFillText())
        assertEquals("查我明天的日程", ex[3].exampleFillText())
    }

    @Test
    fun buildExpandedExamples_withMeeting_insertsReminderSecond() {
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExpandedExamples(
            daohang, dacheNoParams, event(now + 7_200_000L, "机场T3"), now
        )
        assertEquals("meeting-reminder", ex[1].id)
        assertNull(ex[1].capabilityId) // 上下文派生，背后无能力 schema
        assertEquals(10, ex.size)
    }

    @Test
    fun buildExpandedExamples_split_main5GuessRest() {
        // IntentEntry 切分口径：前 5 条主行、其余猜你想问行
        val now = 1_700_000_000_000L
        val ex = SlotTemplates.buildExpandedExamples(daohang, dacheNoParams, null, now)
        assertEquals(5, ex.take(5).size)
        assertEquals(4, ex.drop(5).size)
        assertTrue(ex.take(5).map { it.id }.contains("rili"))
        assertTrue(ex.drop(5).map { it.id }.contains("wechat"))
    }
}
