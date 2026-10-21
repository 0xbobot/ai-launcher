package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.CapabilityParam
import org.junit.Assert.assertEquals
import org.junit.Test

/** hints 生成纯逻辑单测（意图改进二批）：模型必须能从清单里看出每个能力要什么参数。 */
class CapabilityHintsTest {

    private fun cap(id: String, label: String, params: List<CapabilityParam> = emptyList()) =
        Capability(
            id = id,
            label = label,
            iconName = "",
            packageNames = listOf("com.example.$id"),
            deeplinkTemplate = null,
            params = params
        )

    @Test
    fun `paramless capability stays plain id-label`() {
        val hints = CapabilityHints.build(listOf(cap("wechat", "微信")))
        assertEquals("wechat: 微信", hints)
    }

    @Test
    fun `required params are spelled out in hints`() {
        val hints = CapabilityHints.build(
            listOf(
                cap(
                    "daohang", "导航",
                    listOf(CapabilityParam("to", "目的地", required = true, ask = "导航去哪儿？"))
                ),
                cap(
                    "ditie", "地铁",
                    listOf(
                        CapabilityParam("lat", "目的地纬度", required = true),
                        CapabilityParam("lon", "目的地经度", required = true)
                    )
                )
            )
        )
        assertEquals(
            "daohang: 导航（必填 to=目的地）\n" +
                "ditie: 地铁（必填 lat=目的地纬度，必填 lon=目的地经度）",
            hints
        )
    }

    @Test
    fun `optional params are marked optional`() {
        val hints = CapabilityHints.build(
            listOf(cap("x", "X", listOf(CapabilityParam("from", "起点", required = false))))
        )
        assertEquals("x: X（可选 from=起点）", hints)
    }
}
