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
        assertEquals(
            listOf(
                LandscapeGridItem.Header("收藏"),
                LandscapeGridItem.App(wechat),
                LandscapeGridItem.App(chatgpt),
                LandscapeGridItem.App(douyin, isUnread = true, isFirstUnread = true),
                LandscapeGridItem.Header("A"),
                LandscapeGridItem.App(douyin),
                LandscapeGridItem.App(bobot),
                LandscapeGridItem.Header("B"),
                LandscapeGridItem.App(bilibili)
            ),
            items
        )
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
        assertEquals(
            listOf(
                LandscapeGridItem.Header("A"),
                LandscapeGridItem.App(a),
                LandscapeGridItem.FoldHeader(2, true),
                LandscapeGridItem.App(a),
                LandscapeGridItem.App(b),
                LandscapeGridItem.FoldFooter
            ),
            items
        )
    }

    @Test
    fun `hidden locked shows placeholder instead of apps`() {
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
        assertEquals(
            listOf(
                LandscapeGridItem.Header("已隐藏的应用"),
                LandscapeGridItem.LockPlaceholder
            ),
            items
        )
    }

    @Test
    fun `hidden unlocked shows apps`() {
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
        assertEquals(
            listOf(
                LandscapeGridItem.Header("已隐藏的应用"),
                LandscapeGridItem.App(h)
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
        // 0:收藏头 1:微信 2:抖音(未读) 3:A头 4:抖音 5:bobot 6:不常用头 7:隐藏头 8:锁定占位
        val anchors = landscapeGridAnchors(items)
        assertEquals(0, anchors.fav)
        assertEquals(2, anchors.unread)
        assertEquals(mapOf('A' to 3), anchors.letters)
        assertEquals(6, anchors.fold)
        assertEquals(7, anchors.hidden)
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
        assertEquals(mapOf('A' to 0), anchors.letters)
        assertNull(anchors.fold)
        assertNull(anchors.hidden)
    }
}
