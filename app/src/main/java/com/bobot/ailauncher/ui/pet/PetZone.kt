package com.bobot.ailauncher.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.glass.LocalGlassBackdrop
import com.bobot.ailauncher.ui.home.BriefMeetingRow
import com.bobot.ailauncher.ui.home.CoverHero
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.theme.AILauncherColors
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
 * 宠物区（封面模式）：杂志 hero（简报区）+ 简报会行 + TODAY 数据管线。
 * 放在首页顶栏下方。
 *
 * v0.68.99（主页重构 R-1）：静态主页只剩简报 + Dock 两层——意图胶囊槽位
 * 撤除（改由 HomeScreen 在输入态下底部浮现），NextUp 紧凑条退役、会行以
 * BriefMeetingRow 形式长进简报；七仔点按语义见 PullUpDock
 *（2026-10-25 动作清单批起：点按开关动作清单，直进输入作废）。
 *
 * v0.68.96（F-01 退役）：非封面分支（宠物舞台 + 全量 TODAY 列表卡 + 编辑模式）
 * 整段删除——自 v0.68.44 杂志封面改版起唯一调用点写死封面模式，该分支无入口
 * 可达，Bob 2026-10-22 拍板定性退役。连带下线：舞台上的 ding/sort/carry 浮层、
 * 抚摸手势、长按刷新天气、十万火急舞台动效、编辑模式（含其 BackHandler 与
 * 版本更新指引文案）。七仔说话气泡的唯一调用点也在该分支内，SpeechBubble 本体
 * 保留在 PetCards.kt（.94 强玻璃刚落地，是否在封面形态补回气泡属产品决策，
 * 不在本批范围）。天气/通知/会议提醒等共享状态与 effect 全部保留，行为不变。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetZone(
    // v0.68.101（Stage 1 热修·单宠物原则，Bob 08:08 拍板）：讲述者七仔
    // 撤出简报——七仔只留 Dock 上沿坐姿一只（静态）与输入态意图卡片上沿
    // 的同一只（2026-10-25 意图卡片统一：HybridPet 换 perch），简报回归
    // 纯文字。原 hasUpgrade /
    // onUpgradeTap / onPetTap 随讲述者退役（hasUpgrade 从未有调用方传入，
    // 升级提醒走更新流的既有通道；进输入的唯一门是 Dock 坐姿七仔）。
    // 输入态标记：驱动简报紧凑（D-1）与横屏 hero 让位。
    inputActive: Boolean = false,
    // v0.68.99（R-1 修订）：输入态胶囊槽——恢复 .97 的 coverIntentSlot 位置
    // （hero 与会行之间，横屏收进 coverLeftFrac 左区）：静态时槽内容零高度、
    // 构图与无槽一致；输入态由 HomeScreen 填充 IntentEntry。早先的底部浮层
    // 方案在横屏会跨进 hero 区（矩阵 landphone 腿实测 overlap），流式槽位靠
    // 布局分区天然不重叠，且与 .97 几何基线同构。
    intentSlot: @Composable () -> Unit = {}
) {
    // alive-p1：普通通知不再经 deliverTick 开口——通知到达的呈现收敛到
    // PetRepository.handleIncomingNotification：普通通知只瞥视（不出声、
    // 不弹泡），重要通知走播报（先定向再说）。sayApp 函数本身保留
    // （合并/深夜降级规则不动），调用点随一期清单退役。
    // （deliverTick/deliverApp 流保留在仓库层，供其他呈现面消费。）
    // v0.30.0：天气微动作
    val weatherDesc by com.bobot.ailauncher.data.WeatherState.desc.collectAsState()
    // v0.46.0：天气自动触发——突变才播场景，不打扰
    var lastWeatherDesc by remember { mutableStateOf<String?>(null) }
    var weatherTriggersToday by remember { mutableIntStateOf(0) }
    var weatherTriggerDate by remember { mutableStateOf("") }
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

    fun canTriggerWeatherScene(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return false // 深夜不打扰
        val today = todayStr()
        if (weatherTriggerDate != today) {
            weatherTriggerDate = today
            weatherTriggersToday = 0
        }
        return weatherTriggersToday < 3 // 每天最多 3 次
    }

    fun onWeatherFetched(info: WeatherRepository.WeatherInfo?, allowTrigger: Boolean) {
        val desc = info?.desc
        val old = lastWeatherDesc
        lastWeatherDesc = desc
        WeatherState.updateFull(info)
        if (desc == null || !allowTrigger || !canTriggerWeatherScene()) return
        val wasBad = old != null && isBadWeather(old)
        val isBad = isBadWeather(desc)
        val isGood = isGoodWeather(desc)
        // v0.68.101：天气场景演示（下雨/天晴姿态）随讲述者七仔退役，
        // 开口提醒保留（语音状态机，其他呈现面可消费）。
        when {
            old != null && !wasBad && isBad -> {
                PetRepository.say("下雨啦，出门记得带伞！")
                weatherTriggersToday++
            }
            old != null && wasBad && isGood -> {
                PetRepository.say("雨停啦，太阳出来了！")
                weatherTriggersToday++
            }
        }
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
                PetRepository.say("早上好！今天${w.desc}，${w.temp}°")
            }
        }
    }
    val context = LocalContext.current
    // v0.67.6：删除 15 分钟日程轮询（省电原则）
    // 改为一次性任务：TodaySection 数据管线的会议提醒调度（开会前 30/15 分钟
    // 各一次）；日历数据变化时重算，边界刷新天然兜底（防任务被杀）
    val notifiedCalendarKeys = remember { mutableSetOf<String>() }
    // 七仔会议提醒（含去重：同一场同一档只提醒一次）
    // v0.67.1：会议提醒不再弹模态 Capsule——只走七仔说话 + TODAY 数据，
    // 产品原则：重要信息不用模态弹窗
    fun remindMeeting(e: CalEvent, tag: String) {
        // 没权限就静默跳过
        if (!CalendarRepository.hasCalendarPermission(context)) return
        val key = "${e.title}|${e.begin}|$tag"
        if (!notifiedCalendarKeys.add(key)) return
        val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA)
            .format(Date(e.begin))
        val title = e.title.ifBlank { "（无标题）" }
        // 七仔开口（v0.68.101：看日程场景随讲述者七仔退役）
        PetRepository.say("$timeStr 有日程：$title")
        // v0.51.2：日历只走 TODAY，不再走整理员（避免多重重复）
    }
    // 封面紧凑条点按（文案承诺"点一下加入"，必须真入会/打开日程，不能只提醒）：
    // 实现已抽为文件级 joinCalendarEvent（意图入口建议面的会议行共用同一条入会
    // 路径，三级路径说明见该函数）；不走 remindMeeting 的去重（点按每次都要有结果）。
    fun joinMeeting(e: CalEvent) = joinCalendarEvent(context, e)
    // v0.68.101：点按果冻随讲述者七仔退役（jellyTick 删除）

    // v0.68.44：封面模式状态（hero 标题/下个会议）
    var coverSummary by remember { mutableStateOf("难得清闲") }
    var coverNextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // 意图改进一批：下一会议同步到共享 holder——首页意图入口的零打字建议面要读
    // （会议数据原本只活在本 Composable 的状态里，IntentEntry 够不着）；
    // PetZone 离场时清空，避免建议面读到陈旧会议。TODAY 数据流本身不改。
    LaunchedEffect(coverNextEvent) { TodayEventsHolder.update(coverNextEvent) }
    // v0.68.93（布局与交互 Loop 一批④）：会议呈现状态与数据同源上报——呈现点
    // 的渲染条件就是 coverNextEvent != null（.99 起为简报会行 BriefMeetingRow，
    // 原 NextUpStrip），呈现的事件 id 与 coverNextEvent.eventId 同拍写入/清空，
    // 供建议面按 id 去重（去重契约随呈现点迁移不变）。
    LaunchedEffect(coverNextEvent) {
        TodayEventsHolder.updatePresentedNextEventId(coverNextEvent?.eventId)
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

    // 简报自适应字色（Bob 2026-10-25 21:17 拍板：自适应字色、去纱）：
    // 简报区不再靠罩纱保可读性，改靠字色自适应——纱平时不可见（淡出），
    // 只在对比度不够／读不到壁纸时回退 347 纱（淡入），边缘问题从根上消失。
    // 亮度只看简报区那一带：玻璃层已读的 48×48 缩略图顶部带（与 PetZone
    // 本身高度对应的带数），不用整张壁纸平均；与时间段配色（CoverPalette）
    // 的关系：本处只覆盖传给 CoverHero／BriefMeetingRow 的 palette，简报区
    // 字色以壁纸亮度为准，其他区域的时间段配色不动；intentSlot 与 Dock
    // 不经过本处，一律不动。
    val glassState = LocalGlassBackdrop.current
    var briefingColumnHPx by remember { mutableIntStateOf(0) }
    val briefingBackdropHPx = glassState?.backdropHeightPx ?: 0
    val briefingZoneFraction =
        if (briefingBackdropHPx > 0 && briefingColumnHPx > 0)
            (briefingColumnHPx.toFloat() / briefingBackdropHPx).coerceIn(0f, 1f) else 0f
    val briefingZoneBands = remember(glassState?.backdropLuminance, briefingZoneFraction) {
        BriefingAdaptiveText.bandsForZone(
            glassState?.backdropLuminance ?: emptyList(), briefingZoneFraction
        )
    }
    var briefingPrevDecision by remember { mutableStateOf<BriefingTextMode?>(null) }
    val briefingDecision = remember(briefingZoneBands, briefingPrevDecision) {
        BriefingAdaptiveText.decide(briefingZoneBands, briefingPrevDecision)
    }
    LaunchedEffect(briefingDecision) { briefingPrevDecision = briefingDecision }
    val briefingScrimFallback = briefingDecision == BriefingTextMode.FALLBACK_SCRIM
    // 纱淡入淡出：决策翻转时不硬切（首帧壁纸加载完成、换壁纸时各一次），300ms 过渡。
    val briefingScrimAlpha by animateFloatAsState(
        targetValue = if (briefingScrimFallback) 1f else 0f,
        animationSpec = tween(300), label = "briefingScrim"
    )
    val briefingPalette = when (briefingDecision) {
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
    // 刊头金：亮底上原金色对比不足，切深铜金保品牌感；暗底与回退态沿用现行金。
    val briefingAccent = when (briefingDecision) {
        BriefingTextMode.DARK_TEXT -> Color(BriefingAdaptiveText.DARK_ACCENT_ARGB)
        BriefingTextMode.LIGHT_TEXT -> Color(BriefingAdaptiveText.LIGHT_ACCENT_ARGB)
        BriefingTextMode.FALLBACK_SCRIM -> AILauncherColors.current.Accent
    }

    // v0.68.44（Bob）：封面模式——杂志 hero + 紧凑条；TodaySection 只跑数据不上 UI
    // v0.68.45：恢复点七仔快捷面板（Bob：快捷服务别删）；无会议时紧凑条隐藏（Bob：别和 hero 重复）
    // v0.68.47：装备环包进宠物槽位、以宠物为中心（Bob：散落不优雅）
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
        // v0.68.101（D-4）：简报罩纱——原全屏渐变（MainScreen 0.75→0.45）
        // 到会议行高度只剩弱洗底，金色眉题压在繁忙壁纸上对比不足。本层
        // 渐变衬底跟随简报内容（hero+槽位+会行）延伸：上部轻洗不抢简报
        // 的通透、中下部持平浓度托住会议行、到会行下缘之下再渐隐收尾。
        // 纯渐变无边框无圆角——不做成卡片、不复活 NextUp 卡形态。
        // v0.68.102（310 冻结修复）：渐变改挂 Column 自身 background，不再
        // 用独立 Box + matchParentSize 覆层——二分实证（probe-freeze-*）：
        // 该覆层与 inputCompact 同在时，进输入态 UI 确定性冻结（摘其一即
        // 活）；渐变作为内容容器自身背景，视觉口径不变（同色标、同范围：
        // 容器高度本就由内容决定），动画路径上少一个跟随测量的节点。
        // 简报罩纱框线修复（Bob 2026-10-25 真机截图）：PetZone 是 HomeScreen
        // 根 Column 的第一个孩子，根 Column 的顶距（竖屏 16dp、横屏
        // max(16dp, 状态栏高)）与两侧 displayCutout inset 把罩纱 Column 框在
        // 内侧；原 background 只画到 Column 边界，边界外侧只有 MainScreen
        // 罩纱，边界处 alpha 台阶约 0.22——真机上就是顶部与左侧的两道框线
        // （横屏挖孔在侧边故左右都有线，竖屏侧边 inset 为 0 故只见顶线）。
        // 修法：罩纱改 drawBehind 出界绘制——上 bleed 根 Column 顶距、左右
        // bleed 两侧 cutout inset，罩纱铺满到屏边，边界处不再有台阶；纵向
        // 渐变映射不变（startY=0/endY=Column 高，出界区按首色平铺），内容
        // 上的浓度与之前一字不动；drawBehind 不引入布局节点，v0.68.102 的
        // 冻结修复口径不受影响；字号、间距、布局、手势一字不动。
        // 简报渐变重设计（Bob 2026-10-25 19:29 真机实拍）：旧曲线 0.86→1.0
        // 一段线性直落、收尾点就在问候语下 26dp 处——问候语正好压在过渡带
        // 上；且线性段两端斜率突变（平台→斜坡、斜坡→0）在人眼里形成 Mach
        // 带，看起来就是一道横向明暗分界。345 取消全屏罩纱后，简报罩纱成
        // 为壁纸上唯一一层纱，边缘比以前更显眼。
        // 新曲线：平台期盖住全部简报文字（含问候语＋26dp 尾巴）；收尾改在
        // 内容下方 84dp 的空壁纸区做 smoothstep 缓动（两端斜率为零，无
        // Mach 带）、精确归零；上升段同样用 smoothstep 采样，消除 0.55 处
        // 平台拐点的带。峰值 0.46 与顶部 0.22 不变，文字对比度与之前一致；
        // 下 bleed 只落在 PetZone 下方的空壁纸区（正常态其下无内容，Dock
        // 在屏底另层绘制），横屏 Dock 顶部是否吃到尾巴尖列入真机验证。
        val scrimColor = AILauncherColors.current.Background
        val veilDensity = LocalDensity.current
        val veilLayoutDirection = LocalLayoutDirection.current
        val veilStatusBarTopDp =
            with(veilDensity) { WindowInsets.statusBars.getTop(veilDensity).toDp() }
        val veilTopBleedPx = with(veilDensity) {
            (if (isLandscapeCover) maxOf(16.dp, veilStatusBarTopDp) else 16.dp).toPx()
        }
        val veilStartBleedPx =
            WindowInsets.displayCutout.getLeft(veilDensity, veilLayoutDirection).toFloat()
        val veilEndBleedPx =
            WindowInsets.displayCutout.getRight(veilDensity, veilLayoutDirection).toFloat()
        // 收尾羽化距离：内容下方空壁纸区的 smoothstep 缓动长度。
        val veilTailPx = with(veilDensity) { 84.dp.toPx() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { briefingColumnHPx = it.size.height }
                .drawBehind {
                    // 自适应字色激活时纱不画（淡出到 0 后直接跳过绘制）；回退态
                    // 画 347 渐变（淡入）。alpha 乘在每个色标上，动画期间逐帧重绘。
                    if (briefingScrimAlpha <= 0f) return@drawBehind
                    // smoothstep：两端斜率为零，平台↔缓动、缓动↔0 的接头处
                    // 无斜率突变，故无 Mach 带；分段线性直接拼是旧曲线的病根。
                    fun smooth(t: Float): Float = t * t * (3f - 2f * t)
                    // 注意：buildList 的 lambda 接收者是 MutableList，其自带
                    // size 属性会遮住 DrawScope.size，故列高须先提到外层变量，
                    // 否则 buildList 内写 size.height 编译失败（CI 实测）。
                    val columnH = size.height
                    val gradLen = columnH + veilTailPx
                    fun stopAt(y: Float, alpha: Float) =
                        (y / gradLen).coerceIn(0f, 1f) to
                            scrimColor.copy(alpha = alpha)
                    val riseEnd = 0.55f * columnH
                    val stops = buildList {
                        add(0f to scrimColor.copy(alpha = 0.22f * briefingScrimAlpha))
                        // 上升段：0.22→0.46，smoothstep 采样（端点与旧值一致）。
                        for (t in listOf(0.25f, 0.5f, 0.75f)) {
                            add(stopAt(t * riseEnd, (0.22f + 0.24f * smooth(t)) * briefingScrimAlpha))
                        }
                        // 平台期：盖住全部简报文字＋26dp 尾巴，浓度与旧值一致。
                        add(stopAt(riseEnd, 0.46f * briefingScrimAlpha))
                        add(stopAt(columnH, 0.46f * briefingScrimAlpha))
                        // 收尾段：内容下方空壁纸区，0.46→0，smoothstep 采样、
                        // 精确归零；问候语已不在过渡带内。
                        for (t in listOf(
                            0.125f, 0.25f, 0.375f, 0.5f,
                            0.625f, 0.75f, 0.875f
                        )) {
                            add(
                                stopAt(
                                    columnH + t * veilTailPx,
                                    0.46f * (1f - smooth(t)) * briefingScrimAlpha
                                )
                            )
                        }
                        add(1f to scrimColor.copy(alpha = 0.0f))
                    }
                    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        *stops.toTypedArray(),
                        startY = 0f,
                        endY = gradLen
                    )
                    drawRect(
                        brush = brush,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            -veilStartBleedPx,
                            -veilTopBleedPx
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            size.width + veilStartBleedPx + veilEndBleedPx,
                            size.height + veilTopBleedPx + veilTailPx
                        )
                    )
                }
        ) {
        // v0.68.101（D-1）：横屏输入态 hero 整体让位——横屏 IME 抬升后
        // 可用高度只剩约四成，左区只装胶囊堆栈；竖屏走各模板 inputCompact。
        if (!(isLandscapeCover && inputActive)) {
            CoverHero(
                summary = coverSummary,
                weatherDesc = weatherDesc ?: "",
                palette = briefingPalette,
                accent = briefingAccent,
                inputCompact = inputActive
            )
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
        // v0.68.99（主页重构 R-1）：简报会行——有会议才显示，无会时 hero 已有
        // "难得清闲"，不重复。横屏走 coverLeftFrac 左区分区（.97 成果延续）。
        if (coverNextEvent != null) {
            Spacer(modifier = Modifier.height(14.dp))
            BriefMeetingRow(
                event = coverNextEvent!!,
                palette = briefingPalette,
                accent = briefingAccent,
                onJoin = { e -> joinMeeting(e) },
                modifier = Modifier.fillMaxWidth(coverLeftFrac)
            )
        }
        // 罩纱尾巴：会行（或简报末尾）之下留一段渐隐距离，衬底在此收尾
        Spacer(modifier = Modifier.height(26.dp))
        }  // Column 结束
    }  // v0.68.74：BoxWithConstraints 结束
    // 数据源：只跑 effect、上报，不渲染（TodaySection 数据管线）
    TodaySection(
        onMeetingRemind = { e, tag -> remindMeeting(e, tag) },
        onSummaryChange = { coverSummary = it },
        onNextEventChange = { coverNextEvent = it }
    )
}

/** 思考中的三个跳动点（v0.68.101：放开给输入态伴随七仔共用同一语言） */
@Composable
internal fun ThinkDots(modifier: Modifier = Modifier) {
    // R3-D：系统减动效时三点静止（只表"思考中"，不跳动）
    val reduceMotion = com.bobot.ailauncher.ui.theme.rememberReduceMotion()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                    dampingRatio = 0.6f
                ),
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

/**
 * v0.46.0：天气好坏分类（用于自动触发场景）。
 * 坏=降水类；好=晴/多云；阴/雾为中性（转好时播晴，转坏时不触发）。
 */
private fun isBadWeather(desc: String?): Boolean =
    desc == "小雨" || desc == "雨" || desc == "雪" || desc == "雷阵雨"

private fun isGoodWeather(desc: String?): Boolean =
    desc == "晴" || desc == "多云"

// v0.67.2：天气描述 → 宠物场景（一眼可区分）
private fun weatherSceneFor(desc: String?): QizaiScene = when (desc) {
    "晴" -> QizaiScene.WEATHER_SUN
    "多云" -> QizaiScene.WEATHER_CLOUDY
    "阴", "雾", "霾" -> QizaiScene.WEATHER_OVERCAST
    "小雨", "雨", "雪", "雷阵雨", "中雨", "大雨" -> QizaiScene.WEATHER_RAIN
    else -> QizaiScene.WEATHER_SUN
}

// 批B ②：CalEvent 已随日历查询下沉 data 层（com.bobot.ailauncher.data.CalEvent）

/**
 * v0.58.0（需求4）：从日历事件的 location/description/title 提取在线会议链接。
 * 返回 null 表示没有识别到会议链接。
 */
private val MEETING_DOMAINS = listOf(
    "meeting.tencent.com", "wemeet", "voovmeeting.com",
    "zoom.us", "zoom.com",
    "teams.microsoft.com", "teams.live.com",
    "meeting.feishu.cn", "feishu.cn",
    "webex.com",
    "dingtalk.com"
)
private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

private fun extractMeetingUrl(event: CalEvent): String? {
    val combined = "${event.location}\n${event.description}\n${event.title}"
    return URL_REGEX.findAll(combined)
        .map { it.value.trimEnd('.', ',', ')', ']', '!', '；', '。') }
        .firstOrNull { url ->
            val lower = url.lowercase()
            MEETING_DOMAINS.any { lower.contains(it) }
        }
}

/**
 * v0.68.101（D-5）：入会防多发守卫（纯逻辑，单测锁死）——同一事件在
 * 窗口内重复触发（连点/重组竞态）只放行第一次。真机录屏里「加入」后
 * 浏览器与主页来回闪烁，代码侧先把「本应用多次发放意图」这一路堵死：
 * joinCalendarEvent 本身是命令式一次性链（无 Effect 重触发、无重试
 * 循环），守卫再兜住手速连点与任何未来的重复调用点。
 */
internal class JoinGuard(private val windowMs: Long = 2_000L) {
    private var lastKey: String? = null
    private var lastMs: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldFire(key: String, nowMs: Long): Boolean {
        if (key == lastKey && nowMs - lastMs < windowMs) return false
        lastKey = key
        lastMs = nowMs
        return true
    }
}

private val joinGuard = JoinGuard()

/**
 * 点按入会（封面紧凑条与意图入口建议面的会议行共用同一条路径，2026-10-21 抽出）：
 * 1) 事件里有可解析的在线会议链接 → 直开链接（会议 App/浏览器接管）；
 * 2) 无链接 → 打开日历 App / 事件详情（三级兜底，与非封面 TODAY 会议行同款）；
 * 3) 全失败 → 七仔说话兜底，绝不无反应。
 */
fun joinCalendarEvent(context: android.content.Context, e: CalEvent) {
    // D-5：窗口内重复触发直接吞掉（见 JoinGuard）
    val joinKey = "${e.eventId}|${extractMeetingUrl(e) ?: ""}"
    if (!joinGuard.shouldFire(joinKey, System.currentTimeMillis())) return
    val url = getValidMeetingUrl(context, e)
    if (url != null) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) { /* 链接打开失败 → 落日历兜底 */ }
    }
    var opened = false
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_MAIN
        ).addCategory(
            android.content.Intent.CATEGORY_APP_CALENDAR
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            opened = true
        }
    } catch (_: Exception) { }
    if (!opened && e.eventId > 0) {
        try {
            val uri = android.net.Uri.parse(
                "content://com.android.calendar/events/${e.eventId}"
            )
            val viewIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW, uri
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                opened = true
            }
        } catch (_: Exception) { }
    }
    if (!opened) {
        PetRepository.say("没找到日历应用")
    }
}

// v0.63.1：验证会议 URL 有效且系统能解析，避免无效 URL 弹出应用选择器
internal fun getValidMeetingUrl(context: android.content.Context, event: CalEvent): String? {
    val url = extractMeetingUrl(event) ?: return null
    if (url.isBlank()) return null
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    )
    return if (intent.resolveActivity(context.packageManager) != null) url else null
}

// 批B ②：hasCalendarPermission / loadTodayEvents 已下沉 data 层 CalendarRepository
