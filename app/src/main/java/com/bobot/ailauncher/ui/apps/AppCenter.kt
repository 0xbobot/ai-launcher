package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.text.style.TextOverflow
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
        // 拼音首字母分组：用字典（20924 字），GB2312 区位码做兜底
        val pinyinOf: (Char) -> Char = { c ->
            val fromDict = AppSearchIndex.pinyinInitialOf(context, c)
            if (fromDict != '#') fromDict else pinyinInitial(c)
        }
        azApps.forEach { app ->
            map.getOrPut(groupKey(app.label, pinyinOf)) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    val letters = remember(azGroups) { azGroups.map { it.first } }

    // v0.21：应用中心搜索（PRD §三十三）——拼音/首字母/自然语言/模糊
    var query by remember { mutableStateOf("") }
    val searching = query.trim().isNotBlank()
    // v0.25.5：Niagara 式——拖动直接滚完整列表，不做内容过滤，松手零位移
    val searchIndex = remember(azApps) { AppSearchIndex.build(context, azApps) }
    val searchResults = remember(query, searchIndex) {
        if (query.trim().isBlank()) null
        else AppSearchIndex.search(context, query, searchIndex)
    }
    // v0.25.3：最近使用横条（搜索下方），取高频应用
    val recentApps = remember(azApps, refreshTick) {
        AppUsageTracker.topApps(context, azApps, count = 8, smartSort = true)
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

    // v0.25.2：松手滚动——等列表恢复全量重组完成后再滚，避免打在旧内容上
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
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
                        "搜索应用，支持拼音/首字母",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            // v0.25.3：最近使用横条（搜索下方）——只显示图标，一行排满
            if (!searching && recentApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentApps.forEach { app ->
                        val iconBitmap = remember(app.packageName) {
                            try {
                                app.icon.toBitmap().asImageBitmap()
                            } catch (_: Exception) { null }
                        }
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = app.label.toString(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .combinedClickable(
                                        onClick = { launchApp(app) },
                                        onLongClick = { quickActionsApp = app }
                                    )
                            )
                        }
                    }
                }
            }
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
            // Rail：波浪字母导航（v0.24，学 Niagara/参考视频）——只做定位
            // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp）
            // v0.21：搜索态隐藏 rail（定位无意义）
            // v0.24：点按跳字母（居中）；拖动时波浪跟手，列表跟手滚动切换字母
            if (!searching && letters.isNotEmpty()) {
                WaveRail(
                    letters = letters,
                    handed = handed,
                    onActiveLetter = { letter ->
                        // Niagara 做法：直接滚完整列表，不做内容过滤；
                        // 松手时列表本来就在位置上，零位移
                        scope.launch {
                            val anchor = letterAnchors[letter] ?: 0
                            listState.scrollToItem(anchor)
                        }
                    },
                    onRelease = {
                        // 列表已在拖动中滚到位，松手无需任何操作
                    },
                    modifier = Modifier
                        .align(
                            if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd
                            else Alignment.CenterStart
                        )
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

