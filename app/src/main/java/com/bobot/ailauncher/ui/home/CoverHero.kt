package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.CoverPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v0.68.34（Bob）：杂志封面 hero——主屏顶层编辑区。
 * v0.68.59：3 模板轮换——按日期哈希每天切换。
 * 版式 A「时间巨像」：日期刊头 + 金线 + 巨型时间 + 宋体简报标题 + 天气副行，七仔在右侧。
 * 版式 B「头条简报」：标题是主角，时间退居副行。
 * 版式 C「留白时刻」：极简居中，时间唯一巨像。
 * 纯展示（无交互），人体工学：不占用拇指区，无需点按。
 *
 * v0.68.99（主页重构 R-1，Bob 拍板「简报即主页」）：hero 即简报区本体——
 * 静态主页只剩简报 + Dock 两层。模板映射：HEADLINE 是简报形态本体（眉题
 * 改为「TODAY · 今日简报」，补讲述句一行）；GIANT_TIME 保留巨字时钟，
 * 其简报句（summary）与时段讲述本就在版面内，会行由 [BriefMeetingRow]
 * 接在 hero 下方整合；MINIMAL 收为时钟行 + 一行简报（summary 单行）。
 * 下一场会不再是独立卡：会行长在简报内（见 [BriefMeetingRow]），
 * 信息只在简报出现一处（讲述句不复述会议，会议细节只在会行）。
 */
@Composable
fun CoverHero(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.68.95（F-05）：模板选择也从分钟 ticker 派生——跨午夜模板与日期
    // 同拍翻篇，不再「新模板 + 旧日期」。
    val nowMillis by rememberNowMillis()
    // v0.68.60（Bob）：横屏优先——统一紧凑横排，不跟 Dock 抢竖向空间
    // v0.68.97（布局普查批）：冒烟几何断言锚点（封面 hero 整体）
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.testTag("cover_hero")) {
        if (maxWidth > maxHeight) {
            CoverHeroLandscape(summary, weatherDesc, palette, petSlot, Modifier.fillMaxWidth())
        } else {
            when (coverTemplateFor(nowMillis)) {
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
    // v0.68.95（F-05）：时间/日期/时段统一从分钟 ticker 派生——此前时钟每
    // 分钟刷新，日期与时段文案却被无 key remember{} 冻结，跨午夜/跨时段
    // 不翻篇（且与每分钟自算的模板选择自相矛盾）。
    val nowMillis by rememberNowMillis()
    val timeText = remember(nowMillis) { currentTimeText(nowMillis) }
    val dateText = remember(nowMillis) { currentDateText(nowMillis) }
    val daypartLine = remember(nowMillis) { daypartLine(nowMillis) }

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
                    maxLines = 3,
                    // v0.68.96（F-07）：超行省略收尾，不硬裁切半个字（下同）
                    overflow = TextOverflow.Ellipsis
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

private fun currentTimeText(nowMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(nowMs))

/** 日期文案（纯函数：入参毫秒 → 文案，供单测）：「M月d日 星期X」 */
internal fun currentDateText(nowMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val weekdays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
    return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 ${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}"
}

private fun daypartLine(nowMs: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMs }
        .get(Calendar.HOUR_OF_DAY)
    return daypartForHour(hour)
}

/** 时段文案（纯函数，供单测）：小时 → 时段句，边界与原实现一致 */
internal fun daypartForHour(hour: Int): String = when (hour) {
    in 6..11 -> "七仔帮你盯着呢"
    in 12..17 -> "适合出门走走"
    in 18..22 -> "晚上好好放松"
    else -> "好好休息"
}

/**
 * v0.68.59（Bob）：每日封面 3 模板轮换。
 * A 时间巨像（现有）/ B 头条简报 / C 留白时刻，按日期哈希每天轮换。
 */
enum class CoverTemplate { GIANT_TIME, HEADLINE, MINIMAL }

fun coverTemplateFor(nowMs: Long): CoverTemplate {
    val day = Calendar.getInstance().apply { timeInMillis = nowMs }
        .get(Calendar.DAY_OF_YEAR)
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
    // v0.68.95（F-05）：时间/日期从分钟 ticker 派生（原各自时钟循环 + 冻结日期）
    val nowMillis by rememberNowMillis()
    val timeText = remember(nowMillis) { currentTimeText(nowMillis) }
    val dateText = remember(nowMillis) { currentDateText(nowMillis) }
    // v0.68.99：讲述句（时段句）——HEADLINE 此前没有讲述行，本批补齐
    val narration = remember(nowMillis) { daypartLine(nowMillis) }

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
                    TrackedText(text = "TODAY · 今日简报", fontSize = 12.sp, tracking = 4.dp,
                        color = AILauncherColors.current.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = summary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = titleSize, lineHeight = (titleSize.value + 8).sp,
                        color = palette.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "$timeText · $weatherDesc", fontSize = 16.sp,
                        color = palette.title.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = narration, fontSize = 15.sp,
                        color = palette.title.copy(alpha = 0.7f))
                }
                Box(modifier = Modifier.padding(start = 16.dp)) { petSlot() }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TrackedText(text = "TODAY · 今日简报", fontSize = 12.sp, tracking = 4.dp,
                        color = AILauncherColors.current.Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.width(44.dp).height(3.dp)
                        .background(AILauncherColors.current.Accent))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = summary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = titleSize, lineHeight = (titleSize.value + 8).sp,
                        letterSpacing = 2.sp, color = palette.title, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "$timeText · $weatherDesc", fontSize = 16.sp,
                        color = palette.title.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    TrackedText(text = dateText, fontSize = 12.sp, tracking = 4.dp,
                        color = palette.title.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.height(10.dp))
                    // v0.68.99：讲述句——简报的一句话（时段句，不复述会议）
                    Text(text = narration, fontSize = 15.sp,
                        color = palette.title.copy(alpha = 0.7f))
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = (140 * scale).dp)) { petSlot() }
            }
        }
    }
}

/**
 * 模板 C「留白时刻」：极简居中，大量留白。
 * v0.68.99（主页重构 R-1）：收为「时钟行 + 一行简报」——时间从 104sp 巨像
 * 降到 64sp 时钟行，简报句单行呈现，日期·天气作副行；七仔仍居中。
 */
@Composable
fun CoverHeroMinimal(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    petSlot: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // v0.68.95（F-05）：时间从分钟 ticker 派生（原各自时钟循环）
    val nowMillis by rememberNowMillis()
    val timeText = remember(nowMillis) { currentTimeText(nowMillis) }
    val dateText = remember(nowMillis) { currentDateText(nowMillis) }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(top = 80.dp, start = 28.dp, end = 28.dp)
    ) {
        val scale = (maxWidth / 400.dp).coerceIn(0.75f, 1f)
        // v0.68.99：时钟行 64sp（原 104sp 巨像收编——简报句才是 MINIMAL 的锚点）
        val timeSize = (64 * scale).sp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 居中时钟行
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = timeText.split(":")
                Text(text = parts.getOrElse(0) { "--" }, fontSize = timeSize,
                    fontWeight = FontWeight.Bold, letterSpacing = (-2).sp,
                    color = palette.title, lineHeight = timeSize)
                Text(text = ":", fontSize = timeSize, fontWeight = FontWeight.Bold,
                    color = AILauncherColors.current.Accent, lineHeight = timeSize)
                Text(text = parts.getOrElse(1) { "--" }, fontSize = timeSize,
                    fontWeight = FontWeight.Bold, letterSpacing = (-2).sp,
                    color = palette.title, lineHeight = timeSize)
            }
            Spacer(modifier = Modifier.height(20.dp))
            // 金线（居中短线）
            Box(modifier = Modifier.width(32.dp).height(2.dp)
                .background(AILauncherColors.current.Accent))
            Spacer(modifier = Modifier.height(20.dp))
            // 一行简报（单行省略——MINIMAL 只给锚点，不展开）
            Text(text = summary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = palette.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            // 日期 · 天气副行
            Text(text = "$dateText · $weatherDesc", fontSize = 14.sp,
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
    // v0.68.95（F-05）：时间/日期从分钟 ticker 派生（原各自时钟循环 + 冻结日期）
    val nowMillis by rememberNowMillis()
    val timeText = remember(nowMillis) { currentTimeText(nowMillis) }
    val dateText = remember(nowMillis) { currentDateText(nowMillis) }

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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

/**
 * v0.68.99（主页重构 R-1，Bob 拍板「简报即主页」）：简报会行——下一场会
 * 直接长在简报里，取代退役的 NextUp 独立卡（NextUpStrip 已删）。
 *
 * 形态：无卡片、无玻璃——分隔线 + 金色「接下来 · HH:mm」标签 + 宋体标题
 * + 「加入」动作，整行可点走既有 joinCalendarEvent 路径（TODAY 铁律：
 * 信息必须配用户最可能的下一步动作）。呈现即登记：本行呈现的事件由
 * 调用方（PetZone）同步写入 TodayEventsHolder.presentedNextEventId，
 * 建议面去重契约（.93）不变——会议信息全场只在这一处呈现。
 *
 * testTag 沿用 today_nextup：冒烟（nextup_tap_flow / 去重断言）与横屏
 * 几何断言的语义等价迁移到本行。
 */
@Composable
fun BriefMeetingRow(
    event: CalEvent,
    palette: CoverPalette,
    onJoin: (CalEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = remember(event.begin) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.begin))
    }
    val title = event.title.ifBlank { "日程" }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.title.copy(alpha = 0.18f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("today_nextup")
                // 不合并语义：标题文本节点须独立存在（冒烟按标题文本定位本行、
                // 去重断言按文本节点计数）；行自身挂整句 contentDescription
                .semantics {
                    contentDescription = "接下来 $timeText 的会议：$title，点按加入"
                }
                .clip(RoundedCornerShape(12.dp))
                .clickable { onJoin(event) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TrackedText(
                    text = "接下来 · $timeText",
                    fontSize = 12.sp,
                    tracking = 3.dp,
                    color = AILauncherColors.current.Accent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(AILauncherColors.current.Accent.copy(alpha = 0.16f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "加入",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Accent
                )
            }
        }
    }
}
