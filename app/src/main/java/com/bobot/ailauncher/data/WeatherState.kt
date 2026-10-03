package com.bobot.ailauncher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v0.30.0：天气状态（单例，供宠物微动作读取）。
 */
object WeatherState {
    /** 天气描述：晴/雨/雪/多云/阴/雾 等 */
    private val _desc = MutableStateFlow<String?>(null)
    val desc: StateFlow<String?> = _desc.asStateFlow()

    fun update(desc: String?) {
        _desc.value = desc
    }
}
