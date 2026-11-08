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

/** 截断约 40 字（纯函数）：先单行化（换行→空格、去首尾空白），超长取前 40 字 + "…"。 */
fun truncatePreview(text: String, maxChars: Int = 40): String {
    val single = text.replace('\n', ' ').replace('\r', ' ').trim()
    if (single.length <= maxChars) return single
    return single.take(maxChars).trimEnd() + "…"
}

/** 通知预览（纯函数）：优先 text，没有 text 取 title；两者都空 → null（不显示预览行）。 */
fun notificationPreviewText(n: SimpleNotification?): String? {
    if (n == null) return null
    val raw = n.text.ifBlank { n.title }
    if (raw.isBlank()) return null
    return truncatePreview(raw)
}

/** 短信预览（纯函数）：最新一条未读短信正文片段；空 → null。 */
fun smsPreviewText(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return truncatePreview(body)
}

/**
 * 未读组某行的预览文案（纯函数）：
 * - 短信应用（默认/可见短信包）：最新未读短信正文；没有未读短信时退回通知正文；
 * - 其他应用：最新一条在用通知正文（优先 text，没有取 title）；
 * - 都没有 → null（不显示预览行，只留应用名+角标）。
 */
fun previewForApp(
    packageName: String,
    latestByPackage: Map<String, SimpleNotification>,
    smsPackages: Set<String>,
    smsBody: String?
): String? {
    if (packageName in smsPackages) {
        return smsPreviewText(smsBody) ?: notificationPreviewText(latestByPackage[packageName])
    }
    return notificationPreviewText(latestByPackage[packageName])
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
