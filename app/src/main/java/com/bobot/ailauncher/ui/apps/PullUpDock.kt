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
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.bobot.ailauncher.ui.pet.DragBodyState
import com.bobot.ailauncher.ui.pet.HybridPet
import com.bobot.ailauncher.ui.pet.PetActionList
import com.bobot.ailauncher.ui.pet.PetSpeechBubbleHost
import com.bobot.ailauncher.data.PetRepository.PetManualAction
import kotlinx.coroutines.delay
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetRepository
import kotlinx.coroutines.launch
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.UiPrefs
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
 * - D1 —左拖→ D2（2026-10-27 拖拽代滑动：跟手＋松手确认，可中途取消）；
 *   D2 —左滑→ D3（中途触发，不动）；D3 —右滑→ D2 —右滑→ D1
 * - D2 —右拖→ D1（拖拽代滑动）；D1 右滑 → 隐藏确认（入口之一，不动）
 * - v0.16：隐藏的唯一入口是 D1 右滑（弹确认框，"不再提醒"可记）；D1 下滑不再隐藏；
 *   点按卡片外部不再隐藏（D2→D1 收一档，D1 无动作）
 * - 上滑：隐藏→记住的行数（首页上滑）；D1/D2→D3（直接全屏）
 * - 下滑：D3→D2→D1（逐级收回）；点按卡片外部 → D2 收回 D1
 * - v0.57.0 长按 Dock → 隐藏（与 D1 右滑并存），拖拽批不动（隐藏入口，不是切换触发）
 */
enum class DockState { Hidden, D1, D2, D3 }

/**
 * Dock 圆肩轮廓（2026-10-25，Bob 拍板“材质 + 连接 + 圆肩”）：
 * 圆角矩形（28dp 角）+ 顶部肩线——中央一段向上兜起的平滑拱，
 * 托住宠物底部。两段 cubicTo，弦端与峰顶处切线水平（控制点
 * 取半弦长 50% 处），与平顶缘相切、无折点；弧度与宠物底部
 * 圆弧同向。轮廓可超出布局上沿 rise（肩峰在 y=-rise 处），
 * 布局高本身不动（外层 Box 用 rise−press 公式留出空间）。
 */
internal class DockShoulderShape(
    private val chordDp: Dp,
    private val riseDp: Dp,
    private val cornerDp: Dp,
    // 肩线弹性（2026-10-25）：峰值隆起量，默认 0 即 339 静止形态。
    private val peakDp: Dp = 0.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val chord = with(density) { chordDp.toPx() }
        val rise = with(density) { riseDp.toPx() }
        val corner = with(density) { cornerDp.toPx() }
        val peak = with(density) { peakDp.toPx() }
        return Outline.Generic(dockShoulderPath(size, chord, rise, corner, peak))
    }
}

/**
 * 肩线拱（从 (x0, yBase) 到 (x1, yBase)，峰顶在 yBase - rise），
 * 追加到当前 Path 而不是自建 Path。
 * peak=0 时与 339 两段 cubicTo 一字不动；peak≠0 时在两段三次贝塞尔
 * 采样点上叠加余弦衰减隆起（弦端 0、峰顶 1），两端锚定不动——
 * 皮筋式隆起，不是整条线平移。
 * moveToStart=true（默认）时先 moveTo 到弦端——独立描边（如 dressing
 * 拱高光）用；false 时从当前点直接续画——圆肩轮廓用，轮廓必须保持
 * 单一 contour：旧写法用 addPath 拼独立拱 Path，addPath 另起 contour，
 * 末尾 close() 会从轮廓末点（左下）向拱 contour 起点（左弦端）拉一
 * 条对角线，轮廓描边（亮带／亮边／暗描边）全都沿这条线走——真机上
 * Dock 左侧那道斜线就是它（Bob 2026-10-25 22:31）。单 contour 后
 * close() 只是回到起点，不再有对角线；填充区域与之前两 contour
 * 并集一字不动（对角线本就是两 contour 共边），clip／叠色不受影响。
 */
internal fun Path.appendShoulderArch(
    x0: Float,
    x1: Float,
    cx: Float,
    k: Float,
    risePx: Float,
    peakPx: Float,
    yBase: Float,
    moveToStart: Boolean = true,
) {
    if (peakPx == 0f) {
        if (moveToStart) moveTo(x0, yBase)
        cubicTo(x0 + k, yBase, cx - k, yBase - risePx, cx, yBase - risePx)
        cubicTo(cx + k, yBase - risePx, x1 - k, yBase, x1, yBase)
    } else {
        val steps = 24
        fun cubicPoint(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
            val u = 1f - t
            return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
        }
        if (moveToStart) moveTo(x0, yBase)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val fall = 0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * t)
            lineTo(
                cubicPoint(t, x0, x0 + k, cx - k, cx),
                cubicPoint(t, yBase, yBase, yBase - risePx, yBase - risePx) - peakPx * fall
            )
        }
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val fall = 0.5f + 0.5f * kotlin.math.cos(Math.PI.toFloat() * t)
            lineTo(
                cubicPoint(t, cx, cx + k, x1 - k, x1),
                cubicPoint(t, yBase - risePx, yBase - risePx, yBase, yBase) - peakPx * fall
            )
        }
    }
}

/** 圆肩轮廓 Path（布局坐标：原点在布局左上，y 向下；肩峰在 y=-rise-peak）。 */
private fun dockShoulderPath(
    size: Size,
    chordPx: Float,
    risePx: Float,
    cornerPx: Float,
    peakPx: Float = 0f,
): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val half = (chordPx / 2f).coerceAtMost((w / 2f - cornerPx).coerceAtLeast(0f))
    val x0 = cx - half
    val x1 = cx + half
    // cubic 控制点取半弦长 50% 处，弦端与峰顶切线水平。
    val k = half * 0.5f
    return Path().apply {
        moveTo(0f, h - cornerPx)
        lineTo(0f, cornerPx)
        quadraticTo(0f, 0f, cornerPx, 0f)
        lineTo(x0, 0f)
        // 单 contour 续画拱（moveToStart=false）：不用 addPath，杜绝对角线。
        appendShoulderArch(x0, x1, cx, k, risePx, peakPx, 0f, moveToStart = false)
        lineTo(w - cornerPx, 0f)
        quadraticTo(w, 0f, w, cornerPx)
        lineTo(w, h - cornerPx)
        quadraticTo(w, h, w - cornerPx, h)
        lineTo(cornerPx, h)
        quadraticTo(0f, h, 0f, h - cornerPx)
        close()
    }
}

/**
 * Dock 圆肩材质 dressing（玻璃之上、图标之下，链上游 clip 已把
 * 绘制截在圆肩轮廓内）：
 * ① 沿肩线内侧 3dp 的弧形高光：宽 14dp 淡 + 窄 5dp 亮两道描边
 *    叠出柔边；光方向与宠物同源（右上）：水平渐变右亮左隐
 *    （旧左亮方向已作废，2026-10-25 纠正）；
 * ② 暖白径向内晕：中心偏右上（x 右移 7% 宽、y 上移），呼应宠物
 *    球体的内发光暖核，并叠加右侧增亮。
 * 高光拱跟随肩线弹性形变（与轮廓同一拱函数）。
 */
private fun Modifier.dockShoulderDressing(
    chordDp: Dp,
    riseDp: Dp,
    cornerDp: Dp,
    peakDp: Dp = 0.dp,
): Modifier = drawBehind {
    val chord = chordDp.toPx()
    val rise = riseDp.toPx()
    val corner = cornerDp.toPx()
    val peak = peakDp.toPx()
    val cx = size.width / 2f
    val half = (chord / 2f).coerceAtMost((size.width / 2f - corner).coerceAtLeast(0f))
    val x0 = cx - half
    val x1 = cx + half
    val k = half * 0.5f
    // 肩线内侧 3dp 的拱（与轮廓肩线同形、下移 3dp，含弹性峰值）。
    val yBase = 3.dp.toPx()
    val arch = Path().apply { appendShoulderArch(x0, x1, cx, k, rise, peak, yBase) }
    drawPath(
        path = arch,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.0f),
                Color.White.copy(alpha = 0.10f),
            ),
            startX = x0,
            endX = x1,
        ),
        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path = arch,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.0f),
                Color.White.copy(alpha = 0.32f),
            ),
            startX = x0,
            endX = x1,
        ),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFF6E8).copy(alpha = 0.38f),
                Color(0xFFFFF6E8).copy(alpha = 0.0f),
            ),
            center = Offset(cx + size.width * 0.07f, -size.height * 0.05f),
            radius = size.width * 0.5f,
        ),
    )
    // 右侧增亮：与宠物右上光源同向的暖白横向渐变。
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFF6E8).copy(alpha = 0.0f),
                Color(0xFFFFF6E8).copy(alpha = 0.10f),
            ),
            startX = size.width * 0.4f,
            endX = size.width,
        ),
    )
}

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
    // v0.68.99（主页重构 R-1）：Dock 宠物点按语义——
    // 2026-10-26 宠物交互重构（入口子项）：恢复 327 语义，点宠物 =
    // 问（直进意图输入并聚焦，调用方 enterIntentInput）；长按 =
    // 表演清单（onPetCellLongPress）；动作清单不再挂点按。
    // 输入态在场时 D1/D2 卡让位（cardVisible 关），胶囊底部原地接管
    onPetCellTap: () -> Unit = {},
    // 长按 Dock 宠物：打开修剪后的单层表演清单（PetActionList）。
    onPetCellLongPress: () -> Unit = {},
    // 宠物表演清单（2026-10-26，长按打开、单层 8 行、无 2 级）：
    // 可见性由调用方 MainScreen 持有；点外关闭见本文件点外关闭层
    // 与面板空白点按，D3 / 进输入态时状态回落。
    petActionsVisible: Boolean = false,
    onPetActionsVisibleChange: (Boolean) -> Unit = {},
    intentInputMode: Boolean = false,
    // 竖屏切换闪烁修复（2026-10-27）：退出 funnel 进行中（HomeScreen
    // 经 MainScreen 上报的 perchTransition.isExiting）——Dock 宠物压到
    // 落地后才挂载，否则与在途意图宠物在不同高度同时在场（70% 翻转
    // 点双影）；落地交接的意图宠物 alpha 淡出见 HomeScreen 落地段。
    exitInProgress: Boolean = false,
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

    // 肩线弹性（2026-10-25，Bob 拍板）：仅 Dock 图标点按触发。
    // 峰值动画单位 dp：0 静止 → 7 峰值 → -1 落回过冲 → 0。
    val shoulderScope = rememberCoroutineScope()
    // 拖拽一体拉伸状态机（2026-10-26 定稿）：在 PullUpDock 函数体顶层
    // remember（不在 AnimatedVisibility 内容里），visibility 切换不丢态；
    // 只透传给 Dock 的 HybridPet → FloatingPet，意图侧不传（旧行为）。
    val dragBody = remember { DragBodyState(shoulderScope) }
    val shoulderPeakAnim = remember { Animatable(0f) }
    val shoulderPeakDp by shoulderPeakAnim.asState()
    // 肩线协同（2026-10-26 入口子项，评审 P1-6 口径统一）：
    // 全带——每一个 actionTick 动作都带肩线脉冲，不许再出现 4/19
    // （此前仅 STRETCH / HAPPY / SURPRISED / GREET / TILT 带协同，
    // 其余动作不碰肩线，同一清单内反馈口径不统一）。
    // 幅度按身体参与度分档：拉伸类 7 / 惊讶 5 / 打招呼 4 / 歪头 3、
    // 其余（害羞的挤压、晕眩的摇摆、深呼吸的吸气隆起，及未来由交互
    // 事件自动触发的新动作）一律 2dp 小峰值——自动触发的动作无需
    // 另配肩线，默认就有身体跟随。歪头仍是软连接：头先动、肩延迟
    // 约 90ms 跟上（stretch-v2 柔性口径）。图标启动的 7dp 峰值不动
    // （见 launchApp），点宠物的小峰值见 petTapPeak（3dp）。
    val petActionTick by PetRepository.actionTick.collectAsState()
    val petActionKind by PetRepository.actionKind.collectAsState()
    LaunchedEffect(petActionTick) {
        if (petActionTick == 0) return@LaunchedEffect
        if (petActionKind == PetManualAction.TILT) delay(90)
        val peak = when (petActionKind) {
            PetManualAction.STRETCH, PetManualAction.HAPPY -> 7f
            PetManualAction.SURPRISED -> 5f
            PetManualAction.GREET -> 4f
            PetManualAction.TILT -> 3f
            else -> 2f
        }
        shoulderPeakAnim.animateTo(peak, tween(250, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)))
        shoulderPeakAnim.animateTo(-1f, tween(150, easing = FastOutSlowInEasing))
        shoulderPeakAnim.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
    }
    // 点宠物的小峰值（2026-10-26 入口子项，评审 P1-6）：身体反馈归因
    // 到宠物——点宠物不再只有果冻回弹，肩线也给 3dp 小脉冲（图标启动
    // 的 7dp 不动）。注意：竖屏点按随即进输入态、Dock 卡片退出动画
    // 约 150ms，峰值（250ms 到峰）只能部分可见；横屏 Dock 在场时
    // 完整可见；峰值本身仍按口径触发，不为竖屏可见度延迟进输入。
    fun petTapPeak() {
        shoulderScope.launch {
            shoulderPeakAnim.animateTo(3f, tween(250, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)))
            shoulderPeakAnim.animateTo(-1f, tween(150, easing = FastOutSlowInEasing))
            shoulderPeakAnim.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        }
    }
    // D3 全屏时清单无锚点（宠物不在场），状态回落、避免回 D1 后无故重现。
    LaunchedEffect(state) {
        if (state == DockState.D3) onPetActionsVisibleChange(false)
    }
    // 涟漪与注视定位：图标窗口中心（包名 → Offset）与面板窗口原点/宽。
    val dockIconCenters = remember { mutableStateMapOf<String, Offset>() }
    var dockPanelOrigin by remember { mutableStateOf(Offset.Zero) }
    var dockPanelWidthPx by remember { mutableFloatStateOf(0f) }
    var rippleCenter by remember { mutableStateOf<Offset?>(null) }
    val rippleAnim = remember { Animatable(0f) }

    fun launchApp(app: AppInfo) {
        AppUsageTracker.recordLaunch(context, app.packageName)
        usageTick++
        // 肩线弹性三联（fire-and-forget，不阻塞应用启动）：
        // ① 肩线峰值动画；② 注视转向被点图标方位；③ 图标中心涟漪。
        val iconCenter = dockIconCenters[app.packageName]
        if (iconCenter != null && dockPanelWidthPx > 0f) {
            val panelCenterX = dockPanelOrigin.x + dockPanelWidthPx / 2f
            val dx = iconCenter.x - panelCenterX
            val sideThreshold = with(density) { 8.dp.toPx() }
            if (dx < -sideThreshold) PetRepository.glance(GazeTarget.DOCK_LEFT)
            else if (dx > sideThreshold) PetRepository.glance(GazeTarget.DOCK_RIGHT)
            rippleCenter = iconCenter - dockPanelOrigin
            shoulderScope.launch {
                rippleAnim.snapTo(0f)
                rippleAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
        }
        shoulderScope.launch {
            shoulderPeakAnim.animateTo(
                7f, tween(250, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f))
            )
            shoulderPeakAnim.animateTo(-1f, tween(150, easing = FastOutSlowInEasing))
            shoulderPeakAnim.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        }
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
        // v0.68.101：面板可用高（坐姿七仔紧配置判定用；内层 AnimatedVisibility
        // 作用域里隐式 receiver 不可达，故在此提升为局部值）
        val dockAvailableHeight = maxHeight
        // v0.68.99：输入态下卡片让位（意图胶囊在底部原地接管，Dock 内容不与
        // 之叠画）。横屏聚焦（2026-10-26，Bob 22:57 最终口径）：Dock 要隐藏
        // （与竖屏聚焦一致）——聚焦批的横屏留场例外（isLandscapeDock）已删除，
        // 横屏聚焦时 Dock 隐藏、宠物在左侧意图卡片上（头＋卡片一个身体）。
        val cardVisible = (state == DockState.D1 || state == DockState.D2) &&
            !intentInputMode
        // 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：场景整体换时 Dock
        // 卡片只做淡入淡出、不滑动。重进方向的信号取上一帧 intentInputMode
        // （SideEffect 在组合后才更新，故翻转当帧仍持旧值 true → fade-only；
        // 正常回场时上一帧为 false → 沿用滑动＋淡入）。退出方向直接读当帧
        // intentInputMode（翻转当帧已为 true → fade-only；D3／状态变化离场
        // 时为 false → 沿用滑动＋淡出）。竖屏一字不动。
        var prevIntentInputMode by remember { mutableStateOf(intentInputMode) }
        androidx.compose.runtime.SideEffect { prevIntentInputMode = intentInputMode }
        // v0.27.4：删除全屏"点按外部收起"层（Bob：它挡住了主页非 Dock 区上滑，导致 D2→D1）
        // D2→D1 改由下滑/拖拽手势触发（2026-10-27：右滑触发改为右拖确认）
        // 点外关闭层（2026-10-26 入口子项，仅清单打开时存在，与 v0.27.4
        // 删除的常驻层不同）：透明全屏 Box，画在 D1/D2 盒之下、简报之上——
        // 简报 / 背景区的点按落到它身上即关闭清单；Dock 面板与宠物在其
        // 上层，Dock 自身手势（面板滑动、图标点按、宠物点按）不受影响；
        // 面板空白点按另见面板 combinedClickable 的 onClick。清单打开期间
        // 背景手势（上滑开 D3、盲操扇形）需先点外关闭再操作——清单是瞬时
        // 态，关闭成本为一次点按，与意图区点外退出同一语法。
        // 注意：本层不透传——点外只关闭清单，不同时触发下层动作。
        if (petActionsVisible && !intentInputMode && state != DockState.D3) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pet_action_dismiss_scrim")
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onPetActionsVisibleChange(false) }
            )
        }

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
                    // 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：聚焦退出
                    // 回场时只淡入、不滑动（上一帧仍在聚焦 → fade-only）；正常
                    // 回场（D3 关闭、状态回 D1/D2）沿用 v0.68.82 斜向滑入。
                    if (dockCardFocusSwapIsFadeOnly(isLandscapeDock, prevIntentInputMode)) {
                        fadeIn(
                            tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                        )
                    } else {
                        // v0.68.82（第 3 项）：横屏从右下角斜向滑入
                        // 家族规格（值不动、仅具名）：滑入 DockEnter、淡入 FadeHandoff。
                        slideIn(
                            initialOffset = { IntOffset(it.width, it.height) },
                            animationSpec = PetMotion.DockEnter
                        ) + fadeIn(
                            tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                        )
                    }
                } else {
                    slideInVertically(
                        animationSpec = PetMotion.DockEnter
                    ) { it } + fadeIn(
                        tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                    )
                },
                exit = if (isLandscapeDock) {
                    // 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：进聚焦离场
                    // 时只淡出、不滑动（当帧 intentInputMode 已为 true → fade-only；
                    // 150ms 淡出完成后 HomeScreen 才挂载意图槽位，先出后进不重叠）；
                    // D3／状态变化离场时沿用 v0.68.82 斜向滑出。
                    if (dockCardFocusSwapIsFadeOnly(isLandscapeDock, intentInputMode)) {
                        fadeOut(
                            tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)
                        )
                    } else {
                        // v0.68.82（第 3 项）：横屏向右下角斜向滑出
                        // 家族规格（值不动、仅具名）：滑出 DockExit、淡出 FadeQuick。
                        slideOut(
                            targetOffset = { IntOffset(it.width, it.height) },
                            animationSpec = PetMotion.DockExit
                        ) + fadeOut(
                            tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)
                        )
                    }
                } else {
                    slideOutVertically(
                        animationSpec = PetMotion.DockExit
                    ) { it } + fadeOut(
                        tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)
                    )
                },
                label = "dockCard"
            ) {
                val hThreshPx = with(density) { 48.dp.toPx() }
                val vThreshPx = with(density) { 72.dp.toPx() }
                // D1/D2 拖拽切换（2026-10-27）：内容跟手量（px），卡片本体与
                // 宠物栖坐不动，只平移 AnimatedContent；松手弹簧回落到 0。
                // 跟手钳制 32dp（阻尼 0.8，见 DockDragSwitch）。
                val tierFollowClampPx = with(density) { 32.dp.toPx() }
                val tierDragScope = rememberCoroutineScope()
                val tierFollowAnim = remember { Animatable(0f) }
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
                // v6 静态栖坐（Bob 2026-10-24 18:45 拍板，原型 input-sit-v6 图 2）：
                // 七仔改在面板前面图层（面板之后画、不 clip），脚底压进面板
                // 顶缘 25%、可见高 = 面板高 ×0.52（v6 后调整，Bob 2026-10-24
                // 20:31：再大一倍）、约 75% 露在面板外。外层
                // Box 高 = 面板高 + 露出部分（面板底锚不变，只向上长）；
                // 面板高实测回写（首帧按档位估 D2 184dp / D1 136dp，实测到
                // 即纠正）。紧配置（字体 1.3 / 可用高 <400dp）宠物 ×0.8，
                // 先缩它、不挤其他槽位（布局防线）。
                // 圆肩批（2026-10-25，Bob 拍板“材质 + 连接 + 圆肩”）：
                // 玻璃头（曜仔）不再漂浮——头宽取旧坐姿盒 1.224 倍（即
                // 1.53 × 0.8，B2 缩小档：D1 约 86.6dp / D2 约 117.1dp），盒高按玻璃头
                // 长宽比 1043:1318；气隙取消，改圆肩连接：面板顶缘中央
                // 兜起肩线、宠物底部压入肩峰 5dp（前面图层，
                // 面板先画、宠物后画），不画脚下阴影；外层 Box 只向上长，
                // 面板几何与锚点不动（容器高 = 面板高 + 宠物高 + rise − press）。
                // 圆肩 D1/D2 比例批（2026-10-25，Bob：两档宠物大小变了，
                // 圆肩角度也要相应变）：rise 跟弦长成比例走，rise/chord
                // 恒等于 D1 基准 18/156——拱的两段 cubic 控制点本就与弦长
                // 成比例（k = half × 0.5 = chord/4），故 rise/chord 不变即
                // 拱形是 D1 拱的精确缩放，三档（D1/D2/紧配置）是同一个肩
                // 在缩放，不是被拉扁/拉陡。B2+A 定稿（2026-10-26，Bob 点选
                // B2+A mock 档）：弦长 = 宠物宽 + 两侧各 35dp，D1 rise 约
                // 18.1dp、D2 约 21.6dp，紧配置随宠物 ×0.8 同步回落
                // （D1 约 16.1dp、D2 约 18.9dp）；压入 5dp 与弹性峰值
                // （7/5/4/3dp 绝对形变量）不动。
                val seatCompact = density.fontScale >= 1.3f ||
                    dockAvailableHeight < 400.dp
                var dockPanelHeight by remember { mutableStateOf(if (state == DockState.D2) 184.dp else 136.dp) }
                val dockPetSize = dockPanelHeight * 0.52f * (if (seatCompact) 0.8f else 1f)
                val floatPetWidth = dockPetSize * 1.224f
                val floatPetHeight = floatPetWidth * (1318f / 1043f)
                val shoulderChord = floatPetWidth + 70.dp
                val shoulderRise = shoulderChord * (18f / 156f)
                val petPress = 5.dp
                // 拖拽一体拉伸（2026-10-26 定稿，22:10 后肩升锁定模型）：
                // 肩升锁定等于头升（FloatingPet 侧同帧赋值），面板纵向拉长
                // = 肩线 rise 增大（shape 与 dressing 同一 effectiveRise，
                // 玻璃面 shape 同一实例）；满拉伸拱高 = 肩 rise（D1 约
                // 18.1dp）＋头升 32dp ≈ 50.1dp（1:1 拉伸的直接后果，列为
                // 真机复核项）；外层 Box 向上长 headRise（底锚不动——
                // AnimatedVisibility 底锚 BottomCenter/BottomEnd，故只向上
                // 长、面板不移位）；宠物 offset 公式一字不动（头底与肩峰
                // 压入 5dp 全程恒定，见 DragBody 锁定模型）。
                val dragHeadRiseDp = with(density) { dragBody.headRisePx.toDp() }
                val dragHeadXdp = with(density) { dragBody.headXPx.toDp() }
                val dragHeadSinkDp = with(density) { dragBody.headSinkPx.toDp() }
                val dragShoulderRiseDp = with(density) { dragBody.shoulderRisePx.toDp() }
                val dragRise = shoulderRise + dragShoulderRiseDp
                // 圆肩轮廓（弦长 = 宠物宽 + 两侧各 35dp）；shape 只影响
                // 绘制（clip / 玻璃 / 高光 / 内晕），不改变布局尺寸，故
                // dockPanelHeight 回写不会引起布局循环。
                val shoulderShape = remember(dockPanelHeight, seatCompact, shoulderPeakDp, dragShoulderRiseDp) {
                    DockShoulderShape(
                        chordDp = shoulderChord,
                        riseDp = dragRise,
                        cornerDp = 28.dp,
                        peakDp = shoulderPeakDp.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .let { if (isLandscapeDock) it.wrapContentSize() else it.fillMaxWidth() }
                        .height(dockPanelHeight + floatPetHeight + shoulderRise - petPress + shoulderPeakDp.dp + dragHeadRiseDp)
                ) {
                Column(
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
                            with(density) { dockPanelHeight = coords.size.height.toDp() }
                            dockPanelOrigin = coords.positionInWindow()
                            dockPanelWidthPx = coords.size.width.toFloat()
                        }
                        .clip(shoulderShape)
                        // 玻璃试点批：Dock 强玻璃（AGSL 折射+模糊增色，只换材质不改尺寸/位置）。
                        // 模拟档（老设备/省电/桌面不可见）逐像素回原行为：默认全透明、
                        // 按压时 GlassCard 55%——fallback 与 boost 沿用 dockBgAlpha。
                        // 圆肩批：玻璃裁剪沿圆肩轮廓走（shape 参数，共享玻璃参数不动）。
                        .glassSurface(
                            kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                            cornerRadius = 28.dp,
                            tintColor = AILauncherColors.current.Card,
                            fallbackColor = AILauncherColors.current.GlassCard.copy(alpha = 0.55f * dockBgAlpha),
                            tintBoost = dockBgAlpha,
                            shape = shoulderShape,
                            // 玻璃质感 v2：Dock 专用光影（对角明暗 + 高光面 +
                            // 左下出口 + 沉底暖核 + 方向性亮带）；其他玻璃面
                            // 不传此参数、一字不动。
                            dockLight = DockLightParams(),
                            // 玻璃边框效果不要（Bob 2026-10-25 22:31）：跳过全部
                            // 轮廓描边（暗描边＋方向性亮带＋1dp 顶部亮边），
                            // 仅 Dock 面板；意图卡片等其他玻璃面一字不动。
                            // Dock 左侧斜线另由圆肩轮廓单 contour 化根除（见
                            // appendShoulderArch），与本开关无关。
                            noRim = true
                        )
                        // 圆肩批：弧形高光 + 暖白内晕（玻璃之上、图标之下）。
                        // 拖拽一体拉伸：rise 跟 shape 同一 effectiveRise。
                        .dockShoulderDressing(
                            chordDp = shoulderChord,
                            riseDp = dragRise,
                            cornerDp = 28.dp,
                            peakDp = shoulderPeakDp.dp
                        )
                        // 肩线弹性：图标中心涟漪（内容之上、细白圈、不盖字形）。
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
                            // 面板空白点按：平时无动作；表演清单打开时点空白
                            // 即关闭清单（点外关闭的 Dock 侧一半，另一半是
                            // 外层透明层；图标点按是子节点 clickable，不走这里）。
                            onClick = { if (petActionsVisible) onPetActionsVisibleChange(false) },
                            onLongClick = { onHideDockRequestState.value() }
                        )
                        .pointerInput(state, sysGestureBottomPx, dockBottomPadPx) {
                            var accumX = 0f
                            var accumY = 0f
                            var fired = false
                            var inSysGestureZone = false
                            // D1/D2 拖拽切换（2026-10-27）：水平方向一旦确立即锁定，
                            // 本次手势不再判竖直；拖拽中只跟手、不触发，松手再确认。
                            var horizontalLocked = false
                            fun resetFollow() {
                                tierDragScope.launch { tierFollowAnim.snapTo(0f) }
                            }
                            fun updateFollow() {
                                val follow = dragTierFollowPx(state, accumX, tierFollowClampPx)
                                tierDragScope.launch { tierFollowAnim.snapTo(follow) }
                            }
                            fun springBack() {
                                tierDragScope.launch {
                                    tierFollowAnim.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = 0.9f
                                        )
                                    )
                                }
                            }
                            detectDragGestures(
                                onDragStart = { start ->
                                    accumX = 0f
                                    accumY = 0f
                                    fired = false
                                    horizontalLocked = false
                                    resetFollow()
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
                                    // 已锁定水平：只更新跟手，不再判竖直（防斜拖中途跳 D3）。
                                    if (horizontalLocked) {
                                        updateFollow()
                                        return@detectDragGestures
                                    }
                                    val ax = abs(accumX)
                                    val ay = abs(accumY)
                                    val go = onStateChangeState.value
                                    // v0.57.0：上滑 → D3 开应用中心；已在 D3 时上滑 → 回桌面主页
                                    //（Bob：任何时候从底部上滑都要能回桌面主页）
                                    if (ay > vThreshPx && accumY < 0) {
                                        fired = true
                                        resetFollow()
                                        if (state == DockState.D3) onD3SwipeUpToHomeState.value()
                                        else go(DockState.D3)
                                    } else if (ax > hThreshPx && ax >= ay) {
                                        // 水平方向确立：D2 左滑 → D3、D1 右滑 → 隐藏确认
                                        // 仍沿用旧的中途触发（非 D1/D2 切换，不动）；
                                        // D1↔D2 改走拖拽：锁定水平、只跟手，松手再确认，
                                        // 旧的中途触发分支就此删除（拖拽覆盖完整才删的条件已满足，
                                        // 见 DockDragSwitch：两方向＋阈值＋回落均已覆盖）。
                                        if (state == DockState.D2 && accumX < 0) {
                                            fired = true
                                            resetFollow()
                                            go(DockState.D3)
                                        } else if (state == DockState.D1 && accumX > 0) {
                                            fired = true
                                            resetFollow()
                                            onHideDockRequestState.value()
                                        } else {
                                            horizontalLocked = true
                                            updateFollow()
                                        }
                                    } else if (ay > vThreshPx && ay > ax) {
                                        fired = true
                                        resetFollow()
                                        // 下滑 → 收一档（D1 下滑不再隐藏）
                                        if (state == DockState.D2) go(DockState.D1)
                                    } else {
                                        // 方向未确立：水平倾向时先跟手（竖直倾向不跟手，
                                        // 防竖拖带水平抖动）；松手时低于阈值即回落。
                                        if (ax >= ay) updateFollow() else resetFollow()
                                    }
                                },
                                onDragEnd = {
                                    // 拖拽松手确认：仅水平锁定且净位移过阈值才切换；
                                    // 低于阈值、拖回原方向一律回落（可中途取消）。
                                    if (horizontalLocked) {
                                        val target = resolveDragTierTarget(state, accumX, hThreshPx)
                                        if (target != null) onStateChangeState.value(target)
                                    }
                                    springBack()
                                },
                                onDragCancel = {
                                    springBack()
                                }
                            )
                        },
                    // 横屏首页修：D2 网格盒（270dp）比面板内容宽（312dp）窄，横屏下在面板内居中；
                    // D1 盒 312dp 恰好占满内容宽，无视觉变化。竖屏保持默认 Start。
                    horizontalAlignment = if (isLandscapeDock) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    // pet-hybrid-float：漂浮宠物已移出面板 Column——面板之后画的
                    // HybridPet 兄弟节点（见面板 Column 闭合后的调用），
                    // 面板不再 clip 它，漂浮高度才放得下。
                    // 一二档内容：crossfade + scale，卡片高度弹簧跟随
                    // D1/D2 拖拽跟手：只平移内容（卡片本体与宠物栖坐不动，
                    // 不碰布局尺寸）；松手弹簧回落到 0，切换本身仍走 crossfade。
                    AnimatedContent(
                        modifier = Modifier.graphicsLayer {
                            translationX = tierFollowAnim.value
                        },
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
                }
                // 圆肩批：连接混合宠物（面板之后画、前面图层；面板底锚
                // 不变，宠物只向上长）。单宠物原则（2026-10-26，Bob 22:57
                // 最终口径）：输入态不呈现（让位给意图宠物，横屏亦同——
                // 横屏聚焦时 Dock 隐藏、宠物在左侧意图卡片上，见 IntentEntry），
                // 仍保证任何时刻恰一只。
                // 手势语义（2026-10-26 入口子项）：
                // 点宠物 = 问（直进意图输入，327 语义恢复，另带 3dp 小峰值），
                // 长按 = 表演清单；testTag 沿用 dock_pet_cell（冒烟入口
                // 定位契约不变）；layout_check 已随之上新为接触/压入几何
                // （压入肩峰 5dp、脸盘完整）。B2+A 几何一字不动（宠物系数
                // 1.224、弦 +70dp、rise 18/156、压入 5dp）。
                if (dockPetVisible(intentInputMode, exitInProgress)) {
                    HybridPet(
                        petWidth = floatPetWidth,
                        onPetTap = {
                            petTapPeak()
                            onPetCellTap()
                        },
                        onPetLongPress = onPetCellLongPress,
                        dragBodyState = dragBody,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -(dockPanelHeight + shoulderRise + shoulderPeakDp.dp - petPress))
                            .testTag("dock_pet_cell")
                    )
                }
                // 表演清单（非模态气泡，宠物上方、尾巴朝下指向宠物）：
                // 与宠物同在外层 Box 前面图层（不 clip、不改面板几何），
                // 静止态默认关闭，layout_check 接触/压入规格不受影响。
                // 单层 8 行、无 2 级（2026-10-26 入口子项）；点外关闭走
                // 外层透明层 + 面板空白点按，本处只留 ✕ 与状态回落。
                if (petActionsVisible && !intentInputMode && state != DockState.D3) {
                    PetActionList(
                        onDismiss = { onPetActionsVisibleChange(false) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = -(dockPanelHeight + shoulderRise + shoulderPeakDp.dp - petPress + floatPetHeight + 8.dp + dragHeadRiseDp))
                            .testTag("pet_action_list")
                    )
                }
                // 家里气泡宿主（2026-10-27 气泡搬家）：原 PetZone 列尾宿主移至
                // 此处——与 Dock 宠物同一外层 Box、同一中轴线（BottomCenter＋
                // fillMaxWidth，内容居中对齐宠物中心），尾巴朝下指宠物
                // （tailDown=true 不变）；按钮在文本下方同一中轴线，布局一字
                // 不动。门控：家里只在 !intentInputMode（= PetZone 的
                // !inputActive，HomeScreen 传入 intentInputMode）且清单未开时
                // 挂载——清单与气泡同锚点须让位（意图宿主同款门控；speech 状态
                // 在仓储里不丢，清单关闭后重挂复现）。单挂载点契约不变：
                // PetZone 挂载点已删，意图宿主只在聚焦覆盖层。
                // 拖拽跟头：跟随量取本 perch 的 DragBodyState（头横移、
                // －头升＋头沉），与 FloatingPet 头盒同一量（dragHeadX／
                // dragHeadY），故拖拽中气泡贴头走、不脱节。
                if (!intentInputMode && !petActionsVisible) {
                    PetSpeechBubbleHost(
                        tailDown = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .offset(
                                x = dragHeadXdp,
                                y = -(dockPanelHeight + shoulderRise + shoulderPeakDp.dp - petPress + floatPetHeight + 8.dp) - dragHeadRiseDp + dragHeadSinkDp
                            )
                    )
                }
                } // v6 外层 Box（面板 + 前面图层七仔）
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
 *  v0.68.101：R-1 的行内七仔格已移除（宠物改漂在面板上方，见 HybridPet），
 *  本行回到 4 应用位原始分档（56 图标 / 16 间距）。 */
@Composable
private fun DockRow4(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    menuApp: AppInfo?,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    isLandscape: Boolean = false,
    // 肩线弹性：上报图标窗口中心（供涟漪中心与注视方位计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
) {
    // v0.68.82（第 3 项）：横屏改回横向（同竖屏），不再竖排
    BoxWithConstraints(
        modifier = Modifier
            .let { if (isLandscape) it.wrapContentWidth() else it.fillMaxWidth() }
            .padding(horizontal = 20.dp, vertical = 16.dp)
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
                    corner = if (iconSize >= 56.dp) 16.dp else 14.dp,
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
    // 肩线弹性：上报图标窗口中心（供涟漪中心与注视方位计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
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
    menuApp: AppInfo?,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onDismissMenu: () -> Unit,
    modifier: Modifier = Modifier,
    // 肩线弹性：上报图标窗口中心（供涟漪中心与注视方位计算）
    onIconPositioned: (String, Offset) -> Unit = { _, _ -> }
) {
    val hapticView = LocalView.current
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
        AppPopupMenu(
            app = app,
            expanded = menuApp == app,
            onDismiss = onDismissMenu
        )
    }
}

/**
 * 横屏场景切换（2026-10-26，Bob 22:57 最终口径）：Dock 卡片进出是否只做
 * 淡入淡出、不滑动。横屏＋聚焦切换信号为真时 fade-only；竖屏恒走原滑动＋
 * 淡入淡出（竖屏进态位移、Dock 滑入／滑出、漏斗四步时序一字不动）。
 * 调用方传不同的信号：退出（离场）传当帧 intentInputMode，重进（回场）传
 * 上一帧 intentInputMode（SideEffect 滞后一帧，翻转当帧仍为 true）。
 * 纯函数，单测锁死。
 */
internal fun dockCardFocusSwapIsFadeOnly(isLandscape: Boolean, inFocusSignal: Boolean): Boolean =
    isLandscape && inFocusSignal

/**
 * 竖屏切换闪烁修复（2026-10-27）：Dock 宠物挂载门（纯函数，单测锁死）。
 * 输入态让位意图宠物；退出 funnel 进行中仍让位——Dock 宠物只在落地
 * （finishExit）后挂载，否则与在途意图宠物在不同高度同时在场。
 */
internal fun dockPetVisible(intentInputMode: Boolean, exitInProgress: Boolean): Boolean =
    !intentInputMode && !exitInProgress

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
