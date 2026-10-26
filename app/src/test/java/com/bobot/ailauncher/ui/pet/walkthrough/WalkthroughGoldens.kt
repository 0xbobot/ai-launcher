package com.bobot.ailauncher.ui.pet.walkthrough

import org.json.JSONObject
import org.junit.Assume
import java.io.File

/**
 * 走查黄金基线助手（P0，非阻塞报告语义）。
 *
 * 机制：
 * - 基线文件 src/test/resources/pet-walkthrough-goldens.json（classpath 资源，
 *   首次合入时只含手算数值项；图像基线另存 app/src/test/pet-walkthrough-goldens/，
 *   由报告脚本做像素 diff，不走本助手）。
 * - 无基线 → 记为 new，测试不失败（首轮即确认清单的来源）。
 * - 有基线且漂移超 tol → 记为 fail；仅当环境变量 PETWALKTHROUGH_ENFORCE=true
 *  （走查工作流传）时才抛 AssertionError 使测试失败。主 CI 不传该变量。
 * - 子集过滤：环境变量 PETWALKTHROUGH_SUBSET（逗号分隔的 id 前缀），
 *   不匹配的项以 JUnit assumption 跳过（XML 里记为 skipped）。
 * - 每项结果写入 build/pet-walkthrough/report-<tag>.json（可由
 *   PETWALKTHROUGH_REPORT_DIR 改向），供报告脚本合并。
 * - attention：数值本身越界（如净空为负＝气泡探出屏顶）时附加 attention
 *   标记，报告里单独列为“注意”栏，不计入 fail（P0 只量测、不判好坏）。
 */
object WalkthroughGoldens {

    private val enforce: Boolean =
        System.getenv("PETWALKTHROUGH_ENFORCE") == "true"

    private val subsetPrefixes: Set<String> =
        System.getenv("PETWALKTHROUGH_SUBSET")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private val reportDir: File =
        File(System.getenv("PETWALKTHROUGH_REPORT_DIR") ?: "build/pet-walkthrough")

    private val goldens: JSONObject? = run {
        val text = WalkthroughGoldens::class.java
            .getResource("/pet-walkthrough-goldens.json")
            ?.readText()
        text?.let { JSONObject(it).optJSONObject("items") }
    }

    /** 子集过滤：不在子集内的项返回 false，调用方应直接 return（配合 assume）。 */
    fun included(id: String): Boolean =
        subsetPrefixes.isEmpty() || subsetPrefixes.any { id.startsWith(it) }

    /** 要求当前项在子集内，否则以 assumption 跳过（不算失败）。 */
    fun requireIncluded(id: String) {
        Assume.assumeTrue("walkthrough item $id not in subset", included(id))
    }

    fun check(
        tag: String,
        id: String,
        actual: Double,
        tol: Double,
        unit: String,
        attention: Boolean = false,
    ) {
        requireIncluded(id)
        val goldenObj = goldens?.optJSONObject(id)
        val golden = goldenObj?.optDouble("value", Double.NaN)
            ?.takeIf { !it.isNaN() }
        val status = when {
            golden == null -> "new"
            kotlin.math.abs(actual - golden) <= tol -> "pass"
            else -> "fail"
        }
        record(tag, id, actual, golden, tol, unit, status, attention)
        if (status == "fail" && enforce) {
            throw AssertionError(
                "walkthrough item $id drifted: actual=$actual golden=$golden tol=$tol $unit",
            )
        }
    }

    /** 无数值比较的记录项（如清单类、P1 待量测项），只进报告不判分。 */
    fun note(tag: String, id: String, detail: String) {
        if (!included(id)) return
        record(tag, id, null, null, 0.0, "", "note", false, detail)
    }

    @Synchronized
    private fun record(
        tag: String,
        id: String,
        actual: Double?,
        golden: Double?,
        tol: Double,
        unit: String,
        status: String,
        attention: Boolean,
        detail: String = "",
    ) {
        reportDir.mkdirs()
        val file = File(reportDir, "report-$tag.json")
        val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val items = root.optJSONArray("items") ?: org.json.JSONArray()
        // 同一 tag 内同 id 去重：后写覆盖先写。
        val kept = org.json.JSONArray()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.optString("id") != id) kept.put(o)
        }
        kept.put(
            JSONObject()
                .put("id", id)
                .put("actual", actual ?: JSONObject.NULL)
                .put("golden", golden ?: JSONObject.NULL)
                .put("tol", tol)
                .put("unit", unit)
                .put("status", status)
                .put("attention", attention)
                .put("detail", detail),
        )
        root.put("items", kept)
        file.writeText(root.toString(2))
    }
}
