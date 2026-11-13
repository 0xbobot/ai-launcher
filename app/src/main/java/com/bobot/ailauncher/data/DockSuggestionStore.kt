package com.bobot.ailauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 建议状态存储（SharedPreferences "dock_suggestions"，键见下）。
 *
 * last_result：最近一次通过复核的建议 JSON（含状态与采纳时间）。
 * last_payload：最近一次分析的规范 payload（基线：前 10 进出 / 转移
 * 新占主导等规则需要它；指纹也从它算）。
 * last_fingerprint：last_result 对应的候选指纹（候选未变则直接显示
 * 上次结果，不重新调模型）。
 * suppressed_keys：已忽略建议的 dedupeKey + "pin:<pkg>" /
 * "unpin:<pkg>"（采纳或忽略后该包不再被同一动作提名）。
 * muted_kinds：被「不再提示」关掉的种类（pin/unpin/observe），关了
 * 的种类不进候选。
 * applied：已采纳记录（dedupeKey → {"kind","pkg","appliedAtMs"}，
 * 单独存——新一轮分析会覆盖 last_result，但 7 天撤销窗口要活过
 * 新分析）。
 *
 * 建议的 feedback（「这个建议不对」）记本地 JSONL（只记包名、数字、
 * verdict，不记内容），仿照 UnreadJumpLog：环形上限 200 条。
 */
class DockSuggestionStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _uiState = MutableStateFlow(SuggestionsUiState())
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    val lastPayload: String? get() = prefs.getString(KEY_LAST_PAYLOAD, null)

    // ---------- 纯内存查询（供 analyze 用） ----------

    fun mutedKinds(): Set<SuggestionKind> {
        return prefs.getStringSet(KEY_MUTED_KINDS, emptySet()).orEmpty().mapNotNull {
            when (it) {
                "pin" -> SuggestionKind.PIN
                "unpin" -> SuggestionKind.UNPIN
                "observe" -> SuggestionKind.OBSERVE
                else -> null
            }
        }.toSet()
    }

    /** 已忽略的 dedupeKey 集合（复核门用）。 */
    fun ignoredKeys(): Set<String> {
        return prefs.getStringSet(KEY_SUPPRESSED_KEYS, emptySet()).orEmpty()
            .filter { !it.startsWith("pin:") && !it.startsWith("unpin:") }
            .toSet()
    }

    fun suppressedPinPkgs(): Set<String> {
        return prefs.getStringSet(KEY_SUPPRESSED_KEYS, emptySet()).orEmpty()
            .filter { it.startsWith("pin:") }.map { it.removePrefix("pin:") }.toSet()
    }

    fun suppressedUnpinPkgs(): Set<String> {
        return prefs.getStringSet(KEY_SUPPRESSED_KEYS, emptySet()).orEmpty()
            .filter { it.startsWith("unpin:") }.map { it.removePrefix("unpin:") }.toSet()
    }

    /** 候选指纹与上次相同且有可展示的上次结果 → 返回它（不重新调模型）。 */
    fun cachedResult(fingerprint: String): List<DockSuggestion>? {
        if (prefs.getString(KEY_LAST_FINGERPRINT, null) != fingerprint) return null
        val list = loadLastResult() ?: return null
        val visible = list.filter { it.state == SuggestionState.NEW }
        return visible.ifEmpty { null }
    }

    // ---------- 持久化 ----------

    /** 没候选时只推进基线（不覆盖 last_result / 指纹）。 */
    fun saveBaselineOnly(payloadJson: String) {
        prefs.edit().putString(KEY_LAST_PAYLOAD, payloadJson).apply()
    }

    fun saveResult(suggestions: List<DockSuggestion>, payloadJson: String, fingerprint: String) {
        prefs.edit()
            .putString(KEY_LAST_RESULT, suggestionsJson(suggestions))
            .putString(KEY_LAST_PAYLOAD, payloadJson)
            .putString(KEY_LAST_FINGERPRINT, fingerprint)
            .apply()
        refreshUiState()
    }

    /** 从磁盘重建 UI 状态（说明页打开时调一次）。 */
    fun refreshUiState() {
        val list = loadLastResult() ?: emptyList()
        val applied = loadApplied()
        val now = System.currentTimeMillis()
        // 过滤：过期撤销记录（>7 天）不显示；固定状态与事实不一致的
        // APPLIED（例如用户在别处手动改了固定位）也不显示。
        val pinned = try {
            UiPrefs.getDockPinned(context)
        } catch (_: Exception) {
            emptyList()
        }
        val visible = list.mapNotNull { s ->
            when (s.state) {
                SuggestionState.NEW -> s
                SuggestionState.APPLIED -> {
                    val rec = applied[s.dedupeKey]
                    val fresh = rec != null && now - rec.appliedAtMs < SUGGESTION_UNDO_WINDOW_MS
                    val consistent = when (s.kind) {
                        SuggestionKind.PIN -> s.action.pkg in pinned
                        SuggestionKind.UNPIN -> s.action.pkg !in pinned
                        SuggestionKind.OBSERVE -> true
                    }
                    if (fresh && consistent) s else null
                }
                SuggestionState.DISMISSED -> null
            }
        }
        _uiState.value = SuggestionsUiState(
            suggestions = visible,
            mutedKinds = mutedKinds(),
        )
    }

    /** 忽略：状态 → DISMISSED + 去重键进 suppressed（不再提名）。 */
    fun markDismissed(dedupeKey: String) {
        val list = loadLastResult() ?: return
        val updated = list.map {
            if (it.dedupeKey == dedupeKey) it.copy(state = SuggestionState.DISMISSED) else it
        }
        prefs.edit()
            .putString(KEY_LAST_RESULT, suggestionsJson(updated))
            .putStringSet(KEY_SUPPRESSED_KEYS, (ignoredKeys() + suppressedPinPkgs().map { "pin:$it" } + suppressedUnpinPkgs().map { "unpin:$it" } + dedupeKey).toSet())
            .apply()
        refreshUiState()
    }

    /**
     * 采纳：pin → 固定位加包；unpin → 固定位去包；observe → 只记状态
     * （不改任何东西）。采纳记录单独存 applied，7 天撤销窗口活过新分析。
     * setDockPinned 内部 bumpDock()，Dock 立即重排，说明页理由随之变
     * 成「你固定在 Dock 上」。
     */
    fun markApplied(suggestion: DockSuggestion) {
        val now = System.currentTimeMillis()
        when (suggestion.kind) {
            SuggestionKind.PIN -> {
                val cur = UiPrefs.getDockPinned(context)
                if (suggestion.action.pkg !in cur) {
                    UiPrefs.setDockPinned(context, cur + suggestion.action.pkg)
                }
            }
            SuggestionKind.UNPIN -> {
                val cur = UiPrefs.getDockPinned(context)
                if (suggestion.action.pkg in cur) {
                    UiPrefs.setDockPinned(context, cur - suggestion.action.pkg)
                }
            }
            SuggestionKind.OBSERVE -> Unit
        }
        val list = loadLastResult() ?: emptyList()
        val updated = list.map {
            if (it.dedupeKey == suggestion.dedupeKey) {
                it.copy(state = SuggestionState.APPLIED, appliedAtMs = now)
            } else {
                it
            }
        }
        val applied = loadApplied().toMutableMap()
        applied[suggestion.dedupeKey] = AppliedRecord(
            kind = suggestion.kind.name,
            pkg = suggestion.action.pkg,
            appliedAtMs = now,
        )
        prefs.edit()
            .putString(KEY_LAST_RESULT, suggestionsJson(updated))
            .putString(KEY_APPLIED, appliedJson(applied))
            .putStringSet(KEY_SUPPRESSED_KEYS, currentSuppressedKeys() + suppressionFor(suggestion))
            .apply()
        refreshUiState()
    }

    /** 撤销（7 天内）：固定位反向改回，状态回 NEW（可再次采纳/忽略）。 */
    fun undoApplied(suggestion: DockSuggestion) {
        when (suggestion.kind) {
            SuggestionKind.PIN -> {
                val cur = UiPrefs.getDockPinned(context)
                if (suggestion.action.pkg in cur) {
                    UiPrefs.setDockPinned(context, cur - suggestion.action.pkg)
                }
            }
            SuggestionKind.UNPIN -> {
                val cur = UiPrefs.getDockPinned(context)
                if (suggestion.action.pkg !in cur) {
                    UiPrefs.setDockPinned(context, cur + suggestion.action.pkg)
                }
            }
            SuggestionKind.OBSERVE -> Unit
        }
        val list = loadLastResult() ?: emptyList()
        val updated = list.map {
            if (it.dedupeKey == suggestion.dedupeKey) {
                it.copy(state = SuggestionState.NEW, appliedAtMs = 0L)
            } else {
                it
            }
        }
        val applied = loadApplied().toMutableMap()
        applied.remove(suggestion.dedupeKey)
        prefs.edit()
            .putString(KEY_LAST_RESULT, suggestionsJson(updated))
            .putString(KEY_APPLIED, appliedJson(applied))
            .putStringSet(KEY_SUPPRESSED_KEYS, currentSuppressedKeys() - suppressionFor(suggestion))
            .apply()
        refreshUiState()
    }

    /** 「不再提示」：关一类（关了的种类不进候选）；再点一次恢复。 */
    fun setKindMuted(kind: SuggestionKind, muted: Boolean) {
        val cur = mutedKinds().toMutableSet()
        if (muted) cur += kind else cur -= kind
        prefs.edit()
            .putStringSet(
                KEY_MUTED_KINDS,
                cur.map { it.name.lowercase() }.toSet(),
            )
            .apply()
        refreshUiState()
    }

    /**
     * 「这个建议不对」：建议 + 证据快照 + verdict 记本地 JSONL（只记
     * 包名、数字、verdict，不记内容）。环形上限 200 条；写失败静默。
     * 写入后该建议也按忽略处理（不再提名）。
     */
    fun logWrong(suggestion: DockSuggestion) {
        scope.launch {
            runCatching {
                val obj = JSONObject().apply {
                    put("ts", System.currentTimeMillis())
                    put("id", suggestion.dedupeKey)
                    put("kind", suggestion.kind.name.lowercase())
                    put("pkg", suggestion.action.pkg)
                    put("verdict", "wrong")
                    put("evidence", JSONArray().apply {
                        suggestion.evidence.forEach { e ->
                            put(JSONObject().apply {
                                put("metric", e.metric)
                                put("pkg", e.pkg)
                                e.fromPkg?.let { put("from", it) }
                                e.dayType?.let { put("dayType", it) }
                                e.hour?.let { put("hour", it) }
                                e.intValue?.let { put("value", it) }
                                e.boolValue?.let { put("value", it) }
                            })
                        }
                    })
                }
                val file = File(context.filesDir, SUGGESTION_FEEDBACK_LOG)
                synchronized(FeedbackLock) {
                    val lines = if (file.exists()) file.readLines() else emptyList()
                    val kept = (lines + obj.toString()).takeLast(200)
                    file.writeText(kept.joinToString("\n") + "\n")
                }
                Log.i("DockSuggestions", "feedback wrong id=${suggestion.dedupeKey}")
            }
        }
        markDismissed(suggestion.dedupeKey)
    }

    // ---------- 内部 ----------

    private data class AppliedRecord(val kind: String, val pkg: String, val appliedAtMs: Long)

    private fun currentSuppressedKeys(): Set<String> {
        return prefs.getStringSet(KEY_SUPPRESSED_KEYS, emptySet()).orEmpty().toSet()
    }

    private fun suppressionFor(s: DockSuggestion): String = when (s.kind) {
        SuggestionKind.PIN -> "pin:${s.action.pkg}"
        SuggestionKind.UNPIN -> "unpin:${s.action.pkg}"
        SuggestionKind.OBSERVE -> s.dedupeKey
    }

    private fun loadLastResult(): List<DockSuggestion>? {
        val raw = prefs.getString(KEY_LAST_RESULT, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val kind = when (o.optString("kind")) {
                    "pin" -> SuggestionKind.PIN
                    "unpin" -> SuggestionKind.UNPIN
                    "observe" -> SuggestionKind.OBSERVE
                    else -> return@mapNotNull null
                }
                val state = when (o.optString("state")) {
                    "applied" -> SuggestionState.APPLIED
                    "dismissed" -> SuggestionState.DISMISSED
                    else -> SuggestionState.NEW
                }
                val evidence = mutableListOf<SuggestionEvidence>()
                val evArr = o.optJSONArray("evidence")
                if (evArr != null) {
                    for (j in 0 until evArr.length()) {
                        val e = evArr.optJSONObject(j) ?: continue
                        evidence += SuggestionEvidence(
                            metric = e.optString("metric"),
                            pkg = e.optString("pkg"),
                            fromPkg = e.optString("from", "").ifBlank { null },
                            dayType = e.optString("dayType", "").ifBlank { null },
                            hour = if (e.has("hour")) e.optInt("hour") else null,
                            intValue = if (e.has("value") && e.opt("value") is Number) e.optInt("value") else null,
                            boolValue = if (e.has("value") && e.opt("value") is Boolean) e.optBoolean("value") else null,
                        )
                    }
                }
                DockSuggestion(
                    id = o.optString("id"),
                    kind = kind,
                    title = o.optString("title"),
                    body = o.optString("body"),
                    evidence = evidence,
                    action = SuggestionAction(
                        type = o.optJSONObject("action")?.optString("type", "") ?: "",
                        pkg = o.optJSONObject("action")?.optString("pkg", "") ?: "",
                    ),
                    dedupeKey = o.optString("dedupeKey"),
                    state = state,
                    appliedAtMs = o.optLong("appliedAtMs"),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadApplied(): Map<String, AppliedRecord> {
        val raw = prefs.getString(KEY_APPLIED, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, AppliedRecord>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val r = obj.optJSONObject(k) ?: continue
                out[k] = AppliedRecord(
                    kind = r.optString("kind"),
                    pkg = r.optString("pkg"),
                    appliedAtMs = r.optLong("appliedAtMs"),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun suggestionsJson(list: List<DockSuggestion>): String {
        return JSONArray().apply {
            list.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("kind", s.kind.name.lowercase())
                    put("title", s.title)
                    put("body", s.body)
                    put("evidence", JSONArray().apply {
                        s.evidence.forEach { e ->
                            put(JSONObject().apply {
                                put("metric", e.metric)
                                put("pkg", e.pkg)
                                e.fromPkg?.let { put("from", it) }
                                e.dayType?.let { put("dayType", it) }
                                e.hour?.let { put("hour", it) }
                                e.intValue?.let { put("value", it) }
                                e.boolValue?.let { put("value", it) }
                            })
                        }
                    })
                    put("action", JSONObject().apply {
                        put("type", s.action.type)
                        put("pkg", s.action.pkg)
                    })
                    put("dedupeKey", s.dedupeKey)
                    put("state", s.state.name.lowercase())
                    put("appliedAtMs", s.appliedAtMs)
                })
            }
        }.toString()
    }

    private fun appliedJson(map: Map<String, AppliedRecord>): String {
        return JSONObject().apply {
            map.forEach { (k, r) ->
                put(k, JSONObject().apply {
                    put("kind", r.kind)
                    put("pkg", r.pkg)
                    put("appliedAtMs", r.appliedAtMs)
                })
            }
        }.toString()
    }

    companion object {
        private const val PREFS = "dock_suggestions"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_LAST_PAYLOAD = "last_payload"
        private const val KEY_LAST_FINGERPRINT = "last_fingerprint"
        private const val KEY_SUPPRESSED_KEYS = "suppressed_keys"
        private const val KEY_MUTED_KINDS = "muted_kinds"
        private const val KEY_APPLIED = "applied"
    }
}

private object FeedbackLock

/** 建议区 UI 状态（说明页订阅）。 */
data class SuggestionsUiState(
    val suggestions: List<DockSuggestion> = emptyList(),
    val mutedKinds: Set<SuggestionKind> = emptySet(),
)
