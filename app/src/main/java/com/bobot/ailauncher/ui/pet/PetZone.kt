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
 * BriefMeetingRow 形式长进简报；七仔点按 = 进入输入态（onPetTap）。
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
    // 撤出简报——七仔只留 Dock 上沿坐姿一只（静态）与输入态胶囊旁的反应体
    // （HomeScreen 的 InputPetCompanion），简报回归纯文字。原 hasUpgrade /
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
    // v0.29.0：新通知送达
    val deliverTick by PetRepository.deliverTick.collectAsState()
    val deliverApp by PetRepository.deliverApp.collectAsState()
    // v0.51.0：新通知 → 七仔开口（徽标已删，消息统一进 Today 数据管线）
    // v0.56.0 M3：改用 sayApp（同一应用 30 秒合并 + 深夜/手势中降级）
    // v0.68.101：原「看手机」场景演示随讲述者七仔退役，开口状态保留
    // （PetRepository 语音状态机不动，其他呈现面可继续消费）。
    LaunchedEffect(deliverTick) {
        if (deliverTick > 0) {
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
        // PROBE-freeze-scrim：罩纱 Box（matchParentSize 渐变层）摘除，
        // Column 直挂 BoxWithConstraints，其余（含 inputCompact）保持候选。
        Column(modifier = Modifier.fillMaxWidth()) {
        // v0.68.101（D-1）：横屏输入态 hero 整体让位——横屏 IME 抬升后
        // 可用高度只剩约四成，左区只装胶囊堆栈；竖屏走各模板 inputCompact。
        if (!(isLandscapeCover && inputActive)) {
            CoverHero(
                summary = coverSummary,
                weatherDesc = weatherDesc ?: "",
                palette = coverPalette,
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
                palette = coverPalette,
                onJoin = { e -> joinMeeting(e) },
                modifier = Modifier.fillMaxWidth(coverLeftFrac)
            )
        }
        // 罩纱尾巴：会行（或简报末尾）之下留一段渐隐距离，衬底在此收尾
        Spacer(modifier = Modifier.height(26.dp))
        }  // Column 结束（PROBE：罩纱摘除）
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
