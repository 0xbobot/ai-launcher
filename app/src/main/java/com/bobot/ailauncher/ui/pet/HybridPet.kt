package com.bobot.ailauncher.ui.pet

import android.graphics.Outline
import android.view.TextureView
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
 *
 * —— 应急（2026-10-25，333 发布后闪退）：视频层已彻底关闭，见下 VIDEO_LAYER_EMERGENCY_OFF。
 * Bob 报 333 后根本进不去应用、疑似宠物视频路径；应用内崩溃日志拿不到、根因未定论。
 * 在此期间：不 compose AndroidView、不建视频输出面、不调用 setVideoTextureView /
 * clearVideoTextureView；所有 mood 一律回落代码层（FloatingPet 常驻生命）。
 * 恢复视频层前必须先拿到崩溃堆栈并修掉根因，再由 Bob 拍板。
 */
/** 应急总开关：true = 视频层彻底关闭（333 后闪退应急，见文件头注释）。 */
private const val VIDEO_LAYER_EMERGENCY_OFF = true

/**
 * 单一宠物引擎契约（宠物交互重构 P0-3，2026-10-26）：Dock 上沿与意图
 * 卡片上沿两处 perch 共用本组件与 FloatingPet 活感引擎——同一玻璃头
 * 资产、同一眨眼状态体（PetBlinkState，约 240ms）、同一呼吸/注视/
 * 手动动作管线，不另建第二套。旧毛绒路径 PetView 已随本批退役
 * （零调用方；其约 190ms 眨眼、4s 呼吸、毛绒资产渲染自此不再是
 * 在场宠物的参数来源）。perch 切换的位移本身由 IntentEntry 侧的
 * 附加 offset 承担，本组件不感知 perch、不持有位移状态。
 */
@Composable
fun HybridPet(
    petWidth: Dp,
    onPetTap: () -> Unit,
    // 2026-10-26 入口子项转发胶水（仅此一处超出原定文件范围，见回报说明）：
    // FloatingPet 的长按回调必须经本层转发到 PullUpDock，否则长按清单
    // 无法接线；本层不含任何长按逻辑，只透传。
    onPetLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestOnPetTap by rememberUpdatedState(onPetTap)
    val latestOnPetLongPress by rememberUpdatedState(onPetLongPress)
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
        // 应急：视频层关闭，全部回落代码层；videoActive 永不为真，代码层永不隐藏。
        if (VIDEO_LAYER_EMERGENCY_OFF) return
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

    fun hybridLongPress() {
        latestOnPetLongPress()
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
                onLongPress = { hybridLongPress() },
            )
        }
        // 视频层：仅播片/待换片时挂载。PlayerView 默认 SurfaceView 画在
        // 独立 surface 上，不认 Compose alpha、不认椭圆裁剪、pause 后仍留
        // surface——327 灰框就是这么来的。故：①不用 PlayerView，直接用
        // TextureView + setVideoTextureView（Media3 1.3.1 的 PlayerView 没
        // 有 setSurfaceType 的公开 setter，表面类型只能走 XML 属性）；
        // ②不播且无待换片时根本不 compose AndroidView，不留 surface；
        // ③背景显式透明，不加任何底色。
        // clickable 只在播片时启用：播片时接管点按（PlayerView 本身不消费），
        // 不播时整层不存在，点按自然透传给下层代码层（保留果冻回弹）。
        // 应急：视频层关闭时纵使 videoActive/pendingClip 被置位也不挂载 AndroidView，
        // 绝不建视频输出面、不调用 setVideoTextureView/clearVideoTextureView。
        if (!VIDEO_LAYER_EMERGENCY_OFF && (videoActive || pendingClip != null)) {
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
                        TextureView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isClickable = false
                            isFocusable = false
                            clipToOutline = true
                            outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }
                            player.setVideoTextureView(this)
                        }
                    },
                    onRelease = { textureView ->
                        player.clearVideoTextureView(textureView)
                    },
                    modifier = Modifier.size(petWidth),
                )
            }
        }
    }
}
