package com.bobot.ailauncher.ui.pet

import android.content.Context
import com.bobot.ailauncher.BuildConfig

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
        const val EDIT_MODE = "edit_mode"           // 首次进入编辑模式
        const val P0_REPLY = "p0_reply"             // 首次出现 P0 需回复消息
        const val PET_LONGPRESS = "pet_longpress"   // 首次长按七仔
        const val VERSION_UPDATE = "version_update" // 版本更新后首次打开
        const val QUICK_ACTIONS = "quick_actions"   // v0.65.0：首次见到快捷操作区（已废弃，v0.65.2 移到气泡）
        const val QUICK_PANEL = "quick_panel"         // v0.65.2：首次展开七仔气泡快捷面板
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
     * 版本更新引导：记录上次展示时的 versionCode，
     * 当前版本更新则返回 true（需要展示），并更新记录。
     */
    fun shouldShowVersionUpdate(context: Context): Boolean {
        val last = prefs(context).getInt(KEY_PREFIX + Id.VERSION_UPDATE, 0)
        val cur = BuildConfig.VERSION_CODE
        if (cur > last) {
            prefs(context).edit().putInt(KEY_PREFIX + Id.VERSION_UPDATE, cur).apply()
            return last != 0 // 首次安装不算"更新"，不打扰
        }
        return false
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
}
