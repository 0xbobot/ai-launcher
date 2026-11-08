package com.bobot.ailauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 智能卡片仓库（第一阶段，447/v1.2.0，设计稿 §2–§4）：
 * 短信（READ_SMS 主路径）＋通知监听＋日历（本阶段无日历卡片，会议仍在
 * 简报里、不重复）＋用量统计（去还款落到用户真正用的银行应用）拼成一摞卡。
 *
 * - 数据只在本地处理，不上云；通知正文解析完不长期存原文（设计稿 §4.5）。
 * - 同一信息只在一处呈现：会议已在简报的会行里，卡片不再重复同一场会。
 * - 处理/跳过状态按稳定 id 存 SharedPreferences，带截止点、过期自清。
 * - 连接器开关（通知/日历/消息/电话拨号器/联系人）存在 UiPrefs，
 *   关掉后该来源的卡片立即停用。
 */
object SmartCardRepository {
    private const val TAG = "SmartCardRepository"
    private val _cards = MutableStateFlow<List<SmartCard>>(emptyList())
    val cards: StateFlow<List<SmartCard>> = _cards.asStateFlow()

    private const val PREFS = "smart_cards"
    private const val KEY_DONE_PREFIX = "done_"
    private const val KEY_SKIPPED_PREFIX = "skipped_"
    private const val KEY_LAST_USED_PREFIX = "last_used_"
    private const val KEY_LAST_SMS_REFRESH = "last_sms_refresh_ms"
    /** 引导卡片只出现一次（设计稿 §4.7）：看完（滑完全部）或点 × 关掉即记下，不再弹 */
    private const val KEY_GUIDE_SEEN = "guide_seen"
    /** 引导内容已看过的版本号（设计稿 §4.7，456/v1.2.9）：换版后自动重看
     * 一次，每版引导只出现一次，不是终身一次。旧版布尔 guide_seen 只读、
     * 只做迁移（true 视为已看 v2），不再写。 */
    private const val KEY_GUIDE_SEEN_VERSION = "guide_seen_version"
    /** 引导卡片的“忽略”（设计稿 §4.2，449/v1.2.2）：单张永久不再出现。
     * 与“滑走”不同——滑走只是本轮沉底/看完才记一次，忽略是当即永久记下。 */
    private const val KEY_GUIDE_DISMISSED = "guide_dismissed"

    /** 短信重读节流：5 分钟内不重复查短信库（查询本身便宜，但没必要每分钟查） */
    private const val SMS_REFRESH_MIN_INTERVAL_MS = 5L * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val HOUR_MS = 60L * 60 * 1000

    /** 短信解析出的卡片缓存（15 分钟/回桌面节流重建，分钟 ticker 只重算窗口与顺序） */
    private var smsCards: List<SmartCard> = emptyList()
    private var lastNotifications: List<SimpleNotification> = emptyList()
    private val contactNameCache = mutableMapOf<String, String?>()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读一组带截止点的 id（过期自清），返回未过期的 id 集合 */
    private fun liveIds(context: Context, prefix: String, nowMs: Long): Set<String> {
        val p = prefs(context)
        val out = mutableSetOf<String>()
        val editor = p.edit()
        var changed = false
        for ((key, value) in p.all) {
            if (!key.startsWith(prefix) || value !is Long) continue
            if (value > nowMs) {
                out += key.removePrefix(prefix)
            } else {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
        return out
    }

    private fun liveIdList(context: Context, prefix: String, nowMs: Long): List<String> {
        val p = prefs(context)
        val withDeadline = mutableListOf<Pair<String, Long>>()
        val editor = p.edit()
        var changed = false
        for ((key, value) in p.all) {
            if (!key.startsWith(prefix) || value !is Long) continue
            if (value > nowMs) {
                withDeadline += key.removePrefix(prefix) to value
            } else {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
        // 跳过顺序按当时跳过的先后（截止点越早跳得越早）
        return withDeadline.sortedBy { it.second }.map { it.first }
    }

    /**
     * 引导卡片已看过的版本号：无记录为 null；旧版布尔 guide_seen == true
     * 视为已看 v2（v3 之前最新版引导），不挡 v3——Bob 设备上 452 时记下
     * 的就是旧版布尔，升到新版后 v3 五张引导卡要再出现一次。
     */
    fun guideSeenVersion(context: Context): Int? {
        val p = prefs(context)
        val stored = if (p.contains(KEY_GUIDE_SEEN_VERSION)) {
            p.getInt(KEY_GUIDE_SEEN_VERSION, 0)
        } else {
            null
        }
        return migrateLegacyGuideSeen(p.getBoolean(KEY_GUIDE_SEEN, false), stored)
    }

    /** 记下当前版本引导卡片已看过（整叠走空或点 × 关掉时记当前版本） */
    fun markGuideSeen(context: Context) {
        prefs(context).edit().putInt(KEY_GUIDE_SEEN_VERSION, GUIDE_VERSION).apply()
    }

    /** 点过“忽略”的引导卡 id（永久不再出现） */
    fun dismissedGuideIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_GUIDE_DISMISSED, emptySet())
            ?.toSet() ?: emptySet()

    /** 忽略一张引导卡：当即永久记下（不是滑走，不是只记本轮） */
    fun dismissGuideCard(context: Context, card: SmartCard) {
        val next = dismissedGuideIds(context) + card.id
        prefs(context).edit().putStringSet(KEY_GUIDE_DISMISSED, next).apply()
    }

    /** 点下一步动作算处理完一张（仓库层立即摘掉，UI 侧先播离场动画再调这里） */
    fun markDone(context: Context, card: SmartCard) {
        prefs(context).edit()
            .putLong(KEY_DONE_PREFIX + card.id, card.deadlineMs).apply()
        refresh(context, lastNotifications, System.currentTimeMillis())
    }

    /** 横滑跳过：沉到摞底，轮到最后再出现，不是直接消失 */
    fun skip(context: Context, card: SmartCard) {
        prefs(context).edit()
            .putLong(KEY_SKIPPED_PREFIX + card.id, card.deadlineMs).apply()
        refresh(context, lastNotifications, System.currentTimeMillis())
    }

    /**
     * 重读短信并重建短信卡片（节流 5 分钟；force 用于授权刚接上时立即生效）。
     * 银行应用解析（PackageManager＋用量统计）只在这里做，不在每分钟刷新里做。
     */
    fun refreshSms(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = prefs(context).getLong(KEY_LAST_SMS_REFRESH, 0L)
        if (!force && now - last < SMS_REFRESH_MIN_INTERVAL_MS && smsCards.isNotEmpty()) return
        smsCards = buildSmsCards(context, now)
        prefs(context).edit().putLong(KEY_LAST_SMS_REFRESH, now).apply()
        refresh(context, lastNotifications, now)
    }

    private fun buildSmsCards(context: Context, nowMs: Long): List<SmartCard> {
        if (!UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_MESSAGE)) {
            Log.d(TAG, "buildSmsCards: message connector off, 0 cards")
            return emptyList()
        }
        if (!SmsRepository.hasSmsPermission(context)) {
            Log.d(TAG, "buildSmsCards: no READ_SMS permission, 0 cards")
            return emptyList()
        }
        val out = mutableListOf<SmartCard>()
        val smsList = SmsRepository.loadRecentSms(context)
        var billCount = 0
        var apptCount = 0
        for (sms in smsList) {
            val bill = parseBillSms(sms.body, sms.address, nowMs)
            if (bill != null) {
                out += billCard(bill, sms.address, context)
                billCount++
                continue
            }
            val appt = parseAppointmentSms(sms.body, nowMs)
            if (appt != null) {
                out += appointmentCard(appt, sms.address, context, nowMs)
                apptCount++
            }
        }
        // 仅调试可见：下次“卡片没出现”先看这行——读到 N 条、解析出 M 张，
        // 不用再靠猜是短信没读到、解析灭了还是显示门拦了（448）。
        Log.d(TAG, "buildSmsCards: read=${smsList.size} sms, bills=$billCount, appts=$apptCount")
        return out
    }

    /** 账单卡去重键：银行＋金额＋到期日（448：尾号可选，真实短信常不写尾号，
     * 故尾号不再进键；短信版与通知版共用同一键，去重后短信版优先）。 */
    private fun billKey(bill: ParsedBill): String {
        val dueStart = startOfDayMs(bill.dueDayEndMs)
        return "bill|${bill.bank}|${bill.amountFen}|$dueStart"
    }

    /** 账单卡文案：有尾号才写尾号，无尾号不硬凑（448）。 */
    private fun billFact(bill: ParsedBill): String =
        if (bill.tail4 != null) "${bill.bank}尾号 ${bill.tail4} 有 ${bill.amountText} 待还，${bill.dueLabel}到期"
        else "${bill.bank}有 ${bill.amountText} 待还，${bill.dueLabel}到期"

    private fun billCard(
        bill: ParsedBill,
        address: String,
        context: Context
    ): SmartCard {
        val dueStart = startOfDayMs(bill.dueDayEndMs)
        val key = billKey(bill)
        return SmartCard(
            id = key,
            kind = SmartCardKind.BILL,
            sourceLabel = "短信",
            fact = billFact(bill),
            prep = null,
            actionLabel = "去还款",
            sortMs = bill.dueDayEndMs,
            deadlineMs = bill.dueDayEndMs,
            action = SmartCardActions.smsCardAction(context, address, bill.bank),
            sourceKey = key,
            appearFromMs = dueStart - BILL_APPEAR_DAYS_BEFORE * DAY_MS
        )
    }

    private fun appointmentCard(
        appt: ParsedAppointment,
        address: String,
        context: Context,
        nowMs: Long
    ): SmartCard {
        val startMs = appt.dayStartMs + appt.startMin * 60L * 1000
        val endMs = appt.dayStartMs + (appt.endMin ?: (appt.startMin + 60)) * 60L * 1000
        // 体检按禁食截止排：前一天 20:00（开始前 12 小时），比开始时间更早
        val sortMs = if (appt.isCheckup) startMs - 12 * HOUR_MS else startMs
        val kindWord = if (appt.isCheckup) "体检预约" else "预约"
        val fact = buildString {
            append("短信里有一条$kindWord，")
            append(relativeDayLabel(appt.dayStartMs, nowMs))
            append(" ")
            append(formatTimeRange(appt.startMin, appt.endMin))
            if (appt.place != null) append("，${appt.place}")
        }
        val key = "appt|${appt.dayStartMs}|${appt.startMin}|${appt.place?.hashCode() ?: 0}"
        return SmartCard(
            id = key,
            kind = SmartCardKind.APPOINTMENT,
            sourceLabel = "短信",
            fact = fact,
            prep = if (appt.isCheckup) checkupPrepLine() else null,
            actionLabel = "看预约",
            sortMs = sortMs,
            deadlineMs = endMs,
            action = SmartCardActions.smsCardAction(context, address, null),
            sourceKey = key,
            appearFromMs = startMs - APPOINTMENT_APPEAR_HOURS_BEFORE * HOUR_MS
        )
    }

    /** 通知渠道兜底的账单卡（消息连接器拒绝权限/Play 版时走这条；id 与短信版一致，去重后短信版优先） */
    private fun notificationBillCard(
        bill: ParsedBill,
        n: SimpleNotification
    ): SmartCard {
        val dueStart = startOfDayMs(bill.dueDayEndMs)
        val key = billKey(bill)
        return SmartCard(
            id = key,
            kind = SmartCardKind.BILL,
            sourceLabel = "短信",
            fact = billFact(bill),
            prep = null,
            actionLabel = "去还款",
            sortMs = bill.dueDayEndMs,
            deadlineMs = bill.dueDayEndMs,
            action = SmartCardActions.notificationAction(n),
            sourceKey = key,
            appearFromMs = dueStart - BILL_APPEAR_DAYS_BEFORE * DAY_MS
        )
    }

    private fun notificationAppointmentCard(
        appt: ParsedAppointment,
        n: SimpleNotification,
        nowMs: Long
    ): SmartCard {
        val startMs = appt.dayStartMs + appt.startMin * 60L * 1000
        val endMs = appt.dayStartMs + (appt.endMin ?: (appt.startMin + 60)) * 60L * 1000
        val sortMs = if (appt.isCheckup) startMs - 12 * HOUR_MS else startMs
        val kindWord = if (appt.isCheckup) "体检预约" else "预约"
        val fact = buildString {
            append("短信里有一条$kindWord，")
            append(relativeDayLabel(appt.dayStartMs, nowMs))
            append(" ")
            append(formatTimeRange(appt.startMin, appt.endMin))
            if (appt.place != null) append("，${appt.place}")
        }
        val key = "appt|${appt.dayStartMs}|${appt.startMin}|${appt.place?.hashCode() ?: 0}"
        return SmartCard(
            id = key,
            kind = SmartCardKind.APPOINTMENT,
            sourceLabel = "短信",
            fact = fact,
            prep = if (appt.isCheckup) checkupPrepLine() else null,
            actionLabel = "看预约",
            sortMs = sortMs,
            deadlineMs = endMs,
            action = SmartCardActions.notificationAction(n),
            sourceKey = key,
            appearFromMs = startMs - APPOINTMENT_APPEAR_HOURS_BEFORE * HOUR_MS
        )
    }

    private fun missedCallCard(
        n: SimpleNotification,
        context: Context,
        contactsOn: Boolean
    ): SmartCard {
        val number = n.phoneNumber
        val name = if (contactsOn && ContactNames.hasContactsPermission(context)) {
            contactNameCache.getOrPut(number) { ContactNames.lookup(context, number) }
        } else null
        val dayStart = startOfDayMs(n.time)
        val key = "missed|$number|$dayStart"
        return SmartCard(
            id = key,
            kind = SmartCardKind.MISSED_CALL,
            sourceLabel = "未接来电",
            fact = if (name != null) "$name ${maskPhoneNumber(number)}" else maskPhoneNumber(number),
            prep = null,
            actionLabel = "回拨",
            sortMs = n.time,
            deadlineMs = n.time + DAY_MS,
            action = SmartCardAction.Dial(number),
            sourceKey = key,
            contactName = name,
            appearFromMs = n.time
        )
    }

    private fun deliveryCard(n: SimpleNotification): SmartCard {
        val key = "notif|${n.notifKey}"
        val fact = n.text.ifBlank { n.title }.trim()
        return SmartCard(
            id = key,
            kind = SmartCardKind.NOTIFICATION,
            sourceLabel = "通知",
            fact = fact,
            prep = null,
            actionLabel = "查看",
            sortMs = n.time,
            deadlineMs = n.time + 12 * HOUR_MS,
            action = SmartCardActions.notificationAction(n),
            sourceKey = key,
            appearFromMs = n.time
        )
    }

    /**
     * 重算可见卡片（分钟 ticker / 通知变化 / 处理/跳过后调）。
     * 每条通知最多出一张卡：未接来电 → 账单/预约（消息兜底）→ 快递/航班，
     * 按此顺序先中先得；短信候选在前，去重后短信版优先。
     */
    fun refresh(context: Context, notifications: List<SimpleNotification>, nowMs: Long) {
        lastNotifications = notifications
        val messageOn = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_MESSAGE)
        val notifOn = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_NOTIFICATION)
        val phoneOn = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_PHONE)
        val contactsOn = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_CONTACTS)
        val candidates = mutableListOf<SmartCard>()
        if (messageOn) candidates += smsCards
        for (n in notifications) {
            if (n.packageName == context.packageName) continue
            if (notifOn && phoneOn && n.phoneNumber.isNotBlank()) {
                candidates += missedCallCard(n, context, contactsOn)
                continue
            }
            if (messageOn) {
                val combined = "${n.title}\n${n.text}"
                val bill = parseBillSms(combined, n.appName, nowMs)
                if (bill != null) {
                    candidates += notificationBillCard(bill, n)
                    continue
                }
                val appt = parseAppointmentSms(combined, nowMs)
                if (appt != null) {
                    candidates += notificationAppointmentCard(appt, n, nowMs)
                    continue
                }
            }
            if (notifOn && isDeliveryOrTravelNotification(n.title, n.text)) {
                candidates += deliveryCard(n)
            }
        }
        val doneIds = liveIds(context, KEY_DONE_PREFIX, nowMs)
        val skippedIds = liveIdList(context, KEY_SKIPPED_PREFIX, nowMs)
        val visible = visibleSmartCards(candidates, doneIds, skippedIds, nowMs)
        _cards.value = visible
        recordLastUsed(context, visible, nowMs)
    }

    /** 各来源“最后使用时间”（设置连接器列表里显示“今天用过”这类状态） */
    private fun recordLastUsed(context: Context, visible: List<SmartCard>, nowMs: Long) {
        val used = mutableSetOf<String>()
        for (card in visible) {
            when (card.kind) {
                SmartCardKind.BILL, SmartCardKind.APPOINTMENT ->
                    used += UiPrefs.CONNECTOR_MESSAGE
                SmartCardKind.MISSED_CALL -> {
                    used += UiPrefs.CONNECTOR_PHONE
                    used += UiPrefs.CONNECTOR_NOTIFICATION
                }
                SmartCardKind.NOTIFICATION -> used += UiPrefs.CONNECTOR_NOTIFICATION
                // 引导卡不是一件真事项，不计入任何来源的“最后使用时间”
                // （且引导卡根本不进仓库的候选表，这里只是穷尽分支）。
                SmartCardKind.GUIDE -> Unit
            }
        }
        if (used.isEmpty()) return
        val editor = prefs(context).edit()
        for (key in used) editor.putLong(KEY_LAST_USED_PREFIX + key, nowMs)
        editor.apply()
    }

    fun lastUsedMs(context: Context, connectorKey: String): Long =
        prefs(context).getLong(KEY_LAST_USED_PREFIX + connectorKey, 0L)

    /**
     * 日历来源的使用记录：本阶段日历卡片还没有，会议仍在简报的会行里——
     * 会行出现即算日历来源用过一次（PetZone 在 coverNextEvent 非空时调）。
     */
    fun recordCalendarUsed(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_USED_PREFIX + UiPrefs.CONNECTOR_CALENDAR,
                System.currentTimeMillis()).apply()
    }
}

/** 号码打码：11 位手机号留前 3 后 4（138****1234），其余留首尾各 2 位 */
internal fun maskPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return when {
        digits.length == 11 -> "${digits.take(3)}****${digits.takeLast(4)}"
        digits.length >= 7 -> "${digits.take(2)}****${digits.takeLast(2)}"
        else -> number
    }
}

/** 设置连接器列表里一行的数据（设计稿 §5.3：名字、状态、它提供什么、断开按钮） */
data class ConnectorState(
    val key: String,
    val name: String,
    val desc: String,
    /** 是否已接上（权限/系统开关已开） */
    val granted: Boolean,
    /** 用户开关（关掉后该来源的卡片立即停用） */
    val enabled: Boolean,
    /** 最后使用时间文案：今天用过 / 昨天用过 / N 天前用过 / 还没用过 */
    val lastUsedLabel: String
)

/** 最后使用时间文案（纯函数，单测锁死） */
fun formatLastUsed(lastMs: Long, nowMs: Long): String {
    if (lastMs <= 0L) return "还没用过"
    val days = (startOfDayMs(nowMs) - startOfDayMs(lastMs)) / (24L * 60 * 60 * 1000)
    return when (days) {
        0L -> "今天用过"
        1L -> "昨天用过"
        else -> "$days 天前用过"
    }
}

/** 五个本机连接器的状态（设置 → 提醒来源列表用） */
fun buildConnectorStates(context: Context): List<ConnectorState> {
    val now = System.currentTimeMillis()
    fun lastUsed(key: String) =
        formatLastUsed(SmartCardRepository.lastUsedMs(context, key), now)
    val notifGranted = NotificationRepository.hasAccess(context)
    return listOf(
        ConnectorState(
            key = UiPrefs.CONNECTOR_NOTIFICATION,
            name = "通知",
            desc = "应用通知里的待办、未接来电和快递航班提醒",
            granted = notifGranted,
            enabled = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_NOTIFICATION),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_NOTIFICATION)
        ),
        ConnectorState(
            key = UiPrefs.CONNECTOR_CALENDAR,
            name = "日历",
            desc = "首页显示今天的会、点一下加入",
            granted = CalendarRepository.hasCalendarPermission(context),
            enabled = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_CALENDAR),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_CALENDAR)
        ),
        ConnectorState(
            key = UiPrefs.CONNECTOR_MESSAGE,
            name = "消息",
            desc = "短信里的账单到期、体检预约提醒",
            granted = SmsRepository.hasSmsPermission(context),
            enabled = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_MESSAGE),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_MESSAGE)
        ),
        ConnectorState(
            key = UiPrefs.CONNECTOR_PHONE,
            name = "电话拨号器",
            desc = "未接来电显示号码、点一下回拨（随通知一起开，不另要权限）",
            granted = notifGranted,
            enabled = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_PHONE),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_PHONE)
        ),
        ConnectorState(
            key = UiPrefs.CONNECTOR_CONTACTS,
            name = "联系人",
            desc = "未接来电显示成名字",
            granted = ContactNames.hasContactsPermission(context),
            enabled = UiPrefs.isConnectorEnabled(context, UiPrefs.CONNECTOR_CONTACTS),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_CONTACTS)
        )
    )
}
