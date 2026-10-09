package com.bobot.ailauncher.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.26.2：天气（Open-Meteo 免费 API，无需 key）。
 * 定位用 IP（ip-api.com，免权限），失败回退深圳。
 * 注意：调用方需在 IO 线程调用（用线程，不用协程，避免依赖问题）。
 */
object WeatherRepository {
    private const val TAG = "Weather"

    data class WeatherInfo(
        val temp: Int,
        val desc: String,
        val city: String,
        // v0.61.0：今日最高/最低、体感、湿度、风速、未来几小时
        val high: Int = 0,
        val low: Int = 0,
        val feelsLike: Int = 0,
        val humidity: Int = 0,
        val windSpeed: Double = 0.0,
        val hourly: List<HourPoint> = emptyList()
    )

    data class HourPoint(
        val hour: Int,      // 0-23
        val temp: Int,
        val desc: String
    )

    private fun codeToDesc(code: Int): String = when (code) {
        0 -> "晴"
        1, 2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55, 56, 57 -> "小雨"
        61, 63, 65, 66, 67, 80, 81, 82 -> "雨"
        71, 73, 75, 77, 85, 86 -> "雪"
        95, 96, 99 -> "雷阵雨"
        else -> "多云"
    }

    /** 同步调用，调用方需在后台线程执行 */
    fun fetchSync(): WeatherInfo? {
        try {
            var lat = 22.5431
            var lon = 114.0579
            var city = "深圳"
            // IP 定位
            try {
                val c1 = URL("http://ip-api.com/json/?fields=lat,lon,city,status")
                    .openConnection() as HttpURLConnection
                c1.connectTimeout = 5000
                c1.readTimeout = 5000
                val j1 = JSONObject(c1.inputStream.bufferedReader().readText())
                if (j1.optString("status") == "success") {
                    lat = j1.getDouble("lat")
                    lon = j1.getDouble("lon")
                    city = j1.optString("city", "深圳")
                }
                c1.disconnect()
            } catch (_: Exception) {
            }
            // 天气（v0.61.0：加体感/湿度/风速/今日高低/逐小时）
            val c2 = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto"
            ).openConnection() as HttpURLConnection
            c2.connectTimeout = 8000
            c2.readTimeout = 8000
            val j2 = JSONObject(c2.inputStream.bufferedReader().readText())
            c2.disconnect()
            val cur = j2.getJSONObject("current")
            val temp = cur.getDouble("temperature_2m").toInt()
            val desc = codeToDesc(cur.getInt("weather_code"))
            val feelsLike = cur.optDouble("apparent_temperature", temp.toDouble()).toInt()
            val humidity = cur.optInt("relative_humidity_2m", 0)
            val windSpeed = cur.optDouble("wind_speed_10m", 0.0)
            // 今日最高/最低（取 daily 第 0 天）
            var high = temp
            var low = temp
            try {
                val daily = j2.getJSONObject("daily")
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
            } catch (_: Exception) {
            }
            // 未来几小时：从当前小时往后取 3 个点
            val hourly = mutableListOf<HourPoint>()
            try {
                val h = j2.getJSONObject("hourly")
                val times = h.getJSONArray("time")
                val temps = h.getJSONArray("temperature_2m")
                val codes = h.getJSONArray("weather_code")
                val curTime = cur.optString("time", "")
                var startIdx = -1
                for (i in 0 until times.length()) {
                    if (times.getString(i) == curTime) { startIdx = i; break }
                }
                if (startIdx < 0) startIdx = 0
                // 取 +3h / +6h / +9h 三个点
                for (offset in intArrayOf(3, 6, 9)) {
                    val idx = startIdx + offset
                    if (idx < times.length()) {
                        val t = times.getString(idx) // "2026-10-09T15:00"
                        val hour = t.substringAfter("T").substringBefore(":").toIntOrNull() ?: 0
                        hourly.add(
                            HourPoint(
                                hour = hour,
                                temp = temps.getDouble(idx).toInt(),
                                desc = codeToDesc(codes.getInt(idx))
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            return WeatherInfo(temp, desc, city, high, low, feelsLike, humidity, windSpeed, hourly)
        } catch (e: Exception) {
            Log.d(TAG, "weather failed: ${e.message}")
            return null
        }
    }
}
