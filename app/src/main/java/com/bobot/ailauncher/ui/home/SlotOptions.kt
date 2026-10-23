package com.bobot.ailauncher.ui.home

/**
 * 槽位快捷选择面板的选项生成（v0.68.101·R-2）：纯逻辑，单测锁死。
 *
 * 选项来源（全部真实存在，禁止编造）：
 * - 目的地：① 当前已填值（面板里要能看见自己选的是哪个）② 下个会议的
 *   地点（日历事件 location）③ 历史输入（用户在本机面板里确认过的目的
 *   地，见 data/SlotHistoryStore）④ 模板自带示例默认（如「机场」）。
 *   没有家庭/公司地址簿这类数据源——原型图里的「家/公司」不落地，
 *   不假装有。
 * - 时间：会议派生的绝对时刻（出发时刻 HH:mm）+ 相对时间 chips
 *   （15 分钟后 / 半小时后 / 1 小时后 / 明早 9 点）。填入值即 chip 文案
 *   本身（自然语言，规划器按原文理解）。
 */
object SlotOptions {

    /**
     * 目的地选项：当前值优先，其后依次会议地点、历史（新→旧）、示例
     * 默认；去空白、去重（先到先得），上限 [max]。
     */
    fun destinationOptions(
        current: String?,
        meetingLocation: String?,
        history: List<String>,
        templateDefault: String?,
        max: Int = 6
    ): List<String> {
        val out = mutableListOf<String>()
        fun push(v: String?) {
            val t = v?.trim().orEmpty()
            if (t.isNotEmpty() && t !in out) out += t
        }
        push(current)
        push(meetingLocation)
        history.forEach { push(it) }
        push(templateDefault)
        return out.take(max)
    }

    /**
     * 时间选项：会议出发时刻（若有）打头，其后固定相对时间集。
     * [meetingDepartMs] 为会议派生的出发时刻（毫秒），null 时不出现。
     */
    fun timeOptions(meetingDepartMs: Long?): List<String> {
        val out = mutableListOf<String>()
        if (meetingDepartMs != null) out += SlotTemplates.fmtHHmm(meetingDepartMs)
        out += listOf("15 分钟后", "半小时后", "1 小时后", "明早 9 点")
        return out.distinct()
    }
}
