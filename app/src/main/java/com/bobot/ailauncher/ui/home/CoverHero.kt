package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.CoverPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * v0.68.34（Bob）：杂志封面 hero——主屏顶层编辑区。
 * 版式 A「时间巨像」：日期刊头 + 金线 + 巨型时间 + 宋体简报标题 + 天气副行，七仔在右侧。
 * 纯展示（无交互），人体工学：不占用拇指区，无需点按。
 */
@Composable
fun CoverHero(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.68.34： live 时间，每分钟整点刷新（轻量协程，无轮询浪费）
    var timeText by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 200L)
            timeText = currentTimeText()
        }
    }
    val dateText = remember { currentDateText() }
    val daypartLine = remember { daypartLine() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 28.dp, end = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 刊头日期
            TrackedText(
                text = dateText,
                fontSize = 12.sp,
                tracking = 4.dp,
                color = palette.title.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 金线
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .background(AILauncherColors.Accent)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 巨型时间（冒号金色点睛）
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = timeText.split(":")
                Text(
                    text = parts.getOrElse(0) { "--" },
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-4).sp,
                    color = palette.title,
                    lineHeight = 104.sp
                )
                Text(
                    text = ":",
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Accent,
                    lineHeight = 104.sp
                )
                Text(
                    text = parts.getOrElse(1) { "--" },
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-4).sp,
                    color = palette.title,
                    lineHeight = 104.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 简报标题（宋体）
            Text(
                text = summary,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 54.sp,
                letterSpacing = 2.sp,
                color = palette.title,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 副行：天气 + 时段
            Text(
                text = listOf(weatherDesc, daypartLine).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 15.sp,
                lineHeight = 28.sp,
                color = palette.title.copy(alpha = 0.6f)
            )
        }
        // 七仔在右侧（调用方传入 PetView）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 190.dp)
        ) {
            petSlot()
        }
    }
}

/** 字距排版小组件（杂志刊头用） */
@Composable
fun TrackedText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    tracking: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        text.forEach { ch ->
            Text(
                text = ch.toString(),
                fontSize = fontSize,
                color = color
            )
            Spacer(modifier = Modifier.width(tracking))
        }
    }
}

private fun currentTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)

private fun currentDateText(): String {
    val cal = Calendar.getInstance()
    val weekdays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
    return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 ${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}"
}

private fun daypartLine(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..11 -> "七仔帮你盯着呢"
        in 12..17 -> "适合出门走走"
        in 18..22 -> "晚上好好放松"
        else -> "好好休息"
    }
}
