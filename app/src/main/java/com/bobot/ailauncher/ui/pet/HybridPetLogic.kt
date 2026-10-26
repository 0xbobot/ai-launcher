package com.bobot.ailauncher.ui.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobot.ailauncher.data.PetMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 宠物混合路线（pet-hybrid-float）：代码常驻生命 + 视频表情高光。
 *
 * 片源：app/src/main/assets/clips/（压制脚本 scripts/transcode-pet-clips.sh，
 * 源片 10 秒按状态截 3 秒段、480px H.264 静音、单片 32–64KB）。
 * 生成片是实色灰底，播放层在 UI 侧做圆形裁剪（徽章式），不做抠像。
 */

/** 高光片：assetFile 为 assets 内相对路径，loop 为 true 的在状态持续期间循环。 */
enum class HighlightClip(val assetFile: String, val loop: Boolean) {
    GREET("clips/greet.mp4", false),
    HAPPY("clips/happy.mp4", false),
    THINKING("clips/thinking.mp4", true),
    SPEAKING("clips/speaking.mp4", true),
    SURPRISED("clips/surprised.mp4", false),
}

/**
 * mood → 高光片。只映射播视频的状态；IDLE / 聆听 / 瞥视 / 其他情绪
 * 返回 null，走代码层（FloatingPet），不播视频。
 */
fun clipForMood(mood: PetMood): HighlightClip? = when (mood) {
    PetMood.HAPPY -> HighlightClip.HAPPY
    PetMood.SURPRISED -> HighlightClip.SURPRISED
    PetMood.FETCH -> HighlightClip.THINKING
    else -> null
}

/**
 * 眨眼状态体：代码层与视频层共享的唯一眨眼权威。
 * 代码层跑 AliveLogic 的间隔循环；视频层换片前可强制请求一次，
 * 在眨眼闭合期切层，遮住换片跳变；400ms 内未切成则交叉淡化兜底。
 *
 * 眨眼新幅度（2026-10-25，随动作清单一并批）：总时长约 240ms——
 * 闭合约 85ms → 闭合保持约 70ms（closedPhase，组合层画粗弯曲线 +
 * 2dp 同步点头）→ 睁开约 85ms；旧 140ms 细线一闪而过作废。
 */
class PetBlinkState {
    var blinking by mutableStateOf(false)
        private set

    /** 闭合保持期：组合层在此期画粗 ∪ 形弯曲线（不用细线压缩条）并点头。 */
    var closedPhase by mutableStateOf(false)
        private set

    /**
     * 本次眨眼深度（活感第一批多样化）：1.0＝全闭，0.45＝半闭；
     * 组合层 blinkLid 取此值（半闭不触发闭合点头，见 closedPhase 门）。
     * 眨眼外恒为 0。
     */
    var blinkDepth by mutableStateOf(0f)
        private set
    private var blinkJob: Job? = null

    private suspend fun oneBlink(timing: BlinkTiming) {
        try {
            blinking = true
            blinkDepth = timing.depth
            delay(timing.closeMs) // 闭合
            // 半闭不进闭合保持期：不画粗弯曲线、不点头（只是眼睑半落）。
            if (timing.depth >= 1f) closedPhase = true
            delay(timing.holdMs) // 闭合保持
            closedPhase = false
            delay(timing.openMs) // 睁开
        } finally {
            // G1：取消（effect 重启等）时必复位。原来无 finally，
            // 取消会跳过末尾复位、blinking 永久为真，此后一切
            // requestBlink 被丢弃、眼睛永久闭合。
            blinking = false
            closedPhase = false
            blinkDepth = 0f
        }
    }

    /**
     * 强制眨眼一次（已在眨眼中则不重复触发）。
     * 活感第一批多样化（BlinkVariety）：正常眨、双眨（沿用 10%＋120ms 间隔）、
     * 半眨、慢半眨；双眨的第二眨与第一眨同参。
     */
    fun requestBlink(scope: CoroutineScope) {
        if (blinking) return
        blinkJob?.cancel()
        blinkJob = scope.launch {
            val kind = BlinkVariety.pick(Random)
            val timing = BlinkVariety.timing(kind)
            oneBlink(timing)
            if (kind == BlinkKind.DOUBLE) {
                delay(BlinkVariety.DOUBLE_GAP_MS)
                oneBlink(timing)
            }
        }
    }

    /**
     * 外部复位：取消在途眨眼。实际复位由 oneBlink 的 finally 执行
     * （取消投递后、协程恢复时清掉 blinking／closedPhase）；
     * 不从外部直接写状态，避免与在途协程的 finally 竞写。
     * 调用后需让出一次调度（runCurrent／下一帧），复位即生效。
     */
    fun reset() {
        blinkJob?.cancel()
    }
}
