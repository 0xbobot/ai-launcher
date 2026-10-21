package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.CalEvent
import com.bobot.ailauncher.data.CalendarRepository
import com.bobot.ailauncher.data.AiInsight
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bobot.ailauncher.data.WeatherState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.app.NotificationManagerCompat
import android.provider.Settings
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bobot.ailauncher.ui.theme.AILauncherType

/**
 * 体检批 C 从 PetZone.kt 拆出：TODAY 区块与消息行。纯搬移，逻辑/文案/数值逐字未动。
 */

/**
 * v0.50.0：TODAY——直接放首屏（不再用 Bottom Sheet）。
 * AI 对今天的理解：接下来 + 天气小 pill。极简，不抢 Capsule 的戏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodaySection(
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
    var hasCalPermission by remember { mutableStateOf(CalendarRepository.hasCalendarPermission(context)) }
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
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
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
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
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
                if (CalendarRepository.hasCalendarPermission(context)) {
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
            val events = withContext(Dispatchers.IO) { CalendarRepository.loadTodayEvents(context) }
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
                        fontSize = AILauncherType.Title.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.current.Title,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // v0.65.3：空态副标题按时间段区分（C 方案）；有内容时保持"七仔帮你看完了"
                        text = if (!hasAnyContent) emptySubtitle else "七仔帮你看完了",
                        fontSize = AILauncherType.Footnote.fontSize,
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
                    color = AILauncherColors.current.Card.copy(alpha = 0.5f),
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
                        fontSize = AILauncherType.Footnote.fontSize,
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
                                    Text(text = "授权日历，七仔帮你盯日程", fontSize = AILauncherType.Caption.fontSize)
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
                                            fontSize = AILauncherType.Title.fontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = AILauncherColors.current.Title
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$timeStr · $timeLabel" +
                                                (if (e.location.isNotBlank()) " · ${e.location}" else ""),
                                            fontSize = AILauncherType.Callout.fontSize,
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
                                                Text(text = "加入会议", fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
                                        fontSize = AILauncherType.Caption.fontSize,
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
                                            fontSize = AILauncherType.Caption.fontSize,
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
                                    fontSize = AILauncherType.Caption.fontSize,
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
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Title,
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
                    Text(text = "清除", fontSize = AILauncherType.Callout.fontSize, color = Color.White)
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
                    fontSize = AILauncherType.Headline.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.current.Title,
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
                                fontSize = AILauncherType.Callout.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.current.Title,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = item.text,
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = Color(0xFF636366),
                        lineHeight = 18.sp
                    )
                } else {
                    Text(
                        // v0.67.4：未接来电有号码时直接显示号码
                        text = if (isMissedCallItem(item) && item.phoneNumber.isNotBlank()) item.phoneNumber else snippet,
                        fontSize = AILauncherType.Callout.fontSize,
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
                            fontSize = AILauncherType.Caption.fontSize,
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
                    containerColor = AILauncherColors.current.Title,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isMissedCallItem(item) && item.phoneNumber.isNotBlank()) "回拨" else "去回复",
                    fontSize = AILauncherType.Footnote.fontSize,
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
                    Text(text = "清除", fontSize = AILauncherType.Callout.fontSize, color = Color.White)
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (showNumber) item.phoneNumber else snippet,
                    fontSize = AILauncherType.Callout.fontSize,
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
                        Text(text = "删除", fontSize = AILauncherType.Callout.fontSize, color = Color.White)
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
                        Text(text = "✓", fontSize = AILauncherType.Caption.fontSize, color = Color(0xFF22C55E))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // v0.67.7：正文可点——"看原文"，直达来源消息会话
                Text(
                    text = todo.action,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title,
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
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = if (todo.deadline.contains("今天")) Color(0xFFFF9500)
                            else Color(0xFF8E8E93)
                )
            }
        }
    }
}
