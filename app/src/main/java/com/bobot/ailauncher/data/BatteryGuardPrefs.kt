package com.bobot.ailauncher.data

import android.content.Context

/**
 * v0.28.0：低电量守护配置。
 * 原则：只做温和提醒，零干预、零侵略感。
 */
object BatteryGuardPrefs {
    private const val FILE = "battery_guard"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WARN_PCT = "warn_pct"
    private const val KEY_CRITICAL_PCT = "critical_pct"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, v: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, v).apply()
    }

    /** 提醒阈值，默认 10 */
    fun warnPct(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_WARN_PCT, 10)

    fun setWarnPct(context: Context, v: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WARN_PCT, v.coerceIn(5, 30)).apply()
    }

    /** 紧急阈值，默认 5 */
    fun criticalPct(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_CRITICAL_PCT, 5)

    fun setCriticalPct(context: Context, v: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CRITICAL_PCT, v.coerceIn(1, 15)).apply()
    }
}
