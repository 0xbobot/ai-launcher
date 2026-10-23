package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.SlotHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.68.101（主页重构 R-2）：槽位选项生成（目的地三源 + 时间）与历史合并。
 */
class SlotOptionsTest {

    @Test
    fun destinationOptions_orderCurrentMeetingHistoryDefault_dedup() {
        val opts = SlotOptions.destinationOptions(
            current = "火车站",
            meetingLocation = "机场T3",
            history = listOf("火车站", "老家", "机场T3"),
            templateDefault = "机场"
        )
        // 当前值置顶；会议地点次之；历史去重（火车站/机场T3 已出现）；默认垫底
        assertEquals(listOf("火车站", "机场T3", "老家", "机场"), opts)
    }

    @Test
    fun destinationOptions_blanksDropped_andCapApplied() {
        val opts = SlotOptions.destinationOptions(
            current = " ",
            meetingLocation = null,
            history = listOf("A", "B", "C", "D", "E", "F", "G"),
            templateDefault = null,
            max = 3
        )
        assertEquals(listOf("A", "B", "C"), opts)
    }

    @Test
    fun destinationOptions_onlyDefault_whenNoContext() {
        val opts = SlotOptions.destinationOptions(
            current = null, meetingLocation = null,
            history = emptyList(), templateDefault = "机场"
        )
        assertEquals(listOf("机场"), opts)
    }

    @Test
    fun timeOptions_meetingDepartFirst_thenRelativeSet() {
        val depart = 1_700_000_000_000L
        val opts = SlotOptions.timeOptions(depart)
        assertEquals(SlotTemplates.fmtHHmm(depart), opts.first())
        assertEquals(
            listOf("15 分钟后", "半小时后", "1 小时后", "明早 9 点"),
            opts.drop(1)
        )
    }

    @Test
    fun timeOptions_noMeeting_relativeOnly() {
        assertEquals(
            listOf("15 分钟后", "半小时后", "1 小时后", "明早 9 点"),
            SlotOptions.timeOptions(null)
        )
    }

    @Test
    fun mergeHistory_newOnTop_dedup_cap() {
        val merged = SlotHistoryStore.mergeHistory(
            listOf("B", "A", "C"), " A "
        )
        assertEquals(listOf("A", "B", "C"), merged)
        val capped = SlotHistoryStore.mergeHistory((1..10).map { "L$it" }, "NEW", cap = 3)
        assertEquals(listOf("NEW", "L1", "L2"), capped)
    }

    @Test
    fun mergeHistory_blankValue_keepsOldCleaned() {
        val merged = SlotHistoryStore.mergeHistory(listOf("A", " ", "B"), "  ")
        assertEquals(listOf("A", "B"), merged)
    }
}
