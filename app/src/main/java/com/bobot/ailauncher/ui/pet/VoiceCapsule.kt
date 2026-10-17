@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.bobot.ailauncher.ui.pet

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.home.QuickMemoStore
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v0.68.24：语音胶囊。
 *
 * 录音结束 → 音频变成一颗"语音胶囊"，浮在七仔周围（复用装备挂件的环绕语言）。
 * 点胶囊：播放 / 转文字 / 删除（v0.68.39 起分享已删）；长按进入多选 → 合并多段；
 * 按天分组列表（今日/昨日/M月d日），点组展开。
 */

/** ============ 数据层 ============ */
object VoiceCapsuleStore {
    private const val PREFS = "voice_capsule_prefs"
    private const val KEY = "capsules_json"
    const val MAX_KEEP = 50

    data class Capsule(
        val id: String,
        val uri: String,
        val durationSec: Int,
        val time: Long,
        val transcript: String? = null,
    )

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick
    private fun bump() { _tick.value += 1 }

    fun all(context: Context): List<Capsule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Capsule(
                    id = o.getString("id"),
                    uri = o.getString("uri"),
                    durationSec = o.optInt("durationSec", 0),
                    time = o.getLong("time"),
                    transcript = o.optString("transcript", null)?.takeIf { it.isNotEmpty() }
                )
            }.sortedByDescending { it.time }
        } catch (_: Exception) { emptyList() }
    }

    private fun persist(context: Context, list: List<Capsule>) {
        val arr = JSONArray()
        list.take(MAX_KEEP).forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("uri", c.uri)
                    .put("durationSec", c.durationSec)
                    .put("time", c.time)
                    .put("transcript", c.transcript ?: "")
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        bump()
    }

    fun add(
        context: Context,
        uri: String,
        durationSec: Int,
        time: Long = System.currentTimeMillis(),
        transcript: String? = null,
    ): Capsule {
        val c = Capsule(UUID.randomUUID().toString(), uri, durationSec, time, transcript)
        val list = (listOf(c) + all(context)).take(MAX_KEEP)
        persist(context, list)
        return c
    }

    /** 删除胶囊（含 MediaStore 音频文件） */
    fun remove(context: Context, id: String) = removeIds(context, listOf(id))

    fun removeIds(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        val doomed = all(context).filter { it.id in idSet }
        doomed.forEach { c ->
            try { context.contentResolver.delete(Uri.parse(c.uri), null, null) }
            catch (_: Exception) { }
        }
        persist(context, all(context).filter { it.id !in idSet })
    }

    fun setTranscript(context: Context, id: String, text: String) {
        persist(
            context,
            all(context).map { if (it.id == id) it.copy(transcript = text) else it }
        )
    }

    /** 今日 / 昨日 / M月d日 */
    fun dayLabel(time: Long): String {
        val cal = Calendar.getInstance()
        val todayStart = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000
        return when {
            time >= todayStart -> "今日"
            time >= todayStart - dayMs -> "昨日"
            else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(time))
        }
    }

    fun timeLabel(time: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
}

fun fmtDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/** ============ 音频拼接（MediaMuxer 样本拷贝） ============ */
object CapsuleAudio {
    private const val TAG = "CapsuleAudio"
    // AAC 一帧 1024 采样 @44.1kHz ≈ 23220µs，文件衔接时补一帧间隔
    private const val AAC_FRAME_US = 23220L

    /** 合并多个 m4a → (MediaStore Uri, 总时长秒)，失败返回 null */
    fun merge(context: Context, srcUris: List<Uri>): Pair<Uri, Int>? {
        if (srcUris.size < 2) return null
        val tmp = File(context.cacheDir, "capsule_merge_${System.currentTimeMillis()}.m4a")
        try {
            val totalMs = concatToFile(context, srcUris, tmp) ?: return null
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "merged_${System.currentTimeMillis()}.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tmp).use { it.copyTo(out) }
                } ?: run {
                    context.contentResolver.delete(uri, null, null)
                    return null
                }
            } catch (_: Exception) {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            return Pair(uri, (totalMs / 1000).toInt().coerceAtLeast(1))
        } finally {
            tmp.delete()
        }
    }

    /** 返回总时长毫秒，失败返回 null */
    private fun concatToFile(context: Context, uris: List<Uri>, out: File): Long? {
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var started = false
        var ptsOffsetUs = 0L
        var totalMs = 0L
        return try {
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            for (uri in uris) {
                val ex = MediaExtractor()
                try {
                    try {
                        ex.setDataSource(context, uri, null)
                    } catch (_: Exception) { continue }
                    var track = -1
                    var format: MediaFormat? = null
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            track = i; format = f; break
                        }
                    }
                    if (track < 0 || format == null) continue
                    if (trackIndex < 0) {
                        trackIndex = muxer.addTrack(format)
                        muxer.start()
                        started = true
                    }
                    ex.selectTrack(track)
                    val fileStartOffset = ptsOffsetUs
                    var fileEndPts = -1L
                    while (true) {
                        val sz = ex.readSampleData(buffer, 0)
                        if (sz < 0) break
                        val st = ex.sampleTime
                        info.set(0, sz, st + ptsOffsetUs, ex.sampleFlags)
                        muxer.writeSampleData(trackIndex, buffer, info)
                        fileEndPts = st + ptsOffsetUs
                        ex.advance()
                    }
                    if (fileEndPts >= 0) {
                        totalMs += (fileEndPts - fileStartOffset) / 1000 + AAC_FRAME_US / 1000
                        ptsOffsetUs = fileEndPts + AAC_FRAME_US
                    }
                } finally {
                    ex.release()
                }
            }
            if (!started || totalMs <= 0) null else totalMs
        } catch (e: Exception) {
            Log.w(TAG, "concat failed", e)
            null
        } finally {
            try {
                if (started) muxer?.stop()
            } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
        }
    }
}

/** ============ 极简播放器 ============ */
object CapsulePlayer {
    private var player: MediaPlayer? = null
    private var playingUri: String? = null

    fun isPlaying(uri: String): Boolean =
        playingUri == uri && player?.isPlaying == true

    /** 切换播放；返回 true=开始播放 */
    fun toggle(context: Context, uri: String): Boolean {
        if (isPlaying(uri)) {
            stop()
            return false
        }
        stop()
        return try {
            val p = MediaPlayer()
            p.setDataSource(context, Uri.parse(uri))
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            playingUri = uri
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        playingUri = null
    }
}

/** ============ UI ============ */

private data class PillSlot(val x: Dp, val y: Dp, val rot: Float)

// 七仔左侧三个槽位（220dp 装备环语言：左腰/左上附近，平时装备面板关闭时不冲突）
private val PillSlots = listOf(
    PillSlot((-88).dp, (-56).dp, -6f),
    PillSlot((-97).dp, (-10).dp, 5f),
    PillSlot((-88).dp, 36.dp, -4f),
)
private const val FLOAT_MAX = 3

@Composable
fun VoiceCapsules(
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by VoiceCapsuleStore.tick.collectAsState()
    val capsules = remember(tick) { VoiceCapsuleStore.all(context) }

    var actionFor by remember { mutableStateOf<VoiceCapsuleStore.Capsule?>(null) }
    var showList by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var merging by remember { mutableStateOf(false) }
    var transcriptTarget by remember { mutableStateOf<String?>(null) }
    var playingUri by remember { mutableStateOf<String?>(null) }

    // v0.68.39：先检测系统语音识别是否可用（豆包手机缺识别引擎，直接调会弹"无法识别"）
    val speechAvailable = remember {
        try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    // 转文字：系统语音识别（v0.68.22 既定语义——对着手机再说一遍，转成文字存入胶囊）
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = transcriptTarget
        transcriptTarget = null
        if (id == null) return@rememberLauncherForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                VoiceCapsuleStore.setTranscript(context, id, text)
                PetRepository.say("记下了：$text")
                actionFor = VoiceCapsuleStore.all(context).find { it.id == id }
            } else {
                PetRepository.say("没听清，再试一次？")
            }
        } else {
            // v0.68.39：透出具体失败原因，不再只说"无法识别"
            PetRepository.say("语音识别没成功，可能是没听到声音或网络问题，再试一次？")
        }
    }

    fun startSpeechInput(capsuleId: String) {
        if (!speechAvailable) {
            PetRepository.say("这台手机没有系统语音识别，转文字用不了")
            return
        }
        transcriptTarget = capsuleId
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "对着手机说出这段录音的内容")
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            transcriptTarget = null
            PetRepository.say("这台手机没有系统语音识别，转文字用不了")
        }
    }

    // v0.68.39：分享已删除（Bob：操作简化为播放/转文字/删除；录音文件在 Music/Recordings 公共目录，真要分享去文件管理器）

    fun toggleSelect(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun enterSelecting(id: String) {
        selecting = true
        selectedIds = setOf(id)
    }

    fun exitSelecting() {
        selecting = false
        selectedIds = emptySet()
    }

    fun doMerge() {
        val ids = selectedIds.toList()
        if (ids.size < 2 || merging) return
        merging = true
        scope.launch(Dispatchers.IO) {
            val caps = VoiceCapsuleStore.all(context)
                .filter { it.id in ids }.sortedBy { it.time }
            val res = CapsuleAudio.merge(context, caps.map { Uri.parse(it.uri) })
            withContext(Dispatchers.Main) {
                merging = false
                if (res != null) {
                    val (uri, dur) = res
                    val text = caps.mapNotNull {
                        it.transcript?.takeIf { t -> t.isNotBlank() }
                    }.joinToString("\n")
                    VoiceCapsuleStore.removeIds(context, ids)
                    VoiceCapsuleStore.add(
                        context, uri.toString(), dur,
                        transcript = text.takeIf { it.isNotBlank() }
                    )
                    exitSelecting()
                    showList = false
                    actionFor = null
                    PetRepository.say("已合并为一段")
                } else {
                    PetRepository.say("合并失败，再试一次？")
                }
            }
        }
    }

    if (hidden) return

    Box(modifier = modifier.size(260.dp, 180.dp)) {
        // ---- 浮动胶囊（最近 3 颗） ----
        val recent = capsules.take(FLOAT_MAX)
        recent.forEachIndexed { i, c ->
            val slot = PillSlots[i]
            key(c.id) {
                CapsulePill(
                    capsule = c,
                    slot = slot,
                    phase = i * 1.7f,
                    selecting = selecting,
                    checked = c.id in selectedIds,
                    onTap = {
                        if (selecting) toggleSelect(c.id)
                        else {
                            CapsulePlayer.stop()
                            playingUri = null
                            actionFor = c
                        }
                    },
                    onLongPress = { enterSelecting(c.id) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        // ---- 超出 3 颗：＋N 入口 ----
        val rest = capsules.size - recent.size
        if (rest > 0 && !selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset((-80).dp, 78.dp)
                    .graphicsLayer { rotationZ = 3f }
                    .shadow(4.dp, RoundedCornerShape(99.dp))
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showList = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$rest",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.Body
                )
            }
        }
        // ---- 多选条（列表未打开时，浮在舞台底部） ----
        if (selecting && !showList) {
            SelectionBar(
                count = selectedIds.size,
                merging = merging,
                onMerge = { doMerge() },
                onCancel = { exitSelecting() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        // ---- 动作卡：v0.68.39 改为 Dialog bottom sheet（从屏幕底部弹出，不再挡七仔说话气泡）----
        actionFor?.let { c ->
            // 刷新引用（转文字/播放后 store 已变）
            val fresh = capsules.find { it.id == c.id } ?: c
            Dialog(
                onDismissRequest = {
                    CapsulePlayer.stop()
                    playingUri = null
                    actionFor = null
                },
                properties = DialogProperties(dismissOnClickOutside = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            CapsulePlayer.stop()
                            playingUri = null
                            actionFor = null
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CapsuleActionCard(
                        capsule = fresh,
                        playing = playingUri == fresh.uri && CapsulePlayer.isPlaying(fresh.uri),
                        speechAvailable = speechAvailable,
                        onPlay = {
                            if (playingUri == fresh.uri && CapsulePlayer.isPlaying(fresh.uri)) {
                                CapsulePlayer.stop()
                                playingUri = null
                            } else {
                                val ok = CapsulePlayer.toggle(context, fresh.uri)
                                playingUri = if (ok) fresh.uri else null
                                if (!ok) PetRepository.say("播放失败")
                            }
                        },
                        onTranscript = { startSpeechInput(fresh.id) },
                        onSaveMemo = {
                            val t = fresh.transcript
                            if (!t.isNullOrBlank()) {
                                QuickMemoStore.add(context, t)
                                PetRepository.say("已存为速记")
                            }
                        },
                        onDelete = {
                            CapsulePlayer.stop()
                            playingUri = null
                            VoiceCapsuleStore.remove(context, fresh.id)
                            actionFor = null
                            PetRepository.say("胶囊删掉了")
                        },
                        onClose = {
                            CapsulePlayer.stop()
                            playingUri = null
                            actionFor = null
                        },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 110.dp)
                    )
                }
            }
        }
        // ---- 按天分组列表 ----
        if (showList) {
            CapsuleListSheet(
                capsules = capsules,
                selecting = selecting,
                selectedIds = selectedIds,
                merging = merging,
                onToggleSelect = { toggleSelect(it) },
                onLongPressRow = { enterSelecting(it) },
                onOpen = { c ->
                    showList = false
                    CapsulePlayer.stop()
                    playingUri = null
                    actionFor = c
                },
                onMerge = { doMerge() },
                onCancelSelect = { exitSelecting() },
                onClose = { showList = false }
            )
        }
    }
}

/** 悬浮胶囊 pill：▶ 时长，呼吸浮动 */
@Composable
private fun CapsulePill(
    capsule: VoiceCapsuleStore.Capsule,
    slot: PillSlot,
    phase: Float,
    selecting: Boolean,
    checked: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val inf = rememberInfiniteTransition(label = "capFloat")
    val t by inf.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "capT"
    )
    val dyDp = with(density) {
        (kotlin.math.sin(phase + t) * 3.dp.toPx()).toDp()
    }
    Box(
        modifier = modifier
            .offset(slot.x, slot.y + dyDp)
            .graphicsLayer { rotationZ = slot.rot }
            .shadow(4.dp, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (checked) AILauncherColors.AccentSoft
                else Color.White.copy(alpha = 0.94f)
            )
            .border(
                width = if (selecting) 1.5.dp else 0.dp,
                color = if (checked) AILauncherColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(99.dp)
            )
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .border(
                            1.5.dp,
                            if (checked) AILauncherColors.Accent else AILauncherColors.Hint,
                            CircleShape
                        )
                        .background(
                            if (checked) AILauncherColors.Accent else Color.Transparent,
                            CircleShape
                        )
                )
            }
            Text(text = "▶", fontSize = 9.sp, color = AILauncherColors.Body)
            Text(
                text = fmtDuration(capsule.durationSec),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.Title
            )
        }
    }
}

/**
 * 点胶囊 → 播放 / 转文字 / 删除（v0.68.39：分享已删，操作简化）。
 * 纯卡片，不带遮罩——由调用方的 Dialog 做 bottom sheet 承载（v0.68.39：卡片从底部弹出，不再挡七仔说话）。
 */
@Composable
internal fun CapsuleActionCard(
    capsule: VoiceCapsuleStore.Capsule,
    playing: Boolean,
    speechAvailable: Boolean,
    onPlay: () -> Unit,
    onTranscript: () -> Unit,
    onSaveMemo: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(enabled = false, onClick = {})
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "语音胶囊 · ${fmtDuration(capsule.durationSec)} · ${VoiceCapsuleStore.timeLabel(capsule.time)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.Title
            )
            Text(
                text = "✕",
                fontSize = 14.sp,
                color = AILauncherColors.Hint,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClose() }
                    .padding(4.dp)
            )
        }
        if (!capsule.transcript.isNullOrBlank()) {
            Text(
                text = capsule.transcript,
                fontSize = 14.sp,
                color = AILauncherColors.Body,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onPlay) {
                Text(
                    text = if (playing) "停止" else "播放",
                    fontSize = 13.sp,
                    color = AILauncherColors.Accent,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(
                onClick = {
                    if (speechAvailable) onTranscript()
                    else PetRepository.say("这台手机没有系统语音识别，转文字用不了")
                }
            ) {
                Text(
                    text = "转文字",
                    fontSize = 13.sp,
                    // v0.68.39：本机无语音识别引擎时置灰
                    color = if (speechAvailable) AILauncherColors.Accent else AILauncherColors.Hint,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = "删除",
                    fontSize = 13.sp,
                    color = Color(0xFFE5484D),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (!capsule.transcript.isNullOrBlank()) {
            Button(
                onClick = onSaveMemo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AILauncherColors.AccentSoft,
                    contentColor = AILauncherColors.Title
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "存为速记", fontSize = 13.sp)
            }
        }
    }
}

/** 多选条：合并 / 取消 */
@Composable
private fun SelectionBar(
    count: Int,
    merging: Boolean,
    onMerge: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(bottom = 4.dp)
            .shadow(6.dp, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "已选 $count 段",
            fontSize = 12.sp,
            color = AILauncherColors.Body
        )
        Text(
            text = if (merging) "合并中…" else "合并",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (count >= 2 && !merging) AILauncherColors.Accent else AILauncherColors.Hint,
            modifier = Modifier.clickable(
                enabled = count >= 2 && !merging,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onMerge() }
        )
        Text(
            text = "取消",
            fontSize = 13.sp,
            color = AILauncherColors.Hint,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCancel() }
        )
    }
}

/** 按天分组列表：今日/昨日/M月d日，点组展开 */
@Composable
private fun CapsuleListSheet(
    capsules: List<VoiceCapsuleStore.Capsule>,
    selecting: Boolean,
    selectedIds: Set<String>,
    merging: Boolean,
    onToggleSelect: (String) -> Unit,
    onLongPressRow: (String) -> Unit,
    onOpen: (VoiceCapsuleStore.Capsule) -> Unit,
    onMerge: () -> Unit,
    onCancelSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val groups = remember(capsules) {
        capsules.groupBy { VoiceCapsuleStore.dayLabel(it.time) }
    }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(groups) {
        if (expanded.isEmpty() && groups.isNotEmpty()) {
            expanded = setOf(groups.keys.first())
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 320.dp)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .clickable(enabled = false, onClick = {})
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "语音胶囊",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
                Text(
                    text = "✕",
                    fontSize = 14.sp,
                    color = AILauncherColors.Hint,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onClose() }
                        .padding(4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = "还没有录音胶囊，左滑试试盲操录音",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                groups.forEach { (label, items) ->
                    val isOpen = label in expanded
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AILauncherColors.AccentSoft.copy(alpha = 0.6f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                expanded = if (isOpen) expanded - label else expanded + label
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$label（${items.size}）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Text(
                            text = if (isOpen) "▾" else "▸",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    if (isOpen) {
                        items.forEach { c ->
                            val checked = c.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            if (selecting) onToggleSelect(c.id) else onOpen(c)
                                        },
                                        onLongClick = { onLongPressRow(c.id) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (selecting) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .border(
                                                1.5.dp,
                                                if (checked) AILauncherColors.Accent else AILauncherColors.Hint,
                                                CircleShape
                                            )
                                            .background(
                                                if (checked) AILauncherColors.Accent else Color.Transparent,
                                                CircleShape
                                            )
                                    )
                                }
                                Text(text = "▶", fontSize = 10.sp, color = AILauncherColors.Body)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${fmtDuration(c.durationSec)} · ${VoiceCapsuleStore.timeLabel(c.time)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AILauncherColors.Title
                                    )
                                    if (!c.transcript.isNullOrBlank()) {
                                        Text(
                                            text = c.transcript,
                                            fontSize = 12.sp,
                                            color = AILauncherColors.Hint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (selecting) {
                SelectionBar(
                    count = selectedIds.size,
                    merging = merging,
                    onMerge = onMerge,
                    onCancel = onCancelSelect,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
