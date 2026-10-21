package com.bobot.ailauncher.data

import java.net.URLEncoder

/**
 * Deep link 模板填充（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 意图改进二批·兜底防线：旧实现把没传值的 `{param}` 占位符直接抹掉再打开，
 * 于是「导航」没给目的地也会打开一个空目的地的路线页、上层还报成功（假成功）。
 * 现在规则只有一条：**任一占位符没有非空值 → 整条链接作废（返回 null）**，
 * 调用方只能降级到「打开 App 主页」，永远不许打开残缺链接。
 */
object DeeplinkTemplate {

    private val PLACEHOLDER = Regex("\\{([^{}]+)\\}")

    /** 模板里声明的占位符名（按出现顺序，去重） */
    fun placeholders(template: String): List<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.distinct().toList()

    /**
     * 用 [params] 填充模板（值做 URL 编码）。
     * 模板没有占位符时原样返回；任一占位符缺值/空白 → 返回 null。
     */
    fun fill(template: String, params: Map<String, String>): String? {
        var missing = false
        val filled = PLACEHOLDER.replace(template) { m ->
            val value = params[m.groupValues[1]]?.trim().orEmpty()
            if (value.isEmpty()) {
                missing = true
                m.value
            } else {
                URLEncoder.encode(value, "UTF-8")
            }
        }
        return if (missing) null else filled
    }
}
