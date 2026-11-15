package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 节点一明天天气解析单测：daily 数组第 1 天（最高/最低/天气码→描述）。
 * JSON 解析本身在 fetchSync 里（try 包着，取不到即缺天气）；这里锁
 * 数组到预报的纯映射。
 */
class WeatherTomorrowTest {

    @Test
    fun tomorrowForecastFromSecondDay() {
        val forecast = WeatherRepository.tomorrowForecast(
            maxTemps = listOf(26.0, 24.0),
            minTemps = listOf(18.0, 17.0),
            codes = listOf(0, 1)
        )
        assertEquals(WeatherRepository.TomorrowForecast(24, 17, "多云"), forecast)
    }

    @Test
    fun tomorrowForecastWeatherCodes() {
        // 天气码 0 = 晴，61 = 雨（与 codeToDesc 同一张表）。
        val sunny = WeatherRepository.tomorrowForecast(
            listOf(26.0, 25.0), listOf(18.0, 16.0), listOf(2, 0)
        )
        assertEquals("晴", sunny!!.desc)
        val rainy = WeatherRepository.tomorrowForecast(
            listOf(26.0, 22.0), listOf(18.0, 15.0), listOf(2, 61)
        )
        assertEquals("雨", rainy!!.desc)
    }

    @Test
    fun shortArraysReturnNull() {
        // 只有今天（第 0 天），没有明天 → 缺天气，该行不显示。
        assertNull(
            WeatherRepository.tomorrowForecast(
                listOf(26.0), listOf(18.0), listOf(0)
            )
        )
        assertNull(
            WeatherRepository.tomorrowForecast(
                emptyList(), emptyList(), emptyList()
            )
        )
        // 任意一个数组不足 2 天都算缺。
        assertNull(
            WeatherRepository.tomorrowForecast(
                listOf(26.0, 24.0), listOf(18.0), listOf(0, 1)
            )
        )
    }
}
