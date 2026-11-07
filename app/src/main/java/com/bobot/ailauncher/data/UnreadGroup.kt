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
