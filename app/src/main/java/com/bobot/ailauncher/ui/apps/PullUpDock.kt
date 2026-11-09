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
import androidx.compose.animation.core.Animatable
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
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import com.bobot.ailauncher.data.AppCatalog
import kotlinx.coroutines.launch
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.BadgeProbe
import com.bobot.ailauncher.data.BadgeStickyStore
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppBadge
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.DockLightParams
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.PetMotion
import com.bobot.ailauncher.ui.theme.systemGestureBottomPx
import kotlin.math.abs
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * - D1 —面板上滑→ D2（2026-11-01，Bob 定：Dock 面板内部上下滑换档；
 *   横竖屏同语义）；D3 —右滑→ 记住的行数
 * - D2 —面板下滑→ D1（面板内部上下滑换档；横竖屏同语义）
 * - 上滑：隐藏→记住的行数（首页上滑）；D1/D2→D3（直接全屏）
 * - 下滑：D3→D2→D1（逐级收回）；点按卡片外部 → D2 收回 D1
 * - 2026-10-27 手势清理（Bob 12:20 确认）：Dock 面板长按、左右滑逻辑
 *   全部删除——长按面板不再出隐藏确认、D1 右滑不再出隐藏确认、
 *   D2 左滑不再进 D3（D3 只剩上滑进）；隐藏手势入口就此没有，
 *   已隐藏的 Dock 仍可由首页上滑叫回。
 */
enum class DockState { Hidden, D1, D2, D3 }

/**
 * Dock 面板轮廓（2026-11-02，无宠物清理）：普通 28dp 圆角矩形。
 * 旧肩线（顶部中央拱、拱函数、弧形高光＋暖白内晕、点按弹性峰值）已随
 * 宠物一并删除，不再为面板顶缘留任何拱形或拱光影。
 */

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PullUpDock(
    state: DockState,
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onOpenSettings: () -> Unit,
    // v0.68.169（盲操扇形找回）：Dock 视觉单元（面板）精确 bounds
    // ——供 MainScreen→HomeScreen 盲操手势仲裁。374 批误用 PullUpDock 外层
    // modifier 的 onGloballyPositioned，其根为 fillMaxSize，捕获的是整屏
    // 矩形，致所有主页手势都被判为 Dock 区内、扇形全屏失效。本回调挂在
    // 单元 Box 自身上报真实单元矩形；内容卸载（D3／意图态）时经
    // DisposableEffect 报 null。
    onDockUnitBoundsChange: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val onStateChangeState = rememberUpdatedState(onStateChange)
    val onD3SwipeRightState = rememberUpdatedState(onD3SwipeRight)
    // v0.68.169：单元 bounds 回调最新态（DisposableEffect(Unit) 不重启，
    // 卸载时报 null 须调最新回调，不读首帧闭包）。
    val onDockUnitBoundsChangeLatest by rememberUpdatedState(onDockUnitBoundsChange)

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
    // 智能 v3：idea 图标点按先刷新排序（whyTick 进 top10 的 key，点按时
    // Dock 按当前钟点重算一次），再开说明页——说明的原因与显示的顺序同源。
    var whyTick by remember { mutableIntStateOf(0) }
    var showWhySheet by remember { mutableStateOf(false) }
    // v0.20：智能排序开关（设置页可关）；key 里带上开关值，切换后返回即生效
    val smartSort = UiPrefs.getDockSmartSort(context)
    // v0.35.0：用户置顶——排最前（按用户自定义顺序），后面用智能排序补齐并去重
    val dockTick by UiPrefs.dockTick.collectAsState()
    val pinnedPkgs = remember(dockTick) { UiPrefs.getDockPinned(context) }
    val top10 = remember(allApps, usageTick, smartSort, dockTick, whyTick) {
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

    // 422（Bob 2026-11-04）：idea 灯泡按洞察出现——常规时隐藏，仅当
    // 智能尾巴出现“新面孔＋有数据理由”时才出现。tail 取 Dock 渲染的
    // 同一份 top10 去掉固定位（固定位变动不触发）；D1/D2 共用同一 tail
    // 与 seenTail（不按档位分，避免切档误触发）。
    val insightTail = remember(top10, pinnedPkgs) {
        top10.filter { it.packageName !in pinnedPkgs }.map { it.packageName }
    }
    // 本地态与持久化镜像（只写 prefs 不触发重组，故点按／建基线时双写）。
    var insightSeenTail by remember { mutableStateOf(UiPrefs.getInsightSeenTail(context)) }
    // 首次运行（seenTail 为 null）静默把当前 tail 存下建基线、不显示（不追认）。
    LaunchedEffect(insightTail) {
        if (insightSeenTail == null) {
            UiPrefs.setInsightSeenTail(context, insightTail)
            insightSeenTail = insightTail
        }
    }
    // 可见条件：seenTail 已存在，且 tail 里有 seenTail 没有的包，且该包有
    // 数据理由（复用说明页的理由判定：时段桶 ≥3，或转移 ≥3（沿用说明页的
    // prevRecent 门，避免点开却是 fallback），或总量 >0；全是 fallback
    // “暂无你的使用记录”的不算洞察）。
    val insightVisible = remember(insightTail, insightSeenTail) {
        val seen = insightSeenTail
        if (seen == null) {
            false
        } else {
            val seenSet = seen.toSet()
            val (dow, hour) = AppUsageTracker.currentDowHour()
            val (prevPkg, prevTime) = AppUsageTracker.prevLaunch(context)
            val prevRecent = prevPkg.isNotEmpty() &&
                System.currentTimeMillis() - prevTime <= 10 * 60 * 1000
            insightTail.any { pkg ->
                pkg !in seenSet && (
                    AppUsageTracker.hwCount(context, pkg, dow, hour) >= 3 ||
                    (prevRecent && AppUsageTracker.transferCount(context, prevPkg, pkg) >= 3) ||
                    AppUsageTracker.totalCount(context, pkg) > 0
                )
            }
        }
    }

    // 图标点按涟漪用协程域。
    val rippleScope = rememberCoroutineScope()
    // D1/D2 换档（2026-11-01，Bob 定）：Dock 面板内部上下滑直接切换，
    // 档位切换走 AnimatedContent crossfade + scale。
    // 涟漪定位：图标窗口中心（包名 → Offset）与面板窗口原点/宽。
    val dockIconCenters = remember { mutableStateMapOf<String, Offset>() }
    var dockPanelOrigin by remember { mutableStateOf(Offset.Zero) }
    var dockPanelWidthPx by remember { mutableFloatStateOf(0f) }
    var rippleCenter by remember { mutableStateOf<Offset?>(null) }
    val rippleAnim = remember { Animatable(0f) }

    fun launchApp(app: AppInfo) {
        AppUsageTracker.recordLaunch(context, app.packageName)
        usageTick++
        // 图标点按涟漪（fire-and-forget，不阻塞应用启动）。
        val iconCenter = dockIconCenters[app.packageName]
        if (iconCenter != null && dockPanelWidthPx > 0f) {
            rippleCenter = iconCenter - dockPanelOrigin
            rippleScope.launch {
                rippleAnim.snapTo(0f)
                rippleAnim.animateTo(1f, tween(MotionTokens.Medium, easing = FastOutSlowInEasing))
            }
        }
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) {
                context.startActivity(intent)
                // 角标粘性数（Bob 2026-11-08）：在我们桌面里点图标启动应用 = 去读消息了，
                // 成功打开才清掉该包粘性数；打开失败不清。
                // 探针数同款清掉（Bob 2026-11-09 12:20 ①：启动=去读消息了）。
                BadgeStickyStore.clearForPackage(context, app.packageName)
                BadgeProbe.clearForPackage(context, app.packageName)
            } else IslandToasts.show("无法打开「${app.label}」")
        } catch (_: Exception) {
            IslandToasts.show("无法打开「${app.label}」")
        }
    }

    // v0.39.0：Dock 长按 → 真弹窗快捷菜单（锚定在图标上）
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // v0.68.73（Bob）：横屏时 Dock 收拢，不横贯全屏
        val isLandscapeDock = maxWidth > maxHeight
        // 横屏 Dock 右锚定（2026-10-27）：右边缘让 cutoutRight（CUTOUT_SPEC
        // 规则 3，读 live inset、不写死数值；无挖孔时为 0，12dp 设计边距
        // 不变；旋转后挖孔换边，inset 跟着换，两向都清）。
        val cutoutRightDp = with(density) {
            WindowInsets.displayCutout.getRight(density, LayoutDirection.Ltr).toDp()
        }
        // v0.68.99：输入态已随意图清理（Bob 2026-11-08 23:38）删除——卡片只按
        // Dock 状态显隐，不再为意图胶囊让位。
        val cardVisible = (state == DockState.D1 || state == DockState.D2)
        // v0.27.4：删除全屏"点按外部收起"层（Bob：它挡住了主页非 Dock 区上滑，导致 D2→D1）
        // D2→D1 改由面板下滑手势触发
        // 点外关闭层（2026-10-26 入口子项，仅清单打开时存在，与 v0.27.4
        // 删除的常驻层不同）：透明全屏 Box，画在 D1/D2 盒之下、简报之上——

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
                    // v0.68.78：横屏时右侧留边，不贴边；2026-10-27 右锚定：
                    // 再让 live cutoutRight（挖孔在右时卡片不顶挖孔区，
                    // 无挖孔时仍是 12dp，一字不写死数值）
                    .let { if (isLandscapeDock) it.padding(end = 16.dp + cutoutRightDp) else it },
                // 意图清理（Bob 2026-11-08 23:38）：聚焦切换的 fade-only 分支
                // 已随意图输入态删除——进出场一律按方向走原滑动＋淡入淡出。
                enter = if (isLandscapeDock) {
                    // v0.68.82（第 3 项）：横屏从右下角斜向滑入
                    // 家族规格（值不动、仅具名）：滑入 DockEnter、淡入 FadeHandoff。
                    slideIn(
                        initialOffset = { IntOffset(it.width, it.height) },
                        animationSpec = PetMotion.DockEnter
                    ) + fadeIn(
                        tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                    )
                } else {
                    slideInVertically(
                        animationSpec = PetMotion.DockEnter
                    ) { it } + fadeIn(
                        tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                    )
                },
                exit = if (isLandscapeDock) {
                    // v0.68.82（第 3 项）：横屏向右下角斜向滑出
                    // 家族规格（值不动、仅具名）：滑出 DockExit、淡出 FadeQuick。
                    slideOut(
                        targetOffset = { IntOffset(it.width, it.height) },
                        animationSpec = PetMotion.DockExit
                    ) + fadeOut(
                        tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)
                    )
                } else {
                    slideOutVertically(
                        animationSpec = PetMotion.DockExit
                    ) { it } + fadeOut(
                        tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)
                    )
                },
                label = "dockCard"
            ) {
                val vThreshPx = with(density) { 72.dp.toPx() }
                // v0.16.1：底部系统手势区高度（导航条/手势区），落在该区域内的拖拽起点不抢——
                // 让系统处理底部左右滑（切最近应用）、底部上滑（回桌面/最近任务）
                val sysGestureBottomPx = systemGestureBottomPx()  // 批B ⑧：收口到 Insets helper（原内联读 WindowInsets.systemGestures）
                val dockBottomPadPx = with(density) { 16.dp.toPx() }
                // v0.27.1：去底（Bob 选一）——图标直接浮在壁纸上，无卡片无阴影
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
                // 400 Dock 不显示修复：面板高度不再由内层实测回写。
                // 399 的外层 Box 高 = 内层 Column 实测高，Column 高又由
                // AnimatedContent 内容决定——首帧目录未到、动效在途或
                // 实测回调丢一帧时，面板高可以被写成一个远小于内容的
                // 值，面板被压扁到看不见，之后也没有回写把它拉回来。
                // 改成档位定高。401 修正 400 的算错：400 只算了行与外层
                // 底边距，漏了 AnimatedContent 内层 Column 的上下内边距
                // 32dp，面板比内容矮 32dp，内容贴底、顶部空一截。
                // 逐项重算：D1 = 内层上下 32 + 行（图标 56 + 上下 32）+
                // 外层底边距 16 = 136dp；D2 = 内层上下 32 + 网格（上下
                // 32 + 两行 92 + 间距 12）+ 外层底边距 16 = 184dp。
                // 面板就是普通 28dp 圆角矩形，高度不再含肩线 rise。
                val dockPanelHeight = if (state == DockState.D2) 184.dp else 136.dp
                // 普通 28dp 圆角（clip 与玻璃共用同一形状，不再有肩线轮廓）。
                val dockShape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .let { if (isLandscapeDock) it.wrapContentSize() else it.fillMaxWidth() }
                        .height(dockPanelHeight)
                        // v0.68.169：单元真实矩形上报仲裁（面板单元，
                        // 非外层 fillMaxSize 根的整屏矩形）。
                        .onGloballyPositioned { onDockUnitBoundsChangeLatest(it.boundsInRoot()) }
                ) {
                // 单元卸载（D3／意图态卡片让位）时报 null，仲裁不再拿
                // stale 矩形拦背景手势。
                DisposableEffect(Unit) {
                    onDispose { onDockUnitBoundsChangeLatest(null) }
                }
                // 424：玻璃盒（原 Column 改为 Box，承接其全部修饰；灯泡移入盒内，见下）
                Box(
                    modifier = Modifier
                        // v0.68.97（布局普查批）：冒烟几何断言锚点（Dock 面板整体）
                        .testTag("dock_panel")
                        .align(Alignment.BottomCenter)
                        // 横屏首页修（Bob 2026-10-25）：横屏面板定宽，不再随档位包内容——
                        // D1 面板宽 = DockRow4 横屏盒宽 312dp（4×56 图标 + 3×16 间距 + 2×20 内边距）
                        //   + 本面板左右内边距 32dp（16 + 16）= 344dp；D2 网格盒 270dp
                        //   （5×46 + 4×4 + 2×12）比它窄 42dp，旧 wrapContent 下档位切换面板宽跳 42dp。
                        // 定宽落在画玻璃背景的本层（不是内层行）；竖屏 fillMaxWidth 口径不动；
                        // 图标尺寸与间距一律不动，背景圆角与内边距不变。
                        .let { if (isLandscapeDock) it.width(344.dp) else it.fillMaxWidth() }
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .onGloballyPositioned { coords ->
                            dockPanelOrigin = coords.positionInWindow()
                            dockPanelWidthPx = coords.size.width.toFloat()
                        }
                        .clip(dockShape)
                        // 玻璃试点批：Dock 强玻璃（AGSL 折射+模糊增色，只换材质不改尺寸/位置）。
                        // 400 Dock 不显示修复：fallback 不再乘 dockBgAlpha。
                        // 旧口径是“默认全透明、按压时才 55%”，玻璃背景源一旦
                        // 不可用（省电/低电/桌面不可见降级），面板就全透明，
                        // 只剩图标——图标再因亮壁纸或目录未到而看不见时，
                        // 整个 Dock 就像不存在。fallback 改为常驻 GlassCard
                        // 55%，按压 boost 仍走 dockBgAlpha，玻璃可用时观感不变。
                        // 玻璃裁剪沿 28dp 圆角轮廓走（shape 参数，共享玻璃参数不动）。
                        .glassSurface(
                            kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                            cornerRadius = 28.dp,
                            tintColor = AILauncherColors.current.Card,
                            fallbackColor = AILauncherColors.current.GlassCard.copy(alpha = 0.55f),
                            tintBoost = dockBgAlpha,
                            shape = dockShape,
                            // 玻璃质感 v2：Dock 专用光影（对角明暗 + 高光面 +
                            // 左下出口 + 沉底暖核 + 方向性亮带）；其他玻璃面
                            // 不传此参数、一字不动。
                            dockLight = DockLightParams(),
                            // 玻璃边框效果不要（Bob 2026-10-25 22:31）：跳过全部
                            // 轮廓描边（暗描边＋方向性亮带＋1dp 顶部亮边），
                            // 仅 Dock 面板；意图卡片等其他玻璃面一字不动。
                            noRim = true
                        )
                        // 图标中心涟漪（内容之上、细白圈、不盖字形）。
                        .drawWithContent {
                            drawContent()
                            val c = rippleCenter
                            val p = rippleAnim.value
                            if (c != null && p > 0f && p < 1f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.75f * (1f - p)),
                                    radius = 40.dp.toPx() * p,
                                    center = c,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                        .combinedClickable(
                            interactionSource = dockPressSource,
                            indication = null,  // 用背景显隐做反馈，不要水波纹
                            // 面板空白点按：无动作（图标点按是子节点 clickable，不走这里）。
                            onClick = { }
                        )
                        .pointerInput(state, sysGestureBottomPx, dockBottomPadPx) {
                            var accumX = 0f
                            var accumY = 0f
                            var fired = false
                            var inSysGestureZone = false
                            // 面板手势（2026-11-01，Bob 定；2026-11-03 删 D3 上滑回桌面）：
                            // Dock 内部上下滑换档——上滑：D1→D2、D2→D3；下滑：D2→D1
                            // （D1 下滑不再隐藏；D3 上滑不再回桌面）；长按与左右滑逻辑全部删除。
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
                                    // 上滑 → 升一档：D1→D2、D2→D3（D3 上滑无动作）
                                    if (ay > vThreshPx && accumY < 0) {
                                        fired = true
                                        if (state == DockState.D1) go(DockState.D2)
                                        else if (state == DockState.D2) go(DockState.D3)
                                    } else if (ay > vThreshPx && ay > ax) {
                                        fired = true
                                        // 下滑 → 收一档（D1 下滑不再隐藏）
                                        if (state == DockState.D2) go(DockState.D1)
                                    }
                                },
                                // 手势清理后松手无动作（横拖跟手与松手确认已随横拖删除）
                            )
                        },
                ) {
                    // 424：玻璃 Column 改为外层 Box（承接原 Column 全部修饰：
                    // testTag／对齐／宽度／padding／clip／玻璃／手势，数值与
                    // 顺序一字不动，padding 仍在 clip／玻璃之前，玻璃仍盖住
                    // padding 条）；内层 Column 装原全部子项，灯泡移入盒内。
                    // 横屏首页修口径移到内层 Column：D2 网格盒（270dp）比面板
                    // 内容宽（312dp）窄，横屏下在面板内居中；D1 盒 312dp 恰好
                    // 占满内容宽，无视觉变化。竖屏保持默认 Start。
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isLandscapeDock) Alignment.CenterHorizontally else Alignment.Start
                    ) {
                        // 拖拽把手（Bob 2026-11-07）：玻璃盒内部顶部一条横线，标示 Dock
                        // 可手按住拖动。标准 bottom-sheet 抓手：32×4dp 全圆角小条，
                        // 水平居中（显式居中，不随本 Column 横屏 Center／竖屏 Start
                        // 的对齐走）；纯视觉指示，不加点击、不加手势——从把手起拖
                        // 仍走玻璃盒的 pointerInput 换档（纯视觉子项不挡触摸）。
                        // 高度预算：把手块 20dp（上 8 + 条 4 + 下 8），从内容 Column
                        // 的上下内边距 32dp 里吸收（顶部归零、底部剩 12dp）；应用行
                        // 下移 4dp，档位定高 136/184dp 不变。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp)
                                .testTag("dock_handle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 32.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AILauncherColors.current.Hint.copy(alpha = 0.5f))
                            )
                        }
                        // 面板内容区：crossfade + scale，卡片高度用快弹簧跟随
                        // （2026-11-02：旧 MediumLow 高度弹簧拖尾是上滑卡顿主因之一）。
                        AnimatedContent(
                            targetState = state,
                            transitionSpec = {
                                (fadeIn(tween(PetMotion.FadeQuick)) +
                                    scaleIn(
                                        PetMotion.PadStandard,
                                        initialScale = 0.96f
                                    ))
                                    .togetherWith(fadeOut(tween(MotionTokens.Short)))
                                    .using(
                                        SizeTransform(clip = false) { _, _ ->
                                            PetMotion.DockTierSize
                                        }
                                    )
                            },
                            label = "dockContent"
                        ) { s ->
                            // v0.68.58（Bob）：内容无背景——背景只在按压时显示（外层控制）
                            // 拖拽把手批：上下内边距 32dp 收成 12dp（顶部 0 + 底部 12），
                            // 吸掉把手块的 20dp（上 8 + 条 4 + 下 8）；应用行下移 4dp，
                            // 档位定高 136/184dp 不变。把手与内容之间 8dp（把手块的下间隙）。
                            Column(
                                modifier = Modifier
                                    .let { if (isLandscapeDock) it.wrapContentSize() else it.fillMaxWidth() }
                                    .padding(top = 0.dp, bottom = 12.dp)
                            ) {
                            if (s == DockState.D2) {
                                DockGrid10(
                                    apps = top10,
                                    onLaunch = ::launchApp,
                                    menuApp = popupApp,
                                    onLongPress = { popupApp = it },
                                    onDismissMenu = { popupApp = null },
                                    isLandscape = isLandscapeDock,
                                    onIconPositioned = { pkg, center -> dockIconCenters[pkg] = center }
                                )
                            } else {
                                DockRow4(
                                    apps = top4,
                                    onLaunch = ::launchApp,
                                    menuApp = popupApp,
                                    onLongPress = { popupApp = it },
                                    onDismissMenu = { popupApp = null },
                                    isLandscape = isLandscapeDock,
                                    onIconPositioned = { pkg, center -> dockIconCenters[pkg] = center }
                                )
                            }
                            }
                        }
                    } // 内层 Column（装原玻璃 Column 的全部子项）
                    // 424（Bob 2026-11-04 实拍纠正）：423 的“贴底”贴的是屏
                    // 幕底，不是玻璃底——灯泡盒是玻璃 Column 的兄弟项，其
                    // BottomCenter 锚的是外层布局（底＝屏幕底），而玻璃底比
                    // 屏幕底高导航条一截（实拍 65px），灯泡落在玻璃外面。
                    // 本批把玻璃 Column 改为外层 Box（承接原全部修饰），灯
                    // 泡移入盒内做 BottomCenter，灯泡底＝玻璃底由结构保证，
                    // 不用 offset、不写死 inset 数值。灯泡盒本身除父级外一
                    // 字不动；去字、移到底部、按洞察出现三点沿用 422（见
                    // insightVisible 定义）；D3／意图态仍随面板
                    // AnimatedVisibility 隐藏（不动）。
                    if (insightVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(width = 64.dp, height = 20.dp)
                                .semantics { contentDescription = "为什么是这几个应用" }
                                .clickable {
                                    // 点灯泡先把当前 tail 存为 seenTail（确认动作），
                                    // 再按原逻辑刷新排序并开说明页（看过就再藏起来，
                                    // 直到下一个新面孔）。
                                    UiPrefs.setInsightSeenTail(context, insightTail)
                                    insightSeenTail = insightTail
                                    whyTick++
                                    showWhySheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } // 玻璃盒（原 Column，承接全部修饰，灯泡在其内）
                } // 单元盒（定高 dockPanelHeight）
            }
        }

        // ---- D3 全屏：不透明整屏替换 ----
        // v0.67.10（Bob）：从主屏 picker 进入时只用淡入（不滑入）——选择器已在 rail 位置，无缝衔接
        val d3FromPicker by UiPrefs.d3EnterFromPicker.collectAsState()
        AnimatedVisibility(
            visible = state == DockState.D3,
            enter = if (d3FromPicker) fadeIn(tween(PetMotion.FadeHandoff))
            else slideInVertically(
                animationSpec = PetMotion.DockTierOffset
            ) { it } + fadeIn(tween(PetMotion.FadeQuick)),
            exit = slideOutVertically(
                animationSpec = PetMotion.DockTierOffset
            ) { it } + fadeOut(tween(MotionTokens.Short)),
            label = "fullOverlay"
        ) {
            FullAppsOverlay(
                onStateChange = onStateChangeState.value,
                onD3SwipeRight = onD3SwipeRightState.value,
                onOpenSettings = onOpenSettings
            )
        }

        // 智能 v3：Dock 说明页——按当前 Dock 显示顺序（固定位＋智能位）
        // 列出 top10，每行只展示原因（不可点）；下滑/返回关闭，无额外按钮。
        // 原因与 Dock 排序同源（点图标时已按当前钟点刷新过一次）。
        if (showWhySheet) {
            val (sheetDow, sheetHour) = AppUsageTracker.currentDowHour()
            val (sheetPrevPkg, sheetPrevTime) = AppUsageTracker.prevLaunch(context)
            val sheetPrevRecent = sheetPrevPkg.isNotEmpty() &&
                System.currentTimeMillis() - sheetPrevTime <= 10 * 60 * 1000
            val sheetPrevLabel = if (sheetPrevRecent) {
                allApps.find { it.packageName == sheetPrevPkg }?.label?.toString()
            } else null
            ModalBottomSheet(
                onDismissRequest = { showWhySheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = AILauncherColors.current.Card,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "为什么是这几个应用",
                        fontSize = AILauncherType.BodyLarge.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.current.Title,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    top10.forEach { app ->
                        val reason = when {
                            app.packageName in pinnedPkgs -> "你固定在 Dock 上"
                            AppUsageTracker.hwCount(context, app.packageName, sheetDow, sheetHour) >= 3 -> {
                                val weekday = when (sheetDow) {
                                    6 -> "周六"
                                    7 -> "周日"
                                    else -> "工作日"
                                }
                                val period = when (sheetHour) {
                                    in 0..5 -> "夜里"
                                    in 6..11 -> "早上"
                                    in 12..13 -> "中午"
                                    in 14..17 -> "下午"
                                    else -> "晚上"
                                }
                                "$weekday$period $sheetHour 点常开"
                            }
                            sheetPrevLabel != null &&
                                AppUsageTracker.transferCount(context, sheetPrevPkg, app.packageName) >= 3 ->
                                "打开 $sheetPrevLabel 后常接着开"
                            AppUsageTracker.totalCount(context, app.packageName) > 0 -> "经常用"
                            else -> "热门应用（暂无你的使用记录）"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                drawable = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = app.label.toString(),
                                    fontSize = AILauncherType.BodyLarge.fontSize,
                                    color = AILauncherColors.current.Title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = reason,
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
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
    onOpenSettings: () -> Unit
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/** 第一档：4 个常用大图标（56dp），只留图标，卡内严格垂直居中。
 *  v0.68.101：R-1 的行内格已移除（删宠物批起面板上方不再有宠物），
 *  本行回到 4 应用位原始分档（56 图标 / 16 间距）。 */
@Composable
private fun DockRow4(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    isLandscape: Boolean = false,
    // 上报图标窗口中心（供点按涟漪中心计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
) {
    // v0.68.82（第 3 项）：横屏改回横向（同竖屏），不再竖排
    BoxWithConstraints(
        modifier = Modifier
            .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        val iconSize = 56.dp
        val gap = 16.dp
        Row(
            modifier = Modifier
                .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() },
            // v0.27.2：居中（之前 spacedBy 默认左对齐）
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                DockIconButton(
                    app = app,
                    iconSize = iconSize,
                    corner = 12.dp,
                    badgeHeight = 20.dp,
                    menuApp = menuApp,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    onDismissMenu = onDismissMenu,
                    onIconPositioned = onIconPositioned
                )
            }
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
    isLandscape: Boolean = false,
    // 上报图标窗口中心（供点按涟漪中心计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
) {
    // v0.68.82（第 3 项）：横屏改回横向 5 列（同竖屏），不再 2 列竖排
    val cols = 5
    Column(
        modifier = Modifier
            .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        apps.chunked(cols).forEach { row ->
            Row(
                modifier = Modifier.let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
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
                            corner = 12.dp,
                            badgeHeight = 18.dp,
                            menuApp = menuApp,
                            onLaunch = onLaunch,
                            onLongPress = onLongPress,
                            onDismissMenu = onDismissMenu,
                            onIconPositioned = onIconPositioned
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
    // 未读角标高度（Bob 2026-11-07 晚）：D1 56dp 图标→20dp，D2 46dp 图标→18dp。
    badgeHeight: Dp = 20.dp,
    menuApp: AppInfo?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    modifier: Modifier = Modifier,
    // 上报图标窗口中心（供点按涟漪中心计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
) {
    val hapticView = LocalView.current
    // 未读角标（Bob 2026-11-07 晚）：通知监听汇总的该包条数；>0 才显示。
    val badgeCounts by NotificationRepository.badgeCounts.collectAsState()
    val badgeCount = badgeCounts[app.packageName] ?: 0
    Box(modifier = modifier
        .onGloballyPositioned { coords ->
            onIconPositioned(
                app.packageName,
                coords.positionInWindow() + Offset(
                    coords.size.width / 2f,
                    coords.size.height / 2f
                )
            )
        }
    ) {
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
        // 未读角标：叠在图标右上角、微微压住角；纯视觉覆盖层，不加点击、
        // 不消费触摸（点按仍走图标本身，长按菜单不受影响）。高度按图标尺寸走
        // （D1 56dp 图标→20dp，D2 46dp 图标→18dp，调用方传 badgeHeight）。
        if (badgeCount > 0) {
            AppBadge(
                count = badgeCount,
                height = badgeHeight,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
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
                fontSize = AILauncherType.Caption.fontSize,
                color = color,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
