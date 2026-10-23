package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.CapabilityParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.101（主页重构 R-2）：槽位模板纯逻辑——模板构建（schema 驱动）、
 * 填充/拦截/拼句、会议提醒派生、示例集顺序与上限。
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

    // ---------- 填充与拼句 ----------

    @Test
    fun fill_defaultsPrefilled_composedTextUsesValues() {
        val fill = SlotTemplates.taxiTemplate(dacheNoParams).initialFill()
        // 默认值预置即已填（实心芯片语义），无空必填
        assertNull(fill.firstEmptyRequired())
        assertEquals("打车去机场", fill.composedText())
        val changed = fill.withValue("to", "火车站")
        assertEquals("打车去火车站", changed.composedText())
    }

    @Test
    fun fill_firstEmptyRequired_followsPartOrder_andClearReopens() {
        val now = 1_700_000_000_000L
        val t = SlotTemplates.meetingReminder(event(now + 7_200_000L, "机场T3"), now)!!
        var fill = t.initialFill().withValue("time", null).withValue("dest", null)
        // 时间槽在目的地之前声明 → 拦截落点先是时间
        assertEquals("time", fill.firstEmptyRequired()?.param)
        fill = fill.withValue("time", "15:50")
        assertEquals("dest", fill.firstEmptyRequired()?.param)
        fill = fill.withValue("dest", "机场T3")
        assertNull(fill.firstEmptyRequired())
        assertEquals("提醒我15:50 出发去机场T3", fill.composedText())
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
}
