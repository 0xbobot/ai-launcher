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

    // v0.61.0：完整天气信息（供 Today 天气卡片展示）
    private val _full = MutableStateFlow<WeatherRepository.WeatherInfo?>(null)
    val full: StateFlow<WeatherRepository.WeatherInfo?> = _full.asStateFlow()

    fun update(desc: String?) {
        _desc.value = desc
    }

    fun updateFull(info: WeatherRepository.WeatherInfo?) {
        _full.value = info
        if (info != null) _desc.value = "${info.desc} ${info.temp}°"
    }
}
