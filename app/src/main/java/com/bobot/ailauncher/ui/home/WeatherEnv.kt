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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.30.0：天气场景区——宠物的局部小世界（Bob 确认的氛围级方案）。
 * 天气收进 230x300dp 椭圆场景区（羽化边缘），不铺满屏。
 * 雨=细长雨丝快落（暗冷），雪=圆绒雪点慢飘（亮暖白），两者拉开。
 */
@Composable
fun WeatherEnv(modifier: Modifier = Modifier) {
    var weather by remember { mutableStateOf<WeatherRepository.WeatherInfo?>(null) }

    LaunchedEffect(Unit) {
        Thread {
            val w = WeatherRepository.fetchSync()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                weather = w
                // v0.30.0：同步给宠物微动作
                com.bobot.ailauncher.data.WeatherState.update(w?.desc)
            }
        }.start()
    }

    val w = weather ?: return
    // 夜间判断：18点-6点为夜
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val isNight = hour >= 18 || hour < 6

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 场景区：椭圆裁剪，天气只发生在这里
        // 氛围渐变本身带透明衰减，边缘自然融入壁纸
        Box(
            modifier = Modifier
                .size(230.dp, 300.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
        ) {
            when {
                isNight -> NightScene()
                w.desc.contains("晴") -> SunnyScene(temp = w.temp, city = w.city)
                w.desc.contains("雨") -> RainyScene()
                w.desc.contains("雪") -> SnowyScene()
                else -> CloudyScene()
            }
        }
        // 温度小字（场景区下方）
        if (!isNight) {
            Text(
                text = "${w.city} ${w.temp}°",
                fontSize = 12.sp,
                color = AILauncherColors.Title.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 170.dp)
            )
        }
    }
}

/** 晴：左上暖光 */
@Composable
private fun SunnyScene(temp: Int, city: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 暖光从左上
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFBE5A).copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.7f
            ),
            size = size
        )
    }
}

/** 雨：暗冷 + 细长雨丝快落 */
@Composable
private fun RainyScene() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 压暗冷调
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2D3C50).copy(alpha = 0.5f),
                    Color(0xFF1C2637).copy(alpha = 0.62f)
                )
            ),
            size = size
        )
    }
    // 22 条雨丝
    repeat(22) { i ->
        val transition = rememberInfiniteTransition(label = "rain$i")
        val y by transition.animateFloat(
            initialValue = -30f,
            targetValue = 320f,
            animationSpec = infiniteRepeatable(
                animation = tween((700 + i * 30), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rainY$i"
        )
        val x = (i * 37) % 220
        Canvas(
            modifier = Modifier
                .size(2.dp, (10 + (i % 5) * 2).dp)
                .offset(x = x.dp, y = y.dp)
        ) {
            // 细长雨丝，带角度
            drawLine(
                color = Color(0xFFA0C3EB).copy(alpha = 0.55f),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2 + 4.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/** 雪：亮暖白 + 圆绒雪点慢飘 */
@Composable
private fun SnowyScene() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 明亮冷白（和雨的暗冷拉开）
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE6F2FF).copy(alpha = 0.35f),
                    Color(0xFFC8DCF5).copy(alpha = 0.12f)
                ),
                center = Offset(size.width / 2, size.height * 0.1f),
                radius = size.width * 0.8f
            ),
            size = size
        )
    }
    // 12 朵雪点
    repeat(12) { i ->
        val transition = rememberInfiniteTransition(label = "snow$i")
        val y by transition.animateFloat(
            initialValue = -20f,
            targetValue = 320f,
            animationSpec = infiniteRepeatable(
                animation = tween((4000 + i * 250), easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "snowY$i"
        )
        // 左右飘
        val swayTransition = rememberInfiniteTransition(label = "sway$i")
        val xOff by swayTransition.animateFloat(
            initialValue = -8f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "swayX$i"
        )
        val baseX = (i * 53) % 210
        val flakeSize = 4 + (i % 3) * 2
        Canvas(
            modifier = Modifier
                .size(flakeSize.dp)
                .offset(x = (baseX + xOff).dp, y = y.dp)
        ) {
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = size.minDimension / 2,
                center = center
            )
        }
    }
}

/** 阴：均匀冷灰 */
@Composable
private fun CloudyScene() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            color = Color(0xFF788291).copy(alpha = 0.22f),
            size = size
        )
    }
}

/** 夜：深蓝月光 */
@Composable
private fun NightScene() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 月光从右上
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF96B4FF).copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.72f, size.height * 0.18f),
                radius = size.width * 0.5f
            ),
            size = size
        )
        // 深蓝底
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F1428).copy(alpha = 0.6f),
                    Color(0xFF0A0E1C).copy(alpha = 0.7f)
                )
            ),
            size = size
        )
    }
}
