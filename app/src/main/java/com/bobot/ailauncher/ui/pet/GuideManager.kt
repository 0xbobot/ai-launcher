package com.bobot.ailauncher.ui.pet

import android.content.Context

/**
 * v0.64.5：渐进式用户指引系统。
 * 隐藏操作（长按/拖拽/左滑）用户发现不了，七仔在适当时机主动引导一次。
 * 原则：不打扰，每个引导最多展示 1 次，走 PetRepository.say() 统一入口。
 */
object GuideManager {
    private const val PREFS = "guide_prefs"
    private const val KEY_PREFIX = "guide_shown_"

    /** 引导 ID */
    object Id {
        const val P0_REPLY = "p0_reply"             // 首次出现 P0 需回复消息
        const val PET_LONGPRESS = "pet_longpress"   // 首次发现引导：首次长按前提示一次（P1-8）
        // 引导批（2026-10-27）：权限缺失提示一次——引导里跳过/拒绝后，首页对应
        // 内容优雅缺失（没有那块内容），七仔气泡提示一次去向，此后不再打扰。
        const val CALENDAR_MISSING = "calendar_missing"         // 首页无会议行时提示一次
        const val NOTIFICATION_MISSING = "notification_missing" // 首页无消息区时提示一次
        // 2026-10-26 宠物重构 D：死 id 删除（全仓 grep 仅定义、无调用方）——
        // EDIT_MODE（编辑模式引导）、VERSION_UPDATE（版本更新引导）、
        // QUICK_ACTIONS（v0.65.0 旧引导，已废弃）、QUICK_PANEL（v0.65.2 快捷面板引导，面板已无调用方）。
        // P0_REPLY 与 PET_LONGPRESS 保留。
        // v0.68.79：EQUIPMENT 已随装备环移除而删除
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否已展示过（普通引导：展示一次后不再展示） */
    fun hasShown(context: Context, id: String): Boolean =
        prefs(context).getBoolean(KEY_PREFIX + id, false)

    /** 标记已展示 */
    fun markShown(context: Context, id: String) {
        prefs(context).edit().putBoolean(KEY_PREFIX + id, true).apply()
    }

    /**
     * 尝试展示引导：未展示过则标记并返回 true（由调用方决定文案并 say）。
     * @return true=应该展示（调用方负责 say）
     */
    fun tryShow(context: Context, id: String): Boolean {
        if (hasShown(context, id)) return false
        markShown(context, id)
        return true
    }

    /** 首次发现引导文案（评审 P1-8）：点按 = 问、长按 = 表演。 */
    const val FIRST_DISCOVERY_TEXT = "点我可以直接问，长按可以看我表演"

    /** 权限缺失提示文案（引导批）：只讲去向（设置 → 权限常驻页），不讲机制。 */
    const val CALENDAR_MISSING_TEXT = "没看到今天的会？在设置 → 权限里打开日历就行"
    const val NOTIFICATION_MISSING_TEXT = "没看到要回的消息？在设置 → 权限里打开消息提醒就行"

    /**
     * 首次发现引导：每用户仅一次，门禁复用 PET_LONGPRESS（与 tryShow 同一
     * SharedPreferences 键 guide_shown_pet_longpress）。
     * 语义是“首次长按前提示一次”：由触发点在 Dock 宠物首次在场时主动说出，
     * 而不是等用户长按后才说。长按手势落地后，不要在长按回调里再调一次——
     * 门禁已被本引导消费，再调只会返回 false，且文案会与本次重复。
     * 渲染依赖：调用方走 PetRepository.say()；say 文本在首页的渲染面由气泡
     * 子项复活（SpeechBubble 在 354 基线里仍零调用方），本函数只负责门禁。
     * @return true=本次应说出引导（调用方负责 say）
     */
    fun tryShowFirstDiscovery(context: Context): Boolean =
        tryShow(context, Id.PET_LONGPRESS)
}
