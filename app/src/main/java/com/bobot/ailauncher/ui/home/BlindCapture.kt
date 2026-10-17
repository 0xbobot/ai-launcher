package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
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
import com.bobot.ailauncher.ui.pet.launchQuickAction
import com.bobot.ailauncher.ui.theme.AILauncherColors
import org.json.JSONObject
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
 * 1. 左滑触发 → 以触发点为圆心，左侧展开 150° 扇形菜单（4 段）
 * 2. 4 段从上到下：录像 / 拍照 / 录音 / 自定义（拍照 60° 居中加宽）
 * 3. 手指滑入某段 → 高亮 + 震动 tick + 七仔报模式名
 * 4. 松手 → 启动高亮模式；无选择松手 → 拍照（零决策）；右滑回 → 取消
 *
 * v0.68.20：视觉重构——展开弹性动画、段间分隔线、选中光晕+放大、
 * 图标+文字排版；拍照为最默认操作。
 *
 * 保留：三重反馈（震动/七仔语音/视觉高亮）、自定义☆槽位、诊断浮层。
 */

/** 扇形 4 段（从上到下）：录像 / 拍照 / 录音 / 自定义 */
enum class BlindMode(val label: String) {
    VIDEO("录像"),
    PHOTO("拍照"),
    AUDIO("录音"),
    CUSTOM("自定义"),
}

// 扇形几何（Canvas 角度制：0°=正右方，顺时针为正，y 向下）
// 左侧 150°：105°（下-左）~ 255°（上-左），index 0 在最上方
private const val FAN_START_DEG = 105f
private const val FAN_SWEEP_DEG = 150f
private const val FAN_R_OUTER_DP = 148f
private const val FAN_R_INNER_DP = 46f
private const val FAN_EDGE_MARGIN_DP = 12f

/**
 * v0.68.20：4 段不等宽（从上到下：录像/拍照/录音/自定义），总和必须 = 150°。
 * 拍照 60° 居中加宽（215°~155°，中心 185°，正左方 180° 落在其中）——最顺手。
 */
private val FAN_SWEEPS = floatArrayOf(40f, 60f, 28f, 22f)

/** 第 i 段的 (startAngle, sweepAngle)，Canvas 角度制；与命中判定共用 */
private fun segmentAngles(i: Int): Pair<Float, Float> {
    var top = FAN_START_DEG + FAN_SWEEP_DEG // 255°，最上方
    for (j in 0 until i) top -= FAN_SWEEPS[j]
    return Pair(top - FAN_SWEEPS[i], FAN_SWEEPS[i])
}

/** 扇形命中：返回 sector index（0=最上方），-1 = 死区内或扇形外 */
private fun sectorForIndex(dx: Float, dy: Float, deadZonePx: Float): Int {
    if (sqrt(dx * dx + dy * dy) < deadZonePx) return -1
    var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (deg < 0f) deg += 360f
    if (deg < FAN_START_DEG || deg > FAN_START_DEG + FAN_SWEEP_DEG) return -1
    var top = FAN_START_DEG + FAN_SWEEP_DEG
    for (i in FAN_SWEEPS.indices) {
        top -= FAN_SWEEPS[i]
        if (deg >= top) return i
    }
    return FAN_SWEEPS.size - 1
}

/** 盲操 UI 状态：隐藏 / 扇形选择中（modeIndex=-1 表示无高亮） */
sealed interface BlindUiState {
    data object Hidden : BlindUiState
    data class Selecting(
        val centerX: Float,
        val centerY: Float,
        val modeIndex: Int,
    ) : BlindUiState
}

/** 自定义☆动作存取（复用 QuickActionItem 数据模型） */
object BlindCapturePrefs {
    private const val PREFS = "blind_capture_prefs"
    private const val KEY_CUSTOM = "custom_json"

    fun load(context: Context): QuickActionItem? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM, null) ?: return null
        return try {
            val o = JSONObject(raw)
            QuickActionItem(
                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                packageName = o.getString("packageName"),
                shortcutId = o.optString("shortcutId", null)?.takeIf { it.isNotEmpty() },
                label = o.optString("label", ""),
                iconUri = o.optString("iconUri", null)?.takeIf { it.isNotEmpty() }
            )
        } catch (_: Exception) { null }
    }

    fun save(context: Context, item: QuickActionItem) {
        val o = JSONObject()
            .put("id", item.id)
            .put("packageName", item.packageName)
            .put("label", item.label)
        item.shortcutId?.let { o.put("shortcutId", it) }
        item.iconUri?.let { o.put("iconUri", it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM, o.toString()).apply()
    }
}

/**
 * App 内极简录音（MediaRecorder）。
 * v0.68.22：改存 MediaStore 公共目录 Music/Recordings/，用户在文件管理器/音乐 App 可找到。
 * （Android 10+ 用 RELATIVE_PATH，无需 WRITE_EXTERNAL_STORAGE 权限）
 */
object BlindAudioRecorder {
    var isRecording: Boolean = false
        private set
    private var recorder: MediaRecorder? = null
    private var outPfd: android.os.ParcelFileDescriptor? = null
    private var outUri: android.net.Uri? = null

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

    /** 停止并返回 MediaStore Uri（可空） */
    fun stop(context: Context): android.net.Uri? {
        val uri = outUri
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
    private val openCustomConfig: () -> Unit,
    private val triggerPx: Float,
    private val onDiag: (String) -> Unit = {},
    // v0.68.22：录音结束回调（HomeScreen 弹出"转文字"卡片）
    private val onRecordingDone: () -> Unit = {},
) {
    companion object {
        private const val TAG = "BlindCapture"
    }

    private val density = context.resources.displayMetrics.density
    // 死区半径 = 内圈半径（与视觉一致）：圆心附近无扇区
    private val deadZonePx = FAN_R_INNER_DP * density
    private val cancelBackPx = 24f * density
    private val fanROuterPx = FAN_R_OUTER_DP * density
    // 扇形垂直半高 = sin(75°) * R，用于圆心 Y 钳制
    private val fanHalfHPx =
        (FAN_R_OUTER_DP * sin(Math.toRadians(75.0)).toFloat()) * density
    private val fanMarginPx = FAN_EDGE_MARGIN_DP * density

    private var tracking = false
    private var triggered = false
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

    /** 手势开始：无条件跟踪（组件已消费的手势到不了这里） */
    fun onStart() {
        tracking = true
        triggered = false
        modeIndex = -1
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
            // 触发：左滑过阈值且横向主导 → 直接展开扇形（无停留、无边缘要求）
            if (totalX <= -triggerPx && abs(totalX) > abs(totalY) * 1.5f) {
                triggered = true
                // 圆心内移，保证扇形不被屏幕边缘裁掉
                centerX = pos.x.coerceAtLeast(fanROuterPx + fanMarginPx)
                val minY = fanHalfHPx + fanMarginPx
                val maxY = (size.height - fanHalfHPx - fanMarginPx)
                    .coerceAtLeast(minY)
                centerY = pos.y.coerceIn(minY, maxY)
                modeIndex = -1
                setUi(BlindUiState.Selecting(centerX, centerY, -1))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                PetRepository.say("拍点什么？")
                diagState("fan展开")
                Log.d(TAG, "triggered center=($centerX, $centerY)")
            }
            return
        }
        // 右滑回 → 取消
        if (pos.x > centerX + cancelBackPx) {
            diagState("idle(右滑取消)")
            reset()
            return
        }
        val idx = sectorForIndex(pos.x - centerX, pos.y - centerY, deadZonePx)
        if (idx != modeIndex) {
            modeIndex = idx
            setUi(BlindUiState.Selecting(centerX, centerY, idx))
            if (idx >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PetRepository.say(modeLabel(idx))
                diagState("sector=${modeLabel(idx)}")
            } else {
                diagText("sector=无")
            }
        }
    }

    /** 模式显示名：自定义段读用户配置 */
    private fun modeLabel(idx: Int): String {
        val mode = BlindMode.values()[idx]
        return if (mode == BlindMode.CUSTOM) {
            BlindCapturePrefs.load(context)?.label?.takeIf { it.isNotBlank() }
                ?: mode.label
        } else mode.label
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
                diagState("commit(${modeLabel(idx)})")
            } else {
                commit(BlindMode.PHOTO.ordinal)
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
        Log.d(TAG, "commit mode=${BlindMode.values()[idx]}")
        when (BlindMode.values()[idx]) {
            BlindMode.PHOTO -> launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
            BlindMode.VIDEO -> launchCamera(MediaStore.ACTION_VIDEO_CAPTURE)
            BlindMode.AUDIO -> toggleAudio()
            BlindMode.CUSTOM -> openCustom()
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
            BlindAudioRecorder.stop(context)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // v0.68.22：告知公共目录位置，并弹出"转文字"卡片
            PetRepository.say("录音已保存到 Music/Recordings")
            onRecordingDone()
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

    private fun openCustom() {
        val item = BlindCapturePrefs.load(context)
        if (item == null) {
            PetRepository.say("先去设置自定义动作")
            openCustomConfig()
        } else {
            launchQuickAction(context, item)
        }
    }
}

/** v0.68.22：段内纯文字，已无图标（modeIcon 删除） */

/**
 * 扇形选择器 v0.68.20 视觉重构：
 * - 展开弹性动画（半径 spring + 淡入）
 * - 段间分隔线、选中光晕 + 图标放大
 * - 图标 + 文字排版（拍照段更大更突出）
 * - 自定义段显示用户配置的名称
 */
@Composable
fun BlindCaptureFan(
    centerX: Float,
    centerY: Float,
    selectedIndex: Int,
    customLabel: String = "自定义",
    modifier: Modifier = Modifier,
) {
    val modes = BlindMode.values()
    val density = LocalDensity.current
    val accent = AILauncherColors.Accent
    val accentSoft = AILauncherColors.AccentSoft
    val titleColor = AILauncherColors.Title
    val bodyColor = AILauncherColors.Body

    // v0.68.20：展开弹性动画——半径从 0 弹到全尺寸 + 淡入
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { expanded = true }
    val rOuterDp by animateFloatAsState(
        targetValue = if (expanded) FAN_R_OUTER_DP else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 280f),
        label = "fanExpand"
    )
    val fanAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(160),
        label = "fanAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fanAlpha)
        ) {
            val rOuter = rOuterDp.dp.toPx()
            if (rOuter < 1f) return@Canvas
            val rInner = FAN_R_INNER_DP.dp.toPx()
            val center = Offset(centerX, centerY)
            // v0.68.20：选中光晕——底层大一圈的柔光
            if (selectedIndex in modes.indices) {
                val (s, sw) = segmentAngles(selectedIndex)
                drawArc(
                    color = accent.copy(alpha = 0.30f),
                    startAngle = s - 2f,
                    sweepAngle = sw + 4f,
                    useCenter = true,
                    topLeft = Offset(centerX - rOuter - 10f, centerY - rOuter - 10f),
                    size = Size((rOuter + 10f) * 2f, (rOuter + 10f) * 2f)
                )
            }
            // 各段底
            modes.forEachIndexed { i, mode ->
                val (s, sw) = segmentAngles(i)
                val selected = i == selectedIndex
                drawArc(
                    color = when {
                        selected -> accentSoft
                        mode == BlindMode.PHOTO -> Color.White.copy(alpha = 0.92f)
                        else -> Color.White.copy(alpha = 0.78f)
                    },
                    startAngle = s + 1.5f,
                    sweepAngle = sw - 3f,
                    useCenter = true,
                    topLeft = Offset(centerX - rOuter, centerY - rOuter),
                    size = Size(rOuter * 2f, rOuter * 2f)
                )
            }
            // v0.68.20：段间分隔线
            var acc = FAN_START_DEG + FAN_SWEEP_DEG
            val divColor = Color(0xFF8A7B6B).copy(alpha = 0.55f)
            val divW = 2.dp.toPx()
            for (i in 0 until modes.size - 1) {
                acc -= FAN_SWEEPS[i]
                val rad = Math.toRadians(acc.toDouble())
                val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                drawLine(
                    color = divColor,
                    start = center + dir * (rInner + 4f),
                    end = center + dir * (rOuter - 4f),
                    strokeWidth = divW,
                    cap = StrokeCap.Round
                )
            }
            // v0.68.22：死区内圈不再画白圆，改由下方七仔 Image 覆盖
        }
        // v0.68.22：中心七仔 mini——选中某段时跳一下给反应
        val qizaiRes = if (selectedIndex in modes.indices)
            com.bobot.ailauncher.R.drawable.meteor_jump
        else
            com.bobot.ailauncher.R.drawable.meteor_main
        val qizaiPop by animateFloatAsState(
            targetValue = if (selectedIndex in modes.indices) 1.18f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
            label = "qizaiPop"
        )
        val rInnerPx2 = with(density) { FAN_R_INNER_DP.dp.toPx() }
        Image(
            painter = androidx.compose.ui.res.painterResource(id = qizaiRes),
            contentDescription = null,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (centerX - rInnerPx2 * qizaiPop).roundToInt(),
                        (centerY - rInnerPx2 * qizaiPop).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = qizaiPop
                    scaleY = qizaiPop
                    alpha = fanAlpha
                }
                .size((FAN_R_INNER_DP * 2).dp)
        )
        // v0.68.20：图标 + 文字 overlay（Compose 排版）
        val rOuterPx = with(density) { rOuterDp.dp.toPx() }
        val rInnerPx = with(density) { FAN_R_INNER_DP.dp.toPx() }
        val rMid = (rOuterPx + rInnerPx) / 2f
        modes.forEachIndexed { i, mode ->
            val (s, sw) = segmentAngles(i)
            val midRad = Math.toRadians((s + sw / 2).toDouble())
            val lx = centerX + cos(midRad).toFloat() * rMid
            val ly = centerY + sin(midRad).toFloat() * rMid
            val selected = i == selectedIndex
            val isPhoto = mode == BlindMode.PHOTO
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
                        text = if (mode == BlindMode.CUSTOM) customLabel else mode.label,
                        fontSize = if (isPhoto) 19.sp else 16.sp,
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
