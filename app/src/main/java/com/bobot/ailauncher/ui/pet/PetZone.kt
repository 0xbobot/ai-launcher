package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.home.CoverHero
import com.bobot.ailauncher.ui.rememberNowMillis
import com.bobot.ailauncher.ui.home.NextUpStrip
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.coverPaletteFor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.data.WeatherState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 宠物区：桌台（宠物本体/ding/思考点/sort-tag/carry）。
 * 放在首页顶栏下方。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetZone(
    // v0.29.0：升级提醒宠物化
    hasUpgrade: Boolean = false,
    onUpgradeTap: () -> Unit = {},
    // v0.68.34（Bob）：扇形展开时 TODAY 卡片淡出到 0.3（视觉让位，和 Dock 同逻辑）
    todayCardAlpha: Float = 1f,
    // v0.68.35（Bob）：杂志封面模式——hero + 紧凑条，替代默认宠物舞台布局
    coverMode: Boolean = false,
    // v0.68.35：简报标题/下个会议上抛（封面 hero 用）
    onSummaryChange: (String) -> Unit = {},
    onNextEventChange: (CalEvent?) -> Unit = {},
    // R2 UI（文档二 P0）：意图入口胶囊槽位——封面模式下渲染在 hero 与紧凑条之间；
    // 状态由 HomeScreen 持有（IntentCoordinator），PetZone 只负责摆位
    coverIntentSlot: @Composable () -> Unit = {}
) {
    val mood by PetRepository.mood.collectAsState()
    // R3-C：意图反应姿态覆盖（对话感）——非空时优先于整理员 mood 渲染，通知流程不受影响
    val intentPose by PetRepository.intentPose.collectAsState()
    val intentThinking by PetRepository.intentThinking.collectAsState()
    val displayMood = intentPose ?: mood
    val mouth by PetRepository.mouth.collectAsState()
    val dingText by PetRepository.dingText.collectAsState()
    val carryText by PetRepository.carryText.collectAsState()
    val carryToRight by PetRepository.carryToRight.collectAsState()
    val showDots by PetRepository.showDots.collectAsState()
    val sortText by PetRepository.sortText.collectAsState()
    // v0.19：语义状态——SLEEPY 时眼睛保持闭合（夜晚睡觉）
    val petState by PetRepository.petState.collectAsState()
    // v0.29.0：新通知送达 / 会议临近
    val deliverTick by PetRepository.deliverTick.collectAsState()
    val deliverApp by PetRepository.deliverApp.collectAsState()
    val meetingSoon by PetRepository.meetingSoon.collectAsState()
    // v0.43.0：七仔场景演示——点按循环 WAVE→READING→WORKING；新通知到达自动进 READING
    var demoScene by remember { mutableStateOf(QizaiScene.NONE) }
    // v0.64.5：编辑模式状态上提（气泡点击退出需要用到）
    var todayEditMode by remember { mutableStateOf(false) }
    // v0.66.8：卡片顶部对齐屏幕 1/3 处——舞台高度 = 屏幕高/3 - 顶部 16dp - 卡片顶部 8dp
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val stageH = screenH / 3 - 24.dp
    // v0.66.9：七仔靠近宠物区底部——宠物顶部 = 舞台高 - 140dp（离卡片 40dp），最小 24dp 保顶部装备不被切
    val petTop = if (stageH - 140.dp > 24.dp) stageH - 140.dp else 24.dp
    // v0.64.5：返回键退出编辑模式
    BackHandler(enabled = todayEditMode) {
        todayEditMode = false
        PetRepository.say("好啦！")
    }
    // v0.51.0：新通知 → 七仔看手机 + 开口（徽标已删，消息统一进 Today）
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
    val scope = rememberCoroutineScope()
    var lastWeatherDesc by remember { mutableStateOf<String?>(null) }
    var weatherTriggersToday by remember { mutableIntStateOf(0) }
    var weatherTriggerDate by remember { mutableStateOf("") }
    var morningBriefedDate by remember { mutableStateOf("") }
    // v0.67.6：天气缓存 TTL（省电原则）——60 分钟内不重复发网络请求
    var lastWeatherFetchMs by remember { mutableLongStateOf(0L) }
    var weatherInitDone by remember { mutableStateOf(false) }
    // v0.67.6：onResume 观察器上提（省电原则：只在用户看到时刷新）
    // 原在 TodaySection，现上提至 PetZone，TodaySection 改为接收 resumeTick 参数
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

    // v0.67.6：删除 30 分钟天气轮询（省电原则）
    // 改为 onResume + 缓存 TTL：见下方 LaunchedEffect(resumeTick) 旁的天气检查块——
    // 回桌面/亮屏时检查，60 分钟内查过直接用缓存，不发网络请求

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
    // v0.47.0：七仔说话——统一信息区，自动消失
    val speech by PetRepository.speech.collectAsState()
    // v0.67.1：AI Capsule 已删除（不再弹窗）
    // v0.64.0：十万火急动效触发器
    val urgentTick by PetRepository.urgentTick.collectAsState()
    var urgentActive by remember { mutableStateOf(false) }
    LaunchedEffect(urgentTick) {
        if (urgentTick > 0) {
            urgentActive = true
            delay(5000) // 动效持续 5 秒，不循环打扰
            urgentActive = false
        }
    }
    // 七仔跳动：3 次快速上下
    val urgentBounce = remember { Animatable(0f) }
    LaunchedEffect(urgentActive) {
        if (urgentActive) {
            repeat(3) {
                urgentBounce.animateTo(-28f, animationSpec = tween(180))
                urgentBounce.animateTo(0f, animationSpec = tween(180))
            }
        } else {
            urgentBounce.snapTo(0f)
        }
    }
    // 红光呼吸：alpha 0.15↔0.35
    val urgentGlow by rememberInfiniteTransition(label = "urgentGlow").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    // v0.50.0：Today 直接放首屏（不再用 Bottom Sheet）
    var speechVisible by remember { mutableStateOf(false) }
    LaunchedEffect(speech?.id) {
        val s = speech
        if (s != null) {
            speechVisible = true
            delay(s.text.length * 40L + 2600L) // 打字机 + 停留
            speechVisible = false
            delay(350) // 淡出
            if (PetRepository.speech.value?.id == s.id) {
                PetRepository.clearSpeech()
            }
        } else {
            speechVisible = false
        }
    }
    var blinking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // v0.64.5：版本更新后首次打开 → 七仔介绍新本事（只一次，首次安装不打扰）
    LaunchedEffect(Unit) {
        if (GuideManager.shouldShowVersionUpdate(context)) {
            PetRepository.say("我有新本事了！长按卡片可以进编辑模式哦")
        }
    }
    // v0.67.6：删除 15 分钟日程轮询（省电原则）
    // 改为一次性任务：见下方 LaunchedEffect(nextEvent) 后的提醒调度块——
    // 日历数据变化时重算，在"开会前30分钟"和"前15分钟"各安排一次提醒；
    // onResume 重查日历会触发 allEvents 更新从而重调度，天然兜底（防任务被杀）
    val notifiedCalendarKeys = remember { mutableSetOf<String>() }
    // 七仔会议提醒（含去重：同一场同一档只提醒一次）
    // v0.67.1：会议提醒不再弹模态 Capsule——只走七仔说话 + TODAY P1，
    // 产品原则：重要信息不用模态弹窗
    fun remindMeeting(e: CalEvent, tag: String) {
        // 没权限就静默跳过，用户在 TodaySheet 里手动授权
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
    if (coverMode) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val isLandscapeCover = maxWidth > maxHeight
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
            coverIntentSlot()
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
                        modifier = if (isLandscapeCover) Modifier.fillMaxWidth(0.62f) else Modifier.fillMaxWidth()
                    )
                }
            }
            }  // Column 结束
        }  // v0.68.74：BoxWithConstraints 结束
        // 数据源：只跑 effect、上报，不渲染
        TodaySection(
            dataOnly = true,
            resumeTick = resumeTick,
            onMeetingRemind = { e, tag -> remindMeeting(e, tag) },
            onSummaryChange = { coverSummary = it },
            onNextEventChange = { coverNextEvent = it }
        )
    } else {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // v0.64.5：编辑模式下点卡片外空白退出。
            // 卡片自身吞掉落在卡片上的点击（见卡片 Box 的 tap-blocker），冒泡到这里的必是卡片外。
            .pointerInput(todayEditMode) {
                if (todayEditMode) {
                    detectTapGestures {
                        todayEditMode = false
                        PetRepository.say("好啦！")
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 桌台
        // v0.65.4：气泡移到七仔下方（8dp 间距），有气泡时舞台自然撑高（宠物位置不动）
        // v0.65.6：舞台最小高度 148dp → 220dp——装备图标+标签+气泡不再被 TODAY 卡片遮挡
        // v0.65.7：220dp → 280dp——装备图标+下方标签有充足空间，不再紧贴 TODAY 卡片
        // v0.65.9：280dp → 320dp——给七仔动画和装备更多活动空间
        // v0.66.1：320dp → 360dp——装备图标/标签/+按钮全部收进舞台，不被 TODAY 卡片遮挡
        // v0.66.3：360dp → 400dp——七仔下移靠近舞台底部，宠物层内部拉开
        // v0.66.7：舞台高度 = 屏幕高/2 - 24dp，卡片顶部对齐屏幕中线
        // v0.66.8：舞台高度 = 屏幕高/3 - 24dp，卡片顶部对齐屏幕 1/3 处
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageH)
        ) {
            // ding：alpha 淡入淡出（Box 内不用 AnimatedVisibility，避免 scope 重载解析问题）
            val dingAlpha by animateFloatAsState(
                targetValue = if (dingText != null) 1f else 0f,
                label = "dingAlpha"
            )
            if (dingText != null || dingAlpha > 0.02f) {
                Text(
                    text = dingText.orEmpty(),
                    fontSize = 12.5.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(dingAlpha)
                        .padding(top = 4.dp)
                        .background(
                            Color(0xFF141428).copy(alpha = 0.72f),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            // sort-tag：分类决策展示
            val sortAlpha by animateFloatAsState(
                targetValue = if (sortText != null) 1f else 0f,
                label = "sortAlpha"
            )
            if (sortText != null || sortAlpha > 0.02f) {
                Text(
                    text = sortText.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(sortAlpha)
                        .padding(top = 40.dp)
                        .background(
                            Color(0xFF141428).copy(alpha = 0.78f),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            // 思考省略号（整理员思考中 / R3-C 意图 Planning 思考中，共用同一呈现）
            if (showDots || intentThinking) {
                ThinkDots(modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp))
            }
            // v0.29.0：升级礼物盒——有新版时七仔头顶抱礼物盒
            if (hasUpgrade) {
                GiftBox(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
            // v0.51.2：会议临近黄条已删（Capsule 已覆盖，避免重复）
            // v0.47.0：七仔说话气泡——统一信息区（Mii 风+游戏化），有话就弹出来
            // v0.64.5：编辑模式下点气泡退出编辑模式
            // v0.68.79（Bob）：七仔装备环已移除，全部以扇形为主
            if (speech != null) {
                SpeechBubble(
                    text = speech!!.text,
                    visible = speechVisible,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = petTop + 90.dp), // v0.66.9：气泡紧贴七仔下方（40dp 间隙内，2 行文本 bottom 在 stageH+16，卡片在 stageH+24，不遮挡）
                    onClick = if (todayEditMode) {
                        {
                            todayEditMode = false
                            PetRepository.say("好啦！")
                        }
                    } else null
                )
            }
    // v0.56.0 M2 抚摸：长按 + 滑动 = 抚摸（害羞+腮红+轻颤）
    var pettedThisGesture by remember { mutableStateOf(false) }
    PetView(
                mood = displayMood,
                mouth = mouth,
                blinking = blinking,
                sleepy = petState == PetState.SLEEPY,
                jellyTick = jellyTick,
                scene = demoScene,
                onSceneDone = { demoScene = QizaiScene.NONE },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = petTop) // v0.66.9：七仔靠近宠物区底部（petTop = stageH - 140dp，离卡片 40dp）
                    // 体检批D ⑥：冒烟语义定位锚点（七仔本体，非封面形态）
                    .testTag("pet_qizai")
                    // v0.64.0：十万火急跳动（3 次快速上下）
                    .graphicsLayer { translationY = urgentBounce.value }
                    // 场景播放时给宽舞台（同中心，宠物视觉大小不变）；常态保持 100dp
                    .then(
                        if (demoScene == QizaiScene.NONE) Modifier.size(100.dp)
                        else Modifier.fillMaxWidth().height(134.dp)
                    )
                    .weatherMotion(weatherDesc)
                    // v0.56.0 M2：抚摸检测（长按 500ms 后滑动 30px）
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val downTime = System.currentTimeMillis()
                            var petted = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val elapsed = System.currentTimeMillis() - downTime
                                val moved = (change.position - down.position).getDistance()
                                if (elapsed > 500 && moved > 30f && !petted) {
                                    petted = true
                                    pettedThisGesture = true
                                    PetRepository.setUserInteracting(true)
                                    PetRepository.onPetted()
                                }
                                if (petted) change.consume()
                            }
                            if (petted) PetRepository.setUserInteracting(false)
                        }
                    }
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            // v0.50.0：Today 已在首屏，点按宠物只做果冻反馈
                            // 场景改由真实事件触发（天气/通知/日程）
                            // v0.56.0 M2：开心表情，不说话
                            // v0.68.79（Bob）：装备环已移除，全部以扇形为主
                            if (hasUpgrade) onUpgradeTap()
                            else {
                                jellyTick++
                                PetRepository.setMoodHappyBrief()
                            }
                        },
                        onLongClick = {
                            // v0.56.0 M2：抚摸过的手势不触发天气刷新
                            if (pettedThisGesture) {
                                pettedThisGesture = false
                                return@combinedClickable
                            }
                            // v0.64.5：首次长按七仔 → 指引（只一次）
                            if (GuideManager.tryShow(context, GuideManager.Id.PET_LONGPRESS)) {
                                PetRepository.say("长按我是抚摸哦")
                            }
                            // v0.46.0：长按手动刷新天气——拉最新数据并播对应场景
                            scope.launch {
                                val w = withContext(Dispatchers.IO) {
                                    WeatherRepository.fetchSync()
                                }
                                if (w != null) {
                                    lastWeatherDesc = w.desc
                                    WeatherState.updateFull(w)
                                    demoScene = weatherSceneFor(w.desc)
                                    PetRepository.say("${w.city}${w.desc}，${w.temp}°")
                                } else {
                                    jellyTick++ // 拉取失败，给个果冻反馈
                                }
                            }
                        }
                    )
            )
            // v0.64.0：十万火急红光（身体微发红，5 秒）
            if (urgentActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .graphicsLayer { translationY = urgentBounce.value }
                        .background(
                            Color(0xFFFF3B30).copy(alpha = urgentGlow * 0.5f),
                            CircleShape
                        )
                )
            }
            // v0.66.6：落地阴影已删除（Bob：宠物下面的横线不要了）
            // carry 小纸条
            val carryAlpha by animateFloatAsState(
                targetValue = if (carryText != null) 1f else 0f,
                label = "carryAlpha"
            )
            if (carryText != null || carryAlpha > 0.02f) {
                val carryX by animateFloatAsState(
                    targetValue = if (carryToRight) 100f else 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.8f
                    ),
                    label = "carryX"
                )
                val density = LocalDensity.current
                Text(
                    text = carryText.orEmpty(),
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(carryAlpha)
                        .offset {
                            IntOffset(
                                with(density) { carryX.dp.toPx() }.roundToInt(),
                                with(density) { 34.dp.toPx() }.roundToInt()
                            )
                        }
                        .background(AILauncherColors.current.Card.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        // v0.68.40：语音胶囊已删除（Bob：太重了，全清）
        }
        // v0.67.1：AI Capsule 模态卡已删除——会议/重要信息不再弹窗，
        // 只走七仔说话 + TODAY 卡片 + 视觉动效（产品原则）
        // v0.50.0：TODAY 直接放首屏
        // v0.56.0 M5：坐卡关联——TODAY 包成磨砂卡，七仔"坐"在上面（卡片上移 30dp 压住宠物底部）
        // v0.58.0（需求1）：暖米白 #FDF8F0，圆角 28dp
        // v0.60.0：毛玻璃——白 65% + 1dp 白描边，系统壁纸透过来
        // v0.63.1：不用 Material3 Card（tonal elevation 在半透明背景上形成灰边），
        // 改用 Box + 手动柔和阴影，边缘与背景自然融合
        // 玻璃试点批：换真中玻璃（模糊+增色+叠白+顶部亮边，见 ui/glass）；
        // 维持单层、无嵌套、28dp 圆角与既有阴影；模拟档回白 65% 现状。
        // v0.64.3：editMode 上提；编辑模式卡片高度自适应内容（去 minHeight、不滚动）
        // v0.64.5：todayEditMode 已移到 PetZone 顶部，此处不再重复声明
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // v0.68.34：扇形展开时卡片淡出到 0.3（视觉让位，和 Dock 同逻辑）
                .graphicsLayer { alpha = todayCardAlpha }
                .padding(horizontal = 20.dp)
                // v0.66.6：去掉 -30dp 上移（七仔坐卡上），上下两部分清晰分开；卡片顶部留 8dp 呼吸
                // v0.66.6：卡片底部留 32dp，与 Dock 拉开距离
                .padding(top = 8.dp, bottom = 32.dp)
                .then(if (todayEditMode) Modifier else Modifier.heightIn(min = 120.dp)) // v0.62.1：空态时不缩成一条线；v0.64.3：编辑模式去掉
                // v0.64.5：编辑模式下吞掉卡片背景上的点击（不冒泡到根布局，避免误退出）
                .then(
                    if (todayEditMode) Modifier.pointerInput(Unit) {
                        detectTapGestures { /* 消费掉，不做事 */ }
                    } else Modifier
                )
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(RoundedCornerShape(28.dp))
                .glassSurface(
                    kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.MEDIUM,
                    cornerRadius = 28.dp,
                    tintColor = AILauncherColors.current.Card,
                    fallbackColor = AILauncherColors.current.Card.copy(alpha = 0.65f)
                )
        ) {
            // v0.64.0：十万火急红色光带扫过顶部（1 次）
            val sweepX = remember { Animatable(-0.3f) }
            LaunchedEffect(urgentTick) {
                if (urgentTick > 0) {
                    sweepX.snapTo(-0.3f)
                    sweepX.animateTo(1.3f, animationSpec = tween(800))
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardW = maxWidth
                if (urgentTick > 0 && sweepX.value < 1.3f) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(3.dp)
                            .offset(x = cardW * sweepX.value - 40.dp)
                            .background(
                                Color(0xFFFF3B30).copy(alpha = 0.8f),
                                RoundedCornerShape(99.dp)
                            )
                    )
                }
            }
            // v0.64.0：卡片限高 600dp，超出内部滚动，不再无限撑大
            // v0.64.3：编辑模式不滚动、高度自适应内容
            Column(
                modifier = if (todayEditMode) {
                    Modifier
                } else {
                    Modifier
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                }
            ) {
                // v0.66.6：去掉 20dp 七仔坐位（上下分开后不再需要）
                // v0.67.6：传入 resumeTick（onResume 观察器上提）与会议提醒回调
                TodaySection(
                    editMode = todayEditMode,
                    onEditModeChange = { todayEditMode = it },
                    resumeTick = resumeTick,
                    onMeetingRemind = { e, tag -> remindMeeting(e, tag) }
                )
            }
        }
    }
    }
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
            val dy by animateFloatAsState(
                targetValue = if (up) -9f else 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
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
 * v0.49.0：Today Bottom Sheet——AI 对今天的理解（不是 Todo List）。
 */
/**
 * v0.64.2：组级别拖拽容器——编辑模式下左侧显示 ⋮⋮ 手柄，长按拖拽换位。
 * v0.64.5：换位用"累计 dy + 各单元高度 + 组间隙"计算中心距（不用 positionInRoot，CI 不认）；
 * 手柄触摸目标放大到 48dp；拖拽中组 zIndex 置顶 + 放大 1.02 + 阴影。
 */
@Composable
internal fun DragGroupRow(
    gkey: String,
    editMode: Boolean,
    isDragging: Boolean,
    dragDyPx: Float,
    onPositioned: (top: Float, height: Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (change: PointerInputChange, amount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    // v0.64.5：onSizeChanged 测整行高度（手柄+内容取高者）；换位用高度+已知间距计算
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onPositioned(0f, it.height.toFloat()) }
            .then(if (isDragging) Modifier.zIndex(1f) else Modifier)
            .then(
                if (isDragging) Modifier
                    .offset { IntOffset(0, dragDyPx.roundToInt()) }
                    .graphicsLayer {
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = 16.dp.toPx()
                    }
                else Modifier
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (editMode) {
            Box(
                modifier = Modifier
                    // v0.64.5：手柄触摸目标放大到 48dp，之前只有文字大小很难按中
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(top = 4.dp, end = 8.dp)
                    .pointerInput(gkey) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount -> onDrag(change, dragAmount) }
                        )
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "⋮⋮",
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFAEAEB2)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            content()
        }
    }
}


/**
 * v0.29.0：升级礼物盒——Canvas 手绘，七仔头顶。
 * 有新版时显示，点按七仔弹更新对话框。
 */
@Composable
private fun GiftBox(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(44.dp, 40.dp)
    ) {
        val w = size.width
        val h = size.height
        val boxColor = androidx.compose.ui.graphics.Color(0xFFE8734A)
        val ribbonColor = androidx.compose.ui.graphics.Color(0xFFFFD66B)
        // 盒身
        drawRoundRect(
            color = boxColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        // 盒盖
        drawRoundRect(
            color = boxColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        // 纵丝带
        drawRect(
            color = ribbonColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.78f)
        )
        // 横丝带（盖上）
        drawRect(
            color = ribbonColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.08f)
        )
        // 蝴蝶结（两个圆）
        drawCircle(
            color = ribbonColor,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.14f)
        )
        drawCircle(
            color = ribbonColor,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.14f)
        )
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

/**
 * v0.30.0：天气微动作——按天气给宠物加极小的身体语言。
 * 晴±0.6°晃 / 雨微微低头 / 阴几乎不动 / 雪抬头看 / 夜下沉。
 */
@Composable
private fun Modifier.weatherMotion(desc: String?): Modifier {
    val density = LocalDensity.current
    return when {
        desc == null -> this
        desc.contains("晴") -> {
            val t = rememberInfiniteTransition(label = "bask")
            val r by t.animateFloat(
                initialValue = -0.6f, targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "baskR"
            )
            this.graphicsLayer { rotationZ = r }
        }
        desc.contains("雨") -> {
            val t = rememberInfiniteTransition(label = "droop")
            val y by t.animateFloat(
                initialValue = 0f, targetValue = with(density) { 2.dp.toPx() },
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "droopY"
            )
            this.graphicsLayer { translationY = y }
        }
        desc.contains("雪") -> {
            val t = rememberInfiniteTransition(label = "lookUp")
            val y by t.animateFloat(
                initialValue = 0f, targetValue = with(density) { (-4).dp.toPx() },
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "lookY"
            )
            this.graphicsLayer { translationY = y }
        }
        else -> this
    }
}
