package com.bobot.ailauncher.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.core.action.ActionEngine
import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.pet.PetPresentedCard
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import com.bobot.ailauncher.util.rebindListener
import java.text.SimpleDateFormat
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
    val densityConn = remember {
        object : NestedScrollConnection {
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

/**
 * v0.19（原型 v2 / PRD §七）：情境信息条——一次只显示一条最重要的事。
 * 当前：60 分钟内的日程 → 点按直达日历（走 ActionEngine）。无事则隐藏。
 */
@Composable
/** v0.26.0：天气条（PRD §七） */
@Composable
private fun WeatherPill(w: WeatherRepository.WeatherInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = w.city,
            fontSize = 13.sp,
            color = AILauncherColors.Title.copy(alpha = 0.7f)
        )
        Text(
            text = "${w.desc} ${w.tempC.roundToInt()}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AILauncherColors.Title
        )
    }
}

private fun AmbientInfoPill(calEvents: List<CalEvent>?) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val next = remember(calEvents, now / 60_000) {
        calEvents
            ?.filter { !it.allDay && it.begin > now && it.begin <= now + 60 * 60 * 1000 }
            ?.minByOrNull { it.begin }
    }
    if (next == null) return
    val mins = ((next.begin - now) / 60_000).toInt()
    val time = remember(next.begin) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(next.begin))
    }
    val label = if (mins <= 0) "正在进行" else "还有 $mins 分钟"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(AILauncherColors.GlassCard)
            .clickable {
                // 点按直达日历（PRD Apple 评审结论 5：Action 必须靠近结果）
                val cap = CapabilityRegistry.find("rili")
                if (cap == null) {
                    Toast.makeText(context, "本机没有日历应用", Toast.LENGTH_SHORT).show()
                    return@clickable
                }
                val res = ActionEngine.submit(
                    context,
                    ActionRequest(
                        intent = "查看日程",
                        capabilityId = cap.id,
                        riskLevel = cap.riskLevel
                    )
                )
                if (res is ActionResult.Failed) {
                    Toast.makeText(context, res.reason, Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "📅", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$time「${next.title.ifBlank { "日程" }}」$label",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(text = "›", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

/** 点击通知卡片：打开对应 App（计入常用统计） */
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


