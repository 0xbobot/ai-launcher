package com.bobot.ailauncher.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v0.26.0：天气（Open-Meteo 免费 API，无需 key）。
 * 定位用 ip-api.com（IP 定位，免权限），失败时回退深圳坐标。
 */
object WeatherRepository {
    private const val TAG = "Weather"
    // 深圳默认坐标（Bob 常驻城市）
    private const val DEFAULT_LAT = 22.5431
    private const val DEFAULT_LON = 114.0579

    data class WeatherInfo(
        val tempC: Double,
        val weatherCode: Int,
        val city: String,
        /** 中文天气描述 */
        val desc: String
    )

    private fun codeToDesc(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "多云"
        2 -> "阴"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75, 77 -> "雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "冰雹"
        else -> "多云"
    }

    suspend fun fetch(): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            // 1. IP 定位
            var lat = DEFAULT_LAT
            var lon = DEFAULT_LON
            var city = "深圳"
            try {
                val ipUrl = URL("http://ip-api.com/json/?fields=lat,lon,city,status")
                val ipConn = ipUrl.openConnection() as HttpURLConnection
                ipConn.connectTimeout = 5000
                ipConn.readTimeout = 5000
                val ipJson = JSONObject(ipConn.inputStream.bufferedReader().readText())
                if (ipJson.optString("status") == "success") {
                    lat = ipJson.getDouble("lat")
                    lon = ipJson.getDouble("lon")
                    city = ipJson.optString("city", "深圳")
                }
                ipConn.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "IP 定位失败，用默认坐标: ${e.message}")
            }

            // 2. Open-Meteo 天气
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()

            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")

            WeatherInfo(
                tempC = temp,
                weatherCode = code,
                city = city,
                desc = codeToDesc(code)
            )
        } catch (e: Exception) {
            Log.d(TAG, "天气获取失败: ${e.message}")
            null
        }
    }
}
