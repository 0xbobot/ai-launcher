package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.data.Capability

/**
 * planner 能力清单（hints）生成（意图改进二批，纯函数可单测）。
 *
 * 一批之前 hints 只有「id: 名称」，模型不知道每个能力要什么参数——
 * daohang 需要目的地这件事从没写进 prompt，缺参只能靠事后追问兜底。
 * 现在带参数 schema 的一行长这样：
 * `daohang: 导航（必填 to=目的地）`、
 * `ditie: 地铁（必填 lat=目的地纬度，必填 lon=目的地经度）`、
 * 无参能力保持 `wechat: 微信`。
 */
object CapabilityHints {

    fun build(capabilities: List<Capability>): String =
        capabilities.joinToString("\n") { cap ->
            if (cap.params.isEmpty()) {
                "${cap.id}: ${cap.label}"
            } else {
                val spec = cap.params.joinToString("，") { p ->
                    (if (p.required) "必填 " else "可选 ") + "${p.name}=${p.label}"
                }
                "${cap.id}: ${cap.label}（$spec）"
            }
        }
}
