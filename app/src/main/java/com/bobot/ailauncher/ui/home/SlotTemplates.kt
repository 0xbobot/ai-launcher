package com.bobot.ailauncher.ui.home

import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.Capability
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 示例模板（v0.68.101 主页重构 R-2 建，F 批收敛）：甲板「动态示例」的
 * 派生来源。模板内部仍是「固定文本段 + 槽位」的结构，但 F 批起槽位只
 * 参与示例文案的派生（[SlotTemplate.exampleFillText]）——R-2 的槽位
 * 填充 UX（芯片条/选择面板/发送拦截）已退役：点示例 = 把派生出的整句
 * 纯文本填入输入框，用户可继续编辑，发送走自由文本路径，缺参由协调器
 * NeedsInput 追问兜底。
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

/** 槽位种类：标记槽位语义（目的地/时间），F 批后仅作模板结构标注。 */
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
 * 带槽位的示例模板。[defaults] 是派生示例文案时槽位的预置值（来自上下文，
 * 如下一场会的地点）。
 */
data class SlotTemplate(
    val id: String,
    val capabilityId: String?,
    val parts: List<TemplatePart>,
    val defaults: Map<String, String> = emptyMap()
) {
    fun slots(): List<SlotDef> = parts.filterIsInstance<TemplatePart.Slot>().map { it.def }

    /** 示例行上的文案：槽位用默认值呈现（无默认用槽位名占位）。 */
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

    /**
     * F 批·示例填充文案派生（纯函数，单测锁死）：点示例时写入输入框的
     * 整句纯文本。口径与 [chipText] 完全一致（同一派生，两个名字分别
     * 服务「行上显示」与「点后填入」两个语义位）——有默认值代入默认值，
     * 无默认值的槽位以槽位 label 原文呈现（如「导航去目的地」），用户
     * 填入后可直接改字，不存在任何槽位态。
     */
    fun exampleFillText(): String = chipText()
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
     * （v6 起 IntentEntry 不再用它，改用 [buildExpandedExamples]；本函数
     * 保留供单测与旧口径对照，不许删。）
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

    /**
     * 固定文案示例（v6 能力发散）：整句即模板，无槽位；capabilityId 必须
     * 指向 capabilities.json 里真实存在的能力，不编不存在的能力。文案里
     * 的「明天/上海」与 DEFAULT_DESTINATION 的「机场」同属模板自带示例
     * 值，非用户数据。
     */
    fun fixedTemplate(id: String, capabilityId: String, sentence: String): SlotTemplate =
        SlotTemplate(
            id = id,
            capabilityId = capabilityId,
            parts = listOf(TemplatePart.Text(sentence))
        )

    /**
     * v6 扩展示例池（Bob 2026-10-24 18:45：推荐问要结合能力发散一点，
     * 不要只换文案不换覆盖面）：出行（打车/导航/航班/地铁/酒店）+
     * 办公（日程）+ 生活（外卖）+ 通讯（微信）+ 支付（支付宝）——每条
     * 都对应注册表里的真实能力；会议提醒仍是上下文派生（有地点且未
     * 开始时插在第 2 位）。IntentEntry 用整池：胶囊上方主行横滚全量
     * 呈现（v6 后调整：猜你想问行已删，切分口径退役）。
     * 注意缺口（v6 回执待 Bob 定夺）：注册表里没有系统操作类能力
     * （闹钟/手电/亮度等），故本池没有系统操作代表——没编不存在的能力。
     */
    fun buildExpandedExamples(
        daohang: Capability?,
        dache: Capability?,
        nextEvent: CalEvent?,
        nowMs: Long
    ): List<SlotTemplate> {
        val out = mutableListOf<SlotTemplate>()
        out += taxiTemplate(dache)
        if (nextEvent != null) meetingReminder(nextEvent, nowMs)?.let { out += it }
        out += navTemplate(daohang)
        out += fixedTemplate("hangban", "hangban", "查明天飞上海的航班")
        out += fixedTemplate("rili", "rili", "查我明天的日程")
        out += fixedTemplate("meituan", "meituan", "帮我点一份午饭外卖")
        out += fixedTemplate("ditie", "ditie", "查去机场的地铁")
        out += fixedTemplate("wechat", "wechat", "打开微信")
        out += fixedTemplate("zhifubao", "zhifubao", "打开支付宝")
        out += fixedTemplate("jiudian", "jiudian", "帮我订一间上海的酒店")
        return out
    }

    /** HH:mm（与封面条/提醒文案同一格式口径）。 */
    fun fmtHHmm(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
