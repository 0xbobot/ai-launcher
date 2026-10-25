package com.bobot.ailauncher.ui.pet

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bobot.ailauncher.data.BatteryState
import com.bobot.ailauncher.data.PetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 混合宠物编排层：代码常驻生命（FloatingPet）+ 视频表情高光（ExoPlayer）。
 *
 * 状态联动（复用 PetStateMachine/mood 与事件）：
 * - 回到主页 → GREET（一次，3 秒）
 * - 任务完成（mood HAPPY）→ HAPPY（一次）；点宠物 → 立即进输入态，不播片
 * - AI 出结果等待（FETCH）→ THINKING（循环，mood 离开 FETCH 即停）
 * - 气泡播报（speech 非空）→ SPEAKING（循环，speech 清空即停，停后
 *   若仍在 FETCH 则回到 THINKING）
 * - 特殊惊讶事件（SURPRISED）→ SURPRISED（一次）
 * - idle / listening / glance 不播视频，走代码层。
 *
 * 切换：优先眨眼帧遮切（换片前强制眨眼一次，在闭合期切层）；若 400ms
 * 内未切成则 150ms 交叉淡化兜底。一次只播一条：循环片可被新片打断，
 * 一次片播完前的新请求排队（单槽，后来者覆盖）。
 *
 * 门禁：只在主页 RESUMED 时播；切后台或低电量即停回代码层。片源缺失时不切层、不崩溃、
 * 行为与无视频时完全一致。播放器音量恒为 0（高光片全部静音）。
 * 生成片是实色灰底，播放层做圆形裁剪（徽章式），不做抠像。
 */
@Composable
fun HybridPet(
    petWidth: Dp,
    onPetTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestOnPetTap by rememberUpdatedState(onPetTap)
    val blinkState = remember { PetBlinkState() }
    val lowPower by BatteryState.isLowPower.collectAsState()
    val mood by PetRepository.mood.collectAsState()
    val speech by PetRepository.speech.collectAsState()

    // —— 主页 RESUMED 门禁（与 DockPetPerch 同款 LifecycleEventObserver）——
    var resumed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // —— 播放器（常驻一个，换片只换 MediaItem；隐藏时预加载首帧）——
    val player = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var loadedClip by remember { mutableStateOf<HighlightClip?>(null) }
    // 同一条片二次播时 loadedClip 值不变，加载 effect 不重跑、播放器会
    // 停在 ENDED 不重播；nonce 强制重跑加载 effect（重 prepare 即从头播）。
    var loadNonce by remember { mutableStateOf(0) }
    var videoActive by remember { mutableStateOf(false) }
    var pendingClip by remember { mutableStateOf<HighlightClip?>(null) }
    var queuedClip by remember { mutableStateOf<HighlightClip?>(null) }
    var blinkCut by remember { mutableStateOf(false) }
    var firstFrameClip by remember { mutableStateOf<HighlightClip?>(null) }

    fun assetExists(clip: HighlightClip): Boolean = try {
        context.assets.open(clip.assetFile).close()
        true
    } catch (e: IOException) {
        false
    }

    fun stopVideo() {
        videoActive = false
        blinkCut = false
        player.pause()
    }

    fun play(clip: HighlightClip) {
        if (!resumed || lowPower) return
        if (!assetExists(clip)) return
        if (loadedClip == clip && videoActive) return // 已在播同一条
        if (videoActive && loadedClip?.loop == true) {
            // 循环片可被打断：先停当前，再走加载+换片路径。
            stopVideo()
        } else if (videoActive) {
            // 一次片播完前的新请求排队（单槽，后来者覆盖）。
            queuedClip = clip
            return
        }
        if (loadedClip == clip) loadNonce++
        loadedClip = clip
        pendingClip = clip
    }

    /** 取消一条片（排队中 / 待换片 / 已在播）：播片停回代码层，未播的直接卸掉。 */
    fun cancelClip(clip: HighlightClip) {
        if (queuedClip == clip) queuedClip = null
        if (pendingClip == clip) pendingClip = null
        if (loadedClip == clip) {
            if (videoActive) {
                stopVideo()
            } else {
                loadedClip = null
                player.stop()
            }
        }
    }

    /** 回到 mood 的循环片（目前只有 FETCH → THINKING）；一次片不续播。 */
    fun resumeLoopClip() {
        val clip = clipForMood(mood)
        if (clip?.loop == true) play(clip)
    }

    // —— 换片：加载 MediaItem（隐藏预加载，首帧就绪才允许切层）——
    LaunchedEffect(loadedClip, loadNonce) {
        firstFrameClip = null
        val clip = loadedClip ?: return@LaunchedEffect
        player.repeatMode = if (clip.loop) Player.REPEAT_MODE_ONE
            else Player.REPEAT_MODE_OFF
        player.setMediaItem(
            MediaItem.Builder().setUri("asset:///${clip.assetFile}").build()
        )
        player.prepare()
    }
    // —— 播放/暂停跟随可见性与前后台 ——
    LaunchedEffect(videoActive, resumed) {
        player.playWhenReady = videoActive && resumed
    }
    // —— 播放结束与错误：回代码层，触发排队片 ——
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameClip = loadedClip
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                stopVideo()
                val next = queuedClip
                queuedClip = null
                if (next != null) {
                    play(next)
                } else {
                    // 一次片播完：若仍在循环态（FETCH），回到 THINKING。
                    resumeLoopClip()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 播不动不崩溃：回代码层，行为与无视频时一致。
                stopVideo()
                pendingClip = null
                queuedClip = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // —— 眨眼遮切 vs 400ms 交叉淡化兜底 ——
    // 切层条件：有待换片 + 视频层未在播 + 该片首帧已就绪。
    LaunchedEffect(pendingClip, videoActive, firstFrameClip) {
        val clip = pendingClip ?: return@LaunchedEffect
        if (videoActive || firstFrameClip != clip) return@LaunchedEffect
        val timeout = launch {
            delay(400)
            blinkCut = false
            videoActive = true
            pendingClip = null
        }
        blinkState.requestBlink(this)
        // 等闭合期：已在眨眼中则立即命中；否则等强制眨眼到来。
        while (!blinkState.blinking) {
            delay(16)
        }
        timeout.cancel()
        blinkCut = true
        videoActive = true
        pendingClip = null
    }
    // —— 切后台 / 低电量：停回代码层 ——
    LaunchedEffect(resumed, lowPower) {
        if (!resumed || lowPower) {
            stopVideo()
            pendingClip = null
            queuedClip = null
            loadedClip = null
            player.stop()
        }
    }
    // —— 状态联动 ——
    LaunchedEffect(resumed) {
        if (resumed) play(HighlightClip.GREET)
    }
    LaunchedEffect(mood) {
        val clip = clipForMood(mood)
        if (clip != null) {
            play(clip)
        } else {
            // mood 离开循环态（FETCH）：THINKING 无论在排队、待换片还是
            // 在播，一律取消，不让它在 IDLE 下空转。
            cancelClip(HighlightClip.THINKING)
        }
    }
    LaunchedEffect(speech) {
        if (speech != null) {
            play(HighlightClip.SPEAKING)
        } else {
            cancelClip(HighlightClip.SPEAKING)
            resumeLoopClip()
        }
    }

    // —— 点按：立即进输入，不播片；HAPPY 只由任务完成（mood HAPPY）触发 ——
    fun hybridTap() {
        latestOnPetTap()
    }

    // —— 两层交叉：代码层 + 视频层（圆形徽章）——
    val videoAlpha by animateFloatAsState(
        targetValue = if (videoActive) 1f else 0f,
        animationSpec = tween(if (blinkCut) 0 else 150),
        label = "hybridVideoAlpha",
    )
    val codeAlpha by animateFloatAsState(
        targetValue = if (videoActive) 0f else 1f,
        animationSpec = tween(if (blinkCut) 0 else 150),
        label = "hybridCodeAlpha",
    )
    val density = LocalDensity.current
    // 视频圆心对准玻璃头球心（盒高约 62% 处）：盒中心 50% + 下移 12%。
    val videoOffsetYPx = with(density) { (petWidth * (1318f / 1043f)).toPx() * 0.12f }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer { alpha = codeAlpha },
            contentAlignment = Alignment.Center,
        ) {
            FloatingPet(
                petWidth = petWidth,
                blinkState = blinkState,
                active = resumed,
                onTap = { hybridTap() },
            )
        }
        // 视频层：仅播片/待换片时挂载。PlayerView 默认 SurfaceView 画在
        // 独立 surface 上，不认 Compose alpha、不认椭圆裁剪、pause 后仍留
        // surface——327 灰框就是这么来的。故：①改 TextureView 渲染（认
        // alpha、认裁剪）；②不播且无待换片时根本不 compose AndroidView，
        // 不留 surface；③背景显式透明，不加任何底色。
        // clickable 只在播片时启用：播片时接管点按（PlayerView 本身不消费），
        // 不播时整层不存在，点按自然透传给下层代码层（保留果冻回弹）。
        if (videoActive || pendingClip != null) {
            Box(
                modifier = Modifier
                    .size(petWidth)
                    .graphicsLayer {
                        alpha = videoAlpha
                        translationY = videoOffsetYPx
                    }
                    .clickable(
                        enabled = videoActive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { hybridTap() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setSurfaceType(PlayerView.SURFACE_TYPE_TEXTURE_VIEW)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            this.player = player
                            isClickable = false
                            isFocusable = false
                            clipToOutline = true
                            outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(petWidth),
                )
            }
        }
    }
}
