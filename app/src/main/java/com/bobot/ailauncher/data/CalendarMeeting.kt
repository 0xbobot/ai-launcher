package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 日历事件的会议链接与打开链路（第十二批）：
 * 原在 ui/pet/PetZone.kt 的 extractMeetingUrl / joinCalendarEvent，
 * 下沉到 data 层——日历智能卡片（仓库）与简报会行（PetZone）共用同一条
 * 链路，不另写一套。域名白名单与三级兜底原样照搬。防多发守卫
 * （JoinGuard）第十六批也下沉到本文件：简报会行已随 486 拿掉，加入的
 * 唯一入口是日历卡「加入会议」（performAction 的 CalendarEvent 分支），
 * 卡片动作与会行路径（joinCalendarEvent，暂无调用点、定义保留）各用
 * 一个实例、同一窗口（2 秒）；卡片动作在成功发出后才记（没发出不记，
 * 失败可重试、失败不摘卡）。
 *
 * 会议类判定：现有代码没有“标题含会议词”这类判定，就用“有会议链接即
 * 会议类”（提案口径），不另造宽泛词表。
 *
 * 第十七批：有会议链接时先走会议应用（meetingAppPackage，按域名定包，
 * 同一条 https 链接限定到该包），应用声明能接（resolveActivity 非空）
 * 才真正发，接不住落回浏览器。Bob 10:29 录屏报：点「加入会议」进了
 * 浏览器（vc.feishu.cn），还要登录再唤起飞书。
 *
 * 第十八批：飞书先走官方入会深链（feishuJoinDeepLink），排在第十七批
 * 的显式包名 https 意图之前——深链接不住（没装／没声明）才落回 489 链。
 * 深链格式已从飞书 APK v7.70.12（com.ss.android.lark）本身核实：
 * manifest 里 RouterActivity 声明了自定义 scheme feishu://applink.feishu.cn
 * （无 path 限制，applink 下任意路径都路由到它）与 verified App Link
 * https://applink.feishu.cn；dex 里的 VideoChatHandler（ly6/j）把路径
 * /client/videochat/open 映射到入会处理，读 query 参数 action 与 num：
 * action=join＋num=<会议号> 走「open join meeting page」分支，会议号
 * 经 g() 清洗（只留数字、最多 12 位），空则落回会议列表。会议号即
 * vc.feishu.cn/j/<9 位数字> 里的数字（飞书帮助文档：「会议号是视频
 * 会议链接的最后 9 位数字」）。manifest 全文无 vc.feishu.cn /
 * meeting.feishu.cn 声明——489 显式包名 https 意图在真机 resolveActivity
 * 为空的根因已证实（Bob 13:56 报「还是不行」）。其它会议应用与其它
 * 卡片不动；JoinGuard 与兜底链照常。
 */
/**
 * v0.68.101（D-5）：入会防多发守卫（纯逻辑，单测锁死）——同一事件在
 * 窗口内重复触发（连点/重组竞态）只放行第一次。真机录屏里「加入」后
 * 浏览器与主页来回闪烁，代码侧先把「本应用多次发放意图」这一路堵死。
 * 原在 ui/pet/PetZone.kt（只拦简报会行的连点）；486 拿掉会行后，加入
 * 的唯一入口是日历卡「加入会议」，第十六批移到 data 层。
 * 只查不记是第十六批的拆分：wasFired 只判断、record 只记录——
 * performAction 在成功发出后才记（没发出不记，失败可重试、失败不摘卡）；
 * shouldFire 是一次性查＋记，给会行路径（joinCalendarEvent）用。
 */
internal class JoinGuard(private val windowMs: Long = 2_000L) {
    private var lastKey: String? = null
    private var lastMs: Long = Long.MIN_VALUE

    /** 窗口内是否已放行过（只查不记；查完不影响后续判断）。 */
    @Synchronized
    fun wasFired(key: String, nowMs: Long): Boolean =
        key == lastKey && nowMs - lastMs < windowMs

    /** 记录一次放行（成功发出后才记；没发出不记，失败可重试）。 */
    @Synchronized
    fun record(key: String, nowMs: Long) {
        lastKey = key
        lastMs = nowMs
    }

    /** 查＋记一次（会行路径用）：窗口内重复触发只放行第一次。 */
    @Synchronized
    fun shouldFire(key: String, nowMs: Long): Boolean {
        if (wasFired(key, nowMs)) return false
        record(key, nowMs)
        return true
    }
}

private val MEETING_DOMAINS = listOf(
    "meeting.tencent.com", "wemeet", "voovmeeting.com",
    "zoom.us", "zoom.com",
    "teams.microsoft.com", "teams.live.com",
    "meeting.feishu.cn", "feishu.cn",
    "webex.com",
    "dingtalk.com"
)

/**
 * 会议链接 → 会议应用包名（纯函数，单测锁死；第十七批）：
 * 有会议链接时先走会议应用（同一条 https 链接、限定到该包），只在
 * 应用声明能接这条链接时才真正发（执行层 resolveActivity 校验，
 * 没装／没声明就落回浏览器）。只含 MEETING_DOMAINS 已有的域名。
 *
 * 包名核实状态（详见收据 §飞书核实）：
 * - 飞书 com.ss.android.lark：已确认——PGYER 飞书 APK 列表（北京飞书
 *   科技有限公司，v7.31.8）＋ feishu.cn/.well-known/assetlinks.json
 *   （verified App Link，delegate_permission/common.handle_all_urls）。
 *   第十八批已从飞书 APK v7.70.12 的 manifest 本身证实：vc.feishu.cn /
 *   meeting.feishu.cn 这两个 host 在 manifest 全文 0 命中——飞书根本
 *   没声明接这两个 host，所以 489 的显式包名 https 意图在真机上
 *   resolveActivity 为空、落回浏览器（Bob 13:56 报「还是不行」）。
 *   第十八批改走官方入会深链（feishuJoinDeepLink），排在显式包名
 *   https 意图之前。
 * - 腾讯会议 com.tencent.wemeet.app、VooV com.tencent.voov：已确认——
 *   APKPure 腾讯会议列表包名 com.tencent.wemeet.app（多版本一致，
 *   腾讯科技深圳公司）＋ Play VooV 列表 id=com.tencent.voov。
 *   注意：不是 com.tencent.meeting（APKPure 三个版本都写
 *   com.tencent.wemeet.app）。meeting.tencent.com 走腾讯会议，
 *   voovmeeting.com 走 VooV（国际版），wemeet 走腾讯会议。
 * - Zoom us.zoom.videomeetings：已确认——Play 列表
 *   id=us.zoom.videomeetings（Zoom Workplace / ZOOM Cloud Meetings）。
 * - Teams com.microsoft.teams：已确认——Play 列表
 *   id=com.microsoft.teams。
 * - 钉钉 com.alibaba.android.rimet、Webex com.cisco.webex.meetings：
 *   本批未逐条再核——沿用两家长期公开的包名。即使包名有变也无害：
 *   显式意图先校验 resolveActivity，接不住就落回浏览器，不会错开
 *   别的应用（错包名＋该包又声明接这条链接，实际不可能）。
 * 不猜自定义 scheme：本批只用同一条 https 链接＋显式包名，不用
 * lark://、wemeet:// 等未核实的 scheme。
 */
fun meetingAppPackage(meetingUrl: String): String? {
    val host = hostOf(meetingUrl.lowercase()) ?: return null
    return when {
        host == "feishu.cn" || host.endsWith(".feishu.cn") -> "com.ss.android.lark"
        host == "meeting.tencent.com" || host.endsWith(".meeting.tencent.com") ||
            host.contains("wemeet") -> "com.tencent.wemeet.app"
        host == "voovmeeting.com" || host.endsWith(".voovmeeting.com") -> "com.tencent.voov"
        host == "zoom.us" || host.endsWith(".zoom.us") ||
            host == "zoom.com" || host.endsWith(".zoom.com") -> "us.zoom.videomeetings"
        host == "teams.microsoft.com" || host.endsWith(".teams.microsoft.com") ||
            host == "teams.live.com" || host.endsWith(".teams.live.com") -> "com.microsoft.teams"
        host == "dingtalk.com" || host.endsWith(".dingtalk.com") -> "com.alibaba.android.rimet"
        host == "webex.com" || host.endsWith(".webex.com") -> "com.cisco.webex.meetings"
        else -> null
    }
}

/** 取 URL 的 host（纯字符串处理，不依赖 android.net.Uri，JVM 可测） */
private fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return null
    val hostPort = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = hostPort.substringBefore(':').trim().trimEnd('.')
    return host.ifBlank { null }
}

/**
 * 飞书会议链接 → 官方入会深链（纯函数，单测锁死；第十八批）：
 * `feishu://applink.feishu.cn/client/videochat/open?action=join&num=<会议号>`。
 *
 * 核实依据（飞书 APK v7.70.12，详见收据 §核实结论）：
 * - manifest：RouterActivity 声明自定义 scheme `feishu://applink.feishu.cn`
 *  （无 path 限制）与 verified App Link `https://applink.feishu.cn`；这条
 *   自定义 scheme 不依赖 App Link 域名校验，显式限定包 com.ss.android.lark
 *   ＋ resolveActivity 校验后直接发。
 * - dex：路由表把路径 `/client/videochat/open` 映射到 VideoChatHandler
 *   （ly6/j），它读 query 参数 action 与 num；action=join＋num=<会议号>
 *   走「open join meeting page」分支（ey6/h.S），会议号经清洗器 g()
 *   （只留数字、最多 12 位），空则落回会议列表（ey6/h.R）。
 * - 会议号即 vc.feishu.cn/j/<9 位数字> 里的数字（飞书帮助文档：「会议号
 *   是视频会议链接的最后 9 位数字」）。
 *
 * 只对飞书会议链接（meetingAppPackage == com.ss.android.lark）构造；
 * 其它会议应用返回 null（走 489 链，不动）。提不到会议号（meeting.feishu.cn
 * 等无数字路径的链接、或数字少于 6 位）也返回 null——不要编一个不存在
 * 的会议号，落回 489 的显式包名 https 意图再走浏览器。
 * 清洗口径与应用侧 g() 相同：只留数字、最多 12 位。
 */
fun feishuJoinDeepLink(meetingUrl: String): String? {
    if (meetingAppPackage(meetingUrl) != "com.ss.android.lark") return null
    val number = extractFeishuMeetingNumber(meetingUrl) ?: return null
    return "feishu://applink.feishu.cn/client/videochat/open?action=join&num=$number"
}

/** 从飞书会议链接里提取会议号（纯字符串处理，JVM 可测；第十八批） */
private fun extractFeishuMeetingNumber(meetingUrl: String): String? {
    // 会议号是数字，lowercase 不影响提取；但路径里的 /j/ 可能大写。
    val afterScheme = meetingUrl.lowercase().substringAfter("://", "")
    if (afterScheme.isEmpty()) return null
    val path = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    // 优先 /j/ 后面那段（飞书会议链接格式 vc.feishu.cn/j/<9 位会议号>）；
    // 没有 /j/ 时取最后一段（meeting.feishu.cn 等其它形态的兜底）。
    val jIndex = segments.indexOf("j")
    val raw = if (jIndex >= 0 && jIndex + 1 < segments.size) segments[jIndex + 1]
        else segments.last()
    val digits = raw.filter { it.isDigit() }.take(12)
    // 会议号是 9 位数字；少于 6 位的大多是其它 id（年份、短标识），不认。
    return digits.ifBlank { null }?.takeIf { it.length >= 6 }
}

private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

/** 从日历事件的 location/description/title 提取在线会议链接；没有返回 null */
fun extractMeetingUrl(event: CalEvent): String? {
    val combined = "${event.location}\n${event.description}\n${event.title}"
    return URL_REGEX.findAll(combined)
        .map { it.value.trimEnd('.', ',', ')', ']', '!', '；', '。') }
        .firstOrNull { url ->
            val lower = url.lowercase()
            MEETING_DOMAINS.any { lower.contains(it) }
        }
}

/** 是否会议类事件：有会议链接即会议类（没有其他判定，不另造词表） */
fun isMeetingEvent(event: CalEvent): Boolean = extractMeetingUrl(event) != null

/**
 * 日历卡出现窗口（纯函数，第十二批）：会议类开始前 30 分钟、非会议日程
 * 开始前 2 小时出现；过了结束时间消失（deadline＝结束时间）。
 * 返回 (appearFromMs, deadlineMs)。
 */
fun calendarCardWindow(event: CalEvent): Pair<Long, Long> {
    val leadMs = if (isMeetingEvent(event)) 30L * 60 * 1000 else 2L * 60 * 60 * 1000
    return (event.begin - leadMs) to event.end
}

/** 日历卡一句话事实（纯函数）：时间＋标题＋地点（有就写，没有不硬凑） */
fun calendarCardFact(event: CalEvent, nowMs: Long): String {
    val dayStart = startOfDayMs(event.begin)
    val startMin = ((event.begin - dayStart) / 60_000L).toInt()
    val endMin = ((event.end - dayStart) / 60_000L).toInt()
    return buildString {
        append(relativeDayLabel(dayStart, nowMs))
        append(" ")
        append(formatTimeRange(startMin, endMin))
        append(" ")
        append(event.title.trim())
        val place = event.location.trim()
        if (place.isNotEmpty()) append("，$place")
    }
}

/**
 * 日历卡去重键（纯函数）：与短信预约卡同一口径（提案：同一件事短信和
 * 日历里都有，只留一张，以短信那条为准）——
 * `appt|<当天 00:00>|<开始分钟>|<地点 hash>`，与 SmartCardRepository 里
 * 短信预约卡的 key 同格式；候选顺序短信在前，去重后短信版优先。
 * 地点写法不一致（短信启发式提取 vs 日历地点原文）时键对不上、两张并存，
 * 是已知局限，不硬凑。
 */
fun calendarCardSourceKey(event: CalEvent): String {
    val dayStart = startOfDayMs(event.begin)
    val startMin = ((event.begin - dayStart) / 60_000L).toInt()
    val place = event.location.trim().ifBlank { null }
    return "appt|$dayStart|$startMin|${place?.hashCode() ?: 0}"
}

/**
 * 打开链路（第十八批，四级＋兜底）：
 * 1) 有会议链接 → 飞书先试官方入会深链（feishuJoinDeepLink，
 *    feishu://applink.feishu.cn/client/videochat/open?action=join&num=<会议号>，
 *    显式包名＋resolveActivity 校验，接不住落回下一级）；再走第十七批
 *    的显式包名 https 意图（同一条链接限定到该包，resolveActivity 非空
 *    才真正发），接不住落回通用浏览器意图直开链接；
 * 2) 无链接 → 打开日历 App；
 * 3) 再兜底按 content://com.android.calendar/events/{eventId} 打开事件详情；
 * 全失败返回 false（调用方提示，不在这里弹）。
 */
fun openCalendarEvent(context: Context, eventId: Long, meetingUrl: String?): Boolean {
    if (meetingUrl != null) {
        // 第十八批：飞书先试官方入会深链（已从飞书 APK 核实，
        // 见 feishuJoinDeepLink）：显式包名＋resolveActivity 校验，
        // 没装／没声明／没会议号时直接落回 489 链，不抛、不卡。
        val deepLink = feishuJoinDeepLink(meetingUrl)
        if (deepLink != null) {
            try {
                val deepIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setPackage("com.ss.android.lark")
                if (deepIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(deepIntent)
                    return true
                }
            } catch (_: Exception) { /* 没接住 → 落 489 链 */ }
        }
        // 先走会议应用：应用声明能接这条链接才真正发（没装／没声明时
        // resolveActivity 为 null，直接落回浏览器，不抛、不卡）。
        val appPackage = meetingAppPackage(meetingUrl)
        if (appPackage != null) {
            try {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(meetingUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setPackage(appPackage)
                if (appIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appIntent)
                    return true
                }
            } catch (_: Exception) { /* 没接住 → 落浏览器 */ }
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(meetingUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (_: Exception) { /* 链接打开失败 → 落日历兜底 */ }
    }
    try {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        }
    } catch (_: Exception) { }
    if (eventId > 0) {
        try {
            val uri = Uri.parse("content://com.android.calendar/events/$eventId")
            val viewIntent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                return true
            }
        } catch (_: Exception) { }
    }
    return false
}
