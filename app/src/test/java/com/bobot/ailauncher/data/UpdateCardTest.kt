package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.carouselShouldAutoRotate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 升级提醒做成智能卡片（第四十五批，Bob 2026-11-10 定稿，设计见
 * upgrade-reminder-card-design.md §2–§10；智能卡片设计稿 §4.9）：
 * 新 kind UPDATE 的状态机单测——纯函数、JVM 可跑。
 * 状态机：有新版 → 下载中（带进度）→ 下载好了 → 安装；失败 → 重试；
 * 「我知道了」只压住那一个版本（新版本是新 id，照常出卡）；强更卡
 * 无「我知道了」／置顶／自动轮换停住。
 */
class UpdateCardTest {

    private fun info(
        code: Int = 547,
        name: String = "1.4.3",
        forced: Boolean = false
    ) = OtaInfo(
        versionCode = code,
        versionName = name,
        apkUrl = "https://example.com/ai-launcher-v$name.apk",
        changelog = "开发口径，不贴卡上",
        forceUpdate = forced,
        prerelease = true,
        sha256 = ""
    )

    private fun billCard(id: String = "bill", sortMs: Long = 1000L) =
        SmartCard(
            id = id,
            kind = SmartCardKind.BILL,
            sourceLabel = "短信",
            fact = "fact $id",
            prep = null,
            actionLabel = "去还款",
            sortMs = sortMs,
            deadlineMs = 2000L,
            action = SmartCardAction.SmsThread("95558"),
            sourceKey = id
        )

    private fun guideCard(id: String = "guide") =
        SmartCard(
            id = id,
            kind = SmartCardKind.GUIDE,
            sourceLabel = "AI桌面",
            fact = "fact $id",
            prep = null,
            actionLabel = "连接",
            sortMs = Long.MAX_VALUE,
            deadlineMs = Long.MAX_VALUE,
            action = SmartCardAction.Connect("message"),
            sourceKey = id
        )

    // ---------- 状态推导 updateDownloadPhase ----------

    @Test
    fun phase_idle_notDownloaded_isAvailable() {
        assertEquals(
            UpdatePhase.AVAILABLE,
            updateDownloadPhase(OtaDownloader.State.Idle, 0, 547, false)
        )
    }

    @Test
    fun phase_downloading_targetMatches_isDownloading() {
        assertEquals(
            UpdatePhase.DOWNLOADING,
            updateDownloadPhase(
                OtaDownloader.State.Downloading(0.45f), 547, 547, false
            )
        )
    }

    @Test
    fun phase_downloading_targetDiffers_isAvailable() {
        // 下载器里是上一版的状态（下完 546 没装、新版 547 又来了），
        // 不拿旧版的状态冒充新版。
        assertEquals(
            UpdatePhase.AVAILABLE,
            updateDownloadPhase(
                OtaDownloader.State.Downloading(0.9f), 546, 547, false
            )
        )
    }

    @Test
    fun phase_success_targetMatches_isDownloaded() {
        assertEquals(
            UpdatePhase.DOWNLOADED,
            updateDownloadPhase(
                OtaDownloader.State.Success(File("a.apk")), 547, 547, false
            )
        )
    }

    @Test
    fun phase_success_targetDiffers_isAvailable() {
        assertEquals(
            UpdatePhase.AVAILABLE,
            updateDownloadPhase(
                OtaDownloader.State.Success(File("a.apk")), 546, 547, false
            )
        )
    }

    @Test
    fun phase_failed_targetMatches_isFailed() {
        assertEquals(
            UpdatePhase.FAILED,
            updateDownloadPhase(OtaDownloader.State.Failed, 547, 547, false)
        )
    }

    @Test
    fun phase_failed_targetDiffers_isAvailable() {
        assertEquals(
            UpdatePhase.AVAILABLE,
            updateDownloadPhase(OtaDownloader.State.Failed, 546, 547, false)
        )
    }

    @Test
    fun phase_idle_downloadedFlag_isDownloaded() {
        // 进程重启后下载器是 Idle，但盘里已有同版本完整包：直接到「下载好了」。
        assertEquals(
            UpdatePhase.DOWNLOADED,
            updateDownloadPhase(OtaDownloader.State.Idle, 547, 547, true)
        )
    }

    @Test
    fun phase_failed_butDownloadedFlag_isDownloaded() {
        // 包已在盘里时，之前一次失败的记录不应把卡片打回失败态。
        assertEquals(
            UpdatePhase.DOWNLOADED,
            updateDownloadPhase(OtaDownloader.State.Failed, 547, 547, true)
        )
    }

    // ---------- 文案与按钮 updateCardDisplay ----------

    @Test
    fun display_available_factAndAction() {
        val d = updateCardDisplay(info(), UpdatePhase.AVAILABLE)
        assertEquals("有新版本 v1.4.3", d.fact)
        assertEquals("去更新", d.actionLabel)
        assertNull(d.progress)
        assertTrue(d.showActionButton)
        assertTrue(d.showDismissButton)
        assertFalse(d.pinned)
        assertEquals(UpdatePhase.AVAILABLE, d.phase)
    }

    @Test
    fun display_available_forced_factNoDismissPinned() {
        val d = updateCardDisplay(info(forced = true), UpdatePhase.AVAILABLE)
        assertEquals("有新版本 v1.4.3，必须更新", d.fact)
        assertEquals("去更新", d.actionLabel)
        assertTrue(d.showActionButton)
        assertFalse(d.showDismissButton)
        assertTrue(d.pinned)
    }

    @Test
    fun display_available_forced_withReason() {
        val d = updateCardDisplay(
            info(forced = true), UpdatePhase.AVAILABLE, 0f, "旧版本有个严重问题"
        )
        assertEquals("有新版本 v1.4.3，必须更新\n旧版本有个严重问题", d.fact)
        assertFalse(d.showDismissButton)
        assertTrue(d.pinned)
    }

    @Test
    fun display_downloading_factProgressAndHiddenAction() {
        val d = updateCardDisplay(info(), UpdatePhase.DOWNLOADING, 0.45f)
        assertEquals("正在下载 v1.4.3（45%）", d.fact)
        assertEquals(0.45f, d.progress)
        assertFalse(d.showActionButton)
        // 下载中也可以点「我知道了」关卡片（只关卡片，不取消下载）。
        assertTrue(d.showDismissButton)
        assertFalse(d.pinned)
    }

    @Test
    fun display_downloading_percentBounds() {
        assertEquals(
            "正在下载 v1.4.3（0%）",
            updateCardDisplay(info(), UpdatePhase.DOWNLOADING, 0f).fact
        )
        assertEquals(
            "正在下载 v1.4.3（100%）",
            updateCardDisplay(info(), UpdatePhase.DOWNLOADING, 1f).fact
        )
        // 超界进度钳到 0..100，不出「（120%）」这种文案。
        assertEquals(
            "正在下载 v1.4.3（100%）",
            updateCardDisplay(info(), UpdatePhase.DOWNLOADING, 1.2f).fact
        )
    }

    @Test
    fun display_downloaded_factAndInstall() {
        val d = updateCardDisplay(info(), UpdatePhase.DOWNLOADED)
        assertEquals("下载好了", d.fact)
        assertEquals("安装", d.actionLabel)
        assertNull(d.progress)
        assertTrue(d.showActionButton)
        assertTrue(d.showDismissButton)
    }

    @Test
    fun display_failed_factAndRetry() {
        val d = updateCardDisplay(info(), UpdatePhase.FAILED)
        assertEquals("下载失败", d.fact)
        assertEquals("重试", d.actionLabel)
        assertNull(d.progress)
        assertTrue(d.showActionButton)
        assertTrue(d.showDismissButton)
    }

    @Test
    fun display_downloading_forced_stillNoDismiss() {
        val d = updateCardDisplay(info(forced = true), UpdatePhase.DOWNLOADING, 0.3f)
        assertEquals("正在下载 v1.4.3（30%）", d.fact)
        assertFalse(d.showActionButton)
        assertFalse(d.showDismissButton)
        assertTrue(d.pinned)
    }

    // ---------- 建卡 buildUpdateCard ----------

    @Test
    fun build_available_fields() {
        val card = buildUpdateCard(info(), OtaDownloader.State.Idle, 0, false)
        assertEquals("update|547", card.id)
        assertEquals("update|547", card.sourceKey)
        assertEquals(SmartCardKind.UPDATE, card.kind)
        assertEquals("应用更新", card.sourceLabel)
        assertEquals("有新版本 v1.4.3", card.fact)
        assertNull(card.prep)
        assertNull(card.targetApp)
        assertEquals("去更新", card.actionLabel)
        assertEquals(Long.MAX_VALUE, card.sortMs)
        assertEquals(Long.MAX_VALUE, card.deadlineMs)
        assertEquals(SmartCardAction.Update(info()), card.action)
        assertNull(card.progress)
        assertFalse(card.hideActionButton)
        assertFalse(card.hideDismissButton)
        assertFalse(card.pinned)
    }

    @Test
    fun build_forced_sortMinPinnedNoDismiss() {
        val card = buildUpdateCard(
            info(forced = true), OtaDownloader.State.Idle, 0, false
        )
        assertEquals(Long.MIN_VALUE, card.sortMs)
        assertTrue(card.pinned)
        assertTrue(card.hideDismissButton)
        assertFalse(card.hideActionButton)
        assertEquals("有新版本 v1.4.3，必须更新", card.fact)
    }

    @Test
    fun build_downloaded_startsAtDownloadedStep() {
        // 已下载过同版本包：初始状态就是「下载好了」＋「安装」，不重新下载。
        val card = buildUpdateCard(info(), OtaDownloader.State.Idle, 547, true)
        assertEquals("下载好了", card.fact)
        assertEquals("安装", card.actionLabel)
    }

    @Test
    fun build_downloading_progressOnCard() {
        val card = buildUpdateCard(
            info(), OtaDownloader.State.Downloading(0.6f), 547, false
        )
        assertEquals("正在下载 v1.4.3（60%）", card.fact)
        assertEquals(0.6f, card.progress)
        assertTrue(card.hideActionButton)
        assertFalse(card.hideDismissButton)
    }

    // ---------- 排序／可见／抑制 ----------

    @Test
    fun sort_updateAfterTimedCardsBeforeGuide() {
        val update = buildUpdateCard(info(), OtaDownloader.State.Idle, 0, false)
        val sorted = sortSmartCards(listOf(guideCard(), update, billCard()))
        assertEquals(listOf("bill", "update|547", "guide"), sorted.map { it.id })
    }

    @Test
    fun sort_forcedUpdateFirst() {
        val forced = buildUpdateCard(
            info(forced = true), OtaDownloader.State.Idle, 0, false
        )
        val sorted = sortSmartCards(listOf(billCard(), forced, guideCard()))
        assertEquals("update|547", sorted.first().id)
    }

    @Test
    fun visible_doneSuppressesOnlyThatVersion() {
        val now = 1_000L
        val card547 = buildUpdateCard(info(547), OtaDownloader.State.Idle, 0, false)
        val card548 = buildUpdateCard(info(548), OtaDownloader.State.Idle, 0, false)
        // 547 点了「我知道了」：547 不出，548 照常出（新版本是新 id）。
        val visible = visibleSmartCards(
            listOf(card547, card548),
            doneIds = setOf("update|547"),
            skippedIds = emptyList(),
            nowMs = now
        )
        assertEquals(listOf("update|548"), visible.map { it.id })
    }

    @Test
    fun kindRank_updateBetweenDayOverviewAndDefaultLauncher() {
        assertTrue(kindRank(SmartCardKind.DAY_OVERVIEW) < kindRank(SmartCardKind.UPDATE))
        assertTrue(kindRank(SmartCardKind.UPDATE) < kindRank(SmartCardKind.DEFAULT_LAUNCHER))
        assertTrue(kindRank(SmartCardKind.DEFAULT_LAUNCHER) < kindRank(SmartCardKind.GUIDE))
        assertTrue(kindRank(SmartCardKind.NOTIFICATION) < kindRank(SmartCardKind.UPDATE))
    }

    @Test
    fun isUserMatter_updateIsAppPushed() {
        // 升级卡是应用推的（不是用户的事项提醒）：划过就丢，不沉底。
        assertFalse(isUserMatterCard(SmartCardKind.UPDATE))
    }

    @Test
    fun shouldKeepCardAfterAction_updateAlwaysTrue() {
        // 点「去更新」／「安装」／「重试」成功都不摘卡：一张卡走完全程。
        val card = buildUpdateCard(info(), OtaDownloader.State.Idle, 0, false)
        assertTrue(SmartCardActions.shouldKeepCardAfterAction(card, 1_000L))
        val forced = buildUpdateCard(
            info(forced = true), OtaDownloader.State.Idle, 0, false
        )
        assertTrue(SmartCardActions.shouldKeepCardAfterAction(forced, 1_000L))
    }

    @Test
    fun carouselShouldAutoRotate_pinnedPauses() {
        val bill = billCard()
        val forced = buildUpdateCard(
            info(forced = true), OtaDownloader.State.Idle, 0, false
        )
        val normal = buildUpdateCard(info(), OtaDownloader.State.Idle, 0, false)
        // 当前是强更卡：停住，不自动轮走。
        assertFalse(carouselShouldAutoRotate(listOf(forced, bill), 0))
        // 当前是普通卡：照常轮。
        assertTrue(carouselShouldAutoRotate(listOf(bill, normal), 0))
        // 只有一张：不轮（与旧口径一致）。
        assertFalse(carouselShouldAutoRotate(listOf(normal), 0))
        // 强更卡不在当前位：轮到它时才停，现在照常轮。
        assertTrue(carouselShouldAutoRotate(listOf(bill, forced), 0))
    }

    @Test
    fun updateCardId_format() {
        assertEquals("update|547", updateCardId(547))
        assertEquals("update|548", updateCardId(548))
    }

    // ---------- UI 层显示覆盖 overlayUpdateCardDisplay ----------

    @Test
    fun overlay_nonUpdateCardUnchanged() {
        val bill = billCard()
        assertEquals(
            bill,
            overlayUpdateCardDisplay(
                bill, OtaDownloader.State.Downloading(0.5f), 547, false
            )
        )
    }

    @Test
    fun overlay_downloadStateChange() {
        val built = buildUpdateCard(info(), OtaDownloader.State.Idle, 0, false)
        assertEquals("有新版本 v1.4.3", built.fact)
        val overlaid = overlayUpdateCardDisplay(
            built, OtaDownloader.State.Downloading(0.25f), 547, false
        )
        // 事实／进度跟着下载状态翻，身份与排序不动。
        assertEquals("正在下载 v1.4.3（25%）", overlaid.fact)
        assertEquals(0.25f, overlaid.progress)
        assertTrue(overlaid.hideActionButton)
        assertEquals("update|547", overlaid.id)
        assertEquals(built.sortMs, overlaid.sortMs)
        assertEquals(built.action, overlaid.action)
    }
}
