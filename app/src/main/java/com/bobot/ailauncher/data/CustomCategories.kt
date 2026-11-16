package com.bobot.ailauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 用户自定义分组定义 */
data class CustomGroupDef(val id: String, val label: String)

/**
 * 用户整理分类：SharedPreferences "custom_cats"。
 * - "mapping": {packageName: groupId}，用户手动指定的应用归属（覆盖自动匹配）
 * - "groups": [{id, label}]，用户新建的自定义分组
 */
object CustomCategories {
    private const val PREFS = "custom_cats"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** packageName -> groupId */
    fun getMapping(context: Context): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(prefs(context).getString("mapping", "{}").orEmpty())
            obj.keys().forEach { k -> out[k] = obj.optString(k, "") }
        } catch (_: Exception) {
        }
        return out.filterValues { it.isNotBlank() }
    }

    fun getCustomGroups(context: Context): List<CustomGroupDef> {
        val out = mutableListOf<CustomGroupDef>()
        try {
            val arr = JSONArray(prefs(context).getString("groups", "[]").orEmpty())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                val label = o.optString("label", "")
                if (id.isNotBlank() && label.isNotBlank()) out += CustomGroupDef(id, label)
            }
        } catch (_: Exception) {
        }
        return out
    }

}
