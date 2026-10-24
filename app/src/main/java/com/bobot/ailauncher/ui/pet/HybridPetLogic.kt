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
 */
class PetBlinkState {
    var blinking by mutableStateOf(false)
        private set
    private var blinkJob: Job? = null

    /** 强制眨眼一次（已在眨眼中则不重复触发）。 */
    fun requestBlink(scope: CoroutineScope) {
        if (blinking) return
        blinkJob?.cancel()
        blinkJob = scope.launch {
            blinking = true
            delay(140)
            blinking = false
            if (BlinkPolicy.isDoubleBlink(Random)) {
                delay(120)
                blinking = true
                delay(140)
                blinking = false
            }
        }
    }
}
