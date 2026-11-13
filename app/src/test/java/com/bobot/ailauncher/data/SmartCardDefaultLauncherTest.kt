package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次安装设默认桌面卡单测（Bob 2026-11-14 07:33 亲令，07:39 纠正文案方向）：
 * 纯函数、JVM 可跑。锁死：
 * - 文案：只写一句话（Bob 07:39：“不要写长、不要把 4 条好处都列出来。
 *   只要一句话、简洁一点，让用户知道设了默认桌面体验会更好就行，还要
 *   有对话的感觉”）——4 条核实好处（快捷操作更多／按 Home 键回到这个
 *   桌面／未读消息／智能卡片和 Dock 智能排序）只作内部依据、不上卡片；
 *   应用间跳转确认弹窗未验证，绝不写成好处。
 * - 出现条件（shouldShowDefaultLauncherCard）：没问过且还不是默认才出现。
 * - 关闭条件：点「取消」/点「去设置」成功发出/横滑，任一响应都记已问过
 *   ＋进 done_ 集合，visibleSmartCards 即过滤掉（退出来没设也关——
 *   问过即关，不反复追问）；Intent 发不出去不记、卡片留着（那条是
 *   performAction 的异常分支，JVM 测不到，靠代码走查＋真机验收）。
 * - 排序：无截止点（sortMs/deadlineMs 都是 Long.MAX_VALUE），有时效真卡
 *   之后、引导卡之前；划走按应用推的（划过就丢，不沉底）。
 * Android 侧（SharedPreferences 读写、isDefaultLauncher 的
 * PackageManager 解析、openHomeSettings 的 Intent 发出）JVM 测不到，
 * 靠代码走查＋真机首次安装验收。
 */
class SmartCardDefaultLauncherTest {

    /** 按仓库 defaultLauncherCard() 的口径构造一张设默认卡（仓库构建器
     * 本身要 Context，JVM 跑不了；这里锁死它应有的字段值）。 */
    private fun defaultLauncherCard() = SmartCard(
        id = DEFAULT_LAUNCHER_CARD_ID,
        kind = SmartCardKind.DEFAULT_LAUNCHER,
        sourceLabel = DEFAULT_LAUNCHER_SOURCE_LABEL,
        fact = DEFAULT_LAUNCHER_CARD_FACT,
        prep = null,
        actionLabel = DEFAULT_LAUNCHER_ACTION_LABEL,
        dismissLabel = DEFAULT_LAUNCHER_DISMISS_LABEL,
        sortMs = Long.MAX_VALUE,
        deadlineMs = Long.MAX_VALUE,
        action = SmartCardAction.OpenHomeSettings,
        sourceKey = DEFAULT_LAUNCHER_CARD_ID
    )

    private fun billCard(sortMs: Long, deadlineMs: Long) = SmartCard(
        id = "bill|test",
        kind = SmartCardKind.BILL,
        sourceLabel = "短信",
        fact = "fact",
        prep = null,
        actionLabel = "去还款",
        sortMs = sortMs,
        deadlineMs = deadlineMs,
        action = SmartCardAction.SmsThread("95558"),
        sourceKey = "bill|test"
    )

    // ---- 文案（逐字锁死） ----

    @Test
    fun fact_isOneSentence() {
        val fact = DEFAULT_LAUNCHER_CARD_FACT
        assertEquals(
            "把 AI桌面设为默认桌面吧，以后按 Home 键就回到这里，用起来更顺手。",
            fact
        )
        // 只有一句话：一个句号，没有分号、换行、编号清单。
        assertEquals(1, fact.count { it == '。' })
        assertFalse(fact.contains('；'))
        assertFalse(fact.contains('\n'))
        assertFalse(fact.contains("1."))
        assertFalse(fact.contains("1、"))
    }

    @Test
    fun fact_saysWhatDefaultMeans_andBetterExperience() {
        val fact = DEFAULT_LAUNCHER_CARD_FACT
        // 讲清楚设默认意味着什么（按 Home 键回到这里），以及体验会更好。
        assertTrue(fact.contains("默认桌面"))
        assertTrue(fact.contains("Home"))
        assertTrue(fact.contains("更顺手"))
    }

    @Test
    fun fact_doesNotListBenefits() {
        // Bob 07:39 纠正：不列 4 条好处。4 条核实好处的关键词都不许上卡片。
        val fact = DEFAULT_LAUNCHER_CARD_FACT
        assertFalse(fact.contains("快捷操作"))
        assertFalse(fact.contains("未读"))
        assertFalse(fact.contains("智能排序"))
        assertFalse(fact.contains("Dock"))
        // 应用间跳转确认弹窗未验证，绝不写成好处。
        assertFalse(fact.contains("弹窗"))
        assertFalse(fact.contains("不再弹"))
    }

    @Test
    fun fact_hasConversationalTone_noInternalJargon() {
        val fact = DEFAULT_LAUNCHER_CARD_FACT
        // 有对话感（“吧”“以后”“用起来”），不用实现术语与内部黑话。
        assertTrue(fact.contains("吧"))
        assertFalse(fact.contains("下一步"))
        assertFalse(fact.contains("权限"))
        assertFalse(fact.contains("默认启动器"))
        assertFalse(fact.contains("Launcher"))
    }

    @Test
    fun labels_areCancelAndGoToSettings() {
        assertEquals("取消", DEFAULT_LAUNCHER_DISMISS_LABEL)
        assertEquals("去设置", DEFAULT_LAUNCHER_ACTION_LABEL)
        assertEquals("AI桌面", DEFAULT_LAUNCHER_SOURCE_LABEL)
        assertEquals("default_launcher", DEFAULT_LAUNCHER_CARD_ID)
        val card = defaultLauncherCard()
        assertEquals("取消", card.dismissLabel)
        assertEquals("去设置", card.actionLabel)
        // 左按钮不是通用的「我知道了」。
        assertFalse(card.dismissLabel == SMART_CARD_DISMISS_LABEL)
    }

    @Test
    fun defaultDismissLabel_isStillWoZhiDaoLe() {
        // 其他卡的左按钮默认仍是「我知道了」，不受 dismissLabel 字段影响。
        val card = billCard(100L, 200L)
        assertEquals(SMART_CARD_DISMISS_LABEL, card.dismissLabel)
        assertEquals("我知道了", SMART_CARD_DISMISS_LABEL)
    }

    // ---- 出现条件（纯函数） ----

    @Test
    fun shouldShow_onlyWhenNotAskedAndNotDefault() {
        assertTrue(shouldShowDefaultLauncherCard(asked = false, isDefault = false))
        assertFalse(shouldShowDefaultLauncherCard(asked = true, isDefault = false))
        assertFalse(shouldShowDefaultLauncherCard(asked = false, isDefault = true))
        assertFalse(shouldShowDefaultLauncherCard(asked = true, isDefault = true))
    }

    // ---- 划走语义与排序 ----

    @Test
    fun isAppPushed_swipeDiscards() {
        // 设默认卡是应用推的（不是用户的事项提醒）：划过就丢，不沉底。
        assertFalse(isUserMatterCard(SmartCardKind.DEFAULT_LAUNCHER))
    }

    @Test
    fun kindRank_beforeGuide() {
        assertTrue(kindRank(SmartCardKind.DEFAULT_LAUNCHER) < kindRank(SmartCardKind.GUIDE))
    }

    @Test
    fun sort_afterTimeSensitiveCard() {
        val bill = billCard(sortMs = 1_000L, deadlineMs = 2_000L)
        val launcher = defaultLauncherCard()
        assertEquals(
            listOf(bill, launcher),
            sortSmartCards(listOf(launcher, bill))
        )
    }

    // ---- 关闭条件（可见性过滤） ----

    @Test
    fun visible_whenNotDoneAndNotExpired() {
        val now = 1_000_000L
        val launcher = defaultLauncherCard()
        assertEquals(
            listOf(launcher),
            visibleSmartCards(listOf(launcher), emptySet(), emptyList(), now)
        )
    }

    @Test
    fun gone_whenMarkedDone() {
        // 点「取消」/点「去设置」成功发出/横滑，任一响应都把卡记进 done_
        // 集合（仓库 markDone/skip），可见列表即过滤掉——问过即关。
        val now = 1_000_000L
        val launcher = defaultLauncherCard()
        assertEquals(
            emptyList<SmartCard>(),
            visibleSmartCards(
                listOf(launcher),
                setOf(DEFAULT_LAUNCHER_CARD_ID),
                emptyList(),
                now
            )
        )
    }

    @Test
    fun neverExpiresOnItsOwn() {
        // 无截止点：不过期，不靠过期消失，只靠问过/已是默认消失。
        val launcher = defaultLauncherCard()
        assertFalse(isCardExpired(launcher, Long.MAX_VALUE - 1))
        assertEquals(Long.MAX_VALUE, launcher.deadlineMs)
    }
}
