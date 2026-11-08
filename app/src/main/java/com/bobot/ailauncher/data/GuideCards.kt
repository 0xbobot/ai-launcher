package com.bobot.ailauncher.data

/**
 * 首次加载的引导卡片（设计稿 §4.7，448/v1.2.1 起；v3 按权限重做，455/v1.2.8，
 * Bob 2026-11-08 凌晨 verdict）：
 *
 * v3 不再按场景分四张，改按权限分、一权限一张（消息、通知、日历、联系人、
 * 电话拨号器）。每张讲三件事：这是什么（来源标签＝权限名）、为什么要连
 * （fact 先讲连上能得到什么）、连上之后会发生什么（fact＋prep：会出现
 * 哪些卡片、什么时候出现、数据只在本地处理）。场景（账单、体检预约、
 * 未接来电、快递航班）写进对应权限卡片的 fact 里，当“连上之后你会看到”
 * 的例子，不单独成卡。
 *
 * 文案按 Bob 的两条写（讲清楚、不用内部黑话、不造新词），不拿用户的
 * 真实短信内容当例子（没有尾号、金额、真实地点）。
 *
 * 复用真卡片同一套模型与叠层 UI（kind = GUIDE，动作为 Connect）；
 * 宿主在真卡片为空且引导未看过时才展示，见 SmartCardStackHost。
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
 * 当前 v3：五张按权限分的引导卡（455/v1.2.8 的文案）。
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
    actionLabel = "连接",
    sortMs = 0L,
    deadlineMs = Long.MAX_VALUE,
    action = SmartCardAction.Connect(connectorKey),
    sourceKey = id
)

/** 按权限分的引导卡片，固定顺序：消息、通知、日历、联系人、电话拨号器。 */
fun guideCards(): List<SmartCard> = listOf(
    guideCard(
        id = "guide|message",
        sourceLabel = "消息",
        fact = "连接消息后：信用卡快到期、体检预约快到了，我会提前提醒你。",
        prep = "读短信里的账单和预约，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_MESSAGE
    ),
    guideCard(
        id = "guide|notification",
        sourceLabel = "通知",
        fact = "连接通知后：没接到的电话、包裹到了、航班要飞了，我会给你一张卡片，点一下就能处理。",
        prep = "只读通知内容，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    ),
    guideCard(
        id = "guide|calendar",
        sourceLabel = "日历",
        fact = "连接日历后：会议会出现在简报里，到时间提醒你。",
        prep = "只读当天的日程，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_CALENDAR
    ),
    guideCard(
        id = "guide|contacts",
        sourceLabel = "联系人",
        fact = "连接联系人后：来电和短信里的号码会显示名字。",
        prep = "只用来把号码显示成名字，只在本地处理",
        connectorKey = UiPrefs.CONNECTOR_CONTACTS
    ),
    guideCard(
        id = "guide|phone",
        sourceLabel = "电话拨号器",
        fact = "回拨只进拨号盘，不会直接拨出去。",
        prep = "随通知一起开，不另要权限",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    )
)

/**
 * 可见的引导卡片（纯函数，单测锁死）：
 * 已接上的权限，其卡片不再出现；横滑跳过的只本轮沉底（下次还在）；
 * 点过“忽略”的永久不再出现。
 * 电话拨号器随通知一起开：通知一接上，电话拨号器卡也不再出现。
 */
fun visibleGuideCards(
    messageGranted: Boolean,
    notificationGranted: Boolean,
    calendarGranted: Boolean = false,
    contactsGranted: Boolean = false,
    swipedIds: Set<String> = emptySet(),
    dismissedIds: Set<String> = emptySet()
): List<SmartCard> = guideCards().filter { card ->
    val granted = when (card.id) {
        "guide|message" -> messageGranted
        "guide|notification", "guide|phone" -> notificationGranted
        "guide|calendar" -> calendarGranted
        "guide|contacts" -> contactsGranted
        else -> true
    }
    !granted && card.id !in swipedIds && card.id !in dismissedIds
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
    calendarGranted: Boolean,
    contactsGranted: Boolean
): Boolean = when (connectorKey) {
    UiPrefs.CONNECTOR_MESSAGE -> messageGranted
    UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE -> notificationGranted
    UiPrefs.CONNECTOR_CALENDAR -> calendarGranted
    UiPrefs.CONNECTOR_CONTACTS -> contactsGranted
    else -> true
}

/** 接上某个权限后，接下来会发生什么（一句话，写进“已连接”反馈卡）。 */
fun connectedNextStep(connectorKey: String): String = when (connectorKey) {
    UiPrefs.CONNECTOR_MESSAGE -> "有账单或预约短信时，这里会提前提醒你"
    UiPrefs.CONNECTOR_NOTIFICATION -> "有未接来电、包裹或航班提醒时，这里会出现一张卡片，点一下就能处理"
    UiPrefs.CONNECTOR_CALENDAR -> "会议会出现在简报里，到时间提醒你"
    UiPrefs.CONNECTOR_CONTACTS -> "来电和短信里的号码会显示名字"
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
    /** 接上之前真实已接源个数（用户点“继续”那一刻记下） */
    val previousLevel: Int
) {
    /** 本次接上让智能涨了多少（正常为 1；通知＋电话拨号器同涨时为 2） */
    val levelDelta: Int get() = level - previousLevel
    /** 动效触发标志：反馈一出现，UI 就播一次庆祝动效 */
    val celebrate: Boolean = true
}

/**
 * 等级行文案（纯函数，单测逐字锁死）：“智能 +1 · 已接 3/5 个源”。
 * total 默认取 SMART_SOURCE_ORDER 的个数（五个源），不写死 5。
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
