package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 智能卡片仓库（第一阶段，447/v1.2.0，设计稿 §2–§4）：
 * 短信（READ_SMS 主路径）＋通知监听＋日历（第十二批：日历卡，会议仍在
 * 简报里并存——简报按提案等日历卡验过、Bob 点头后再退，本批不动）＋
 * 用量统计（去还款落到用户真正用的银行应用）拼成一摞卡。
 *
 * - 数据只在本地处理，不上云；通知正文解析完不长期存原文（设计稿 §4.5）。
 *   例外（第十二批，Bob 2026-11-09 22:08 批复 A）：准备清单走云端大模型，
 *   只发脱敏后的原文（SmartCardPrep.sanitizeForLlm），只在用户配了 Key
 *   时才调，只有预约卡与日程卡才调，每卡最多一次，失败静默。
 * - 同一信息只在一处呈现：短信预约与日历日程是同一件事时只留短信那张
 *   （共用去重键 appt|…）。
 * - 处理/跳过状态按稳定 id 存 SharedPreferences，带截止点、过期自清。
 *   划走按卡种分（第十四批，Bob 2026-11-10 23:43 定，取代 482「本轮收
 *   起来」）：用户的事项提醒（账单/预约/日历/未接来电/快递航班）划过
 *   沉到摞底、看完其他卡就轮到它，只有点「我知道了」才丢；应用推的
 *   提醒（小贴士、升级卡、引导卡）划过就丢。不再有“新卡到了开新一轮”——
 *   划走的事项卡不用新卡来“放回来”，小贴士、升级卡的到来也不开新一轮。
 * - 卡片跟着权限走（第十三批，Bob 2026-11-10 定：双重关全改掉）：
 *   每个来源的门就是系统权限本身（消息＝READ_SMS、通知＝通知监听、
 *   日历＝READ_CALENDAR；联系人只做未接来电的名字解析、只认 READ_CONTACTS，
 *   不卡卡片；电话拨号器无独立权限、跟通知监听走），不再另设开关。UiPrefs 的 smart_connector_* 启用
 *   标志已删除——删了 API，编译器就能证明没有开关门残留。
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
    /** 引导卡片已处理完（第十四批起的口径，设计稿 §4.7 轮换推）：接上某个
     * 来源后“已连接”反馈给完、或反馈卡被横滑走，就当即永久记下这张，
     * 不再出现。点「我知道了」/横滑一张引导卡不再走这里——那是轮换推
     * 的“被拒一次”（放队尾，见 rejectGuideCard），不是永久记下。 */
    private const val KEY_GUIDE_DISMISSED = "guide_dismissed"
    /** 引导卡轮换队列（第十四批，设计稿 §4.7 单卡提醒轮换推，Bob
     * 2026-11-10 23:07 定）：按 GUIDE_VERSION 存的有序队列（逗号拼 id）。
     * 该版本第一次读时默认 guideCards() 的顺序（即 SMART_SOURCE_ORDER：
     * 消息、通知、日历、电话拨号器）；被拒一次放队尾、被拒两次移出
     * 队列（本版不再推）。队列空了＝本版引导走完了，宿主随即记
     * guide_seen_version。键带版本号，换版后自动从新队列开始。 */
    private const val KEY_GUIDE_QUEUE_PREFIX = "guide_queue_v"
    /** 某张引导卡本版被拒的次数（键带版本号＋卡 id）：0/1/2，2 即出局。 */
    private const val KEY_GUIDE_REJECT_PREFIX = "guide_reject_v"

    /** 预约卡已建过提醒（第十一批）：按卡片 id 记，带截止点、过期自清。
     * 建完提醒后卡片动作换回看预约（防重复建），卡上显示“已建提醒”。 */
    private const val KEY_REMINDER_CREATED_PREFIX = "reminder_created_"

    /** 准备清单异步结果（第十二批）：按卡片 id 缓存的大模型返回，
     * UI 层订阅后覆盖卡片构建时的 prep（构建时为 null）。失败不写，
     * 卡片只显示事实＋动作。内存缓存，随进程走。 */
    private val _prepById = MutableStateFlow<Map<String, String>>(emptyMap())
    val prepById: StateFlow<Map<String, String>> = _prepById.asStateFlow()
    /** 已向大模型请求过准备清单的卡片 id（每卡最多调一次，失败也算一次，
     * 本轮不重试）。内存记，随进程走。 */
    private val prepRequested = mutableSetOf<String>()
    private val prepScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 顶卡划走动画复位代次（第十二批）：skip() 时加一，UI 层把
     * TopSmartCard 的组合键挂上它——同一张卡以新代次重新呈现时动画
     * 状态（offsetX/alpha）重建，不会再出现“数着卡、看不见卡”。
     * 第十四批：新一轮机制已删，代次只在 skip() 时加（“新一轮清空时
     * 加一”那半句作废）。 */
    private val _skipEpoch = MutableStateFlow(0)
    val skipEpoch: StateFlow<Int> = _skipEpoch.asStateFlow()

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

    /** 一张引导卡处理完了（接上后“已连接”反馈已给完、或反馈卡被横滑
     * 走）：当即永久记下，不再出现。点「我知道了」/横滑一张引导卡不
     * 走这里——那是轮换推的“被拒一次”（见 rejectGuideCard）。 */
    fun dismissGuideCard(context: Context, card: SmartCard) {
        val next = dismissedGuideIds(context) + card.id
        prefs(context).edit().putStringSet(KEY_GUIDE_DISMISSED, next).apply()
    }

    /**
     * 当前版本的引导轮换队列（有序）：没存过时默认 guideCards() 的顺序
     * （即 SMART_SOURCE_ORDER：消息、通知、日历、电话拨号器）；存过
     * （哪怕已空）就按存的走——空队列是“走完了”，不是“没存过”。
     * 队列里的旧 id（换版后不存在的卡）过滤掉，不挡新版。
     */
    fun guideQueue(context: Context): List<String> {
        val key = KEY_GUIDE_QUEUE_PREFIX + GUIDE_VERSION
        val p = prefs(context)
        if (!p.contains(key)) return guideCards().map { it.id }
        val raw = p.getString(key, "") ?: ""
        if (raw.isBlank()) return emptyList()
        val known = guideCards().map { it.id }.toSet()
        return raw.split(",").filter { it in known }
    }

    private fun saveGuideQueue(context: Context, queue: List<String>) {
        prefs(context).edit()
            .putString(KEY_GUIDE_QUEUE_PREFIX + GUIDE_VERSION, queue.joinToString(","))
            .apply()
    }

    /** 某张引导卡本版已被拒几次（0/1/2；2＝本版不再推，等引导换版）。 */
    fun guideRejectCount(context: Context, id: String): Int =
        prefs(context).getInt(
            KEY_GUIDE_REJECT_PREFIX + GUIDE_VERSION + "_" + id, 0
        )

    /**
     * 被拒一次（点「我知道了」或横滑，设计稿 §4.7 轮换推，Bob 2026-11-10
     * 23:07 定）：第一次放队尾（下次推其他来源的卡，推完再回来一次），
     * 第二次移出队列、本版不再推。键带版本号，换版后计数清零、队列重来。
     */
    fun rejectGuideCard(context: Context, card: SmartCard) {
        val prior = guideRejectCount(context, card.id)
        prefs(context).edit()
            .putInt(
                KEY_GUIDE_REJECT_PREFIX + GUIDE_VERSION + "_" + card.id,
                prior + 1
            ).apply()
        saveGuideQueue(
            context,
            guideQueueAfterReject(guideQueue(context), card.id, prior)
        )
    }

    /**
     * 记下某张预约卡已建过提醒（第十一批）：按卡片 id 记、带截止点；
     * 记完立即重建卡片——动作换回看预约、卡上显示“已建提醒”，
     * 卡片本身不摘（预约还没到，用户还可能要点开看原文）。
     */
    fun markReminderCreated(context: Context, card: SmartCard) {
        prefs(context).edit()
            .putLong(KEY_REMINDER_CREATED_PREFIX + card.id, card.deadlineMs).apply()
        refreshSms(context, force = true)
        refresh(context, lastNotifications, System.currentTimeMillis())
    }

    /** 已建过提醒的预约卡 id（未过期的那部分） */
    private fun reminderCreatedIds(context: Context, nowMs: Long): Set<String> =
        liveIds(context, KEY_REMINDER_CREATED_PREFIX, nowMs)

    /** 点下一步动作算处理完一张（仓库层立即摘掉，UI 侧先播离场动画再调这里）——
     * 除了第十九批的有时间段提醒（日历/预约）：时间段内点动作不摘卡，
     * performAction 内按 kind＋deadlineMs 判、不调这里；只有「我知道了」
     * （宿主 onIgnore 直接调这里）或过了截止点才摘。 */
    fun markDone(context: Context, card: SmartCard) {
        prefs(context).edit()
            .putLong(KEY_DONE_PREFIX + card.id, card.deadlineMs).apply()
        refresh(context, lastNotifications, System.currentTimeMillis())
    }

    /**
     * 横滑跳过（第十四批，Bob 2026-11-10 23:43 定：划走按卡种分）：
     * 用户的事项提醒（账单/预约/日历/未接来电/快递航班）划过沉到摞底——
     * 记进跳过集合，visibleSmartCards 按划走先后排后面，看完其他卡就
     * 轮到它，带截止点、过期自清；应用推的提醒（引导卡，以及还没落地的
     * 小贴士/升级卡）划过就丢——记进处理完集合，不再出现。
     * 只有点第一个按钮「我知道了」才丢用户的事项提醒（那是 markDone）。
     */
    fun skip(context: Context, card: SmartCard) {
        val key = if (isUserMatterCard(card.kind)) {
            KEY_SKIPPED_PREFIX + card.id
        } else {
            KEY_DONE_PREFIX + card.id
        }
        prefs(context).edit()
            .putLong(key, card.deadlineMs).apply()
        _skipEpoch.value += 1
        refresh(context, lastNotifications, System.currentTimeMillis())
    }

    /**
     * 为一张卡请求大模型准备清单（第十二批，宿主在可见卡片变化时调）：
     * 本地过滤（只有预约卡/日程卡）＋有脱敏原文＋没请求过＋用户配了 Key，
     * 四条全满足才调；否则直接返回（不调、不报错、不追问配 Key）。
     * 每卡最多调一次（prepRequested 记下，失败也算一次）；结果写进
     * prepById，UI 订阅后覆盖卡片的 prep；超时/失败/校验不过静默丢弃，
     * 卡片只显示事实＋动作。走已有通路 LlmRouter.postChatCompletions。
     */
    fun requestPrep(context: Context, card: SmartCard) {
        if (!needsLlmPrep(card)) return
        val source = card.prepSource?.takeIf { it.isNotBlank() } ?: return
        if (card.id in prepRequested || _prepById.value.containsKey(card.id)) return
        val appContext = context.applicationContext
        if (!LlmConfig.hasKey(appContext)) return
        prepRequested += card.id
        prepScope.launch {
            val prep = runCatching {
                val r = LlmRouter.postChatCompletions(
                    LlmConfig.getBaseUrl(appContext),
                    LlmConfig.getApiKey(appContext),
                    LlmConfig.getModel(appContext),
                    PREP_SYSTEM_PROMPT,
                    source,
                    temperature = 0.2,
                    maxTokens = 200,
                    tag = "SmartCardPrep"
                )
                if (r.networkError != null || r.httpCode !in 200..299) null
                else parsePrepResponse(r.rawBody)
            }.getOrNull()
            if (prep != null) {
                _prepById.value = _prepById.value + (card.id to prep)
            }
            // 失败静默：不写 prep、不重试（每卡一次）、不向用户报大模型错误。
        }
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
        // 第十三批：卡片跟着权限走——短信卡只认 READ_SMS，不再查开关
        // （开关当初是第二道关；简报已经在用数据了，它管不住数据用不用）。
        if (!SmsRepository.hasSmsPermission(context)) {
            Log.d(TAG, "buildSmsCards: no READ_SMS permission, 0 cards")
            return emptyList()
        }
        val out = mutableListOf<SmartCard>()
        val smsList = SmsRepository.loadRecentSms(context)
        val created = reminderCreatedIds(context, nowMs)
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
                out += appointmentCard(appt, sms.address, sms.body, context, nowMs, created)
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
        // 动作：还款目标应用（银行 → 云闪付/支付宝/微信，带还款页深链）
        // → 短信会话兜底（smsCardAction 内含这条链）。
        val action = SmartCardActions.smsCardAction(context, address, bill.bank)
        return SmartCard(
            id = key,
            kind = SmartCardKind.BILL,
            sourceLabel = "短信",
            fact = billFact(bill),
            prep = null,
            actionLabel = "去还款",
            sortMs = bill.dueDayEndMs,
            deadlineMs = bill.dueDayEndMs,
            action = action,
            sourceKey = key,
            appearFromMs = dueStart - BILL_APPEAR_DAYS_BEFORE * DAY_MS,
            // 小标＝还款目标应用；退回短信会话时是默认短信应用。
            targetApp = repaymentTargetApp(context, action)
        )
    }

    /**
     * 账单卡小标的目标应用（第十一批）：动作是 LaunchApp 时就是那家应用；
     * 动作是短信会话时是默认短信应用（点下一步会打开它）。
     */
    private fun repaymentTargetApp(
        context: Context,
        action: SmartCardAction
    ): TargetApp? {
        val pkg = when (action) {
            is SmartCardAction.LaunchApp -> action.packageName
            else -> SmartCardActions.defaultSmsPackage(context)
        } ?: return null
        val label = SmartCardActions.appLabel(context, pkg) ?: return null
        return TargetApp(pkg, label)
    }

    /**
     * 预约卡的动作（第十一批）：没建过提醒时是“帮我建提醒”
     * （CreateReminder，点下一步由宿主拿授权后写系统日历）；
     * 已建过提醒时换回看预约（短信会话），防重复建。
     * 小标＝下一步的目标应用：建提醒时是日历，建完后是短信应用。
     * 准备清单（第十二批）：构建时 prep 为 null，写死模板已作废；
     * 脱敏后的原文存在 prepSource 里，大模型回来后经 prepById 填上。
     * CreateReminder 的 description 不再贴准备清单（第十二批）：
     * 标题＋地点已是事件本身的内容，备注留空，不泄露敏感信息。
     */
    private fun appointmentCard(
        appt: ParsedAppointment,
        address: String,
        sourceText: String,
        context: Context,
        nowMs: Long,
        reminderCreated: Set<String>
    ): SmartCard {
        val startMs = appt.dayStartMs + appt.startMin * 60L * 1000
        val endMs = appt.dayStartMs + (appt.endMin ?: (appt.startMin + 60)) * 60L * 1000
        // 体检按禁食截止排：前一天 20:00（开始前 12 小时），比开始时间更早
        val sortMs = if (appt.isCheckup) startMs - 12 * HOUR_MS else startMs
        val kindWord = if (appt.isCheckup) "体检预约" else "预约"
        // 第十九批：开始后、结束前正文前加「进行中 · 」（设计稿 §4.2）；
        // 构建时按当前时间算，分钟 ticker 的 refresh 里还会再算一次
        // （短信卡有 5 分钟节流缓存，开始那一刻靠 refresh 翻过来）。
        val fact = withInProgressPrefix(
            buildString {
                append("短信里有一条$kindWord，")
                append(relativeDayLabel(appt.dayStartMs, nowMs))
                append(" ")
                append(formatTimeRange(appt.startMin, appt.endMin))
                if (appt.place != null) append("，${appt.place}")
            },
            startMs, endMs, nowMs
        )
        val key = "appt|${appt.dayStartMs}|${appt.startMin}|${appt.place?.hashCode() ?: 0}"
        val fallback = SmartCardActions.smsCardAction(context, address, null)
        val created = key in reminderCreated
        val action = if (created) {
            fallback
        } else {
            SmartCardAction.CreateReminder(
                startMs = startMs,
                endMs = endMs,
                title = reminderEventTitle(kindWord, appt.startMin, appt.endMin),
                place = appt.place,
                description = null,
                isCheckup = appt.isCheckup,
                fallback = fallback
            )
        }
        return SmartCard(
            id = key,
            kind = SmartCardKind.APPOINTMENT,
            sourceLabel = "短信",
            fact = fact,
            prep = null,
            prepSource = sanitizeForLlm(sourceText),
            actionLabel = if (created) "看预约" else "帮我建提醒",
            sortMs = sortMs,
            deadlineMs = endMs,
            action = action,
            sourceKey = key,
            appearFromMs = startMs - APPOINTMENT_APPEAR_HOURS_BEFORE * HOUR_MS,
            windowStartMs = startMs,
            targetApp = appointmentTargetApp(context, action, fallback),
            reminderCreated = created
        )
    }

    /**
     * 预约卡小标的目标应用（第十一批）：下一步是建提醒时是日历；
     * 建完提醒、下一步变回看预约时，是短信会话/通知所属应用。
     */
    private fun appointmentTargetApp(
        context: Context,
        action: SmartCardAction,
        fallback: SmartCardAction
    ): TargetApp? {
        if (action is SmartCardAction.CreateReminder) {
            return resolveCalendarApp(context)
        }
        return when (val target = fallback) {
            is SmartCardAction.LaunchApp ->
                SmartCardActions.appLabel(context, target.packageName)
                    ?.let { TargetApp(target.packageName, it) }
            is SmartCardAction.OpenNotification ->
                target.pendingIntent?.creatorPackage?.let { pkg ->
                    SmartCardActions.appLabel(context, pkg)?.let { TargetApp(pkg, it) }
                }
            else -> SmartCardActions.defaultSmsPackage(context)?.let { pkg ->
                SmartCardActions.appLabel(context, pkg)?.let { TargetApp(pkg, it) }
            }
        }
    }

    /** 通知渠道兜底的账单卡（消息连接器拒绝权限/Play 版时走这条；id 与短信版一致，去重后短信版优先） */
    private fun notificationBillCard(
        bill: ParsedBill,
        n: SimpleNotification,
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
            action = SmartCardActions.notificationAction(n),
            sourceKey = key,
            appearFromMs = dueStart - BILL_APPEAR_DAYS_BEFORE * DAY_MS,
            // 小标＝通知所属应用（点下一步会打开它）。
            targetApp = SmartCardActions.appLabel(context, n.packageName)
                ?.let { TargetApp(n.packageName, it) }
        )
    }

    private fun notificationAppointmentCard(
        appt: ParsedAppointment,
        n: SimpleNotification,
        combinedText: String,
        nowMs: Long,
        context: Context,
        reminderCreated: Set<String>
    ): SmartCard {
        val startMs = appt.dayStartMs + appt.startMin * 60L * 1000
        val endMs = appt.dayStartMs + (appt.endMin ?: (appt.startMin + 60)) * 60L * 1000
        val sortMs = if (appt.isCheckup) startMs - 12 * HOUR_MS else startMs
        val kindWord = if (appt.isCheckup) "体检预约" else "预约"
        // 第十九批：开始后、结束前正文前加「进行中 · 」（设计稿 §4.2）；
        // 构建时按当前时间算，分钟 ticker 的 refresh 里还会再算一次
        // （短信卡有 5 分钟节流缓存，开始那一刻靠 refresh 翻过来）。
        val fact = withInProgressPrefix(
            buildString {
                append("短信里有一条$kindWord，")
                append(relativeDayLabel(appt.dayStartMs, nowMs))
                append(" ")
                append(formatTimeRange(appt.startMin, appt.endMin))
                if (appt.place != null) append("，${appt.place}")
            },
            startMs, endMs, nowMs
        )
        val key = "appt|${appt.dayStartMs}|${appt.startMin}|${appt.place?.hashCode() ?: 0}"
        val fallback = SmartCardActions.notificationAction(n)
        val created = key in reminderCreated
        val action = if (created) {
            fallback
        } else {
            SmartCardAction.CreateReminder(
                startMs = startMs,
                endMs = endMs,
                title = reminderEventTitle(kindWord, appt.startMin, appt.endMin),
                place = appt.place,
                description = null,
                isCheckup = appt.isCheckup,
                fallback = fallback
            )
        }
        return SmartCard(
            id = key,
            kind = SmartCardKind.APPOINTMENT,
            sourceLabel = "短信",
            fact = fact,
            prep = null,
            prepSource = sanitizeForLlm(combinedText),
            actionLabel = if (created) "看预约" else "帮我建提醒",
            sortMs = sortMs,
            deadlineMs = endMs,
            action = action,
            sourceKey = key,
            appearFromMs = startMs - APPOINTMENT_APPEAR_HOURS_BEFORE * HOUR_MS,
            windowStartMs = startMs,
            targetApp = appointmentTargetApp(context, action, fallback),
            reminderCreated = created
        )
    }

    private fun missedCallCard(
        n: SimpleNotification,
        context: Context
    ): SmartCard {
        val number = n.phoneNumber
        // 第十三批：联系人只做名字解析，只认 READ_CONTACTS——有就显示名字，
        // 没有只显示号码，不卡卡片本身（联系人本身不是出卡的来源）。
        val name = if (ContactNames.hasContactsPermission(context)) {
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
            appearFromMs = n.time,
            // 小标＝电话（默认拨号应用；拿不到时不画）。
            targetApp = resolveDialerApp(context)
        )
    }

    private fun deliveryCard(n: SimpleNotification, context: Context): SmartCard {
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
            appearFromMs = n.time,
            // 小标＝通知所属应用（点下一步会打开它）。
            targetApp = SmartCardActions.appLabel(context, n.packageName)
                ?.let { TargetApp(n.packageName, it) }
        )
    }

    /**
     * 日历应用（第十一批，预约卡小标＝建提醒的目标）：
     * 意图逻辑 rili 能力的提供方（capabilities.json：Google 日历 /
     * 系统日历），已装的按最近使用排；都没有时退到系统日历入口
     * （CATEGORY_APP_CALENDAR）解析；再拿不到就不画小标。
     */
    private fun resolveCalendarApp(context: Context): TargetApp? {
        return try {
            CapabilityRegistry.load(context)
            val cap = CapabilityRegistry.find("rili")
            val providers = cap?.let { CapabilityResolution.effectiveProviders(it) }
                ?.flatMap { it.packageNames }.orEmpty()
            val installed = providers.filter { pkg ->
                try {
                    context.packageManager.getApplicationInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            val pkg = SmartCardActions.selectByRecency(installed) {
                AppUsageTracker.effectiveLastUsed(context, it)
            } ?: resolveCalendarIntentPackage(context)
            pkg?.let { p ->
                SmartCardActions.appLabel(context, p)?.let { TargetApp(p, it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 系统日历入口解析（CATEGORY_APP_CALENDAR 拿不到时再试日历内容 URI） */
    private fun resolveCalendarIntentPackage(context: Context): String? {
        return try {
            val pm = context.packageManager
            val calendarIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR)
            pm.resolveActivity(calendarIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?: run {
                    val viewIntent = Intent(
                        Intent.ACTION_VIEW,
                        android.provider.CalendarContract.CONTENT_URI
                    )
                    pm.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        ?.activityInfo?.packageName
                }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 默认拨号应用（第十一批，未接来电卡小标＝电话）：
     * 系统默认拨号器；拿不到时退到 ACTION_DIAL 解析；再拿不到不画小标。
     */
    private fun resolveDialerApp(context: Context): TargetApp? {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE)
                as? android.telecom.TelecomManager
            val pkg = tm?.defaultDialerPackage
                ?: run {
                    val dialIntent = Intent(
                        Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:10086")
                    )
                    context.packageManager.resolveActivity(
                        dialIntent, PackageManager.MATCH_DEFAULT_ONLY
                    )?.activityInfo?.packageName
                }
            pkg?.let { p ->
                SmartCardActions.appLabel(context, p)?.let { TargetApp(p, it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 日历卡（第十二批）：本机日历里的日程/会议。出现窗口：会议类开始前
     * 30 分钟、非会议日程开始前 2 小时（calendarCardWindow）；过了结束
     * 时间消失。全天事件不进卡（没有可排的开始时间）；标题空白、起止
     * 倒挂的也不要。去重键与短信预约卡同格式，同一件事短信在前、日历
     * 在后，去重后短信版优先。准备清单走大模型（prepSource 是脱敏后的
     * 标题＋地点＋描述）。小标＝日历应用。
     * 已知局限：loadTodayEvents 只查当日窗口，跨日的日程本批不处理。
     */
    private fun calendarCard(
        event: CalEvent,
        context: Context,
        nowMs: Long
    ): SmartCard? {
        if (event.allDay || event.title.isBlank() || event.end <= event.begin) {
            return null
        }
        val meetingUrl = extractMeetingUrl(event)
        val (appearFromMs, deadlineMs) = calendarCardWindow(event)
        val sourceText = listOf(event.title, event.location, event.description)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return SmartCard(
            id = "cal|${event.eventId}|${event.begin}",
            kind = SmartCardKind.CALENDAR,
            sourceLabel = "日历",
            fact = calendarCardFact(event, nowMs),
            prep = null,
            prepSource = sanitizeForLlm(sourceText),
            actionLabel = if (meetingUrl != null) "加入会议" else "看日程",
            sortMs = event.begin,
            deadlineMs = deadlineMs,
            action = SmartCardAction.CalendarEvent(event.eventId, meetingUrl),
            sourceKey = calendarCardSourceKey(event),
            appearFromMs = appearFromMs,
            // 第十九批：时间段起点（refresh 里重算「进行中 · 」前缀用，
            // 日历卡事实每分钟本来就重建、这里再算一次结果相同）。
            windowStartMs = event.begin,
            targetApp = resolveCalendarApp(context)
        )
    }

    /**
     * 重算可见卡片（分钟 ticker / 通知变化 / 处理/跳过后调）。
     * 每条通知最多出一张卡：未接来电 → 账单/预约（消息兜底）→ 快递/航班，
     * 按此顺序先中先得；短信候选在前，日历候选次之，去重后短信版优先。
     * 划走的事项卡沉在摞底（跳过集合），看完其他卡就轮到它，不靠新卡
     * “放回来”（第十四批：新一轮机制已删）。
     */
    fun refresh(context: Context, notifications: List<SimpleNotification>, nowMs: Long) {
        lastNotifications = notifications
        // 第十三批：卡片跟着权限走——每个来源的门就是它自己的系统权限，
        // 开关不再进卡片构建路径（UiPrefs 的启用标志已删，编译不过即有残留）。
        // 通知的权限要显式判：监听断开时 onListenerDisconnected 不清
        // 通知列表，缓存的通知可能陈旧，不能只凭“列表非空”当已接上。
        val notifGranted = NotificationRepository.hasAccess(context)
        val candidates = mutableListOf<SmartCard>()
        val created = reminderCreatedIds(context, nowMs)
        // 短信卡：buildSmsCards 只认 READ_SMS，但短信卡片是 5 分钟节流缓存
        // 的——权限刚撤掉、缓存还没重建时，这里再按实时权限校验一次，
        // 撤权限后卡片最迟 1 分钟随分钟 ticker 的 refresh 摘掉，
        // 不等下次重建（通知/日历两条路每分钟都重读实时权限，短信不能例外）。
        if (SmsRepository.hasSmsPermission(context)) candidates += smsCards
        if (CalendarRepository.hasCalendarPermission(context)) {
            candidates += CalendarRepository.loadTodayEvents(context)
                .mapNotNull { calendarCard(it, context, nowMs) }
        }
        for (n in notifications) {
            if (n.packageName == context.packageName) continue
            // 未接来电：电话拨号器无独立权限，跟着通知监听走（461 的
            // “通知＋电话拨号器两个都开”随开关一起作废）。
            if (notifGranted && n.phoneNumber.isNotBlank()) {
                candidates += missedCallCard(n, context)
                continue
            }
            // 通知兜底的账单/预约：数据来自通知监听，门是通知监听权限，
            // 不是 READ_SMS（旧口径错查了消息开关）。
            if (notifGranted) {
                val combined = "${n.title}\n${n.text}"
                val bill = parseBillSms(combined, n.appName, nowMs)
                if (bill != null) {
                    candidates += notificationBillCard(bill, n, context)
                    continue
                }
                val appt = parseAppointmentSms(combined, nowMs)
                if (appt != null) {
                    candidates += notificationAppointmentCard(
                        appt, n, combined, nowMs, context, created
                    )
                    continue
                }
            }
            if (notifGranted && isDeliveryOrTravelNotification(n.title, n.text)) {
                candidates += deliveryCard(n, context)
            }
        }
        val doneIds = liveIds(context, KEY_DONE_PREFIX, nowMs)
        // 跳过集合就是沉底顺序（liveIdList 按卡片截止点排，与正常排序
        // 同一方向）：划走的事项卡一直在可见列表里、排后面，不再靠
        // “新卡到了开新一轮”放回来（第十四批：新一轮机制已删，小贴士、
        // 升级卡的到来也不开新一轮）。只有点「我知道了」（markDone）或
        // 过了截止点才丢。
        val skippedIds = liveIdList(context, KEY_SKIPPED_PREFIX, nowMs)
        // 第十九批：日历/预约卡的「进行中 · 」前缀在这里按当前时间重算
        // 一次（withInProgressPrefix 幂等，先剥再加）。日历卡的事实在
        // 上面本来就每分钟重建（calendarCardFact 内已算），这里再算
        // 一次结果相同；短信预约卡的事实存在 5 分钟节流缓存（smsCards）
        // 里，不在这里重算、开始那一刻最多晚 5 分钟才翻——分钟 ticker
        // 就是靠这一处保证开始那一刻翻过来。只改正文展示，不改去重键、
        // 排序、截止点、prepSource。
        val withPrefix = candidates.map { card ->
            if (card.kind == SmartCardKind.CALENDAR ||
                card.kind == SmartCardKind.APPOINTMENT
            ) {
                card.copy(
                    fact = withInProgressPrefix(
                        card.fact, card.windowStartMs, card.deadlineMs, nowMs
                    )
                )
            } else card
        }
        val visible = visibleSmartCards(withPrefix, doneIds, skippedIds, nowMs)
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
                SmartCardKind.CALENDAR -> used += UiPrefs.CONNECTOR_CALENDAR
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
     * 日历来源的使用记录：简报会行出现即算日历来源用过一次
     * （PetZone 在 coverNextEvent 非空时调）；日历卡本身出现时由
     * recordLastUsed 记（CALENDAR 分支），两条都记同一来源。
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
    /** 是否已接上（权限已授——第十三批起开关不再是一道关，
     * 接上＝权限给了；本字段就是设置页开关的显示值） */
    val granted: Boolean,
    /** 最后使用时间文案：今天用过 / 昨天用过 / N 天前用过 / 还没用过 */
    val lastUsedLabel: String
)

/**
 * 提醒来源状态行文案（只做展示、不可点）：未接上 / 已接上·最后使用。
 * 纯函数，单测锁死。第十三批：“已关闭”分支随开关一起删掉——没有开关
 * 可关，只有权限给了没给。
 */
fun connectorStatusText(state: ConnectorState): String = when {
    !state.granted -> "未接上"
    else -> "已接上 · ${state.lastUsedLabel}"
}

/**
 * 智能源顺序（设计稿 §2，2026-11-08 Bob 定：按源对智能的提升比例，
 * 贡献大的在前，已按卡片实际的源依赖核对）：消息 > 通知 > 日历 > 电话拨号器。
 * buildConnectorStates、设置页“智能”区行序与引导卡的顺序都以它为准，
 * 是同一份（第十四批，Bob 2026-11-10 23:45 定统一——引导卡直接按这
 * 份顺序出，不另写一份）。
 * 联系人已取消（同日 Bob 定：不是必须的，不申请 READ_CONTACTS）——
 * 不在顺序里，没有联系人行、没有联系人引导卡。
 */
val SMART_SOURCE_ORDER: List<String> = listOf(
    UiPrefs.CONNECTOR_MESSAGE,
    UiPrefs.CONNECTOR_NOTIFICATION,
    UiPrefs.CONNECTOR_CALENDAR,
    UiPrefs.CONNECTOR_PHONE
)

/**
 * 智能源说明 v1（设计稿 §2，2026-11-08 Bob 定；待 Bob 真机定）：
 * 按“接上后智能会什么”写，不只写提供什么提醒。纯函数，单测逐字锁死。
 */
fun connectorDescription(key: String): String = when (key) {
    UiPrefs.CONNECTOR_MESSAGE -> "接上后，智能会提前提醒你账单到期、体检预约。"
    UiPrefs.CONNECTOR_NOTIFICATION -> "接上后，智能会提醒你待办、未接来电、包裹和航班。"
    UiPrefs.CONNECTOR_CALENDAR -> "接上后，智能知道你今天的会。"
    UiPrefs.CONNECTOR_PHONE -> "接上后，未接来电点一下就能回拨。"
    else -> ""
}

/**
 * 智能等级 = 已接上的源个数（第十三批：只认权限给了没给，开关不再计入；
 * 电话拨号器随通知计：通知接上才算，它没有自己的开关）。
 * 默认桌面不是源，不进这个列表、不计入等级。纯函数，单测锁死。
 *
 * 电话拨号器行的开关显示与计数走同一口径（2026-11-08 Bob 定：电话拨号器
 * 开关跟着通知走——第十三批改为跟着通知的权限走）：通知权限未授，电话拨号器
 * 行显示关、行变灰点不动；通知接上时行可点，开关反映通知的状态。
 * 电话拨号器没有自己的启用标志可留（第十三批已删），不写回任何东西。
 */
fun connectedSourceCount(states: List<ConnectorState>): Int {
    val byKey = states.associateBy { it.key }
    // 电话拨号器随通知计：通知已接上才算（它的 granted 本就是从通知推导的，
    // 见 buildConnectorStates——全批唯一推导出来的“权限”；这里再按通知
    // 校验一次，输入表不一致时也不多算）。
    val notificationGranted =
        byKey[UiPrefs.CONNECTOR_NOTIFICATION]?.granted ?: false
    return states.count { state ->
        when (state.key) {
            UiPrefs.CONNECTOR_PHONE -> notificationGranted
            else -> state.granted
        }
    }
}

/**
 * 电话拨号器行能不能点（与等级计数同一条口径）：通知已接上时行可点
 * （这时开关反映通知已接上，点它去通知设置页）；通知权限未授时行变灰、
 * 开关点不动——电话拨号器没有自己的开关，不能单独开。纯函数，单测锁死。
 */
fun isDialerRowEnabled(states: List<ConnectorState>): Boolean {
    val byKey = states.associateBy { it.key }
    return byKey[UiPrefs.CONNECTOR_NOTIFICATION]?.granted ?: false
}

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

/** 四个本机连接器的状态（设置 → “智能”区列表用，按 SMART_SOURCE_ORDER 排；
 * 第十三批：只带权限状态，不再带开关——开关不再是一道关。
 * 电话拨号器的 granted 从通知推导（notifGranted），不是直接判的权限：
 * 它没有自己的权限，数据与动作都走通知监听，全批唯一推导出来的“权限”。
 * 联系人已取消（第十四批）：不在这个列表里，不申请 READ_CONTACTS。 */
fun buildConnectorStates(context: Context): List<ConnectorState> {
    val now = System.currentTimeMillis()
    fun lastUsed(key: String) =
        formatLastUsed(SmartCardRepository.lastUsedMs(context, key), now)
    val notifGranted = NotificationRepository.hasAccess(context)
    val byKey = mapOf(
        UiPrefs.CONNECTOR_NOTIFICATION to ConnectorState(
            key = UiPrefs.CONNECTOR_NOTIFICATION,
            name = "通知",
            desc = connectorDescription(UiPrefs.CONNECTOR_NOTIFICATION),
            granted = notifGranted,
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_NOTIFICATION)
        ),
        UiPrefs.CONNECTOR_CALENDAR to ConnectorState(
            key = UiPrefs.CONNECTOR_CALENDAR,
            name = "日历",
            desc = connectorDescription(UiPrefs.CONNECTOR_CALENDAR),
            granted = CalendarRepository.hasCalendarPermission(context),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_CALENDAR)
        ),
        UiPrefs.CONNECTOR_MESSAGE to ConnectorState(
            key = UiPrefs.CONNECTOR_MESSAGE,
            name = "消息",
            desc = connectorDescription(UiPrefs.CONNECTOR_MESSAGE),
            granted = SmsRepository.hasSmsPermission(context),
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_MESSAGE)
        ),
        UiPrefs.CONNECTOR_PHONE to ConnectorState(
            key = UiPrefs.CONNECTOR_PHONE,
            name = "电话拨号器",
            desc = connectorDescription(UiPrefs.CONNECTOR_PHONE),
            granted = notifGranted,
            lastUsedLabel = lastUsed(UiPrefs.CONNECTOR_PHONE)
        )
    )
    return SMART_SOURCE_ORDER.map { byKey.getValue(it) }
}
