package com.bobot.ailauncher.ui.pet

import android.content.Context

/**
 * v0.62.0：TODAY 卡片自定义——区块排序 + 显隐持久化。
 * key="today_section_order"：逗号分隔，如 "next,msg,todo"
 * key="today_section_visible"：逗号分隔的可见集合，如 "next,msg,todo"
 * key="today_weather_visible"：天气 pill 是否显示
 */
enum class TodaySection(val key: String, val label: String) {
    NEXT("next", "接下来"),
    MSG("msg", "消息"),
    TODO("todo", "待办");

    companion object {
        fun fromKey(key: String): TodaySection? = entries.firstOrNull { it.key == key }
    }
}

object TodayPrefs {
    private const val PREFS = "today_card_prefs"
    private const val KEY_ORDER = "today_section_order"
    private const val KEY_VISIBLE = "today_section_visible"
    private const val KEY_WEATHER_VISIBLE = "today_weather_visible"

    private val DEFAULT_ORDER = listOf(TodaySection.NEXT, TodaySection.MSG, TodaySection.TODO)
    private val DEFAULT_VISIBLE = setOf(TodaySection.NEXT, TodaySection.MSG, TodaySection.TODO)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadOrder(context: Context): List<TodaySection> {
        val raw = prefs(context).getString(KEY_ORDER, null) ?: return DEFAULT_ORDER
        val parsed = raw.split(",").mapNotNull { TodaySection.fromKey(it.trim()) }
        // 容错：缺失的补上，多余的去掉，保证三个都在
        val merged = (parsed + DEFAULT_ORDER).distinct()
        return merged.filter { it in DEFAULT_ORDER }
    }

    fun saveOrder(context: Context, order: List<TodaySection>) {
        prefs(context).edit()
            .putString(KEY_ORDER, order.joinToString(",") { it.key })
            .apply()
    }

    fun loadVisible(context: Context): Set<TodaySection> {
        val raw = prefs(context).getString(KEY_VISIBLE, null)
            ?: return DEFAULT_VISIBLE
        return raw.split(",").mapNotNull { TodaySection.fromKey(it.trim()) }.toSet()
    }

    fun saveVisible(context: Context, visible: Set<TodaySection>) {
        prefs(context).edit()
            .putString(KEY_VISIBLE, visible.joinToString(",") { it.key })
            .apply()
    }

    fun loadWeatherVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WEATHER_VISIBLE, true)

    fun saveWeatherVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEATHER_VISIBLE, visible).apply()
    }

    // v0.64.2：用户拖拽排序（组级别），key="today_user_order"，null=用 AI 优先级
    private const val KEY_USER_ORDER = "today_user_order"

    fun loadUserOrder(context: Context): List<String>? {
        val raw = prefs(context).getString(KEY_USER_ORDER, null) ?: return null
        val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return list.takeIf { it.isNotEmpty() }
    }

    fun saveUserOrder(context: Context, order: List<String>?) {
        val e = prefs(context).edit()
        if (order == null) e.remove(KEY_USER_ORDER)
        else e.putString(KEY_USER_ORDER, order.joinToString(","))
        e.apply()
    }
}
