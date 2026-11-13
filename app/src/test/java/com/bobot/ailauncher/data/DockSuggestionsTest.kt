package com.bobot.ailauncher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大模型建议第一批纯函数单测（Bob 2026-11-13 23:18 亲定「按改后的方案做
 * 第一批」）：候选预筛、payload、JSON 解析、复核门。LLM 调用本身 CI
 * 测不了（无 Key）——纯逻辑与调用层分开，调用走分支 CI + Bob 真机验收
 * （真机需已配 Key；无 Key 时按钮不出现是符合设计的，不是 bug）。
 */
class DockSuggestionsTest {

    private fun stats(
        pkg: String,
        hw: Map<String, Int> = emptyMap(),
        total7: Int = 0,
        total: Int = 0,
        lastUsedDays: Int = -1,
        pinned: Boolean = false,
        dockRank: Int? = null,
        transfersIn: List<TransferIn> = emptyList(),
        transfersOutTotal: Int = 0,
        transfersOutTargets: Int = 0,
    ) = AppStats(pkg, pkg, hw, total7, total, lastUsedDays, pinned, dockRank,
        transfersIn, transfersOutTotal, transfersOutTargets)

    private fun snap(vararg apps: AppStats, top10: List<String> = emptyList()) =
        UsageSnapshot(apps.toList(), top10, dow = 1, hour = 21, dayType = "weekday")

    private fun nominate(
        s: UsageSnapshot,
        prev: UsageSnapshot? = null,
        prevTop10: List<String>? = null,
        muted: Set<SuggestionKind> = emptySet(),
        suppressedPin: Set<String> = emptySet(),
        suppressedUnpin: Set<String> = emptySet(),
    ) = nominateCandidates(s, prev, prevTop10, muted, suppressedPin, suppressedUnpin)

    private fun rawBody(content: String): String =
        JSONObject().put(
            "choices",
            JSONArray().put(
                JSONObject().put(
                    "message",
                    JSONObject().put("content", content),
                ),
            ),
        ).toString()

    private fun pinJson(
        pkg: String = "com.example.chat",
        hwValue: Int = 5,
        total7Value: Int = 14,
        kind: String = "pin",
        actionType: String = "pin",
        metric: String = "hwDayType",
    ): String = """
        {"suggestions":[{"id":"pin_$pkg","kind":"$kind",
        "title":"晚上常开，固定到 Dock","body":"工作日 21 点开了 $hwValue 次，近 7 天 $total7Value 次",
        "evidence":[{"metric":"$metric","pkg":"$pkg","dayType":"weekday","hour":21,"value":$hwValue},
        {"metric":"total7","pkg":"$pkg","value":$total7Value}],
        "action":{"type":"$actionType","pkg":"$pkg"}}]}
    """.trimIndent()

    private fun chatSnap(pinned: Boolean = false) = snap(
        stats(
            pkg = "com.example.chat",
            hw = mapOf("weekday-21" to 5),
            total7 = 14,
            total = 40,
            lastUsedDays = 2,
            pinned = pinned,
            dockRank = 0,
            transfersIn = listOf(TransferIn("com.example.hub", 8, true)),
        ),
        top10 = listOf("com.example.chat"),
    )

    // ---- 候选预筛：固定 ----

    @Test
    fun `pin standing when hw cell at least 3 and opened in last 7 days`() {
        val n = nominate(
            snap(stats("com.example.chat", hw = mapOf("weekday-21" to 5), total7 = 14)),
        )
        assertTrue(n.hasCandidates)
        assertTrue(n.kinds.contains(SuggestionKind.PIN))
        assertTrue(n.hints.any { it.startsWith("pin ") && it.contains("com.example.chat") })
    }

    @Test
    fun `pin needs recent opens`() {
        val n = nominate(
            snap(stats("com.example.chat", hw = mapOf("weekday-21" to 5), total7 = 0)),
        )
        assertFalse(n.hints.any { it.startsWith("pin") })
    }

    @Test
    fun `pin-new when cell newly crossed 3 versus baseline`() {
        val prev = snap(
            stats("com.example.chat", hw = mapOf("weekday-21" to 2), total7 = 3),
        )
        val cur = snap(
            stats("com.example.chat", hw = mapOf("weekday-21" to 5), total7 = 14),
        )
        val n = nominate(cur, prev = prev)
        assertTrue(n.hints.any { it.startsWith("pin-new") && it.contains("prev=2") })
    }

    @Test
    fun `pin not repeated for suppressed pkg`() {
        val n = nominate(
            snap(stats("com.example.chat", hw = mapOf("weekday-21" to 5), total7 = 14)),
            suppressedPin = setOf("com.example.chat"),
        )
        assertFalse(n.hints.any { it.startsWith("pin") })
    }

    // ---- 候选预筛：取消固定 ----

    @Test
    fun `unpin when pinned app unused for 14 days`() {
        val n = nominate(
            snap(stats("com.example.chat", pinned = true, lastUsedDays = 20)),
        )
        assertTrue(n.hints.any { it.startsWith("unpin") })
        assertTrue(n.kinds.contains(SuggestionKind.UNPIN))
    }

    @Test
    fun `unpin not for recently used pinned app`() {
        val n = nominate(
            snap(stats("com.example.chat", pinned = true, lastUsedDays = 5)),
        )
        assertFalse(n.hints.any { it.startsWith("unpin") })
    }

    @Test
    fun `muted kind never enters candidates`() {
        val n = nominate(
            snap(stats("com.example.chat", pinned = true, lastUsedDays = 20)),
            muted = setOf(SuggestionKind.UNPIN),
        )
        assertFalse(n.hasCandidates)
    }

    // ---- 候选预筛：模式观察 ----

    @Test
    fun `weekend split when weekday has cells and weekend is zero`() {
        val n = nominate(
            snap(stats("com.example.chat", hw = mapOf("weekday-21" to 4), total7 = 9)),
        )
        assertTrue(n.hints.any { it.startsWith("split") && it.contains("weekend=0") })
        assertTrue(n.kinds.contains(SuggestionKind.OBSERVE))
    }

    @Test
    fun `weekend split reversed when weekday is zero`() {
        val n = nominate(
            snap(stats("com.example.chat", hw = mapOf("saturday-10" to 4), total7 = 9)),
        )
        assertTrue(n.hints.any { it.startsWith("split") && it.contains("weekday=0") })
    }

    @Test
    fun `transfer newly dominant versus baseline`() {
        val prev = snap(
            stats("com.example.chat", total = 40,
                transfersIn = listOf(TransferIn("com.example.hub", 8, false))),
        )
        val cur = snap(
            stats("com.example.chat", total = 40,
                transfersIn = listOf(TransferIn("com.example.hub", 8, true))),
        )
        val n = nominate(cur, prev = prev)
        assertTrue(n.hints.any { it.startsWith("transfer-new") })
    }

    @Test
    fun `transfer rule skipped without baseline`() {
        val n = nominate(
            snap(stats("com.example.chat", total = 40,
                transfersIn = listOf(TransferIn("com.example.hub", 8, true)))),
        )
        assertFalse(n.hints.any { it.startsWith("transfer-new") })
    }

    @Test
    fun `hub when one source dominates three targets`() {
        val s = snap(
            stats("a", total = 10, transfersIn = listOf(TransferIn("hub", 5, true))),
            stats("b", total = 10, transfersIn = listOf(TransferIn("hub", 5, true))),
            stats("c", total = 10, transfersIn = listOf(TransferIn("hub", 5, true))),
            stats("hub", total = 30, transfersOutTotal = 15, transfersOutTargets = 3),
        )
        val n = nominate(s)
        assertTrue(n.hints.any { it.startsWith("hub from=hub") })
    }

    @Test
    fun `top10 entry and exit versus baseline`() {
        val prev = snap(top10 = listOf("com.example.old", "com.example.keep"))
        val cur = snap(
            stats("com.example.new"),
            stats("com.example.keep"),
            top10 = listOf("com.example.new", "com.example.keep"),
        )
        val n = nominate(cur, prevTop10 = prev.top10)
        assertTrue(n.hints.any { it == "top10-in pkg=com.example.new" })
        assertTrue(n.hints.any { it == "top10-out pkg=com.example.old" })
    }

    @Test
    fun `no candidates for empty snapshot`() {
        assertFalse(nominate(snap()).hasCandidates)
    }

    // ---- payload ----

    @Test
    fun `payload is deterministic and fingerprint ignores now`() {
        val s = chatSnap()
        val hints = listOf("pin pkg=com.example.chat hw=weekday-21:5 total7=14")
        assertEquals(buildPayloadJson(s, hints), buildPayloadJson(s, hints))
        assertEquals(payloadFingerprint(s, hints), payloadFingerprint(s, hints))
        assertNotEquals(
            payloadFingerprint(s, hints),
            payloadFingerprint(s, listOf("pin pkg=com.example.chat hw=weekday-21:6 total7=14")),
        )
        assertFalse(buildPayloadJson(s, hints, includeNow = false).contains("\"now\""))
    }

    @Test
    fun `payload round trips through snapshotFromPayloadJson`() {
        val s = chatSnap()
        val json = buildPayloadJson(s, listOf("pin pkg=com.example.chat hw=weekday-21:5 total7=14"))
        val back = snapshotFromPayloadJson(json)
        assertNotNull(back)
        assertEquals(listOf("com.example.chat"), back!!.top10)
        val app = back.app("com.example.chat")
        assertNotNull(app)
        assertEquals(5, app!!.hw["weekday-21"])
        assertEquals(14, app.total7)
        assertEquals(0, app.dockRank)
    }

    @Test
    fun `dayTypeName maps dow to day type`() {
        assertEquals("weekday", dayTypeName(1))
        assertEquals("weekday", dayTypeName(5))
        assertEquals("saturday", dayTypeName(6))
        assertEquals("sunday", dayTypeName(7))
    }

    // ---- 解析 ----

    @Test
    fun `parse valid suggestion json`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson()))
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
        assertEquals("pin", parsed[0].kind)
        assertEquals("com.example.chat", parsed[0].action.pkg)
        assertEquals(2, parsed[0].evidence.size)
        assertEquals("hwDayType", parsed[0].evidence[0].metric)
        assertEquals(21, parsed[0].evidence[0].hour)
        assertEquals(5, parsed[0].evidence[0].intValue)
    }

    @Test
    fun `parse strips code fence`() {
        val parsed = parseSuggestionsResponse(rawBody("```json\n${pinJson()}\n```"))
        assertNotNull(parsed)
        assertEquals(1, parsed!!.size)
    }

    @Test
    fun `parse failure returns null for whole round`() {
        assertNull(parseSuggestionsResponse(rawBody("not json at all")))
        assertNull(parseSuggestionsResponse("{\"noChoices\":true}"))
    }

    @Test
    fun `extractModelContent picks first choice`() {
        assertEquals("hello", extractModelContent(rawBody("hello")))
        assertNull(extractModelContent("garbage"))
    }

    // ---- 复核门 ----

    @Test
    fun `verify passes numbers matching snapshot`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson()))!!
        val verified = verifySuggestions(parsed, chatSnap(), emptySet(), emptySet())
        assertEquals(1, verified.size)
        assertEquals(SuggestionKind.PIN, verified[0].kind)
        assertEquals("com.example.chat", verified[0].action.pkg)
    }

    @Test
    fun `verify drops suggestion with wrong number`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson(hwValue = 6)))!!
        assertTrue(verifySuggestions(parsed, chatSnap(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `verify drops suggestion referencing app outside snapshot`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson(pkg = "com.example.other")))!!
        assertTrue(verifySuggestions(parsed, chatSnap(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `verify drops kind action mismatch`() {
        val parsed = parseSuggestionsResponse(
            rawBody(pinJson(kind = "pin", actionType = "unpin")),
        )!!
        assertTrue(verifySuggestions(parsed, chatSnap(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `verify drops pin for already pinned app`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson()))!!
        assertTrue(
            verifySuggestions(parsed, chatSnap(pinned = true), emptySet(), emptySet()).isEmpty(),
        )
    }

    @Test
    fun `verify drops unknown metric`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson(metric = "mystery")))!!
        assertTrue(verifySuggestions(parsed, chatSnap(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `verify drops muted kind`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson()))!!
        assertTrue(
            verifySuggestions(parsed, chatSnap(), setOf(SuggestionKind.PIN), emptySet()).isEmpty(),
        )
    }

    @Test
    fun `verify drops ignored dedupeKey`() {
        val parsed = parseSuggestionsResponse(rawBody(pinJson()))!!
        val key = dockSuggestionDedupeKey(
            SuggestionKind.PIN,
            SuggestionAction("pin", "com.example.chat"),
            parsed[0].evidence,
        )
        assertTrue(
            verifySuggestions(parsed, chatSnap(), emptySet(), setOf(key)).isEmpty(),
        )
    }

    @Test
    fun `verify drops lastUsedDays claim when no record`() {
        val json = """
            {"suggestions":[{"id":"u1","kind":"unpin","title":"t","body":"b",
            "evidence":[{"metric":"lastUsedDays","pkg":"com.example.chat","value":20}],
            "action":{"type":"unpin","pkg":"com.example.chat"}}]}
        """.trimIndent()
        val parsed = parseSuggestionsResponse(rawBody(json))!!
        // 快照里 lastUsedDays=2（且该应用未固定，unpin 本就不成立）；换成
        // 无记录快照：lastUsedDays=-1 时任何 claimed 值都对不上。
        val noRecord = snap(
            stats("com.example.chat", pinned = true, lastUsedDays = -1),
        )
        assertTrue(verifySuggestions(parsed, noRecord, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `verify caps at three suggestions`() {
        val items = (1..4).joinToString(",") { i ->
            """{"id":"p$i","kind":"observe","title":"t$i","body":"b$i",
            "evidence":[{"metric":"total7","pkg":"com.example.chat","value":14}],
            "action":{"type":"observe","pkg":"com.example.chat"}}"""
        }
        val parsed = parseSuggestionsResponse(rawBody("""{"suggestions":[$items]}"""))!!
        assertEquals(3, verifySuggestions(parsed, chatSnap(), emptySet(), emptySet()).size)
    }

    @Test
    fun `dedupeKey is order insensitive and value sensitive`() {
        val ev1 = listOf(
            SuggestionEvidence("hwDayType", "com.example.chat", dayType = "weekday", hour = 21, intValue = 5),
            SuggestionEvidence("total7", "com.example.chat", intValue = 14),
        )
        val ev2 = listOf(ev1[1], ev1[0])
        val ev3 = listOf(
            SuggestionEvidence("hwDayType", "com.example.chat", dayType = "weekday", hour = 21, intValue = 6),
            SuggestionEvidence("total7", "com.example.chat", intValue = 14),
        )
        val action = SuggestionAction("pin", "com.example.chat")
        assertEquals(
            dockSuggestionDedupeKey(SuggestionKind.PIN, action, ev1),
            dockSuggestionDedupeKey(SuggestionKind.PIN, action, ev2),
        )
        assertNotEquals(
            dockSuggestionDedupeKey(SuggestionKind.PIN, action, ev1),
            dockSuggestionDedupeKey(SuggestionKind.PIN, action, ev3),
        )
    }
}
