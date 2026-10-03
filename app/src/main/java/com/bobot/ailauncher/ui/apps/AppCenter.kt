package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppSearchIndex
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 应用中心（v0.22，PRD 重设计方向）：D3 全屏单列表，只干一件事——最快找到应用。
 * - 顶部：Header（"应用"标题 + 设置齿轮）
 * - 搜索框（吸顶）：拼音/首字母/自然语言/模糊四档匹配（AppSearchIndex）
 * - 下面直接连全部应用 A-Z 列表（字母分组头 + 行）
 * - Rail 只做定位：字母跳转对应字母
 * - v0.22：分类区取消（Bob 决定，等需要时再重新设计）；AllAppsScreen 死代码删除
 * - 手势签名：左滑=多 / 右滑=少
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCenterContent(
    onOpenSettings: () -> Unit,
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onPullDownState = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    var refreshTick by remember { mutableIntStateOf(0) }
    // v0.16：长按 → 常用操作 bottom sheet（安卓习惯）
    var quickActionsApp by remember { mutableStateOf<AppInfo?>(null) }
    val handed = remember { UiPrefs.getHanded(context) }

    // 已隐藏应用版本号：变化时 A-Z 自动重算过滤
    val hiddenVersion = HiddenApps.version.intValue
    val hidden = remember(refreshTick, hiddenVersion) { HiddenApps.getHidden(context) }

    // v0.22：分类区取消（等需要时再重新设计），只保留 A-Z

    // A-Z 数据：全部可启动应用（去自己、去隐藏），中文按拼音首字母排序分组
    val azApps = remember(refreshTick, hiddenVersion) {
        val collator = Collator.getInstance(Locale.CHINA)
        listLaunchableApps(context)
            .filter { it.packageName != context.packageName && it.packageName !in hidden }
            .sortedWith { a, b -> collator.compare(a.label.toString(), b.label.toString()) }
    }
    val azGroups: List<Pair<Char, List<AppInfo>>> = remember(azApps) {
        val map = linkedMapOf<Char, MutableList<AppInfo>>()
        azApps.forEach { app ->
            map.getOrPut(groupKey(app.label)) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    val letters = remember(azGroups) { azGroups.map { it.first } }

    // v0.21：应用中心搜索（PRD §三十三）——拼音/首字母/自然语言/模糊
    var query by remember { mutableStateOf("") }
    val searching = query.trim().isNotBlank()
    val searchIndex = remember(azApps) { AppSearchIndex.build(context, azApps) }
    val searchResults = remember(query, searchIndex) {
        if (query.trim().isBlank()) null
        else AppSearchIndex.search(context, query, searchIndex)
    }
    // 开始搜索时滚到顶部
    val listState = rememberLazyListState()
    LaunchedEffect(searching) {
        if (searching) listState.scrollToItem(0)
    }

    val nested = rememberPullDownConnection(listState, onPullDownState.value)

    // 字母 → LazyColumn item index（item0="全部应用"分隔，之后每字母：1 头 + N 行）
    val letterAnchors = remember(azGroups) {
        val m = mutableMapOf<Char, Int>()
        var idx = 1
        azGroups.forEach { (letter, apps) ->
            m[letter] = idx
            idx += 1 + apps.size
        }
        m
    }

    fun launchApp(app: AppInfo) {
        AppUsageTracker.recordLaunch(context, app.packageName)
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "「${app.label}」暂不可用", Toast.LENGTH_SHORT).show()
        }
    }

    // 右滑隐藏（手势签名：右滑=少），HiddenApps.version +1 后列表自动重算
    fun hideApp(app: AppInfo) {
        HiddenApps.hide(context, app.packageName)
        Toast.makeText(context, "已隐藏「${app.label}」，可在设置页恢复", Toast.LENGTH_SHORT).show()
    }

    // v0.16：长按 bottom sheet 里的"应用信息"
    fun openAppDetails(app: AppInfo) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${app.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
        }
    }

    // v0.16.1：A-Z 行左滑操作同时只展开一个——列表级单态，新展开自动收起上一个
    var expandedActionsPkg by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Header：标题 + 设置；标题区右滑/顶部下滑 → D2 ----
        val headThreshPx = with(density) { 80.dp.toPx() }
        Column(
            modifier = Modifier.pointerInput(Unit) {
                var accumY = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = { accumY = 0f; fired = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (fired) return@detectVerticalDragGestures
                        accumY += dragAmount
                        if (accumY > headThreshPx) {
                            fired = true
                            onPullDownState.value()
                        }
                    }
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp)
                    .pointerInput(Unit) {
                        // 标题区右滑 → 回到记住的 Dock 行数（手势签名：右滑=更少）
                        var accumX = 0f
                        var fired = false
                        val hPx = with(density) { 48.dp.toPx() }
                        detectHorizontalDragGestures(
                            onDragStart = { accumX = 0f; fired = false },
                            onDragCancel = { fired = true },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (fired) return@detectHorizontalDragGestures
                                accumX += dragAmount
                                if (accumX > hPx) {
                                    fired = true
                                    onHeaderSwipeRightState.value()
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "应用",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.Hint
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // v0.21 搜索框：拼音/首字母/自然语言/模糊（PRD §三十三）
            val keyboardController =
                androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "搜索应用，支持拼音/首字母/如\"打车\"",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = AILauncherColors.Hint
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                tint = AILauncherColors.Hint
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AILauncherColors.GlassCard,
                    unfocusedContainerColor = AILauncherColors.GlassCard,
                    disabledContainerColor = AILauncherColors.GlassCard,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = AILauncherColors.Title,
                    unfocusedTextColor = AILauncherColors.Title,
                    cursorColor = AILauncherColors.Accent
                ),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- 单列表：分类区在上，A-Z 列表直接在下 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nested)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val results = searchResults
                if (results != null) {
                    // 搜索态：只显示搜索结果（拼音/首字母/自然语言/模糊）
                    if (results.isEmpty()) {
                        item(key = "search-empty") {
                            Text(
                                text = "没有找到「${query.trim()}」相关的应用",
                                fontSize = 14.sp,
                                color = AILauncherColors.Hint,
                                modifier = Modifier.padding(top = 32.dp, start = 4.dp)
                            )
                        }
                    } else {
                        items(results, key = { "s:${it.packageName}" }) { app ->
                            AppRow(
                                app = app,
                                onLaunch = { launchApp(app) },
                                onLongClick = { quickActionsApp = app },
                                onHide = ::hideApp,
                                actionsVisible = expandedActionsPkg == app.packageName,
                                onActionsVisibleChange = { expanded ->
                                    expandedActionsPkg =
                                        if (expanded) app.packageName
                                        else if (expandedActionsPkg == app.packageName) null
                                        else expandedActionsPkg
                                }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                } else {
                // v0.22：分类区取消，直接 A-Z 列表
                // "全部应用"分隔（item0，rail 跳转锚点基准）
                item(key = "az-divider") {
                    Text(
                        text = "全部应用",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.Title,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
                    )
                }
                // A-Z 列表
                azGroups.forEach { (letter, apps) ->
                    item(key = "h:$letter") {
                        Text(
                            text = letter.toString(),
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    items(apps, key = { "a:${it.packageName}" }) { app ->
                        AppRow(
                            app = app,
                            onLaunch = { launchApp(app) },
                            onLongClick = { quickActionsApp = app },
                            onHide = ::hideApp,
                            actionsVisible = expandedActionsPkg == app.packageName,
                            onActionsVisibleChange = { expanded ->
                                expandedActionsPkg =
                                    if (expanded) app.packageName
                                    else if (expandedActionsPkg == app.packageName) null
                                    else expandedActionsPkg
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                } // else 非搜索态：分类区 + A-Z 列表
            }
            // Rail：只做定位——字母跳对应字母；惯用手镜像
            // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp），
            // 气泡仍在 rail 外侧紧贴边缘，允许轻微溢出绘制
            // v0.21：搜索态隐藏 rail（定位无意义）
            if (!searching && letters.isNotEmpty()) {
                val railOutX = if (handed == UiPrefs.Handed.RIGHT) 18.dp else (-18).dp
                CenterRail(
                    letters = letters,
                    handed = handed,
                    onJumpTop = { scope.launch { listState.scrollToItem(0) } },
                    onJumpLetter = { letter ->
                        scope.launch {
                            val anchor = letterAnchors[letter] ?: 0
                            val viewportH = listState.layoutInfo.viewportSize.height
                            // 字母区滚到屏幕垂直居中（方便单手操作和下一步点选），而非顶到列表顶部
                            listState.scrollToItem(anchor, scrollOffset = -(viewportH / 2))
                        }
                    },
                    modifier = Modifier
                        .align(
                            if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd
                            else Alignment.CenterStart
                        )
                        .offset(x = railOutX)
                )
            }
        }
    }

    // v0.16：长按常用操作 bottom sheet（系统快捷方式 + 应用信息）
    quickActionsApp?.let { app ->
        AppQuickActionsSheet(
            app = app,
            onDismiss = { quickActionsApp = null },
            onAppInfo = { openAppDetails(app) }
        )
    }

}

/** 列表到顶继续下滑 → D2 的嵌套滚动连接 */
@Composable
private fun rememberPullDownConnection(
    listState: LazyListState,
    onPullDown: () -> Unit
): NestedScrollConnection {
    val density = LocalDensity.current
    val onPullDownState = rememberUpdatedState(onPullDown)
    val threshPx = with(density) { 90.dp.toPx() }
    return remember(listState) {
        var accum = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && atTop) {
                    accum += available.y
                    if (accum > threshPx) {
                        accum = 0f
                        onPullDownState.value()
                    }
                    return Offset(0f, available.y)
                }
                accum = 0f
                return Offset.Zero
            }
        }
    }
}

/**
 * 应用中心定位 rail（v0.14.1：只做定位，不做视图切换）：
 * 首个 ☰ 跳回顶部整个分类区，后面 A-Z 字母跳转对应字母（应用区滚到屏幕垂直居中）；
 * 点按/纵向拖动；字母波浪避让拇指（字母列不动，波浪往拇指反方向偏移 64dp）；
 * 选中字母的大圆气泡放在 rail 外侧（远离屏幕中心），rail 内缩给气泡留位置；
 * 惯用手决定 rail 在左还是右
 */
@Composable
private fun CenterRail(
    letters: List<Char>,
    handed: UiPrefs.Handed,
    onJumpTop: () -> Unit,
    onJumpLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.6f
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val items = remember(letters) { listOf('\u2630') + letters }
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    // 右手：-1（波浪往左偏，避开右侧拇指）；左手：+1（往右偏）
    val dirSign = if (handed == UiPrefs.Handed.RIGHT) -1f else 1f
    val bulgePx = with(density) { 40.dp.toPx() }
    val shiftPx = with(density) { 64.dp.toPx() }
    val waveShiftX by animateFloatAsState(
        targetValue = if (activeIndex != null) dirSign * shiftPx else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.85f
        ),
        label = "railWaveShift"
    )
    // 外侧 = 远离屏幕中心的一侧：右手 rail 在右，外侧=右；左手镜像
    val rightHanded = handed == UiPrefs.Handed.RIGHT

    fun poke(i: Int) {
        activeIndex = i
        if (i == 0) onJumpTop() else onJumpLetter(items[i])
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(600)
            activeIndex = null
        }
    }

    // 外层 76dp：内侧 48dp 是 rail 本体（含触摸），外侧 28dp 给气泡留位置（rail 内缩）
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(heightFraction)
            .width(76.dp)
    ) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f) return@BoxWithConstraints
        val rowHpx = hPx / items.size
        // rail 本体：内侧 48dp
        Box(
            modifier = Modifier
                .align(if (rightHanded) Alignment.CenterStart else Alignment.CenterEnd)
                .width(48.dp)
                .fillMaxHeight()
        ) {
            // 触摸层：整块可触摸（点按 + 纵向拖动），不偏移
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(items, hPx) {
                        detectTapGestures(onTap = { offset ->
                            val i = ((offset.y / hPx) * items.size)
                                .toInt().coerceIn(items.indices)
                            poke(i)
                        })
                    }
                    .pointerInput(items, hPx) {
                        detectVerticalDragGestures(
                            onDragEnd = { activeIndex = null },
                            onDragCancel = { activeIndex = null },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val i = ((change.position.y / hPx) * items.size)
                                    .toInt().coerceIn(items.indices)
                                activeIndex = i
                                if (i == 0) onJumpTop() else onJumpLetter(items[i])
                            }
                        )
                    }
            )
            // 视觉层：字母列不动；波浪（当前字母放大）单独往拇指反方向偏移
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(40.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEachIndexed { i, ch ->
                    val d = if (activeIndex != null) (i - activeIndex!!).toFloat() else 999f
                    val gTarget = if (activeIndex != null) exp(-(d * d) / 15.68f) else 0f
                    val g by animateFloatAsState(
                        targetValue = gTarget,
                        animationSpec = tween(120),
                        label = "railG"
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.toString(),
                            fontSize = 12.sp,
                            color = if (i == 0 || g > 0.5f) AILauncherColors.Accent
                            else AILauncherColors.Title.copy(alpha = 0.85f),
                            fontWeight = if (g > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.White.copy(alpha = 0.6f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            modifier = Modifier.graphicsLayer {
                                // 高斯波浪：越靠近手指越大；波浪整体再往拇指反方向避让
                                translationX = dirSign * bulgePx * g + waveShiftX * g
                                val sc = 1f + g
                                scaleX = sc
                                scaleY = sc
                            }
                        )
                    }
                }
            }
        }
        // 选中大圆气泡：rail 外侧（远离屏幕中心），不跟波浪偏移，不被拇指盖住；
        // v0.15.1 修：用 Top 对齐 + 偏移量，保证气泡垂直居中对准当前字母（波浪峰顶），
        // 之前用 Center 对齐再叠加偏移，气泡会被推到字母下方
        activeIndex?.let { idx ->
            val rPx = with(density) { 22.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(if (rightHanded) Alignment.TopEnd else Alignment.TopStart)
                    .offset {
                        IntOffset(
                            0,
                            (idx * rowHpx + rowHpx / 2f - rPx).roundToInt()
                        )
                    }
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AILauncherColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = items[idx].toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

