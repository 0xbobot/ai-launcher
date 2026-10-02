package com.bobot.ailauncher.data

import android.content.Context

/** 界面偏好：存 SharedPreferences "ui_prefs" */
object UiPrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_HANDED = "handed"

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
}
