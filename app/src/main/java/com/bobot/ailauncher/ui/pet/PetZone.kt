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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.bobot.ailauncher.data.WeatherRepository
import com.bobot.ailauncher.data.WeatherState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.animateFloatAsState
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
                WeatherState.updateFull(w)
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
    // v0.64.0：Capsule 三态——PREVIEW（前2行）→ POINTS（3关键点）→ FULL（全文）
    var capsuleMode by remember { mutableStateOf(CapsuleMode.PREVIEW) }
    // 新胶囊来时重置为预览态
    LaunchedEffect(capsule?.id) { capsuleMode = CapsuleMode.PREVIEW }
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
            // v0.63.1：用验证版，无效 URL 不显示按钮
            val meetingUrl = getValidMeetingUrl(context, upcoming)
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
                                    WeatherState.updateFull(w)
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
                        // v0.64.0：三态流转——PREVIEW(看看重点) → POINTS(看全文) → FULL(收起) → PREVIEW
                        capsuleMode = when (capsuleMode) {
                            CapsuleMode.PREVIEW -> CapsuleMode.POINTS
                            CapsuleMode.POINTS -> CapsuleMode.FULL
                            CapsuleMode.FULL -> CapsuleMode.PREVIEW
                        }
                    },
                    onSecondary = {
                        // 稍后 → 关闭
                        PetRepository.dismissCapsule()
                        capsuleMode = CapsuleMode.PREVIEW
                    },
                    mode = capsuleMode
                )
            }
        }
        // v0.50.0：TODAY 直接放首屏
        // v0.56.0 M5：坐卡关联——TODAY 包成磨砂卡，七仔"坐"在上面（卡片上移 30dp 压住宠物底部）
        // v0.58.0（需求1）：暖米白 #FDF8F0，圆角 28dp
        // v0.60.0：毛玻璃——白 65% + 1dp 白描边，系统壁纸透过来
        // v0.63.1：不用 Material3 Card（tonal elevation 在半透明背景上形成灰边），
        // 改用 Box + 手动柔和阴影，边缘与背景自然融合
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-30).dp)
                .heightIn(min = 120.dp) // v0.62.1：空态时不缩成一条线
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
            Column {
                // 顶部留出七仔坐的位置
                Box(modifier = Modifier.height(20.dp))
                TodaySection()
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
 * v0.64.0：Capsule 显示模式。
 * PREVIEW 默认前2行 → POINTS 看看重点（3关键点）→ FULL 全文。
 */
private enum class CapsuleMode { PREVIEW, POINTS, FULL }

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
    mode: CapsuleMode = CapsuleMode.PREVIEW
) {
    // v0.64.0：三态正文——PREVIEW 前2行 / POINTS 3关键点 / FULL 全文
    val keyPoints = remember(capsule.body) {
        com.bobot.ailauncher.data.AiInsight.extractKeyPoints(capsule.body)
    }
    val displayBody = when (mode) {
        CapsuleMode.PREVIEW ->
            capsule.body.lineSequence().take(2).joinToString("\n")
        CapsuleMode.POINTS ->
            if (keyPoints.isNotEmpty()) keyPoints.joinToString("\n")
            else capsule.body
        CapsuleMode.FULL -> capsule.body
    }
    val primaryLabel = when (mode) {
        CapsuleMode.PREVIEW -> capsule.primaryAction // "看看重点"
        CapsuleMode.POINTS -> "看全文"
        CapsuleMode.FULL -> "收起"
    }
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
            // 正文（三态）
            Text(
                text = displayBody,
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
                        text = primaryLabel,
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
 * AI 对今天的理解：接下来 + 天气小 pill。极简，不抢 Capsule 的戏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodaySection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nextEvent by remember { mutableStateOf<CalEvent?>(null) }
    // v0.59.0 AI-2：保留今日所有事件，用于消息关联日历
    var allEvents by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var hasCalPermission by remember { mutableStateOf(hasCalendarPermission(context)) }
    // v0.62.0：编辑模式 / 区块排序 / 显隐
    var editMode by remember { mutableStateOf(false) }
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
            nextEvent = allEvents.firstOrNull { it.begin > now }
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
    // v0.63.0：AI 简报式——按行动优先级渲染，不再按"接下来/消息/待办"区块
    // P0 需回复消息 / P1 会议 / P2 普通消息 / P3 折叠 / P4 待办
    val replyMsgs = remember(recentMsgs) {
        recentMsgs.filter { AiInsight.analyzeReplyNeed(it.title, it.text).needsReply }
    }
    val normalMsgs = remember(recentMsgs) {
        recentMsgs.filter { !AiInsight.analyzeReplyNeed(it.title, it.text).needsReply }
    }
    // P2/P3：普通消息的分组折叠（复用 AI-4 逻辑）
    val normalGrouped = remember(normalMsgs) {
        normalMsgs.groupBy { "${it.appName}|${it.title}" }
    }
    val meetingCount = if (TodaySection.NEXT in visibleSections && hasCalPermission && nextEvent != null) 1 else 0
    val summaryText = AiInsight.buildSummary(replyMsgs.size, meetingCount)
    // v0.63.0：空态判断——P0/P1/P2/P4 任一有内容即非空
    val hasAnyContent = (TodaySection.MSG in visibleSections && (replyMsgs.isNotEmpty() || normalMsgs.isNotEmpty())) ||
        meetingCount > 0 ||
        (TodaySection.TODO in visibleSections && todos.isNotEmpty())
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
                    onLongClick = { if (!editMode) editMode = true }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // v0.63.0：正常模式显示 AI 总结标题；编辑模式显示"编辑"提示
            if (editMode) {
                Text(
                    text = "编辑卡片",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1C1E),
                    modifier = Modifier.weight(1f)
                )
            } else {
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
                        text = "七仔帮你看完了",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
            // v0.61.0：天气 pill —— 点击开天气应用，长按看详情
            // v0.62.0：受 weatherVisible 控制显隐
            val weatherFull by WeatherState.full.collectAsState()
            if (weatherVisible) weatherFull?.let { wf ->
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
            if (editMode) {
                // v0.63.0：消息/日程/待办显隐
                listOf(
                    TodaySection.MSG to "消息",
                    TodaySection.NEXT to "日程",
                    TodaySection.TODO to "待办"
                ).forEach { (section, label) ->
                    val vis = section in visibleSections
                    TextButton(
                        onClick = { toggleSection(section, !vis) },
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (vis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = label,
                                tint = if (vis) Color(0xFF57534E) else Color(0xFFD6D3D1),
                                modifier = Modifier.size(20.dp)
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
                    contentPadding = PaddingValues(4.dp)
                ) {
                    // v0.62.2：统一用 Material Icons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (weatherVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (weatherVisible) "隐藏天气" else "显示天气",
                            tint = if (weatherVisible) Color(0xFF57534E) else Color(0xFFD6D3D1),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = "天气", fontSize = 10.sp, color = Color(0xFF8E8E93))
                    }
                }
                TextButton(onClick = { editMode = false }) {
                    Text(text = "完成", fontSize = 14.sp, color = Color(0xFF3B82F6))
                }
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

        // === P0：需回复的消息（红竖线 + 大字 + 去回复按钮 + 关联标签）===
        if (TodaySection.MSG in visibleSections) {
            replyMsgs.forEach { item ->
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
                nextEvent?.let { e ->
                    if (replyMsgs.isNotEmpty()) PriorityDivider()
                    val timeStr = SimpleDateFormat("HH:mm", Locale.CHINA)
                        .format(Date(e.begin))
                    val mins = ((e.begin - System.currentTimeMillis()) / 60000).toInt()
                    val meetingUrl = remember(e) { getValidMeetingUrl(context, e) }
                    // v0.63.0：扁平会议行（无白卡），标题大字 + 时间地点 + 加入会议按钮
                    // v0.63.3：用 CATEGORY_APP_CALENDAR 精准打开日历，不弹选择器
                    Column(
                        modifier = Modifier.clickable(enabled = !editMode) {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_MAIN
                                ).addCategory(
                                    android.content.Intent.CATEGORY_APP_CALENDAR
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    PetRepository.say("没找到日历应用")
                                }
                            } catch (_: Exception) {
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
                            text = "$timeStr · 还有 $mins 分钟" +
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

        // === P2 普通消息 + P3 折叠 ===
        if (TodaySection.MSG in visibleSections && normalGrouped.isNotEmpty()) {
            if (replyMsgs.isNotEmpty() || meetingCount > 0) PriorityDivider()
            normalGrouped.entries.take(5).forEach { (groupKey, items) ->
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
        }

        // === P4：待办 ===
        if (TodaySection.TODO in visibleSections && todos.isNotEmpty()) {
            if (replyMsgs.isNotEmpty() || meetingCount > 0 ||
                (TodaySection.MSG in visibleSections && normalGrouped.isNotEmpty())
            ) PriorityDivider()
            todos.forEach { todo ->
                key(todo.id) {
                    TodoRow(todo = todo, editMode = editMode)
                }
            }
        }

        // v0.63.0：空态（编辑模式不显示）
        if (!editMode && !hasAnyContent) {
            val hour = java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY)
            val isNight = hour >= 22 || hour < 6
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isNight) "今晚无事，好好休息 🌙" else "今日暂无安排",
                    fontSize = 13.sp,
                    color = Color(0xFFA8A29E)
                )
            }
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
    fun openSession() {
        val pi = item.pendingIntent
        var sent = false
        if (pi != null) {
            try {
                pi.send()
                sent = true
            } catch (_: android.app.PendingIntent.CanceledException) {
            } catch (_: Exception) { }
        }
        if (!sent) {
            val pkg = item.packageName
            if (pkg.isNotBlank()) {
                try {
                    context.packageManager.getLaunchIntentForPackage(pkg)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                } catch (_: Exception) { }
            }
        }
    }
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
                    color = Color(0xFF1C1C1E)
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
                        text = snippet,
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
            // 去回复按钮
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
                Text(text = "去回复", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
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
    fun openSession() {
        val pi = item.pendingIntent
        var sent = false
        if (pi != null) {
            try {
                pi.send()
                sent = true
            } catch (_: android.app.PendingIntent.CanceledException) {
            } catch (_: Exception) { }
        }
        if (!sent) {
            val pkg = item.packageName
            if (pkg.isNotBlank()) {
                try {
                    context.packageManager.getLaunchIntentForPackage(pkg)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { context.startActivity(it) }
                } catch (_: Exception) { }
            }
        }
    }
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
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, RoundedCornerShape(99.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = snippet,
                fontSize = 14.sp,
                color = Color(0xFF636366),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * v0.62.0：待办行——点左侧圆圈勾选完成（删除线 + 淡出动画后移除），左滑删除。
 */
@Composable
private fun TodoRow(
    todo: AiInsight.TodoItem,
    editMode: Boolean = false
) {
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
    Spacer(modifier = Modifier.height(6.dp))
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.5f),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todo.action,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1C1917),
                            textDecoration = if (completing) TextDecoration.LineThrough else null
                        )
                        Text(
                            text = "${todo.source} · ${todo.deadline}",
                            fontSize = 12.sp,
                            color = Color(0xFF78716C)
                        )
                    }
                }
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
