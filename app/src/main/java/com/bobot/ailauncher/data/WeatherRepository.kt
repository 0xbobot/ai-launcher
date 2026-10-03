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
        val city: String
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
            // 天气
            val c2 = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            ).openConnection() as HttpURLConnection
            c2.connectTimeout = 8000
            c2.readTimeout = 8000
            val j2 = JSONObject(c2.inputStream.bufferedReader().readText())
            c2.disconnect()
            val cur = j2.getJSONObject("current")
            val temp = cur.getDouble("temperature_2m").toInt()
            val desc = codeToDesc(cur.getInt("weather_code"))
            return WeatherInfo(temp, desc, city)
        } catch (e: Exception) {
            Log.d(TAG, "weather failed: ${e.message}")
            return null
        }
    }
}
