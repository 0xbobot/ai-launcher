package com.bobot.ailauncher.data

import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体检批D ⑥：应用搜索索引纯逻辑单测。
 * 覆盖拼音构建（全拼/首字母/英文原样/缺字降级）、子序列模糊、
 * 自然语言关键词集合（中文包含/反向包含/拼音路径/单字符守卫）、
 * 六档打分与排序（同档频次破同分、高档压高频、稳定序、空查询全量）。
 * build()/search() 的 Context（assets 字典）入口不在覆盖内，
 * 经 keywordPackagesFor/scoreFor/rank/pinyinOf/isSubsequence 接缝测逻辑本体。
 */
class AppSearchIndexTest {

    private val dict = mapOf(
        "微" to "wei", "信" to "xin", "支" to "zhi", "付" to "fu",
        "高" to "gao", "德" to "de", "图" to "tu", "云" to "yun"
    )

    private fun entry(pkg: String, label: String, full: String, initials: String) =
        AppSearchIndex.Entry(AppInfo(pkg, label, ColorDrawable()), full, initials)

    // ---------- pinyinOf ----------

    @Test
    fun `pinyinOf builds full and initials`() {
        val (full, initials) = AppSearchIndex.pinyinOf("微信", dict)
        assertEquals("weixin", full)
        assertEquals("wx", initials)
    }

    @Test
    fun `pinyinOf keeps latin and digits as-is`() {
        val (full, initials) = AppSearchIndex.pinyinOf("Clock7", emptyMap())
        assertEquals("clock7", full)
        assertEquals("clock7", initials)
    }

    @Test
    fun `pinyinOf mixes chinese and latin`() {
        val (full, initials) = AppSearchIndex.pinyinOf("微信Pro", dict)
        assertEquals("weixinpro", full)
        assertEquals("wxpro", initials)
    }

    @Test
    fun `pinyinOf degrades on missing chars and symbols`() {
        // 猫不在字典里：未收录汉字走 isLetterOrDigit 分支原样透传（真实降级口径）；
        // 间隔号等符号忽略
        val (full, initials) = AppSearchIndex.pinyinOf("微猫·", dict)
        assertEquals("wei猫", full)
        assertEquals("w猫", initials)
        assertEquals("" to "", AppSearchIndex.pinyinOf("", dict))
    }

    // ---------- isSubsequence ----------

    @Test
    fun `subsequence matches in order only`() {
        assertTrue(AppSearchIndex.isSubsequence("高图", "高德地图"))
        assertFalse(AppSearchIndex.isSubsequence("图高", "高德地图"))
        assertFalse(AppSearchIndex.isSubsequence("高德地图图", "高德地图"))
    }

    @Test
    fun `subsequence requires at least two chars`() {
        assertFalse(AppSearchIndex.isSubsequence("高", "高德地图"))
        assertFalse(AppSearchIndex.isSubsequence("", "高德地图"))
    }

    // ---------- keywordPackagesFor ----------

    @Test
    fun `keyword chinese contains both directions`() {
        val hit = AppSearchIndex.keywordPackagesFor("我要打车去机场", emptyMap())
        assertTrue(hit.contains("didi"))
        // 只打一个「打」：关键词包含查询，同样命中打车一族
        val partial = AppSearchIndex.keywordPackagesFor("打", emptyMap())
        assertTrue(partial.contains("didi"))
    }

    @Test
    fun `keyword pinyin path matches full spelling`() {
        val kp = mapOf("打车" to listOf("dache"), "音乐" to listOf("yinyue"))
        val hit = AppSearchIndex.keywordPackagesFor("dache", kp)
        assertTrue(hit.contains("didi"))
        // 音乐的常用读音别名（yinyue）经同一条拼音路径命中
        val music = AppSearchIndex.keywordPackagesFor("yinyue", kp)
        assertTrue(music.contains("cloudmusic"))
    }

    @Test
    fun `keyword pinyin path ignores single char queries`() {
        val kp = mapOf("打车" to listOf("dache"))
        val hit = AppSearchIndex.keywordPackagesFor("d", kp)
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `keyword no match returns empty`() {
        assertTrue(AppSearchIndex.keywordPackagesFor("zzzz", emptyMap()).isEmpty())
    }

    // ---------- scoreFor 六档 ----------

    @Test
    fun `score name contains is top tier`() {
        assertEquals(100, AppSearchIndex.scoreFor("微", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
    }

    @Test
    fun `score keyword tier beats pinyin`() {
        // 滴滴出行：名称不含「打车」，靠关键词包名片段命中 80
        assertEquals(
            80,
            AppSearchIndex.scoreFor("打车", "滴滴出行", "com.didi", "didichuxing", "ddcx", setOf("didi"))
        )
    }

    @Test
    fun `score pinyin full tier`() {
        assertEquals(70, AppSearchIndex.scoreFor("weix", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
    }

    @Test
    fun `score initials tier requires two chars`() {
        assertEquals(60, AppSearchIndex.scoreFor("wx", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
        // 单字符不走首字母档（带 q.length>=2 门槛）：w 先被全拼档收走
        // （weixin 含 w）→ 70，而非首字母档的 60
        assertEquals(70, AppSearchIndex.scoreFor("w", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
    }

    @Test
    fun `score package tier`() {
        assertEquals(40, AppSearchIndex.scoreFor("tencent", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
    }

    @Test
    fun `score subsequence tier and no match`() {
        // 微付：不是「微信支付」的子串，是子序列 → 30
        assertEquals(30, AppSearchIndex.scoreFor("微付", "微信支付", "com.tencent.mm", "weixinzhifu", "wxzf", emptySet()))
        assertEquals(0, AppSearchIndex.scoreFor("zzz", "微信", "com.tencent.mm", "weixin", "wx", emptySet()))
    }

    // ---------- rank 排序 ----------

    @Test
    fun `rank blank query returns all in order`() {
        val a = entry("pkg.a", "甲", "jia", "j")
        val b = entry("pkg.b", "乙", "yi", "y")
        val out = AppSearchIndex.rank(listOf(a, b), "", emptySet()) { 0.0 }
        assertEquals(listOf("pkg.a", "pkg.b"), out.map { it.packageName })
    }

    @Test
    fun `rank usage breaks same tier ties`() {
        // 两条都名称命中「支付」（同为 100 档）：频次高的排前面
        val low = entry("pkg.low", "云支付", "yunzhifu", "yzf")
        val high = entry("pkg.high", "微信支付", "weixinzhifu", "wxzf")
        val usage = mapOf("pkg.low" to 1.0, "pkg.high" to 9.0)
        val out = AppSearchIndex.rank(listOf(low, high), "支付", emptySet()) { usage[it] ?: 0.0 }
        assertEquals(listOf("pkg.high", "pkg.low"), out.map { it.packageName })
    }

    @Test
    fun `rank higher tier beats higher usage`() {
        // 名称命中（100）频次为 0，也压过关键词命中（80）频次拉满
        val exact = entry("pkg.exact", "打车帮手", "dachebangshou", "dcbs")
        val kw = entry("com.didi", "滴滴出行", "didichuxing", "ddcx")
        val usage = mapOf("pkg.exact" to 0.0, "com.didi" to 100.0)
        val out = AppSearchIndex.rank(listOf(kw, exact), "打车", setOf("didi")) { usage[it] ?: 0.0 }
        assertEquals(listOf("pkg.exact", "com.didi"), out.map { it.packageName })
    }

    @Test
    fun `rank filters misses and keeps stable order on full ties`() {
        val a = entry("pkg.a", "高德地图", "gaodeditu", "gddt")
        val miss = entry("pkg.x", "时钟", "shizhong", "sz")
        val b = entry("pkg.b", "高德打车", "gaodedache", "gddc")
        // a、b 同档同频（都含「高德」，usage 同为 0）→ 保持索引原序；miss 被过滤
        val out = AppSearchIndex.rank(listOf(a, miss, b), "高德", emptySet()) { 0.0 }
        assertEquals(listOf("pkg.a", "pkg.b"), out.map { it.packageName })
    }
}
