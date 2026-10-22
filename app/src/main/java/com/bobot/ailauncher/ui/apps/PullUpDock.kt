package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.systemGestureBottomPx
import kotlin.math.abs
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 上拉 Dock（v0.14：手势签名纠正为「左滑=多，右滑=少」，横线手柄彻底去掉）。
 *
 * 状态机（全部离散档位，弹簧动画切换，不再连续拖动）：
 * - Hidden：什么都不显示
 * - D1：悬浮玻璃卡（iOS dock 式：左右 16dp、底部 16dp，严格居中），4 个常用大图标（56dp，卡内垂直居中）
 * - D2：同一张卡片，10 个小图标（2×5，46dp）；d1↔d2 内容 crossfade + scale
 * - D3：不透明整屏替换（App 底色），顶部常用横滑一行 + 全部应用 A-Z 列表 + 弧形 A-Z 导航
 *
 * 手势签名（全 App 统一，v0.14 纠正）：左滑 = 更多，右滑 = 更少
 * - D1 —左滑→ D2 —左滑→ D3；D3 —右滑→ D2 —右滑→ D1
 * - v0.16：隐藏的唯一入口是 D1 右滑（弹确认框，"不再提醒"可记）；D1 下滑不再隐藏；
 *   点按卡片外部不再隐藏（D2→D1 收一档，D1 无动作）
 * - 上滑：隐藏→记住的行数（首页上滑）；D1/D2→D3（直接全屏）
 * - 下滑：D3→D2→D1（逐级收回）；点按卡片外部 → D2 收回 D1
 */
enum class DockState { Hidden, D1, D2, D3 }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PullUpDock(
    state: DockState,
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onD3SwipeUpToHome: () -> Unit,
    onHideDockRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    // R2 UI（10-21 新规格）：应用中心「问七仔」——回首页并提交协调器（HomeHost 提供）
    onAskQizai: (String) -> Unit = {},
    // v0.68.99（主页重构 R-1）：Dock 左端七仔格点按 = 进入输入态；
    // 输入态在场时 D1/D2 卡让位（cardVisible 关），胶囊底部原地接管
    onPetCellTap: () -> Unit = {},
    intentInputMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val onStateChangeState = rememberUpdatedState(onStateChange)
    val onD3SwipeRightState = rememberUpdatedState(onD3SwipeRight)
    val onD3SwipeUpToHomeState = rememberUpdatedState(onD3SwipeUpToHome)
    val onHideDockRequestState = rememberUpdatedState(onHideDockRequest)
    val onAskQizaiState = rememberUpdatedState(onAskQizai)

    // v0.68.95（F-03）：清单改挂 AppCatalog 仓库快照——此前无 key 的 remember{}
    // 进程内只算一次，新装应用永不进 Dock、卸载的点开只剩 toast。现与 .88 同源：
    // InstalledApps.epoch 变化即触发后台重建，快照到了自动换表（IO 构建，
    // 组合期只读）。快照未就绪时为空表，构建完成即补上（与意图层懒建同口径）。
    val installedEpoch by InstalledApps.epoch.collectAsState()
    LaunchedEffect(installedEpoch) { AppCatalog.ensure(context) }
    val catalog by AppCatalog.snapshot.collectAsState()
    val allApps = remember(catalog) {
        catalog?.apps?.filter { it.packageName != context.packageName } ?: emptyList()
    }
    var usageTick by remember { mutableIntStateOf(0) }
    // v0.20：智能排序开关（设置页可关）；key 里带上开关值，切换后返回即生效
    val smartSort = UiPrefs.getDockSmartSort(context)
    // v0.35.0：用户置顶——排最前（按用户自定义顺序），后面用智能排序补齐并去重
    val dockTick by UiPrefs.dockTick.collectAsState()
    val pinnedPkgs = remember(dockTick) { UiPrefs.getDockPinned(context) }
    val top10 = remember(allApps, usageTick, smartSort, dockTick) {
        val pinned = pinnedPkgs.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
        val rest = AppUsageTracker.topApps(
            context,
            allApps.filter { it.packageName !in pinnedPkgs },
            10,
            smartSort
        )
        (pinned + rest).take(10)
    }
    val top4 = remember(top10) { top10.take(4) }

    fun launchApp(app: AppInfo) {
        AppUsageTracker.recordLaunch(context, app.packageName)
        usageTick++
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
            else IslandToasts.show("无法打开「${app.label}」")
        } catch (_: Exception) {
            IslandToasts.show("无法打开「${app.label}」")
        }
    }

    // v0.39.0：Dock 长按 → 真弹窗快捷菜单（锚定在图标上）
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // v0.68.73（Bob）：横屏时 Dock 收拢，不横贯全屏
        val isLandscapeDock = maxWidth > maxHeight
        // v0.68.99：输入态下卡片让位（意图胶囊在底部原地接管，Dock 内容不与
        // 之叠画）——让位只针对竖屏底栏形态：横屏 Dock 是右下面板，与左区
        // 胶囊不争空间，保持在场（与 .97 基线一致；矩阵 landphone 腿实测
        // 横屏让位会抽走底部带可点节点且与基线几何脱钩）。
        val cardVisible = (state == DockState.D1 || state == DockState.D2) &&
            (!intentInputMode || isLandscapeDock)
        // v0.27.4：删除全屏"点按外部收起"层（Bob：它挡住了主页非 Dock 区上滑，导致 D2→D1）
        // D2→D1 改由下滑/右滑手势触发

        // ---- D1/D2 悬浮图标（无卡片，v0.27.1 去底） ----
        // v0.68.75（Bob）：横屏时 Dock 放右侧（分栏布局），不再放底部
        // v0.68.78（Bob）：用 align 直接定位，contentAlignment 在横屏下不可靠
        // v0.68.82（横屏专项第 3 项，Bob）：横屏改回横向（同竖屏），放右下角约四分之一屏
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = cardVisible,
                modifier = Modifier
                    .align(
                        if (isLandscapeDock) Alignment.BottomEnd else Alignment.BottomCenter
                    )
                    // v0.68.78：横屏时右侧留边，不贴边
                    .let { if (isLandscapeDock) it.padding(end = 12.dp) else it },
                enter = if (isLandscapeDock) {
                    // v0.68.82（第 3 项）：横屏从右下角斜向滑入
                    slideIn(
                        initialOffset = { IntOffset(it.width, it.height) },
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = 0.86f
                        )
                    ) + fadeIn(tween(180))
                } else {
                    slideInVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = 0.86f
                        )
                    ) { it } + fadeIn(tween(180))
                },
                exit = if (isLandscapeDock) {
                    // v0.68.82（第 3 项）：横屏向右下角斜向滑出
                    slideOut(
                        targetOffset = { IntOffset(it.width, it.height) },
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = 0.9f
                        )
                    ) + fadeOut(tween(150))
                } else {
                    slideOutVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = 0.9f
                        )
                    ) { it } + fadeOut(tween(150))
                },
                label = "dockCard"
            ) {
                val hThreshPx = with(density) { 48.dp.toPx() }
                val vThreshPx = with(density) { 72.dp.toPx() }
                // v0.16.1：底部系统手势区高度（导航条/手势区），落在该区域内的拖拽起点不抢——
                // 让系统处理底部左右滑（切最近应用）、底部上滑（回桌面/最近任务）
                val sysGestureBottomPx = systemGestureBottomPx()  // 批B ⑧：收口到 Insets helper（原内联读 WindowInsets.systemGestures）
                val dockBottomPadPx = with(density) { 16.dp.toPx() }
                // v0.27.1：去底（Bob 选一）——图标直接浮在壁纸上，无卡片无阴影
                // v0.57.0：长按 Dock → 隐藏（与 D1 右滑并存）
                // v0.68.58（Bob）：Dock 默认无背景——按压时才显示圆角底（触摸反馈）
                val dockPressSource = remember { MutableInteractionSource() }
                val dockPressed by dockPressSource.collectIsPressedAsState()
                val dockBgAlpha by animateFloatAsState(
                    targetValue = if (dockPressed) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                        easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                    ),
                    label = "dockBg"
                )
                Column(
                    modifier = Modifier
                        // v0.68.97（布局普查批）：冒烟几何断言锚点（Dock 面板整体）
                        .testTag("dock_panel")
                        // v0.68.82（第 3 项）：横屏包内容（右下角），不再 148dp 右侧窄条
                        .let { if (isLandscapeDock) it.wrapContentSize() else it.fillMaxWidth() }
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        // 玻璃试点批：Dock 强玻璃（AGSL 折射+模糊增色，只换材质不改尺寸/位置）。
                        // 模拟档（老设备/省电/桌面不可见）逐像素回原行为：默认全透明、
                        // 按压时 GlassCard 55%——fallback 与 boost 沿用 dockBgAlpha。
                        .glassSurface(
                            kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                            cornerRadius = 28.dp,
                            tintColor = AILauncherColors.current.Card,
                            fallbackColor = AILauncherColors.current.GlassCard.copy(alpha = 0.55f * dockBgAlpha),
                            tintBoost = dockBgAlpha
                        )
                        .combinedClickable(
                            interactionSource = dockPressSource,
                            indication = null,  // 用背景显隐做反馈，不要水波纹
                            onClick = {},
                            onLongClick = { onHideDockRequestState.value() }
                        )
                        .pointerInput(state, sysGestureBottomPx, dockBottomPadPx) {
                            var accumX = 0f
                            var accumY = 0f
                            var fired = false
                            var inSysGestureZone = false
                            detectDragGestures(
                                onDragStart = { start ->
                                    accumX = 0f
                                    accumY = 0f
                                    fired = false
                                    // 起点在底部系统手势区内（卡片底部 dockBottomPadPx 之上、
                                    // 屏幕底部 sysGestureBottomPx 之内）→ 本次不消费、不触发，交还系统
                                    val dangerPx = sysGestureBottomPx - dockBottomPadPx
                                    inSysGestureZone =
                                        dangerPx > 0 && start.y > size.height - dangerPx
                                },
                                onDrag = { change, dragAmount ->
                                    if (inSysGestureZone) return@detectDragGestures
                                    change.consume()
                                    accumX += dragAmount.x
                                    accumY += dragAmount.y
                                    if (fired) return@detectDragGestures
                                    val ax = abs(accumX)
                                    val ay = abs(accumY)
                                    val go = onStateChangeState.value
                                    // v0.57.0：上滑 → D3 开应用中心；已在 D3 时上滑 → 回桌面主页
                                    //（Bob：任何时候从底部上滑都要能回桌面主页）
                                    if (ay > vThreshPx && accumY < 0) {
                                        fired = true
                                        if (state == DockState.D3) onD3SwipeUpToHomeState.value()
                                        else go(DockState.D3)
                                    } else if (ax > hThreshPx && ax >= ay) {
                                        fired = true
                                        if (accumX < 0) {
                                            // 左滑 = 更多：D1→D2→D3
                                            if (state == DockState.D1) go(DockState.D2)
                                            else if (state == DockState.D2) go(DockState.D3)
                                        } else {
                                            // 右滑 = 更少：D2→D1；D1 右滑 → 隐藏确认（唯一隐藏入口）
                                            if (state == DockState.D2) go(DockState.D1)
                                            else if (state == DockState.D1) onHideDockRequestState.value()
                                        }
                                    } else if (ay > vThreshPx && ay > ax) {
                                        fired = true
                                        // 下滑 → 收一档（D1 下滑不再隐藏）
                                        if (state == DockState.D2) go(DockState.D1)
                                    }
                                }
                            )
                        }
                ) {
                    // 一二档内容：crossfade + scale，卡片高度弹簧跟随
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                                scaleIn(
                                    spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = 0.9f
                                    ),
                                    initialScale = 0.92f
                                ))
                                .togetherWith(fadeOut(tween(120)))
                                .using(
                                    SizeTransform(clip = false) { _, _ ->
                                        spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = 0.9f
                                        )
                                    }
                                )
                        },
                        label = "dockContent"
                    ) { s ->
                        // v0.68.58（Bob）：内容无背景——背景只在按压时显示（外层控制）
                        Column(
                            modifier = Modifier
                                .let { if (isLandscapeDock) it.wrapContentSize() else it.fillMaxWidth() }
                                .padding(vertical = 16.dp)
                        ) {
                        if (s == DockState.D2) {
                            DockGrid10(
                                apps = top10,
                                onLaunch = ::launchApp,
                                menuApp = popupApp,
                                onLongPress = { popupApp = it },
                                onDismissMenu = { popupApp = null },
                                isLandscape = isLandscapeDock
                            )
                        } else {
                            DockRow4(
                                apps = top4,
                                onLaunch = ::launchApp,
                                menuApp = popupApp,
                                onLongPress = { popupApp = it },
                                onDismissMenu = { popupApp = null },
                                isLandscape = isLandscapeDock,
                                // v0.68.99：七仔格只进 D1 行（D2 网格不加）
                                onPetCellTap = onPetCellTap
                            )
                        }
                        }
                    }
                }
            }
        }

        // ---- D3 全屏：不透明整屏替换 ----
        // v0.67.10（Bob）：从主屏 picker 进入时只用淡入（不滑入）——选择器已在 rail 位置，无缝衔接
        val d3FromPicker by UiPrefs.d3EnterFromPicker.collectAsState()
        AnimatedVisibility(
            visible = state == DockState.D3,
            enter = if (d3FromPicker) fadeIn(tween(200))
            else slideInVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = 0.9f
                )
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = 0.9f
                )
            ) { it } + fadeOut(tween(150)),
            label = "fullOverlay"
        ) {
            FullAppsOverlay(
                onStateChange = onStateChangeState.value,
                onD3SwipeRight = onD3SwipeRightState.value,
                onOpenSettings = onOpenSettings,
                onAskQizai = onAskQizaiState.value
            )
        }
    }
}

/**
 * D3 全屏：不透明底色整屏替换，内容为「应用中心」（单列表：分类区在上 + A-Z 在下）。
 * 手势签名（全 App 统一，v0.14 纠正：左滑 = 多，右滑 = 少）：
 * - D3 → D2：header 标题区右滑 / 顶部区域下滑 / 列表到顶继续下滑
 *   （右滑检测只放在 header 标题区：A-Z 行与分组组头自有横滑手势，
 *   全屏级检测会双重触发，按"最具体目标优先"收拢到标题区）
 * - 顶部"常用"横滑行是横向滚动区：行内手势由 LazyRow 先消费
 */
@Composable
private fun FullAppsOverlay(
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onOpenSettings: () -> Unit,
    // R2 UI（10-21 新规格）：应用中心「问七仔」
    onAskQizai: (String) -> Unit = {}
) {
    val onStateChangeState = rememberUpdatedState(onStateChange)
    val onD3SwipeRightState = rememberUpdatedState(onD3SwipeRight)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // v0.68.68：D3 用实底——半透明 GlassCardStrong 会让壁纸颜色透出来
            //（深色横屏时最左侧蓝条）；AppCenterContent 自己有实底背景，
            // 这里用实底确保边缘无缝
            .background(AILauncherColors.current.Background)
    ) {
        AppCenterContent(
            onOpenSettings = onOpenSettings,
            onPullDownToD2 = { onStateChangeState.value(DockState.D2) },
            onHeaderSwipeRight = { onD3SwipeRightState.value() },
            // v0.40.0：底部上滑 → 回桌面（与标题区右滑同义：回到记住的 Dock 档）
            onBottomSwipeUp = { onD3SwipeRightState.value() },
            // R2 UI（10-21 新规格）：「问七仔」——回首页并提交协调器
            onAskQizai = onAskQizai,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/** 第一档：4 个常用大图标（56dp），只留图标，卡内严格垂直居中。
 *  v0.68.99（主页重构 R-1）：行首加七仔格（意图入口）——格不占应用位，
 *  4 个应用位一个不少；总宽变大后按可用宽分档收图标/间距（布局防线：
 *  降级新内容本身，不挤其他槽位）。 */
@Composable
private fun DockRow4(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    isLandscape: Boolean = false,
    onPetCellTap: (() -> Unit)? = null
) {
    // v0.68.82（第 3 项）：横屏改回横向（同竖屏），不再竖排
    BoxWithConstraints(
        modifier = Modifier
            .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val hasPet = onPetCellTap != null
        // 分档（内容宽 = 本 Box 实测宽）：≥336 全尺寸；≥308 中档；其余小档
        val iconSize: Dp
        val gap: Dp
        val cellSize: Dp
        when {
            !hasPet -> { iconSize = 56.dp; gap = 16.dp; cellSize = 56.dp }
            maxWidth >= 336.dp -> { iconSize = 56.dp; gap = 14.dp; cellSize = 56.dp }
            maxWidth >= 308.dp -> { iconSize = 52.dp; gap = 12.dp; cellSize = 52.dp }
            else -> { iconSize = 48.dp; gap = 10.dp; cellSize = 48.dp }
        }
        Row(
            modifier = Modifier
                .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() },
            // v0.27.2：居中（之前 spacedBy 默认左对齐）
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPetCellTap != null) {
                DockPetCell(size = cellSize, onTap = onPetCellTap)
            }
            apps.forEach { app ->
                DockIconButton(
                    app = app,
                    iconSize = iconSize,
                    corner = if (iconSize >= 56.dp) 16.dp else 14.dp,
                    menuApp = menuApp,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    onDismissMenu = onDismissMenu
                )
            }
        }
    }
}

/**
 * v0.68.99（主页重构 R-1，Bob 定）：Dock 七仔格——意图入口的门，形态为
 * 「对话等待态」：倾听姿态（meteor_wave，与意图 FETCH 姿态同图）头部为主
 * 裁切 + 头边小对话泡（内「…」）暗示「点我说话」；慢呼吸微动仅在主页
 * RESUMED 且非减动效 / 非低电量时开（省电口径同 PetView）。热区即整格
 * （随 DockRow4 分档，≥48dp），点按进输入态。不占应用位（行宽分档处理）。
 */
@Composable
private fun DockPetCell(size: Dp, onTap: () -> Unit) {
    val view = LocalView.current
    val reduceMotion = com.bobot.ailauncher.ui.theme.rememberReduceMotion()
    val lowPower by com.bobot.ailauncher.data.BatteryState.isLowPower.collectAsState()
    var resumed by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> resumed = true
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val breathing = resumed && !reduceMotion && !lowPower
    val breatheScale: Float = if (breathing) {
        rememberInfiniteTransition(label = "dockPetBreathe")
            .animateFloat(
                initialValue = 1f,
                targetValue = 1.045f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dockPetBreatheVal"
            ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(size)
            .testTag("dock_pet_cell")
            .semantics { contentDescription = "问七仔" }
            .clickable {
                view.tickHaptic()
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.bobot.ailauncher.R.drawable.meteor_wave),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .size(size - 8.dp)
                .graphicsLayer { scaleX = breatheScale; scaleY = breatheScale }
        )
        // 对话泡：头边右上，内「…」——「它在等你说话」
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(9.dp))
                .background(AILauncherColors.current.Card.copy(alpha = 0.92f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "…",
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = AILauncherColors.current.Accent
            )
        }
    }
}

/** 第二档：同一批常用重排成 10 个小图标（2×5，46dp），只留图标 */
@Composable
private fun DockGrid10(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    isLandscape: Boolean = false
) {
    // v0.68.82（第 3 项）：横屏改回横向 5 列（同竖屏），不再 2 列竖排
    val cols = 5
    Column(
        modifier = Modifier
            .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() }
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.chunked(cols).forEach { row ->
            Row(
                modifier = Modifier.let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() },
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { app ->
                    // v0.68.77（Bob）：横屏不用 weight，否则被撑开散掉
                    Box(
                        modifier = if (isLandscape) Modifier else Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        DockIconButton(
                            app = app,
                            iconSize = 46.dp,
                            corner = 13.dp,
                            menuApp = menuApp,
                            onLaunch = onLaunch,
                            onLongPress = onLongPress,
                            onDismissMenu = onDismissMenu
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.39.0：Dock 图标按钮——点击启动，长按弹出真弹窗快捷菜单。
 * 菜单锚定在图标所在的 Box 上（图标在屏幕下方时自动弹到图标上面）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIconButton(
    app: AppInfo,
    iconSize: Dp,
    corner: Dp,
    menuApp: AppInfo?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticView = LocalView.current
    Box(modifier = modifier) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(corner))
                // 体检批D ⑥：冒烟语义定位锚点（Dock 应用项）
                .testTag("dock_item")
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        hapticView.confirmHaptic()
                        onLongPress(app)
                    }
                )
        )
        AppPopupMenu(
            app = app,
            expanded = menuApp == app,
            onDismiss = onDismissMenu
        )
    }
}

/** v0.68.44：页脚刊头字距文本 */
@Composable
private fun TrackedFooterText(text: String, color: androidx.compose.ui.graphics.Color) {
    Row {
        text.forEach { ch ->
            androidx.compose.material3.Text(
                text = ch.toString(),
                fontSize = 12.sp,
                color = color,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}
