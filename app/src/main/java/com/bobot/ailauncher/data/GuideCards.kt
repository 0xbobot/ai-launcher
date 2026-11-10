package com.bobot.ailauncher.data

/**
 * 首次加载的引导卡片（设计稿 §4.7，448/v1.2.1 起；v3 按权限重做，455/v1.2.8，
 * Bob 2026-11-08 凌晨 verdict）：
 *
 * v3 不再按场景分，改按权限分、一权限一张（消息、通知、日历、电话拨号器）。
 * 每张讲三件事：这是什么（来源标签＝权限名）、为什么要连（fact 先讲连上
 * 能得到什么）、连上之后会发生什么（fact＋prep：会出现哪些卡片、什么
 * 时候出现、数据只在本地处理）。场景（账单、体检预约、未接来电、快递
 * 航班）写进对应权限卡片的 fact 里，当“连上之后你会看到”的例子，不单
 * 独成卡。联系人已取消（第十四批，Bob 2026-11-10 23:45 定：不是必须的，
 * 不申请 READ_CONTACTS）——没有联系人引导卡。
 *
 * 文案按 Bob 的两条写（讲清楚、不用内部黑话、不造新词），不拿用户的
 * 真实短信内容当例子（没有尾号、金额、真实地点）。
 *
 * 复用真卡片同一套模型与叠层 UI（kind = GUIDE，动作为 Connect）；
 * 宿主在真卡片为空且引导未看过时才展示，见 SmartCardStackHost。
 *
 * 单卡提醒、轮换推（第十四批，Bob 2026-11-10 23:07 定）：一次只推一张，
 * 接上一个来源后继续推下一张；点「我知道了」或横滑＝被拒一次，放队尾、
 * 其他来源推完后再回来一次，再拒就本版不再推（等引导换版）。轮换状态
 * （队列＋被拒次数）存在 SmartCardRepository 里、按 GUIDE_VERSION 存，
 * 换版自动重来。
 *
 * 接上某个权限后，对应引导卡先显示“已连接”＋接下来会发生什么（3 秒），
 * 再消失；其他权限的引导卡留着（452 的问题：连一个权限整叠全消失，
 * 根因在 SmartCardStackHost：已接上的权限被静默过滤＋guide_seen 在
 * 引导列表一空就记下，全程没有任何反馈）。有真事项时真卡片顶掉整叠
 * 引导卡（宿主只在真卡片为空时展示引导卡）。
 */
/**
 * 引导内容版本号（设计稿 §4.7，456/v1.2.9，Bob 2026-11-08 定）：
 * 换版时只改这一个数。已看过的标记按版本记（guide_seen_version），
 * 不是终身一次——换版后自动重看一次，每版引导只出现一次。
 * 当前 v3：四张按权限分的引导卡（455/v1.2.8 的文案，第十四批删联系人）。
 */
const val GUIDE_VERSION = 3

/** 存量旧版布尔 guide_seen == true 视为已看 v2（v3 之前最新版引导）。 */
const val LEGACY_GUIDE_SEEN_VERSION = 2

/**
 * 该版本引导是否该出现：没记过，或记的版本比当前旧，就出现；
 * 记的版本等于当前（看完本版）或更新，就不再出现。
 * currentVersion 只为单测“未来换版”留口子，生产调用不传。
 */
fun shouldShowGuideVersion(
    seenVersion: Int?,
    currentVersion: Int = GUIDE_VERSION
): Boolean = seenVersion == null || seenVersion < currentVersion

/**
 * 迁移：旧版布尔 guide_seen 与新版整数 guide_seen_version 合并成一个
 * 已看版本。整数在就以整数为准；只有布尔 true 时视为已看 v2——
 * 452 记下的旧版 guide_seen 不挡 v3（Bob 设备上记的就是这个）。
 */
fun migrateLegacyGuideSeen(legacySeen: Boolean, storedVersion: Int?): Int? =
    storedVersion ?: if (legacySeen) LEGACY_GUIDE_SEEN_VERSION else null

private fun guideCard(
    id: String,
    sourceLabel: String,
    fact: String,
    prep: String,
    connectorKey: String
): SmartCard = SmartCard(
    id = id,
    kind = SmartCardKind.GUIDE,
    sourceLabel = sourceLabel,
    fact = fact,
    prep = prep,
    actionLabel = "连接$sourceLabel",
    sortMs = 0L,
    deadlineMs = Long.MAX_VALUE,
    action = SmartCardAction.Connect(connectorKey),
    sourceKey = id
)

/**
 * 按权限分的引导卡片，顺序与智能区同一份 SMART_SOURCE_ORDER（第十四批，
 * Bob 2026-11-10 23:45 定统一）：消息、通知、日历、电话拨号器。
 * 顺序由 SMART_SOURCE_ORDER 决定，这里只负责每张卡的文案——改顺序只改
 * 一处，设置页“智能”区行序与引导卡不会两头各写一份再对不上。
 * 电话拨号器卡随通知一起开（action key 是通知），但它是独立的一张卡。
 */
fun guideCards(): List<SmartCard> = SMART_SOURCE_ORDER.map(::guideCardFor)

private fun guideCardFor(connectorKey: String): SmartCard = when (connectorKey) {
    UiPrefs.CONNECTOR_MESSAGE -> guideCard(
        id = "guide|message",
        sourceLabel = "消息",
        fact = "连接消息后：信用卡快到期、体检预约快到了，我会提前提醒你。",
        prep = "读短信里的账单和预约，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_MESSAGE
    )
    UiPrefs.CONNECTOR_NOTIFICATION -> guideCard(
        id = "guide|notification",
        sourceLabel = "通知",
        fact = "连接通知后：没接到的电话、包裹到了、航班要飞了，我会给你一张卡片，点一下就能处理。",
        prep = "只读通知内容，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    )
    UiPrefs.CONNECTOR_CALENDAR -> guideCard(
        id = "guide|calendar",
        sourceLabel = "日历",
        fact = "连接日历后：会议会出现在简报里，到时间提醒你。",
        prep = "只读当天的日程，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_CALENDAR
    )
    UiPrefs.CONNECTOR_PHONE -> guideCard(
        id = "guide|phone",
        sourceLabel = "电话拨号器",
        fact = "回拨只进拨号盘，不会直接拨出去。",
        prep = "随通知一起开，不另要权限",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    )
    else -> error("guideCards: SMART_SOURCE_ORDER 里有没配引导卡的来源 $connectorKey")
}

/**
 * 一张引导卡本版最多被拒几次（第十四批，设计稿 §4.7 轮换推）：
 * 第 1 次放队尾、第 2 次本版不再推（等引导换版）。
 */
const val GUIDE_MAX_REJECTS = 2

/**
 * 被拒后队列怎么变（纯函数，单测锁死）：第 1 次（priorRejects = 0）
 * 放队尾，第 2 次（priorRejects = 1）移出队列；不在队列里的 id 不动。
 */
fun guideQueueAfterReject(
    queue: List<String>,
    id: String,
    priorRejects: Int
): List<String> {
    if (id !in queue) return queue
    val rest = queue - id
    return if (priorRejects + 1 >= GUIDE_MAX_REJECTS) rest else rest + id
}

/**
 * 轮换推当前该推哪一张（纯函数，单测锁死）：队列里第一张仍符合出现条件
 * 的卡（没接上、没被永久记下）。一次只推一张，宿主只展示这一张。
 */
fun currentGuideCard(
    queue: List<String>,
    eligible: List<SmartCard>
): SmartCard? = queue.firstNotNullOfOrNull { id ->
    eligible.find { it.id == id }
}

/**
 * 符合出现条件的引导卡片（纯函数，单测锁死）：
 * 已接上的权限，其卡片不再出现；处理完（接上后反馈已给完、或反馈卡被
 * 横滑走）永久记下的，不再出现。点「我知道了」/横滑不再走这里——那是
 * 轮换推的“被拒一次”（见 guideQueueAfterReject），队列会把它放队尾。
 * 电话拨号器随通知一起开：通知一接上，电话拨号器卡也不再出现。
 */
fun visibleGuideCards(
    messageGranted: Boolean,
    notificationGranted: Boolean,
    calendarGranted: Boolean = false,
    dismissedIds: Set<String> = emptySet()
): List<SmartCard> = guideCards().filter { card ->
    val granted = when (card.id) {
        "guide|message" -> messageGranted
        "guide|notification", "guide|phone" -> notificationGranted
        "guide|calendar" -> calendarGranted
        else -> true
    }
    !granted && card.id !in dismissedIds
}

/** 某张引导卡用的连接器 key；电话拨号器卡随通知一起开，key 是通知。 */
fun connectorKeyForGuideId(id: String): String? =
    guideCards().find { it.id == id }?.let {
        (it.action as? SmartCardAction.Connect)?.connectorKey
    }

/** 用某个连接器的引导卡 id（电话拨号器与通知共用通知 key，故可能多张）。 */
fun guideIdsForConnector(connectorKey: String): List<String> =
    guideCards().filter {
        (it.action as? SmartCardAction.Connect)?.connectorKey == connectorKey
    }.map { it.id }

/** 某个连接器是否已接上；电话拨号器随通知一起开，跟通知走。 */
fun isGuideConnectorGranted(
    connectorKey: String,
    messageGranted: Boolean,
    notificationGranted: Boolean,
    calendarGranted: Boolean
): Boolean = when (connectorKey) {
    UiPrefs.CONNECTOR_MESSAGE -> messageGranted
    UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE -> notificationGranted
    UiPrefs.CONNECTOR_CALENDAR -> calendarGranted
    else -> true
}

/** 接上某个权限后，接下来会发生什么（一句话，写进“已连接”反馈卡）。 */
fun connectedNextStep(connectorKey: String): String = when (connectorKey) {
    UiPrefs.CONNECTOR_MESSAGE -> "有账单或预约短信时，这里会提前提醒你"
    UiPrefs.CONNECTOR_NOTIFICATION -> "有未接来电、包裹或航班提醒时，这里会出现一张卡片，点一下就能处理"
    UiPrefs.CONNECTOR_CALENDAR -> "会议会出现在简报里，到时间提醒你"
    UiPrefs.CONNECTOR_PHONE -> "回拨只进拨号盘，不会直接拨出去"
    else -> ""
}

/**
 * “已连接”反馈停留时长（设计稿 §4.7 v3，455/v1.2.8 起：3 秒后离开；
 * 第八批不改时长，只改这一刻的样子）。宿主 delay 用这个常量，单测锁死。
 */
const val CONNECTED_FEEDBACK_MS = 3000L

/**
 * “已连接”庆祝反馈的状态（设计稿 §4.7，第八批 2026-11-08 Bob verdict：
 * “已连接”那一刻要让人振奋，不只是换文案）。
 *
 * 动效本身 JVM 单测测不到，锁死方式是状态机：反馈状态必须携带
 * [celebrate]（动效触发标志）与等级增量（[levelDelta]，由真实已接源
 * 个数算出，不是写死的 +1——例如接通知时电话拨号器开关已开，
 * 等级一次涨 2）。UI 层见 SmartCardView：celebrate 为真时整卡轻弹
 * （弹簧回弹）＋金色对勾弹入＋金色等级行；视觉以代码复核＋Bob 真机为准。
 * [level] 必须复用 [connectedSourceCount]（457 的智能等级同一处计数），
 * 不许另写一套、更不许写死。
 */
data class ConnectedFeedback(
    /** 已按 connectedGuideCard 换成“已连接”文案的那张卡 */
    val card: SmartCard,
    /** 接上之后真实已接源个数（connectedSourceCount） */
    val level: Int,
    /** 接上之前真实已接源个数（用户点“下一步”那一刻记下） */
    val previousLevel: Int
) {
    /** 本次接上让智能涨了多少（正常为 1；通知＋电话拨号器同涨时为 2） */
    val levelDelta: Int get() = level - previousLevel
    /** 动效触发标志：反馈一出现，UI 就播一次庆祝动效 */
    val celebrate: Boolean = true
}

/**
 * 等级行文案（纯函数，单测逐字锁死）：“智能 +1 · 已接 3/4 个源”。
 * total 默认取 SMART_SOURCE_ORDER 的个数（四个源），不写死 4。
 */
fun celebrationLevelText(
    level: Int,
    previousLevel: Int,
    total: Int = SMART_SOURCE_ORDER.size
): String = if (level > previousLevel) {
    "智能 +${level - previousLevel} · 已接 $level/$total 个源"
} else {
    "已接 $level/$total 个源"
}

/** 由一张引导卡＋接上前后的真实等级，造出带庆祝标志的反馈状态。 */
fun connectedFeedback(
    card: SmartCard,
    level: Int,
    previousLevel: Int
): ConnectedFeedback = ConnectedFeedback(
    card = connectedGuideCard(card),
    level = level,
    previousLevel = previousLevel
)

/**
 * 接上权限后显示的“已连接”反馈卡：同一张卡（id 不变），只换文案——
 * fact 是“已连接”，prep 是一句接下来会发生什么。没有动作按钮，
 * 3 秒后自动离开（宿主负责记永久忽略）。第八批：文案不变，庆祝感由
 * [ConnectedFeedback]（对勾弹入＋金色等级行＋整卡轻弹）给，不换文案。
 */
fun connectedGuideCard(card: SmartCard): SmartCard {
    val key = (card.action as? SmartCardAction.Connect)?.connectorKey
        ?: return card
    // 电话拨号器卡随通知一起开（action key 是通知），但“已连接”反馈要讲
    // 电话拨号器自己的下一步——按卡片 id 分，不是按 action key 分。
    val step = if (card.id == "guide|phone") {
        connectedNextStep(UiPrefs.CONNECTOR_PHONE)
    } else {
        connectedNextStep(key)
    }
    return card.copy(
        fact = "已连接",
        prep = step
    )
}
