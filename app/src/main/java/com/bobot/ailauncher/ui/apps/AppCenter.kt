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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.fragment.app.FragmentActivity
import com.bobot.ailauncher.data.BiometricAuth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    onBottomSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val onPullDownState = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val onBottomSwipeUpState = rememberUpdatedState(onBottomSwipeUp)
    var refreshTick by remember { mutableIntStateOf(0) }
    // v0.40.0：长按 → 真弹窗快捷菜单（与 Dock 长按同一套 AppPopupMenu，替代 bottom sheet）
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }
    // v0.40.2：惯用手设置已删除；右侧一条可见 rail + 左侧隐形触发区，左右手都可操作
    // v0.41.9：已隐藏应用版本号：变化时 A-Z 自动重算过滤
    val hiddenVersion = HiddenApps.version.intValue
    val hidden = remember(refreshTick, hiddenVersion) { HiddenApps.getHidden(context) }
    // v0.41.18（Bob）：隐藏应用不再用独立页面，放到主列表末尾的"隐藏"分组；
    // 解锁后可像正常应用一样直接打开，不用先恢复。
    // v0.41.17：隐藏区解锁状态——未解锁时分组显示锁定占位，点按才触发面部/指纹
    var hiddenUnlocked by remember { mutableStateOf(false) }
    val hiddenApps = remember(refreshTick, hiddenVersion) {
        if (hidden.isEmpty()) emptyList()
        else {
            val collator = Collator.getInstance(Locale.CHINA)
            listLaunchableApps(context)
                .filter { it.packageName in hidden }
                .sortedWith { a, b -> collator.compare(a.label.toString(), b.label.toString()) }
        }
    }

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
    // 开始搜索时滚到顶部
    val listState = rememberLazyListState()
    LaunchedEffect(searching) {
        if (searching) listState.scrollToItem(0)
    }

    val nested = rememberPullDownConnection(listState, onPullDownState.value)

    // 字母 → LazyColumn item index（每字母：N 行 + 1 组间呼吸间距）
    // v0.41.8（Bob）：字母不再独占一行（浪费纵向空间），回到每组首行行内幽灵字母；
    // 吸顶仍由悬停 overlay 实现
    val letterAnchors = remember(azGroups) {
        val m = mutableMapOf<Char, Int>()
        var idx = 0
        azGroups.forEach { (letter, apps) ->
            m[letter] = idx
            idx += apps.size + 1
        }
        m
    }

    // v0.41.8：手动吸顶——当前组首行滚出顶部后，悬停 overlay 接管显示；
    // 下一组首行接近顶部时把悬停字母往上顶走（Bob：滚动悬停/顶走）
    val stuckLetter: Char? by remember(azGroups) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val current = letterAnchors.entries
                .filter { it.value <= firstIdx }
                .maxByOrNull { it.value }
                ?.key ?: return@derivedStateOf null
            // 该组首行还在视口内时不悬停，直接用行内的真字母
            if (firstIdx > (letterAnchors[current] ?: 0)) current else null
        }
    }
    // 顶走位移：下一组首行进入顶部区域时，悬停字母被往上顶（负值）；
    // 读 firstVisibleItemScrollOffset 订阅逐像素滚动，保证顶走动画跟手。
    // 外层 Box 有 clipToBounds，顶出去的部分会被裁掉，不会跑到搜索栏上面（Bob）
    val stuckPushPx: Float by remember(azGroups) {
        derivedStateOf {
            val cur = stuckLetter ?: return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val order = letters
            val next = order.getOrNull(order.indexOf(cur) + 1) ?: return@derivedStateOf 0f
            val nextAnchor = letterAnchors[next] ?: return@derivedStateOf 0f
            val vis =
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == nextAnchor }
            val off = vis?.offset?.toFloat() ?: return@derivedStateOf 0f
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
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
        Toast.makeText(context, "已隐藏「${app.label}」", Toast.LENGTH_SHORT).show()
    }
    // v0.41.18：从隐藏分组恢复显示
    fun unhideApp(app: AppInfo) {
        HiddenApps.unhide(context, app.packageName)
        Toast.makeText(context, "「${app.label}」已恢复显示", Toast.LENGTH_SHORT).show()
    }
    // v0.41.18：解锁隐藏分组——触发面部/指纹，通过后展开
    fun unlockHiddenApps() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            Toast.makeText(context, "无法启动验证", Toast.LENGTH_SHORT).show()
            return
        }
        if (!BiometricAuth.canAuthenticate(context)) {
            Toast.makeText(context, "请先在系统设置中录入指纹或面容", Toast.LENGTH_LONG).show()
            return
        }
        BiometricAuth.authenticate(
            activity = activity,
            onSuccess = { hiddenUnlocked = true },
            onFail = {
                Toast.makeText(context, "验证未通过", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // v0.16.1：A-Z 行左滑操作同时只展开一个——列表级单态，新展开自动收起上一个
    var expandedActionsPkg by remember { mutableStateOf<String?>(null) }

    // v0.35.0：首次左滑新手引导——只展示一次，之后不再打扰
    var showActionsGuide by remember { mutableStateOf(false) }
    LaunchedEffect(expandedActionsPkg) {
        if (expandedActionsPkg != null && !UiPrefs.hasSeenRowActionsGuide(context)) {
            showActionsGuide = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- Header（v0.41.0 B 方案·呼吸感）：去大标题，搜索收成一条细线 + 设置齿轮 ----
        // 顶栏下滑 → D2；顶栏右滑 → 回记住的 Dock 档（手势签名：右滑=更少）
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
                        // 顶栏右滑 → 回到记住的 Dock 行数（手势签名：右滑=更少）
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
                // v0.21 搜索框：拼音/首字母/自然语言/模糊（PRD §三十三）
                // v0.41.9（Bob 选 B）：细线搜索 + 左侧放大镜图标
                val keyboardController =
                    androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                Column(modifier = Modifier.weight(1f)) {
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
                                contentDescription = "搜索",
                                tint = AILauncherColors.Hint,
                                modifier = Modifier.size(20.dp)
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
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = AILauncherColors.Title,
                            unfocusedTextColor = AILauncherColors.Title,
                            cursorColor = AILauncherColors.Accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(
                        color = AILauncherColors.Divider,
                        thickness = 1.dp
                    )
                }
                // v0.41.9（Bob 选 B）：设置齿轮缩小弱化，仍在右上
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.Hint.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- 单列表：分类区在上，A-Z 列表直接在下 ----
        // v0.41.8：clipToBounds——悬停字母被顶走时不画出列表区域，不会跑到搜索栏上面（Bob）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nested),
                // v0.41.7：列表不再统一 padding（之前字母头用负 offset 伸进左留白，
                // 在某些行渲染异常）；改为每项各自加正向 padding——应用行保持
                // 72dp 左缩进 + 64dp 右缩进（左隐形触发区/右 rail 避让），字母头占满宽
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // v0.40.0：底部留出手势条高度，避免末行被手势条盖住
                contentPadding = PaddingValues(bottom = 96.dp)
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
                                modifier = Modifier.padding(
                                    top = 32.dp, start = 72.dp, end = 64.dp
                                )
                            )
                        }
                    } else {
                        items(results, key = { "s:${it.packageName}" }) { app ->
                            // v0.40.0：每行自带弹窗锚点——长按弹出真菜单（与 Dock 同一套），
                            // 位置跟图标走：行在上面时菜单弹到下方（DropdownMenu 自动翻转）
                            // v0.41.7：行级 padding（列表不再统一 padding）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                            ) {
                                AppRow(
                                    app = app,
                                    onLaunch = { launchApp(app) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        popupApp = app
                                    },
                                    onHide = ::hideApp,
                                    actionsVisible = expandedActionsPkg == app.packageName,
                                    onActionsVisibleChange = { expanded ->
                                        expandedActionsPkg =
                                            if (expanded) app.packageName
                                            else if (expandedActionsPkg == app.packageName) null
                                            else expandedActionsPkg
                                    }
                                )
                                AppPopupMenu(
                                    app = app,
                                    expanded = popupApp?.packageName == app.packageName,
                                    onDismiss = { popupApp = null }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                } else {
                // v0.25.7：去掉"全部应用"标题（Bob）
                // A-Z 列表（v0.41.0 B 方案）
                // v0.41.8：每组 = 应用行 + 组间间距（字母不再独占一行，Bob）。
                // 每组首行左侧留白放幽灵字母（正向 padding，不用负 offset）；
                // 首行滚出顶部后由悬停 overlay 接管（手动吸顶，见 stuckLetter）
                azGroups.forEach { (letter, apps) ->
                    itemsIndexed(apps, key = { _, app -> "a:${app.packageName}" }) { index, app ->
                        // v0.41.7：行级 padding（列表不再统一 padding）
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (index == 0) {
                                Text(
                                    text = letter.toString(),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 40.sp,
                                    // v0.41.8：弱化（Bob：太显眼），22% → 14%
                                    color = AILauncherColors.Title.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                            ) {
                            AppRow(
                                app = app,
                                onLaunch = { launchApp(app) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    popupApp = app
                                },
                                onHide = ::hideApp,
                                actionsVisible = expandedActionsPkg == app.packageName,
                                onActionsVisibleChange = { expanded ->
                                    expandedActionsPkg =
                                        if (expanded) app.packageName
                                        else if (expandedActionsPkg == app.packageName) null
                                        else expandedActionsPkg
                                }
                            )
                            AppPopupMenu(
                                app = app,
                                expanded = popupApp?.packageName == app.packageName,
                                onDismiss = { popupApp = null }
                            )
                        }
                        }
                    }
                    item(key = "sp:$letter") { Spacer(modifier = Modifier.height(24.dp)) }
                }
                // v0.41.18（Bob）：隐藏分组——主列表末尾，像 ABCD 分组一样；
                // v0.41.20（Bob）：不显示"已隐藏"标题文字，只留幽灵图标。
                // 未解锁显示锁定占位，解锁后应用可直接打开（不用先恢复）。
                if (hiddenApps.isNotEmpty()) {
                    item(key = "hidden-header") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = if (hiddenUnlocked) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = AILauncherColors.Title.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 20.dp)
                                    .size(36.dp)
                            )
                        }
                    }
                    if (!hiddenUnlocked) {
                        // 锁定占位：点按触发面部/指纹
                        // v0.41.21（Bob）：锁定占位按应用行样式——40dp 圆角锁图标 + 15sp 文字，
                        // 与上方应用行视觉统一；点按整行触发面部/指纹解锁。
                        item(key = "hidden-locked") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { unlockHiddenApps() }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AILauncherColors.Divider),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = AILauncherColors.Hint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "点击解锁查看",
                                    fontSize = 15.sp,
                                    color = AILauncherColors.Title
                                )
                            }
                        }
                    } else {
                        // 解锁后：隐藏应用像正常应用一样直接打开
                        itemsIndexed(hiddenApps, key = { _, app -> "h:${app.packageName}" }) { _, app ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                            ) {
                                AppRow(
                                    app = app,
                                    onLaunch = { launchApp(app) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        popupApp = app
                                    },
                                    onHide = {}, // 隐藏分组内：右滑=取消隐藏
                                    onUnhide = ::unhideApp,
                                    actionsVisible = expandedActionsPkg == app.packageName,
                                    onActionsVisibleChange = { expanded ->
                                        expandedActionsPkg =
                                            if (expanded) app.packageName
                                            else if (expandedActionsPkg == app.packageName) null
                                            else expandedActionsPkg
                                    }
                                )
                                AppPopupMenu(
                                    app = app,
                                    expanded = popupApp?.packageName == app.packageName,
                                    onDismiss = { popupApp = null }
                                )
                            }
                        }
                    }
                    item(key = "sp:hidden") { Spacer(modifier = Modifier.height(24.dp)) }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                } // else 非搜索态：分类区 + A-Z 列表
            }
            // v0.41.8：悬停幽灵字母——只在当前组首行滚出顶部后显示，下一组首行
            // 接近时被顶走；外层 clipToBounds 保证不会顶到搜索栏上面（Bob）。
            // 字母弱化到 14%（Bob：太显眼），与行内字母一致。
            val sl = stuckLetter
            if (!searching && sl != null) {
                Text(
                    text = sl.toString(),
                    fontFamily = FontFamily.Serif,
                    fontSize = 40.sp,
                    color = AILauncherColors.Title.copy(alpha = 0.14f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, top = 12.dp)
                        .offset { IntOffset(0, stuckPushPx.roundToInt()) }
                )
            }
            // v0.40.0：底部上滑 → 回桌面（Bob）。
            // 系统手势导航会吃掉最底部边缘的触摸，这里在系统手势区之上放一条
            // 透明手势条：从底部向上滑过阈值就关闭应用中心，回到记住的 Dock 档。
            // 放在 Rail 之前绘制，Rail 保持可交互。
            val bottomStripPx = with(density) { 56.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(88.dp)
                    .pointerInput(Unit) {
                        var accumY = 0f
                        var fired = false
                        detectVerticalDragGestures(
                            onDragStart = { accumY = 0f; fired = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (fired) return@detectVerticalDragGestures
                                accumY += dragAmount
                                if (accumY < -bottomStripPx) {
                                    fired = true
                                    onBottomSwipeUpState.value()
                                }
                            }
                        )
                    }
            )
            // Rail：波浪字母导航（v0.24，学 Niagara/参考视频）——只做定位
            // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp）
            // v0.21：搜索态隐藏 rail（定位无意义）
            // v0.24：点按跳字母（居中）；拖动时波浪跟手，列表跟手滚动切换字母
            // v0.40.2：右侧一条可见 rail + 左侧隐形触发区（Bob）——
            // 左边不摆 rail（摆两个很奇怪），但左侧滑动同样触发字母切换，
            // 触发后是右侧 rail 亮起波浪跟随；应用列表左侧缩进给手指留空间。
            if (!searching && letters.isNotEmpty()) {
                var glideJob by remember { mutableStateOf<Job?>(null) }
                // 左侧隐形区触发时，右侧可见 rail 跟随显示波浪
                var mirrorIndex by remember { mutableStateOf<Int?>(null) }
                // Niagara 做法：直接滚完整列表，不做内容过滤；松手时列表本来就在位置上，零位移
                val glideToLetter: (Char) -> Unit = { letter ->
                    val anchor = letterAnchors[letter] ?: 0
                    glideJob?.cancel()
                    glideJob = scope.launch {
                        val cur = listState.firstVisibleItemIndex
                        if (kotlin.math.abs(anchor - cur) > 20) {
                            listState.scrollToItem(anchor)
                        } else {
                            listState.animateScrollToItem(anchor)
                        }
                    }
                }
                val stopGlide: () -> Unit = {
                    glideJob?.cancel()
                    glideJob = null
                    // 列表已在拖动中滚到位，松手无需任何操作
                }
                WaveRail(
                    letters = letters,
                    side = RailSide.RIGHT,
                    onActiveLetter = glideToLetter,
                    onRelease = stopGlide,
                    forcedActiveIndex = mirrorIndex,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                WaveRail(
                    letters = letters,
                    side = RailSide.LEFT,
                    visible = false,
                    railWidth = 56.dp,
                    onActiveLetter = { letter ->
                        mirrorIndex = letters.indexOf(letter)
                        glideToLetter(letter)
                    },
                    onRelease = { mirrorIndex = null },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                // v0.41.20（Bob）：右下角眼睛按钮已删除——隐藏分组在主列表末尾，
                // 直接滚动到底就能看到（未解锁显示锁定占位）。
            }
        }
    }



    // v0.40.0：长按 bottom sheet 已删除，改用每行自带的真弹窗菜单（AppPopupMenu）

    // v0.35.0：首次左滑新手引导——点任意处关闭，只出现一次
    if (showActionsGuide) {
        RowActionsGuideOverlay(
            onDismiss = {
                UiPrefs.setRowActionsGuideSeen(context)
                showActionsGuide = false
            }
        )
    }
    } // Box
}

/**
 * v0.35.0：行左滑操作区新手引导——一次性半透明遮罩 + 白卡片，
 * 给三个按钮各配一句短说明。点任意处关闭，看过一次后不再打扰。
 * 配色跟 App 主题走（浅暖灰系）。
 */
@Composable
private fun RowActionsGuideOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "左滑打开快捷操作",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
                GuideRow(
                    icon = Icons.Filled.Star,
                    tint = Color(0xFFC9A227),
                    title = "加到 Dock",
                    desc = "把应用固定到底部 Dock，再点一次可移出"
                )
                GuideRow(
                    icon = Icons.Filled.Info,
                    tint = Color(0xFF8A8478),
                    title = "应用信息",
                    desc = "打开系统应用信息页"
                )
                GuideRow(
                    icon = Icons.Filled.Delete,
                    tint = Color(0xFFD16A6A),
                    title = "卸载",
                    desc = "卸载这个应用"
                )
                Text(
                    text = "点任意处关闭",
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.Title
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = AILauncherColors.Hint
            )
        }
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



