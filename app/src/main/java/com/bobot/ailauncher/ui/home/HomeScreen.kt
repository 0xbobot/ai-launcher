package com.bobot.ailauncher.ui.home

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import com.bobot.ailauncher.data.AppUsageTracker
import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SimpleNotification
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import com.bobot.ailauncher.util.rebindListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 今日日历事件 */
private data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val allDay: Boolean
)

@Composable
fun HomeScreen(onOpenAppDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val notifications by NotificationRepository.notifications.collectAsState()
    val listenerConnected by NotificationRepository.isConnected.collectAsState()

    // ---------- 上滑打开应用抽屉：中间内容在顶部且上滑累计超过 120dp ----------
    val middleScrollState = rememberScrollState()
    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    val drawerScrollConnection = remember {
        object : NestedScrollConnection {
            var accum = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = middleScrollState.value == 0
                if (available.y < 0f && atTop) {
                    accum += -available.y
                    if (accum >= swipeThresholdPx) {
                        accum = 0f
                        openDrawerState()
                        return available // 吞掉本次手势，不让内容滚动
                    }
                    return available // 累计中也吞掉，避免内容跟着动
                }
                if (available.y > 0f) accum = 0f // 换向清零
                return Offset.Zero
            }
        }
    }

    // ---------- 通知监听：从设置页返回时若已授权但服务未连接，强制重绑 ----------
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isNotificationAccessGranted(context) &&
                    !NotificationRepository.isConnected.value
                ) {
                    rebindListener(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---------- 语音输入：优先 in-app SpeechRecognizer（国产机无系统识别 Activity 也能用） ----------
    var micVisible by remember { mutableStateOf(true) }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) input = text
        }
    }

    fun stopVoice() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    // 老路径降级：系统 RecognizerIntent（in-app 不可用时）
    fun startVoiceLegacy() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想干嘛")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            micVisible = false
            Toast.makeText(context, "当前设备不支持语音识别", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "语音识别启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun startInAppVoice() {
        stopVoice()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            startVoiceLegacy()
            return
        }
        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，请再说一次"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_SERVER -> "网络异常，请重试"
                        else -> "语音识别失败"
                    }
                    stopVoice()
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    stopVoice()
                    if (!text.isNullOrBlank()) input = text
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer = sr
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            sr.startListening(intent)
        } catch (_: Exception) {
            stopVoice()
            startVoiceLegacy()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startInAppVoice()
        else Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
    }

    fun startVoiceInput() {
        if (listening) {
            stopVoice()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startInAppVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    // ---------- 日历 ----------
    var calEvents by remember { mutableStateOf<List<CalEvent>?>(null) } // null = 未授权
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calEvents = if (granted) loadTodayEvents(context) else emptyList()
    }
    LaunchedEffect(Unit) {
        if (hasCalendarPermission(context)) {
            calEvents = loadTodayEvents(context)
        }
    }
    // 日历三层视觉：下一个日程（大卡片）/ 全天事件（一行小字）/ 其余后续（小行）
    val now = System.currentTimeMillis()
    val timedEvents = remember(calEvents) {
        (calEvents ?: emptyList()).filter { !it.allDay }.sortedBy { it.begin }
    }
    val allDayEvents = remember(calEvents) {
        (calEvents ?: emptyList()).filter { it.allDay }
    }
    val nextEvent = remember(timedEvents, now) {
        timedEvents.firstOrNull { it.begin > now } ?: allDayEvents.firstOrNull()
    }
    val laterEvents = remember(timedEvents, now, nextEvent) {
        timedEvents.filter { it.begin > now && it != nextEvent }.take(3)
    }

    // ---------- 意图提交：无 Key 走关键词演示版，有 Key 直连大模型问答 ----------
    var chatQuestion by remember { mutableStateOf<String?>(null) }
    var chatAnswer by remember { mutableStateOf<String?>(null) }

    fun keywordFallback(text: String) {
        val capId = routeKeyword(text)
        val ok = capId?.let { CapabilityRegistry.resolveAndLaunch(context, it) } ?: false
        if (!ok) {
            Toast.makeText(context, "已收到意图\"$text\"", Toast.LENGTH_SHORT).show()
        }
    }

    fun submit() {
        val text = input.trim()
        if (text.isBlank() || thinking) return
        if (!LlmConfig.hasKey(context)) {
            keywordFallback(text)
            input = ""
            return
        }
        thinking = true
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    LlmRouter.chat(
                        LlmConfig.getBaseUrl(context),
                        LlmConfig.getApiKey(context),
                        LlmConfig.getModel(context),
                        text
                    )
                }
                when (res) {
                    is LlmRouter.LlmChatResult.Ok -> {
                        chatQuestion = text
                        chatAnswer = res.text
                        input = ""
                    }
                    is LlmRouter.LlmChatResult.Err -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "请求失败，请重试", Toast.LENGTH_SHORT).show()
            } finally {
                thinking = false
            }
        }
    }

    // ---------- 桌面感布局：大时钟 → 意图框 → 正在进行时（中间可滚） ----------
    // 底部无 Dock：页面圆点悬浮在底部；上滑手势打开悬浮卡（MainScreen / PullUpDock）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(drawerScrollConnection)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp) // 给底部悬浮的圆点 + 横线手柄留位
    ) {
        ClockHeader()
        Spacer(modifier = Modifier.height(10.dp))
        // 意图输入框（玻璃拟态）
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, AILauncherColors.GlassBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = AILauncherColors.Accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("想做什么，直接告诉我…", color = AILauncherColors.Hint) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) keyboardController?.hide() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "清空",
                                    tint = AILauncherColors.Hint
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (micVisible) {
                    IconButton(onClick = { startVoiceInput() }) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = if (listening) "停止语音输入" else "语音输入",
                            tint = if (listening) AILauncherColors.Accent
                            else AILauncherColors.Hint
                        )
                    }
                }
            }
        }
        if (thinking) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = AILauncherColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "思考中…",
                    fontSize = 13.sp,
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), shadow = GlassTextShadow)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        // 正在进行时（中间可滚动区域）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(middleScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "正在进行",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(color = Color.White, shadow = GlassTextShadow)
            )
            // 今日日程：未授权 → 授权入口；有下一个日程 → 大卡片
            if (calEvents == null) {
                TextButton(
                    onClick = {
                        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "授权日历后显示今日日程",
                        fontSize = 13.sp,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.85f),
                            shadow = GlassTextShadow
                        )
                    )
                }
            } else if (nextEvent != null) {
                NextEventCard(event = nextEvent, now = now)
            }
            allDayEvents.forEach { e ->
                Text(
                    text = "全天 · ${e.title.ifBlank { "（无标题）" }}",
                    fontSize = 12.sp,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        shadow = GlassTextShadow
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            laterEvents.forEach { e ->
                Text(
                    text = "${formatTime(e.begin)}  ${e.title.ifBlank { "（无标题）" }}",
                    fontSize = 12.sp,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        shadow = GlassTextShadow
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            // 通知卡片流（可点击：打开对应 App）
            if (!isNotificationAccessGranted(context)) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, AILauncherColors.GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "开启通知读取后，「正在进行时」才能把会议、快递、消息主动浮上来。",
                            fontSize = 14.sp,
                            color = AILauncherColors.Body
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) {
                            Text("去开启", color = AILauncherColors.Accent)
                        }
                    }
                }
            } else if (notifications.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, AILauncherColors.GlassBorder)
                ) {
                    Text(
                        text = if (!listenerConnected) "正在连接通知服务…" else "暂无进行中的事项",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                notifications.forEach { n ->
                    NotificationCard(n = n, onOpen = {
                        openApp(context, n.packageName, n.appName)
                    })
                }
            }
        }
    }

    // 聆听中 Dialog
    if (listening) {
        AlertDialog(
            onDismissRequest = { stopVoice() },
            title = { Text("正在聆听…") },
            text = { Text("说出你想做什么，说完会自动识别") },
            confirmButton = {
                TextButton(onClick = { stopVoice() }) { Text("取消") }
            }
        )
    }

    // 大模型回答 Dialog（可滚动 + 复制）
    val clipboardManager = LocalClipboardManager.current
    if (chatAnswer != null) {
        AlertDialog(
            onDismissRequest = { chatQuestion = null; chatAnswer = null },
            title = { Text(chatQuestion.orEmpty(), fontSize = 16.sp) },
            text = {
                Text(
                    text = chatAnswer.orEmpty(),
                    fontSize = 14.sp,
                    color = AILauncherColors.Body,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(chatAnswer.orEmpty()))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatQuestion = null; chatAnswer = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

/** 桌面 widget 感的大时钟 + 日期（每 20 秒刷新一次） */
@Composable
private fun ClockHeader() {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val time = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(nowMs))
    }
    Column(modifier = Modifier.padding(top = 28.dp, bottom = 2.dp)) {
        Text(
            text = time,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(color = Color.White, shadow = GlassTextShadow)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = todayText(),
            fontSize = 14.sp,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                shadow = GlassTextShadow
            )
        )
    }
}

/** 点击通知卡片：打开对应 App（计入常用统计） */
private fun openApp(context: Context, packageName: String, appName: String) {
    try {
        AppUsageTracker.recordLaunch(context, packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
    }
}

/** 演示版意图路由：关键词 → capabilityId（无 Key 时的降级路径） */
private fun routeKeyword(raw: String): String? {
    val q = raw.lowercase(Locale.ROOT)
    return when {
        q.contains("导航") -> "daohang"
        q.contains("打车") || q.contains("叫车") -> "dache"
        q.contains("地铁") -> "ditie"
        q.contains("火车") -> "huoche"
        q.contains("航班") || q.contains("飞机") -> "hangban"
        q.contains("酒店") -> "jiudian"
        else -> null
    }
}

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

/** 读取今天 0 点 ~ 24 点的日历事件（含全天标记），按开始时间排序 */
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

private fun todayText(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(time: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
        .format(timeFormatter)

/** 倒计时文案：<60 分钟显示分钟，否则显示"X 小时 Y 分" */
private fun countdownText(begin: Long, now: Long): String {
    val mins = Duration.between(
        Instant.ofEpochMilli(now), Instant.ofEpochMilli(begin)
    ).toMinutes().coerceAtLeast(0)
    return if (mins < 60) "还有 $mins 分钟"
    else "还有 ${mins / 60} 小时 ${mins % 60} 分"
}

/** 下一个日程：大卡片突出（标题 / 时间 / 地点 / 倒计时）——玻璃拟态
 * 手势签名：右滑展开（详情 + 操作），左滑收起；高度弹簧动画 */
@Composable
private fun NextEventCard(event: CalEvent, now: Long) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AILauncherColors.GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .swipeExpandCollapse { expanded = it }
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.9f
                    )
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "接下来",
                    fontSize = 12.sp,
                    color = AILauncherColors.Accent,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = countdownText(event.begin, now),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Accent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.title.ifBlank { "（无标题）" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.Title
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    text = if (event.allDay) "全天"
                    else "${formatTime(event.begin)} – ${formatTime(event.end)}",
                    fontSize = 13.sp,
                    color = AILauncherColors.Body
                )
                if (event.location.isNotBlank()) {
                    Text(
                        text = " · ${event.location}",
                        fontSize = 13.sp,
                        color = AILauncherColors.Body
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (event.location.isNotBlank()) {
                    Text(
                        text = "地点：${event.location}",
                        fontSize = 13.sp,
                        color = AILauncherColors.Body
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                TextButton(onClick = {
                    Toast.makeText(context, "在日历中打开（待接入）", Toast.LENGTH_SHORT).show()
                }) {
                    Text("在日历中打开", color = AILauncherColors.Accent)
                }
            }
        }
    }
}

/** 通知卡片：右滑展开（全文 + 操作按钮），左滑收起；高度弹簧动画 */
@Composable
private fun NotificationCard(n: SimpleNotification, onOpen: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AILauncherColors.GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .swipeExpandCollapse { expanded = it }
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.9f
                    )
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = n.appName,
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(n.time),
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (n.title.isNotBlank()) {
                Text(
                    text = n.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
            }
            if (n.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = n.text,
                    fontSize = 13.sp,
                    color = AILauncherColors.Body,
                    maxLines = if (expanded) Int.MAX_VALUE else 2
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) {
                        Text("打开应用", color = AILauncherColors.Accent)
                    }
                    TextButton(onClick = {
                        NotificationRepository.dismiss(n)
                    }) {
                        Text("忽略", color = AILauncherColors.Hint)
                    }
                }
            }
        }
    }
}

/**
 * 手势签名（全 App 统一）：右滑 = 更多（展开），左滑 = 更少（收起）。
 * 横向专用检测（touch slop 后才消费），与纵向滚动不冲突；点按不受影响。
 */
@Composable
private fun Modifier.swipeExpandCollapse(
    onExpandedChange: (Boolean) -> Unit
): Modifier {
    val density = LocalDensity.current
    val threshPx = remember(density) { with(density) { 48.dp.toPx() } }
    val latestChange = rememberUpdatedState(onExpandedChange)
    return this.pointerInput(Unit) {
        var accumX = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { accumX = 0f; fired = false },
            onDragCancel = { fired = true },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                if (fired) return@detectHorizontalDragGestures
                accumX += dragAmount
                if (abs(accumX) > threshPx) {
                    fired = true
                    latestChange.value(accumX > 0)
                }
            }
        )
    }
}
