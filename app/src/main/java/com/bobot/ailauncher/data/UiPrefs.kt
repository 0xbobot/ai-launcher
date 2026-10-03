package com.bobot.ailauncher.data

import android.content.Context

/** 界面偏好：存 SharedPreferences "ui_prefs" */
object UiPrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_HANDED = "handed"
    private const val KEY_DOCK_ROWS = "dock_rows"
    private const val KEY_DOCK_VISIBLE = "dock_visible"
    private const val KEY_DOCK_HIDE_WARNED = "dock_hide_warned"
    private const val KEY_DOCK_SMART_SORT = "dock_smart_sort"

    enum class Handed { LEFT, RIGHT }

    fun getHanded(context: Context): Handed {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HANDED, Handed.RIGHT.name).orEmpty()
        return try {
            Handed.valueOf(v)
        } catch (_: Exception) {
            Handed.RIGHT
        }
    }

    fun setHanded(context: Context, handed: Handed) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HANDED, handed.name).apply()
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
     * Dock 智能排序（PRD §八/§九）：按总频次 + 当前时段亲和度排列；
     * 关闭后按名称排列。默认开，用户可关（AI 推荐，不能强行改变）。
     */
    fun getDockSmartSort(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOCK_SMART_SORT, true)
    }

    fun setDockSmartSort(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_SMART_SORT, enabled).apply()
    }
}
