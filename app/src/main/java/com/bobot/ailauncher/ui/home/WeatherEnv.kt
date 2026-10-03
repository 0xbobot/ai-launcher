package com.bobot.ailauncher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.29.0：天气环境——七仔的天空。
 * 晴→太阳照+阴影+温度；雨→雨滴落到七仔身上；多云/阴→云飘过。
 * 纯视觉，不打扰。
 */
@Composable
fun WeatherEnv(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherRepository.WeatherInfo?>(null) }

    LaunchedEffect(Unit) {
        // 后台线程取天气（fetchSync 是阻塞调用）
        Thread {
            val w = WeatherRepository.fetchSync()
            // 回主线程
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                weather = w
            }
        }.start()
    }

    val w = weather ?: return
    Box(modifier = modifier.fillMaxSize()) {
        when {
            w.desc.contains("晴") -> SunnyEnv(temp = w.temp, city = w.city, modifier = Modifier.align(Alignment.TopCenter))
            w.desc.contains("雨") -> RainyEnv(modifier = Modifier.fillMaxSize())
            w.desc.contains("雪") -> SnowyEnv(modifier = Modifier.fillMaxSize())
            else -> CloudyEnv(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

/** 晴：太阳在上方照 + 温度文字 */
@Composable
private fun SunnyEnv(temp: Int, city: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // 太阳（Canvas：圆 + 光芒）
        Canvas(modifier = Modifier.size(72.dp)) {
            val c = center
            val r = size.minDimension / 2 * 0.55f
            // 光芒
            for (i in 0 until 8) {
                val angle = i * (Math.PI / 4).toFloat()
                val start = Offset(
                    c.x + kotlin.math.cos(angle) * r * 1.3f,
                    c.y + kotlin.math.sin(angle) * r * 1.3f
                )
                val end = Offset(
                    c.x + kotlin.math.cos(angle) * r * 1.7f,
                    c.y + kotlin.math.sin(angle) * r * 1.7f
                )
                drawLine(
                    color = Color(0xFFFFD66B).copy(alpha = 0.8f),
                    start = start,
                    end = end,
                    strokeWidth = 4.dp.toPx()
                )
            }
            // 太阳本体
            drawCircle(color = Color(0xFFFFD66B), radius = r, center = c)
            drawCircle(color = Color(0xFFFFE9A8), radius = r * 0.7f, center = c)
        }
        // 温度文字（太阳下方）
        Text(
            text = "$city $temp°",
            fontSize = 13.sp,
            color = AILauncherColors.Title.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
        )
    }
}

/** 雨：雨滴下落到七仔身上 */
@Composable
private fun RainyEnv(modifier: Modifier = Modifier) {
    val drops = remember { List(10) { it } }
    Box(modifier = modifier) {
        drops.forEach { i ->
            val transition = rememberInfiniteTransition(label = "rain$i")
            val y by transition.animateFloat(
                initialValue = -40f,
                targetValue = 700f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400 + i * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rainY$i"
            )
            // 横向错开
            val xOffset = (i * 37) % 300
            Canvas(
                modifier = Modifier
                    .size(3.dp, 14.dp)
                    .offset(x = xOffset.dp, y = y.dp)
                    .align(Alignment.TopStart)
            ) {
                drawLine(
                    color = Color(0xFF7FB8E8).copy(alpha = 0.7f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

/** 雪：雪花飘落 */
@Composable
private fun SnowyEnv(modifier: Modifier = Modifier) {
    val flakes = remember { List(8) { it } }
    Box(modifier = modifier) {
        flakes.forEach { i ->
            val transition = rememberInfiniteTransition(label = "snow$i")
            val y by transition.animateFloat(
                initialValue = -30f,
                targetValue = 700f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200 + i * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snowY$i"
            )
            val xOffset = (i * 53) % 320
            Canvas(
                modifier = Modifier
                    .size(8.dp)
                    .offset(x = xOffset.dp, y = y.dp)
                    .align(Alignment.TopStart)
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = size.minDimension / 2,
                    center = center
                )
            }
        }
    }
}

/** 多云/阴：云飘过 */
@Composable
private fun CloudyEnv(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(110.dp, 60.dp)) {
        val cloudColor = Color(0xFFE8E4DE).copy(alpha = 0.9f)
        // 三个圆拼成云
        drawCircle(cloudColor, radius = size.width * 0.22f, center = Offset(size.width * 0.3f, size.height * 0.6f))
        drawCircle(cloudColor, radius = size.width * 0.28f, center = Offset(size.width * 0.55f, size.height * 0.45f))
        drawCircle(cloudColor, radius = size.width * 0.2f, center = Offset(size.width * 0.78f, size.height * 0.62f))
        // 底部连成一片
        drawRect(
            color = cloudColor,
            topLeft = Offset(size.width * 0.12f, size.height * 0.55f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.3f)
        )
    }
}
