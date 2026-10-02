package com.bobot.ailauncher.ui.home

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.bobot.ailauncher.util.rebindListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 今日日历事件 */
private data class CalEvent(val title: String, val begin: Long, val location: String)

@Composable
fun HomeScreen(onOpenAppDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val notifications by NotificationRepository.notifications.collectAsState()
    val listenerConnected by NotificationRepository.isConnected.collectAsState()

    // ---------- 上滑打开应用抽屉：列表在顶部且上滑累计超过 120dp ----------
    val listState = rememberLazyListState()
    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    val swipeThresholdPx = with(density) { 120.dp.toPx() }
    val drawerScrollConnection = remember {
        object : NestedScrollConnection {
            var accum = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y < 0f && atTop) {
                    accum += -available.y
                    if (accum >= swipeThresholdPx) {
                        accum = 0f
                        openDrawerState()
                        return available // 吞掉本次手势，不让列表滚动
                    }
                    return available // 累计中也吞掉，避免列表跟着动
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

    // ---------- 语音输入：系统 RecognizerIntent（国产机无 Google 语音服务也能用自带识别） ----------
    var micVisible by remember { mutableStateOf(true) }
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
    fun startVoiceInput() {
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

    // ---------- 意图提交：有 Key 走 LLM，无 Key 走关键词 ----------
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
                val apiKey = LlmConfig.getApiKey(context)
                val baseUrl = LlmConfig.getBaseUrl(context)
                val model = LlmConfig.getModel(context)
                val result = withContext(Dispatchers.IO) {
                    LlmRouter.route(baseUrl, apiKey, model, text, CapabilityRegistry.validIds())
                }
                if (result != null && result.capabilityId != "none") {
                    val ok = CapabilityRegistry.resolveAndLaunch(
                        context, result.capabilityId, result.params
                    )
                    if (result.reply.isNotBlank()) {
                        Toast.makeText(context, result.reply, Toast.LENGTH_SHORT).show()
                    } else if (!ok) {
                        Toast.makeText(context, "能力暂不可用", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    keywordFallback(text)
                }
            } catch (_: Exception) {
                keywordFallback(text)
            } finally {
                thinking = false
                input = ""
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(drawerScrollConnection),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 问候 + 应用抽屉兜底入口（紧凑排版）
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.Title
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = todayText(),
                        fontSize = 12.sp,
                        color = AILauncherColors.Hint
                    )
                }
                TextButton(onClick = onOpenAppDrawer) {
                    Icon(
                        Icons.Filled.Apps,
                        contentDescription = null,
                        tint = AILauncherColors.Hint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("应用", color = AILauncherColors.Hint, fontSize = 14.sp)
                }
            }
        }
        // 意图输入框（压缩高度）
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
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
                                contentDescription = "语音输入",
                                tint = AILauncherColors.Hint
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
                    Text("思考中…", fontSize = 13.sp, color = AILauncherColors.Hint)
                }
            }
        }
        // 正在进行
        item {
            Text(
                text = "正在进行",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.Title
            )
        }
        // 今日日程（日历）
        when {
            calEvents == null -> item {
                TextButton(
                    onClick = {
                        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "授权日历后显示今日日程",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                }
            }
            calEvents!!.isNotEmpty() -> items(calEvents!!, key = { it.begin }) { e ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.AccentSoft),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = e.title.ifBlank { "（无标题）" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AILauncherColors.Title
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row {
                            Text(
                                text = formatTime(e.begin),
                                fontSize = 13.sp,
                                color = AILauncherColors.Body
                            )
                            if (e.location.isNotBlank()) {
                                Text(
                                    text = " · ${e.location}",
                                    fontSize = 13.sp,
                                    color = AILauncherColors.Body
                                )
                            }
                        }
                    }
                }
            }
        }
        // 通知卡片流
        if (!isNotificationAccessGranted(context)) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
            }
        } else if (notifications.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = if (!listenerConnected) "正在连接通知服务…" else "暂无进行中的事项",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(notifications, key = { it.packageName + it.time }) { n ->
                NotificationCard(n)
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** 演示版意图路由：关键词 → capabilityId（无 Key 时的降级路径） */
private fun routeKeyword(raw: String): String? {
    val q = raw.lowercase(Locale.ROOT)
    return when {
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

/** 读取今天 0 点 ~ 24 点的日历事件，取前 3 个 */
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
                CalendarContract.Instances.EVENT_LOCATION
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC"
        )
        val list = mutableListOf<CalEvent>()
        cursor?.use {
            val ti = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val bi = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val li = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            while (it.moveToNext() && list.size < 3) {
                list += CalEvent(
                    title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                    begin = if (bi >= 0) it.getLong(bi) else 0L,
                    location = if (li >= 0) it.getString(li).orEmpty() else ""
                )
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
}

private fun todayText(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())

private fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(time))

@Composable
private fun NotificationCard(n: SimpleNotification) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    maxLines = 2
                )
            }
        }
    }
}
