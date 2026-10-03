package com.bobot.ailauncher.core.pet

import com.bobot.ailauncher.data.PetMood
import java.util.Calendar

/**
 * 宠物语义状态（PRD §十二）。
 *
 * 状态不是随机动画，由时间/用户互动/使用频率/Context/历史行为共同决定。
 * 渲染层目前只认 PetMood（5 种），语义状态经 [toMood] 映射；更细的表情
 * （如 sleepy 闭眼）在 PetView 里按需加参数。
 */
enum class PetState {
    HAPPY, CURIOUS, SLEEPY, BORED, EXCITED, FOCUSED,
    CONCERNED, WAITING, TALKING, LISTENING, WORKING, AWAY
}

/** 语义状态 → 渲染 mood（现有 PetView 只支持 5 种） */
fun PetState.toMood(): PetMood = when (this) {
    PetState.HAPPY, PetState.EXCITED -> PetMood.HAPPY
    PetState.WORKING, PetState.FOCUSED -> PetMood.SORTING
    else -> PetMood.IDLE
}

/** 状态推导输入 */
data class PetContext(
    /** 正在处理通知/归档（忙） */
    val busy: Boolean = false,
    /** 正在聆听用户说话 */
    val listening: Boolean = false,
    /** 有未处理的重要事项 */
    val hasImportant: Boolean = false,
    /** 距上次互动分钟数 */
    val minutesIdle: Long = 999,
    /** 当前小时（0–23） */
    val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
)

/**
 * 状态机：规则推导（PRD §十二）。
 * 例子：深夜 → SLEEPY；忙 → WORKING；有重要未处理 → CONCERNED；久无互动 → BORED。
 */
object PetStateMachine {
    fun derive(ctx: PetContext): PetState = when {
        ctx.listening -> PetState.LISTENING
        ctx.busy -> PetState.WORKING
        ctx.hour >= 23 || ctx.hour <= 6 -> PetState.SLEEPY
        ctx.hasImportant -> PetState.CONCERNED
        ctx.minutesIdle > 180 -> PetState.AWAY
        ctx.minutesIdle > 60 -> PetState.BORED
        ctx.minutesIdle < 3 -> PetState.HAPPY
        else -> PetState.CURIOUS
    }
}

/**
 * 主动预算（PRD §十四）。
 *
 * AI Launcher 最大风险是"太聪明→太烦"。系统每天只有有限主动行为预算：
 * - 每次主动行为消耗预算，预算耗尽则当天只响应、不主动；
 * - 用户买账（点击/跟进）回一点预算；用户忽略则额外扣减、降频。
 * - 每天零点重置。
 *
 * Phase 4a：模型 + 接入点；AiBrain.shouldThink 在 Phase 4b 接入。
 */
class AttentionBudget(private val daily: Int = 12) {
    private var dayStamp: String = todayStamp()
    private var remaining: Int = daily

    private fun todayStamp(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun rollover() {
        val t = todayStamp()
        if (t != dayStamp) {
            dayStamp = t
            remaining = daily
        }
    }

    /** 尝试消耗预算，返回是否允许本次主动行为 */
    @Synchronized
    fun tryConsume(cost: Int = 1): Boolean {
        rollover()
        return if (remaining >= cost) {
            remaining -= cost
            true
        } else false
    }

    /** 用户跟进了主动行为：回预算，提高相关权重 */
    @Synchronized
    fun recordEngaged() {
        rollover()
        remaining = minOf(daily, remaining + 2)
    }

    /** 用户忽略：降频 */
    @Synchronized
    fun recordIgnored() {
        rollover()
        remaining = maxOf(0, remaining - 2)
    }

    @Synchronized
    fun debugRemaining(): Int {
        rollover()
        return remaining
    }
}
