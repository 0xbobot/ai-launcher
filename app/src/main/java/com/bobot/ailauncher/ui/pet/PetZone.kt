package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.data.WeatherState
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 宠物区：桌台（宠物本体/ding/思考点/sort-tag/carry）。
 * 放在首页顶栏下方。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetZone(
    // v0.29.0：升级提醒宠物化
    hasUpgrade: Boolean = false,
    onUpgradeTap: () -> Unit = {}
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
    var showDeliver by remember { mutableStateOf(false) }
    // v0.43.0：七仔场景演示——点按循环 WAVE→READING→WORKING；新通知到达自动进 READING
    var demoScene by remember { mutableStateOf(QizaiScene.NONE) }
    // 送达徽标显示 3 秒
    LaunchedEffect(deliverTick) {
        if (deliverTick > 0) {
            showDeliver = true
            demoScene = QizaiScene.READING // 新通知 → 七仔拿起手机看
            // v0.48.0：七仔开口——只报应用名，不念内容（守门人分寸）
            deliverApp?.let { app ->
                PetRepository.say("$app 有新消息")
            }
            delay(3000)
            showDeliver = false
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

    fun onWeatherFetched(desc: String?, allowTrigger: Boolean) {
        val old = lastWeatherDesc
        lastWeatherDesc = desc
        WeatherState.update(desc)
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
        if (w != null) onWeatherFetched(w.desc, allowTrigger)
    }

    // 天气轮询：30 分钟一次，突变才触发场景
    LaunchedEffect(Unit) {
        fetchWeather(allowTrigger = false) // 首次只更新状态，不播
        while (true) {
            delay(30 * 60 * 1000L)
            fetchWeather(allowTrigger = true)
        }
    }

    // 早晨简报：6-10 点首次进入播一次今日天气
    LaunchedEffect(Unit) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val today = todayStr()
        if (hour in 6..10 && morningBriefedDate != today) {
            morningBriefedDate = today
            val w = withContext(Dispatchers.IO) { WeatherRepository.fetchSync() }
            if (w != null) {
                lastWeatherDesc = w.desc
                WeatherState.update(w.desc)
                demoScene = if (isBadWeather(w.desc)) QizaiScene.WEATHER_RAIN
                else QizaiScene.WEATHER_SUN
                PetRepository.say("早上好！今天${w.desc}，${w.temp}°")
            }
        }
    }
    // v0.47.0：七仔说话——统一信息区，自动消失
    val speech by PetRepository.speech.collectAsState()
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
    // v0.48.0：日程轮询——15 分钟一次，30 分钟内有会就让七仔说
    val notifiedCalendarKeys = remember { mutableSetOf<String>() }
    var calendarPermissionAsked by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝就安静失败，下次轮询再看 */ }
    LaunchedEffect(Unit) {
        suspend fun checkCalendar() {
            if (!hasCalendarPermission(context)) {
                // 没权限就申请一次（v0.48.1：之前从没申请过，导致日程一直不显示）
                if (!calendarPermissionAsked) {
                    calendarPermissionAsked = true
                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
                return
            }
            val events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            val now = System.currentTimeMillis()
            val upcoming = events.firstOrNull {
                !it.allDay && it.begin in (now + 1)..(now + 30 * 60 * 1000)
            } ?: return
            val key = "${upcoming.title}|${upcoming.begin}"
            if (!notifiedCalendarKeys.add(key)) return // 同一场只提醒一次
            val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA)
                .format(Date(upcoming.begin))
            val title = upcoming.title.ifBlank { "（无标题）" }
            // 七仔开口 + 看日程场景
            PetRepository.say("$timeStr 有日程：$title")
            demoScene = QizaiScene.READING
            // 走整理员流程 + 15 分钟内进会议临近
            PetRepository.handleIncomingCalendar(upcoming.title, upcoming.begin, upcoming.location)
            if (upcoming.begin in (now + 1)..(now + 15 * 60 * 1000)) {
                PetRepository.setMeetingSoon(title)
            }
        }
        checkCalendar() // 首次立即查一次
        while (true) {
            delay(15 * 60 * 1000L)
            checkCalendar()
        }
    }
    // v0.25.6 P0：点按果冻（纯视觉反馈，Bob 拍板）
    var jellyTick by remember { mutableIntStateOf(0) }

    // 定时眨眼
    LaunchedEffect(Unit) {
        while (true) {
            delay(4100)
            blinking = true
            delay(150)
            blinking = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 桌台
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
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
            // v0.29.0：新通知送达——头顶冒出应用名小徽标（3 秒）
            if (showDeliver && deliverApp != null) {
                Text(
                    text = deliverApp!!,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .background(Color(0xFF5C8DEF), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            // v0.29.0：会议临近——七仔头顶会议提醒
            if (meetingSoon != null) {
                Text(
                    text = "15 分钟后：$meetingSoon",
                    fontSize = 11.sp,
                    color = AILauncherColors.Title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .background(Color(0xFFFFE9A8), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            // v0.47.0：七仔说话气泡——统一信息区（Mii 风+游戏化），有话就弹出来
            if (speech != null) {
                SpeechBubble(
                    text = speech!!.text,
                    visible = speechVisible,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }
            // 宠物本体
            PetView(
                mood = mood,
                mouth = mouth,
                blinking = blinking,
                sleepy = petState == PetState.SLEEPY,
                jellyTick = jellyTick,
                scene = demoScene,
                onSceneDone = { demoScene = QizaiScene.NONE },
                modifier = Modifier
                    .align(Alignment.Center)
                    // 场景播放时给宽舞台（同中心，宠物视觉大小不变）；常态保持 100dp
                    .then(
                        if (demoScene == QizaiScene.NONE) Modifier.size(100.dp)
                        else Modifier.fillMaxWidth().height(134.dp)
                    )
                    .weatherMotion(weatherDesc)
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            // v0.29.0：有升级时点按 → 弹更新对话框；否则果冻 + 场景循环演示
                            if (hasUpgrade) onUpgradeTap()
                            else {
                                jellyTick++
                                demoScene = when (demoScene) {
                                    QizaiScene.NONE -> QizaiScene.WAVE
                                    QizaiScene.WAVE -> QizaiScene.READING
                                    QizaiScene.READING -> QizaiScene.WORKING
                                    QizaiScene.WORKING -> QizaiScene.WEATHER_RAIN
                                    QizaiScene.WEATHER_RAIN -> QizaiScene.WEATHER_SUN
                                    QizaiScene.WEATHER_SUN -> QizaiScene.NONE
                                }
                            }
                        },
                        onLongClick = {
                            // v0.46.0：长按手动刷新天气——拉最新数据并播对应场景
                            scope.launch {
                                val w = withContext(Dispatchers.IO) {
                                    WeatherRepository.fetchSync()
                                }
                                if (w != null) {
                                    lastWeatherDesc = w.desc
                                    WeatherState.update(w.desc)
                                    demoScene = if (isBadWeather(w.desc))
                                        QizaiScene.WEATHER_RAIN
                                    else
                                        QizaiScene.WEATHER_SUN
                                    PetRepository.say("${w.city}${w.desc}，${w.temp}°")
                                } else {
                                    jellyTick++ // 拉取失败，给个果冻反馈
                                }
                            }
                        }
                    )
            )
            // P0：落地阴影（奶油白在暖灰底上加对比）；场景播放时隐藏（场景自带舞台）
            if (demoScene == QizaiScene.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 92.dp)
                        .size(width = 64.dp, height = 12.dp)
                        .background(
                            Color.Black.copy(alpha = 0.12f),
                            CircleShape
                        )
                )
            }
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
                    text = if (armed) "选一个操作，或右滑收回" else "左滑 → 更多操作 · 右滑 → 收到右侧",
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
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "「$appName」", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        AppUsageTracker.recordLaunch(context, packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 右侧标签栏：四个文件夹从右边缘露出一点。
 * 手势：标签左滑 = 拉进屏幕展开卡片；标签右滑 = 推出屏幕完成清空。
 * D3 打开时隐藏（避免和 A-Z rail 冲突），由调用方传 dockHidden 控制。
 */
@Composable
fun PetTabsOverlay(dockHidden: Boolean) {
    val filed by PetRepository.filed.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    AnimatedVisibility(
        visible = dockHidden,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PetCat.values().forEach { cat ->
                    val count = filed[cat]?.size ?: 0
                    var dragX by remember { mutableStateOf(0f) }
                    val empty = count == 0
                    // v0.16：系统返回手势冲突——手指按下标签时把该标签 rect 设为
                    // 系统手势排除区（小而一定被系统接受），抬起/取消后清除。
                    // rect 取 view 本地坐标（exclusionRects 要求 view 坐标系）。
                    var tabRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 56.dp)
                            .offset {
                                IntOffset(
                                    with(density) { (36.dp.toPx() + dragX).roundToInt() },
                                    0
                                )
                            }
                            .alpha(if (empty) 0.35f else 1f)
                            .background(
                                cat.color,
                                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                            )
                            .onGloballyPositioned { coords ->
                                val winPos = coords.localToWindow(Offset.Zero)
                                val loc = IntArray(2)
                                view.getLocationInWindow(loc)
                                val l = (winPos.x - loc[0]).roundToInt()
                                val t = (winPos.y - loc[1]).roundToInt()
                                tabRect = android.graphics.Rect(
                                    l, t,
                                    l + coords.size.width, t + coords.size.height
                                )
                            }
                            .pointerInput(cat, empty) {
                                // 排除区管理：按下即设，抬起/取消即清（与拖拽检测并行）
                                if (empty) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown()
                                    tabRect?.let {
                                        view.systemGestureExclusionRects = listOf(it)
                                    }
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        view.systemGestureExclusionRects = emptyList()
                                    }
                                }
                            }
                            .pointerInput(cat, empty) {
                                // 点按展开（备用入口，不与左滑冲突）
                                if (empty) return@pointerInput
                                detectTapGestures(onTap = { PetRepository.expandFromTab(cat) })
                            }
                            .pointerInput(cat, empty) {
                                if (empty) return@pointerInput
                                var accumX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumX = 0f; dragX = 0f },
                                    onDragCancel = { dragX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumX += dragAmount
                                        dragX = (dragX + dragAmount).coerceIn(-120f, 80f)
                                    },
                                    onDragEnd = {
                                        val dxDp = dragX / density.density
                                        dragX = 0f
                                        if (dxDp < -48) PetRepository.expandFromTab(cat)  // 左滑=多
                                        else if (dxDp > 48) PetRepository.completeTab(cat) // 右滑=少
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(text = cat.tabEmoji, fontSize = 15.sp)
                            Text(
                                text = cat.tabLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        if (!empty) {
                            Text(
                                text = "$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-16).dp, y = (-7).dp)
                                    .background(Color(0xFFFF4D4F), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier
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
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                Text(
                    text = text.take(shownChars),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            // 小尾巴指向七仔
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(18.dp, 10.dp)
            ) {
                val tailPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(tailPath, Color.White)
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

// v0.48.0：日程——从 v0.31.0 移除的 HomeScreen 日历逻辑迁回，由七仔统一呈现
private data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val allDay: Boolean
)

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
                CalendarContract.Instances.ALL_DAY
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
            val ai = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (it.moveToNext() && list.size < 20) {
                list += CalEvent(
                    title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                    begin = if (bi >= 0) it.getLong(bi) else 0L,
                    end = if (ei >= 0) it.getLong(ei) else 0L,
                    location = if (li >= 0) it.getString(li).orEmpty() else "",
                    allDay = if (ai >= 0) it.getInt(ai) == 1 else false
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
