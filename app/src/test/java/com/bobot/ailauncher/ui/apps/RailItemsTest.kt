package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字母导航完全统一（Bob 2026-10-25 追问「为什么不改」）：三处 rail（应用中心
 * 竖屏／横屏、首页选择器）共用 buildRailItems，锁死构造语义——房子；有 Dock
 * 时分隔＋★；分隔；有未读组时信封＋分隔；有应用字母；有折叠组时分隔＋收纳盒；
 * 有隐藏时分隔＋👁。调用方只传参，不再各自拼表，构造本身无第二份实现可供漂移。
 */
class RailItemsTest {

    @Test
    fun buildRailItems_full() {
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Star,
                RailItem.Separator,
                RailItem.Letter('A'),
                RailItem.Letter('B'),
                RailItem.Separator,
                RailItem.Eye,
            ),
            buildRailItems(hasDock = true, letters = listOf('A', 'B'), hasHidden = true),
        )
    }

    @Test
    fun buildRailItems_noDockNoHidden() {
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Letter('A'),
            ),
            buildRailItems(hasDock = false, letters = listOf('A'), hasHidden = false),
        )
    }

    @Test
    fun buildRailItems_emptyLettersKeepsSeparators() {
        // letters 为空时应用中心仍显示房子＋分隔（快照未到时的退化态），
        // 首页同口径，不擅自把分隔吞掉。
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Star,
                RailItem.Separator,
                RailItem.Separator,
                RailItem.Eye,
            ),
            buildRailItems(hasDock = true, letters = emptyList(), hasHidden = true),
        )
    }

    @Test
    fun buildRailItems_letterOrderPreserved() {
        // letters 顺序即调用方 azGroups 顺序（'#' 排最后由 railLetters 保证），
        // 构造不重排。
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Letter('Z'),
                RailItem.Letter('A'),
                RailItem.Letter('#'),
            ),
            buildRailItems(hasDock = false, letters = listOf('Z', 'A', '#'), hasHidden = false),
        )
    }

    @Test
    fun buildRailItems_unreadBeforeLetters() {
        // 未读组（Bob 2026-11-07 晚）：信封专位在 A 前面、字母之前，
        // 与主列表段顺序一致（未读 → A-Z → 折叠组 → 隐藏区）。
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Star,
                RailItem.Separator,
                RailItem.Unread,
                RailItem.Separator,
                RailItem.Letter('A'),
                RailItem.Letter('B'),
                RailItem.Separator,
                RailItem.Eye,
            ),
            buildRailItems(hasDock = true, letters = listOf('A', 'B'), hasHidden = true, hasUnread = true),
        )
    }

    @Test
    fun buildRailItems_unreadAndFoldOrder() {
        // 未读在字母前、折叠在字母后，两专位不换位。
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Unread,
                RailItem.Separator,
                RailItem.Letter('A'),
                RailItem.Separator,
                RailItem.Fold,
            ),
            buildRailItems(hasDock = false, letters = listOf('A'), hasHidden = false, hasFold = true, hasUnread = true),
        )
    }

    @Test
    fun buildRailItems_noUnreadByDefault() {
        // 不传 hasUnread 时不出现信封（首页选择器与旧调用点行为不变）。
        assertEquals(
            listOf(
                RailItem.House,
                RailItem.Separator,
                RailItem.Letter('A'),
            ),
            buildRailItems(hasDock = false, letters = listOf('A'), hasHidden = false),
        )
    }
}
