package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.pet.QuickActionItem
import com.bobot.ailauncher.ui.pet.launchQuickAction
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * v0.68.0：盲操——不看屏幕快速捕捉瞬间。
 *
 * L 形手势（右边缘）：
 * 1. 触发：右边缘 24dp 内起滑，向左滑过阈值，停留 120ms → 激活
 *    （震动 + 七仔说"拍点什么？"）
 * 2. 选择：按住不放上下滑，模式轨高亮跟随
 *    （每过一格震动 + 七仔报模式名）
 * 3. 确认：松手 → 启动选中模式
 * 4. 取消：滑回右边缘 / 3 秒不动
 *
 * 防误触三层：边缘起始区 + 120ms 停留（过滤 flick）+ 导航后 800ms 冷却。
 * 与右边缘长按字母快找天然区分：长按不动走 picker，一动即左滑走盲操。
 */

/** 模式轨（自上而下）：录像 / 拍照（默认）/ 录音 / 自定义 */
enum class BlindMode(val label: String) {
    VIDEO("录像"),
    PHOTO("拍照"),
    AUDIO("录音"),
    CUSTOM("自定义"),
}

/** 盲操 UI 状态：隐藏 / 选择中 */
sealed interface BlindUiState {
    data object Hidden : BlindUiState
    data class Selecting(val modeIndex: Int, val fingerY: Float) : BlindUiState
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

/** App 内极简录音（MediaRecorder） */
object BlindAudioRecorder {
    var isRecording: Boolean = false
        private set
    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    fun start(context: Context): Boolean {
        if (isRecording) return true
        return try {
            val dir = File(context.filesDir, "blind_audio").apply { mkdirs() }
            outFile = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(outFile!!.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            isRecording = true
            true
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            isRecording = false
            false
        }
    }

    fun stop(): File? {
        val f = outFile
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        outFile = null
        isRecording = false
        return f
    }
}

/**
 * 盲操手势状态机。由 HomeScreen 的 pointerInput 转发事件驱动（UI 线程）。
 */
class BlindCaptureController(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val scope: CoroutineScope,
    private val setUi: (BlindUiState) -> Unit,
    private val requestAudioPermission: () -> Unit,
    private val openCustomConfig: () -> Unit,
    private val edgePx: Float,
    private val triggerPx: Float,
) {
    companion object {
        private const val DWELL_MS = 120L          // 停留激活
        private const val IDLE_CANCEL_MS = 3000L  // 3 秒不动取消
        private const val COOLDOWN_MS = 800L       // 导航后冷却
        private var lastNavMs: Long = 0L

        /** 页面导航时调用（D3 打开/盲操提交等），800ms 内忽略盲操触发 */
        fun noteNavigation() {
            lastNavMs = SystemClock.uptimeMillis()
        }

        private fun cooldownOk(): Boolean =
            SystemClock.uptimeMillis() - lastNavMs > COOLDOWN_MS
    }

    private var tracking = false
    private var armed = false
    private var selecting = false
    private var modeIndex = 1 // 默认拍照
    private var dwellJob: Job? = null
    private var idleJob: Job? = null

    private fun cancelJobs() {
        dwellJob?.cancel(); dwellJob = null
        idleJob?.cancel(); idleJob = null
    }

    private fun reset() {
        cancelJobs()
        tracking = false
        armed = false
        selecting = false
        setUi(BlindUiState.Hidden)
    }

    /** 手势开始：右边缘 24dp 内起滑才跟踪 */
    fun onStart(x: Float, widthPx: Float) {
        cancelJobs()
        tracking = widthPx > 0f && x >= widthPx - edgePx && cooldownOk()
        armed = false
        selecting = false
    }

    fun onMove(
        change: PointerInputChange,
        drag: Offset,
        totalX: Float,
        totalY: Float,
        size: IntSize,
        verticalThresholdPx: Float,
    ) {
        if (!tracking) return
        val now = SystemClock.uptimeMillis()
        when {
            !armed && !selecting -> {
                if (totalX <= -triggerPx && abs(totalX) > abs(totalY) * 1.5f) {
                    // 左滑到位 → 等待 120ms 停留
                    armed = true
                    val y0 = change.position.y
                    dwellJob = scope.launch {
                        delay(DWELL_MS)
                        if (armed && !selecting) activate(y0)
                    }
                } else if (abs(totalY) > verticalThresholdPx && abs(totalY) > abs(totalX)) {
                    // 转为纵向手势，盲操退出
                    tracking = false
                }
            }
            armed && !selecting -> {
                // 停留中：大幅移动则取消（回到跟踪）；dwellJob 负责激活
                if (abs(drag.x) + abs(drag.y) > edgePx) {
                    armed = false
                    cancelJobs()
                    tracking = false
                }
            }
            selecting -> {
                // 取消：滑回右边缘
                if (change.position.x >= size.width - edgePx / 2f) {
                    reset()
                    return
                }
                val idx = indexForY(change.position.y, size.height.toFloat())
                setUi(BlindUiState.Selecting(idx, change.position.y))
                if (idx != modeIndex) {
                    modeIndex = idx
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PetRepository.say(BlindMode.values()[idx].label)
                    resetIdle()
                }
            }
        }
    }

    /**
     * 手指抬起。返回 true 表示手势被盲操消费，调用方跳过原有纵向逻辑。
     */
    fun onEnd(): Boolean {
        cancelJobs()
        return when {
            selecting -> {
                commit()
                reset()
                true
            }
            armed || tracking -> {
                // 停留未完成 / 位移不够：吞掉，不触发纵向
                reset()
                true
            }
            else -> false
        }
    }

    fun onCancel() {
        reset()
    }

    /** 录音权限结果回调 */
    fun onAudioPermissionResult(granted: Boolean) {
        if (granted) startAudio()
        else PetRepository.say("没有麦克风权限，录不了音")
    }

    private fun activate(fingerY: Float) {
        selecting = true
        modeIndex = 1 // 默认拍照
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        PetRepository.say("拍点什么？")
        setUi(BlindUiState.Selecting(modeIndex, fingerY))
        resetIdle()
    }

    private fun resetIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_CANCEL_MS)
            if (selecting) reset() // 3 秒不动 → 静默取消
        }
    }

    private fun indexForY(y: Float, h: Float): Int {
        if (h <= 0f) return 1
        return ((y / h) * BlindMode.values().size).toInt()
            .coerceIn(BlindMode.values().indices)
    }

    private fun commit() {
        noteNavigation() // 提交后冷却，防连续误触
        when (BlindMode.values()[modeIndex]) {
            BlindMode.PHOTO -> launchCamera(MediaStore.ACTION_IMAGE_CAPTURE)
            BlindMode.VIDEO -> launchCamera(MediaStore.ACTION_VIDEO_CAPTURE)
            BlindMode.AUDIO -> toggleAudio()
            BlindMode.CUSTOM -> openCustom()
        }
    }

    private fun launchCamera(action: String) {
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            BlindAudioRecorder.stop()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            PetRepository.say("录音已保存")
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

/** 右侧纵向模式轨：当前项放大+高亮 */
@Composable
fun BlindCaptureRail(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val modes = BlindMode.values()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        modes.forEachIndexed { i, mode ->
            val selected = i == selectedIndex
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.3f else 1f,
                label = "blindRailScale"
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) AILauncherColors.AccentSoft
                        else Color.White.copy(alpha = 0.72f)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) AILauncherColors.Title else AILauncherColors.Body
                )
            }
        }
    }
}
