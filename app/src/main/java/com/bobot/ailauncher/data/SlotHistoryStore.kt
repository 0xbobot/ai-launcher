package com.bobot.ailauncher.data

import android.content.Context
import org.json.JSONArray

/**
 * 槽位历史（v0.68.101·R-2）：用户在槽位快捷选择面板里**亲手确认过**的
 * 目的地（点选项或自由输入确认），作为之后目的地槽位的选项来源之一。
 *
 * 只记用户输入过的目的地文本（新→旧、去重、上限 8）；模板默认值不入
 * 历史（那不是用户数据）。本机 SharedPreferences 存储，不上传。
 */
object SlotHistoryStore {

    private const val PREFS = "slot_history"
    private const val KEY_DESTINATIONS = "destinations"

    fun destinations(context: Context): List<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DESTINATIONS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }.getOrDefault(emptyList())

    fun recordDestination(context: Context, value: String) {
        val merged = mergeHistory(destinations(context), value)
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DESTINATIONS, arr.toString()).apply()
    }

    /** 历史合并（纯逻辑，单测锁死）：新值置顶、去重、去空白，上限 [cap]。 */
    fun mergeHistory(old: List<String>, value: String, cap: Int = 8): List<String> {
        val v = value.trim()
        if (v.isEmpty()) return old.filter { it.isNotBlank() }.take(cap)
        return (listOf(v) + old.filter { it.isNotBlank() && it != v }).take(cap)
    }
}
