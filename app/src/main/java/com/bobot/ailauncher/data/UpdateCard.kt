package com.bobot.ailauncher.data

/**
 * 升级提醒做成智能卡片（第四十五批，Bob 2026-11-10 定稿，设计见
 * upgrade-reminder-card-design.md §2–§10；智能卡片设计稿 §4.9）：
 * 新 kind UPDATE 进卡片叠层，TODAY「有新版本」行与 UpdateDialog 退役。
 *
 * 本文件只放纯逻辑（状态机／文案／建卡／显示覆盖），不碰 Android 框架，
 * JVM 单测锁死（UpdateCardTest）：
 * - 出现：OtaUpdater.latestAvailable 非空且当前 versionCode 小于它；
 *   仓库 refresh() 里读 latestAvailable.value 拼卡（见
 *   SmartCardRepository.updateCard），不另起检查、不另加轮询。
 * - 一张卡走完整个更新：有新版本 → 正在下载 → 下载好了 → 安装；
 *   下载失败留在失败态、右按钮变「重试」；点「去更新」不摘卡，
 *   只有装上新版（versionCode 追上）或点「我知道了」才消失。
 * - 同一版本 id 固定（update|<versionCode>）；新版本＝新 id＝新卡，
 *   「我知道了」只压住那一个版本（done_ 按 id 记、永不过期）。
 * - 强更（forceUpdate=true）：卡片置顶、不可划走、不出「我知道了」，
 *   只剩「去更新」；不做全屏阻塞。
 *
 * 显示分两层（同一套纯函数，不分叉）：
 * - 仓库层：refresh() 建卡时按 OtaDownloader.state 当下快照算一次；
 * - UI 层：SmartCardStackHost 订阅 OtaDownloader.state，状态变了就用
 *   overlayUpdateCardDisplay 按同一口径覆盖事实／按钮／进度条——下载
 *   进度每 200ms 推一次，UI 层只重组、不触发仓库 refresh（refresh
 *   一次要重查日历／短信缓存／复盘卡，每 200ms 跑一轮太贵）。
 */

/** 升级卡来源标签（设计 §2：固定「应用更新」） */
const val UPDATE_SOURCE_LABEL = "应用更新"

/** 升级卡右按钮文案（设计 §2／§4）：去更新／安装／重试 */
const val UPDATE_ACTION_LABEL = "去更新"
const val UPDATE_INSTALL_LABEL = "安装"
const val UPDATE_RETRY_LABEL = "重试"

/** 升级卡 id 与去重键（设计 §2：update|<versionCode>，同一版本固定） */
fun updateCardId(versionCode: Int): String = "update|$versionCode"

/**
 * 升级卡下载状态（纯数据）：AVAILABLE＝有新版可下；DOWNLOADING＝下载中；
 * DOWNLOADED＝包已下好（可安装）；FAILED＝下载失败（可重试）。
 * 注意：OtaDownloader.state 是全局的（不分版本）——只有目标版本
 * （OtaUpdater.downloadTargetVersion）与本卡版本一致时，下载中／成功／
 * 失败才算本卡的状态；否则（例如上一版下完没装、新版又来了）按 AVAILABLE
 * 算，不拿旧版的状态冒充新版。
 */
enum class UpdatePhase {
    AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

/**
 * 按当下状态算升级卡处在哪一步（纯函数，单测锁死）：
 * - 已下载好同版本包（isDownloadedVersion）→ DOWNLOADED；
 * - 否则下载器状态只有目标版本与本卡版本一致时才认：Downloading→
 *   DOWNLOADING、Success→DOWNLOADED、Failed→FAILED；
 * - 其余（Idle、目标版本对不上、没下过）→ AVAILABLE。
 * isDownloaded 优先于 Failed：包已在盘里时，之前一次失败的记录不
 * 应把卡片打回失败态（下次点「安装」直接装盘里的包）。
 */
fun updateDownloadPhase(
    downloadState: OtaDownloader.State,
    downloadTargetVersion: Int,
    cardVersionCode: Int,
    isDownloaded: Boolean
): UpdatePhase {
    if (isDownloaded) return UpdatePhase.DOWNLOADED
    if (downloadTargetVersion != cardVersionCode) return UpdatePhase.AVAILABLE
    return when (downloadState) {
        is OtaDownloader.State.Downloading -> UpdatePhase.DOWNLOADING
        is OtaDownloader.State.Success -> UpdatePhase.DOWNLOADED
        is OtaDownloader.State.Failed -> UpdatePhase.FAILED
        is OtaDownloader.State.Idle -> UpdatePhase.AVAILABLE
    }
}

/** 升级卡该显示什么（纯数据）：事实／右按钮文案／进度／两个按钮显隐／置顶 */
data class UpdateCardDisplay(
    val fact: String,
    val actionLabel: String,
    /** 下载进度 0..1：只有 DOWNLOADING 时非空，UI 在事实下方画进度条 */
    val progress: Float?,
    /** DOWNLOADING 时 false：右按钮先收起来，下载中没什么可点的 */
    val showActionButton: Boolean,
    /** 强更时 false：不出「我知道了」 */
    val showDismissButton: Boolean,
    /** 强更时 true：置顶、自动轮换停住（见 carouselShouldAutoRotate） */
    val pinned: Boolean,
    val phase: UpdatePhase
)

/**
 * 升级卡文案与按钮（纯函数，单测逐字锁死；设计 §2／§4／§6／§7）：
 * - 平时：「有新版本 vX.X.X」＋「去更新」；
 * - 强更：「有新版本 vX.X.X，必须更新」（＋发布时给定的一行原因，
 *   原因目前没有下发通道——update.json 里没有原因字段，changelog
 *   是开发口径不贴卡上；forceReason 为空时只写必须更新这一句，
 *   Bob 要加原因时再补下发字段，不在本批硬凑）；
 * - 下载中：「正在下载 vX.X.X」＋进度条，右按钮收起；
 * - 下完：「下载好了」＋「安装」；
 * - 失败：「下载失败」＋「重试」。
 * 只写版本号，不贴 changelog（设计 §7：changelog 是开发口径，
 * 贴到卡上违反用户文案两原则）。
 */
fun updateCardDisplay(
    info: OtaInfo,
    phase: UpdatePhase,
    progress: Float = 0f,
    forceReason: String? = null
): UpdateCardDisplay {
    val versionName = info.versionName
    val forced = info.forceUpdate
    return when (phase) {
        UpdatePhase.AVAILABLE -> {
            val fact = if (forced) {
                val reason = forceReason?.trim().orEmpty()
                if (reason.isNotEmpty()) {
                    "有新版本 v$versionName，必须更新\n$reason"
                } else {
                    "有新版本 v$versionName，必须更新"
                }
            } else {
                "有新版本 v$versionName"
            }
            UpdateCardDisplay(
                fact = fact,
                actionLabel = UPDATE_ACTION_LABEL,
                progress = null,
                showActionButton = true,
                showDismissButton = !forced,
                pinned = forced,
                phase = phase
            )
        }
        UpdatePhase.DOWNLOADING -> {
            UpdateCardDisplay(
                fact = "正在下载 v$versionName",
                actionLabel = UPDATE_ACTION_LABEL,
                progress = progress.coerceIn(0f, 1f),
                showActionButton = false,
                // 下载中也可以点「我知道了」关卡片（只关卡片，不取消下载，
                // 设计 §4：下载是后台的，下载完照常弹安装）；强更仍不出。
                showDismissButton = !forced,
                pinned = forced,
                phase = phase
            )
        }
        UpdatePhase.DOWNLOADED -> UpdateCardDisplay(
            fact = "下载好了",
            actionLabel = UPDATE_INSTALL_LABEL,
            progress = null,
            showActionButton = true,
            showDismissButton = !forced,
            pinned = forced,
            phase = phase
        )
        UpdatePhase.FAILED -> UpdateCardDisplay(
            fact = "下载失败",
            actionLabel = UPDATE_RETRY_LABEL,
            progress = null,
            showActionButton = true,
            showDismissButton = !forced,
            pinned = forced,
            phase = phase
        )
    }
}

/**
 * 建一张升级卡（纯函数，仓库层调用）：
 * - id/sourceKey＝update|<versionCode>；kind＝UPDATE；sourceLabel＝应用更新；
 * - 无 prep（不用大模型）、无 targetApp 小标（要更新的是启动器自己，
 *   没有别的目标应用可标）、无截止点（deadlineMs＝Long.MAX_VALUE，
 *   只随版本走：装上新版就消失，来新版就换新卡）；
 * - 排序：平时 sortMs＝Long.MAX_VALUE（无截止点组，有时效真卡之后、
 *   引导卡之前；与设默认卡同值时按 kindRank 分先后，UPDATE 在
 *   DEFAULT_LAUNCHER 之前）；强更 sortMs＝Long.MIN_VALUE（置顶）。
 * - 动作＝SmartCardAction.Update(info)：点右按钮时按当下状态分流
 *   （见 SmartCardActions.performUpdateAction），成功不摘卡
 *   （shouldKeepCardAfterAction 对 UPDATE 恒 true）。
 */
fun buildUpdateCard(
    info: OtaInfo,
    downloadState: OtaDownloader.State,
    downloadTargetVersion: Int,
    isDownloaded: Boolean,
    forceReason: String? = null
): SmartCard {
    val phase = updateDownloadPhase(
        downloadState, downloadTargetVersion, info.versionCode, isDownloaded
    )
    val progress = (downloadState as? OtaDownloader.State.Downloading)
        ?.progress?.takeIf { phase == UpdatePhase.DOWNLOADING }
    val display = updateCardDisplay(info, phase, progress ?: 0f, forceReason)
    return SmartCard(
        id = updateCardId(info.versionCode),
        kind = SmartCardKind.UPDATE,
        sourceLabel = UPDATE_SOURCE_LABEL,
        fact = display.fact,
        prep = null,
        actionLabel = display.actionLabel,
        sortMs = if (info.forceUpdate) Long.MIN_VALUE else Long.MAX_VALUE,
        deadlineMs = Long.MAX_VALUE,
        action = SmartCardAction.Update(info),
        sourceKey = updateCardId(info.versionCode),
        progress = display.progress,
        hideActionButton = !display.showActionButton,
        hideDismissButton = !display.showDismissButton,
        pinned = display.pinned
    )
}

/**
 * UI 层显示覆盖（纯函数，SmartCardStackHost 调用）：下载状态变了时，
 * 按同一口径重算事实／按钮／进度条，覆盖仓库建卡时的快照；id/kind/
 * sourceKey/sortMs/deadlineMs/action 不动（身份与排序只认仓库那张卡）。
 * 不是 UPDATE 卡、或动作里没有 OtaInfo 时原样返回。
 */
fun overlayUpdateCardDisplay(
    card: SmartCard,
    downloadState: OtaDownloader.State,
    downloadTargetVersion: Int,
    isDownloaded: Boolean,
    forceReason: String? = null
): SmartCard {
    val info = (card.action as? SmartCardAction.Update)?.info ?: return card
    val phase = updateDownloadPhase(
        downloadState, downloadTargetVersion, info.versionCode, isDownloaded
    )
    val progress = (downloadState as? OtaDownloader.State.Downloading)
        ?.progress?.takeIf { phase == UpdatePhase.DOWNLOADING }
    val display = updateCardDisplay(info, phase, progress ?: 0f, forceReason)
    return card.copy(
        fact = display.fact,
        actionLabel = display.actionLabel,
        progress = display.progress,
        hideActionButton = !display.showActionButton,
        hideDismissButton = !display.showDismissButton,
        pinned = display.pinned
    )
}
