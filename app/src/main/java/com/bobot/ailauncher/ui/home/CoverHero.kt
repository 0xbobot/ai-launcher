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
 * v0.68.59：3 模板轮换——按日期哈希每天切换。
 * 版式 A「时间巨像」：日期刊头 + 金线 + 巨型时间 + 宋体简报标题 + 天气副行，七仔在右侧。
 * 版式 B「头条简报」：标题是主角，时间退居副行。
 * 版式 C「留白时刻」：极简居中，时间唯一巨像。
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
    // v0.68.60（Bob）：横屏优先——统一紧凑横排，不跟 Dock 抢竖向空间
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        if (maxWidth > maxHeight) {
            CoverHeroLandscape(summary, weatherDesc, palette, petSlot, Modifier.fillMaxWidth())
        } else {
            when (coverTemplateFor()) {
                CoverTemplate.HEADLINE -> CoverHeroHeadline(summary, weatherDesc, palette, petSlot, Modifier.fillMaxWidth())
                CoverTemplate.MINIMAL -> CoverHeroMinimal(summary, weatherDesc, palette, petSlot, Modifier.fillMaxWidth())
                CoverTemplate.GIANT_TIME -> CoverHeroGiantTime(summary, weatherDesc, palette, petSlot, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CoverHeroGiantTime(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.68.50：响应式——按屏宽缩放，基准 400.dp，小屏不溢出
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 28.dp, end = 16.dp)
    ) {
        val scale = (maxWidth / 400.dp).coerceIn(0.75f, 1f)
        val timeSize = (104 * scale).sp
        val titleSize = (32 * scale).sp  // v0.68.59：字阶收敛 44→32
        val petTopPad = (190 * scale).dp
        val isLandscape = maxWidth > maxHeight
        // v0.68.59：横屏双栏——时间左，标题/七仔右
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

    // v0.68.50：入场动画——kicker 先现，时间上滑，标题 stagger 80ms
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        entered = true
    }
    val mt = com.bobot.ailauncher.ui.theme.MotionTokens

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 刊头日期
            androidx.compose.animation.AnimatedVisibility(
                visible = entered,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Fast, easing = mt.Easing)
                )
            ) {
                TrackedText(
                    text = dateText,
                    fontSize = 12.sp,
                    tracking = 4.dp,
                    color = palette.title.copy(alpha = 0.45f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 金线
            androidx.compose.animation.AnimatedVisibility(
                visible = entered,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Fast, easing = mt.Easing)
                ) + androidx.compose.animation.expandHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                )
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(3.dp)
                        .background(AILauncherColors.current.Accent)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 巨型时间（冒号金色点睛）——上滑入场
            androidx.compose.animation.AnimatedVisibility(
                visible = entered,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(mt.Slow, easing = mt.Easing)
                ) { it / 3 } + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Slow, easing = mt.Easing)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val parts = timeText.split(":")
                    Text(
                        text = parts.getOrElse(0) { "--" },
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-4).sp,
                        color = palette.title,
                        lineHeight = timeSize
                    )
                    Text(
                        text = ":",
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.current.Accent,
                        lineHeight = timeSize
                    )
                    Text(
                        text = parts.getOrElse(1) { "--" },
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-4).sp,
                        color = palette.title,
                        lineHeight = timeSize
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 简报标题（宋体）——stagger 80ms
            var titleEntered by remember { mutableStateOf(false) }
            LaunchedEffect(entered) {
                if (entered) {
                    delay(80)
                    titleEntered = true
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = titleEntered,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                ) { it / 4 } + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                )
            ) {
                Text(
                    text = summary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    lineHeight = (titleSize.value + 10).sp,
                    letterSpacing = 2.sp,
                    color = palette.title,
                    maxLines = 3
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 副行：天气 + 时段
            androidx.compose.animation.AnimatedVisibility(
                visible = titleEntered,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                )
            ) {
                Text(
                    text = listOf(weatherDesc, daypartLine).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    color = palette.title.copy(alpha = 0.6f)
                )
            }
        }
        // 七仔在右侧（调用方传入 PetView）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = petTopPad)
        ) {
            petSlot()
        }
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

/**
 * v0.68.59（Bob）：每日封面 3 模板轮换。
 * A 时间巨像（现有）/ B 头条简报 / C 留白时刻，按日期哈希每天轮换。
 */
enum class CoverTemplate { GIANT_TIME, HEADLINE, MINIMAL }

fun coverTemplateFor(): CoverTemplate {
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    return CoverTemplate.values()[day % 3]
}

/**
 * 模板 B「头条简报」：标题是主角，时间退居副行。
 * 刊头 HEADLINE + 宋体大标题 + 时间/天气副行。
 */
@Composable
fun CoverHeroHeadline(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeText by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 200L)
            timeText = currentTimeText()
        }
    }
    val dateText = remember { currentDateText() }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(top = 56.dp, start = 28.dp, end = 16.dp)
    ) {
        val scale = (maxWidth / 400.dp).coerceIn(0.75f, 1f)
        val titleSize = (32 * scale).sp
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // 横屏：左右双栏
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    TrackedText(text = "HEADLINE · 头条", fontSize = 12.sp, tracking = 4.dp,
                        color = AILauncherColors.current.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = summary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = titleSize, lineHeight = (titleSize.value + 8).sp,
                        color = palette.title, maxLines = 3)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "$timeText · $weatherDesc", fontSize = 16.sp,
                        color = palette.title.copy(alpha = 0.6f))
                }
                Box(modifier = Modifier.padding(start = 16.dp)) { petSlot() }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TrackedText(text = "HEADLINE · 头条", fontSize = 12.sp, tracking = 4.dp,
                        color = AILauncherColors.current.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.width(44.dp).height(3.dp)
                        .background(AILauncherColors.current.Accent))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = summary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = titleSize, lineHeight = (titleSize.value + 8).sp,
                        letterSpacing = 2.sp, color = palette.title, maxLines = 4)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "$timeText · $weatherDesc", fontSize = 16.sp,
                        color = palette.title.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    TrackedText(text = dateText, fontSize = 12.sp, tracking = 4.dp,
                        color = palette.title.copy(alpha = 0.45f))
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = (140 * scale).dp)) { petSlot() }
            }
        }
    }
}

/**
 * 模板 C「留白时刻」：极简居中，大量留白，时间是唯一的巨像。
 * 无宋体标题，只有时间和一句天气，七仔更突出。
 */
@Composable
fun CoverHeroMinimal(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeText by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 200L)
            timeText = currentTimeText()
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(top = 80.dp, start = 28.dp, end = 28.dp)
    ) {
        val scale = (maxWidth / 400.dp).coerceIn(0.75f, 1f)
        val timeSize = (104 * scale).sp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 居中巨型时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = timeText.split(":")
                Text(text = parts.getOrElse(0) { "--" }, fontSize = timeSize,
                    fontWeight = FontWeight.Bold, letterSpacing = (-4).sp,
                    color = palette.title, lineHeight = timeSize)
                Text(text = ":", fontSize = timeSize, fontWeight = FontWeight.Bold,
                    color = AILauncherColors.current.Accent, lineHeight = timeSize)
                Text(text = parts.getOrElse(1) { "--" }, fontSize = timeSize,
                    fontWeight = FontWeight.Bold, letterSpacing = (-4).sp,
                    color = palette.title, lineHeight = timeSize)
            }
            Spacer(modifier = Modifier.height(24.dp))
            // 金线（居中短线）
            Box(modifier = Modifier.width(32.dp).height(2.dp)
                .background(AILauncherColors.current.Accent))
            Spacer(modifier = Modifier.height(24.dp))
            // 一句天气
            Text(text = weatherDesc, fontSize = 16.sp,
                color = palette.title.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(32.dp))
            // 七仔居中
            petSlot()
        }
    }
}

/**
 * v0.68.60（Bob）：横屏统一紧凑布局——横排，不跟 Dock 抢竖向空间。
 * 左：日期刊头 + 时间（64.sp）+ 标题 + 天气；右：七仔。
 */
@Composable
fun CoverHeroLandscape(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeText by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 200L)
            timeText = currentTimeText()
        }
    }
    val dateText = remember { currentDateText() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 28.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TrackedText(
                text = dateText,
                fontSize = 12.sp,
                tracking = 4.dp,
                color = palette.title.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = timeText.split(":")
                Text(
                    text = "${parts.getOrElse(0) { "--" }}:${parts.getOrElse(1) { "--" }}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = palette.title,
                    lineHeight = 64.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = summary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = palette.title,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = weatherDesc,
                fontSize = 14.sp,
                color = palette.title.copy(alpha = 0.6f)
            )
        }
        Box(modifier = Modifier.padding(start = 24.dp)) {
            petSlot()
        }
    }
}
