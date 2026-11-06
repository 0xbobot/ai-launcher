package com.bobot.ailauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 用户教的意图→应用映射（Bob 2026-11-06：识别不到时的反馈闭环）。
 *
 * 搜不到时，用户在选择器里点了一个应用，就把这次的映射（整句查询→该应用包名）
 * 存下来，以后同样的句式能认出来。键是归一化后的整句（与
 * [AppSearchIndex.search] 同口径：trim/lowercase/去空格），值是包名。
 *
 * 另开一份 SharedPreferences "intent_mapping"，不碰 assets 里的 capabilities.json
 * （运行时改不了），也不碰 "app_usage"（Dock 推荐）/"action_usage"（智能关键字）。
 * 存取永不抛（try/catch 包住），映射丢了只是下次再认不出，不影响正常搜索。
 */
object UserIntentMapping {
    private const val PREFS_NAME = "intent_mapping"
    private const val KEY_MAP_PREFIX = "map_"
    private const val KEY_LAST_PREFIX = "last_"

    /** 版本号：存一次 +1，供 AppCenter 把它挂进搜索 remember 的 key，存完立即重算。 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** 与 AppSearchIndex.search 同口径的归一化（trim/lowercase/去空格）。 */
    fun normalizeQuery(query: String): String =
        query.trim().lowercase().replace(" ", "")

    /** 生产入口：把（查询→包名）写进 SharedPreferences。 */
    fun save(context: Context, query: String, packageName: String) {
        try {
            save(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                query,
                packageName,
                System.currentTimeMillis()
            )
        } catch (_: Exception) {
        }
    }

    /** 可测入口：显式传 prefs 与时间（测试用 FakePrefs，不碰 Android 框架）。 */
    fun save(prefs: SharedPreferences, query: String, packageName: String, nowMs: Long) {
        try {
            val key = normalizeQuery(query)
            if (key.isEmpty() || packageName.isBlank()) return
            prefs.edit()
                .putString(KEY_MAP_PREFIX + key, packageName)
                .putLong(KEY_LAST_PREFIX + key, nowMs)
                .apply()
            _version.update { it + 1 }
        } catch (_: Exception) {
        }
    }

    /** 生产入口：查某句以前被用户点名过哪家（没有返回 null）。 */
    fun lookup(context: Context, query: String): String? {
        return try {
            lookup(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), query)
        } catch (_: Exception) {
            null
        }
    }

    /** 可测入口：显式传 prefs。 */
    fun lookup(prefs: SharedPreferences, query: String): String? {
        return try {
            val key = normalizeQuery(query)
            if (key.isEmpty()) null
            else prefs.getString(KEY_MAP_PREFIX + key, null)
        } catch (_: Exception) {
            null
        }
    }
}
