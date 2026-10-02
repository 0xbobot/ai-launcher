package com.bobot.ailauncher.data

import android.content.Context

/** 界面偏好：存 SharedPreferences "ui_prefs" */
object UiPrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_HANDED = "handed"
    private const val KEY_DOCK_ROWS = "dock_rows"

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
}
