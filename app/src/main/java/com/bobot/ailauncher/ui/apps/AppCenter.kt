package com.bobot.ailauncher.ui.apps

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppClassifier
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.ClassifyResult
import com.bobot.ailauncher.data.CustomCategories
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.ResolvedCapability
import com.bobot.ailauncher.data.ResolvedGroup
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/** 应用中心视图模式 */
private enum class CenterView { GROUP, AZ }

/**
 * 应用中心（v0.14）：D3 全屏内容，能力页分类 + 全部应用的融合体。
 * - Header："应用"标题 + AI 智能分类按钮 + 设置齿轮
 * - 顶部常用横滑一行（10 图标）
 * - 双视图（默认分组）：rail 顶部的"组/A"切换钮切换
 *   - 分组视图：7 组（AI/社交/出行/支付/办公/生活/购物），组头名称+数量、可折叠，
 *     长按图标整理分类（复用 OrganizeDialog）
 *   - A-Z 视图：全部应用列表 + 弧形波浪导航
 * - 数据层复用 CustomCategories（AI 智能分类结果、长按手动调整）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCenterContent(
    top10: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onPullDownToD2: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onPullDownState = rememberUpdatedState(onPullDownToD2)
    var viewMode by remember { mutableStateOf(CenterView.GROUP) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var organizeApp by remember { mutableStateOf<AppInfo?>(null) }
    val handed = remember { UiPrefs.getHanded(context) }

    // 分组数据（CustomCategories 数据层：AI 智能分类 + 长按手动调整）
    val hiddenVersion = HiddenApps.version.intValue
    val groups = remember(refreshTick, hiddenVersion) {
        val hidden = HiddenApps.getHidden(context)
        CapabilityRegistry.installedCapabilities(context)
            .map { g -> g.copy(apps = g.apps.filter { it.packageName !in hidden }) }
            .filter { it.apps.isNotEmpty() }
    }
    // 折叠态：默认全部展开
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }

    val azListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val azNested = rememberPullDownConnection(azListState, onPullDownState.value)
    val groupNested = rememberPullDownConnection(groupListState, onPullDownState.value)

    // AI 智能分类（从能力页搬过来，逻辑复用 AppClassifier）
    var classifying by remember { mutableStateOf(false) }
    var classifyProgress by remember { mutableStateOf(0 to 0) }
    fun startAiClassify() {
        if (classifying) return
        classifying = true
        classifyProgress = 0 to 0
        scope.launch {
            when (val r = AppClassifier.classifyAll(context) { done, total ->
                classifyProgress = done to total
            }) {
                is ClassifyResult.Ok -> {
                    CustomCategories.setMappings(context, r.mapping)
                    refreshTick++
                    Toast.makeText(context, "已智能分类 ${r.classifiedCount} 个应用", Toast.LENGTH_SHORT).show()
                }
                is ClassifyResult.Err -> {
                    Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                }
            }
            classifying = false
        }
    }

    fun launchResolvedApp(app: ResolvedCapability) {
        val ok = CapabilityRegistry.launchResolved(context, app)
        if (ok) AppUsageTracker.recordLaunch(context, app.packageName)
        else Toast.makeText(context, "「${app.label}」暂不可用", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Header：标题 + AI 智能分类 + 设置；顶部下滑 → D2 ----
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
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "应用",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { startAiClassify() }) {
                    Text(text = "AI 智能分类", fontSize = 13.sp, color = AILauncherColors.Hint)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.Hint
                    )
                }
            }
            Text(
                text = "常用",
                fontSize = 12.sp,
                color = AILauncherColors.Hint,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(top10, key = { it.packageName }) { app ->
                    AppIconImage(
                        drawable = app.icon,
                        contentDescription = app.label.toString(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .clickable { onLaunch(app) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- 双视图 + rail（组/A 切换钮在 rail 顶部） ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (viewMode) {
                CenterView.GROUP -> {
                    GroupListView(
                        groups = groups,
                        collapsedGroups = collapsedGroups,
                        onToggleGroup = { gid ->
                            collapsedGroups =
                                if (gid in collapsedGroups) collapsedGroups - gid
                                else collapsedGroups + gid
                        },
                        onExpandGroup = { gid -> collapsedGroups = collapsedGroups - gid },
                        onCollapseGroup = { gid -> collapsedGroups = collapsedGroups + gid },
                        onLaunch = ::launchResolvedApp,
                        onOrganize = { app ->
                            organizeApp = AppInfo(app.packageName, app.label, app.icon)
                        },
                        listState = groupListState,
                        nestedScrollConnection = groupNested,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CenterView.AZ -> {
                    AllAppsContent(
                        modifier = Modifier.fillMaxSize(),
                        listState = azListState,
                        nestedScrollConnection = azNested,
                        showSearch = false,
                        showIndexBar = true,
                        topPadding = 0.dp,
                        indexBarHeightFraction = 0.6f,
                        railHeader = {
                            RailModeToggle(
                                toAz = false,
                                onClick = { viewMode = CenterView.GROUP }
                            )
                        }
                    )
                }
            }
            // 分组视图的 rail：切换钮 + 分组快捷字
            if (viewMode == CenterView.GROUP && groups.isNotEmpty()) {
                Column(
                    modifier = Modifier.align(
                        if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd
                        else Alignment.CenterStart
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RailModeToggle(toAz = true, onClick = { viewMode = CenterView.AZ })
                    Spacer(modifier = Modifier.height(8.dp))
                    GroupShortcutRail(
                        groups = groups,
                        handed = handed,
                        onJump = { idx ->
                            scope.launch { groupListState.scrollToItem(idx) }
                        }
                    )
                }
            }
        }
    }

    // 长按整理分类 Dialog（分组视图用）
    organizeApp?.let { app ->
        OrganizeDialog(app = app, onDismiss = {
            organizeApp = null
            refreshTick++
        })
    }

    // AI 智能分类进度框
    if (classifying) {
        val (done, total) = classifyProgress
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 智能分类", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (total > 0) "正在智能分类…$done/$total" else "正在智能分类…",
                        fontSize = 14.sp,
                        color = AILauncherColors.Body
                    )
                }
            },
            confirmButton = {}
        )
    }
}

/** 列表到顶继续下滑 → D2 的嵌套滚动连接（分组/A-Z 各用一个） */
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
 * Rail 顶部切换钮：分组视图下显示 "A"（点按切到 A-Z），
 * A-Z 视图下显示 "组"（点按切回分组）
 */
@Composable
private fun RailModeToggle(toAz: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.dp, AILauncherColors.Divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (toAz) "A" else "组",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Accent
        )
    }
}

/** 分组快捷字：内置 7 组用固定字，自定义分组用名称首字 */
private fun groupShortcutChar(group: ResolvedGroup): Char = when (group.id) {
    "ai" -> '智'
    "social" -> '社'
    "travel" -> '出'
    "pay" -> '支'
    "work" -> '办'
    "life" -> '生'
    "shop" -> '购'
    else -> group.label.firstOrNull() ?: '组'
}

/**
 * 分组快捷 rail（分组视图用）：☆ + 分组快捷字等分排布，
 * 点按/纵向拖动跳转到对应分组；惯用手决定在左还是右
 */
@Composable
private fun GroupShortcutRail(
    groups: List<ResolvedGroup>,
    handed: UiPrefs.Handed,
    onJump: (Int) -> Unit,
    heightFraction: Float = 0.6f
) {
    val items = remember(groups) { listOf('☆') + groups.map(::groupShortcutChar) }
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight(heightFraction)
            .width(40.dp)
    ) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f) return@BoxWithConstraints
        // 触摸层
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(items, hPx) {
                    detectTapGestures(onTap = { offset ->
                        val i = ((offset.y / hPx) * items.size)
                            .toInt().coerceIn(items.indices)
                        activeIndex = i
                        onJump(if (i == 0) 0 else i - 1)
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
                            onJump(if (i == 0) 0 else i - 1)
                        }
                    )
                }
        )
        // 视觉层：等分排布
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEachIndexed { i, ch ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (activeIndex == i) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (activeIndex == i) AILauncherColors.Accent
                        else AILauncherColors.Title.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 分组列表视图 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupListView(
    groups: List<ResolvedGroup>,
    collapsedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onExpandGroup: (String) -> Unit,
    onCollapseGroup: (String) -> Unit,
    onLaunch: (ResolvedCapability) -> Unit,
    onOrganize: (ResolvedCapability) -> Unit,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection?,
    modifier: Modifier = Modifier
) {
    var listModifier = modifier
    if (nestedScrollConnection != null) {
        listModifier = listModifier.nestedScroll(nestedScrollConnection)
    }
    LazyColumn(
        state = listState,
        modifier = listModifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            val collapsed = group.id in collapsedGroups
            GroupBlock(
                group = group,
                collapsed = collapsed,
                onToggle = { onToggleGroup(group.id) },
                onExpand = { onExpandGroup(group.id) },
                onCollapse = { onCollapseGroup(group.id) },
                onLaunch = onLaunch,
                onOrganize = onOrganize
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * 分组块（手势签名，v0.14 纠正：左滑=多/右滑=少）：
 * - 组头：点按切换展开/收起；左滑→展开；右滑→折叠
 * - 展开：4 列图标网格（真实图标+应用名），点按启动，长按整理分类
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupBlock(
    group: ResolvedGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onLaunch: (ResolvedCapability) -> Unit,
    onOrganize: (ResolvedCapability) -> Unit
) {
    val density = LocalDensity.current
    val swipePx = with(density) { 48.dp.toPx() }
    val catColor = groupColor(group.id)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(group.id) {
                    var accumX = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; fired = false },
                        onDragCancel = { fired = true },
                        onDragEnd = {
                            if (!fired) {
                                if (accumX < -swipePx) onExpand()
                                else if (accumX > swipePx) onCollapse()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (!fired) accumX += dragAmount
                        }
                    )
                }
                .clickable { onToggle() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(catColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = group.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.Title
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = group.apps.size.toString(),
                fontSize = 12.sp,
                color = AILauncherColors.Hint
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (collapsed) "展开" else "收起",
                fontSize = 12.sp,
                color = AILauncherColors.Hint
            )
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                    dampingRatio = 0.9f
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                    dampingRatio = 0.9f
                )
            ) + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                group.apps.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = { onLaunch(app) },
                                        onLongClick = { onOrganize(app) }
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                AppIconImage(
                                    drawable = app.icon,
                                    contentDescription = app.label.toString(),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app.label.toString(),
                                    fontSize = 11.sp,
                                    color = AILauncherColors.Body,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AILauncherColors.Divider.copy(alpha = 0.6f))
        )
    }
}

/** 分组色（能力页 v4 沿用） */
fun groupColor(id: String): Color = when (id) {
    "ai" -> Color(0xFF5B8DEF)
    "social" -> Color(0xFF3CB54A)
    "travel" -> Color(0xFFE8933D)
    "pay" -> Color(0xFFB45BE8)
    "work" -> Color(0xFF4A7DDB)
    "life" -> Color(0xFF2BB5A0)
    "shop" -> Color(0xFFE86A8A)
    else -> Color(0xFF8A8A93)
}

