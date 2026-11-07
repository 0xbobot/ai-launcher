package com.bobot.ailauncher.data

/**
 * 未读角标计数（Bob 2026-11-07 晚：Dock + 应用中心显示消息数量；
 * 同晚 Bob 修正口径：角标要的是应用的未读数据，不是通知栏的消息数，
 * 通知栏的数保留作兜底）。
 *
 * 每包角标数 = 下面三路的最大值：
 * ① 短信库未读数（仅默认短信应用包，需 READ_SMS）——最准的短信未读数，
 *    不受通知栏清掉影响；
 * ② 通知 number 最大值（应用通过 setNumber 上报的未读数，是 Feishu/微信
 *    这类应用目前唯一能拿到的未读信号；未设为 0，不参与）；
 * ③ 在用通知条数（兜底，保留 450 的逻辑）。
 * 展示列表只取最新 20 条，计数必须从完整列表算——否则“125 条”会被截成
 * 20 条以内的数（截断坑，本文件单测锁死）。
 * 数字文案：1–99 显示数字，≥100 显示 "99+"（Bob 亲选），0 条不显示。
 *
 * 局限（Bob 已知）：Feishu/微信没有公开的未读数接口，number 是它们唯一
 * 能上报的信号——若某应用不设 number 且通知栏已清，角标仍会是 0。
 */

/** 按包名汇总未读条数（纯函数）：输入为完整在用通知列表，不截断、不排序。 */
fun aggregateBadgeCounts(notifications: List<SimpleNotification>): Map<String, Int> =
    notifications.groupingBy { it.packageName }.eachCount()

/**
 * 每包通知 number 最大值（纯函数）：输入为（包名，notification.number）对；
 * 只取 >0 的（未设 number 为 0，不参与取最大）。
 */
fun aggregateNumberMax(numbers: List<Pair<String, Int>>): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    for ((pkg, n) in numbers) {
        if (n <= 0) continue
        val cur = out[pkg] ?: 0
        if (n > cur) out[pkg] = n
    }
    return out
}

/**
 * 合并三路角标数（纯函数），每包取最大值；0 的包不进 map：
 * - notifCounts：每包在用通知条数（兜底）
 * - numberMax：每包通知 number 最大值（应用自报未读数）
 * - smsUnread：短信库未读数，只作用于默认短信应用包
 * - defaultSmsPackage：默认短信应用包名（null 时短信数不起作用）
 */
fun combineBadgeCounts(
    notifCounts: Map<String, Int>,
    numberMax: Map<String, Int>,
    smsUnread: Int,
    defaultSmsPackage: String?
): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    for (pkg in notifCounts.keys + numberMax.keys) {
        val v = maxOf(notifCounts[pkg] ?: 0, numberMax[pkg] ?: 0)
        if (v > 0) out[pkg] = v
    }
    if (defaultSmsPackage != null && smsUnread > 0) {
        out[defaultSmsPackage] = maxOf(out[defaultSmsPackage] ?: 0, smsUnread)
    }
    return out
}

/** 角标数字文案（纯函数）：0 及以下不显示（null）；1–99 显示数字；≥100 显示 "99+"。 */
fun formatBadgeCount(count: Int): String? = when {
    count <= 0 -> null
    count >= 100 -> "99+"
    else -> count.toString()
}
