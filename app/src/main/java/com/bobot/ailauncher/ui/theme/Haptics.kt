package com.bobot.ailauncher.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * v0.68.93（布局与交互 Loop 一批③）：全局震动收敛。
 *
 * 此前全仓 22 个震动调用点几乎全用 LongPress（系统长振）：滑过一个字母、
 * 点一下图标、扇区切换都嗡一声，Bob 真机点名「所有震动可以再轻一点」。
 * 收敛为两档，调用点只换档位——触发时机与条件一律不动：
 * - [tickHaptic]：最轻的 tick。日常点按、滑过/切换、阈值经过、拖动重排。
 * - [confirmHaptic]：稍明确一档。真正提交/进入模式/解锁/确认类时刻用，
 *   仍显著轻于旧 LongPress。
 *
 * 为什么走 View 框架常量而不是 Compose 的 HapticFeedbackType：本仓 BOM
 * 2024.10.01（Compose UI 1.7.5）里 HapticFeedbackType 还没有 SegmentTick /
 * Confirm（1.8.0-alpha06 才加入），框架常量则 compileSdk 34 现成可用：
 * SEGMENT_TICK 是 API 34 常量，34 以下退 CLOCK_TICK（同为轻 tick）；
 * CONFIRM 是 API 30 常量，29 上退 KEYBOARD_TAP。方向只轻不重。
 * View.performHapticFeedback 同样尊重系统「触摸反馈」开关，与旧路径一致。
 */
fun View.tickHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    )
}

/** 见 [tickHaptic]：确认档。 */
fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
    )
}
