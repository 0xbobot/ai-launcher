package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.Capability
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 槽位模板（v0.68.101·主页重构 R-2 输入甲板）：甲板上的「动态示例」不再是
 * 点了就提交的整句，而是带槽位的模板——点示例只把模板填进胶囊，槽位以
 * 内嵌芯片呈现，用户在甲板上区的快捷选择面板里逐个确认/改值，必填槽位
 * 没填齐不许发送（事前防缺参；提交后解析仍缺参时 .87 的 NeedsInput 追问
 * 是兜底，两者不冲突：槽位管「模板句」，追问管「自由句与解析结果」）。
 *
 * 槽位定义来源（不许编造）：
 * - 导航/打车模板的「目的地」槽位直接取 capabilities.json 里对应能力的
 *   参数 schema（.87 已有：daohang.to；dache.to 随本批补进 schema）——
 *   label / required 都读 schema，schema 缺失时才用同值兜底定义。
 * - 会议提醒模板是上下文派生（下一场会），其时间/目的地槽位为 UI 定义
 *   （背后没有能力 schema），默认值来自真实日历事件（开始时间/地点）。
 *
 * 本文件全部为纯逻辑（无 Android 依赖，时间以毫秒参数传入），单测锁死。
 */

/** 槽位种类：决定快捷选择面板的选项来源。 */
enum class SlotKind { DESTINATION, TIME }

/** 一个槽位的形式定义（param 对应能力参数名，UI 模板可用自有 param）。 */
data class SlotDef(
    val param: String,
    val label: String,
    val kind: SlotKind,
    val required: Boolean
)

/** 模板片段：固定文本或槽位。 */
sealed interface TemplatePart {
    data class Text(val text: String) : TemplatePart
    data class Slot(val def: SlotDef) : TemplatePart
}

/**
 * 带槽位的示例模板。[defaults] 是填入时预置的槽位值（来自上下文，如
 * 下一场会的地点）——预置也算已填（实心芯片），用户点芯片可改。
 */
data class SlotTemplate(
    val id: String,
    val capabilityId: String?,
    val parts: List<TemplatePart>,
    val defaults: Map<String, String> = emptyMap()
) {
    fun slots(): List<SlotDef> = parts.filterIsInstance<TemplatePart.Slot>().map { it.def }

    /** 初始填充：默认值落位（空白默认值不算已填）。 */
    fun initialFill(): TemplateFill =
        TemplateFill(this, defaults.filterValues { it.isNotBlank() })

    /** 示例 chips 上的文案：槽位用默认值呈现（无默认用槽位名占位）。 */
    fun chipText(): String = buildString {
        for (p in parts) {
            when (p) {
                is TemplatePart.Text -> append(p.text)
                is TemplatePart.Slot -> append(
                    defaults[p.def.param]?.takeIf { it.isNotBlank() } ?: p.def.label
                )
            }
        }
    }
}

/** 模板的一次填充状态（不可变，改值返回新实例）。 */
data class TemplateFill(
    val template: SlotTemplate,
    val values: Map<String, String>
) {
    fun valueOf(param: String): String? = values[param]?.takeIf { it.isNotBlank() }

    fun isFilled(def: SlotDef): Boolean = valueOf(def.param) != null

    /** 第一个未填的必填槽位（发送拦截的落点）；全填齐返回 null。 */
    fun firstEmptyRequired(): SlotDef? =
        template.slots().firstOrNull { it.required && !isFilled(it) }

    fun withValue(param: String, value: String?): TemplateFill =
        copy(values = if (value.isNullOrBlank()) values - param else values + (param to value))

    /** 拼成最终提交文本：槽位以已填值代入，未填槽位贡献空串（发送前必已拦截）。 */
    fun composedText(): String = buildString {
        for (p in template.parts) {
            when (p) {
                is TemplatePart.Text -> append(p.text)
                is TemplatePart.Slot -> append(values[p.def.param].orEmpty())
            }
        }
    }.trim()
}

object SlotTemplates {

    /** 无上下文时的示例默认目的地（模板自带示例值，非用户数据）。 */
    const val DEFAULT_DESTINATION = "机场"

    /** 提醒出发的提前量：会议开始前 30 分钟。 */
    const val DEPART_LEAD_MS = 30 * 60_000L

    private fun schemaSlot(cap: Capability?, fallbackLabel: String): SlotDef {
        val p = cap?.params?.firstOrNull { it.name == "to" }
        return SlotDef(
            param = "to",
            label = p?.label?.takeIf { it.isNotBlank() } ?: fallbackLabel,
            kind = SlotKind.DESTINATION,
            required = p?.required ?: true
        )
    }

    /** 导航模板「导航去{目的地}」：槽位取 daohang 的参数 schema。 */
    fun navTemplate(daohang: Capability?): SlotTemplate = SlotTemplate(
        id = "nav",
        capabilityId = "daohang",
        parts = listOf(
            TemplatePart.Text("导航去"),
            TemplatePart.Slot(schemaSlot(daohang, "目的地"))
        ),
        defaults = mapOf("to" to DEFAULT_DESTINATION)
    )

    /** 打车模板「打车去{目的地}」：槽位取 dache 的参数 schema。 */
    fun taxiTemplate(dache: Capability?): SlotTemplate = SlotTemplate(
        id = "taxi",
        capabilityId = "dache",
        parts = listOf(
            TemplatePart.Text("打车去"),
            TemplatePart.Slot(schemaSlot(dache, "目的地"))
        ),
        defaults = mapOf("to" to DEFAULT_DESTINATION)
    )

    /**
     * 会议提醒模板「提醒我{时间}出发去{目的地}」（上下文派生）：
     * 仅当事件有真实地点且尚未开始时成立；时间默认 = 开始前 30 分钟
     * （已过则用开始时间本身），目的地默认 = 会议地点。
     */
    fun meetingReminder(event: CalEvent, nowMs: Long): SlotTemplate? {
        val location = event.location.trim()
        if (location.isEmpty() || event.begin <= nowMs) return null
        val departMs = (event.begin - DEPART_LEAD_MS).takeIf { it > nowMs } ?: event.begin
        return SlotTemplate(
            id = "meeting-reminder",
            capabilityId = null,
            parts = listOf(
                TemplatePart.Text("提醒我"),
                TemplatePart.Slot(SlotDef("time", "时间", SlotKind.TIME, required = true)),
                TemplatePart.Text(" 出发去"),
                TemplatePart.Slot(SlotDef("dest", "目的地", SlotKind.DESTINATION, required = true))
            ),
            defaults = mapOf(
                "time" to fmtHHmm(departMs),
                "dest" to location
            )
        )
    }

    /**
     * 甲板示例集（顺序同原型 chips：打车 / 会議提醒 / 导航），上限 3 条。
     * 会议提醒只在下一场会有地点且未开始时出现，其余时候回落静态模板集。
     */
    fun buildExamples(
        daohang: Capability?,
        dache: Capability?,
        nextEvent: CalEvent?,
        nowMs: Long
    ): List<SlotTemplate> {
        val out = mutableListOf<SlotTemplate>()
        out += taxiTemplate(dache)
        if (nextEvent != null) meetingReminder(nextEvent, nowMs)?.let { out += it }
        out += navTemplate(daohang)
        return out.take(3)
    }

    /** HH:mm（与封面条/提醒文案同一格式口径）。 */
    fun fmtHHmm(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
