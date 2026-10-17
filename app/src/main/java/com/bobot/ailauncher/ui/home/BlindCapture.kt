package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.apps.loadShortcutIcon
import com.bobot.ailauncher.ui.pet.QuickActionItem
import com.bobot.ailauncher.ui.pet.QuickActionsPrefs
import com.bobot.ailauncher.ui.pet.launchQuickAction
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.io.File
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
 * 保留：三重反馈（震动/七仔语音/视觉高亮）、诊断浮层。
 */

/** 扇形固定段（从上到下）：录像 / 拍照 / 快捷装备段 / 录音（录音固定最下方） */
enum class BlindMode(val label: String) {
    VIDEO("录像"),
    PHOTO("拍照"),
    AUDIO("录音"),
}

/**
 * v0.68.35：扇形段动态化——固定 3 段（录像/拍照/录音）+ 用户快捷装备段（最多 2 段，
 * 取七仔装备列表排序前两位）。增删复用七仔装备现有流程，扇形每次展开时读最新列表。
 */
sealed interface FanSegment {
    data class BuiltIn(val mode: BlindMode) : FanSegment
    data class Quick(val item: QuickActionItem) : FanSegment
}

/** 扇形方向：LEFT=朝左（左滑触发），RIGHT=朝右（右滑触发，镜像） */
enum class FanDirection { LEFT, RIGHT }

// 扇形几何（Canvas 角度制：0°=正右方，顺时针为正，y 向下）
// v0.68.38：所有段等宽 36°（Bob 拍板：不用大角度，拍照只占最顺手的位置）；
// 段顺序自上而下 录像 / 拍照 / 快捷段×N / 录音；拍照锚定正中方向
// （左扇 180° / 右扇 0°）；总角度：无快捷 108°，1 快捷 144°，2 快捷 180°；
// 圆心 Y 钳制按实际总角度算
private const val FAN_R_OUTER_DP = 120f // v0.68.35：148→120，整体缩小更精致
// v0.68.24 方案A：选中段伸长到的外半径；v0.68.35：176→140（+20dp）
private const val FAN_R_OUTER_SELECTED_DP = 140f
private const val FAN_R_INNER_DP = 46f // 死区内圈不变
private const val FAN_EDGE_MARGIN_DP = 12f

private const val FAN_SWEEP_SEG = 36f // v0.68.38：所有段等宽 36°
private const val MAX_QUICK_SEGS = 2
// v0.68.36：拍照磁吸——落点在拍照边界外 ±10° 内一律吸附为拍照（只影响判定不影响绘制）
private const val FAN_PHOTO_SNAP_DEG = 10f
// v0.68.36：快滑覆盖阈值——松手速度超此值直接=拍照（"甩"的量级，保守取高；px/s）
private const val FLING_PHOTO_VELOCITY_PX_S = 4500f

/**
 * v0.68.38：各段扫过角度（等宽 36°）——录像 / 拍照 / 快捷段×N / 录音；
 * 绘制与命中判定共用同一套。总角度：无快捷 108°，1 快捷 144°，2 快捷 180°
 */
private fun fanSweeps(quickCount: Int): FloatArray {
    val n = quickCount.coerceIn(0, MAX_QUICK_SEGS)
    return FloatArray(2 + n + 1) { FAN_SWEEP_SEG }
}

/**
 * 第 i 段的 (startAngle, sweepAngle)，Canvas 角度制；与命中判定共用。
 * v0.68.38：等宽 36°，拍照锚定正中方向（左扇 180° [162,198] / 右扇 0° [-18,18]，
 * 最顺手的位置）；录像在上，快捷段与录音依次在下。快捷段再多拍照也不漂移。
 */
private fun segmentAngles(
    i: Int,
    sweeps: FloatArray,
    direction: FanDirection = FanDirection.LEFT,
): Pair<Float, Float> {
    val seg = FAN_SWEEP_SEG
    return if (direction == FanDirection.LEFT) {
        when (i) {
            0 -> Pair(198f, seg) // 录像 [198,234]（上）
            1 -> Pair(162f, seg) // 拍照 [162,198]（正中）
            else -> Pair(126f - (i - 2) * seg, seg) // 快捷0..N-1 / 录音（下）
        }
    } else {
        // 右扇镜像：拍照锚定正右方 0°
        when (i) {
            0 -> Pair(-54f, seg) // 录像 [-54,-18]（上）
            1 -> Pair(-18f, seg) // 拍照 [-18,18]（正中）
            else -> Pair(18f + (i - 2) * seg, seg) // 快捷0..N-1 / 录音（下）
        }
    }
}

/** 扇形相对圆心的上下伸展系数（÷R）：v0.68.38 等宽 36°，总角度 108°/144°/180°；
 * 左扇跨度 [126-36N, 234]，右扇跨度 [-54, 54+36N]（Canvas 角度制） */
private fun fanVerticalReach(
    sweeps: FloatArray,
    direction: FanDirection,
): Pair<Float, Float> {
    val n = (sweeps.size - 3).coerceAtLeast(0) // 快捷段数
    val (a0, a1) = if (direction == FanDirection.LEFT) {
        Pair(126f - n * FAN_SWEEP_SEG, 234f)
    } else {
        Pair(-54f, 54f + n * FAN_SWEEP_SEG)
    }
    var up = 0f
    var down = 0f
    val samples = mutableListOf(a0, a1)
    if (90f in a0..a1) samples.add(90f) // 正下方
    if (270f in a0..a1) samples.add(270f) // 正上方
    for (a in samples) {
        val y = sin(Math.toRadians(a.toDouble())).toFloat() // canvas y 向下：+ 为下
        if (y < 0f) up = maxOf(up, -y) else down = maxOf(down, y)
    }
    return Pair(up, down)
}

/** 扇形命中：返回 sector index（0=最上方），-1 = 死区内或扇形外
 * v0.68.38：等宽 36°；拍照 [162,198]（左扇）/ [-18,18]（右扇）+ 磁吸 ±10°；
 * 录像在上，快捷段/录音依次在下 */
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
        if (deg >= 162f - FAN_PHOTO_SNAP_DEG && deg <= 198f + FAN_PHOTO_SNAP_DEG) 1
        else if (deg > 198f) {
            if (deg <= 234f) 0 else -1 // 录像 [198,234] / 扇形外
        } else {
            // 快捷段/录音：index 2..，第 i 段 [126-(i-2)*36, 162-(i-2)*36]
            for (i in 2 until sweeps.size) {
                val lo = 126f - (i - 2) * seg
                if (deg >= lo && deg < lo + seg) return i
            }
            -1 // 扇形外
        }
    } else {
        // 右扇：归一化到 -180°~180° 再判定
        val degR = if (deg > 180f) deg - 360f else deg
        // 拍照 [-18,18] + 磁吸（镜像）
        if (degR >= -18f - FAN_PHOTO_SNAP_DEG && degR <= 18f + FAN_PHOTO_SNAP_DEG) 1
        else if (degR < -18f) {
            if (degR >= -54f) 0 else -1 // 录像 [-54,-18] / 扇形外
        } else {
            // 快捷段/录音：index 2..，第 i 段 [18+(i-2)*36, 54+(i-2)*36]
            for (i in 2 until sweeps.size) {
                val lo = 18f + (i - 2) * seg
                if (degR >= lo && degR < lo + seg) return i
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
}

/**
 * App 内极简录音（MediaRecorder）。
 * v0.68.22：改存 MediaStore 公共目录 Music/Recordings/，用户在文件管理器/音乐 App 可找到。
 * （Android 10+ 用 RELATIVE_PATH，无需 WRITE_EXTERNAL_STORAGE 权限）
 */
object BlindAudioRecorder {
    var isRecording: Boolean = false
        private set
    // v0.68.24：录音时长（生成语音胶囊时显示）
    var lastDurationSec: Int = 0
        private set
    private var recorder: MediaRecorder? = null
    private var outPfd: android.os.ParcelFileDescriptor? = null
    private var outUri: android.net.Uri? = null
    private var startMs: Long = 0L

    fun start(context: Context): Boolean {
        if (isRecording) return true
        return try {
            // v0.68.22：MediaStore 公共目录
            val values = android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "rec_${System.currentTimeMillis()}.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            val pfd = context.contentResolver.openFileDescriptor(uri, "w") ?: run {
                context.contentResolver.delete(uri, null, null)
                return false
            }
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(pfd.fileDescriptor)
            r.prepare()
            r.start()
            startMs = System.currentTimeMillis()
            recorder = r
            outPfd = pfd
            outUri = uri
            isRecording = true
            true
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) { }
            try { outPfd?.close() } catch (_: Exception) { }
            outUri?.let { try { context.contentResolver.delete(it, null, null) } catch (_: Exception) { } }
            recorder = null
            outPfd = null
            outUri = null
            isRecording = false
            false
        }
    }

    /** 停止并返回 MediaStore Uri（可空）；同时结算本次时长 */
    fun stop(context: Context): android.net.Uri? {
        val uri = outUri
        if (startMs > 0) {
            lastDurationSec =
                ((System.currentTimeMillis() - startMs) / 1000).toInt().coerceAtLeast(1)
            startMs = 0L
        }
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        try { outPfd?.close() } catch (_: Exception) { }
        recorder = null
        outPfd = null
        outUri = null
        isRecording = false
        return uri
    }
}

/** 速记备忘存储（SharedPreferences JSON 列表） */
object QuickMemoStore {
    private const val PREFS = "quick_memo_prefs"
    private const val KEY_MEMOS = "memos_json"

    data class Memo(val text: String, val time: Long)

    fun all(context: Context): List<Memo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MEMOS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Memo(o.getString("text"), o.getLong("time"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(context: Context, text: String) {
        val list = all(context).toMutableList()
        list.add(0, Memo(text, System.currentTimeMillis()))
        // 只保留最近 50 条
        val trimmed = list.take(50)
        val arr = org.json.JSONArray()
        trimmed.forEach { m ->
            arr.put(org.json.JSONObject().put("text", m.text).put("time", m.time))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MEMOS, arr.toString()).apply()
    }
}

/**
 * 盲操手势状态机。由 HomeScreen 的 pointerInput 转发事件驱动（UI 线程）。
 */
class BlindCaptureController(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val setUi: (BlindUiState) -> Unit,
    private val requestAudioPermission: () -> Unit,
    private val triggerPx: Float,
    private val onDiag: (String) -> Unit = {},
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

    private fun diagText(s: String) = onDiag(s)

    private fun diagState(s: String) {
        onDiag(s)
        diagTick()
        Log.d(TAG, "diag: $s")
    }

    // 50ms 精确短震动（Compose 1.7 的 HapticFeedbackType 只有基础款，
    // 用系统 Vibrator；VIBRATE 为普通权限，安装即授）
    private fun diagTick() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        50, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(50)
            }
        } catch (_: Exception) { }
    }

    private fun reset() {
        tracking = false
        triggered = false
        modeIndex = -1
        setUi(BlindUiState.Hidden)
    }

    /**
     * v0.68.35：本次手势的扇形段 = 录像 / 拍照 / 快捷装备（七仔装备排序前两位）/ 录音。
     * v0.68.36：录音固定最下方，离拍照最远。每次触发时读最新装备列表。
     */
    private fun buildSegments(): List<FanSegment> {
        val quicks = try {
            QuickActionsPrefs.load(context).take(MAX_QUICK_SEGS)
        } catch (_: Exception) {
            emptyList()
        }
        return listOf(
            FanSegment.BuiltIn(BlindMode.VIDEO),
            FanSegment.BuiltIn(BlindMode.PHOTO),
        ) + quicks.map { FanSegment.Quick(it) } +
            listOf(FanSegment.BuiltIn(BlindMode.AUDIO))
    }

    /** 手势开始：无条件跟踪（组件已消费的手势到不了这里） */
    fun onStart() {
        tracking = true
        triggered = false
        modeIndex = -1
        maxLeftPx = 0f
        maxRightPx = 0f
        velocityTracker.resetTracking()
        diagText("idle(跟踪中)")
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
                // v0.68.35：读最新七仔装备，构建本次手势的动态段 + 角度
                segments = buildSegments()
                sweeps = fanSweeps(segments.count { it is FanSegment.Quick })
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
                diagState(if (fanDir == FanDirection.LEFT) "fan展开" else "fan展开(右)")
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
            diagState("idle(滑回取消)")
            reset()
            return
        }
        val idx = sectorForIndex(pos.x - centerX, pos.y - centerY, deadZonePx, sweeps, fanDir)
        if (idx != modeIndex) {
            modeIndex = idx
            setUi(BlindUiState.Selecting(centerX, centerY, idx, fanDir, segments))
            if (idx >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PetRepository.say(segmentLabel(idx))
                diagState("sector=${segmentLabel(idx)}")
            } else {
                diagText("sector=无")
            }
        }
    }

    /** v0.68.35：段显示名——快捷段读装备短标签 */
    private fun segmentLabel(idx: Int): String {
        return when (val s = segments.getOrNull(idx)) {
            is FanSegment.BuiltIn -> s.mode.label
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
        reset()
        if (wasTriggered) {
            val photoIdx = photoIndex()
            if (flingPhoto && photoIdx >= 0) {
                commit(photoIdx)
                diagState("commit(拍照-快滑)")
            } else if (idx >= 0) {
                commit(idx)
                diagState("commit(${segmentLabel(idx)})")
            } else if (photoIdx >= 0) {
                // v0.68.35：无选择松手（快滑）= 拍照，零决策
                commit(photoIdx)
                diagState("commit(拍照-默认)")
            }
            return true
        }
        return false
    }

    /** 拍照段下标（动态段列表中查找） */
    private fun photoIndex(): Int = segments.indexOfFirst {
        it is FanSegment.BuiltIn && it.mode == BlindMode.PHOTO
    }

    fun onCancel() {
        diagText("idle(手势取消)")
        reset()
    }

    /** 录音权限结果回调 */
    fun onAudioPermissionResult(granted: Boolean) {
        if (granted) startAudio()
        else PetRepository.say("没有麦克风权限，录不了音")
    }

    private fun commit(idx: Int) {
        when (val s = segments.getOrNull(idx)) {
            is FanSegment.BuiltIn -> {
                Log.d(TAG, "commit mode=${s.mode}")
                when (s.mode) {
                    BlindMode.PHOTO -> launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
                    BlindMode.VIDEO -> launchCamera(MediaStore.ACTION_VIDEO_CAPTURE)
                    BlindMode.AUDIO -> toggleAudio()
                }
            }
            // v0.68.35：快捷装备段——松手启动该快捷方式（复用 launchQuickAction）
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
    private fun findSystemCameraPackage(action: String): String? {
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

    private fun toggleAudio() {
        if (BlindAudioRecorder.isRecording) {
            val uri = BlindAudioRecorder.stop(context)
            val durSec = BlindAudioRecorder.lastDurationSec
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // v0.68.24：录音结束 → 生成语音胶囊（浮在七仔周围）
            if (uri != null) {
                com.bobot.ailauncher.ui.pet.VoiceCapsuleStore.add(
                    context, uri.toString(), durSec
                )
                PetRepository.say("录音变成胶囊啦，点它可以播放或转文字")
            } else {
                PetRepository.say("录音保存失败了")
            }
        } else if (hasAudioPermission()) {
            startAudio()
        } else {
            requestAudioPermission()
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun startAudio() {
        val ok = BlindAudioRecorder.start(context)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (ok) PetRepository.say("开始录音了，再做一次手势停止")
        else PetRepository.say("录音启动失败")
    }

}

/** v0.68.37：选中段外缘跟随的线稿小图标（替代方案C七仔探出）
 * 录像/拍照/录音用 Material Outlined 线稿（深暖灰）；快捷段用装备自己的图标，
 * 无图标时显示标签首字小圆章。 */
@Composable
private fun FanIndicatorIcon(seg: FanSegment) {
    val iconTint = Color(0xFF6B6259) // 深暖灰
    when (seg) {
        is FanSegment.BuiltIn -> {
            val iv = when (seg.mode) {
                BlindMode.VIDEO -> Icons.Outlined.Videocam
                BlindMode.PHOTO -> Icons.Outlined.PhotoCamera
                BlindMode.AUDIO -> Icons.Outlined.Mic
            }
            Icon(
                imageVector = iv,
                contentDescription = seg.mode.label,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }
        is FanSegment.Quick -> {
            val context = LocalContext.current
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
    modifier: Modifier = Modifier,
) {
    // 绘制与命中判定共用同一套动态角度
    val sweeps = fanSweeps(segments.count { it is FanSegment.Quick })
    val density = LocalDensity.current
    val selectedFill = Color(0xFFD8CBB6) // v0.68.24 方案A：选中段暖棕底
    val titleColor = AILauncherColors.Title
    val bodyColor = AILauncherColors.Body

    // v0.68.24 方案A：选中段径向伸长——每段外半径独立 spring；
    // v0.68.35：展开 0→120dp，选中该段 120→140dp（+20dp），滑出回弹；
    // 展开 stagger：每段延迟 25ms 依次绽开（录像→拍照→快捷→录音）
    val segShown = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(Unit) {
        segments.indices.forEach { i ->
            launch {
                delay(i * 25L)
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
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fanAlpha)
        ) {
            val rInner = FAN_R_INNER_DP.dp.toPx()
            val center = Offset(centerX, centerY)
            // v0.68.24 方案A：选中光晕已删除，改径向伸长
            // v0.68.35：毛玻璃质感——段底半透明白 + 外缘 1px 高光描边；选中段保留暖棕
            // 各段底（每段用自己的半径）
            segments.forEachIndexed { i, _ ->
                val (s, sw) = segmentAngles(i, sweeps, direction)
                val selected = i == selectedIndex
                val rOuter = segRadiiDp[i].value.dp.toPx()
                if (rOuter < 1f) return@forEachIndexed
                drawArc(
                    color = if (selected) selectedFill
                    else Color.White.copy(alpha = 0.55f),
                    startAngle = s + 1.5f,
                    sweepAngle = sw - 3f,
                    useCenter = true,
                    topLeft = Offset(centerX - rOuter, centerY - rOuter),
                    size = Size(rOuter * 2f, rOuter * 2f)
                )
                // 1px 高光描边（外缘弧线）
                drawArc(
                    color = Color.White.copy(alpha = 0.5f),
                    startAngle = s + 1.5f,
                    sweepAngle = sw - 3f,
                    useCenter = false,
                    topLeft = Offset(centerX - rOuter, centerY - rOuter),
                    size = Size(rOuter * 2f, rOuter * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            // v0.68.20：段间分隔线（v0.68.24：伸到相邻两段半径较大者）
            // v0.68.36：分隔线角度与 segmentAngles 共用同一套拍照锚定角度
            val divColor = Color(0xFF8A7B6B).copy(alpha = 0.55f)
            val divW = 2.dp.toPx()
            for (i in 0 until segments.size - 1) {
                val (bs, bsw) = segmentAngles(i + 1, sweeps, direction)
                val acc = if (direction == FanDirection.LEFT) bs + bsw else bs
                val rad = Math.toRadians(acc.toDouble())
                val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                val rMax = maxOf(
                    segRadiiDp[i].value,
                    segRadiiDp[i + 1].value
                ).dp.toPx()
                if (rMax < 1f) continue
                drawLine(
                    color = divColor,
                    start = center + dir * (rInner + 4f),
                    end = center + dir * (rMax - 4f),
                    strokeWidth = divW,
                    cap = StrokeCap.Round
                )
            }
            // v0.68.29 方案C：中心触摸涟漪（极淡圆环，表示手指落点）
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = rInner * 0.55f,
                center = center
            )
            drawCircle(
                color = Color(0xFF8A7B6B).copy(alpha = rippleA),
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
        val indScale by animateFloatAsState(
            targetValue = if (hasSel) 1f else 0.8f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
            label = "indScale"
        )
        val indAlpha by animateFloatAsState(
            targetValue = if (hasSel) 1f else 0f,
            animationSpec = tween(150),
            label = "indAlpha"
        )
        val indSeg = segments.getOrNull(selectedIndex)
        if (indAlpha > 0.01f && indSeg != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (indOffset.x - indSizePx / 2f).roundToInt(),
                            (indOffset.y - indSizePx / 2f).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = indScale
                        scaleY = indScale
                        alpha = (indAlpha * fanAlpha).coerceIn(0f, 1f)
                    }
                    .size(indSizeDp),
                contentAlignment = Alignment.Center
            ) {
                FanIndicatorIcon(seg = indSeg)
            }
        }
        // v0.68.20：图标 + 文字 overlay（Compose 排版）
        // v0.68.24 方案A：文字随段半径走（选中段伸长后文字外移）；选中字号放大
        // v0.68.35：纯文字（快捷段显示装备短标签，无图标）
        val rInnerPx = with(density) { FAN_R_INNER_DP.dp.toPx() }
        segments.forEachIndexed { i, seg ->
            val (s, sw) = segmentAngles(i, sweeps, direction)
            val segRPx = with(density) { segRadiiDp[i].value.dp.toPx() }
            val rMid = (segRPx + rInnerPx) / 2f
            val midRad = Math.toRadians((s + sw / 2).toDouble())
            val lx = centerX + cos(midRad).toFloat() * rMid
            val ly = centerY + sin(midRad).toFloat() * rMid
            val selected = i == selectedIndex
            val isPhoto = seg is FanSegment.BuiltIn && seg.mode == BlindMode.PHOTO
            val segLabel = when (seg) {
                is FanSegment.BuiltIn -> seg.mode.label
                is FanSegment.Quick -> seg.item.label.takeIf { it.isNotBlank() } ?: "快捷"
            }
            val pop by animateFloatAsState(
                targetValue = if (selected) 1.22f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "segPop$i"
            )
            key(i) {
                // v0.68.38：统一横排（等宽 36° 下横排全部放得下，竖排分支删除）；
                // maxLines=1 + Ellipsis，超长装备名截断
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
                            alpha = fanAlpha
                        }
                        .size(96.dp, 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segLabel,
                        // 选中放大——拍照 19sp→22sp，其他 16sp→19sp
                        fontSize = when {
                            selected && isPhoto -> 22.sp
                            selected -> 19.sp
                            isPhoto -> 19.sp
                            else -> 16.sp
                        },
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
