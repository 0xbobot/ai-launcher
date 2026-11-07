package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** 界面偏好：存 SharedPreferences "ui_prefs" */
object UiPrefs {
    private const val PREFS = "ui_prefs"
    private const val KEY_DOCK_ROWS = "dock_rows"
    private const val KEY_DOCK_VISIBLE = "dock_visible"
    private const val KEY_DOCK_SMART_SORT = "dock_smart_sort"
    private const val KEY_DOCK_PINNED = "dock_pinned"
    private const val KEY_ROW_ACTIONS_GUIDE_SEEN = "row_actions_guide_seen"
    private const val KEY_PANEL_GUIDE_SEEN = "panel_guide_seen"
    private const val KEY_PANEL_GUIDE_REMIND = "panel_guide_reminded"
    // 折叠组（419/421）：上次取消时 N/展开态；foldTick 供首页 rail 跟随刷新
    private const val KEY_FOLD_LAST_DECLINED_N = "fold_last_declined_n"
    private const val KEY_FOLD_EXPANDED = "fold_expanded"
    // idea 灯泡（422）：上次看过说明页时的智能尾巴（逗号拼包名）；null 表从未建立基线
    private const val KEY_INSIGHT_SEEN_TAIL = "dock_insight_seen_tail"
    // 智能卡片连接器（447）：五个本机来源各自的开关（默认全开；关掉后该来源
    // 的卡片立即停用，权限本身不动——断开≠撤权限，重接不用再跑系统设置）
    private const val KEY_CONNECTOR_PREFIX = "smart_connector_"

    /** 连接器键（与 SmartCardRepository.buildConnectorStates 的五项一致） */
    const val CONNECTOR_NOTIFICATION = "notification"
    const val CONNECTOR_CALENDAR = "calendar"
    const val CONNECTOR_MESSAGE = "message"
    const val CONNECTOR_PHONE = "phone"
    const val CONNECTOR_CONTACTS = "contacts"

    /** 智能卡片连接器是否开启（默认 true；权限未开时开关本身不产生内容） */
    fun isConnectorEnabled(context: Context, key: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONNECTOR_PREFIX + key, true)
    }

    fun setConnectorEnabled(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONNECTOR_PREFIX + key, enabled).apply()
    }

    /** Dock 数据变化通知：置顶增删/排序后 bump，Dock 侧重算 */
    val dockTick = MutableStateFlow(0)
    // v0.56.2（Bob）：Rail 图标闪烁——收藏/隐藏时提示目标位置；Pair(计数, "star"/"eye")
    private val _railFlash = MutableStateFlow(0 to "")
    val railFlash = _railFlash
    fun flashRail(target: String) {
        _railFlash.value = (_railFlash.value.first + 1) to target
    }
    // v0.67.8（Bob）：主屏长按 rail 选字母 → 打开应用中心定位到该字母
    // v0.67.10：连续驱动——手指不松开时持续更新，应用中心跟随滚动；dismiss 时置 null
    val homeLetterRequest = MutableStateFlow<Char?>(null)
    // v0.67.13（Bob）：主屏 picker 目标类型
    enum class HomeRailTarget { LETTER, DOCK, HIDDEN }
    val homeRailTarget = MutableStateFlow<HomeRailTarget?>(null)
    val homeRailLetter = MutableStateFlow<Char?>(null)
    // v0.67.10：从主屏 picker 打开 D3 时，D3 用淡入（不滑入），过渡无缝
    val d3EnterFromPicker = MutableStateFlow(false)
    // 字母导航等高（Bob 2026-10-25 追问）：应用中心竖屏列表容器实测高度（px），
    // 由应用中心列表容器 onSizeChanged 写入（键盘弹出时不采，见 AppCenter 侧），
    // 首页选择器读它做等高；首次进应用中心前为 null，首页回退跟随容器。
    // 进程内状态，不持久化；转屏/字号变化后下次进应用中心重测即自愈。
    val appCenterListHeightPx = MutableStateFlow<Int?>(null)
    // 智能卡片叠层带（451）：首页卡片叠层在根坐标里的上下缘（px，top to bottom），
    // 由 HomeScreen 卡片槽位 onGloballyPositioned 写入；MainScreen 首页 picker
    // （AppRail）读它，把触摸层在卡带处断开——卡片右侧按钮（连接/×）落在
    // 48dp 隐形长按触发条内时点按会被条偷走、到不了卡片（449 实拍）；带内不挂
    // 触摸层、点按落到卡片，带外仍挂、长按开 picker。无卡时为 null，触摸层整条
    // 不断。进程内状态，不持久化；卡片换张/高度变时重写。
    val cardStackBandPx = MutableStateFlow<Pair<Float, Float>?>(null)
    fun bumpDock() {
        dockTick.value++
    }

    /** 首页 Dock 记住的行数（1=D1 一行，2=D2 两行），默认 1 */
    fun getDockRows(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DOCK_ROWS, 1).coerceIn(1, 2)
    }

    fun setDockRows(context: Context, rows: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DOCK_ROWS, rows.coerceIn(1, 2)).apply()
    }

    /** Dock 是否显示（2026-10-27 手势清理起隐藏手势入口已删除，恢复走首页上滑叫回） */
    fun getDockVisible(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOCK_VISIBLE, true)
    }

    fun setDockVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_VISIBLE, visible).apply()
    }

    /**
     * Dock 智能排序（v0.57.0：默认开启，不再提供开关）。恒为 true。
     */
    fun getDockSmartSort(context: Context): Boolean = true

    fun setDockSmartSort(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DOCK_SMART_SORT, enabled).apply()
    }

    /**
     * Dock 置顶：用户手动固定的应用包名有序列表（v0.35.0）。
     * Dock 渲染时置顶排最前（按此顺序），后面再跟智能排序补齐。
     */
    fun getDockPinned(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOCK_PINNED, "").orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun setDockPinned(context: Context, pkgs: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DOCK_PINNED, pkgs.filter { it.isNotBlank() }.distinct().joinToString("\n")).apply()
        bumpDock()
    }

    /** 应用行左滑操作区的新手引导是否已展示过（v0.35.0）：只打扰一次 */
    fun hasSeenRowActionsGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ROW_ACTIONS_GUIDE_SEEN, false)
    }

    fun setRowActionsGuideSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ROW_ACTIONS_GUIDE_SEEN, true).apply()
    }

    /**
     * 首屏下滑手势的权限说明卡（v0.36.0）：第一次触发下滑手势但无障碍未开启时，
     * 先弹 App 内说明卡而不是直接跳系统设置。看过一次后不再弹整卡。
     */
    fun hasSeenPanelGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PANEL_GUIDE_SEEN, false)
    }

    fun setPanelGuideSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PANEL_GUIDE_SEEN, true).apply()
    }

    /**
     * 未授权时的二次轻提醒（v0.36.0）：说明卡看过、点了"去开启"但仍没授权，
     * 下次触发时再轻提醒一次，之后不再打扰（回落到 Toast）。
     */
    fun hasRemindedPanelGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PANEL_GUIDE_REMIND, false)
    }

    fun setPanelGuideReminded(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PANEL_GUIDE_REMIND, true).apply()
    }

    /** 折叠组状态变化通知：取消/收展后 bump，首页 rail 跟随重算 */
    val foldTick = MutableStateFlow(0)

    /** 上次取消折叠时的 N（-1 表从未取消）；N 再多 10 个时安静重现 */
    fun getFoldLastDeclinedN(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FOLD_LAST_DECLINED_N, -1)
    }

    fun setFoldLastDeclinedN(context: Context, n: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FOLD_LAST_DECLINED_N, n).apply()
        foldTick.value++
    }

    /** 折叠组展开态，默认折叠（false） */
    fun getFoldExpanded(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FOLD_EXPANDED, false)
    }

    fun setFoldExpanded(context: Context, expanded: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FOLD_EXPANDED, expanded).apply()
        foldTick.value++
    }

    /** idea 灯泡看过的智能尾巴（逗号拼包名）；null 表从未建立基线（首次运行静默建基线） */
    fun getInsightSeenTail(context: Context): List<String>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_INSIGHT_SEEN_TAIL, null) ?: return null
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setInsightSeenTail(context: Context, tail: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_INSIGHT_SEEN_TAIL, tail.joinToString(",")).apply()
    }
}
