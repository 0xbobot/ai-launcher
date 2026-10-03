package com.bobot.ailauncher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v0.28.1：电池状态（单例，供宠物省电模式读取）。
 * 低电量时七仔自动进入省电模式：变困、眨眼变慢、停止呼吸动画。
 */
object BatteryState {
    private val _batteryPct = MutableStateFlow(100)
    val batteryPct: StateFlow<Int> = _batteryPct.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _isLowPower = MutableStateFlow(false)
    /** 低电量（<=10% 且未充电）：七仔省电模式 */
    val isLowPower: StateFlow<Boolean> = _isLowPower.asStateFlow()

    fun update(pct: Int, charging: Boolean) {
        _batteryPct.value = pct
        _isCharging.value = charging
        _isLowPower.value = !charging && pct <= 10
    }
}
