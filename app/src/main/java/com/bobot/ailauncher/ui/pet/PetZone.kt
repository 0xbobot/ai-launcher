package com.bobot.ailauncher.ui.pet
import com.bobot.ailauncher.ui.island.IslandToasts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.ui.glass.GlassPolicy
import com.bobot.ailauncher.ui.glass.LocalGlassBackdrop
import com.bobot.ailauncher.ui.home.CoverHero
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.PetMotion
import com.bobot.ailauncher.ui.theme.coverPaletteFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.data.WeatherState
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 宠物区（封面模式）：杂志 hero（简报区）+ TODAY 数据管线。
 * 放在首页顶栏下方。
 *
 * 2026-11-11（Bob 亲口）：简报会行（BriefMeetingRow）拿掉——会议只走
 * 日历卡（会前 30 分钟/非会议日程 2 小时出现），不再在简报里提前预览。
 * coverNextEvent / TodaySection 数据管线不动（日历卡还靠它）。
 * v0.68.99（主页重构 R-1）：静态主页只剩简报 + Dock 两层——意图胶囊槽位
 * 撤除（改由 HomeScreen 在输入态下底部浮现），NextUp 紧凑条退役、会行以
 * BriefMeetingRow 形式长进简报。
 *
 * v0.68.96（F-01 退役）：非封面分支（宠物舞台 + 全量 TODAY 列表卡 + 编辑模式）
 * 整段删除——自 v0.68.44 杂志封面改版起唯一调用点写死封面模式，该分支无入口
 * 可达，Bob 2026-10-22 拍板定性退役。连带下线：舞台上的 ding/sort/carry 浮层、
 * 抚摸手势、长按刷新天气、十万火急舞台动效、编辑模式（含其 BackHandler 与
 * 版本更新指引文案）。天气/通知/会议提醒等共享状态与 effect 全部保留，行为不变。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetZone(
    // 删宠物批起本文件只剩简报/天气/会议容器，不再有宠物身体与宠物说话。
    // 输入态标记：驱动简报紧凑（D-1）与横屏 hero 让位。
    inputActive: Boolean = false,
    // v0.68.99（R-1 修订）：输入态胶囊槽——恢复 .97 的 coverIntentSlot 位置
    // （hero 与会行之间，横屏收进 coverLeftFrac 左区）：静态时槽内容零高度、
    // 构图与无槽一致；输入态由 HomeScreen 填充 IntentEntry。早先的底部浮层
    // 方案在横屏会跨进 hero 区（矩阵 landphone 腿实测 overlap），流式槽位靠
    // 布局分区天然不重叠，且与 .97 几何基线同构。
    intentSlot: @Composable () -> Unit = {},
    // 智能卡片（447）：叠层槽位——会行下方、独立一段；无卡时零高度，
    // 构图与无槽一致；输入态与简报一起淡出（卡片是首页内容，不是输入内容）。
    cardStack: @Composable () -> Unit = {}
) {
    // v0.30.0：天气微动作
    val weatherDesc by com.bobot.ailauncher.data.WeatherState.desc.collectAsState()
    // v0.46.0：天气自动触发——突变才播场景，不打扰
    var lastWeatherDesc by remember { mutableStateOf<String?>(null) }
    var morningBriefedDate by remember { mutableStateOf("") }
    // v0.67.6：天气缓存 TTL（省电原则）——60 分钟内不重复发网络请求
    var lastWeatherFetchMs by remember { mutableLongStateOf(0L) }
    var weatherInitDone by remember { mutableStateOf(false) }
    // v0.67.6：onResume 观察器（省电原则：只在用户看到时刷新）
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)


    // 天气只更新简报文字状态（WeatherState），不再有突变播报/场景。
    fun onWeatherFetched(info: WeatherRepository.WeatherInfo?, allowTrigger: Boolean) {
        lastWeatherDesc = info?.desc
        WeatherState.updateFull(info)
    }

    suspend fun fetchWeather(allowTrigger: Boolean) {
        val w = withContext(Dispatchers.IO) { WeatherRepository.fetchSync() }
        if (w != null) onWeatherFetched(w, allowTrigger)
    }
    // v0.67.6：天气 onResume + 缓存 TTL（省电原则）
    // 回桌面/亮屏时检查：60 分钟内查过直接用缓存，不发网络请求；
    // 首次只更新状态不播场景（allowTrigger=false），之后有突变才播
    LaunchedEffect(resumeTick) {
        val now = System.currentTimeMillis()
        if (!weatherInitDone || now - lastWeatherFetchMs >= 60 * 60 * 1000L) {
            lastWeatherFetchMs = now
            fetchWeather(allowTrigger = weatherInitDone)
            weatherInitDone = true
        }
    }

    // 早晨简报：6-10 点首次进入播一次今日天气
    // v0.68.101：原挥手场景演示随讲述者七仔退役，只保留天气播报本身
    LaunchedEffect(Unit) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val today = todayStr()
        if (hour in 6..10 && morningBriefedDate != today) {
            morningBriefedDate = today
            val w = withContext(Dispatchers.IO) { WeatherRepository.fetchSync() }
            if (w != null) {
                lastWeatherDesc = w.desc
                lastWeatherFetchMs = System.currentTimeMillis() // v0.67.6：计入 TTL，避免重复请求
                weatherInitDone = true
                WeatherState.updateFull(w)
            }
        }
    }
    val context = LocalContext.current
    // 智能卡片（第十三批，Bob 2026-11-10 定：双重关全改掉）：简报会行不再查
    // 日历连接器开关——会行跟着权限走（数据加载在 TodaySection 里只认
    // READ_CALENDAR），与数据加载一致。旧口径（447）在这里留了一道开关门，
    // 导致简报内部本身不一致：计数认权限、会行认开关。
    // v0.67.6：删除 15 分钟日程轮询（省电原则）
    // 改为一次性任务：TodaySection 数据管线的会议提醒调度（开会前 30/15 分钟
    // 各一次）；日历数据变化时重算，边界刷新天然兜底（防任务被杀）
    val notifiedCalendarKeys = remember { mutableSetOf<String>() }
    // v0.68.44：封面模式状态（hero 标题/下个会议）
    var coverSummary by remember { mutableStateOf("难得清闲") }
    var coverNextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // 意图改进一批：下一会议同步到共享 holder——首页意图入口的零打字建议面要读
    // （会议数据原本只活在本 Composable 的状态里，IntentEntry 够不着）；
    // PetZone 离场时清空，避免建议面读到陈旧会议。
    LaunchedEffect(coverNextEvent) {
        TodayEventsHolder.update(coverNextEvent)
    }
    // v0.68.93（布局与交互 Loop 一批④）：会议呈现状态与数据同源上报。
    // 2026-11-11：简报会行已拿掉（Bob），呈现点不存在；presentedNextEventId
    // 的写入暂时保留（当前无 live 读方，建议面暂无会议行），去重契约待新的
    // 呈现点出现时再定。recordCalendarUsed 仍按 nextEvent 非空记（沿用旧口径，
    // 设置连接器列表的“最后使用时间”用）。
    LaunchedEffect(coverNextEvent) {
        TodayEventsHolder.updatePresentedNextEventId(
            coverNextEvent?.eventId
        )
        if (coverNextEvent != null) {
            SmartCardRepository.recordCalendarUsed(context)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            TodayEventsHolder.update(null)
            TodayEventsHolder.updatePresentedNextEventId(null)
        }
    }
    // 批B ③：coverPaletteFor 是非组合纯函数，深浅由这里从 CompositionLocal 读后传入
    val coverIsDark = AILauncherColors.current.isDark
    // v0.68.95（F-05）：色板改为从分钟 ticker 按小时派生——此前无 key 的
    // remember{} 把首帧的小时与深浅模式冻结，跨时段/切换深色后色板不跟随。
    val coverNowMillis by rememberNowMillis()
    val coverHour = remember(coverNowMillis) {
        java.util.Calendar.getInstance().apply { timeInMillis = coverNowMillis }
            .get(java.util.Calendar.HOUR_OF_DAY)
    }
    val coverPalette = remember(coverHour, coverIsDark) {
        coverPaletteFor(coverHour, coverIsDark)
    }

    // 简报自适应字色（Bob 2026-10-25 21:17 拍板：自适应字色、去纱；
    // 2026-10-25 22:31 再令：罩纱彻底去掉，包括回退路径；
    // 2026-10-25 23:27 定 A＋B＋C：取样修根因＋字阴影兜底＋堵无带数据回退）：
    // 简报区不再靠罩纱保可读性，改靠字色自适应；回退态也不再画纱。
    // A 取样：只看字实际所在的左 40% 横向范围的顶部带（briefingLuminance，
    // 与 PetZone 本身高度对应的带数、连续加权），不用整幅宽带均值——
    // 23:14 白字案就是整幅宽均值被右上深红块拉暗而误判暗底。
    // 与时间段配色（CoverPalette）的关系：本处只覆盖传给 CoverHero 的 palette，
    // 简报区字色以壁纸亮度为准，其他区域的时间段配色不动；intentSlot 与 Dock
    // 不经过本处，一律不动。
    val glassState = LocalGlassBackdrop.current
    var briefingColumnHPx by remember { mutableIntStateOf(0) }
    val briefingBackdropHPx = glassState?.backdropHeightPx ?: 0
    val briefingZoneFraction =
        if (briefingBackdropHPx > 0 && briefingColumnHPx > 0)
            (briefingColumnHPx.toFloat() / briefingBackdropHPx).coerceIn(0f, 1f) else 0f
    val briefingZoneBands = remember(glassState?.briefingLuminance, briefingZoneFraction) {
        BriefingAdaptiveText.zoneBandsForZone(
            glassState?.briefingLuminance ?: emptyList(), briefingZoneFraction
        )
    }
    var briefingPrevDecision by remember { mutableStateOf<BriefingTextMode?>(null) }
    // C 的锚点：上一次已算出的字色档，只由 DARK/LIGHT 写入，回退态不覆盖。
    var briefingLastPalette by remember { mutableStateOf<BriefingTextMode?>(null) }
    val briefingDecision = remember(briefingZoneBands, briefingPrevDecision) {
        BriefingAdaptiveText.decide(briefingZoneBands, briefingPrevDecision)
    }
    LaunchedEffect(briefingDecision) {
        briefingPrevDecision = briefingDecision
        if (briefingDecision == BriefingTextMode.DARK_TEXT ||
            briefingDecision == BriefingTextMode.LIGHT_TEXT
        ) briefingLastPalette = briefingDecision
    }
    // 回退映射（decide() 仍返回 FALLBACK_SCRIM 信号，渲染层在此映射）：
    // - 带非空但无档严格达标（双峰带等）：沿用 351 做法，选加权均值下对比度更高的档；
    // - 无带数据（壁纸读不到／首帧未加载）：C——不再回退时间段调色板，
    //   改沿用上次档；首帧尚无数据、无历史时默认浅色档，理由：①首帧只是闪现，
    //   壁纸一旦读到就立即按实测重算；②字级阴影是深色影，浅色档误判在浅底上
    //   仍有阴影边缘可救，而深色档误判在暗底上无阴影可救，故盲选浅色更安全。
    // 迟滞（briefingPrevDecision）仍按 decide() 原值回写，不受映射影响。
    val briefingRenderMode = when {
        briefingDecision == BriefingTextMode.FALLBACK_SCRIM &&
            briefingZoneBands.isNotEmpty() -> {
            val totalWeight = briefingZoneBands.sumOf { it.weight }
            val zoneMean = if (totalWeight > 0.0) {
                briefingZoneBands.sumOf { it.mean * it.weight } / totalWeight
            } else 0.0
            val darkLum =
                GlassPolicy.relativeLuminance(BriefingAdaptiveText.DARK_TITLE_ARGB.toInt())
            val lightLum =
                GlassPolicy.relativeLuminance(BriefingAdaptiveText.LIGHT_TITLE_ARGB.toInt())
            if (GlassPolicy.contrastRatio(darkLum, zoneMean) >=
                GlassPolicy.contrastRatio(lightLum, zoneMean)
            ) BriefingTextMode.DARK_TEXT else BriefingTextMode.LIGHT_TEXT
        }
        briefingDecision == BriefingTextMode.FALLBACK_SCRIM ->
            briefingLastPalette ?: BriefingTextMode.LIGHT_TEXT
        else -> briefingDecision
    }
    // FALLBACK_SCRIM 臂为防御性兜底：经上 mapping 后 briefingRenderMode 已无回退态。
    val briefingPalette = when (briefingRenderMode) {
        BriefingTextMode.DARK_TEXT -> coverPalette.copy(
            title = Color(BriefingAdaptiveText.DARK_TITLE_ARGB),
            body = Color(BriefingAdaptiveText.DARK_BODY_ARGB),
            divider = Color(BriefingAdaptiveText.DARK_DIVIDER_ARGB)
        )
        BriefingTextMode.LIGHT_TEXT -> coverPalette.copy(
            title = Color(BriefingAdaptiveText.LIGHT_TITLE_ARGB),
            body = Color(BriefingAdaptiveText.LIGHT_BODY_ARGB),
            divider = Color(BriefingAdaptiveText.LIGHT_DIVIDER_ARGB)
        )
        BriefingTextMode.FALLBACK_SCRIM -> coverPalette
    }
    // 刊头金：亮底上原金色对比不足，切深铜金保品牌感；暗底与无数据回退态沿用现行金。
    val briefingAccent = when (briefingRenderMode) {
        BriefingTextMode.DARK_TEXT -> Color(BriefingAdaptiveText.DARK_ACCENT_ARGB)
        BriefingTextMode.LIGHT_TEXT -> Color(BriefingAdaptiveText.LIGHT_ACCENT_ARGB)
        BriefingTextMode.FALLBACK_SCRIM -> AILauncherColors.current.Accent
    }

    // v0.68.44（Bob）：封面模式——杂志 hero + 紧凑条；TodaySection 只跑数据不上 UI
    // v0.68.45：恢复快捷面板（Bob：快捷服务别删）；无会议时紧凑条隐藏（Bob：别和 hero 重复）
    // v0.68.47：装备环收进简报槽位（Bob：散落不优雅）
    // v0.68.74（Bob）：横屏时紧凑条收窄放左侧，避免和 Dock 重叠
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val isLandscapeCover = maxWidth > maxHeight
        // v0.68.97（布局普查批，Bob 2026-10-22 横屏截图）：横屏封面分区——右侧
        // 恒为 Dock 面板区（D1 面板实测宽 312dp＝4×56 图标+间距+内距，D2 网格
        // 270dp 更窄；加右侧间隙约 28dp 与安全余量），左区内容右缘对齐、
        // 永不与面板相交（矩阵横屏腿几何断言常驻回归）。竖屏 frac=1，构图不变。
        // v0.68.99（R-1）：D1 面板因七仔格变宽，预留曾放到 432；
        // v0.68.101：行内格移除（七仔改坐面板上沿，不占行宽），D1 面板
        // 回到 4 应用位 ≈312dp，预留回 360（312 + 约 48 间隙，.97 同量级）。
        val coverLeftFrac = if (isLandscapeCover) {
            ((maxWidth - 360.dp) / maxWidth).coerceIn(0.5f, 0.72f)
        } else {
            1f
        }
        // 简报罩纱简史（已退役，勿复活）：101 衬底 → 102 挂 Column
        // background → 343 出界绘制修框线 → 347 渐变重设计 → 350 自适应
        // 字色（平时不画、回退画纱）。2026-10-25 22:31 Bob 真机反馈：
        // 繁忙壁纸下回退纱仍可见，令罩纱彻底去掉（含回退路径）——
        // 本 Column 自此不再画任何衬底，字色可读性只靠自适应档
        // （含回退改选对比度更高档）。布局、字号、间距、手势一字不动。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { briefingColumnHPx = it.size.height }
        ) {
        // v0.68.101（D-1）：横屏输入态 hero 整体让位——横屏 IME 抬升后
        // 可用高度只剩约四成，左区只装胶囊堆栈；竖屏走各模板 inputCompact。
        // 输入态专注壳·A：专注态简报淡出——fade-only 150ms，不缩小、
        // 不移位；退场完成后零高度、不留占位。进态路径不许高度动画
        // （310 冻结教训）。卡片槽位不在本处，不受影响。
        // （本改动在 PetZone.kt：简报节点宿主；HomeScreen 侧无此节点可改。）
        if (!(isLandscapeCover && inputActive)) {
            // 竖屏区块切换（2026-10-27）：竖屏进出用 None——英雄区同帧
            // 整体显隐，不与 Dock 区／意图区半透明重叠；横屏沿用 fade。
            androidx.compose.animation.AnimatedVisibility(
                visible = !inputActive,
                enter = if (isLandscapeCover) androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(
                        com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                        easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                    )
                ) else androidx.compose.animation.EnterTransition.None,
                exit = if (isLandscapeCover) androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(
                        com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                        easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                    )
                ) else androidx.compose.animation.ExitTransition.None
            ) {
                CoverHero(
                    summary = coverSummary,
                    weatherDesc = weatherDesc ?: "",
                    palette = briefingPalette,
                    accent = briefingAccent,
                    inputCompact = inputActive
                )
            }
        }
        // v0.68.99（R-1 修订）：输入态胶囊槽（.97 位置：hero 与会行之间；
        // 横屏只占左侧区，右端不滑入面板下——.97 原注）
        if (isLandscapeCover) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(coverLeftFrac)
            ) {
                intentSlot()
            }
        } else {
            intentSlot()
        }
        // 2026-11-11（Bob 亲口）：简报里的日历提醒（NextUp 会行）直接拿掉——
        // 会议只在会前 30 分钟（非会议日程 2 小时）以日历卡出现，不再在简报里
        // 提前预览。coverNextEvent / TodaySection 数据管线不动（日历卡还靠它），
        // BriefMeetingRow 定义保留（CoverHero.kt），暂无调用点。
        // 智能卡片（447）：叠层槽位——简报下方、独立一段，与简报一起在输入态
        // 淡出（竖屏区块切换用 None、横屏沿用 fade，与会行同款）；无卡时
        // SmartCardStackHost 零高度、不留占位。
        androidx.compose.animation.AnimatedVisibility(
            visible = !inputActive,
            enter = if (isLandscapeCover) androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                    easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                )
            ) else androidx.compose.animation.EnterTransition.None,
            exit = if (isLandscapeCover) androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                    easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                )
            ) else androidx.compose.animation.ExitTransition.None
        ) {
            if (isLandscapeCover) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth(coverLeftFrac)
                ) {
                    cardStack()
                }
            } else {
                cardStack()
            }
        }
        // 输入态专注·集成：专注态下末尾留白与列尾气泡宿主均不挂载——
        // 留白会顶起卡片、破坏卡片与键盘的 0dp 贴合；气泡改挂意图卡片
        // 覆盖层（卡片宠物上方，见 IntentEntry），单挂载点契约不变。
        // 竖屏区块切换（2026-10-27）：竖屏 mode 回落即整块消失，无
        // 翻转点复现、无交叉淡化；横屏沿用场景换口径。
        if (!inputActive) {
            // 末尾留白 26dp（原罩纱尾巴间距保留，纱已删，布局不动）
            Spacer(modifier = Modifier.height(24.dp))
            // 列尾留白保留、布局不动。
        }
        }  // Column 结束
    }  // v0.68.74：BoxWithConstraints 结束
    // 数据源：只跑 effect、上报，不渲染（TodaySection 数据管线）
    TodaySection(
        onSummaryChange = { coverSummary = it },
        onNextEventChange = { coverNextEvent = it }
    )
}

/** 思考中的三个跳动点 */
@Composable
internal fun ThinkDots(modifier: Modifier = Modifier) {
    // R3-D：系统减动效时三点静止（只表"思考中"，不跳动）
    val reduceMotion = com.bobot.ailauncher.ui.theme.rememberReduceMotion()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            var up by remember { mutableStateOf(false) }
            LaunchedEffect(reduceMotion) {
                if (reduceMotion) return@LaunchedEffect
                delay(i * 150L)
                while (true) {
                    up = !up
                    delay(450)
                }
            }
            val dy by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (up) -9f else 0f,
                animationSpec = PetMotion.PetDot,
                label = "dot$i"
            )
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, with(density) { dy.dp.toPx() }.roundToInt()) }
                    .size(9.dp)
                    .background(Color.White, RoundedCornerShape(99.dp))
            )
        }
    }
}



// 批B ②：CalEvent 已随日历查询下沉 data 层（com.bobot.ailauncher.data.CalEvent）

/**
 * v0.58.0（需求4）：从日历事件的 location/description/title 提取在线会议链接。
 * 返回 null 表示没有识别到会议链接。
 * 实现在 data 层（CalendarMeeting.extractMeetingUrl，第十二批下沉）：
 * 日历智能卡片与简报会行共用同一条链路与同一份域名白名单。
 */
private fun extractMeetingUrl(event: CalEvent): String? =
    com.bobot.ailauncher.data.extractMeetingUrl(event)

// 入会防多发守卫在 data 层（CalendarMeeting.JoinGuard，第十六批下沉）：
// 会行路径与日历卡「加入会议」动作各用一个实例、同一窗口（2 秒）。
private val joinGuard = com.bobot.ailauncher.data.JoinGuard()

/**
 * 点按入会（封面紧凑条与意图入口建议面的会议行共用同一条路径，2026-10-21 抽出）：
 * 1) 事件里有可解析的在线会议链接 → 直开链接（会议 App/浏览器接管）；
 * 2) 无链接 → 打开日历 App / 事件详情（三级兜底，与非封面 TODAY 会议行同款）；
 * 3) 全失败 → 系统提示兜底，绝不无反应。
 * 打开链路在 data 层（CalendarMeeting.openCalendarEvent，第十二批下沉）：
 * 日历智能卡片的“加入会议/看日程”与这里同一条链。
 */
fun joinCalendarEvent(context: android.content.Context, e: CalEvent) {
    // D-5：窗口内重复触发直接吞掉（见 JoinGuard）
    val joinKey = "${e.eventId}|${extractMeetingUrl(e) ?: ""}"
    if (!joinGuard.shouldFire(joinKey, System.currentTimeMillis())) return
    val opened = com.bobot.ailauncher.data.openCalendarEvent(
        context, e.eventId, extractMeetingUrl(e)
    )
    if (!opened) {
        IslandToasts.show("没找到日历应用")
    }
}

// 批B ②：hasCalendarPermission / loadTodayEvents 已下沉 data 层 CalendarRepository
