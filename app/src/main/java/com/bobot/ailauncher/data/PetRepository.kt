package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 宠物身体状态：对应原型 .pet 的 class */
enum class PetMood { IDLE, FETCH, SORTING, FILE, HAPPY }

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

    private var busy = false
    private var autoFileJob: Job? = null
    private val recent = mutableMapOf<String, Long>()
    private val handledCalendarKeys = mutableSetOf<String>()

    private const val PREFS = "ui_prefs"
    private const val KEY_AFFECTION = "pet_affection"

    fun init(context: Context) {
        appCtx = context.applicationContext
        ownPackage = context.packageName
        _affection.value = appCtx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_AFFECTION, 15)
    }

    fun addAffection(n: Int) {
        val v = (_affection.value + n).coerceAtMost(100)
        _affection.value = v
        appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putInt(KEY_AFFECTION, v)?.apply()
    }

    /** 点按宠物：happy 弹跳一下 */
    fun petTapped() {
        if (_mood.value == PetMood.HAPPY) return
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
        handleIncoming(
            PetItem(
                id = "n${n.time}_${n.packageName.hashCode()}",
                cat = cat,
                sortDesc = desc,
                appName = n.appName,
                title = n.title,
                text = n.text,
                time = n.time,
                packageName = n.packageName
            )
        )
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
        scope.launch { runSequence(item) }
    }

    private suspend fun runSequence(item: PetItem) {
        if (busy) {
            _toast.emit("等它忙完手头这件…")
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
            // 3. 展示分类决策
            _showDots.value = false
            _sortText.value = "${item.sortDesc} → 归到「${item.cat.cnName}」"
            delay(1100)
            // 4. 叼向右侧
            _sortText.value = null
            _carryToRight.value = true
            _mood.value = PetMood.FILE
            delay(600)
            // 5. 呈现一次
            _carryText.value = null
            present(item)
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
        scope.launch {
            _toast.emit("呈现一次 · 8 秒没理它会被收走")
            // 小弹跳
            _mood.value = PetMood.HAPPY
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

    /** 8 秒无操作 / 卡片右滑：收回成右侧标签 */
    fun fileToTab() {
        val item = _presented.value ?: run { busy = false; return }
        autoFileJob?.cancel()
        _presented.value = null
        _cardArmed.value = false
        val map = _filed.value.toMutableMap()
        map[item.cat] = (map[item.cat] ?: emptyList()) + item
        _filed.value = map
        _mouth.value = PetMouth.BUSY
        _mood.value = PetMood.FILE
        scope.launch {
            _toast.emit("已收到右侧「${item.cat.cnName}」· 左滑标签展开")
            delay(700)
            _mood.value = PetMood.IDLE
            _mouth.value = PetMouth.IDLE
            busy = false
        }
    }

    /** 标签左滑：拉进屏幕展开卡片（取该分类最新一件） */
    fun expandFromTab(cat: PetCat) {
        val list = _filed.value[cat] ?: emptyList()
        if (list.isEmpty()) return
        autoFileJob?.cancel()
        val item = list.last()
        val map = _filed.value.toMutableMap()
        map[cat] = list.dropLast(1)
        _filed.value = map
        busy = true
        _mouth.value = PetMouth.HAPPY
        _mood.value = PetMood.HAPPY
        _presented.value = item
        _cardArmed.value = false
        scope.launch {
            _toast.emit("左滑卡片出操作，右滑收回去")
            delay(450)
            if (_mood.value == PetMood.HAPPY) _mood.value = PetMood.IDLE
        }
    }

    /** 卡片左滑：出现操作按钮 */
    fun armCard() {
        if (_presented.value == null) return
        autoFileJob?.cancel()
        _cardArmed.value = true
        scope.launch { _toast.emit("下一步操作出现了") }
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

    /** 卡片上"完成"/操作按钮：完成这一件 */
    fun completeItem(how: String? = null) {
        autoFileJob?.cancel()
        val item = _presented.value
        _presented.value = null
        _cardArmed.value = false
        item?.let { dismissFromStream(it) }
        happyDone(how)
    }

    private fun happyDone(how: String?) {
        _mood.value = PetMood.HAPPY
        _mouth.value = PetMouth.HAPPY
        addAffection(1)
        scope.launch {
            _toast.emit(if (how != null) "已${how} ✓" else "搞定！亲密度 +1 ♥")
            delay(550)
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

    private fun dingTextFor(item: PetItem): String = when (item.cat) {
        PetCat.IMP -> "叮！${item.appName}有重要消息"
        PetCat.WORK -> "叮！收到一条工作消息"
        PetCat.FUN -> "有条娱乐通知"
        PetCat.PRIV -> "收到一条私密消息（已打码）"
    }

    private fun carryTextFor(item: PetItem): String =
        if (item.cat == PetCat.PRIV) "${item.appName} · •••"
        else "${item.appName} · ${item.title.take(12)}${if (item.title.length > 12) "…" else ""}"
}
