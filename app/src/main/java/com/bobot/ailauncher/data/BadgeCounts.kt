package com.bobot.ailauncher.data

import android.app.AppOpsManager

/**
 * 未读角标计数（Bob 2026-11-07 晚：Dock + 应用中心显示消息数量；
 * 同晚 Bob 修正口径：角标要的是应用的未读数据，不是通知栏的消息数，
 * 通知栏的数保留作兜底）。
 *
 * 每包角标数 = 下面四路的最大值：
 * ① 短信库未读数（仅默认短信应用包，需 READ_SMS）——最准的短信未读数，
 *    不受通知栏清掉影响；
 * ② 通知 number 最大值（应用通过 setNumber 上报的未读数，是 Feishu/微信
 *    这类应用目前唯一能拿到的未读信号；未设为 0，不参与）；
 * ③ 在用通知条数（兜底，保留 450 的逻辑）。
 * ④ 粘性数（Bob 2026-11-08：应用最后一次上报的 number，通知栏清掉后仍粘住；
 *    启动应用或新通知 number=0 时清掉；不会立刻变出飞书 125）。
 * 展示列表只取最新 20 条，计数必须从完整列表算——否则“125 条”会被截成
 * 20 条以内的数（截断坑，本文件单测锁死）。
 * 数字文案：1–99 显示数字，≥100 显示 "99+"（Bob 亲选），0 条不显示。
 *
 * 局限（Bob 已知）：Feishu/微信没有公开的未读数接口，number 是它们唯一
 * 能上报的信号——若某应用不设 number 且通知栏已清，角标仍会是 0。
 */

/**
 * 通知权限是否开（纯函数，判 AppOpsManager 的 POST_NOTIFICATION mode；Bob
 * 2026-11-15：通知关了，角标里的通知那份就清掉）。
 *
 * - MODE_ALLOWED → 开；
 * - MODE_IGNORED / MODE_ERRORED → 关（明确拒绝）；
 * - MODE_DEFAULT → 不确定（系统没记过明确决定），按权限兜底：持有
 *   POST_NOTIFICATIONS 权限算开、没持有算关（目标 33 以下的老应用权限
 *   安装即授予，走这条不会被误删）；
 * - 其他未知 mode → 开（fail safe，保角标、不误删）。
 *
 * 本函数只用 AppOpsManager 的 mode 常量（static final int，编译期
 * 内联，JVM 单测不加载 AppOpsManager 类）；真正的 mode 查询
 * （checkOpNoThrow + 取 uid）在调用方（通知监听服务）做，查不到
 * uid / 抛异常时调用方一律按开处理。
 */
fun postNotificationAllowed(mode: Int, permissionGranted: Boolean): Boolean = when (mode) {
    AppOpsManager.MODE_ALLOWED -> true
    AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
    AppOpsManager.MODE_DEFAULT -> permissionGranted
    else -> true
}

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
 * - stickyCounts：粘性数（Bob 2026-11-08），每包取最大值；为空时等价于旧的三路合并。
 */
fun combineBadgeCounts(
    notifCounts: Map<String, Int>,
    numberMax: Map<String, Int>,
    smsUnread: Int,
    defaultSmsPackage: String?,
    stickyCounts: Map<String, Int> = emptyMap()
): Map<String, Int> = combineBadgeCounts(
    notifCounts, numberMax, smsUnread, listOfNotNull(defaultSmsPackage),
    stickyCounts = stickyCounts
)

/**
 * 合并三路角标数 + 探针第四路 + 粘性数第五路（纯函数），每包取最大值；0 的包不进 map：
 * - smsPackages：短信数要落的包（默认短信应用 + 用户实际看到的短信应用，
 *   点号码发短信时会打开的那个；两者相同时只记一处，不同则两处都记，各取最大值。
 *   Bob 2026-11-08 凌晨：短信角标 6、系统桌面 15，机制已通——归属加固作兜底，
 *   主因是短信库 read 标志与应用自己的未读定义对不上，不是记错包）
 * - extraCounts：探针第四路（角标广播 + ZTE provider 读到的数），每包取最大值；
 *   为空时等价于旧的三路合并。探针失败时调用方传空 map，角标行为一字不变。
 * - stickyCounts：粘性数第五路（Bob 2026-11-08：应用最后一次上报的 number，
 *   通知栏清掉后仍粘住；启动应用或新通知 number=0 时清掉）；为空时不影响旧行为。
 * - notifOffPackages：通知权限关着的包（Bob 2026-11-15：通知关了，角标里的
 *   通知那份就清掉）——这些包的通知派生信号（在用通知条数、通知 number 最大值、
 *   粘性数）不参与取最大；探针第四路（角标广播 + ZTE provider，应用自己报数 /
 *   系统桌面自己的通道，不走我们的通知监听）与短信数不受影响。
 */
fun combineBadgeCounts(
    notifCounts: Map<String, Int>,
    numberMax: Map<String, Int>,
    smsUnread: Int,
    smsPackages: Collection<String>,
    extraCounts: Map<String, Int> = emptyMap(),
    stickyCounts: Map<String, Int> = emptyMap(),
    notifOffPackages: Set<String> = emptySet()
): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    for (pkg in notifCounts.keys + numberMax.keys + extraCounts.keys + stickyCounts.keys) {
        val notifOff = pkg in notifOffPackages
        val v = maxOf(
            if (notifOff) 0 else notifCounts[pkg] ?: 0,
            if (notifOff) 0 else numberMax[pkg] ?: 0,
            extraCounts[pkg] ?: 0,
            if (notifOff) 0 else stickyCounts[pkg] ?: 0
        )
        if (v > 0) out[pkg] = v
    }
    if (smsUnread > 0) {
        for (pkg in smsPackages) {
            out[pkg] = maxOf(out[pkg] ?: 0, smsUnread)
        }
    }
    return out
}

/** 角标数字文案（纯函数）：0 及以下不显示（null）；1–99 显示数字；≥100 显示 "99+"。 */
fun formatBadgeCount(count: Int): String? = when {
    count <= 0 -> null
    count >= 100 -> "99+"
    else -> count.toString()
}
