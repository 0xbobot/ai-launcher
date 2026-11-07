package com.bobot.ailauncher.data

import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用中心「未读」组推导单测（Bob 2026-11-07 晚：有消息通知的应用排到
 * A 前面最优先显示）。覆盖：哪些应用进组、组内按数降序、数相同按名排、
 * 有角标的应用不进折叠组、字母变空后 rail 字母消失。
 */
class UnreadGroupTest {

    private fun app(pkg: String, label: String) = AppInfo(pkg, label, ColorDrawable())

    // ---------- deriveUnreadApps ----------

    @Test
    fun `deriveUnreadApps - only badge greater than zero enters`() {
        val apps = listOf(app("wx", "微信"), app("sms", "短信"), app("tel", "电话"))
        val counts = mapOf("wx" to 125, "sms" to 0, "tel" to 1)
        assertEquals(
            listOf("wx", "tel"),
            deriveUnreadApps(apps, counts).map { it.packageName }
        )
    }

    @Test
    fun `deriveUnreadApps - sorted by count descending`() {
        val apps = listOf(app("wx", "微信"), app("sms", "短信"), app("tel", "电话"))
        val counts = mapOf("wx" to 2, "sms" to 15, "tel" to 125)
        assertEquals(
            listOf("tel", "sms", "wx"),
            deriveUnreadApps(apps, counts).map { it.packageName }
        )
    }

    @Test
    fun `deriveUnreadApps - tie broken by name`() {
        // 同数按名排（中文 Collator）：阿里 < 百度 < 腾讯
        val apps = listOf(app("bd", "百度"), app("ali", "阿里"), app("tx", "腾讯"))
        val counts = mapOf("ali" to 5, "bd" to 5, "tx" to 5)
        assertEquals(
            listOf("ali", "bd", "tx"),
            deriveUnreadApps(apps, counts).map { it.packageName }
        )
    }

    @Test
    fun `deriveUnreadApps - empty counts gives empty group`() {
        val apps = listOf(app("wx", "微信"))
        assertTrue(deriveUnreadApps(apps, emptyMap()).isEmpty())
        assertTrue(deriveUnreadApps(emptyList(), mapOf("wx" to 3)).isEmpty())
    }

    // ---------- withoutPkgs ----------

    @Test
    fun `withoutPkgs - removes listed packages, keeps the rest`() {
        val apps = listOf(app("wx", "微信"), app("sms", "短信"), app("tel", "电话"))
        assertEquals(
            listOf("sms"),
            withoutPkgs(apps, setOf("wx", "tel")).map { it.packageName }
        )
        // 空集不过滤（与 foldMemberPkgs 为空时 azApps 原样走同一口径）
        assertEquals(
            listOf("wx", "sms", "tel"),
            withoutPkgs(apps, emptySet()).map { it.packageName }
        )
    }

    @Test
    fun `withoutPkgs - badge apps do not enter fold group`() {
        // 有角标的应用不进折叠组（未读消息即不算“不常用”）：不活跃集里
        // 含微信（角标 125），过滤后只剩电话。
        val inactive = listOf(app("wx", "微信"), app("tel", "电话"))
        assertEquals(
            listOf("tel"),
            withoutPkgs(inactive, setOf("wx")).map { it.packageName }
        )
    }

    // ---------- groupAppsByLetter ----------

    @Test
    fun `groupAppsByLetter - letter disappears when all its apps moved out`() {
        val apps = listOf(app("wx", "微信"), app("sms", "短信"), app("tel", "电话"))
        val keyOf: (AppInfo) -> Char = { it.label.first().uppercaseChar() }
        assertEquals(
            listOf('微', '短', '电'),
            groupAppsByLetter(apps, keyOf).map { it.first }
        )
        // 微信进了未读组、从字母组移出后，“微”组变空、rail 字母自动消失
        val rest = groupAppsByLetter(withoutPkgs(apps, setOf("wx")), keyOf)
        assertEquals(listOf('短', '电'), rest.map { it.first })
    }

    @Test
    fun `groupAppsByLetter - hash sorts last`() {
        val apps = listOf(app("x", "123"), app("wx", "微信"))
        val keyOf: (AppInfo) -> Char = { if (it.label.first().isDigit()) '#' else '微' }
        assertEquals(
            listOf('微', '#'),
            groupAppsByLetter(apps, keyOf).map { it.first }
        )
    }
}
