package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** 界面偏好：存 SharedPreferences "ui_prefs" */
object UiPrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_DOCK_ROWS = "dock_rows"
    private const val KEY_DOCK_VISIBLE = "dock_visible"
    private const val KEY_DOCK_HIDE_WARNED = "dock_hide_warned"
    private const val KEY_DOCK_SMART_SORT = "dock_smart_sort"
    private const val KEY_DOCK_PINNED = "dock_pinned"
    private const val KEY_ROW_ACTIONS_GUIDE_SEEN = "row_actions_guide_seen"
    private const val KEY_PANEL_GUIDE_SEEN = "panel_guide_seen"
    private const val KEY_PANEL_GUIDE_REMIND = "panel_guide_reminded"

    /** Dock 数据变化通知：置顶增删/排序后 bump，Dock 侧重算 */
    val dockTick = MutableStateFlow(0)
    // v0.56.2（Bob）：Rail 图标闪烁——收藏/隐藏时提示目标位置；Pair(计数, "star"/"eye")
    private val _railFlash = MutableStateFlow(0 to "")
    val railFlash = _railFlash
    fun flashRail(target: String) {
        _railFlash.value = (_railFlash.value.first + 1) to target
    }
    fun bumpDock() {
        dockTick.value++
    }

    /** 首页 Dock 记住的行数（1=D1 一行，2=D2 两行），默认 1 */
    fun getDockRows(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DOCK_ROWS, 1).coerceIn(1, 2)
    }

    fun setDockRows(context: Context, rows: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DOCK_ROWS, rows.coerceIn(1, 2)).apply()
    }

    /** Dock 是否显示（v0.16：隐藏的唯一入口是 D1 右滑+确认框，恢复走设置页开关） */
    fun getDockVisible(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOCK_VISIBLE, true)
    }

    fun setDockVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_VISIBLE, visible).apply()
    }

    /** 是否已勾选"隐藏 Dock 不再提醒" */
    fun getDockHideWarned(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOCK_HIDE_WARNED, false)
    }

    fun setDockHideWarned(context: Context, warned: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_HIDE_WARNED, warned).apply()
    }

    /**
     * Dock 智能排序（v0.57.0：默认开启，不再提供开关）。恒为 true。
     */
    fun getDockSmartSort(context: Context): Boolean = true

    fun setDockSmartSort(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_SMART_SORT, enabled).apply()
    }

    /**
     * Dock 置顶：用户手动固定的应用包名有序列表（v0.35.0）。
     * Dock 渲染时置顶排最前（按此顺序），后面再跟智能排序补齐。
     */
    fun getDockPinned(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOCK_PINNED, "").orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun setDockPinned(context: Context, pkgs: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DOCK_PINNED, pkgs.filter { it.isNotBlank() }.distinct().joinToString("\n")).apply()
        bumpDock()
    }

    /** 应用行左滑操作区的新手引导是否已展示过（v0.35.0）：只打扰一次 */
    fun hasSeenRowActionsGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ROW_ACTIONS_GUIDE_SEEN, false)
    }

    fun setRowActionsGuideSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ROW_ACTIONS_GUIDE_SEEN, true).apply()
    }

    /**
     * 首屏下滑手势的权限说明卡（v0.36.0）：第一次触发下滑手势但无障碍未开启时，
     * 先弹 App 内说明卡而不是直接跳系统设置。看过一次后不再弹整卡。
     */
    fun hasSeenPanelGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PANEL_GUIDE_SEEN, false)
    }

    fun setPanelGuideSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PANEL_GUIDE_SEEN, true).apply()
    }

    /**
     * 未授权时的二次轻提醒（v0.36.0）：说明卡看过、点了"去开启"但仍没授权，
     * 下次触发时再轻提醒一次，之后不再打扰（回落到 Toast）。
     */
    fun hasRemindedPanelGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PANEL_GUIDE_REMIND, false)
    }

    fun setPanelGuideReminded(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PANEL_GUIDE_REMIND, true).apply()
    }
}
