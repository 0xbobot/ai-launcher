package com.bobot.ailauncher.data

/**
 * 首次加载的引导卡片（设计稿 §4.7，448/v1.2.1，Bob 2026-11-07 晚定）：
 * 用户升到带卡片叠层的版本后第一次看到首页时，叠层位置出现一叠引导卡片
 * （而不是空着）。只出现一次。
 *
 * - 每张讲一个智能场景，生动一点、一句话说清它能帮什么忙，下面附需要
 *   连接哪个来源＋一个“连接”按钮；场景按 §4 的四种来。
 * - 文案按 Bob 的两条写（讲清楚、不用内部黑话、不造新词），不拿用户的
 *   真实短信内容当例子（没有尾号、金额、真实地点）。
 * - 复用真卡片同一套模型与叠层 UI（kind = GUIDE，动作为 Connect）；
 *   宿主在真卡片为空且引导未看过时才展示，见 SmartCardStackHost。
 * - 接上某个来源后，对应引导卡消失（宿主按授权状态过滤）；有真事项时
 *   真卡片顶掉整叠引导卡（宿主只在真卡片为空时展示引导卡）。
 */
private fun guideCard(
    id: String,
    scenarioLabel: String,
    fact: String,
    sourceLine: String,
    connectorKey: String
): SmartCard = SmartCard(
    id = id,
    kind = SmartCardKind.GUIDE,
    sourceLabel = scenarioLabel,
    fact = fact,
    prep = sourceLine,
    actionLabel = "连接",
    sortMs = 0L,
    deadlineMs = Long.MAX_VALUE,
    action = SmartCardAction.Connect(connectorKey),
    sourceKey = id,
    appearFromMs = 0L
)

/** 四张引导卡（固定顺序）：账单到期、体检预约、未接来电、快递航班 */
fun guideCards(): List<SmartCard> = listOf(
    guideCard(
        id = "guide|bill",
        scenarioLabel = "账单到期",
        fact = "信用卡到期前两天提醒你，点一下就能去还款",
        sourceLine = "需要连接消息",
        connectorKey = UiPrefs.CONNECTOR_MESSAGE
    ),
    guideCard(
        id = "guide|appointment",
        scenarioLabel = "体检预约",
        fact = "体检或预约快到了，提前一天告诉你，还附上今晚要做的准备",
        sourceLine = "需要连接消息",
        connectorKey = UiPrefs.CONNECTOR_MESSAGE
    ),
    guideCard(
        id = "guide|missed-call",
        scenarioLabel = "未接来电",
        fact = "有未接来电时显示号码，点一下就能回拨",
        sourceLine = "需要连接通知",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    ),
    guideCard(
        id = "guide|delivery",
        scenarioLabel = "快递航班",
        fact = "包裹到了提醒你去取，航班起飞前提醒你去值机",
        sourceLine = "需要连接通知",
        connectorKey = UiPrefs.CONNECTOR_NOTIFICATION
    )
)

/**
 * 当前应展示的引导卡（纯函数）：已接上来源的卡不再出现（接上后这张引导卡
 * 即完成使命）；滑走过的卡本轮不再出现（滑完全部即看完，宿主随即记一次）。
 * 两个来源都已接上时返回空表——没什么可引导的。
 */
fun visibleGuideCards(
    messageGranted: Boolean,
    notificationGranted: Boolean,
    swipedIds: Set<String> = emptySet()
): List<SmartCard> = guideCards().filter { card ->
    val action = card.action as? SmartCardAction.Connect ?: return@filter false
    val granted = when (action.connectorKey) {
        UiPrefs.CONNECTOR_MESSAGE -> messageGranted
        UiPrefs.CONNECTOR_NOTIFICATION -> notificationGranted
        else -> true
    }
    !granted && card.id !in swipedIds
}
