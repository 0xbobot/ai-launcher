package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.AiInsight
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.home.CoverHero
import com.bobot.ailauncher.ui.home.NextUpStrip
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.coverPaletteFor
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.data.WeatherState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.app.NotificationManagerCompat
import android.provider.Settings
import kotlin.math.roundToInt
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bobot.ailauncher.ui.island.IslandToasts

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
    onNextEventChange: (CalEvent?) -> Unit = {}
) {
    val mood by PetRepository.mood.collectAsState()
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
    // v0.65.5：七仔装备——点七仔展开/收起环绕的快捷图标（无容器拟物化）
    var showQuickPanel by remember { mutableStateOf(false) }
    // v0.66.6：收起时先播反向摇摆动画，再真正移除（panelCollapsing 期间保持挂载）
    var panelCollapsing by remember { mutableStateOf(false) }
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
        if (!hasCalendarPermission(context)) return
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
    // v0.25.6 P0：点按果冻（纯视觉反馈，Bob 拍板）
    var jellyTick by remember { mutableIntStateOf(0) }

    // v0.56.0 M1：眨眼已移入 PetView（8 秒 ± 3 秒随机），此处删除旧定时器

    // v0.68.44：封面模式状态（hero 标题/下个会议）
    var coverSummary by remember { mutableStateOf("难得清闲") }
    var coverNextEvent by remember { mutableStateOf<CalEvent?>(null) }
    val coverPalette = remember {
        coverPaletteFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }

    // v0.68.44（Bob）：封面模式——杂志 hero + 紧凑条；TodaySection 只跑数据不上 UI
    // v0.68.45：恢复点七仔快捷面板（Bob：快捷服务别删）；无会议时紧凑条隐藏（Bob：别和 hero 重复）
    // v0.68.47：装备环包进宠物槽位、以宠物为中心（Bob：散落不优雅）
    if (coverMode) {
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
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        // 和原版一致：点七仔展开/收起快捷面板
                                        if (hasUpgrade) onUpgradeTap()
                                        else {
                                            jellyTick++
                                            PetRepository.setMoodHappyBrief()
                                            if (showQuickPanel && !panelCollapsing) {
                                                panelCollapsing = true
                                            } else if (!showQuickPanel) {
                                                showQuickPanel = true
                                            }
                                        }
                                    }
                                )
                        ) {
                            PetView(
                                mood = mood,
                                mouth = mouth,
                                blinking = blinking,
                                sleepy = petState == PetState.SLEEPY,
                                jellyTick = jellyTick,
                                scene = demoScene,
                                onSceneDone = { demoScene = QizaiScene.NONE },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 装备环：220.dp 以七仔为中心，四周溢出 50.dp 环绕
                        // 右上 pet，环左移 40.dp 让右腰装备不飞出屏幕
                        if (showQuickPanel || panelCollapsing) {
                            QizaiEquipment(
                                modifier = Modifier.offset(x = (-40).dp),
                                onLaunch = { if (!panelCollapsing) panelCollapsing = true },
                                collapsing = panelCollapsing,
                                onCollapseDone = {
                                    panelCollapsing = false
                                    showQuickPanel = false
                                }
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 有会议才显示——无会时 hero 已有"难得清闲"，不重复
            if (coverNextEvent != null) {
                NextUpStrip(
                    summary = coverSummary,
                    nextEvent = coverNextEvent,
                    palette = coverPalette,
                    onMeetingTap = { e -> remindMeeting(e, "cover") }
                )
            }
        }
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
            // 思考省略号
            if (showDots) {
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
            // v0.65.5：七仔装备——拟物化快捷方式，无容器，图标像武器一样挂在七仔周围
            // v0.65.2：快捷面板打开时优先显示，普通说话气泡让位
            // v0.66.6：panelCollapsing 期间保持挂载，播完反向摇摆才移除
            if (showQuickPanel || panelCollapsing) {
                QizaiEquipment(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = petTop), // v0.66.8：随七仔，七仔在上 1/3 区域偏下
                    onLaunch = { if (!panelCollapsing) panelCollapsing = true },
                    collapsing = panelCollapsing,
                    onCollapseDone = {
                        panelCollapsing = false
                        showQuickPanel = false
                    }
                )
            } else if (speech != null) {
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
                mood = mood,
                mouth = mouth,
                blinking = blinking,
                sleepy = petState == PetState.SLEEPY,
                jellyTick = jellyTick,
                scene = demoScene,
                onSceneDone = { demoScene = QizaiScene.NONE },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = petTop) // v0.66.9：七仔靠近宠物区底部（petTop = stageH - 140dp，离卡片 40dp）
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
                            // v0.65.5：点七仔展开/收起装备（环绕的快捷图标）
                            // v0.66.6：收起时先播反向摇摆（panelCollapsing），播完才真正移除
                            if (hasUpgrade) onUpgradeTap()
                            else {
                                jellyTick++
                                PetRepository.setMoodHappyBrief()
                                if (showQuickPanel && !panelCollapsing) {
                                    panelCollapsing = true
                                } else if (!showQuickPanel) {
                                    showQuickPanel = true
                                }
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
                    fontSize = 11.sp,
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
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
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
                .background(Color.White.copy(alpha = 0.65f))
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { i ->
            var up by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
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
 * 宠物呈现卡片：展示一次。
 * 手势：左滑 = 更多（出现操作按钮），右滑 = 更少（收回成标签）。
 * 隐私类：未展开前标题正文打码。
 */
@Composable
fun PetPresentedCard() {
    val item by PetRepository.presented.collectAsState()
    val armed by PetRepository.cardArmed.collectAsState()
    val density = LocalDensity.current
    val threshPx = remember(density) { with(density) { 56.dp.toPx() } }

    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically { with(density) { 26.dp.toPx() }.roundToInt() } + fadeIn(),
        exit = fadeOut() + slideOutVertically { with(density) { 20.dp.toPx() }.roundToInt() }
    ) {
        val cur = item ?: return@AnimatedVisibility
        val masked = cur.cat == PetCat.PRIV && !armed
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .pointerInput(cur.id, armed) {
                    var accumX = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; fired = false },
                        onDragCancel = { fired = true },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (fired) return@detectHorizontalDragGestures
                            accumX += dragAmount
                            if (kotlin.math.abs(accumX) > threshPx) {
                                fired = true
                                if (accumX < 0) PetRepository.armCard()      // 左滑=多
                                else PetRepository.fileToTab()              // 右滑=少
                            }
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${cur.cat.tabEmoji} ${cur.appName}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cur.cat.color,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = cur.cat.cnName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(cur.cat.color, RoundedCornerShape(99.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                if (cur.isBatchSummary) {
                    // v0.16：汇总卡片——多条分行列出（隐私类保持打码）
                    cur.batchItems.forEach { sub ->
                        Text(
                            text = if (sub.cat == PetCat.PRIV) "${sub.cat.tabEmoji} ${sub.appName} · •••"
                            else "${sub.cat.tabEmoji} ${sub.appName} · ${sub.title}",
                            fontSize = 12.5.sp,
                            color = Color(0xFF6B6B76),
                            lineHeight = 19.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = if (masked) "••••••" else cur.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2C34)
                    )
                    if (cur.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (masked) "••••••（左滑查看完整内容）" else cur.text,
                            fontSize = 12.5.sp,
                            color = Color(0xFF6B6B76),
                            lineHeight = 18.sp
                        )
                    }
                }
                AnimatedVisibility(visible = armed) {
                    PetCardActions(item = cur)
                }
                Text(
                    text = if (armed) "选一个操作，或右滑收回" else "左滑 → 更多操作 · 右滑 → 记入 Today",
                    fontSize = 10.5.sp,
                    color = Color(0xFFA0A0AD),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** 卡片操作按钮：按分类给 2-3 个 */
@Composable
private fun PetCardActions(item: PetItem) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val actions = remember(item.cat, item.isBatchSummary) {
        // v0.16：汇总卡片只有一个"全部完成"
        if (item.isBatchSummary) listOf("全部完成" to false)
        else when (item.cat) {
            PetCat.IMP -> listOf("打开应用" to true, "标为已读" to false, "完成" to false)
            PetCat.WORK -> listOf("打开应用" to true, "完成" to false)
            PetCat.FUN -> listOf("打开应用" to true, "完成" to false)
            PetCat.PRIV -> listOf("复制验证码" to true, "打开应用" to false, "完成" to false)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        actions.forEach { (label, primary) ->
            if (primary) {
                Button(
                    onClick = { onPetAction(context, clipboard, item, label) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = { onPetAction(context, clipboard, item, label) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 12.5.sp, color = Color(0xFF5A4BD6))
                }
            }
        }
    }
}

private fun onPetAction(
    context: Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    item: PetItem,
    label: String
) {
    when (label) {
        "复制验证码" -> {
            clipboard.setText(AnnotatedString(item.text))
            IslandToasts.show("已复制")
        }
        "打开应用" -> {
            openAppForPet(context, item.packageName, item.appName)
            PetRepository.completeItem("打开应用")
        }
        "标为已读", "完成", "全部完成" -> PetRepository.completeItem(label)
    }
}

/** 打开应用（计入常用统计）；日程类没有包名则只 toast */
private fun openAppForPet(context: Context, packageName: String, appName: String) {
    if (packageName.isBlank()) {
        IslandToasts.show("「$appName」")
        return
    }
    try {
        AppUsageTracker.recordLaunch(context, packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            IslandToasts.show("无法打开「$appName」")
        }
    } catch (_: Exception) {
        IslandToasts.show("无法打开「$appName」")
    }
}


/**
 * v0.47.0：七仔说话气泡——Mii 风（干净圆润白气泡+小尾巴）+ 游戏化（弹性弹出+打字机）。
 * 七仔有什么话要说，都走这里统一呈现。
 */
@Composable
private fun SpeechBubble(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    // v0.64.5：编辑模式下点气泡退出编辑模式
    onClick: (() -> Unit)? = null
) {
    // 打字机：一字一字蹦出来（RPG 对话感）
    var shownChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shownChars = 0
        for (i in 1..text.length) {
            delay(40)
            shownChars = i
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
        ) + fadeIn(),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // v0.65.4：小尾巴指向上方七仔（气泡在七仔下方）
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(18.dp, 10.dp)
            ) {
                val tailPath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, 0f)
                    close()
                }
                drawPath(tailPath, Color.White)
            }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .then(if (onClick != null) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() } else Modifier)
            ) {
                Text(
                    text = text.take(shownChars),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
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
private fun DragGroupRow(
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
 * v0.50.0：TODAY——直接放首屏（不再用 Bottom Sheet）。
 * AI 对今天的理解：接下来 + 天气小 pill。极简，不抢 Capsule 的戏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodaySection(
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
    // v0.67.6：resumeTick 由 PetZone 传入（onResume 观察器上提）
    resumeTick: Int = 0,
    // v0.67.6：会议提醒回调（省电原则：一次性任务；实际提醒逻辑在 PetZone 的 remindMeeting）
    onMeetingRemind: (CalEvent, String) -> Unit = { _, _ -> },
    // v0.68.44：简报标题/下个会议上抛（封面 hero 用）
    onSummaryChange: (String) -> Unit = {},
    onNextEventChange: (CalEvent?) -> Unit = {},
    // v0.68.44：纯数据模式——只跑数据 effect、上报，不渲染 UI（封面模式用）
    dataOnly: Boolean = false
) {
    val context = LocalContext.current
    val view = LocalView.current
    var nextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // v0.59.0 AI-2：保留今日所有事件，用于消息关联日历
    var allEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var hasCalPermission by remember { mutableStateOf(hasCalendarPermission(context)) }
    // v0.62.0：编辑模式 / 区块排序 / 显隐
    // v0.64.3：editMode 上提到 PetZone，卡片可按编辑模式调整高度
    var sectionOrder by remember { mutableStateOf(TodayPrefs.loadOrder(context)) } // v0.63.0：保留兼容，排序已移除
    var visibleSections by remember { mutableStateOf(TodayPrefs.loadVisible(context)) }
    var weatherVisible by remember { mutableStateOf(TodayPrefs.loadWeatherVisible(context)) }
    var pendingEnable by remember { mutableStateOf<TodaySection?>(null) }
    val calPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalPermission = granted
        // v0.62.0：编辑模式里打开"接下来"区块时触发的按需权限请求
        if (pendingEnable == TodaySection.NEXT) {
            if (granted) {
                val nv = visibleSections + TodaySection.NEXT
                visibleSections = nv
                TodayPrefs.saveVisible(context, nv)
            } else {
                PetRepository.say("需要日历权限才能显示日程")
            }
            pendingEnable = null
        }
    }

    LaunchedEffect(hasCalPermission) {
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            val now = System.currentTimeMillis()
            // v0.59.0 AI-2：保留所有今日事件用于关联
            allEvents = events.filter { !it.allDay }
            // v0.67.2：过滤已结束的会议（end <= now 的不显示，避免"还有 -1240 分钟"）
            nextEvent = allEvents.firstOrNull { it.end > now }
        }
    }
    // v0.67.5：会议开始/结束时间点一次性刷新（省电原则：边界点一次性任务，不轮询）
    LaunchedEffect(nextEvent) {
        val e = nextEvent ?: return@LaunchedEffect
        val effectiveEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
        val now = System.currentTimeMillis()
        // 先等到开始（"还有X分钟"→"进行中"），再等到结束（切下一场/消失）
        if (e.begin > now) delay(e.begin - now)
        val waitEnd = effectiveEnd - System.currentTimeMillis()
        if (waitEnd > 0) delay(waitEnd)
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            val now2 = System.currentTimeMillis()
            allEvents = events.filter { !it.allDay }
            nextEvent = allEvents.firstOrNull { it.end > now2 }
        }
    }
    // v0.67.6：会议提醒一次性任务（省电原则：不轮询）
    // 日历数据变化（加载/onResume重查/边界刷新）时取消旧任务重算；
    // 在"开会前30分钟"和"前15分钟"各提醒一次；已进入 30 分钟窗口则立即提醒
    // 实际提醒走 onMeetingRemind（PetZone 的 remindMeeting：七仔说话 + 去重）
    LaunchedEffect(allEvents) {
        val now = System.currentTimeMillis()
        val upcoming = allEvents.firstOrNull { !it.allDay && it.begin > now }
            ?: return@LaunchedEffect
        // 30 分钟前提醒
        val d30 = upcoming.begin - 30 * 60 * 1000L - System.currentTimeMillis()
        if (d30 > 0) delay(d30)
        if (System.currentTimeMillis() >= upcoming.begin - 30 * 60 * 1000L) {
            onMeetingRemind(upcoming, "30")
        }
        // 15 分钟前提醒
        val d15 = upcoming.begin - 15 * 60 * 1000L - System.currentTimeMillis()
        if (d15 > 0) delay(d15)
        if (System.currentTimeMillis() >= upcoming.begin - 15 * 60 * 1000L) {
            onMeetingRemind(upcoming, "15")
        }
    }


    // v0.62.0：区块显隐切换（含按需权限请求）
    fun toggleSection(section: TodaySection, enable: Boolean) {
        if (!enable) {
            val nv = visibleSections - section
            visibleSections = nv
            TodayPrefs.saveVisible(context, nv)
            return
        }
        when (section) {
            TodaySection.NEXT -> {
                if (hasCalendarPermission(context)) {
                    val nv = visibleSections + section
                    visibleSections = nv
                    TodayPrefs.saveVisible(context, nv)
                } else {
                    pendingEnable = section
                    calPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            }
            TodaySection.MSG -> {
                val ok = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
                if (ok) {
                    val nv = visibleSections + section
                    visibleSections = nv
                    TodayPrefs.saveVisible(context, nv)
                } else {
                    try {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) { }
                    PetRepository.say("去设置里打开通知监听，七仔才能看到消息")
                }
            }
            TodaySection.TODO -> {
                val nv = visibleSections + section
                visibleSections = nv
                TodayPrefs.saveVisible(context, nv)
            }
        }
    }

    // v0.62.0 hoist：消息/待办共享数据（供排序后的各区块使用）
        val filed by PetRepository.filed.collectAsState()
        val doneTodos by PetRepository.doneTodos.collectAsState()
        val now = System.currentTimeMillis()
        val recentMsgs = remember(filed) {
            filed.values.flatten()
                .filter { !it.isCalendar }
                .filter { now - it.time < 3 * 60 * 60 * 1000 }
                .sortedWith(
                    compareByDescending<PetItem> {
                        when (it.cat) {
                            PetCat.IMP -> 3
                            PetCat.WORK -> 2
                            else -> 1
                        }
                    }.thenByDescending { it.time }
                )
        }
        // AI-4：按 (appName+title) 分组，判断折叠（v0.63.0：groupedMsgs 已废弃，用 normalGrouped）
        var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
        // AI-3：待办提取（最多 2 条，排除已完成）
        // v0.67.7：保留来源 PetItem，"看原文"点正文直达来源会话
        val todos = remember(recentMsgs, doneTodos) {
            recentMsgs.mapNotNull { item ->
                AiInsight.extractTodo(
                    id = item.id,
                    appName = item.appName,
                    title = item.title,
                    text = item.text,
                    time = item.time
                )?.let { todo -> todo to item }
            }.filter { (todo, _) -> todo.id !in doneTodos }
                .sortedByDescending { (todo, _) -> todo.time }
                .take(2)
        }
    // v0.63.0：AI 简报式——按行动优先级渲染，不再按"接下来/消息/待办"区块
    // P0 需回复消息 / P1 会议 / P2 普通消息 / P3 折叠 / P4 待办
    val replyMsgs = remember(recentMsgs) {
        recentMsgs.filter { AiInsight.analyzeReplyNeed(it.title, it.text).needsReply }
    }
    // v0.64.5：首次出现 P0 需回复消息 → 七仔引导（只一次）
    LaunchedEffect(replyMsgs.isNotEmpty()) {
        if (replyMsgs.isNotEmpty() && GuideManager.tryShow(context, GuideManager.Id.P0_REPLY)) {
            PetRepository.say("红色的是要回复的，点进去看看")
        }
    }
    val normalMsgs = remember(recentMsgs) {
        recentMsgs.filter { !AiInsight.analyzeReplyNeed(it.title, it.text).needsReply }
    }
    // P2/P3：普通消息的分组折叠（复用 AI-4 逻辑）
    val normalGrouped = remember(normalMsgs) {
        normalMsgs.groupBy { "${it.appName}|${it.title}" }
    }
    // v0.67.5：已结束的会议不计入（无结束时间默认按1小时算），避免 stale "1 个会要开"
    val meetingCount = if (TodaySection.NEXT in visibleSections && hasCalPermission && nextEvent != null) {
        val e = nextEvent!!
        val nowM = System.currentTimeMillis()
        val effEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
        if (nowM < effEnd) 1 else 0
    } else 0
    val summaryText = AiInsight.buildSummary(replyMsgs.size, meetingCount)
    // v0.68.44：上抛给封面 hero
    LaunchedEffect(summaryText) { onSummaryChange(summaryText) }
    LaunchedEffect(nextEvent) { onNextEventChange(nextEvent) }
    // v0.68.44：纯数据模式——effect 已跑完，直接返回不渲染
    if (dataOnly) return
    // v0.63.0：空态判断——P0/P1/P2/P4 任一有内容即非空
    val hasAnyContent = (TodaySection.MSG in visibleSections && (replyMsgs.isNotEmpty() || normalMsgs.isNotEmpty())) ||
        meetingCount > 0 ||
        (TodaySection.TODO in visibleSections && todos.isNotEmpty())
    // v0.65.3：空态副标题按时间段区分——在 onResume 时重算（用户回到桌面时刷新），不做轮询，避免隔夜 stale
    // v0.67.6：resumeTick 上提至 PetZone，此处改为接收参数
    val emptySubtitle = remember(resumeTick) { AiInsight.buildEmptySubtitle() }
    // v0.67.5：回到桌面时重查日历（省电原则：只在用户看到时刷新，不轮询），避免隔夜 stale
    LaunchedEffect(resumeTick) {
        if (resumeTick > 0 && hasCalPermission) {
            val events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            val now = System.currentTimeMillis()
            allEvents = events.filter { !it.allDay }
            nextEvent = allEvents.firstOrNull { it.end > now }
        }
    }
    // v0.67.6：天气 onResume + TTL 检查已移至 PetZone（天气状态在 PetZone），此处删除

    // ============ v0.64.2：组级别拖拽排序 ============
    // 组 key（默认 AI 优先级顺序）；用户拖过后用 today_user_order
    val groupKeys = listOf("reply", "event", "msg", "todo")
    val groupLabels = mapOf("reply" to "需回复", "event" to "日程", "msg" to "消息", "todo" to "待办")
    val groupHasContent = mapOf(
        "reply" to (TodaySection.MSG in visibleSections && replyMsgs.isNotEmpty()),
        "event" to (TodaySection.NEXT in visibleSections && (!hasCalPermission || nextEvent != null)),
        "msg" to (TodaySection.MSG in visibleSections && normalGrouped.isNotEmpty()),
        "todo" to (TodaySection.TODO in visibleSections && todos.isNotEmpty())
    )
    val visibleGroupKeys = groupKeys.filter { groupHasContent[it] == true }
    var userGroupOrder by remember { mutableStateOf(TodayPrefs.loadUserOrder(context)) }
    // AI 默认顺序：用户没拖过用 visibleGroupKeys；拖过后用户顺序优先，新出现的组补在末尾
    val baseOrder = remember(visibleGroupKeys, userGroupOrder) {
        val uo = userGroupOrder
        if (uo == null) visibleGroupKeys
        else uo.filter { it in visibleGroupKeys } + visibleGroupKeys.filter { it !in uo }
    }
    // 拖拽中的实时顺序（null=未拖拽，用 baseOrder）
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val renderOrder = dragOrder ?: baseOrder
    // 拖拽共享状态
    val scope = rememberCoroutineScope()
    val groupHeights = remember { mutableStateMapOf<String, Float>() }
    // v0.64.5：组间隙 px = 间距×2 + 分割线高（Column spacedBy 10.dp，分割线在两组之间，上下各一个间距）
    val density = LocalDensity.current
    val spacingPx = with(density) { 10.dp.toPx() }
    var dividerH by remember { mutableStateOf(0f) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    val dragDy = remember { Animatable(0f) }
    // 内容变化且不在拖拽中 → 回到 baseOrder
    LaunchedEffect(baseOrder) {
        if (dragKey == null) dragOrder = null
    }
    fun endGroupDrag(save: Boolean) {
        val k = dragKey
        val order = dragOrder
        if (save && k != null && order != null) {
            userGroupOrder = order
            TodayPrefs.saveUserOrder(context, order)
        }
        scope.launch {
            dragDy.animateTo(0f)
            // 动画结束后再清状态，避免回弹瞬间跳动
            dragKey = null
            dragOrder = null
        }
    }
    // v0.64.5：基于累计 dy + 单元高度 + 组间隙换位（不用 positionInRoot）。
    // 组间隙 = 间距×2 + 分割线高（均匀，任意相邻两组之间都有分割线）。
    // 被拖组中心到相邻组中心距离 = myH/2 + gapPx + neighborH/2；拖过即换位，
    // 换位后补偿 offset 保证视觉连续：newOffset = newDy ∓ centerDist。
    fun onGroupDrag(key: String, change: PointerInputChange, amount: Offset) {
        change.consume()
        val cur = dragOrder ?: return
        val idx = cur.indexOf(key)
        if (idx < 0) return
        val myH = groupHeights[key] ?: 0f
        if (myH <= 0f) return
        val newDy = dragDy.value + amount.y
        val gap = spacingPx * 2 + dividerH
        scope.launch {
            // 向下：拖过下一组中心 → 换位
            if (idx < cur.size - 1) {
                val nH = groupHeights[cur[idx + 1]] ?: 0f
                if (nH > 0f) {
                    val dist = myH / 2f + gap + nH / 2f
                    if (newDy > dist) {
                        val nkey = cur[idx + 1]
                        dragOrder = cur.toMutableList().also {
                            it[idx] = nkey
                            it[idx + 1] = key
                        }
                        dragDy.snapTo(newDy - dist)
                        return@launch
                    }
                }
            }
            // 向上：拖过上一组中心 → 换位
            if (idx > 0) {
                val pH = groupHeights[cur[idx - 1]] ?: 0f
                if (pH > 0f) {
                    val dist = myH / 2f + gap + pH / 2f
                    if (newDy < -dist) {
                        val pkey = cur[idx - 1]
                        dragOrder = cur.toMutableList().also {
                            it[idx] = pkey
                            it[idx - 1] = key
                        }
                        dragDy.snapTo(newDy + dist)
                        return@launch
                    }
                }
            }
            dragDy.snapTo(newDy)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 20.dp), // v0.62.1：纵向呼吸感 12→20dp
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // v0.63.0：AI 简报标题区——天气小 pill + AI 总结大标题 + "七仔帮你看完了"
        // 长按标题栏空白处进入编辑模式（显隐开关）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    // v0.64.4：长按震动 + 七仔说话引导进入编辑模式
                    // v0.64.5：纳入指引系统（只展示一次）；文案更新含退出方式
                    onLongClick = {
                        if (!editMode) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onEditModeChange(true)
                            if (GuideManager.tryShow(context, GuideManager.Id.EDIT_MODE)) {
                                PetRepository.say("拖拽可以排序，点眼睛可以隐藏，点空白处退出哦")
                            }
                        }
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // v0.63.0：正常模式显示 AI 总结标题
            // v0.64.2：编辑模式开关行用横向滚动防止溢出被切
            // v0.64.4：编辑模式去掉"编辑卡片"标题，只留开关行 + 完成按钮
            if (!editMode) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // v0.65.3：空态副标题按时间段区分（C 方案）；有内容时保持"七仔帮你看完了"
                        text = if (!hasAnyContent) emptySubtitle else "七仔帮你看完了",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
            // v0.61.0：天气 pill —— 点击开天气应用，长按看详情
            // v0.62.0：受 weatherVisible 控制显隐
            val weatherFull by WeatherState.full.collectAsState()
            // v0.64.2：编辑模式下不显示天气 pill（开关行已有"天气"开关，避免重复占用空间）
            if (weatherVisible && !editMode) weatherFull?.let { wf ->
                val weatherEmoji = when (wf.desc) {
                    "晴" -> "☀️"
                    "多云" -> "⛅"
                    "阴" -> "☁️"
                    "雾" -> "🌫️"
                    "雷阵雨" -> "⛈️"
                    "雪" -> "❄️"
                    else -> if (wf.desc.contains("雨")) "🌧️" else "⛅"
                }
                // 详情文案（fallback / 长按用）：高低温 + 未来降雨提醒
                val rainHour = wf.hourly.firstOrNull {
                    it.desc.contains("雨") || it.desc.contains("雪") || it.desc == "雷阵雨"
                }
                val detailText = buildString {
                    append("今天${wf.desc}，${wf.temp}°，最高${wf.high}°最低${wf.low}°")
                    if (rainHour != null) append("，${rainHour.hour}时后有${rainHour.desc}")
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .combinedClickable(
                            enabled = !editMode,
                            onClick = {
                                val weatherIntent = android.content.Intent(
                                    android.content.Intent.ACTION_MAIN
                                ).addCategory(android.content.Intent.CATEGORY_APP_WEATHER)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                val resolved =
                                    weatherIntent.resolveActivity(context.packageManager)
                                if (resolved != null) {
                                    try {
                                        context.startActivity(weatherIntent)
                                    } catch (_: Exception) {
                                        PetRepository.say(detailText)
                                    }
                                } else {
                                    PetRepository.say(detailText)
                                }
                            },
                            onLongClick = { PetRepository.say(detailText) }
                        )
                ) {
                    Text(
                        text = "$weatherEmoji ${wf.temp}°",
                        fontSize = 13.sp,
                        color = Color(0xFF57534E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            // v0.63.0：编辑模式——各内容显隐开关 + 完成按钮（排序已移除，P0-P4 按优先级固定）
            // v0.64.2：开关行横向可滚动，内容再多也不被切掉
            if (editMode) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // v0.63.0：消息/日程/待办显隐
                    listOf(
                        TodaySection.MSG to "消息",
                        TodaySection.NEXT to "日程",
                        TodaySection.TODO to "待办"
                    ).forEach { (section, label) ->
                        val vis = section in visibleSections
                        TextButton(
                            onClick = { toggleSection(section, !vis) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (vis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = label,
                                    tint = if (vis) Color(0xFF57534E) else Color(0xFFD6D3D1),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(text = label, fontSize = 10.sp, color = Color(0xFF8E8E93))
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            val nv = !weatherVisible
                            weatherVisible = nv
                            TodayPrefs.saveWeatherVisible(context, nv)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        // v0.62.2：统一用 Material Icons
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (weatherVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (weatherVisible) "隐藏天气" else "显示天气",
                                tint = if (weatherVisible) Color(0xFF57534E) else Color(0xFFD6D3D1),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(text = "天气", fontSize = 10.sp, color = Color(0xFF8E8E93))
                        }
                    }
                    // v0.64.2：恢复智能排序（仅用户拖过顺序后显示）
                    if (userGroupOrder != null) {
                        TextButton(
                            onClick = {
                                userGroupOrder = null
                                TodayPrefs.saveUserOrder(context, null)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "恢复智能排序", fontSize = 10.sp, color = Color(0xFF3B82F6))
                        }
                    }
                }
                // v0.64.5：去掉"完成"按钮，退出方式：点卡片外空白 / 返回键 / 点七仔气泡
            }
        }
        // ============ v0.63.0：AI 简报式优先级渲染 ============
        // P0 需回复消息 → P1 会议 → P2 普通消息 → P3 折叠 → P4 待办
        val dividerColor = Color(0xFFBEBEBE).copy(alpha = 0.4f)
        @Composable
        fun PriorityDivider() {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ============ v0.64.2：组级别拖拽排序渲染 ============
        // P0 需回复 / P1 会议日程 / P2 普通消息+P3折叠 / P4 待办 —— 组内不排序，只排组
        @Composable
        fun GroupContent(gkey: String) {
            when (gkey) {
                "reply" -> {
                // === P0：需回复的消息（红竖线 + 大字 + 去回复按钮 + 关联标签）===
                        // v0.64.0：P0 最多 2 条，超出并入折叠
                        if (TodaySection.MSG in visibleSections) {
                            replyMsgs.take(2).forEach { item ->
                                key(item.id) {
                                    PriorityReplyRow(
                                        item = item,
                                        allEvents = allEvents,
                                        context = context,
                                        editMode = editMode
                                    )
                                }
                            }
                        }

        
                }
                "event" -> {
                // === P1：会议 ===
                        if (TodaySection.NEXT in visibleSections) {
                            if (!hasCalPermission) {
                                OutlinedButton(
                                    onClick = {
                                        calPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                    },
                                    shape = RoundedCornerShape(99.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "授权日历，七仔帮你盯日程", fontSize = 12.sp)
                                }
                            } else {
                                // v0.67.5：已结束的会议不在此显示（无结束时间默认按1小时算），避免 stale "进行中"
                                val activeEvent = nextEvent?.takeIf { e ->
                                    val now = System.currentTimeMillis()
                                    val effEnd = if (e.end > e.begin) e.end else e.begin + 3600_000L
                                    now < effEnd
                                }
                                activeEvent?.let { e ->
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA)
                                        .format(Date(e.begin))
                                    // v0.67.2：已开始未结束显示"进行中"，未开始显示倒计时
                                    val nowMs = System.currentTimeMillis()
                                    val timeLabel = if (e.begin <= nowMs) "进行中"
                                    else "还有 ${((e.begin - nowMs) / 60000).toInt()} 分钟"
                                    val meetingUrl = remember(e) { getValidMeetingUrl(context, e) }
                                    // v0.63.0：扁平会议行（无白卡），标题大字 + 时间地点 + 加入会议按钮
                                    // v0.63.3：用 CATEGORY_APP_CALENDAR 精准打开日历，不弹选择器
                                    Column(
                                        modifier = Modifier.clickable(enabled = !editMode) {
                                            // v0.67.2：三级 fallback 打开日历（豆包手机 CATEGORY_APP_CALENDAR 解析不到）
                                            var opened = false
                                            try {
                                                // 1. 系统日历分类
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
                                                    // 2. 直接打开事件详情
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
                                            // 3. 都失败则七仔说话，不弹系统 toast
                                            if (!opened) {
                                                PetRepository.say("没找到日历应用")
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = e.title.ifBlank { "（无标题）" },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1C1C1E)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$timeStr · $timeLabel" +
                                                (if (e.location.isNotBlank()) " · ${e.location}" else ""),
                                            fontSize = 14.sp,
                                            color = Color(0xFF636366)
                                        )
                                        meetingUrl?.let { url ->
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    try {
                                                        context.startActivity(
                                                            android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse(url)
                                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    } catch (_: Exception) { }
                                                },
                                                enabled = !editMode,
                                                shape = RoundedCornerShape(99.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF34C759),
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                                            ) {
                                                Text(text = "加入会议", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

        
                }
                "msg" -> {
                // === P2 普通消息 + P3 折叠 ===
                        // v0.64.0：P2 最多 3 组，P0/P2 超出都并入折叠计数
                        if (TodaySection.MSG in visibleSections && normalGrouped.isNotEmpty()) {
                            val p0Overflow = (replyMsgs.size - 2).coerceAtLeast(0)
                            val p2Shown = normalGrouped.entries.take(3)
                            val p2Overflow = (normalGrouped.size - p2Shown.size).coerceAtLeast(0)
                            p2Shown.forEach { (groupKey, items) ->
                                val first = items.first()
                                val shouldFold = remember(items) {
                                    AiInsight.shouldFoldGroup(
                                        items.map { AiInsight.FoldCandidate(it.title, it.text) }
                                    )
                                }
                                val isExpanded = groupKey in expandedGroups
                                if (shouldFold && !isExpanded) {
                                    // P3：折叠——灰色小字
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${first.appName}等 ${items.size} 条消息已折叠",
                                        fontSize = 12.sp,
                                        color = Color(0xFFAEAEB2),
                                        modifier = Modifier.clickable(enabled = !editMode) {
                                            expandedGroups = expandedGroups + groupKey
                                        }
                                    )
                                } else {
                                    val displayItems = if (shouldFold) items else listOf(first)
                                    displayItems.forEach { item ->
                                        key(item.id) {
                                            NormalMessageRow(
                                                item = item,
                                                context = context,
                                                editMode = editMode
                                            )
                                        }
                                    }
                                    if (shouldFold && isExpanded) {
                                        Text(
                                            text = "收起",
                                            fontSize = 12.sp,
                                            color = Color(0xFF78716C),
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .clickable(enabled = !editMode) { expandedGroups = expandedGroups - groupKey }
                                        )
                                    }
                                }
                            }
                            // v0.64.0：P0/P2 溢出汇总
                            val totalOverflow = p0Overflow + p2Overflow
                            if (totalOverflow > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "还有 $totalOverflow 条消息已折叠",
                                    fontSize = 12.sp,
                                    color = Color(0xFFAEAEB2)
                                )
                            }
                        }

        
                }
                "todo" -> {
                // === P4：待办 ===
                        if (TodaySection.TODO in visibleSections && todos.isNotEmpty()) {
                            todos.forEachIndexed { index, (todo, source) ->
                                key(todo.id) {
                                    // v0.64.1：多条待办之间用细分割线
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(dividerColor)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    TodoRow(todo = todo, source = source, editMode = editMode)
                                }
                            }
                        }
                }
            }
        }
        renderOrder.forEachIndexed { gi, gkey ->
            key(gkey) {
                // v0.64.5：分割线在单元外（高度稳定，不随换位变化）；测量高度用于换位间距计算
                if (gi > 0) {
                    Box(
                        modifier = Modifier.onSizeChanged { dividerH = it.height.toFloat() }
                    ) {
                        PriorityDivider()
                    }
                }
                DragGroupRow(
                    gkey = gkey,
                    editMode = editMode,
                    isDragging = dragKey == gkey,
                    dragDyPx = if (dragKey == gkey) dragDy.value else 0f,
                    onPositioned = { _, h ->
                        if (groupHeights[gkey] != h) groupHeights[gkey] = h
                    },
                    onDragStart = {
                        dragKey = gkey
                        dragOrder = renderOrder
                        scope.launch { dragDy.snapTo(0f) }
                    },
                    onDrag = { change, amount -> onGroupDrag(gkey, change, amount) },
                    onDragEnd = { endGroupDrag(save = true) }
                ) {
                    GroupContent(gkey)
                }
            }
        }
        // v0.65.2：快捷操作已移到七仔气泡（点七仔展开），TODAY 卡片不再放
        Spacer(modifier = Modifier.height(4.dp))
        // v0.64.2：更新提醒——有新版本时卡片底部一行，点之弹更新对话框；无更新时不占位置
        val availUpdateState by OtaUpdater.latestAvailable.collectAsState()
        val availUpdate = availUpdateState
        if (availUpdate != null && availUpdate.versionCode > com.bobot.ailauncher.BuildConfig.VERSION_CODE) {
            if (hasAnyContent) PriorityDivider() else Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { OtaUpdater.updatePrompt.value = availUpdate },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎉 有新版本 ${availUpdate.versionName}",
                    fontSize = 13.sp,
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.weight(1f)
                )
                Text(text = "›", fontSize = 16.sp, color = Color(0xFFC7C7CC))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

    }
}

/**
 * v0.63.0：P0 需回复消息行——红竖线 + 大字标题 + 正文 + 蓝色关联标签 + 黑色"去回复"按钮。
 * 点击行或按钮都用 contentIntent 直达会话；左滑清除。
 */
@Composable
private fun PriorityReplyRow(
    item: PetItem,
    allEvents: List<CalEvent>,
    context: Context,
    editMode: Boolean = false
) {
    val snippet = when {
        item.title.isNotBlank() && item.text.isNotBlank() ->
            // v0.64.0：正文放宽到 80 字，配合 maxLines=2 + 省略号，不截断关键信息
            "${item.title}：${item.text.take(80)}"
        item.title.isNotBlank() -> item.title
        item.text.isNotBlank() -> item.text.take(80)
        else -> item.sortDesc
    }
    // AI-2：关联日历
    val calLinkTime = remember(item.id, allEvents) {
        allEvents.firstNotNullOfOrNull { event ->
            val kw = AiInsight.findCalendarLink(item.title, item.text, event.title)
            if (kw != null) {
                SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(event.begin))
            } else null
        }
    }
    // v0.67.7：逻辑已抽取为顶层 openPetItemSession，待办"看原文"共用
    fun openSession() = openPetItemSession(context, item)
    // v0.64.0：点击行展开看关键点+全文（看看重点移到 P0 行）
    var expanded by remember(item.id) { mutableStateOf(false) }
    val keyPoints = remember(item.id) {
        com.bobot.ailauncher.data.AiInsight.extractKeyPoints(item.text)
    }
    // v0.64.0：十万火急红条呼吸（alpha 0.4↔1.0，1 秒循环，5 秒止）
    val urgentBreath = remember(item.id) { Animatable(1f) }
    LaunchedEffect(item.id) {
        if (item.isUrgent) {
            val end = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < end) {
                urgentBreath.animateTo(0.4f, animationSpec = tween(500))
                urgentBreath.animateTo(1f, animationSpec = tween(500))
            }
            urgentBreath.snapTo(1f)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                PetRepository.removeFiledByPredicate { it.id == item.id }
                PetRepository.say("已清除")
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !editMode,
        backgroundContent = {
            // v0.63.2：只在滑动时显示红色，避免静态透出
            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(text = "清除", fontSize = 14.sp, color = Color.White)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // v0.64.0：点行展开看关键点+全文，去回复按钮才进会话
                .clickable(enabled = !editMode) { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 红竖线（十万火急时呼吸）
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .alpha(if (item.isUrgent) urgentBreath.value else 1f)
                    .background(Color(0xFFFF3B30), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.appName} · ${item.title.ifBlank { "新消息" }}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E),
                    // v0.68.20：单行省略，杜绝长标题换行重叠
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (expanded) {
                    // v0.64.0：展开态——关键点 + 全文
                    if (keyPoints.isNotEmpty()) {
                        keyPoints.forEach { kp ->
                            Text(
                                text = kp,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1C1C1E),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = item.text,
                        fontSize = 13.sp,
                        color = Color(0xFF636366),
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        // v0.67.4：未接来电有号码时直接显示号码
                        text = if (isMissedCallItem(item) && item.phoneNumber.isNotBlank()) item.phoneNumber else snippet,
                        fontSize = 14.sp,
                        color = Color(0xFF3A3A3C),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                calLinkTime?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = Color(0xFF0A84FF).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "🔗 和 $it 会议相关",
                            fontSize = 12.sp,
                            color = Color(0xFF0A84FF),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 去回复按钮（v0.67.4：未接来电有号码时改为"回拨"）
            Button(
                onClick = { openSession() },
                enabled = !editMode,
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isMissedCallItem(item) && item.phoneNumber.isNotBlank()) "回拨" else "去回复",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * v0.67.4：未接来电识别——标题/正文含"未接来电"，或包名为拨号/通话应用。
 */
private fun isMissedCallItem(item: PetItem): Boolean {
    val t = "${item.title} ${item.text}"
    return t.contains("未接来电") || t.contains("未接电话") ||
        t.contains("Missed call", ignoreCase = true) ||
        item.packageName.lowercase().contains("dialer") ||
        item.packageName.lowercase().contains("incallui")
}

/**
 * v0.67.4：打开系统通话记录，失败则 fallback 拨号盘。
 * @return true=成功打开其一
 */
private fun openCallLog(context: Context): Boolean {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    } catch (_: Exception) { }
    try {
        context.startActivity(
            Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    } catch (_: Exception) { }
    return false
}

/**
 * v0.67.4：一键回拨。有 CALL_PHONE 权限直接拨出，否则打开拨号盘带号码（用户点一下拨出）。
 * @return true=成功打开
 */
private fun dialBack(context: Context, number: String): Boolean {
    return try {
        val uri = Uri.parse("tel:${number.trim()}")
        val base = if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }
        context.startActivity(base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }
}

/**
 * v0.67.7：消息行/待办行共用——点按直达会话（contentIntent），失败时打开 App。
 * 由 NormalMessageRow.openSession() 抽取，待办"看原文"复用同一逻辑。
 */
private fun openPetItemSession(context: Context, item: PetItem) {
    // v0.67.4：未接来电——有号码直接回拨，无号码才开通话记录
    if (isMissedCallItem(item)) {
        val ok = if (item.phoneNumber.isNotBlank()) {
            dialBack(context, item.phoneNumber)
        } else {
            openCallLog(context)
        }
        if (ok) {
            // v0.68.20：点开后从 TODAY 移除（用户已处理）
            com.bobot.ailauncher.data.PetRepository.removeFiledByPredicate { it.id == item.id }
        } else {
            com.bobot.ailauncher.data.PetRepository.say("打不开拨号盘")
        }
        return
    }
    // v0.67.2：contentIntent 失败时 fallback 直接打开 App；全部失败时七仔说话反馈
    val pi = item.pendingIntent
    var sent = false
    if (pi != null) {
        try {
            pi.send()
            sent = true
        } catch (_: android.app.PendingIntent.CanceledException) {
        } catch (_: Exception) { }
    }
    var launched = false
    if (!sent) {
        val pkg = item.packageName.ifBlank { "com.ss.android.lark" }
        try {
            launched = context.packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { context.startActivity(it); true } ?: false
        } catch (_: Exception) { }
        if (!launched) {
            com.bobot.ailauncher.data.PetRepository.say("打不开，试试手动打开${item.appName}")
        }
    }
    // v0.68.20：成功打开后从 TODAY 移除（复用已读清除逻辑）
    if (sent || launched) {
        com.bobot.ailauncher.data.PetRepository.removeFiledByPredicate { it.id == item.id }
    }
}

/**
 * v0.63.0：P2 普通消息行——扁平（无白卡），小圆点 + 应用名 + 正文，点击进会话，左滑清除。
 */
@Composable
private fun NormalMessageRow(
    item: PetItem,
    context: Context,
    editMode: Boolean = false
) {
    val dotColor = when (item.cat) {
        PetCat.IMP -> Color(0xFFFF3B30)
        PetCat.WORK -> Color(0xFF0A84FF)
        PetCat.FUN -> Color(0xFF30D158)
        PetCat.PRIV -> Color(0xFF9CA3AF)
    }
    val snippet = when {
        item.text.isNotBlank() -> item.text.take(40)
        item.title.isNotBlank() -> item.title
        else -> item.sortDesc
    }
    // v0.67.7：逻辑已抽取为顶层 openPetItemSession，待办"看原文"共用
    fun openSession() = openPetItemSession(context, item)
    Spacer(modifier = Modifier.height(8.dp))
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                PetRepository.removeFiledByPredicate { it.id == item.id }
                PetRepository.say("已清除")
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !editMode,
        backgroundContent = {
            // v0.63.2：只在滑动时显示红色，避免静态透出
            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(text = "清除", fontSize = 14.sp, color = Color.White)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !editMode) { openSession() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, RoundedCornerShape(99.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            // v0.67.4：标题+正文改 Column 垂直排列，杜绝横向重叠；单行省略
            // 未接来电有号码时第二行直接显示号码
            val showNumber = isMissedCallItem(item) && item.phoneNumber.isNotBlank()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1C1E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (showNumber) item.phoneNumber else snippet,
                    fontSize = 14.sp,
                    color = Color(0xFF636366),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * v0.62.0：待办行——点左侧圆圈勾选完成（删除线 + 淡出动画后移除），左滑删除。
 * v0.67.7：点正文"看原文"——直达来源消息会话（复用 openPetItemSession）。
 */
@Composable
private fun TodoRow(
    todo: AiInsight.TodoItem,
    source: PetItem,
    editMode: Boolean = false
) {
    val context = LocalContext.current
    var completing by remember(todo.id) { mutableStateOf(false) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (completing) 0f else 1f,
        animationSpec = tween(350),
        label = "todoFade"
    )
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                PetRepository.markTodoDone(todo.id)
                true
            } else false
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Box(modifier = Modifier.alpha(rowAlpha)) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            gesturesEnabled = !editMode && !completing,
            backgroundContent = {
                // v0.63.2：只在滑动时显示红色，避免静态透出
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(text = "删除", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        ) {
            // v0.64.1：扁平化——去掉内层白卡，和 P2 消息行一致
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圆圈：点击勾选完成
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            2.dp,
                            if (completing) Color(0xFF22C55E) else Color(0xFFD6D3D1),
                            RoundedCornerShape(99.dp)
                        )
                        .background(
                            if (completing) Color(0xFF22C55E).copy(alpha = 0.15f)
                            else Color.Transparent,
                            RoundedCornerShape(99.dp)
                        )
                        .clickable(enabled = !editMode && !completing) {
                            completing = true
                            scope.launch {
                                delay(350)
                                PetRepository.markTodoDone(todo.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (completing) {
                        Text(text = "✓", fontSize = 12.sp, color = Color(0xFF22C55E))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // v0.67.7：正文可点——"看原文"，直达来源消息会话
                Text(
                    text = todo.action,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1917),
                    textDecoration = if (completing) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    // v0.68.20：补省略号，防长文本溢出
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                        .clickable(enabled = !editMode && !completing) {
                            openPetItemSession(context, source)
                        }
                )
                // v0.64.1：截止时间放右侧
                Text(
                    text = todo.deadline,
                    fontSize = 13.sp,
                    color = if (todo.deadline.contains("今天")) Color(0xFFFF9500)
                            else Color(0xFF8E8E93)
                )
            }
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

// v0.48.0：日程——从 v0.31.0 移除的 HomeScreen 日历逻辑迁回，由七仔统一呈现
// v0.58.0：加 description 字段，用于提取在线会议链接
data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val description: String = "",
    val allDay: Boolean,
    // v0.67.2：事件 ID，用于日历 fallback 直接打开事件详情
    val eventId: Long = 0L
)

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

// v0.63.1：验证会议 URL 有效且系统能解析，避免无效 URL 弹出应用选择器
private fun getValidMeetingUrl(context: android.content.Context, event: CalEvent): String? {
    val url = extractMeetingUrl(event) ?: return null
    if (url.isBlank()) return null
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    )
    return if (intent.resolveActivity(context.packageManager) != null) url else null
}

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

private fun loadTodayEvents(context: Context): List<CalEvent> {
    return try {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)
        val cursor = context.contentResolver.query(
            builder.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_ID
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC"
        )
        val list = mutableListOf<CalEvent>()
        cursor?.use {
            val ti = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val bi = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val ei = it.getColumnIndex(CalendarContract.Instances.END)
            val li = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val di = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val ai = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val eidi = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            while (it.moveToNext() && list.size < 20) {
                list += CalEvent(
                    title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                    begin = if (bi >= 0) it.getLong(bi) else 0L,
                    end = if (ei >= 0) it.getLong(ei) else 0L,
                    location = if (li >= 0) it.getString(li).orEmpty() else "",
                    description = if (di >= 0) it.getString(di).orEmpty() else "",
                    allDay = if (ai >= 0) it.getInt(ai) == 1 else false,
                    eventId = if (eidi >= 0) it.getLong(eidi) else 0L
                )
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

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
