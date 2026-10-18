package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.apps.loadShortcutIcon
import com.bobot.ailauncher.ui.pet.QuickActionItem
import com.bobot.ailauncher.ui.pet.QuickActionsPrefs
import com.bobot.ailauncher.ui.pet.launchQuickAction
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v0.68.17：盲操触发简化（用户拍板）。
 *
 * 去掉：边缘区要求、120ms 停留激活、导航后 800ms 冷却。
 * 新触发：首页背景任何左滑直接触发（组件已消费的手势除外：
 * 七仔拖拽、应用行横扫等优先）。
 *
 * 扇形选择器：
 * 1. 左滑/右滑触发 → 以触发点为圆心展开扇形菜单（左扇朝左/右扇朝右镜像）
 * 2. v0.68.38：所有段等宽 36°（Bob 拍板：不用大角度，拍照只占最顺手的位置）；
 *    段从上到下：录像 / 拍照 / 快捷装备段（动态，最多 2 段，
 *    取七仔装备排序前两位）/ 录音（固定最下方）；总角度 108°/144°/180°；
 *    拍照锚定正中方向（左扇 180° / 右扇 0°）+ 边界 ±10° 磁吸——最容易触发
 * 3. 手指滑入某段 → 伸长高亮 + 震动 tick + 七仔报段名（七仔从段外缘探出）
 * 4. 松手 → 启动高亮段；无选择松手 → 拍照（零决策）；快滑（甩）→ 拍照（忽略选择）；
 *    滑回 → 取消
 *
 * v0.68.20：视觉重构——展开弹性动画、段间分隔线、选中光晕+放大、
 * 图标+文字排版；拍照为最默认操作。
 *
 * v0.68.28：右滑镜像——首页背景右滑也触发，扇形朝右展开，
 * 角度/伸长动效/取消/圆心钳制全部镜像；七仔中心 mini 不动。
 *
 * v0.68.29 方案C「选中探出」：中心七仔 mini 移除，换成触摸涟漪（极淡圆环）；
 * 手指滑入某段 → 七仔从该段外缘+36dp 处探出（64dp，scale spring+淡入）；
 * 换段 → 七仔位移动画跳到新段外缘；滑出 → 缩回；位置钳制防出屏；左右扇共用镜像逻辑。
 *
 * v0.68.35：旧"自定义☆"固定槽位作废——快捷段直连七仔装备（QuickActionsPrefs），
 * 增删复用七仔装备长按/+ 流程；整体缩小（外半径 120dp）；毛玻璃质感段底 +
 * 1px 高光描边；展开 stagger（25ms/段）；七仔分模式姿态（拍照jump/录像wave/
 * 录音hold/快捷main）+ 旋转探出 + 钻回。
 *
 * v0.68.36：拍照最容易触发——拍照段 60°→84°（正中方向上下各 42°）；边界磁吸
 * （落点在拍照边界外 ±10° 内吸附为拍照，只影响判定不影响绘制）；录音固定最下方
 * （离拍照最远）；快滑覆盖（松手速度超 4500px/s 直接=拍照，忽略段选择）。
 *
 * v0.68.37：窄段标题竖排（<40° 的录音/快捷段，中文标签 upright 沿径向堆叠；
 * 非中文或超 4 字 fallback 小一号字号 + 省略）；选中反馈改图标（删除方案C七仔
 * 探出——选中段外缘+36dp 跟一枚 32dp 线稿小图标，淡入淡出 + 轻微 scale；
 * 快捷段用装备自己的图标，无图标显示标签首字小圆章）；保留七仔语音报段名、
 * 段伸长动效、震动、中心涟漪。
 *
 * v0.68.38：等宽 36°——拍照 84° 收掉，所有段等宽，拍照只锚定最顺手的位置
 * （正中方向）；标题竖排分支删除，统一横排 + maxLines=1 + Ellipsis；
 * 保留磁吸 ±10°、快滑 4500px/s 直拍、毛玻璃+高光、stagger、选中图标、涟漪、语音。
 *
 * v0.68.40（Bob 拍板）：系统只默认放相机——扇形 = 拍照（唯一系统预设，
 * 36°，锚定正中）+ 快捷装备段（36°/段，最多 4 段，取七仔装备排序前 4，
 * 顺序 下方1/上方1/下方2/上方2）；录像/录音预设段作废；语音胶囊全家桶删除
 * （VoiceCapsule.kt、BlindAudioRecorder、QuickMemoStore、转文字）；
 * 0 装备时左滑/右滑直接拍照（不展开扇形），七仔引导一次；
 * 段标签外移到外圈约 95dp（v0.68.41：14sp，拍照 15sp 加粗；径向旋转，
 * 文字方向跟扇形同角度）。
 *
 * 保留：三重反馈（震动/七仔语音/视觉高亮）。
 */

/** 扇形段：拍照（系统唯一预设）+ 用户快捷装备段 */
sealed interface FanSegment {
    data object Photo : FanSegment
    data class Quick(val item: QuickActionItem) : FanSegment
}

/** 扇形方向：LEFT=朝左（左滑触发），RIGHT=朝右（右滑触发，镜像） */
enum class FanDirection { LEFT, RIGHT }

// 扇形几何（Canvas 角度制：0°=正右方，顺时针为正，y 向下）
// v0.68.40：拍照（唯一预设，36°）+ 快捷装备段（36°/段，最多 4 段，取排序前 4）；
// 拍照锚定正中方向（左扇 180° / 右扇 0°）；快捷槽位顺序：下方1/上方1/下方2/上方2；
// 总角度：1+0段=36°，1+4段=180°；圆心 Y 钳制按实际跨度算
private const val FAN_R_OUTER_DP = 120f // v0.68.35：148→120，整体缩小更精致
// v0.68.24 方案A：选中段伸长到的外半径；v0.68.35：176→140（+20dp）
private const val FAN_R_OUTER_SELECTED_DP = 140f
private const val FAN_R_INNER_DP = 46f // 死区内圈不变
private const val FAN_EDGE_MARGIN_DP = 16f // v0.68.42：12→16（曲面屏边缘安全边距）
// v0.68.40：段标签外移半径（效果图确认：外圈约 100dp，手指盖不住）
// v0.68.41：100→95（标签径向旋转后半长约 21~28dp，径向跨度约 67~123dp，
// 不进 46dp 死区、基本不出 120dp 外缘）
private const val FAN_LABEL_R_DP = 95f

private const val FAN_SWEEP_SEG = 36f // v0.68.38：所有段等宽 36°
private const val MAX_QUICK_SEGS = 4 // v0.68.40：2→4（录像/录音预设作废，装备成唯一其他段）
/** v0.68.40：快捷槽位角度（Canvas 坐标，startAngle），顺序 下方1/上方1/下方2/上方2 */
private val QUICK_SLOTS_LEFT = listOf(126f, 198f, 90f, 234f)
private val QUICK_SLOTS_RIGHT = listOf(18f, -54f, 54f, -90f)
// v0.68.36：拍照磁吸——落点在拍照边界外 ±10° 内一律吸附为拍照（只影响判定不影响绘制）
private const val FAN_PHOTO_SNAP_DEG = 10f
// v0.68.36：快滑覆盖阈值——松手速度超此值直接=拍照（"甩"的量级，保守取高；px/s）
private const val FLING_PHOTO_VELOCITY_PX_S = 4500f

/**
 * v0.68.40：各段扫过角度（等宽 36°）——拍照 + 快捷段×N（N≤4）；
 * 绘制与命中判定共用同一套。总角度：36°×(1+N)
 */
private fun fanSweeps(quickCount: Int): FloatArray {
    val n = quickCount.coerceIn(0, MAX_QUICK_SEGS)
    return FloatArray(1 + n) { FAN_SWEEP_SEG }
}

/**
 * 第 i 段的 (startAngle, sweepAngle)，Canvas 角度制；与命中判定共用。
 * v0.68.40：index 0 = 拍照（锚定正中方向：左扇 [162,198] / 右扇 [-18,18]）；
 * index 1..N = 快捷装备（槽位顺序 下方1/上方1/下方2/上方2）。
 */
private fun segmentAngles(
    i: Int,
    sweeps: FloatArray,
    direction: FanDirection = FanDirection.LEFT,
): Pair<Float, Float> {
    val seg = FAN_SWEEP_SEG
    if (i == 0) {
        return if (direction == FanDirection.LEFT) Pair(162f, seg)
        else Pair(-18f, seg)
    }
    val slots = if (direction == FanDirection.LEFT) QUICK_SLOTS_LEFT else QUICK_SLOTS_RIGHT
    val s = slots.getOrElse(i - 1) { slots.last() }
    return Pair(s, seg)
}

/** 扇形相对圆心的上下伸展系数（÷R）：v0.68.40 按实际段角度采样计算 */
private fun fanVerticalReach(
    sweeps: FloatArray,
    direction: FanDirection,
): Pair<Float, Float> {
    var up = 0f
    var down = 0f
    fun norm360(a: Float): Float = ((a % 360f) + 360f) % 360f
    for (i in sweeps.indices) {
        val (s, sw) = segmentAngles(i, sweeps, direction)
        val angles = mutableListOf(s, s + sw / 2f, s + sw)
        // Canvas 坐标：90°=正下方极值，270°=正上方极值
        if (norm360(90f) in norm360(s)..norm360(s + sw)) angles.add(90f)
        if (norm360(270f) in norm360(s)..norm360(s + sw)) angles.add(270f)
        for (a in angles) {
            val y = sin(Math.toRadians(a.toDouble())).toFloat() // canvas y 向下：+ 为下
            if (y < 0f) up = maxOf(up, -y) else down = maxOf(down, y)
        }
    }
    return Pair(up, down)
}

/** 扇形命中：返回 sector index（0=拍照），-1 = 死区内或扇形外
 * v0.68.40：拍照 [162,198]（左扇）/ [-18,18]（右扇）+ 磁吸 ±10° 优先；
 * 快捷段按槽位顺序 下方1/上方1/下方2/上方2 */
private fun sectorForIndex(
    dx: Float,
    dy: Float,
    deadZonePx: Float,
    sweeps: FloatArray,
    direction: FanDirection = FanDirection.LEFT,
): Int {
    if (sqrt(dx * dx + dy * dy) < deadZonePx) return -1
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (deg < 0f) deg += 360f
    val seg = FAN_SWEEP_SEG
    return if (direction == FanDirection.LEFT) {
        // 拍照 [162,198] + 磁吸：落点在其边界外 ±10° 内一律吸附为拍照
        if (deg >= 162f - FAN_PHOTO_SNAP_DEG && deg <= 198f + FAN_PHOTO_SNAP_DEG) 0
        else {
            // 快捷槽位：下方1 [126,162] / 上方1 [198,234] / 下方2 [90,126] / 上方2 [234,270]
            val n = (sweeps.size - 1).coerceAtLeast(0)
            for (j in 0 until minOf(n, QUICK_SLOTS_LEFT.size)) {
                val lo = QUICK_SLOTS_LEFT[j]
                if (deg >= lo && deg < lo + seg) return j + 1
            }
            -1 // 扇形外
        }
    } else {
        // 右扇：归一化到 -180°~180° 再判定
        val degR = if (deg > 180f) deg - 360f else deg
        // 拍照 [-18,18] + 磁吸（镜像）
        if (degR >= -18f - FAN_PHOTO_SNAP_DEG && degR <= 18f + FAN_PHOTO_SNAP_DEG) 0
        else {
            // 快捷槽位：下方1 [18,54] / 上方1 [-54,-18] / 下方2 [54,90] / 上方2 [-90,-54]
            val n = (sweeps.size - 1).coerceAtLeast(0)
            for (j in 0 until minOf(n, QUICK_SLOTS_RIGHT.size)) {
                val lo = QUICK_SLOTS_RIGHT[j]
                if (degR >= lo && degR < lo + seg) return j + 1
            }
            -1 // 扇形外
        }
    }
}

/** 盲操 UI 状态：隐藏 / 扇形选择中（modeIndex=-1 表示无高亮） */
sealed interface BlindUiState {
    data object Hidden : BlindUiState
    data class Selecting(
        val centerX: Float,
        val centerY: Float,
        val modeIndex: Int,
        // v0.68.28：扇形方向（右滑镜像）
        val direction: FanDirection = FanDirection.LEFT,
        // v0.68.35：动态段列表（固定3段 + 快捷装备段）
        val segments: List<FanSegment> = emptyList(),
    ) : BlindUiState
    /** v0.68.42：收起中——扇形播 120ms 内收+淡出，播完 UI 回调 onDismissFinished */
    data class Dismissing(
        val centerX: Float,
        val centerY: Float,
        val direction: FanDirection = FanDirection.LEFT,
        val segments: List<FanSegment> = emptyList(),
    ) : BlindUiState
    /** v0.68.42：拍照快门闪——白闪 80ms + 咔嚓震感后进相机 */
    data object PhotoFlash : BlindUiState
}

/**
 * 盲操手势状态机。由 HomeScreen 的 pointerInput 转发事件驱动（UI 线程）。
 */
class BlindCaptureController(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val setUi: (BlindUiState) -> Unit,
    private val triggerPx: Float,
) {
    companion object {
        private const val TAG = "BlindCapture"
    }

    private val density = context.resources.displayMetrics.density
    // 死区半径 = 内圈半径（与视觉一致）：圆心附近无扇区
    private val deadZonePx = FAN_R_INNER_DP * density
    private val cancelBackPx = 24f * density
    private val fanROuterPx = FAN_R_OUTER_DP * density
    private val fanMarginPx = FAN_EDGE_MARGIN_DP * density

    private var tracking = false
    private var triggered = false
    // v0.68.28：扇形方向（左滑=朝左，右滑=朝右镜像）
    private var fanDir = FanDirection.LEFT
    // v0.68.35：本次手势的动态段列表 + 角度（触发时从七仔装备读最新列表）
    private var segments: List<FanSegment> = emptyList()
    private var sweeps: FloatArray = fanSweeps(0)
    // v0.68.32：手势开始以来的最大左行/右行位移（正值）——方向按主导者判定，
    // 落点后反向小勾（<24dp）不再误锁方向
    private var maxLeftPx = 0f
    private var maxRightPx = 0f
    // v0.68.32：底部 Dock 区高度（px，0=无 Dock）——圆心钳制用
    var dockZonePx: Float = 0f
    // v0.68.36：快滑覆盖——VelocityTracker 记松手速度，超阈值直接=拍照
    private val velocityTracker = VelocityTracker()
    private var centerX = 0f
    private var centerY = 0f
    private var modeIndex = -1

    // v0.68.39：诊断链路已删除（v0.68.12 屏上诊断浮层使命结束）；保留 Log.d 供开发期使用

    /**
     * v0.68.42：收起——扇形已展开时走 Dismissing（120ms 内收+淡出动画，
     * 替代瞬间消失），动画播完 UI 回调 onDismissFinished；未展开直接 Hidden。
     */
    private fun dismissFan() {
        val wasTriggered = triggered
        tracking = false
        triggered = false
        modeIndex = -1
        if (wasTriggered) {
            setUi(BlindUiState.Dismissing(centerX, centerY, fanDir, segments))
        } else {
            setUi(BlindUiState.Hidden)
        }
    }

    /** v0.68.42：收起动画播完，UI 侧回调收尾 */
    fun onDismissFinished() {
        setUi(BlindUiState.Hidden)
    }

    /**
     * v0.68.40：本次手势的扇形段 = 拍照（系统唯一预设）+ 快捷装备
     * （七仔装备排序前 4，槽位顺序 下方1/上方1/下方2/上方2）。
     * 每次触发时读最新装备列表。
     */
    private fun buildSegments(): List<FanSegment> {
        val quicks = try {
            QuickActionsPrefs.load(context).take(MAX_QUICK_SEGS)
        } catch (_: Exception) {
            emptyList()
        }
        return listOf(FanSegment.Photo) + quicks.map { FanSegment.Quick(it) }
    }

    /** v0.68.40：0 装备引导（只说一次） */
    private fun guideZeroQuickOnce() {
        try {
            val prefs = context.getSharedPreferences("blind_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("zero_quick_guided", false)) {
                prefs.edit().putBoolean("zero_quick_guided", true).apply()
                PetRepository.say("左滑现在只拍照片，去七仔装备里加你想要的功能")
            }
        } catch (_: Exception) { }
    }

    /** 手势开始：无条件跟踪（组件已消费的手势到不了这里） */
    fun onStart() {
        tracking = true
        triggered = false
        modeIndex = -1
        maxLeftPx = 0f
        maxRightPx = 0f
        velocityTracker.resetTracking()
        Log.d(TAG, "onStart tracking")
    }

    fun onMove(
        change: PointerInputChange,
        drag: Offset,
        totalX: Float,
        totalY: Float,
        size: IntSize,
    ) {
        if (!tracking) return
        val pos = change.position
        // v0.68.36：记录速度（快滑覆盖用）
        velocityTracker.addPosition(change.uptimeMillis, pos)
        if (!triggered) {
            // 触发：左滑/右滑过阈值且横向主导 → 直接展开扇形（无停留、无边缘要求）
            // v0.68.28：右滑也触发，扇形朝右镜像
            // v0.68.32：主导方向判定——跟踪最大左行/右行位移，任一超阈值才触发，
            // 方向取较大者；落点后的反向小勾（<24dp）不再误锁方向；触发后方向锁定不变
            maxLeftPx = maxOf(maxLeftPx, -totalX)
            maxRightPx = maxOf(maxRightPx, totalX)
            val leftSwipe = maxLeftPx >= triggerPx && maxLeftPx >= maxRightPx &&
                maxLeftPx > abs(totalY) * 1.5f
            val rightSwipe = maxRightPx >= triggerPx && maxRightPx > maxLeftPx &&
                maxRightPx > abs(totalY) * 1.5f
            if (leftSwipe || rightSwipe) {
                triggered = true
                fanDir = if (leftSwipe) FanDirection.LEFT else FanDirection.RIGHT
                // v0.68.40：读最新七仔装备，构建本次手势的动态段 + 角度
                segments = buildSegments()
                sweeps = fanSweeps(segments.count { it is FanSegment.Quick })
                // v0.68.40：0 装备——不展开扇形，直接拍照；七仔引导一次
                if (segments.size == 1) {
                    launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
                    guideZeroQuickOnce()
                    dismissFan()
                    return
                }
                // v0.68.36：扇形上下伸展按实际跨度算（拍照锚定正中，快捷多时向下延伸更多）
                val (upK, downK) = fanVerticalReach(sweeps, fanDir)
                val upPx = FAN_R_OUTER_SELECTED_DP * upK * density
                val downPx = FAN_R_OUTER_SELECTED_DP * downK * density
                // 圆心钳制，保证扇形不被屏幕边缘裁掉（镜像：左扇防左边缘，右扇防右边缘）
                centerX = if (fanDir == FanDirection.LEFT) {
                    pos.x.coerceAtLeast(fanROuterPx + fanMarginPx)
                } else {
                    val maxX = (size.width - fanROuterPx - fanMarginPx)
                        .coerceAtLeast(fanMarginPx)
                    pos.x.coerceAtMost(maxX)
                }
                val minY = upPx + fanMarginPx
                val maxY = (size.height - downPx - fanMarginPx)
                    .coerceAtLeast(minY)
                // v0.68.32：Dock 钳制——触摸点离底部 Dock 区 < 扇形外半径（含选中伸长）
                // 时，圆心上移到 Dock 上方，保证扇形主体完整可见
                val fanROuterSelPx = FAN_R_OUTER_SELECTED_DP * density
                centerY = if (dockZonePx > 0 &&
                    size.height - pos.y < fanROuterSelPx + fanMarginPx
                ) {
                    (size.height - dockZonePx - fanROuterSelPx - fanMarginPx)
                        .coerceAtLeast(minY)
                } else {
                    pos.y.coerceIn(minY, maxY)
                }
                modeIndex = -1
                setUi(BlindUiState.Selecting(centerX, centerY, -1, fanDir, segments))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                PetRepository.say("拍点什么？")
                Log.d(TAG, "diag: fan展开")
                Log.d(TAG, "triggered dir=$fanDir center=($centerX, $centerY)")
            }
            return
        }
        // 滑回 → 取消（镜像：左扇右滑回取消，右扇左滑回取消）
        val cancelBack = if (fanDir == FanDirection.LEFT) {
            pos.x > centerX + cancelBackPx
        } else {
            pos.x < centerX - cancelBackPx
        }
        if (cancelBack) {
            Log.d(TAG, "diag: idle(滑回取消)")
            dismissFan()
            return
        }
        val idx = sectorForIndex(pos.x - centerX, pos.y - centerY, deadZonePx, sweeps, fanDir)
        if (idx != modeIndex) {
            modeIndex = idx
            setUi(BlindUiState.Selecting(centerX, centerY, idx, fanDir, segments))
            if (idx >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PetRepository.say(segmentLabel(idx))
                Log.d(TAG, "diag: sector=${segmentLabel(idx)}")
            }
        }
    }

    /** v0.68.40：段显示名——拍照 / 快捷段读装备短标签 */
    private fun segmentLabel(idx: Int): String {
        return when (val s = segments.getOrNull(idx)) {
            is FanSegment.Photo -> "拍照"
            is FanSegment.Quick -> s.item.label.takeIf { it.isNotBlank() } ?: "快捷"
            null -> ""
        }
    }

    /**
     * 手指抬起。返回 true 表示被盲操消费，调用方跳过原有纵向逻辑。
     * v0.68.20：无选择松手（快滑）= 拍照，零决策；右滑回取消已在 onMove 处理。
     * v0.68.36：快滑覆盖——松手速度超阈值直接=拍照（快=着急=拍照，忽略段选择）。
     */
    fun onEnd(): Boolean {
        if (!tracking) return false
        val wasTriggered = triggered
        val idx = modeIndex
        val flingPhoto = try {
            val v = velocityTracker.calculateVelocity()
            v.x * v.x + v.y * v.y >=
                FLING_PHOTO_VELOCITY_PX_S * FLING_PHOTO_VELOCITY_PX_S
        } catch (_: Exception) { false }
        if (wasTriggered) {
            // v0.68.40：拍照永远是 index 0
            val seg = segments.getOrNull(idx)
            if (flingPhoto || idx < 0 || seg is FanSegment.Photo) {
                // v0.68.42：拍照 signature——白闪 80ms + 咔嚓震感 → 进相机
                commitPhotoWithFlash()
                Log.d(TAG, "diag: commit(拍照-快门闪)")
            } else {
                dismissFan()
                commit(idx)
                Log.d(TAG, "diag: commit(${segmentLabel(idx)})")
            }
            return true
        }
        dismissFan()
        return false
    }

    fun onCancel() {
        dismissFan()
    }

    // v0.68.42：快门闪 token——80ms 内又有新手势则旧 flash 回调作废
    private var flashToken = 0

    /** v0.68.42：拍照快门闪——白闪 80ms + 强短震（咔嚓感）→ 再进系统相机 */
    private fun commitPhotoWithFlash() {
        tracking = false
        triggered = false
        modeIndex = -1
        setUi(BlindUiState.PhotoFlash)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val t = ++flashToken
        Handler(Looper.getMainLooper()).postDelayed({
            if (t != flashToken) return@postDelayed
            launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
            setUi(BlindUiState.Hidden)
        }, 80L)
    }

    /** v0.68.40：拍照（系统唯一预设）/ 快捷装备段（复用 launchQuickAction） */
    private fun commit(idx: Int) {
        when (val s = segments.getOrNull(idx)) {
            is FanSegment.Photo -> {
                Log.d(TAG, "commit photo")
                launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
            }
            is FanSegment.Quick -> {
                Log.d(TAG, "commit quick=${s.item.label}")
                launchQuickAction(context, s.item)
            }
            null -> Unit
        }
    }

    /**
     * v0.68.22：找系统自带相机包名——query 所有能处理该 action 的应用，
     * 优先选 system app（用户手机自带相机），避免调起三方相机。
     */
    private fun findSystemCameraPackage(action: String): String? =
        findSystemCameraPackageName(context, action)

    private fun launchCamera(action: String) {
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // v0.68.22：明确指定系统相机包，避免 resolve 到三方相机
            findSystemCameraPackage(action)?.let { intent.setPackage(it) }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                PetRepository.say("没找到相机应用")
            }
        } catch (_: Exception) {
            PetRepository.say("打不开相机")
        }
    }

}

/** v0.68.22：找系统自带相机包名（顶层函数，controller 与指示器图标共用） */
private fun findSystemCameraPackageName(context: Context, action: String): String? {
    return try {
        val pm = context.packageManager
        val infos = pm.queryIntentActivities(
            Intent(action), PackageManager.MATCH_DEFAULT_ONLY
        )
        if (infos.isEmpty()) return null
        infos.firstOrNull {
            it.activityInfo.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }?.activityInfo?.packageName
            ?: infos.first().activityInfo.packageName
    } catch (_: Exception) { null }
}

/** v0.68.37：选中段外缘跟随的线稿小图标（替代方案C七仔探出）
 * v0.68.40：拍照用 Material Outlined 相机线稿（深暖灰）；快捷段用装备自己的图标，
 * 无图标时显示标签首字小圆章。
 * v0.68.41：拍照改用本机相机 App 的真实图标（拿不到时 fallback 线稿）。 */
@Composable
private fun FanIndicatorIcon(seg: FanSegment) {
    val iconTint = Color(0xFF6B6259) // 深暖灰
    val context = LocalContext.current
    when (seg) {
        is FanSegment.Photo -> {
            val bitmap = remember {
                try {
                    findSystemCameraPackageName(context, MediaStore.ACTION_IMAGE_CAPTURE)
                        ?.let { context.packageManager.getApplicationIcon(it) }
                        ?.toFanImageBitmap()
                } catch (_: Exception) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "拍照",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "拍照",
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        is FanSegment.Quick -> {
            val bitmap = remember(seg.item.packageName, seg.item.shortcutId) {
                try {
                    val d: Drawable? = if (seg.item.shortcutId != null) {
                        loadShortcutIcon(context, seg.item.packageName, seg.item.shortcutId)
                    } else null
                    (d ?: try {
                        context.packageManager.getApplicationIcon(seg.item.packageName)
                    } catch (_: Exception) { null })?.toFanImageBitmap()
                } catch (_: Exception) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = seg.item.label,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                // 无图标：标签首字小圆章
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5E0D8)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seg.item.label.firstOrNull()?.toString() ?: "快",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = iconTint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Drawable → ImageBitmap（与 QuickActions.kt 内同名私有函数逻辑一致，本地复用） */
private fun Drawable.toFanImageBitmap(): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp.asImageBitmap()
}

/** v0.68.22：段内纯文字，已无图标（modeIcon 删除） */

/**
 * 扇形选择器 v0.68.20 视觉重构：
 * - 展开弹性动画（半径 spring + 淡入）
 * - 段间分隔线、选中光晕 + 图标放大
 * - 图标 + 文字排版（拍照段更大更突出）
 *
 * v0.68.35：
 * - 段动态化：固定 3 段（录像/拍照/录音）+ 快捷装备段（最多 2 段，30°/段）
 * - 整体缩小：外半径 120dp，选中伸长 140dp（+20dp）
 * - 毛玻璃质感：段底半透明白 + 1px 高光描边，选中段保留暖棕
 * - 展开 stagger：每段延迟 25ms 依次绽开
 */
@Composable
fun BlindCaptureFan(
    centerX: Float,
    centerY: Float,
    selectedIndex: Int,
    // v0.68.35：动态段列表（替代 customLabel）
    segments: List<FanSegment>,
    // v0.68.28：扇形方向（右滑镜像）
    direction: FanDirection = FanDirection.LEFT,
    // v0.68.42：收起中——播 120ms 内收+淡出
    dismiss: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 绘制与命中判定共用同一套动态角度
    val sweeps = fanSweeps(segments.count { it is FanSegment.Quick })
    val density = LocalDensity.current
    // v0.68.42：选中段暖金 #D9A05B alpha 0.85（替代暖棕 #D8CBB6，克制的记忆色）
    val selectedFill = Color(0xFFD9A05B).copy(alpha = 0.85f)
    val goldRipple = Color(0xFFD9A05B)
    val titleColor = AILauncherColors.Title
    val bodyColor = AILauncherColors.Body

    // v0.68.24 方案A：选中段径向伸长——每段外半径独立 spring；
    // v0.68.35：展开 0→120dp，选中该段 120→140dp（+20dp），滑出回弹；
    // v0.68.42：展开 stagger 改非线性级联 0/18/32/42/48ms（was 等距 25ms）
    val segShown = remember { mutableStateMapOf<Int, Boolean>() }
    val staggerDelays = remember { listOf(0L, 18L, 32L, 42L, 48L) }
    LaunchedEffect(Unit) {
        segments.indices.forEach { i ->
            launch {
                delay(staggerDelays.getOrElse(i) { 48L })
                segShown[i] = true
            }
        }
    }
    val segRadiiDp = segments.indices.map { i ->
        animateFloatAsState(
            targetValue = if (segShown[i] != true) 0f
            else if (i == selectedIndex) FAN_R_OUTER_SELECTED_DP
            else FAN_R_OUTER_DP,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 300f),
            label = "segR$i"
        )
    }
    // v0.68.35：整体淡入（段半径走 stagger，此处只做透明度）
    var fanShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fanShown = true }
    val fanAlpha by animateFloatAsState(
        targetValue = if (fanShown) 1f else 0f,
        animationSpec = tween(160),
        label = "fanAlpha"
    )
    // v0.68.42：收起 120ms 内收+淡出（替代瞬间消失）
    val dismissT by animateFloatAsState(
        targetValue = if (dismiss) 1f else 0f,
        animationSpec = tween(120),
        label = "dismissT"
    )
    // v0.68.29 方案C：中心触摸涟漪（呼吸感圆环，替代七仔 mini）
    val ripple = rememberInfiniteTransition(label = "ripple")
    val rippleR by ripple.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rippleR"
    )
    val rippleA by ripple.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rippleA"
    )

    // v0.68.29 方案C：探出七仔位置钳制需要屏幕尺寸
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { screenSize = it.size }
            // v0.68.42：收起内收——以扇形圆心为轴缩放+淡出
            .graphicsLayer {
                val sc = 1f - 0.18f * dismissT
                scaleX = sc
                scaleY = sc
                alpha = 1f - dismissT
                val w = screenSize.width.coerceAtLeast(1)
                val h = screenSize.height.coerceAtLeast(1)
                transformOrigin = TransformOrigin(
                    (centerX / w).coerceIn(0f, 1f),
                    (centerY / h).coerceIn(0f, 1f)
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fanAlpha)
        ) {
            val rInner = FAN_R_INNER_DP.dp.toPx()
            val center = Offset(centerX, centerY)
            // v0.68.24 方案A：选中光晕已删除，改径向伸长
            // v0.68.35：毛玻璃质感——段底半透明白 + 外缘 1px 高光描边；选中段暖金
            // v0.68.42：段间 2dp 分隔线删除，改 4dp 气隙（±1.4°，透背景）；
            // 未选中段整体透明度 0.6；选中段柔和投影（HW 安全：两层错位暗弧）
            val gapHalf = 1.4f
            segments.forEachIndexed { i, _ ->
                val (s, sw) = segmentAngles(i, sweeps, direction)
                val selected = i == selectedIndex
                val rOuter = segRadiiDp[i].value.dp.toPx()
                if (rOuter < 1f) return@forEachIndexed
                val segAlpha = if (selected) 1f else 0.6f
                if (selected) {
                    val shColor = Color(0xFF6E5A3E)
                    drawArc(
                        color = shColor.copy(alpha = 0.14f),
                        startAngle = s + gapHalf,
                        sweepAngle = sw - gapHalf * 2,
                        useCenter = true,
                        topLeft = Offset(centerX - rOuter - 4f, centerY - rOuter + 10f),
                        size = Size((rOuter + 4f) * 2f, (rOuter + 4f) * 2f)
                    )
                    drawArc(
                        color = shColor.copy(alpha = 0.10f),
                        startAngle = s + gapHalf,
                        sweepAngle = sw - gapHalf * 2,
                        useCenter = true,
                        topLeft = Offset(centerX - rOuter - 8f, centerY - rOuter + 18f),
                        size = Size((rOuter + 8f) * 2f, (rOuter + 8f) * 2f)
                    )
                }
                drawArc(
                    color = if (selected) selectedFill
                    else Color.White.copy(alpha = 0.55f * segAlpha),
                    startAngle = s + gapHalf,
                    sweepAngle = sw - gapHalf * 2,
                    useCenter = true,
                    topLeft = Offset(centerX - rOuter, centerY - rOuter),
                    size = Size(rOuter * 2f, rOuter * 2f)
                )
                // 1px 高光描边（外缘弧线）
                drawArc(
                    color = Color.White.copy(alpha = 0.5f * segAlpha),
                    startAngle = s + gapHalf,
                    sweepAngle = sw - gapHalf * 2,
                    useCenter = false,
                    topLeft = Offset(centerX - rOuter, centerY - rOuter),
                    size = Size(rOuter * 2f, rOuter * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            // v0.68.29 方案C：中心触摸涟漪（v0.68.42：改暖金，与选中段呼应）
            drawCircle(
                color = goldRipple.copy(alpha = 0.16f),
                radius = rInner * 0.55f,
                center = center
            )
            drawCircle(
                color = goldRipple.copy(alpha = rippleA),
                radius = (rInner * rippleR).coerceAtLeast(1f),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        // v0.68.37：选中反馈改图标（替代方案C七仔探出）——选中段外缘+36dp 处跟一枚
        // 32dp 线稿小图标，随选中段出现/切换/消失（淡入淡出 + 轻微 scale）；
        // 位置钳制防出屏；左右扇镜像共用
        val hasSel = selectedIndex in segments.indices
        val indSizeDp = 32.dp
        val indSizePx = with(density) { indSizeDp.toPx() }
        val indGapPx = with(density) { 36.dp.toPx() }
        val indEdgePx = with(density) { 40.dp.toPx() }
        val indRaw = if (hasSel) {
            val (ps, psw) = segmentAngles(selectedIndex, sweeps, direction)
            val pRad = Math.toRadians((ps + psw / 2).toDouble())
            val pR = with(density) { segRadiiDp[selectedIndex].value.dp.toPx() } +
                indGapPx
            Offset(
                centerX + cos(pRad).toFloat() * pR,
                centerY + sin(pRad).toFloat() * pR
            )
        } else null
        val indClamped = indRaw?.let {
            Offset(
                it.x.coerceIn(
                    indEdgePx,
                    (screenSize.width - indEdgePx).coerceAtLeast(indEdgePx)
                ),
                it.y.coerceIn(
                    indEdgePx,
                    (screenSize.height - indEdgePx).coerceAtLeast(indEdgePx)
                )
            )
        }
        // 无选中时锚点停留上次位置
        var indAnchor by remember { mutableStateOf(Offset(centerX, centerY)) }
        LaunchedEffect(indClamped) {
            if (indClamped != null) indAnchor = indClamped
        }
        val indOffset by animateOffsetAsState(
            targetValue = indClamped ?: indAnchor,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
            label = "indOffset"
        )
        // v0.68.42：选中图标 pop（0.6→1 overshoot，替代淡入）；换段重播一次
        val indScaleAnim = remember { Animatable(0.6f) }
        val indAlphaAnim = remember { Animatable(0f) }
        LaunchedEffect(hasSel, selectedIndex) {
            if (hasSel) {
                indScaleAnim.snapTo(0.6f)
                launch { indAlphaAnim.animateTo(1f, tween(100)) }
                indScaleAnim.animateTo(
                    1f,
                    spring(dampingRatio = 0.45f, stiffness = 1100f)
                )
            } else {
                launch { indAlphaAnim.animateTo(0f, tween(100)) }
                indScaleAnim.snapTo(0.6f)
            }
        }
        val indSeg = segments.getOrNull(selectedIndex)
        if (indAlphaAnim.value > 0.01f && indSeg != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (indOffset.x - indSizePx / 2f).roundToInt(),
                            (indOffset.y - indSizePx / 2f).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = indScaleAnim.value
                        scaleY = indScaleAnim.value
                        alpha = (indAlphaAnim.value * fanAlpha).coerceIn(0f, 1f)
                    }
                    .size(indSizeDp),
                contentAlignment = Alignment.Center
            ) {
                FanIndicatorIcon(seg = indSeg)
            }
        }
        // v0.68.20：图标 + 文字 overlay（Compose 排版）
        // v0.68.40：标签外移到外圈约 100dp（效果图确认，手指盖不住）；圆心不动；
        // v0.68.41：14sp（拍照 15sp 加粗）；标签径向旋转——文字方向跟扇形同角度，
        // α = centerAngle − θ_mid（左扇 180°/右扇 0°），绕标签自身中心旋转，
        // α 天然落在 ±90° 内，文字永远不会倒过来；选中指示器图标不旋转
        val rLabelPx = with(density) { FAN_LABEL_R_DP.dp.toPx() }
        val centerDeg = if (direction == FanDirection.LEFT) 180f else 0f
        segments.forEachIndexed { i, seg ->
            val (s, sw) = segmentAngles(i, sweeps, direction)
            val midDeg = s + sw / 2f
            val midRad = Math.toRadians(midDeg.toDouble())
            val lx = centerX + cos(midRad).toFloat() * rLabelPx
            val ly = centerY + sin(midRad).toFloat() * rLabelPx
            val selected = i == selectedIndex
            val isPhoto = seg is FanSegment.Photo
            val segLabel = when (seg) {
                is FanSegment.Photo -> "拍照"
                is FanSegment.Quick -> seg.item.label.takeIf { it.isNotBlank() } ?: "快捷"
            }
            val pop by animateFloatAsState(
                targetValue = if (selected) 1.22f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "segPop$i"
            )
            // v0.68.42：选中段字号 +2sp 过渡（14→16sp，拍照 15→17sp）
            val targetSize = when {
                selected && isPhoto -> 17f
                selected -> 16f
                isPhoto -> 15f
                else -> 14f
            }
            val sizeAnim by animateFloatAsState(
                targetValue = targetSize,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "segSize$i"
            )
            key(i) {
                // v0.68.38：统一横排（等宽 36° 下横排全部放得下，竖排分支删除）；
                // maxLines=1 + Ellipsis，超长装备名截断；
                // v0.68.41：rotationZ 径向旋转（文字跟扇形同角度）；
                // v0.68.42：letterSpacing 0.08em；未选中段透明度 0.6
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (lx - with(density) { 96.dp.toPx() } / 2f).roundToInt(),
                                (ly - with(density) { 48.dp.toPx() } / 2f).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                            rotationZ = centerDeg - midDeg
                            alpha = fanAlpha * (1f - dismissT) *
                                (if (selected) 1f else 0.6f)
                        }
                        .size(96.dp, 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segLabel,
                        fontSize = sizeAnim.sp,
                        letterSpacing = 0.08.em,
                        fontWeight = if (selected || isPhoto) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) titleColor else bodyColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
