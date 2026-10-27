package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.CalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.68.164 过期日程修复 A：选会与标签状态的纯函数回归锁。
 * 覆盖：进行中／未来／已结束三态、分钟边界翻转、今日多场会排序、
 * 以及 2026-10-27 实拍回归（16:00–17:00 的会，17:30 不得再被选中）。
 * 时间比较只用绝对毫秒数，与时区无关。
 */
class NextEventLogicTest {

    private val hour = 3600_000L
    private val min = 60_000L
    // 任意基准日 09:00（绝对毫秒，仅用于相对比较）。
    private val base = 1_790_000_000_000L

    private fun t(h: Int, m: Int = 0) = base + h * hour + m * min

    private fun event(title: String, beginH: Int, beginM: Int, endH: Int, endM: Int) =
        CalEvent(
            title = title,
            begin = t(beginH, beginM),
            end = t(endH, endM),
            location = "",
            allDay = false
        )

    @Test
    fun selectNextEvent_skipsEndedAndPicksFirstNotEnded() {
        val events = listOf(
            event("已结束", 9, 0, 10, 0),
            event("进行中", 10, 0, 11, 0),
            event("未来", 14, 0, 15, 0)
        )
        // 10:30：第一场已结束，第二场进行中 → 选中进行中那场。
        assertEquals("进行中", selectNextEvent(events, t(10, 30))?.title)
    }

    @Test
    fun selectNextEvent_allEnded_returnsNull() {
        val events = listOf(
            event("上午会", 9, 0, 10, 0),
            event("午会", 11, 0, 12, 0)
        )
        assertNull(selectNextEvent(events, t(13, 0)))
    }

    @Test
    fun selectNextEvent_sortsByBeginRegardlessOfInputOrder() {
        val events = listOf(
            event("晚场", 18, 0, 19, 0),
            event("早场", 9, 0, 10, 0)
        )
        assertEquals("早场", selectNextEvent(events, t(8, 0))?.title)
    }

    @Test
    fun selectNextEvent_regression_2026_10_27_case() {
        // 实拍：16:00–17:00 的会，17:30 必须不再被选中（快照过期主因）。
        val events = listOf(event("Ops Innovation周会", 16, 0, 17, 0))
        assertNull(selectNextEvent(events, t(17, 30)))
        // 同一场在 16:30（进行中）仍应被选中。
        assertEquals("Ops Innovation周会", selectNextEvent(events, t(16, 30))?.title)
    }

    @Test
    fun meetingStatus_threeStates() {
        // 未来：now < begin。
        assertEquals(MeetingStatus.UPCOMING, meetingStatus(t(16, 0), t(17, 0), t(15, 59)))
        // 进行中：begin <= now < end。
        assertEquals(MeetingStatus.ONGOING, meetingStatus(t(16, 0), t(17, 0), t(16, 30)))
        // 已结束：now >= end。
        assertEquals(MeetingStatus.ENDED, meetingStatus(t(16, 0), t(17, 0), t(17, 30)))
    }

    @Test
    fun meetingStatus_minuteBoundaryFlips() {
        // 开始时刻整点翻转：15:59 未来 → 16:00 进行中。
        assertEquals(MeetingStatus.UPCOMING, meetingStatus(t(16, 0), t(17, 0), t(15, 59)))
        assertEquals(MeetingStatus.ONGOING, meetingStatus(t(16, 0), t(17, 0), t(16, 0)))
        // 结束时刻整点翻转：16:59 进行中 → 17:00 已结束（选会 end > now 同口径）。
        assertEquals(MeetingStatus.ONGOING, meetingStatus(t(16, 0), t(17, 0), t(16, 59)))
        assertEquals(MeetingStatus.ENDED, meetingStatus(t(16, 0), t(17, 0), t(17, 0)))
        // 选会侧同一边界：17:00 整点跳过已结束场、选中下一场。
        val events = listOf(
            event("第一场", 16, 0, 17, 0),
            event("第二场", 18, 0, 19, 0)
        )
        assertEquals("第二场", selectNextEvent(events, t(17, 0))?.title)
    }
}
