package com.bobot.ailauncher.ui.apps

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 横屏图标网格（Bob 2026-11-22 定稿）：
 * 项表构建（纯函数）与锚点下标的单测。渲染层（LazyVerticalGrid /
 * 格子 / 角标）不在 JVM 单测源集里做（无 Robolectric、无 compose
 * ui-test 依赖、无 app/src/androidTest），由分支 CI 的编译＋设计
 * lint 兜底，真机验收看实拍。
 */
class AppCenterLandscapeGridTest {

    private fun app(pkg: String, label: String) = AppInfo(pkg, label, ColorDrawable())

    @Test
    fun `grid has 8 columns`() {
        assertEquals(8, LandscapeGridColumns)
    }

    @Test
    fun `grid icon is 50dp as approved in the draft`() {
        assertEquals(50.dp, LandscapeGridIconSize)
    }

    @Test
    fun `apps per row is seven`() {
        assertEquals(7, LandscapeGridAppsPerRow)
        assertEquals(LandscapeGridColumns - 1, LandscapeGridAppsPerRow)
    }

    @Test
    fun `build items order is favorites then unread then letters`() {
        val wechat = app("com.tencent.mm", "微信")
        val chatgpt = app("com.openai.chatgpt", "ChatGPT")
        val douyin = app("com.ss.android.ugc.aweme", "抖音")
        val bobot = app("com.bobot.ailauncher", "bobot")
        val bilibili = app("tv.danmaku.bili", "哔哩哔哩")
        val items = buildLandscapeGridItems(
            favApps = listOf(wechat, chatgpt),
            unreadApps = listOf(douyin),
            azGroups = listOf('A' to listOf(douyin, bobot), 'B' to listOf(bilibili)),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        // 每组一行 8 格：标签 1 格＋应用＋空占位格垫满，下一组另起一行。
        // 收藏行：标签＋2 应用＋5 空；未读行：标签＋1 未读＋6 空；
        // A 行：标签＋2 应用＋5 空；B 行：标签＋1 应用＋6 空。
        val e = LandscapeGridItem.EmptyCell
        assertEquals(
            listOf(
                LandscapeGridItem.GroupLabel("收藏"),
                LandscapeGridItem.App(wechat),
                LandscapeGridItem.App(chatgpt), e, e, e, e, e,
                LandscapeGridItem.GroupLabel("未读"),
                LandscapeGridItem.App(douyin, isUnread = true), e, e, e, e, e, e,
                LandscapeGridItem.GroupLabel("A"),
                LandscapeGridItem.App(douyin),
                LandscapeGridItem.App(bobot), e, e, e, e, e,
                LandscapeGridItem.GroupLabel("B"),
                LandscapeGridItem.App(bilibili), e, e, e, e, e, e
            ),
            items
        )
    }

    @Test
    fun `unread section has its own header before the unread apps`() {
        val douyin = app("com.ss.android.ugc.aweme", "抖音")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = listOf(douyin),
            azGroups = emptyList(),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        assertEquals(LandscapeGridItem.GroupLabel("未读"), items[0])
        assertEquals(
            LandscapeGridItem.App(douyin, isUnread = true),
            items[1]
        )
    }

    @Test
    fun `group with eight apps wraps the eighth under the app column`() {
        val apps = (1..8).map { app("com.example.g$it", "G 应用 $it") }
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = listOf('G' to apps),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        // 第 1 行：标签＋7 个应用（8 格满）；第 2 行：空占位格＋第 8 个应用＋6 个垫格。
        assertEquals(16, items.size)
        assertEquals(LandscapeGridItem.GroupLabel("G"), items[0])
        (1..7).forEach { i -> assertEquals(LandscapeGridItem.App(apps[i - 1]), items[i]) }
        assertEquals(LandscapeGridItem.EmptyCell, items[8])
        assertEquals(LandscapeGridItem.App(apps[7]), items[9])
        (10..15).forEach { i -> assertEquals(LandscapeGridItem.EmptyCell, items[i]) }
    }

    @Test
    fun `unread app appears in both the unread section and its letter group`() {
        val douyin = app("com.ss.android.ugc.aweme", "抖音")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = listOf(douyin),
            azGroups = listOf('A' to listOf(douyin)),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        // 未读组里一个（isUnread），字母组里一个（普通格）。
        assertEquals(2, items.filterIsInstance<LandscapeGridItem.App>().size)
        assertEquals(1, items.filterIsInstance<LandscapeGridItem.App>().count { it.isUnread })
    }

    @Test
    fun `fold collapsed shows only the header row`() {
        val a = app("com.example.a", "A 应用")
        val b = app("com.example.b", "B 应用")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = emptyList(),
            foldApps = listOf(a, b),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        assertEquals(listOf(LandscapeGridItem.FoldHeader(2, false)), items)
    }

    @Test
    fun `fold expanded shows apps and the footer after the header`() {
        val a = app("com.example.a", "A 应用")
        val b = app("com.example.b", "B 应用")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = listOf('A' to listOf(a)),
            foldApps = listOf(a, b),
            foldExpanded = true,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        val e = LandscapeGridItem.EmptyCell
        assertEquals(
            listOf(
                LandscapeGridItem.GroupLabel("A"),
                LandscapeGridItem.App(a), e, e, e, e, e, e,
                LandscapeGridItem.FoldHeader(2, true),
                LandscapeGridItem.App(a),
                LandscapeGridItem.App(b),
                LandscapeGridItem.FoldFooter
            ),
            items
        )
    }

    @Test
    fun `hidden locked shows inline label and unlock cell`() {
        val h = app("com.example.hidden", "隐藏应用")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = emptyList(),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = listOf(h),
            hiddenUnlocked = false
        )
        // 锁定态也和收藏一样内联标签：一行 8 格——标签＋解锁格＋6 个空占位格。
        val e = LandscapeGridItem.EmptyCell
        assertEquals(
            listOf(
                LandscapeGridItem.GroupLabel("已隐藏"),
                LandscapeGridItem.UnlockHidden, e, e, e, e, e, e
            ),
            items
        )
    }

    @Test
    fun `hidden unlocked shows inline label and apps`() {
        val h = app("com.example.hidden", "隐藏应用")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = emptyList(),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = listOf(h),
            hiddenUnlocked = true
        )
        // 解锁态：标签＋1 个应用＋6 个空占位格（和收藏/未读同款垫满口径）。
        val e = LandscapeGridItem.EmptyCell
        assertEquals(
            listOf(
                LandscapeGridItem.GroupLabel("已隐藏"),
                LandscapeGridItem.App(h), e, e, e, e, e, e
            ),
            items
        )
    }

    @Test
    fun `empty sections are omitted`() {
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = emptyList(),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `anchors point at the section headers and the first unread app`() {
        val wechat = app("com.tencent.mm", "微信")
        val douyin = app("com.ss.android.ugc.aweme", "抖音")
        val bobot = app("com.bobot.ailauncher", "bobot")
        val folded = app("com.example.folded", "不常用应用")
        val hidden = app("com.example.hidden", "隐藏应用")
        val items = buildLandscapeGridItems(
            favApps = listOf(wechat),
            unreadApps = listOf(douyin),
            azGroups = listOf('A' to listOf(douyin, bobot)),
            foldApps = listOf(folded),
            foldExpanded = false,
            hiddenApps = listOf(hidden),
            hiddenUnlocked = false
        )
        // 0:收藏标签 1:微信 2-7:空占位 8:未读标签 9:抖音(未读) 10-15:空占位
        // 16:A 标签 17:抖音 18:bobot 19-23:空占位 24:不常用头 25:隐藏标签 26:解锁格 27-32:空占位
        val anchors = landscapeGridAnchors(items)
        assertEquals(0, anchors.fav)
        assertEquals(9, anchors.unread)
        assertEquals(8, anchors.unreadHeader)
        assertEquals(mapOf('A' to 16), anchors.letters)
        assertEquals(24, anchors.fold)
        assertEquals(25, anchors.hidden)
    }

    @Test
    fun `anchors are null for absent sections`() {
        val a = app("com.example.a", "A 应用")
        val items = buildLandscapeGridItems(
            favApps = emptyList(),
            unreadApps = emptyList(),
            azGroups = listOf('A' to listOf(a)),
            foldApps = emptyList(),
            foldExpanded = false,
            hiddenApps = emptyList(),
            hiddenUnlocked = false
        )
        val anchors = landscapeGridAnchors(items)
        assertNull(anchors.fav)
        assertNull(anchors.unread)
        assertNull(anchors.unreadHeader)
        assertEquals(mapOf('A' to 0), anchors.letters)
        assertNull(anchors.fold)
        assertNull(anchors.hidden)
    }
}
