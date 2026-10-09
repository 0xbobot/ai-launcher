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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
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
    // v0.43.0：七仔场景演示——点按循环 WAVE→READING→WORKING；新通知到达自动进 READING
    var demoScene by remember { mutableStateOf(QizaiScene.NONE) }
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
                WeatherState.update(w.desc)
                demoScene = if (isBadWeather(w.desc)) QizaiScene.WEATHER_RAIN
                else QizaiScene.WEATHER_SUN
                PetRepository.say("早上好！今天${w.desc}，${w.temp}°")
            }
        }
    }
    // v0.47.0：七仔说话——统一信息区，自动消失
    val speech by PetRepository.speech.collectAsState()
    // v0.49.0：AI Capsule——一次只说一件最重要的事
    val capsule by PetRepository.capsule.collectAsState()
    // v0.50.0：Today 直接放首屏（不再用 Bottom Sheet）
    var capsuleExpanded by remember { mutableStateOf(false) }
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
    // v0.49.1：修闪屏——开机自动弹权限导致 Activity 闪黑，改为 TodaySheet 里手动申请
    val notifiedCalendarKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(Unit) {
        suspend fun checkCalendar() {
            // 没权限就静默跳过，用户在 TodaySheet 里手动授权
            if (!hasCalendarPermission(context)) return
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
            // v0.49.0：AI Capsule——会议是"最值得关注"的事
            val loc = if (upcoming.location.isNotBlank()) "\n地点：${upcoming.location}" else ""
            // v0.58.0（需求4）：提取在线会议链接，Capsule 显示"加入会议"
            val meetingUrl = extractMeetingUrl(upcoming)
            PetRepository.showCapsule(
                PetRepository.AiCapsule(
                    id = "cal_${upcoming.begin}",
                    timeLabel = timeStr,
                    title = "有一场会议值得关注",
                    body = "$timeStr $title$loc",
                    primaryAction = "看看重点",
                    secondaryAction = "稍后",
                    meetingUrl = meetingUrl
                )
            )
            // v0.51.2：日历只走 Capsule + TODAY，不再走整理员（避免 5 重重复）
            // 15 分钟内的会议临近黄条也删掉，Capsule 已覆盖
        }
        checkCalendar() // 首次立即查一次
        while (true) {
            delay(15 * 60 * 1000L)
            checkCalendar()
        }
    }
    // v0.25.6 P0：点按果冻（纯视觉反馈，Bob 拍板）
    var jellyTick by remember { mutableIntStateOf(0) }

    // v0.56.0 M1：眨眼已移入 PetView（8 秒 ± 3 秒随机），此处删除旧定时器

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
            // v0.51.2：会议临近黄条已删（Capsule 已覆盖，避免重复）
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
                    .align(Alignment.Center)
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
        // v0.49.0：AI Capsule——宠物下方的信息卡
        AnimatedVisibility(
            visible = capsule != null,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(dampingRatio = 0.8f)
            ) + fadeIn(),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            capsule?.let { c ->
                AiCapsuleCard(
                    capsule = c,
                    onPrimary = {
                        // v0.50.0：看看重点 → 展开看详情（Today 已在首屏）
                        capsuleExpanded = !capsuleExpanded
                    },
                    onSecondary = {
                        // 稍后 → 关闭
                        PetRepository.dismissCapsule()
                        capsuleExpanded = false
                    },
                    expanded = capsuleExpanded
                )
            }
        }
        // v0.50.0：TODAY 直接放首屏
        // v0.56.0 M5：坐卡关联——TODAY 包成磨砂卡，七仔"坐"在上面（卡片上移 30dp 压住宠物底部）
        // v0.58.0（需求1）：暖米白 #FDF8F0，圆角 28dp
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-30).dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFDF8F0).copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            // 顶部留出七仔坐的位置
            Box(modifier = Modifier.height(20.dp))
            TodaySection()
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
 * v0.49.0：AI Capsule——宠物下方的信息卡，一次只说一件最重要的事。
 * Mii 风：白卡圆角，柔和阴影；游戏化：滑入+弹性。
 * 宠物负责"表达"，Capsule 负责"信息"。
 */
@Composable
private fun AiCapsuleCard(
    capsule: PetRepository.AiCapsule,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 时间标签
            Text(
                text = capsule.timeLabel,
                fontSize = 11.sp,
                color = Color(0xFF999999)
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 标题
            Text(
                text = capsule.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 正文
            Text(
                text = capsule.body,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            // v0.58.0（需求4）：在线会议显示"加入会议"按钮
            capsule.meetingUrl?.let { url ->
                val ctx = LocalContext.current
                Button(
                    onClick = {
                        try {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) { }
                    },
                    shape = RoundedCornerShape(99.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22C55E),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "加入会议", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 主按钮：实心
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(99.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5B8DEF),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (expanded) "收起" else capsule.primaryAction,
                        fontSize = 13.sp
                    )
                }
                // 次按钮：描边
                OutlinedButton(
                    onClick = onSecondary,
                    shape = RoundedCornerShape(99.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(text = capsule.secondaryAction, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * v0.49.0：Today Bottom Sheet——AI 对今天的理解（不是 Todo List）。
 */
/**
 * v0.50.0：TODAY——直接放首屏（不再用 Bottom Sheet）。
 * AI 对今天的理解：接下来、环境。极简，不抢 Capsule 的戏。
 */
@Composable
private fun TodaySection(
    modifier: Modifier = Modifier
) {
    val weatherDesc by WeatherState.desc.collectAsState()
    val context = LocalContext.current
    var nextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // v0.59.0 AI-2：保留今日所有事件，用于消息关联日历
    var allEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var hasCalPermission by remember { mutableStateOf(hasCalendarPermission(context)) }
    val calPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalPermission = granted
    }

    LaunchedEffect(hasCalPermission) {
        if (hasCalPermission) {
            val events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            val now = System.currentTimeMillis()
            // v0.59.0 AI-2：保留所有今日事件用于关联
            allEvents = events.filter { !it.allDay }
            nextEvent = allEvents.firstOrNull { it.begin > now }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 分割线 + TODAY 标题
        Text(
            text = "TODAY",
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA),
            letterSpacing = 2.sp
        )
        // 接下来
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
            nextEvent?.let { e ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA)
                    .format(Date(e.begin))
                val mins = ((e.begin - System.currentTimeMillis()) / 60000).toInt()
                // v0.59.0 AI-2：会议链接（用于"加入会议"按钮）
                val meetingUrl = remember(e) { extractMeetingUrl(e) }
                Column {
                    Text(text = "接下来", fontSize = 13.sp, color = Color(0xFFA8A29E))
                    Spacer(modifier = Modifier.height(6.dp))
                    // v0.58.0 需求3：可点击白卡（打开系统日历）
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            CalendarContract.CONTENT_URI
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$timeStr ${e.title.ifBlank { "（无标题）" }}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1917)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "还有 $mins 分钟" +
                                        (if (e.location.isNotBlank()) " · ${e.location}" else ""),
                                    fontSize = 13.sp,
                                    color = Color(0xFF78716C)
                                )
                            }
                            Text(text = "›", fontSize = 20.sp, color = Color(0xFFD6D3D1))
                        }
                    }
                    // v0.58.0 需求4：在线会议"加入会议"按钮
                    meetingUrl?.let { url ->
                        Spacer(modifier = Modifier.height(8.dp))
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
                            shape = RoundedCornerShape(99.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "加入会议", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        // 环境
        weatherDesc?.let { w ->
            Column {
                Text(text = "环境", fontSize = 13.sp, color = Color(0xFFA8A29E))
                Spacer(modifier = Modifier.height(6.dp))
                // v0.58.0 需求3：可点击（七仔播天气）
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PetRepository.say("今天$w") }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = w,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1917),
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = "›", fontSize = 20.sp, color = Color(0xFFD6D3D1))
                    }
                }
            }
        }
        // v0.59.0：消息——AI 组织 + 4 个 AI 附加价值
        // AI-1 要不要回 / AI-2 关联日历 / AI-3 待办提取 / AI-4 群聊折叠
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
        // AI-4：按 (appName+title) 分组，判断折叠
        var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
        val groupedMsgs = remember(recentMsgs) {
            recentMsgs.groupBy { "${it.appName}|${it.title}" }
        }
        // AI-3：待办提取（最多 2 条，排除已完成）
        val todos = remember(recentMsgs, doneTodos) {
            recentMsgs.mapNotNull { item ->
                AiInsight.extractTodo(
                    id = item.id,
                    appName = item.appName,
                    title = item.title,
                    text = item.text,
                    time = item.time
                )
            }.filter { it.id !in doneTodos }
                .sortedByDescending { it.time }
                .take(2)
        }
        if (recentMsgs.isNotEmpty()) {
            val impCount = recentMsgs.count { it.cat == PetCat.IMP }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "消息", fontSize = 13.sp, color = Color(0xFFA8A29E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append("${recentMsgs.size} 条新消息")
                            if (impCount > 0) append("，$impCount 条重要")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF78716C)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // AI-4：分组显示，折叠水聊
                groupedMsgs.entries.take(5).forEach { (groupKey, items) ->
                    val first = items.first()
                    val shouldFold = remember(items) {
                        AiInsight.shouldFoldGroup(
                            items.map { AiInsight.FoldCandidate(it.title, it.text) }
                        )
                    }
                    val isExpanded = groupKey in expandedGroups
                    if (shouldFold && !isExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedGroups = expandedGroups + groupKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${first.appName} · ${first.title}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1C1917),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${items.size} 条已折叠",
                                    fontSize = 12.sp,
                                    color = Color(0xFF78716C)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "›", fontSize = 20.sp, color = Color(0xFFD6D3D1))
                            }
                        }
                    } else {
                        val displayItems = if (shouldFold) items else listOf(first)
                        displayItems.forEach { item ->
                            AiMessageRow(
                                item = item,
                                allEvents = allEvents,
                                context = context
                            )
                        }
                        if (shouldFold && isExpanded) {
                            Text(
                                text = "收起",
                                fontSize = 12.sp,
                                color = Color(0xFF78716C),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { expandedGroups = expandedGroups - groupKey }
                            )
                        }
                    }
                }
            }
        }
        // AI-3：待办小节
        if (todos.isNotEmpty()) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "待办", fontSize = 13.sp, color = Color(0xFFA8A29E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${todos.size} 项",
                        fontSize = 13.sp,
                        color = Color(0xFF78716C)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                todos.forEach { todo ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { PetRepository.markTodoDone(todo.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(
                                        2.dp,
                                        Color(0xFFD6D3D1),
                                        RoundedCornerShape(99.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = todo.action,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1C1917)
                                )
                                Text(
                                    text = "${todo.source} · ${todo.deadline}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF78716C)
                                )
                            }
                            Text(
                                text = "完成",
                                fontSize = 12.sp,
                                color = Color(0xFF22C55E)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * v0.59.0：AI 消息行（含 AI-1 需回复标签 + AI-2 日历关联）。
 * v5 设计语言：白色小卡片 + 左侧分类色条 + 右侧 › 箭头，点击打开 App。
 */
@Composable
private fun AiMessageRow(
    item: PetItem,
    allEvents: List<CalEvent>,
    context: Context
) {
    val titleColor = Color(0xFF1C1917)
    val subColor = Color(0xFF78716C)
    val arrowColor = Color(0xFFD6D3D1)
    val snippet = when {
        item.title.isNotBlank() && item.text.isNotBlank() ->
            "${item.title}：${item.text.take(30)}"
        item.title.isNotBlank() -> item.title
        item.text.isNotBlank() -> item.text.take(30)
        else -> item.sortDesc
    }
    // 分类色条
    val barColor = when (item.cat) {
        PetCat.IMP -> Color(0xFFEF4444)
        PetCat.WORK -> Color(0xFF3B82F6)
        PetCat.FUN -> Color(0xFF22C55E)
        PetCat.PRIV -> Color(0xFF9CA3AF)
    }
    // AI-1：要不要回
    val replyAnalysis = remember(item.id) {
        AiInsight.analyzeReplyNeed(item.title, item.text)
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

    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val pkg = item.packageName
                if (pkg.isNotBlank()) {
                    try {
                        context.packageManager.getLaunchIntentForPackage(pkg)
                            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.let { context.startActivity(it) }
                    } catch (_: Exception) { }
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(
                        barColor,
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (replyAnalysis.needsReply) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = Color(0xFFFFF7ED)
                        ) {
                            Text(
                                text = "需回复",
                                fontSize = 11.sp,
                                color = Color(0xFFEA580C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = snippet,
                    fontSize = 13.sp,
                    color = subColor,
                    maxLines = 1
                )
                calLinkTime?.let {
                    Text(
                        text = "和 $it 的会议相关",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
            Text(
                text = "›",
                fontSize = 20.sp,
                color = arrowColor,
                modifier = Modifier.padding(end = 14.dp)
            )
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
// v0.58.0：加 description 字段，用于提取在线会议链接
private data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val description: String = "",
    val allDay: Boolean
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
            val di = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val ai = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (it.moveToNext() && list.size < 20) {
                list += CalEvent(
                    title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                    begin = if (bi >= 0) it.getLong(bi) else 0L,
                    end = if (ei >= 0) it.getLong(ei) else 0L,
                    location = if (li >= 0) it.getString(li).orEmpty() else "",
                    description = if (di >= 0) it.getString(di).orEmpty() else "",
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
