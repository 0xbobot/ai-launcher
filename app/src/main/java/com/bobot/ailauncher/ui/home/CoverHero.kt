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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.SmartCardKind
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.CoverPalette
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v0.68.34（Bob）：杂志封面 hero——主屏顶层编辑区。
 * 版式「时间巨像」：日期刊头 + 金线 + 巨型时间 + 宋体简报标题 + 天气副行。
 * 纯展示（无交互），人体工学：不占用拇指区，无需点按。
 *
 * v0.68.99（主页重构 R-1，Bob 拍板「简报即主页」）：hero 即简报区本体——
 * 静态主页只剩简报 + Dock 两层。GIANT_TIME 保留巨字时钟，其简报句
 * （summary）与时段讲述本就在版面内。
 * 2026-11-11（Bob 亲口）：简报会行拿掉——下一场会不再在简报里出现，会议
 * 只在会前 30 分钟（非会议日程 2 小时）以日历卡出现。
 * （会行组件 BriefMeetingRow 已随 v1.4.0 清理删除。）
 * 2026-11-21（Bob 拍板，Stageshow 桌面优化）：三模板轮换收敛到 GIANT_TIME
 * 一个模板（HEADLINE／MINIMAL 与 coverTemplateFor 已删）——层级不再每天换；
 * 有卡且顶卡是一览卡时 hero 用安静版（无简报标题、无时段句，只留日期刊头
 * ＋金线＋巨型时间＋天气行），卡片当主角。「帮你盯着呢」已砍（daypartForHour
 * 6..11 时段返回空串，安静版与完整版都不留）。
 */
/**
 * 简报文字字级阴影（Bob 2026-10-25 23:27 定 A＋B＋C 之 B）：复用 GlassTextShadow
 * token（offset 0,3、blur 12、黑 0.4），不另发明一套；阴影只跟字形走、
 * 不铺满区域，不是纱。深墨字下深色影近乎不可见（无害），浅色字误判在
 * 浅底上时靠影边恢复可读——深字误判在暗底上的残差由取样 A 负责。
 */
private val briefingTextShadowStyle = TextStyle(shadow = GlassTextShadow)

@Composable
fun CoverHero(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    // v0.68.101（Stage 1 热修·单宠物原则）：输入态紧凑——简报让位给胶囊
    // 堆栈（D-1：键盘上沿之上要装下胶囊+结果条），各模板收顶距/字号。
    // v0.68.101 同批：讲述者七仔撤出简报——七仔只留 Dock 上沿坐姿一只，
    // 简报回归纯文字排版，讲述口吻只活在文案里。
    inputCompact: Boolean = false,
    // Stageshow 桌面优化 3.1（Bob 2026-11-21 定）：安静版——顶卡是一览卡
    // （讲今天）时 hero 不讲简报标题、不讲时段句，只留日期刊头＋金线＋
    // 巨型时间＋天气行，卡片当主角；其他情况 hero 完整。
    quiet: Boolean = false
) {
    // v0.68.60（Bob）：横屏优先——统一紧凑横排，不跟 Dock 抢竖向空间
    // v0.68.97（布局普查批）：冒烟几何断言锚点（封面 hero 整体）
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.testTag("cover_hero")) {
        if (maxWidth > maxHeight) {
            CoverHeroLandscape(summary, weatherDesc, palette, Modifier.fillMaxWidth())
        } else {
            // 2026-11-21：三模板收敛到 GIANT_TIME 一个模板（HEADLINE／MINIMAL 已删）。
            CoverHeroGiantTime(summary, weatherDesc, palette, accent, Modifier.fillMaxWidth(), inputCompact, quiet)
        }
    }
}

@Composable
fun CoverHeroGiantTime(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    inputCompact: Boolean = false,
    // Stageshow 桌面优化 3.1：安静版——无简报标题、无时段句，只留日期刊头
    // ＋金线＋巨型时间＋天气行。
    quiet: Boolean = false
) {
    // v0.68.50：响应式——按屏宽缩放，基准 400.dp，小屏不溢出
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (inputCompact) CoverHeroGiantTimeTopPaddingCompact else CoverHeroGiantTimeTopPadding, start = 32.dp, end = 16.dp)
    ) {
        val scale = (maxWidth / 400.dp).coerceIn(0.75f, 1f)
        // v0.68.101：输入态时钟收一档（104→82 基准），给胶囊堆栈让高
        val timeSize = ((if (inputCompact) 82 else 104) * scale).sp
        val titleSize = (32 * scale).sp  // v0.68.59：字阶收敛 44→32
        val isLandscape = maxWidth > maxHeight
        // v0.68.59：横屏双栏——时间左，标题右（v0.68.101 撤七仔后纯文字）
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
                    fontSize = AILauncherType.Caption.fontSize,
                    tracking = 4.dp,
                    color = palette.title.copy(alpha = 0.45f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                        .background(accent)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                    Text(style = briefingTextShadowStyle,
                        text = parts.getOrElse(0) { "--" },
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-4).sp,
                        color = palette.title,
                        lineHeight = timeSize
                    )
                    Text(style = briefingTextShadowStyle,
                        text = ":",
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        lineHeight = timeSize
                    )
                    Text(style = briefingTextShadowStyle,
                        text = parts.getOrElse(1) { "--" },
                        fontSize = timeSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-4).sp,
                        color = palette.title,
                        lineHeight = timeSize
                    )
                }
            }
            // 简报标题（宋体）——stagger 80ms；安静版不画标题（3.1 卡片当主角），
            // 标题与副行之间的 16dp 间距改由安静版的一段 16dp 承接。
            var titleEntered by remember { mutableStateOf(false) }
            LaunchedEffect(entered, quiet) {
                if (entered && !quiet) {
                    delay(80)
                    titleEntered = true
                }
            }
            if (!quiet) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.animation.AnimatedVisibility(
                    visible = titleEntered,
                    enter = androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                    ) { it / 4 } + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                    )
                ) {
                    Text(style = briefingTextShadowStyle,
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
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            // 副行：安静版只写天气，完整版天气 + 时段
            androidx.compose.animation.AnimatedVisibility(
                visible = if (quiet) entered else titleEntered,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(mt.Medium, easing = mt.Easing)
                )
            ) {
                Text(style = briefingTextShadowStyle,
                    text = if (quiet) weatherDesc else listOf(weatherDesc, daypartLine).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    lineHeight = 28.sp,
                    color = palette.title.copy(alpha = 0.6f)
                )
            }
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
            Text(style = briefingTextShadowStyle,
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

/** 时段文案（纯函数，供单测）：小时 → 时段句，边界与原实现一致。
 * 2026-11-21（Bob 拍板，Stageshow 桌面优化）：6..11 时段的「帮你盯着呢」
 * 已砍，返回空串（调用方过滤空串，副行只剩天气）；其他时段句不动。 */

/**
 * 安静版 hero 判定（纯函数，供单测，Stageshow 桌面优化 3.1）：
 * 顶卡是一览卡（讲今天）时 hero 安静——不讲简报标题、不讲时段句；
 * 顶卡是其他卡或无卡时 hero 完整。
 */
internal fun isQuietHero(topKind: SmartCardKind?): Boolean =
    topKind == SmartCardKind.DAY_OVERVIEW
internal fun daypartForHour(hour: Int): String = when (hour) {
    in 6..11 -> ""
    in 12..17 -> "适合出门走走"
    in 18..22 -> "晚上好好放松"
    else -> "好好休息"
}

/**
 * 横屏 hero 顶距（Bob 2026-11-22 实拍对齐批，563/v1.4.19 横屏截图）：
 * 横屏首页卡片顶边比 D2 Dock 顶边低约 20dp（无宠物线实测 55/1000 屏高）。
 * 根因：Dock 是 MainScreen 悬浮层、底边锚定（顶边＝屏高－184dp，D2）；
 * 卡片在 HomeScreen Column 里顺着 hero 往下排（顶边＝顶距＋hero 高＋8dp
 * 间隙），两边没有共同锚点。hero 底边已顶到 Dock 顶边附近（天气行约在
 * Dock 顶边高度），卡片不能直接上移 20dp（会压 hero 约 12dp），须连 hero
 * 一起收：本顶距 32→12dp，hero 收 20dp，卡片随之上移 20dp，顶边落到
 * D2 Dock 顶边。只动横屏 hero；竖屏顶距（56/16dp）不动。
 */
internal val CoverHeroLandscapeTopPadding = 12.dp

/** 横屏 hero 顶距收窄量（dp）：32－12＝20，与实拍 gap（约 20dp）相等。 */
internal const val CoverHeroLandscapeTopReductionDp = 20f

/**
 * 横屏卡片修后顶边（纯函数，供单测）：修前卡片顶边减收窄量。
 * 对齐判定：修后卡片顶边＝D2 Dock 顶边（屏高－184dp）即对齐。
 */
internal fun landscapeCardTopAfterFix(cardTopBeforeFixDp: Float): Float =
    cardTopBeforeFixDp - CoverHeroLandscapeTopReductionDp

/** 竖屏 hero 顶距（回归锁：本批不动；56dp，输入态紧凑 16dp）。 */
internal val CoverHeroGiantTimeTopPadding = 56.dp
internal val CoverHeroGiantTimeTopPaddingCompact = 16.dp

/**
 * v0.68.60（Bob）：横屏统一紧凑布局——横排，不跟 Dock 抢竖向空间。
 * 左：日期刊头 + 时间（64.sp）+ 标题 + 天气（v0.68.101 撤七仔后纯文字）。
 */
@Composable
fun CoverHeroLandscape(
    summary: String,
    weatherDesc: String,
    palette: CoverPalette,
    modifier: Modifier = Modifier
) {
    // v0.68.95（F-05）：时间/日期从分钟 ticker 派生（原各自时钟循环 + 冻结日期）
    val nowMillis by rememberNowMillis()
    val timeText = remember(nowMillis) { currentTimeText(nowMillis) }
    val dateText = remember(nowMillis) { currentDateText(nowMillis) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = CoverHeroLandscapeTopPadding, start = 32.dp, end = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TrackedText(
                text = dateText,
                fontSize = AILauncherType.Caption.fontSize,
                tracking = 4.dp,
                color = palette.title.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = timeText.split(":")
                Text(style = briefingTextShadowStyle,
                    text = "${parts.getOrElse(0) { "--" }}:${parts.getOrElse(1) { "--" }}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = palette.title,
                    lineHeight = 64.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(style = briefingTextShadowStyle,
                    text = summary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = AILauncherType.Title.fontSize,
                    color = palette.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(style = briefingTextShadowStyle,
                text = weatherDesc,
                fontSize = AILauncherType.Callout.fontSize,
                color = palette.title.copy(alpha = 0.6f)
            )
        }
    }
}

