package com.bobot.ailauncher.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * v0.68.93（布局与交互 Loop 一批③）：全局震动收敛。
 *
 * 此前全仓 22 个震动调用点几乎全用 LongPress（系统长振）：滑过一个字母、
 * 点一下图标、扇区切换都嗡一声，Bob 真机点名「所有震动可以再轻一点」。
 * 收敛为两档，调用点只换档位——触发时机与条件一律不动：
 * - [tick]：最轻的段落 tick。日常点按、滑过/切换、阈值经过、拖动重排。
 * - [confirm]：稍明确一档。真正提交/进入模式/解锁/确认类时刻用，
 *   仍显著轻于旧 LongPress。
 */
fun HapticFeedback.tick() {
    performHapticFeedback(HapticFeedbackType.SegmentTick)
}

/** 见 [tick]：确认档。 */
fun HapticFeedback.confirm() {
    performHapticFeedback(HapticFeedbackType.Confirm)
}

/**
 * View 版确认档（TodaySection 等直接持有 View 的老调用点用）：
 * CONFIRM 常量 Android 11（API 30）才有，minSdk 29 上退回 KEYBOARD_TAP，
 * 方向只轻不重。
 */
fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
    )
}
