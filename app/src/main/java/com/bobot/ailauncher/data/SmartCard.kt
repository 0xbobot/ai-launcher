package com.bobot.ailauncher.data

import android.app.PendingIntent

/**
 * 首页智能卡片（第一阶段，447/v1.2.0，设计稿 smart-card-design.md §4）：
 * 从零做的独立叠层，与简报并存，不长在简报里；第二阶段成熟后才替换简报。
 *
 * 卡片结构固定：来源标签＋一句话事实（含截止点）＋（可选）一行准备清单＋
 * 一个下一步动作按钮。没有标题党、没有解释行、没有“为什么是这条”的说明。
 *
 * 本文件只放数据模型与纯逻辑（排序/去重/跳过/过期），不碰 Android 框架，
 * JVM 单测锁死（SmartCardLogicTest）。
 */
enum class SmartCardKind {
    /** 账单到期（短信主路径，通知兜底） */
    BILL,
    /** 体检/预约准备（短信主路径，通知兜底） */
    APPOINTMENT,
    /** 未接来电回拨（只走通知，不读通话记录） */
    MISSED_CALL,
    /** 快递/外卖/航班等有时效的通知 */
    NOTIFICATION,
    /** 首次加载的引导卡（设计稿 §4.7）：介绍场景、引导接来源，不是一件真事项 */
    GUIDE
}

/** 卡片的下一步动作：点它就算处理完一张。 */
sealed class SmartCardAction {
    /** 打开短信会话（smsto: 地址） */
    data class SmsThread(val address: String) : SmartCardAction()

    /** 打开拨号器并填好号码（ACTION_DIAL，不直接拨号，不用权限） */
    data class Dial(val number: String) : SmartCardAction()

    /** 打开已装应用（去还款落到用户真正用的那家银行应用，见 SmartCardActions） */
    data class LaunchApp(val packageName: String) : SmartCardAction()

    /** 点通知自带的 contentIntent，直达应用内页面（快递取件码/航班值机等） */
    data class OpenNotification(val pendingIntent: PendingIntent?) : SmartCardAction()

    /** 引导卡的“连接”：去接上某个来源（UiPrefs 连接器 key）。
     * 不走 performAction——接来源要弹应用内显著披露/系统框，得由宿主
     * 拿 opener 执行；performAction 里遇见它只返回 false（防御分支）。 */
    data class Connect(val connectorKey: String) : SmartCardAction()
}

data class SmartCard(
    /** 稳定 id：同一件事刷新前后 id 不变（处理/跳过状态靠它持久化） */
    val id: String,
    val kind: SmartCardKind,
    /** 用户看到的来源标签：短信 / 未接来电 / 通知 */
    val sourceLabel: String,
    /** 一句话事实（含截止点），不写成“你的 XX”（身份未确认） */
    val fact: String,
    /** 可选的一行准备清单（如体检：今晚 20:00 后禁食 · 明天空腹去 · 带身份证） */
    val prep: String?,
    /** 下一步动作按钮文案：去还款 / 看预约 / 回拨 / 查看 */
    val actionLabel: String,
    /** 排序键：最早截止的在最上面（体检按禁食截止 20:00 排，比开始时间更早） */
    val sortMs: Long,
    /** 过了这个点卡片即消失（账单＝到期日 23:59；预约＝结束时间；未接来电＝通知后 24 小时） */
    val deadlineMs: Long,
    val action: SmartCardAction,
    /** 去重键：同一件事在短信和通知里同时出现只留一条（短信那条为准） */
    val sourceKey: String,
    /** 未接来电的联系人姓名（接了联系人才有，没有只显示号码） */
    val contactName: String? = null,
    /** 出现窗口起点：账单＝到期前 2 天，预约＝开始前 24 小时，其他＝来源到达时间 */
    val appearFromMs: Long = 0L
) {
    /** 未接来电卡片显示的称呼：有人名显示人名，否则显示号码 */
    val displayName: String
        get() = contactName ?: when (action) {
            is SmartCardAction.Dial -> action.number
            else -> ""
        }
}

/** 排序并列时的种类优先级：有明确下一步动作的在前（设计稿 §4.3） */
internal fun kindRank(kind: SmartCardKind): Int = when (kind) {
    SmartCardKind.BILL -> 0
    SmartCardKind.APPOINTMENT -> 1
    SmartCardKind.MISSED_CALL -> 2
    SmartCardKind.NOTIFICATION -> 3
    SmartCardKind.GUIDE -> 4
}

/**
 * 排序（纯函数）：按截止点排，最早截止的在最上面；截止点相同按种类优先级，
 * 再相同按 id（稳定，不抖动）。
 */
fun sortSmartCards(cards: List<SmartCard>): List<SmartCard> =
    cards.sortedWith(
        compareBy<SmartCard> { it.sortMs }
            .thenBy { kindRank(it.kind) }
            .thenBy { it.id }
    )

/**
 * 去重（纯函数）：同一 sourceKey 只留一条，候选顺序即优先级——仓库层保证
 * 短信来源的候选在前，所以先到的就是短信那条（设计稿 §4.3：同一件事只排
 * 一次，以短信那条为准）。
 */
fun dedupeSmartCards(cards: List<SmartCard>): List<SmartCard> {
    val seen = mutableSetOf<String>()
    return cards.filter { seen.add(it.sourceKey) }
}

/** 是否已过截止点（纯函数）：过了即消失，最迟 1 分钟内摘掉（分钟 ticker 驱动） */
fun isCardExpired(card: SmartCard, nowMs: Long): Boolean = nowMs > card.deadlineMs

/**
 * 跳过后的顺序（纯函数）：跳过的卡片沉到摞底，轮到最后再出现，不是直接消失
 * （2026-11-06 晚 Bob 定）；摞内相对顺序不变。
 */
fun applySkipOrder(cards: List<SmartCard>, skippedIds: List<String>): List<SmartCard> {
    if (skippedIds.isEmpty()) return cards
    val skipped = skippedIds.toSet()
    val head = cards.filter { it.id !in skipped }
    val tail = cards.filter { it.id in skipped }
    return head + tail
}

/**
 * 可见卡片（纯函数）：去重 → 掐掉未到出现窗口、已处理与已过期的 → 排序 →
 * 跳过的沉底。仓库层在 publish 前调它；UI 层只负责渲染，不再算顺序。
 */
fun visibleSmartCards(
    cards: List<SmartCard>,
    doneIds: Set<String>,
    skippedIds: List<String>,
    nowMs: Long
): List<SmartCard> {
    val alive = dedupeSmartCards(cards)
        .filter { it.id !in doneIds && nowMs >= it.appearFromMs && !isCardExpired(it, nowMs) }
    return applySkipOrder(sortSmartCards(alive), skippedIds)
}
