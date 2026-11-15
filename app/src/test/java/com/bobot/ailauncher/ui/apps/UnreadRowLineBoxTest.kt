package com.bobot.ailauncher.ui.apps

import com.bobot.ailauncher.ui.theme.AILauncherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未读行两行行高盒单测（Bob 2026-11-15 未读行标题重叠）。
 *
 * 根因：标题 Text 只给字号、不给行高，行高靠 LocalTextStyle 继承 bodyLarge
 * 的 24sp——竖屏 BodyLarge（16sp）碰巧对得上，横屏 Callout（14sp）却多
 * 2sp；横屏行又是定高 56dp、无纵向内边距，标题 24sp＋预览 18sp＝42sp，
 * 字号 1.3 时 54.6dp 只剩 1.3dp 余量、1.5 时 63dp 直接溢出定高，两行被
 * 夹裁进彼此。修法：标题行高显式取各自 token（竖屏 BodyLarge 24sp、
 * 横屏 Callout 22sp），横屏行定高改 min 高、随字长。
 *
 * 本单测锁死行高盒的算术：token 行高值＋两行之和在验收字号 1.3 下
 * 必须装进横屏行 min 高 56dp、且留足余量（≥4dp）；竖屏标题行高
 * 必须等于 BodyLarge token（24sp），不再靠继承。
 */
class UnreadRowLineBoxTest {

    // ---------- token 行高值（修法锚点） ----------

    @Test
    fun `portrait title lineHeight equals BodyLarge token`() {
        assertEquals(24f, AILauncherType.BodyLarge.lineHeight.value, 0.001f)
    }

    @Test
    fun `landscape title lineHeight equals Callout token`() {
        assertEquals(22f, AILauncherType.Callout.lineHeight.value, 0.001f)
    }

    @Test
    fun `preview lineHeight equals Caption token`() {
        assertEquals(18f, AILauncherType.Caption.lineHeight.value, 0.001f)
    }

    // ---------- 横屏两行行高盒装进 min 高 56dp（字号 1.3） ----------

    @Test
    fun `landscape two-line box fits min row height at fontScale 1_3`() {
        val title = AILauncherType.Callout.lineHeight.value // 22sp
        val preview = AILauncherType.Caption.lineHeight.value // 18sp
        val sumSp = title + preview // 40sp
        assertEquals(40f, sumSp, 0.001f)
        // sp 按字号缩放：40sp × 1.3 ＝ 52dp，装进 56dp min 高、余 4dp。
        val heightDpAt13 = sumSp * 1.3f
        assertEquals(52f, heightDpAt13, 0.001f)
        assertTrue(heightDpAt13 <= 56f)
        assertTrue(56f - heightDpAt13 >= 4f)
    }

    @Test
    fun `old wrong lineHeight would leave almost no slack`() {
        // 旧值：标题继承 bodyLarge 24sp，42sp × 1.3 ＝ 54.6dp、只剩 1.4dp——
        // 这是重叠的另一半。本测试只记账：若有人把横屏标题行高改回 24sp，
        // 上面 `landscape two-line box` 的 40sp 断言会先失败。
        val oldSumSp = 24f + 18f
        assertEquals(42f, oldSumSp, 0.001f)
        assertEquals(54.6f, oldSumSp * 1.3f, 0.01f)
        assertTrue(56f - oldSumSp * 1.3f < 2f)
    }

    // ---------- 竖屏两行行高盒（内容定高、不裁） ----------

    @Test
    fun `portrait two-line box uses BodyLarge plus Caption`() {
        val title = AILauncherType.BodyLarge.lineHeight.value // 24sp
        val preview = AILauncherType.Caption.lineHeight.value // 18sp
        assertEquals(42f, title + preview, 0.001f)
    }
}
