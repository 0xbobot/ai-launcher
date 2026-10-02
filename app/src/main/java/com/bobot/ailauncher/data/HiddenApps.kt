package com.bobot.ailauncher.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray

/**
 * 已隐藏应用：SharedPreferences "hidden_apps"。
 * - "hidden": [packageName]，用户右滑隐藏的应用包名集合（v0.14 纠正：右滑=少）
 * - A-Z 列表与分组视图都会过滤掉这些应用；设置页可恢复
 * - version：每次变更 +1，供 Compose 订阅刷新
 */
object HiddenApps {
    private const val PREFS = "hidden_apps"
    private const val KEY = "hidden"

    /** 全局版本号：hide/unhide 时 +1，UI 层用它触发重组 */
    val version = mutableIntStateOf(0)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHidden(context: Context): Set<String> {
        val out = mutableSetOf<String>()
        try {
            val arr = JSONArray(prefs(context).getString(KEY, "[]").orEmpty())
            for (i in 0 until arr.length()) {
                arr.optString(i, "").takeIf { it.isNotBlank() }?.let { out += it }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun isHidden(context: Context, packageName: String): Boolean =
        getHidden(context).contains(packageName)

    fun hide(context: Context, packageName: String) {
        val set = getHidden(context).toMutableSet()
        if (set.add(packageName)) {
            save(context, set)
        }
    }

    fun unhide(context: Context, packageName: String) {
        val set = getHidden(context).toMutableSet()
        if (set.remove(packageName)) {
            save(context, set)
        }
    }

    private fun save(context: Context, set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        version.intValue++
    }
}
