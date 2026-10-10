package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import com.bobot.ailauncher.core.pet.AttentionBudget
import com.bobot.ailauncher.core.pet.PetContext
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.core.pet.PetStateMachine
import com.bobot.ailauncher.core.pet.toMood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 宠物身体状态：对应原型 .pet 的 class */
enum class PetMood { IDLE, FETCH, SORTING, FILE, HAPPY, SHY, DIZZY, SURPRISED }

/** 宠物嘴型：idle 微笑 / happy 大笑 / busy 一字 / o 惊讶 */
enum class PetMouth { IDLE, HAPPY, BUSY, O }

/**
 * 宠物整理员状态机（单例）。
 * 流程：新通知 → ding → fetch → carry+sorting → sort-tag → file 动画 → 呈现一次 →
 *       8 秒无操作自动 fileToTab；之后走右侧标签栏手势。
 * 手势签名（全 App 统一）：左滑 = 多，右滑 = 少。
 */
object PetRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appCtx: Context? = null
    private var ownPackage: String = ""

    val mood: StateFlow<PetMood> get() = _mood.asStateFlow()
    val mouth: StateFlow<PetMouth> get() = _mouth.asStateFlow()
    val dingText: StateFlow<String?> get() = _dingText.asStateFlow()
    val carryText: StateFlow<String?> get() = _carryText.asStateFlow()
    val carryToRight: StateFlow<Boolean> get() = _carryToRight.asStateFlow()
    val showDots: StateFlow<Boolean> get() = _showDots.asStateFlow()
    val sortText: StateFlow<String?> get() = _sortText.asStateFlow()
    val presented: StateFlow<PetItem?> get() = _presented.asStateFlow()
    val cardArmed: StateFlow<Boolean> get() = _cardArmed.asStateFlow()
    val filed: StateFlow<Map<PetCat, List<PetItem>>> get() = _filed.asStateFlow()
    val affection: StateFlow<Int> get() = _affection.asStateFlow()
    val toast: SharedFlow<String> get() = _toast.asSharedFlow()
    /** 语义状态（PRD §十二）：时间/互动/Context 共同决定，PetZone 可据此微调渲染 */
    val petState: StateFlow<PetState> get() = _petState.asStateFlow()
    /** 主动预算（PRD §十四）：AiBrain 在 Phase 4b 接入 */
    val attentionBudget = AttentionBudget()

    private val _mood = MutableStateFlow(PetMood.IDLE)
    private val _mouth = MutableStateFlow(PetMouth.IDLE)
    private val _dingText = MutableStateFlow<String?>(null)
    private val _carryText = MutableStateFlow<String?>(null)
    private val _carryToRight = MutableStateFlow(false)
    private val _showDots = MutableStateFlow(false)
    private val _sortText = MutableStateFlow<String?>(null)
    private val _presented = MutableStateFlow<PetItem?>(null)
    private val _cardArmed = MutableStateFlow(false)
    private val _filed = MutableStateFlow<Map<PetCat, List<PetItem>>>(emptyMap())
    private val _affection = MutableStateFlow(15)
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _petState = MutableStateFlow(PetState.CURIOUS)
    // v0.29.0：新通知送达——七仔小跑送通知（app 名 + 计数，PetZone 显示浮动徽标）
    private val _deliverTick = MutableStateFlow(0)
    val deliverTick: StateFlow<Int> get() = _deliverTick.asStateFlow()
    // v0.64.0：十万火急触发器，PetZone 观察后播放动效（七仔跳动+红光+P0呼吸+光带）
    private val _urgentTick = MutableStateFlow(0)
    val urgentTick: StateFlow<Int> get() = _urgentTick.asStateFlow()
    private val _deliverApp = MutableStateFlow<String?>(null)
    val deliverApp: StateFlow<String?> get() = _deliverApp.asStateFlow()
    // v0.29.0：会议临近——15 分钟内有会，七仔戴手表
    private val _meetingSoon = MutableStateFlow<String?>(null)
    val meetingSoon: StateFlow<String?> get() = _meetingSoon.asStateFlow()
    // v0.47.0：七仔说话——统一信息区（Mii 风游戏化气泡），七仔有什么话都走这里
    data class SpeechMsg(val id: Long, val text: String)
    private val _speech = MutableStateFlow<SpeechMsg?>(null)
    val speech: StateFlow<SpeechMsg?> get() = _speech.asStateFlow()

    /** 七仔说话：新消息直接替换当前（最新优先） */
    fun say(text: String) {
        _speech.value = SpeechMsg(System.currentTimeMillis(), text)
    }

    fun clearSpeech() {
        _speech.value = null
    }

    // ============ v0.56.0 M2/M3/M4：播报链与时段 ============

    /** M3：同一应用 30 秒内合并——普通消息气泡 */
    private val lastSayPerApp = mutableMapOf<String, Long>()
    fun sayApp(appName: String) {
        val now = System.currentTimeMillis()
        val last = lastSayPerApp[appName] ?: 0L
        if (now - last < 30_000L) return // 30 秒内同一应用合并，不重复说
        lastSayPerApp[appName] = now
        // M4 深夜：普通消息零播报
        if (isNight()) return
        // M3：用户手势中只做 L1（由调用方控制 mood），不抢气泡
        if (userInteracting.value) {
            _mood.value = PetMood.FETCH // L1：表情变化即可
            return
        }
        say("$appName 有新消息")
    }

    /** M3：用户是否正在手势交互（拖拽/抚摸中） */
    private val _userInteracting = MutableStateFlow(false)
    val userInteracting: StateFlow<Boolean> get() = _userInteracting.asStateFlow()
    fun setUserInteracting(v: Boolean) { _userInteracting.value = v }

    /** M4：深夜模式 23:00–06:00 */
    fun isNight(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 6
    }

    /** M4：早晨 6–10 点 */
    fun isMorning(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h in 6..10
    }

    /** M2 抚摸：害羞 + 15% 概率说"舒服"，同会话最多 1 次 */
    private var pettedSaidComfy = false
    fun onPetted() {
        _mood.value = PetMood.SHY
        _mouth.value = PetMouth.O
        if (!pettedSaidComfy && kotlin.random.Random.nextFloat() < 0.15f) {
            pettedSaidComfy = true
            say("舒服~")
        }
        scope.launch {
            delay(1500)
            if (_mood.value == PetMood.SHY) {
                _mood.value = PetMood.IDLE
                _mouth.value = PetMouth.IDLE
            }
        }
    }

    /** M2 拖拽：提起惊讶 */
    fun onDragStart() {
        setUserInteracting(true)
        _mood.value = PetMood.SURPRISED
        _mouth.value = PetMouth.O
    }

    /** M2 拖拽：松手弹跳 + 晕眩 1 秒 */
    fun onDragEnd() {
        setUserInteracting(false)
        _mood.value = PetMood.DIZZY
        scope.launch {
            delay(1000)
            if (_mood.value == PetMood.DIZZY) {
                _mood.value = PetMood.IDLE
                _mouth.value = PetMouth.IDLE
            }
        }
    }

    /** M2 点击：开心表情 1.2 秒（不说话） */
    fun setMoodHappyBrief() {
        _mood.value = PetMood.HAPPY
        _mouth.value = PetMouth.HAPPY
        scope.launch {
            delay(1200)
            if (_mood.value == PetMood.HAPPY) {
                _mood.value = PetMood.IDLE
                _mouth.value = PetMouth.IDLE
            }
        }
    }

    /** M3 重要消息：惊讶 → 弹跳 → 呈现（Capsule 由调用方 show） */
    fun onImportantArrived(appName: String) {
        _mood.value = PetMood.SURPRISED
        _mouth.value = PetMouth.O
        scope.launch {
            delay(400)
            _mood.value = PetMood.HAPPY // 弹跳
            delay(600)
            _mood.value = PetMood.IDLE
            // 深夜也报重要
            say("$appName 有重要消息")
        }
    }
    // v0.49.0：AI Capsule——首页宠物下方的信息卡，一次只说一件最重要的事
    data class AiCapsule(
        val id: String,
        val timeLabel: String, // "17:23" 或 "下午"
        val title: String, // "下午有一件事情值得关注"
        val body: String, // 多行正文
        val primaryAction: String = "看看重点",
        val secondaryAction: String = "稍后",
        val meetingUrl: String? = null // v0.58.0：在线会议链接，有则显示"加入会议"
    )
    private val _capsule = MutableStateFlow<AiCapsule?>(null)
    val capsule: StateFlow<AiCapsule?> get() = _capsule.asStateFlow()

    /** 显示胶囊：一次只显示一个，新的替换旧的（最重要的赢） */
    fun showCapsule(capsule: AiCapsule) {
        _capsule.value = capsule
    }

    fun dismissCapsule() {
        _capsule.value = null
    }
    private var lastInteractMs = System.currentTimeMillis()

    private var busy = false
        set(v) {
            field = v
            // 忙/闲切换 → 语义状态重估（WORKING ↔ 其他）
            refreshState()
        }
    private var autoFileJob: Job? = null
    private val recent = mutableMapOf<String, Long>()
    private val handledCalendarKeys = mutableSetOf<String>()
    // v0.16：10 秒聚合窗口——忙碌中或 10 秒内连续多条 → 合并为一批处理
    private val pendingBatch = mutableListOf<PetItem>()
    private var batchJob: Job? = null
    private var singleJob: Job? = null
    private var lastArrivalTime = 0L
    private const val BATCH_WINDOW_MS = 10_000L

    private const val PREFS = "ui_prefs"
    private const val KEY_AFFECTION = "pet_affection"

    fun init(context: Context) {
        appCtx = context.applicationContext
        ownPackage = context.packageName
        _affection.value = appCtx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_AFFECTION, 15)
        // v0.19：状态机时钟——每 60 秒按时间/闲置重估一次语义状态
        scope.launch {
            while (true) {
                delay(60_000)
                refreshState()
            }
        }
        refreshState()
    }

    /**
     * v0.19 语义状态刷新（PRD §十二）。
     * 只在整理员流程空闲（mood == IDLE）时把语义状态映射到渲染 mood，
     * 流程中的 FETCH/SORTING/FILE 不受影响。
     */
    fun refreshState() {
        val minutesIdle = (System.currentTimeMillis() - lastInteractMs) / 60_000
        val hasImportant = (_filed.value[PetCat.IMP]?.size ?: 0) > 0 ||
            _presented.value?.cat == PetCat.IMP
        val state = PetStateMachine.derive(
            PetContext(
                busy = busy,
                hasImportant = hasImportant,
                minutesIdle = minutesIdle
            )
        )
        _petState.value = state
        if (_mood.value == PetMood.IDLE) {
            _mood.value = state.toMood()
        }
    }

    /** 任何用户互动都先记一笔（状态机输入） */
    private fun markInteracted() {
        lastInteractMs = System.currentTimeMillis()
    }

    fun addAffection(n: Int) {
        val v = (_affection.value + n).coerceAtMost(100)
        _affection.value = v
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putInt(KEY_AFFECTION, v)?.apply()
    }

    /** 点按宠物：happy 弹跳一下 */
    fun petTapped() {
        markInteracted()
        if (_mood.value == PetMood.HAPPY) {
            refreshState()
            return
        }
        val prev = _mood.value
        _mood.value = PetMood.HAPPY
        _mouth.value = PetMouth.HAPPY
        scope.launch {
            delay(450)
            if (_mood.value == PetMood.HAPPY) {
                _mood.value = if (prev == PetMood.IDLE) PetMood.IDLE else prev
                _mouth.value = PetMouth.IDLE
            }
        }
    }

    // ---------------- 新东西进来 ----------------

    /** 通知监听服务回调：单条新通知 */
    fun handleIncomingNotification(n: SimpleNotification) {
        if (n.packageName == ownPackage) return
        val (cat, desc) = PetClassifier.classify(n.packageName, n.appName, n.title, n.text)
        // v0.29.0：新通知送达动画
        _deliverApp.value = n.appName
        _deliverTick.value += 1
        // v0.49.0：重要通知 → AI Capsule（一次只说一件）
        // v0.64.0：重要消息不再弹 Capsule，只走七仔表情+头顶气泡+TODAY P0 置顶
        if (cat == PetCat.IMP) {
            // 重要消息七仔也开口
            say("${n.appName}有重要消息")
        }
        // v0.64.0：十万火急判断（IMP 才判）
        val urgent = cat == PetCat.IMP &&
            AiInsight.isUrgentMessage(n.appName, n.title, n.text, n.time)
        if (urgent) _urgentTick.value += 1
        handleIncoming(
            PetItem(
                id = "n${n.time}_${n.packageName.hashCode()}",
                cat = cat,
                sortDesc = desc,
                appName = n.appName,
                title = n.title,
                text = n.text,
                time = n.time,
                packageName = n.packageName,
                // v0.60.0：带上 contentIntent，点击直达会话
                pendingIntent = n.contentIntent,
                // v0.64.0：十万火急标记
                isUrgent = urgent
            )
        )
    }

    /** v0.29.0：设置/清除会议临近状态（15 分钟内有会） */
    fun setMeetingSoon(title: String?) {
        _meetingSoon.value = title
    }

    /** 日历：30 分钟内开始的日程 → 重要 */
    fun handleIncomingCalendar(title: String, begin: Long, location: String) {
        val key = "$title|$begin"
        if (!handledCalendarKeys.add(key)) return
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(begin))
        handleIncoming(
            PetItem(
                id = "c$begin",
                cat = PetCat.IMP,
                sortDesc = "这是30分钟内的日程",
                appName = "日历",
                title = title.ifBlank { "（无标题）" },
                text = "$time 开始" + if (location.isNotBlank()) " · $location" else "",
                time = System.currentTimeMillis(),
                packageName = "",
                isCalendar = true
            )
        )
    }

    private fun handleIncoming(item: PetItem) {
        val key = "${item.packageName}|${item.title}|${item.text}"
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val last = recent[key]
            if (last != null && now - last < 120_000) return
            recent[key] = now
            if (recent.size > 200) recent.clear()
        }
        // v0.16 聚合：忙碌中、或 10 秒内连续来多条 → 进聚合窗口，debounce 10 秒后一批处理
        if (busy || now - lastArrivalTime < BATCH_WINDOW_MS) {
            pendingBatch.add(item)
            lastArrivalTime = now
            batchJob?.cancel()
            batchJob = scope.launch {
                delay(BATCH_WINDOW_MS)
                runBatch()
            }
        } else {
            lastArrivalTime = now
            singleJob?.cancel()
            singleJob = scope.launch { runSequence(item) }
        }
    }

    /** 一批：一次 fetch 动画 → 逐条展示分类决策 → 一张汇总卡片 → 8 秒后逐个归档 */
    private suspend fun runBatch() {
        if (pendingBatch.isEmpty()) return
        val items = pendingBatch.toList()
        pendingBatch.clear()
        // 单条序列可能还在跑（fetch/思考延迟中）：取消它，避免它随后 present 覆盖批量卡片
        singleJob?.cancel()
        singleJob = null
        if (busy) {
            // 单条序列还在跑：先把正在呈现的收走，再跑批量
            autoFileJob?.cancel()
            _presented.value?.let { cur ->
                _presented.value = null
                _cardArmed.value = false
                fileItems(if (cur.isBatchSummary) cur.batchItems else listOf(cur))
            }
        }
        busy = true
        try {
            _dingText.value = "叮！收到 ${items.size} 条新消息"
            _mouth.value = PetMouth.O
            _mood.value = PetMood.FETCH
            delay(900)
            _dingText.value = null
            _carryText.value = "${items.size} 条新消息"
            _carryToRight.value = false
            _mood.value = PetMood.SORTING
            _showDots.value = true
            _mouth.value = PetMouth.BUSY
            delay(1000)
            _showDots.value = false
            // 逐条（同应用同类合并为"微信×2 → 工作"）简短展示分类决策
            items.groupBy { it.appName to it.cat }.forEach { (key, group) ->
                val (appName, cat) = key
                // v0.51.1：不再按分类归档，统一进 Today
                _sortText.value = if (group.size > 1) "$appName×${group.size}"
                else group.first().sortDesc
                delay(700)
            }
            _sortText.value = null
            _carryToRight.value = true
            _mood.value = PetMood.FILE
            delay(600)
            _carryText.value = null
            val summary = PetItem(
                id = "batch${System.currentTimeMillis()}",
                cat = items.map { it.cat }.minByOrNull { it.ordinal } ?: PetCat.WORK,
                sortDesc = "",
                appName = "宠物整理员",
                title = "收到 ${items.size} 条新消息",
                text = "",
                time = System.currentTimeMillis(),
                packageName = "",
                isBatchSummary = true,
                batchItems = items
            )
            present(summary)
        } catch (e: CancellationException) {
            throw e // 被批量流程取消：不碰 busy/视觉，交由 runBatch 接管
        } catch (_: Exception) {
            busy = false
            resetVisual()
        }
    }

    /** 把一组 item 逐个归档到各自分类标签（badge 累加） */
    private fun fileItems(items: List<PetItem>) {
        val map = _filed.value.toMutableMap()
        items.forEach { sub ->
            map[sub.cat] = (map[sub.cat] ?: emptyList()) + sub
        }
        _filed.value = map
        items.forEach { dismissFromStream(it) }
    }

    /**
     * v0.58.0：按条件删除已归档消息（需求2：通知栏划掉后 Today 同步清除）。
     * 由 LauncherNotificationService.onNotificationRemoved 调用。
     */
    fun removeFiledByPredicate(predicate: (PetItem) -> Boolean) {
        val map = _filed.value.toMutableMap()
        var changed = false
        for ((cat, list) in map) {
            val filtered = list.filterNot(predicate)
            if (filtered.size != list.size) {
                map[cat] = filtered
                changed = true
            }
        }
        if (changed) _filed.value = map
    }

    // ============ v0.59.0 AI-3：待办 ============

    /** 已完成的待办 id（用户点击标记完成） */
    private val _doneTodos = MutableStateFlow<Set<String>>(emptySet())
    val doneTodos: StateFlow<Set<String>> get() = _doneTodos.asStateFlow()

    /** 标记待办完成 */
    fun markTodoDone(id: String) {
        _doneTodos.value = _doneTodos.value + id
    }

    private suspend fun runSequence(item: PetItem) {
        if (busy) {
            // v0.51.3：Toast 已删除：_toast.emit("等它忙完手头这件…")
            return
        }
        busy = true
        try {
            // 1. 叮 + 上跳接东西
            _dingText.value = dingTextFor(item)
            _mouth.value = PetMouth.O
            _mood.value = PetMood.FETCH
            delay(900)
            // 2. 叼着 + 思考分类
            _dingText.value = null
            _carryText.value = carryTextFor(item)
            _carryToRight.value = false
            _mood.value = PetMood.SORTING
            _showDots.value = true
            _mouth.value = PetMouth.BUSY
            delay(1300)
            // 3. 展示已记入（v0.51.1：不再按分类归档，统一进 Today）
            _showDots.value = false
            _sortText.value = item.sortDesc
            delay(1100)
            _sortText.value = null
            _mood.value = PetMood.FILE
            delay(600)
            // 5. 呈现一次
            _carryText.value = null
            present(item)
        } catch (e: CancellationException) {
            throw e // 被批量流程取消：不碰 busy/视觉，交由 runBatch 接管
        } catch (_: Exception) {
            busy = false
            resetVisual()
        }
    }

    private fun present(item: PetItem) {
        _mood.value = PetMood.IDLE
        _mouth.value = PetMouth.HAPPY
        _presented.value = item
        _cardArmed.value = false
        // v0.51.3：Toast 已删除
        // 小弹跳
        _mood.value = PetMood.HAPPY
        scope.launch {
            delay(400)
            if (_mood.value == PetMood.HAPPY && _presented.value?.id == item.id) {
                _mood.value = PetMood.IDLE
            }
        }
        autoFileJob?.cancel()
        autoFileJob = scope.launch {
            delay(8000)
            if (_presented.value?.id == item.id) fileToTab()
        }
    }

    // ---------------- 标签栏手势 ----------------

    /** 8 秒无操作 / 卡片右滑：收回成右侧标签（汇总卡片逐个归档到各自标签） */
    fun fileToTab() {
        val item = _presented.value ?: run { busy = false; return }
        autoFileJob?.cancel()
        _presented.value = null
        _cardArmed.value = false
        val toFile = if (item.isBatchSummary) item.batchItems else listOf(item)
        fileItems(toFile)
        _mouth.value = PetMouth.BUSY
        _mood.value = PetMood.FILE
        scope.launch {
            // v0.51.3：Toast 已全部删除
            delay(700)
            _mood.value = PetMood.IDLE
            _mouth.value = PetMouth.IDLE
            busy = false
        }
    }

    /** 卡片左滑：出现操作按钮 */
    fun armCard() {
        if (_presented.value == null) return
        autoFileJob?.cancel()
        _cardArmed.value = true
        // v0.51.3：Toast 已全部删除，改用七仔说话
    }

    /** 标签右滑：推出屏幕，直接完成清空整个分类 */
    fun completeTab(cat: PetCat) {
        autoFileJob?.cancel()
        val items = _filed.value[cat] ?: emptyList()
        val map = _filed.value.toMutableMap()
        map[cat] = emptyList()
        _filed.value = map
        // 若卡片正展示该分类的东西，一并收掉
        val pres = _presented.value
        if (pres?.cat == cat) {
            _presented.value = null
            _cardArmed.value = false
            dismissFromStream(pres)
        }
        // 顺手把首页通知流里同类的也清掉
        items.forEach { dismissFromStream(it) }
        happyDone(null)
    }

    /** 卡片上"完成"/操作按钮：完成这一件（汇总卡片则完成全部） */
    fun completeItem(how: String? = null) {
        autoFileJob?.cancel()
        val item = _presented.value
        _presented.value = null
        _cardArmed.value = false
        if (item != null) {
            (if (item.isBatchSummary) item.batchItems else listOf(item))
                .forEach { dismissFromStream(it) }
        }
        happyDone(how)
    }

    private fun happyDone(how: String?) {
        _mood.value = PetMood.HAPPY
        _mouth.value = PetMouth.HAPPY
        addAffection(1)
        // v0.51.3：Toast 改用七仔说话
        say(if (how != null) "已${how}" else "搞定")
        scope.launch {
            delay(1200)
            _mood.value = PetMood.IDLE
            _mouth.value = PetMouth.IDLE
            busy = false
        }
    }

    private fun dismissFromStream(item: PetItem) {
        val match = NotificationRepository.notifications.value.firstOrNull {
            it.packageName == item.packageName && it.title == item.title && it.text == item.text
        }
        match?.let { NotificationRepository.dismiss(it) }
    }

    private fun resetVisual() {
        _dingText.value = null
        _carryText.value = null
        _showDots.value = false
        _sortText.value = null
        _mood.value = PetMood.IDLE
        _mouth.value = PetMouth.IDLE
    }

    // ---------------- 文案 ----------------

    private fun dingTextFor(item: PetItem): String = when {
        item.isBatchSummary -> "叮！收到 ${item.batchItems.size} 条新消息"
        else -> when (item.cat) {
            PetCat.IMP -> "叮！${item.appName}有重要消息"
            PetCat.WORK -> "叮！收到一条工作消息"
            PetCat.FUN -> "有条娱乐通知"
            PetCat.PRIV -> "收到一条私密消息（已打码）"
        }
    }

    private fun carryTextFor(item: PetItem): String =
        if (item.cat == PetCat.PRIV) "${item.appName} · •••"
        else "${item.appName} · ${item.title.take(12)}${if (item.title.length > 12) "…" else ""}"
}
