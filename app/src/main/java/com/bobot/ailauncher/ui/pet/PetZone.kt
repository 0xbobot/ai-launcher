package com.bobot.ailauncher.ui.pet

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
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.home.CoverHero
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.home.NextUpStrip
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
 * 宠物区（封面模式）：杂志 hero + 意图胶囊槽位 + NextUp 紧凑条 + TODAY 数据管线。
 * 放在首页顶栏下方。
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
    // v0.29.0：升级提醒宠物化
    hasUpgrade: Boolean = false,
    onUpgradeTap: () -> Unit = {},
    // R2 UI（文档二 P0）：意图入口胶囊槽位——渲染在 hero 与紧凑条之间；
    // 状态由 HomeScreen 持有（IntentCoordinator），PetZone 只负责摆位
    coverIntentSlot: @Composable () -> Unit = {}
) {
    val mood by PetRepository.mood.collectAsState()
    // R3-C：意图反应姿态覆盖（对话感）——非空时优先于整理员 mood 渲染，通知流程不受影响
    val intentPose by PetRepository.intentPose.collectAsState()
    val intentThinking by PetRepository.intentThinking.collectAsState()
    val displayMood = intentPose ?: mood
    val mouth by PetRepository.mouth.collectAsState()
    // v0.19：语义状态——SLEEPY 时眼睛保持闭合（夜晚睡觉）
    val petState by PetRepository.petState.collectAsState()
    // v0.29.0：新通知送达
    val deliverTick by PetRepository.deliverTick.collectAsState()
    val deliverApp by PetRepository.deliverApp.collectAsState()
    // v0.43.0：七仔场景演示——新通知到达自动进 READING 等
    var demoScene by remember { mutableStateOf(QizaiScene.NONE) }
    // v0.51.0：新通知 → 七仔看手机 + 开口（徽标已删，消息统一进 Today 数据管线）
    // v0.56.0 M3：改用 sayApp（同一应用 30 秒合并 + 深夜/手势中降级）
    LaunchedEffect(deliverTick) {
        if (deliverTick > 0) {
            demoScene = QizaiScene.READING // 新通知 → 七仔拿起手机看
            // 七仔开口——只报应用名，不念内容（守门人分寸）
            deliverApp?.let { app ->
                PetRepository.sayApp(app)
            }
        }
    }
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
        when {
            old != null && !wasBad && isBad -> {
                demoScene = QizaiScene.WEATHER_RAIN // 转坏：下雨了
                PetRepository.say("下雨啦，出门记得带伞！")
                weatherTriggersToday++
            }
            old != null && wasBad && isGood -> {
                demoScene = QizaiScene.WEATHER_SUN // 转好：雨停天晴
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
    // v0.56.0 M4：先挥手打招呼（WAVE），再播天气
    LaunchedEffect(Unit) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val today = todayStr()
        if (hour in 6..10 && morningBriefedDate != today) {
            morningBriefedDate = today
            demoScene = QizaiScene.WAVE // 伸懒腰+挥手打招呼
            delay(2600) // 等挥手播完
            val w = withContext(Dispatchers.IO) { WeatherRepository.fetchSync() }
            if (w != null) {
                lastWeatherDesc = w.desc
                lastWeatherFetchMs = System.currentTimeMillis() // v0.67.6：计入 TTL，避免重复请求
                weatherInitDone = true
                WeatherState.updateFull(w)
                demoScene = weatherSceneFor(w.desc)
                PetRepository.say("早上好！今天${w.desc}，${w.temp}°")
            }
        }
    }
    var blinking by remember { mutableStateOf(false) }
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
        // 七仔开口 + 看日程场景
        PetRepository.say("$timeStr 有日程：$title")
        demoScene = QizaiScene.READING
        // v0.51.2：日历只走 TODAY，不再走整理员（避免多重重复）
    }
    // 封面紧凑条点按（文案承诺"点一下加入"，必须真入会/打开日程，不能只提醒）：
    // 实现已抽为文件级 joinCalendarEvent（意图入口建议面的会议行共用同一条入会
    // 路径，三级路径说明见该函数）；不走 remindMeeting 的去重（点按每次都要有结果）。
    fun joinMeeting(e: CalEvent) = joinCalendarEvent(context, e)
    // v0.25.6 P0：点按果冻（纯视觉反馈，Bob 拍板）
    var jellyTick by remember { mutableIntStateOf(0) }

    // v0.56.0 M1：眨眼已移入 PetView（8 秒 ± 3 秒随机），此处删除旧定时器

    // v0.68.44：封面模式状态（hero 标题/下个会议）
    var coverSummary by remember { mutableStateOf("难得清闲") }
    var coverNextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // 意图改进一批：下一会议同步到共享 holder——首页意图入口的零打字建议面要读
    // （会议数据原本只活在本 Composable 的状态里，IntentEntry 够不着）；
    // PetZone 离场时清空，避免建议面读到陈旧会议。TODAY 数据流本身不改。
    LaunchedEffect(coverNextEvent) { TodayEventsHolder.update(coverNextEvent) }
    // v0.68.93（布局与交互 Loop 一批④）：NextUp 呈现状态与数据同源上报——封面条
    // 的渲染条件就是 coverNextEvent != null（见下方 NextUpStrip 调用点），呈现的
    // 事件 id 与 coverNextEvent.eventId 同拍写入/清空，供建议面按 id 去重。
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

    // v0.68.44（Bob）：封面模式——杂志 hero + 紧凑条；TodaySection 只跑数据不上 UI
    // v0.68.45：恢复点七仔快捷面板（Bob：快捷服务别删）；无会议时紧凑条隐藏（Bob：别和 hero 重复）
    // v0.68.47：装备环包进宠物槽位、以宠物为中心（Bob：散落不优雅）
    // v0.68.74（Bob）：横屏时紧凑条收窄放左侧，避免和 Dock 重叠
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val isLandscapeCover = maxWidth > maxHeight
        // v0.68.97（布局普查批，Bob 2026-10-22 横屏截图）：横屏封面分区——右侧
        // 360dp 恒为 Dock 面板区（D1 面板实测宽 312dp＝4×56 图标+间距+内距，
        // D2 网格 270dp 更窄；加右侧间隙约 28dp 与安全余量），胶囊与 NextUp
        // 只占左侧区、右缘对齐，永不与面板相交（矩阵横屏腿几何断言常驻回归）。
        // 竖屏 frac=1，构图不变。
        val coverLeftFrac = if (isLandscapeCover) {
            ((maxWidth - 360.dp) / maxWidth).coerceIn(0.5f, 0.72f)
        } else {
            1f
        }
        Column(modifier = Modifier.fillMaxWidth()) {
        CoverHero(
            summary = coverSummary,
            weatherDesc = weatherDesc ?: "",
            palette = coverPalette,
            petSlot = {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 七仔本体（可点）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 体检批D ⑥：冒烟语义定位锚点（七仔本体，封面形态）
                            .testTag("pet_qizai")
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    // v0.68.79（Bob）：装备环已移除，全部以扇形为主——点七仔只做果冻+开心表情
                                    if (hasUpgrade) onUpgradeTap()
                                    else {
                                        jellyTick++
                                        PetRepository.setMoodHappyBrief()
                                    }
                                }
                            )
                    ) {
                        PetView(
                            mood = displayMood,
                            mouth = mouth,
                            blinking = blinking,
                            sleepy = petState == PetState.SLEEPY,
                            jellyTick = jellyTick,
                            scene = demoScene,
                            onSceneDone = { demoScene = QizaiScene.NONE },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // R3-C：意图思考态——七仔头顶冒思考点点（深色小药丸保底，
                    // 任何封面底色上都看得清；与非封面模式的 ThinkDots 同一语言）
                    if (intentThinking) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(
                                    Color(0xFF141428).copy(alpha = 0.72f),
                                    RoundedCornerShape(99.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            ThinkDots()
                        }
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        // R2 UI：意图入口胶囊（封面与 TODAY 紧凑条之间）
        // v0.68.97：横屏时胶囊只占左侧区（见 coverLeftFrac），右端不再滑入面板下
        if (isLandscapeCover) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(coverLeftFrac)
            ) {
                coverIntentSlot()
            }
        } else {
            coverIntentSlot()
        }
        // 有会议才显示——无会时 hero 已有"难得清闲"，不重复
        if (coverNextEvent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            // v0.68.74（Bob）：横屏时紧凑条收窄到左侧 60%，避免和 Dock 重叠
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.CenterStart
            ) {
                NextUpStrip(
                    summary = coverSummary,
                    nextEvent = coverNextEvent,
                    palette = coverPalette,
                    onMeetingTap = { e -> joinMeeting(e) },
                    // v0.68.97：与胶囊同宽分区（原硬编码 0.62 与面板实际左缘只差
                    // 一线，玻璃面板视觉外扩即压角）——统一走 coverLeftFrac
                    modifier = Modifier.fillMaxWidth(coverLeftFrac)
                )
            }
        }
        }  // Column 结束
    }  // v0.68.74：BoxWithConstraints 结束
    // 数据源：只跑 effect、上报，不渲染（TodaySection 数据管线）
    TodaySection(
        onMeetingRemind = { e, tag -> remindMeeting(e, tag) },
        onSummaryChange = { coverSummary = it },
        onNextEventChange = { coverNextEvent = it }
    )
}

/** 思考中的三个跳动点 */
@Composable
private fun ThinkDots(modifier: Modifier = Modifier) {
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
 * 点按入会（封面紧凑条与意图入口建议面的会议行共用同一条路径，2026-10-21 抽出）：
 * 1) 事件里有可解析的在线会议链接 → 直开链接（会议 App/浏览器接管）；
 * 2) 无链接 → 打开日历 App / 事件详情（三级兜底，与非封面 TODAY 会议行同款）；
 * 3) 全失败 → 七仔说话兜底，绝不无反应。
 */
fun joinCalendarEvent(context: android.content.Context, e: CalEvent) {
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
