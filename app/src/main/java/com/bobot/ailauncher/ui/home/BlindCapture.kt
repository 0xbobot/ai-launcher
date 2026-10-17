package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.data.PetRepository
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
 * 2. 段从上到下：录像 / 拍照 / 录音（固定）+ 快捷装备段（动态，最多 2 段，
 *    取七仔装备排序前两位；总角度 128°~188°）；拍照 60° 居中加宽——最顺手
 * 3. 手指滑入某段 → 伸长高亮 + 震动 tick + 七仔报段名（七仔从段外缘探出）
 * 4. 松手 → 启动高亮段；无选择松手 → 拍照（零决策）；滑回 → 取消
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
 * 保留：三重反馈（震动/七仔语音/视觉高亮）、诊断浮层。
 */

/** 扇形固定段（从上到下）：录像 / 拍照 / 录音；快捷装备段动态追加 */
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
// v0.68.35：总角度动态 128°~188°（固定 128° + 快捷段每段 30°×N），左扇以正左方
// 180° 为中心，右扇以正右方 0° 为中心；拍照 60° 居中加宽，正中方向落在其间——最顺手
private const val FAN_R_OUTER_DP = 120f // v0.68.35：148→120，整体缩小更精致
// v0.68.24 方案A：选中段伸长到的外半径；v0.68.35：176→140（+20dp）
private const val FAN_R_OUTER_SELECTED_DP = 140f
private const val FAN_R_INNER_DP = 46f // 死区内圈不变
private const val FAN_EDGE_MARGIN_DP = 12f

private const val FAN_SWEEP_VIDEO = 40f
private const val FAN_SWEEP_PHOTO = 60f
private const val FAN_SWEEP_AUDIO = 28f
private const val FAN_SWEEP_QUICK = 30f
private const val MAX_QUICK_SEGS = 2

/** 各段扫过角度（动态）：固定 3 段 + 快捷段；绘制与命中判定共用同一套 */
private fun fanSweeps(quickCount: Int): FloatArray {
    val base = floatArrayOf(FAN_SWEEP_VIDEO, FAN_SWEEP_PHOTO, FAN_SWEEP_AUDIO)
    val n = quickCount.coerceIn(0, MAX_QUICK_SEGS)
    if (n == 0) return base
    return base + FloatArray(n) { FAN_SWEEP_QUICK }
}

/** 第 i 段的 (startAngle, sweepAngle)，Canvas 角度制；与命中判定共用 */
private fun segmentAngles(
    i: Int,
    sweeps: FloatArray,
    direction: FanDirection = FanDirection.LEFT,
): Pair<Float, Float> {
    val total = sweeps.sum()
    if (direction == FanDirection.LEFT) {
        var top = 180f + total / 2f // 最上方
        for (j in 0 until i) top -= sweeps[j]
        return Pair(top - sweeps[i], sweeps[i])
    }
    // v0.68.28 右扇镜像：自上而下累加，以正右方 0° 为中心
    var top = -total / 2f // 最上方
    for (j in 0 until i) top += sweeps[j]
    return Pair(top, sweeps[i])
}

/** 扇形命中：返回 sector index（0=最上方），-1 = 死区内或扇形外 */
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
    val total = sweeps.sum()
    if (direction == FanDirection.LEFT) {
        val lo = 180f - total / 2f
        if (deg < lo || deg > lo + total) return -1
        var top = lo + total
        for (i in sweeps.indices) {
            top -= sweeps[i]
            if (deg >= top) return i
        }
    } else {
        // v0.68.28 右扇：归一化到 -180°~180° 再判定
        val degR = if (deg > 180f) deg - 360f else deg
        val lo = -total / 2f
        if (degR < lo || degR > lo + total) return -1
        var top = lo
        for (i in sweeps.indices) {
            if (degR < top + sweeps[i]) return i
            top += sweeps[i]
        }
    }
    return sweeps.size - 1
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
    // v0.68.35：扇形垂直半高按实际总角度算（含选中伸长），触发时设置，用于圆心 Y 钳制
    private var fanHalfHPx = 0f
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
     * v0.68.35：本次手势的扇形段 = 固定3段（录像/拍照/录音）+ 七仔装备排序前两位。
     * 每次触发时读最新装备列表，增删复用七仔装备现有流程。
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
            FanSegment.BuiltIn(BlindMode.AUDIO),
        ) + quicks.map { FanSegment.Quick(it) }
    }

    /** 手势开始：无条件跟踪（组件已消费的手势到不了这里） */
    fun onStart() {
        tracking = true
        triggered = false
        modeIndex = -1
        maxLeftPx = 0f
        maxRightPx = 0f
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
                val totalSweep = sweeps.sum()
                // 扇形垂直半高 = sin(总角度/2) × 选中伸长半径，用于圆心 Y 钳制
                fanHalfHPx = (FAN_R_OUTER_SELECTED_DP *
                    sin(Math.toRadians(totalSweep / 2.0)).toFloat()) * density
                // 圆心钳制，保证扇形不被屏幕边缘裁掉（镜像：左扇防左边缘，右扇防右边缘）
                centerX = if (fanDir == FanDirection.LEFT) {
                    pos.x.coerceAtLeast(fanROuterPx + fanMarginPx)
                } else {
                    val maxX = (size.width - fanROuterPx - fanMarginPx)
                        .coerceAtLeast(fanMarginPx)
                    pos.x.coerceAtMost(maxX)
                }
                val minY = fanHalfHPx + fanMarginPx
                val maxY = (size.height - fanHalfHPx - fanMarginPx)
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
     */
    fun onEnd(): Boolean {
        if (!tracking) return false
        val wasTriggered = triggered
        val idx = modeIndex
        reset()
        if (wasTriggered) {
            if (idx >= 0) {
                commit(idx)
                diagState("commit(${segmentLabel(idx)})")
            } else {
                // v0.68.35：无选择松手（快滑）= 拍照，零决策
                val photoIdx = segments.indexOfFirst {
                    it is FanSegment.BuiltIn && it.mode == BlindMode.PHOTO
                }
                if (photoIdx >= 0) commit(photoIdx)
                diagState("commit(拍照-默认)")
            }
            return true
        }
        return false
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
    val totalSweep = sweeps.sum()
    val density = LocalDensity.current
    val selectedFill = Color(0xFFD8CBB6) // v0.68.24 方案A：选中段暖棕底
    val titleColor = AILauncherColors.Title
    val bodyColor = AILauncherColors.Body

    // v0.68.24 方案A：选中段径向伸长——每段外半径独立 spring；
    // v0.68.35：展开 0→120dp，选中该段 120→140dp（+20dp），滑出回弹；
    // 展开 stagger：每段延迟 25ms 依次绽开（录像→拍照→录音→快捷）
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
    val fanAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
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
            // v0.68.28：右扇镜像——分隔线角度自上而下累加
            // v0.68.35：动态总角度
            var acc = if (direction == FanDirection.LEFT) {
                180f + totalSweep / 2f
            } else {
                -totalSweep / 2f
            }
            val divColor = Color(0xFF8A7B6B).copy(alpha = 0.55f)
            val divW = 2.dp.toPx()
            for (i in 0 until segments.size - 1) {
                acc = if (direction == FanDirection.LEFT) {
                    acc - sweeps[i]
                } else {
                    acc + sweeps[i]
                }
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
        // v0.68.29 方案C「选中探出」：七仔从选中段外缘+36dp 处探出报模式；
        // 换段位移动画跳过去，滑出缩回；位置钳制防出屏；左右扇镜像共用
        // v0.68.35：分模式姿态（拍照jump/录像wave/录音hold/快捷main）；
        // 探出带 15° 旋转回正 + overshoot，收回"钻回去"（下沉+缩小+淡出）
        val hasSel = selectedIndex in segments.indices
        val probeSizeDp = 64.dp
        val probeSizePx = with(density) { probeSizeDp.toPx() }
        val probeGapPx = with(density) { 36.dp.toPx() }
        val probeEdgePx = with(density) { 40.dp.toPx() }
        val probeSinkPx = with(density) { 16.dp.toPx() }
        val probeResId = when (val seg = segments.getOrNull(selectedIndex)) {
            is FanSegment.BuiltIn -> when (seg.mode) {
                BlindMode.PHOTO -> com.bobot.ailauncher.R.drawable.meteor_jump
                BlindMode.VIDEO -> com.bobot.ailauncher.R.drawable.meteor_wave
                BlindMode.AUDIO -> com.bobot.ailauncher.R.drawable.meteor_hold
            }
            is FanSegment.Quick -> com.bobot.ailauncher.R.drawable.meteor_main
            null -> com.bobot.ailauncher.R.drawable.meteor_wave
        }
        val probeRaw = if (hasSel) {
            val (ps, psw) = segmentAngles(selectedIndex, sweeps, direction)
            val pRad = Math.toRadians((ps + psw / 2).toDouble())
            val pR = with(density) { segRadiiDp[selectedIndex].value.dp.toPx() } +
                probeGapPx
            Offset(
                centerX + cos(pRad).toFloat() * pR,
                centerY + sin(pRad).toFloat() * pR
            )
        } else null
        val probeClamped = probeRaw?.let {
            Offset(
                it.x.coerceIn(
                    probeEdgePx,
                    (screenSize.width - probeEdgePx).coerceAtLeast(probeEdgePx)
                ),
                it.y.coerceIn(
                    probeEdgePx,
                    (screenSize.height - probeEdgePx).coerceAtLeast(probeEdgePx)
                )
            )
        }
        // 无选中时锚点停留上次位置（缩回原地，下次从该处探出）
        var probeAnchor by remember { mutableStateOf(Offset(centerX, centerY)) }
        LaunchedEffect(probeClamped) {
            if (probeClamped != null) probeAnchor = probeClamped
        }
        val probeOffset by animateOffsetAsState(
            targetValue = probeClamped ?: probeAnchor,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
            label = "probeOffset"
        )
        val probeScale by animateFloatAsState(
            targetValue = if (hasSel) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f),
            label = "probeScale"
        )
        val probeAlpha by animateFloatAsState(
            targetValue = if (hasSel) 1f else 0f,
            animationSpec = tween(150),
            label = "probeAlpha"
        )
        // v0.68.35：探出时从 -15° 旋转回正（overshoot 弹性），收回时转回去（不可见）
        val probeRot by animateFloatAsState(
            targetValue = if (hasSel) 0f else -15f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
            label = "probeRot"
        )
        if (probeAlpha > 0.01f) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = probeResId),
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (probeOffset.x - probeSizePx * probeScale / 2f).roundToInt(),
                            (probeOffset.y - probeSizePx * probeScale / 2f).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        scaleX = probeScale
                        scaleY = probeScale
                        rotationZ = probeRot
                        // "钻回去"：收回时轻微下沉
                        translationY = (1f - probeScale) * probeSinkPx
                        alpha = (probeAlpha * fanAlpha).coerceIn(0f, 1f)
                    }
                    .size(probeSizeDp)
            )
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
                // v0.68.22：纯文字排版（去图标，段内空间小，字号拉大居中）
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (lx - with(density) { 48.dp.toPx() }).roundToInt(),
                                (ly - with(density) { 24.dp.toPx() }).roundToInt()
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
                        // v0.68.24 方案A：选中放大——拍照 19sp→22sp，其他 16sp→19sp
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
