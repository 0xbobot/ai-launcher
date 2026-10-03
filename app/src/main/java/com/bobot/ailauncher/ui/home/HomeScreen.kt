package com.bobot.ailauncher.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.pet.PetPresentedCard
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.util.rebindListener
import java.util.Calendar

/**
 * v0.27.0：重建的极简主页——只留宠物。
 * Bob 要求：和 Dock/宠物无关的全部清除，实在不行就重建。
 * 布局：七仔居中 + 通知卡片。Dock 由 MainScreen 的 PullUpDock 单独管理。
 */
@Composable
fun HomeScreen(onOpenAppDrawer: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 上滑打开应用抽屉
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
                        return available
                    }
                    return available
                }
                if (available.y > 0f) accum = 0f
                return Offset.Zero
            }
        }
    }

    // 通知监听重绑
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

    // 日历只喂给宠物整理员，首页不展示
    var calEvents by remember { mutableStateOf<List<CalEvent>?>(null) }
    LaunchedEffect(Unit) {
        if (hasCalendarPermission(context)) {
            calEvents = loadTodayEvents(context)
        }
    }
    LaunchedEffect(calEvents) {
        val t = System.currentTimeMillis()
        (calEvents ?: emptyList())
            .firstOrNull { !it.allDay && it.begin in (t + 1)..(t + 30 * 60 * 1000) }
            ?.let { PetRepository.handleIncomingCalendar(it.title, it.begin, it.location) }
    }

    // 极简布局：只有宠物
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(drawerScrollConnection)
            .padding(horizontal = 16.dp)
    ) {
        PetZone()
        PetPresentedCard()
    }
}


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
    val density = LocalDensity.current

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

    // ---------- 日历（只喂给宠物整理员，首页不再展示） ----------
    var calEvents by remember { mutableStateOf<List<CalEvent>?>(null) } // null = 未授权
    LaunchedEffect(Unit) {
        if (hasCalendarPermission(context)) {
            calEvents = loadTodayEvents(context)
        }
    }
    // v0.16：首页不再展示日程/通知（统一纳入宠物管理），日历只用于喂给宠物整理员

    // v0.15 宠物整理员：30 分钟内开始的日程 → 重要（去重由 PetRepository 保证）
    LaunchedEffect(calEvents) {
        val t = System.currentTimeMillis()
        (calEvents ?: emptyList())
            .firstOrNull { !it.allDay && it.begin in (t + 1)..(t + 30 * 60 * 1000) }
            ?.let { PetRepository.handleIncomingCalendar(it.title, it.begin, it.location) }
    }

    // v0.26.0：天气（Open-Meteo，IP 定位免权限）
    var weather by remember { mutableStateOf<WeatherRepository.WeatherInfo?>(null) }
    LaunchedEffect(Unit) {
        weather = WeatherRepository.fetch()
    }

    // v0.26.0：信息密度（左滑=多/右滑=少，PRD §三十四）
    // 0=极简（只宠物），1=标准（+1 条信息），2=丰富（+天气+日程详情）
    var densityLevel by remember { mutableIntStateOf(1) }
    // v0.26.0：不用 remember 包裹，避免捕获旧的 densityLevel
    val densityConn = object : NestedScrollConnection {
        var accumX = 0f
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            // 只处理水平滑动
            if (kotlin.math.abs(available.x) > kotlin.math.abs(available.y) * 1.5f) {
                accumX += available.x
                val threshold = 120f // dp 转 px 简化
                if (accumX < -threshold && densityLevel < 2) {
                    // 左滑=多
                    densityLevel++
                    accumX = 0f
                    return available
                } else if (accumX > threshold && densityLevel > 0) {
                    // 右滑=少
                    densityLevel--
                    accumX = 0f
                    return available
                }
                return available
            }
            return Offset.Zero
        }
    }

    // ---------- 桌面布局：顶栏 → 情境信息条 → 宠物区 ----------
    // 底部无 Dock：页面圆点悬浮在底部；上滑手势打开悬浮卡（MainScreen / PullUpDock）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(drawerScrollConnection)
            .nestedScroll(densityConn)
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp) // 给底部悬浮的圆点 + 横线手柄留位
    ) {
        // v0.25.8：顶栏日期时间删掉（Bob：和状态栏冲突，没意义）
        // v0.26.0：按密度显示信息
        if (densityLevel >= 1) {
            // v0.19 情境信息条：一次一条最重要的事（PRD §七）
            AmbientInfoPill(calEvents)
        }
        if (densityLevel >= 2) {
            // v0.26.0：天气条
            weather?.let { w ->
                WeatherPill(w)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        // v0.15 宠物整理员：桌台 + 呈现卡片
        PetZone()
        PetPresentedCard()
        // v0.16：删除"正在进行时"区（日程大卡 + 通知流），统一纳入宠物管理
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
