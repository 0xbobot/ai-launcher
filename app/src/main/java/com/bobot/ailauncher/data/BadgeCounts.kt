package com.bobot.ailauncher.data

/**
 * 未读角标计数（Bob 2026-11-07 晚：Dock + 应用中心显示消息数量）。
 *
 * 计数口径：通知监听服务推送的在用通知（可清除且非进行中），按包名汇总。
 * 展示列表只取最新 20 条，计数必须从完整列表算——否则“125 条”会被截成
 * 20 条以内的数（截断坑，本文件单测锁死）。
 * 数字文案：1–99 显示数字，≥100 显示 "99+"（Bob 亲选），0 条不显示。
 */

/** 按包名汇总未读条数（纯函数）：输入为完整在用通知列表，不截断、不排序。 */
fun aggregateBadgeCounts(notifications: List<SimpleNotification>): Map<String, Int> =
    notifications.groupingBy { it.packageName }.eachCount()

/** 角标数字文案（纯函数）：0 及以下不显示（null）；1–99 显示数字；≥100 显示 "99+"。 */
fun formatBadgeCount(count: Int): String? = when {
    count <= 0 -> null
    count >= 100 -> "99+"
    else -> count.toString()
}
