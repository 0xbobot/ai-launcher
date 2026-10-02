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

    fun setMapping(context: Context, packageName: String, groupId: String) {
        val obj = try {
            JSONObject(prefs(context).getString("mapping", "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }
        obj.put(packageName, groupId)
        prefs(context).edit().putString("mapping", obj.toString()).apply()
    }

    fun removeMapping(context: Context, packageName: String) {
        val obj = try {
            JSONObject(prefs(context).getString("mapping", "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }
        obj.remove(packageName)
        prefs(context).edit().putString("mapping", obj.toString()).apply()
    }

    /** 批量写入 mapping（AI 智能分类用）：packageName -> groupId */
    fun setMappings(context: Context, mapping: Map<String, String>) {
        val obj = try {
            JSONObject(prefs(context).getString("mapping", "{}").orEmpty())
        } catch (_: Exception) {
            JSONObject()
        }
        mapping.forEach { (pkg, gid) -> obj.put(pkg, gid) }
        prefs(context).edit().putString("mapping", obj.toString()).apply()
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

    /** 新建自定义分组，返回分组 id */
    fun addCustomGroup(context: Context, label: String): String {
        val id = "custom_" + System.currentTimeMillis()
        val arr = try {
            JSONArray(prefs(context).getString("groups", "[]").orEmpty())
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(JSONObject().put("id", id).put("label", label))
        prefs(context).edit().putString("groups", arr.toString()).apply()
        return id
    }

    /** 删除自定义分组，并清理指向它的 mapping */
    fun removeCustomGroup(context: Context, id: String) {
        val kept = JSONArray()
        getCustomGroups(context).filter { it.id != id }.forEach {
            kept.put(JSONObject().put("id", it.id).put("label", it.label))
        }
        prefs(context).edit().putString("groups", kept.toString()).apply()
        getMapping(context).filter { it.value == id }.keys.forEach { pkg ->
            removeMapping(context, pkg)
        }
    }
}
