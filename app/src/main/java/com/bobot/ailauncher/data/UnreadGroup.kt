package com.bobot.ailauncher.data

import java.text.Collator
import java.util.Locale

/**
 * 应用中心「未读」组推导（Bob 2026-11-07 晚：有消息通知的应用排到 A 前面
 * 最优先显示）。纯函数，供单测；UI 层只做 remember 派生。
 *
 * “有消息通知” = 角标数 > 0（badgeCounts[包名]，口径与 451 角标计数
 * 一致——短信库未读数 / 通知 number 最大值 / 在用通知条数三路最大值；
 * 本文件只读它，不改计数逻辑）。
 */

/**
 * 未读组成员：角标 > 0 的应用；组内按未读数从大到小（最紧急的在最上面），
 * 数相同按应用名（中文 Collator，与 A-Z 排序同口径）排。
 */
fun deriveUnreadApps(apps: List<AppInfo>, badgeCounts: Map<String, Int>): List<AppInfo> {
    val collator = Collator.getInstance(Locale.CHINA)
    return apps.filter { (badgeCounts[it.packageName] ?: 0) > 0 }
        .sortedWith(
            compareByDescending<AppInfo> { badgeCounts[it.packageName] ?: 0 }
                .thenBy(collator) { it.label.toString() }
        )
}

/** 从一集里去掉指定包——进未读组的应用从字母组移出、也不进折叠组，只在一处出现。 */
fun withoutPkgs(apps: List<AppInfo>, pkgs: Set<String>): List<AppInfo> =
    if (pkgs.isEmpty()) apps else apps.filter { it.packageName !in pkgs }

/**
 * 未读预览行文案（Bob 2026-11-08 08:02 反馈①：未读组直接显示未读信息
 * 预览，让用户查看清理）。纯函数，供单测；UI 层只做 remember 派生。
 */

/** 截断约 60 字（纯函数）：先单行化（换行→空格、去首尾空白），超长取前 60 字 + "…"。 */
fun truncatePreview(text: String, maxChars: Int = 60): String {
    val single = text.replace('\n', ' ').replace('\r', ' ').trim()
    if (single.length <= maxChars) return single
    return single.take(maxChars).trimEnd() + "…"
}

/** 通知预览（纯函数）：标题 + 正文一起组装——两者都非空且不同时显示
 * “标题 · 正文”（Bob 2026-11-08 18:37 真机反馈：旧口径只取 text，标题被丢掉，
 * 豆包“做测试 · 今天 19:07 - 20:07”只剩后半截）；只一个有时显示那一个；
 * 标题 == 正文时只显示一次；两者都空 → null（不显示预览行）。 */
fun notificationPreviewText(n: SimpleNotification?): String? {
    if (n == null) return null
    val title = n.title.trim()
    val text = n.text.trim()
    val raw = when {
        title.isNotEmpty() && text.isNotEmpty() && title != text -> "$title · $text"
        text.isNotEmpty() -> text
        title.isNotEmpty() -> title
        else -> return null
    }
    return truncatePreview(raw)
}

/** 短信预览（纯函数）：最新一条未读短信正文片段；空 → null。 */
fun smsPreviewText(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return truncatePreview(body)
}

/**
 * 未读行显示文案（Bob 2026-11-08 22:45 真机反馈：未读组里用通知标题当行名
 * 更清晰，预览只留正文、不再拼“标题 · 正文”）。纯函数，供单测；UI 层只做
 * remember 派生。
 *
 * - 短信应用且有未读短信正文：没有通知标题，行名留应用名、预览取短信正文；
 * - 否则取最新一条在用通知：标题非空 → 行名 = 标题；预览只留正文，正文与
 *   标题相同时不重复（预览置空，不显示该行）；
 * - 标题空 → 行名留应用名，预览按正文（只有正文时就是正文）；
 * - 都没有 → 行名留应用名、不显示预览行。
 */
data class UnreadRowText(val displayName: String, val preview: String?)

fun unreadRowText(
    appLabel: String,
    latest: SimpleNotification?,
    smsBody: String?,
    isSmsApp: Boolean
): UnreadRowText {
    if (isSmsApp && !smsBody.isNullOrBlank()) {
        return UnreadRowText(appLabel, smsPreviewText(smsBody))
    }
    val title = latest?.title?.trim().orEmpty()
    val text = latest?.text?.trim().orEmpty()
    val displayName = if (title.isNotEmpty()) title else appLabel
    val preview = if (text.isNotEmpty() && text != title) truncatePreview(text) else null
    return UnreadRowText(displayName, preview)
}

/**
 * 每包最新一条在用通知（纯函数，供单测；未读组预览 + 左滑启用判定用）：
 * 空列表 → 空 map；同包多条取时间最晚的一条。不取首个、不做 `!!`——
 * 空组、空列表都返回空 map，不崩（2026-11-08 应用中心崩溃 hotfix：
 * 服务层原来用 `maxByOrNull { it.time }!!`，理论上 groupBy 的组非空，
 * 但它是 458 差分里唯一的 `!!`，改成无 `!!` 的纯函数以绝后患）。
 */
fun latestNotificationByPackage(
    notifications: List<SimpleNotification>
): Map<String, SimpleNotification> =
    notifications.groupBy { it.packageName }
        .mapNotNull { (pkg, list) -> list.maxByOrNull { it.time }?.let { pkg to it } }
        .toMap()

/**
 * 按首字母分组（纯函数）：与 AppCenter.kt azGroups 同口径——字典/区位码
 * 由调用方包进 [groupKeyOf]，'#' 排最后。某字母组因成员全被移走而变空时，
 * 该字母自然不在结果里（rail 字母随之消失，与折叠组一致）。
 */
fun groupAppsByLetter(
    apps: List<AppInfo>,
    groupKeyOf: (AppInfo) -> Char
): List<Pair<Char, List<AppInfo>>> {
    val map = linkedMapOf<Char, MutableList<AppInfo>>()
    apps.forEach { app -> map.getOrPut(groupKeyOf(app)) { mutableListOf() }.add(app) }
    return map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
}
