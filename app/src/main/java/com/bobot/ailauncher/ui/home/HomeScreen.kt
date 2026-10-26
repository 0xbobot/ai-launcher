package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.service.PanelAccessibilityService
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import com.bobot.ailauncher.util.rebindListener
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * v0.27.0：重建的极简主页——只留宠物。
 * Bob 要求：和 Dock/宠物无关的全部清除。
 *
 * 首屏手势（v0.34）：
 * - 上滑 → D3 应用中心
 * - 下滑往左（斜）→ 控制中心（快捷设置）
 * - 下滑往右（斜）→ 通知中心
 * 控制中心/通知中心经无障碍服务打开，需用户手动开启。
 *
 * v0.34.1：NestedScrollConnection 在空 Box 上不触发（无可滚动子组件），
 * 改用 pointerInput + detectDragGestures 直接检测。
 *
 * v0.36.0：下滑手势的权限引导——第一次触发但无障碍未开启时，
 * 先弹 App 内说明卡（讲清为什么需要这个权限），再放"去开启"跳系统设置；
 * 看过一次后不再弹整卡；点了去开启仍没授权，下次再轻提醒一次，之后回落到 Toast，不死缠。
 */
@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit,
    cleanHome: Boolean = true,
    onBlindActiveChange: (Boolean) -> Unit = {},
    // v0.68.32：扇形选择状态上报 MainScreen（扇形绘制层级提到 Dock 之上）
    // v0.68.35：Selecting 自带动态段列表，不再传自定义标签
    // v0.68.42：上报全状态（Selecting/Dismissing/PhotoFlash 共用顶层绘制）
    onBlindSelectingChange: (BlindUiState) -> Unit = { },
    // v0.68.64：把 controller 交给 MainScreen——退出编辑态时调 exitEditMode()
    // 清钉住并同步状态（MainScreen 直接 Hidden 会导致 HomeScreen 副本残留）
    onBlindController: (BlindCaptureController) -> Unit = {},
    // v0.68.32：底部 Dock 区高度（dp），用于扇形圆心钳制
    blindDockZoneDp: Float = 0f,
    // R2 UI：意图入口"去设置"导航（无 Key 时引导去配大模型 Key）
    onOpenSettings: () -> Unit = {},
    // R2 UI（10-21 新规格）：意图状态与回调——协调器由 HomeHost 持有（应用中心「问七仔」共用），
    // HomeScreen 只负责把状态交给胶囊渲染
    intentState: com.bobot.ailauncher.core.intent.IntentUiState,
    onIntentSubmit: (String) -> Unit,
    onIntentConfirm: (com.bobot.ailauncher.core.action.ActionRequest) -> Unit,
    onIntentDismiss: () -> Unit,
    // v0.68.98（F-06）：Planning 态取消（宿主真取消在途 Job），透传给 IntentEntry
    onIntentCancelPlanning: () -> Unit,
    // v0.68.99（主页重构 R-1）：输入态开关（HomeHost 持有）——静态主页无胶囊；
    // 点 Dock 坐姿七仔进入（v0.68.101 起为唯一入口——讲述者七仔已撤），
    // 协调器在场状态由 HomeHost 自动带起
    intentInputMode: Boolean = false,
    // 进入输入态时是否挂载即聚焦（点入口进入 = true 要打字；自动带起呈现 = false 不弹键盘）
    intentRequestFocus: Boolean = false,
    onExitIntentInput: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    // v0.68.93（布局与交互 Loop 一批①）：点胶囊外释放焦点的宿主侧状态——
    // 胶囊聚焦与否由 IntentEntry 经 onFocusChanged 外报，胶囊范围用包裹 Box 的
    // boundsInRoot 捕获，根节点原点用 positionInRoot 换算本地坐标（观察者见根 Box）。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var intentFocused by remember { mutableStateOf(false) }
    var intentBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var rootPosInRoot by remember {
        mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }

    // v0.68.99（主页重构 R-1）：意图输入文本由本宿主持有——IntentEntry 随
    // 输入态挂载/卸载，文本放宿主则收起再进入不吞字（沿 .93 口径）。
    var intentFieldValue by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    }
    // 输入态退出判定（v0.68.101·D-2 重构，取代「失焦边沿推断 + 4 秒盲
    // 兜底」）：旧实现把「聚焦过→失焦」当退出信号，真机实证（Bob 录屏
    // 两幕，缺陷文档 D-2）输入会话在进态约 4~5 秒无故退出/收键盘——
    // 切 IME 面板、删字期间的瞬时焦点事件在真机 IME 下时序不可靠，任何
    // 「看焦点猜意图」的推断都会误伤会话。新口径下退出只由用户动作事件
    // 与协调器状态落定驱动（打字/切面板/删到空都不碰这两条通道）：
    //  ① 点外：根观察者真实 Press 事件直接回调退出（见根 Box 的
    //     onOutsideTap，非在场状态时）；② Back（下方 BackHandler）；
    //  ③ 提交后落定：在场状态退场且未聚焦 → 收起；④ 点本地结果/建议
    //     应用开 App（IntentEntry 的 onRequestExitInput）；⑤ 开抽屉。
    // 「曾经聚焦过」锁存只供 ⑥ 滞留兜底，不再直接触发退出。
    var intentHasFocused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(intentFocused) {
        if (intentFocused) intentHasFocused = true
    }
    androidx.compose.runtime.LaunchedEffect(intentInputMode) {
        if (intentInputMode) intentHasFocused = false
    }
    // ---- 输入态专注·进出过渡 C（PetPerchTransition，2026-10-26） ----
    // 过渡状态由本宿主持有，进出两路共用（见 PetPerchTransition.kt 的
    // 四步时序与责任边界）。onExitFocusRequest 是本宿主向下暴露的单一
    // 退出入口：所有退出路径只调它，它跑完四步退态才调真正的
    // onExitIntentInput（即 MainScreen 的 mode 翻转）；D 的 ✕ 一键退出
    // 集成时也只调这个入口（协调方须把它透传给专注卡片的 ✕ 按钮）。
    // 槽位挂载条件因此是 intentInputMode || isExiting：退态进行中 mode
    // 尚未翻转、翻转后槽位仍挂到宠物落地才松开，意图宠物才有机会沿
    // 镜像路径走回去，而不是随 mode 翻转瞬间消失。
    val perchTransition = remember { PetPerchTransitionState() }
    val perchScope = rememberCoroutineScope()
    val reduceMotionHome = rememberReduceMotion()
    val exitIntentInputLatest by rememberUpdatedState(onExitIntentInput)
    // IME 底部高度提前到此处（下方 BackHandler 复用同一值）：退态第①步
    // 等它归零才起步行程，WindowInsets.ime 变化会触发重组、值随之更新。
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomLatest by rememberUpdatedState(imeBottomPx)
    // 点外退出走 pointerInput(Unit)（首帧闭包常驻）：护栏不能读参数原值
    // （首帧 intentInputMode 恒 false，退出永不触发），必须读最新态。
    val intentInputModeLatest by rememberUpdatedState(intentInputMode)
    fun requestExitFocus() {
        if (perchTransition.isExiting || !intentInputModeLatest) return
        perchScope.launch {
            perchTransition.beginExit()
            // ① 键盘落下：清焦点＋收 IME（各路调用方多半已做过，幂等重做
            // 无害）；最多等 350ms 让 IME 底部归零，等不到也继续、不阻塞退态。
            focusManager.clearFocus()
            keyboardController?.hide()
            var waited = 0
            while (imeBottomLatest > 0 && waited < 22) {
                delay(16)
                waited++
            }
            // ② 宠物沿镜像路径走回 Dock 肩峰：同一弹簧、进态冻结位移量取负。
            val travelJob = launch {
                perchTransition.animateExitTravel(reduceMotionHome)
            }
            var dockFadeStarted = false
            // suspend：减动效分支里直接 snapTo（suspend），故本函数也须 suspend。
            suspend fun startDockFade() {
                if (dockFadeStarted) return
                dockFadeStarted = true
                perchTransition.markDockFade()
                // ③ Dock 卡片淡入＋④ 首页淡入：两者都由 intentInputMode 翻转
                // 驱动（PullUpDock cardVisible 门禁、PetZone inputActive 回落），
                // 故翻转点即两者的起步点；意图宠物 alpha 在此另起 180ms 交接
                // 淡出，与 Dock 卡片 enter fadeIn（实测 180ms）交叠，见状态文件。
                exitIntentInputLatest()
                if (reduceMotionHome) {
                    perchTransition.travelAlpha.snapTo(0f)
                } else {
                    perchScope.launch {
                        perchTransition.travelAlpha.animateTo(
                            0f,
                            androidx.compose.animation.core.tween(180)
                        )
                    }
                }
            }
            if (!reduceMotionHome) {
                // 行程过 70%（travel ≤ 0.30）时触发 Dock 淡入＋首页淡入。
                while (travelJob.isActive && perchTransition.travel.value > 0.30f) {
                    delay(8)
                }
                if (travelJob.isActive) startDockFade()
            }
            travelJob.join()
            // 兜底：极小位移等情况下行程起步已 ≤ 0.30、循环未触发时，落地后补翻转。
            if (!dockFadeStarted) startDockFade()
            // 宠物已落地、Dock 已淡入、首页已回场：松开槽位挂载，槽位按原
            // fadeOut（Fast 150ms）收尾——面板淡出发生在宠物离场之后，不抢交接。
            perchTransition.finishExit()
        }
    }
    val onExitFocusRequest: () -> Unit = { requestExitFocus() }
    val intentStateLatest by rememberUpdatedState(intentState)
    // ③ 状态落定退出（纯函数 intentSettleShouldExit，单测锁死矩阵）
    var intentPrevHolds by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(intentState, intentFocused, intentInputMode) {
        val holdsNow = intentStateHoldsInput(intentState)
        if (intentSettleShouldExit(intentInputMode, intentPrevHolds, holdsNow, intentFocused)) {
            // 落定退出同样走过渡 funnel（反向位移），不直接翻转 mode。
            onExitFocusRequest()
        }
        intentPrevHolds = holdsNow
    }
    // ⑥ 滞留兜底：进态 2.5 秒后仍「从未聚焦 + 文本为空 + 无在场状态」
    // 才收起——防聚焦请求被吞后空胶囊永久滞留。真实会话必先聚焦（锁存
    // 置位）或已有文本，永不误伤；它只收「根本没开始过」的会话，与旧
    // 4 秒盲兜底的区别正在于三重门槛（旧版单看未聚焦即在会话中误伤）。
    val intentFieldTextLatest by rememberUpdatedState(intentFieldValue.text)
    // v0.68.102（310 冻结修复二轮·进态错峰）：聚焦（= IME 首秀 + 窗口
    // resize）延迟到进态合成落定后才放行。CI 探针实证（probe-freeze-*，
    // 2026-10-24）：进态首帧的合成爆发（槽位淡入 + hero 紧凑 + 伴随七仔
    // 挂载）与 IME 首秀在同一时间窗叠加时整机图形栈死亡（模拟器进程级
    // 消失、logcat 无宾客侧 FATAL）——冷 IME（开机首秀）与深色首帧各为
    // 一个加重因子，任一与进态同窗即点火；应用中心单独聚焦（无进态
    // 合成）与错峰后的进态均存活。故把链条串行化：胶囊先挂载落定
    // （含 +300ms 的伴随七仔），450ms 后才放行聚焦，IME 在静默窗外弹起。
    // 呈现带起（intentRequestFocus=false）本就不聚焦，不受影响；滞留
    // 兜底的 2.5s 阈值远大于 450ms，不误伤。
    var intentFocusGateOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(intentInputMode) {
        intentFocusGateOpen = false
        if (intentInputMode) {
            delay(450)
            intentFocusGateOpen = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(intentInputMode) {
        if (intentInputMode) {
            delay(2500)
            if (intentInputStranded(
                    intentHasFocused, intentFieldTextLatest.isEmpty(),
                    intentStateHoldsInput(intentStateLatest)
                )
            ) {
                // 滞留兜底同样走过渡 funnel（反向位移），不直接翻转 mode。
                onExitFocusRequest()
            }
        }
    }

    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.68.0：盲操状态
    var blindUi by remember { mutableStateOf<BlindUiState>(BlindUiState.Hidden) }
    // v0.68.39：诊断浮层已删除（v0.68.12 屏上诊断使命结束）
    // v0.68.6：盲操激活状态上报 MainScreen——选择中时压住右边缘字母快找区，
    // 避免长按（~400ms）与盲操 dwell（120ms）双重触发
    // v0.68.62：编辑态也算激活（扇形钉住时 TODAY 保持淡出、边缘字母区保持压住）
    val blindActive = blindUi is BlindUiState.Selecting || blindUi is BlindUiState.Editing
    androidx.compose.runtime.LaunchedEffect(blindActive) {
        onBlindActiveChange(blindActive)
    }
    // v0.68.17：落点跟踪已删除（不再需要边缘区检查）
    // v0.68.35：旧"自定义☆"固定槽位已作废——扇形快捷段直接读七仔装备列表，
    // 增删复用七仔装备长按/+ 流程，不再需要扇形侧配置弹窗
    // v0.68.32：扇形选择状态上报 MainScreen（扇形在 MainScreen 最顶层绘制，盖住 Dock）
    // v0.68.42：上报全状态（Selecting/Dismissing/PhotoFlash）；收起动画 160ms 后通知
    // controller 收尾（动画本身 120ms，留余量）
    val blindFanState = blindUi
    androidx.compose.runtime.LaunchedEffect(blindFanState) {
        onBlindSelectingChange(blindFanState)
    }
    // v0.68.17：右边缘系统手势豁免宽度（仅用于 setSystemGestureExclusionRects，
    // 盲操触发已改为"任何左滑"，不再依赖边缘区）
    val blindEdgePx = with(density) { 48.dp.toPx() }
    // holder 打破 launcher 回调 ↔ controller 的循环引用（v0.68.40：录音删除后不再需要）
    val blindCtl = remember {
        BlindCaptureController(
            context = context,
            hapticView = view,
            setUi = { blindUi = it },
            triggerPx = with(density) { 24.dp.toPx() },
        )
    }
    // v0.68.64：编辑钉住时手势层移交扇形自身（点×/＋/拖拽），controller 不再接管 down/move/up
    androidx.compose.runtime.LaunchedEffect(blindUi) {
        blindCtl.editPinned = blindUi is BlindUiState.Editing
    }
    // v0.68.64：上报 controller（MainScreen 退出编辑态时清钉住）
    androidx.compose.runtime.LaunchedEffect(blindCtl) {
        onBlindController(blindCtl)
    }
    // v0.68.0：右边缘系统手势豁免（响应 cleanHome 变化）
    // v0.68.42：收起动画 160ms 后通知 controller 收尾（动画本身 120ms，留余量）
    val isDismissing = blindUi is BlindUiState.Dismissing
    androidx.compose.runtime.LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(160)
            blindCtl.onDismissFinished()
        }
    }
    var homeSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    androidx.compose.runtime.LaunchedEffect(cleanHome, homeSize) {
        // 干净桌面时豁免右边缘系统手势（桌面上系统返回是空操作）；
        // D3 抽屉等打开时不豁免，保证返回手势正常
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && homeSize.width > 0) {
            view.systemGestureExclusionRects = if (cleanHome) {
                val e = blindEdgePx.toInt().coerceAtLeast(1)
                listOf(Rect(homeSize.width - e, 0, homeSize.width, homeSize.height))
            } else {
                emptyList()
            }
        }
    }
    // v0.27.3：上滑阈值降低（120dp→48dp），更容易触发 D3
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    // 斜滑方向判定：x 偏移超过此值才算"往左/往右"
    val xBiasPx = with(density) { 24.dp.toPx() }
    // v0.68.32：底部 Dock 区高度同步给盲操控制器（圆心钳制用）
    androidx.compose.runtime.LaunchedEffect(blindDockZoneDp) {
        blindCtl.dockZonePx = with(density) { blindDockZoneDp.dp.toPx() }
    }
    // Toast 防抖：2 秒内只提示一次
    val hintShownAt = remember { mutableStateOf(0L) }
    // v0.36.0：权限引导卡状态——null 不显示，0 整卡，1 轻提醒
    var panelGuideMode by remember { mutableStateOf<Int?>(null) }

    fun goAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) { }
    }

    fun ensurePanelService(): Boolean {
        if (PanelAccessibilityService.isEnabled(context)) return true
        // v0.36.0：第一次先弹 App 内说明卡，别直接把用户扔进系统设置
        if (!UiPrefs.hasSeenPanelGuide(context)) {
            panelGuideMode = 0
        } else if (!UiPrefs.hasRemindedPanelGuide(context)) {
            panelGuideMode = 1
        } else {
            val now = System.currentTimeMillis()
            if (now - hintShownAt.value > 2000) {
                hintShownAt.value = now
                IslandToasts.show("请在无障碍设置中开启 AI 桌面，以使用下滑手势")
                goAccessibilitySettings()
            }
        }
        return false
    }

    // 通知监听重绑
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isNotificationAccessGranted(context) &&
                    !NotificationRepository.isConnected.value
                ) {
                    rebindListener(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // v0.31.0：首屏只留 Dock（Bob），日历/宠物逻辑待重想，先移除

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                homeSize = coords.size
                rootPosInRoot = coords.positionInRoot()
            }
            // v0.68.93：点意图区外任意处 → 释放焦点 + 收键盘（不消费事件，
            // 拖拽/点按/扇形全部照旧；文本保留不动）
            // v0.68.101（D-2）：点外同时直接退出输入态（真实用户动作事件，
            // 取代旧「失焦后由 Effect 推断退出」）——协调器在场状态持有
            // 输入框时不退（确认/追问/回复呈现中点外只收键盘，用户看完
            // 还要能接着答；其落定退出走状态通道）。
            .clearIntentFocusOnOutsideTap(
                focused = { intentFocused },
                targetBoundsInHost = {
                    intentBoundsInRoot?.let { b ->
                        androidx.compose.ui.geometry.Rect(
                            b.left - rootPosInRoot.x,
                            b.top - rootPosInRoot.y,
                            b.right - rootPosInRoot.x,
                            b.bottom - rootPosInRoot.y
                        )
                    }
                },
                focusManager = focusManager,
                keyboardController = keyboardController,
                onOutsideTap = {
                    if (!intentStateHoldsInput(intentStateLatest)) {
                        // 点外退出走过渡 funnel（反向位移），不直接翻转 mode。
                        onExitFocusRequest()
                    }
                }
            )
            // v0.68.17：落点跟踪已删除（触发改为"任何左滑"，不再需要真实落点）
            .pointerInput(blindCtl) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f; totalY = 0f
                        blindCtl.onStart()
                    },
                    onDragEnd = {
                        // v0.68.0：盲操消费掉的手势不再走纵向逻辑
                        if (!blindCtl.onEnd()) {
                            when {
                                // 上滑 → D3（y 为负是上滑）
                                totalY < -swipeThresholdPx -> {
                                    openDrawerState()
                                }
                                // 下滑 → 按 x 偏向决定
                                totalY > swipeThresholdPx -> {
                                    when {
                                        totalX < -xBiasPx -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openControlCenter()
                                        }
                                        totalX > xBiasPx -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openNotificationCenter()
                                        }
                                        // 纯下滑：默认通知中心
                                        else -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openNotificationCenter()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = { blindCtl.onCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        // v0.68.17：盲操跟踪（首页背景左滑/右滑直接触发扇形，右滑为镜像）
                        blindCtl.onMove(change, dragAmount, totalX, totalY, size)
                    }
                )
            }
    ) {
        // 输入态专注壳·A：专注态实色底——只在 intentInputMode 时出现，
        // 放在根容器最底层（Column 之前），壁纸完全不透，不做压暗/模糊纱
        // （351 教训）。fade-only 150ms（MotionTokens.Fast），不改尺寸、
        // 不触发布局；无点击处理，不挡卡片/宠物/键盘/触控；状态栏是系统
        // chrome，不受影响；顶部避让仍走 Column 既有规则。
        // 浅色 #E8E2D9（设计 §1）；深色用深色主题同系暖灰 token
        // AILauncherColors.current.Background（深色值 0xFF1A1815），不另发明色值。
        // 注意：本层在内容之下，只负责底色；简报/会议行的淡出在 PetZone.kt
        // （节点宿主，见另一次提交），HomeScreen 内无此节点可改。
        androidx.compose.animation.AnimatedVisibility(
            visible = intentInputMode,
            enter = androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                    easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                )
            ),
            exit = androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                    easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (AILauncherColors.current.isDark)
                            AILauncherColors.current.Background
                        else Color(0xFFE8E2D9)
                    )
            )
        }
        // v0.43.1：宠物回首页——v0.31.0 按"只留 Dock"把 PetZone 从首页移除后，
        // v0.43.0 的七仔场景改的是一段从未被调用的代码，所以真机上看不到。
        // v0.65.8：顶部对齐（之前居中导致宠物上方大量空白）——七仔贴近状态栏下方，
        // TODAY 卡片和应用图标整体上移，首屏显示更多内容。
        // 横屏刘海/状态栏避让（Bob 2026-10-25）：此前根容器只有固定 16dp 顶距、
        // 无任何显式 inset——横屏下挖孔移到左右两侧边，hero 固定 28dp 边距顶不到
        // 横屏 displayCutout 侧边 inset（打孔屏常 30-40dp+），顶端内容会顶到挖孔区。
        // 修法：两侧直接吃 displayCutout inset——竖屏侧边 inset 为 0，竖屏布局零变化；
        // 顶部横屏取 max(16dp, 状态栏高)，竖屏保持 16dp 不动。
        val isLandscapeHome = maxWidth > maxHeight
        val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        // 输入态专注·集成：专注组底边锚定——adjustResize 下窗口底边 =
        // 键盘顶边（键盘升起）或屏底（键盘收起）；结果态导航栏 clearance
        // 取栏高与 16dp 地板的大值（与 Dock 底垫同口径）。
        val navClearanceDp = with(density) {
            maxOf(16.dp, WindowInsets.navigationBars.getBottom(density).toDp())
        }
        // 专注组在场：进专注态起、退态完成止（isExiting 期间槽位仍挂载，
        // 锚定须保留，否则翻转瞬间卡片跳回顶部、反向位移无从渲染）。
        val focusGroupVisible = intentInputMode || perchTransition.isExiting
        // 输入态专注·修复（2026-10-26，356 真机反馈）：组底边改走显式垫高，
        // 不再只依赖窗口收缩。356 的锚定是权重 Spacer + adjustResize：窗口
        // 不收缩时等于没锚。Bob 机上横屏截图证实键盘是悬浮式（右侧圆角浮
        // 窗、压住卡片右侧）——悬浮键盘既不触发窗口收缩、也不产生底部 IME
        // inset，两条旧路径同时失效，卡片留在屏底被键盘盖住；同机竖屏同
        // 一键盘模式，竖屏“没有推起来”与此同根（竖屏截图本身无键盘在场，
        // 故竖屏机制由横屏证据 + 代码链完整性推断：三环逐一核过——Manifest
        // 是 adjustResize、无运行时覆盖、宿主链 Box→NavHost→HomeScreen 全
        // fillMaxSize、无 Popup、无 inset 消费，收缩链本身无断点）。
        // 四路分流（纯函数 resolveFocusBottomPad，单测锁死）：
        // ① 停靠键盘且窗口未收缩（imeBottom > 0）：垫 = imeBottom，卡片精确
        //    坐在键盘顶边，0dp 贴合；
        // ② 停靠键盘且窗口已收缩（窗口高 < 屏高 − 48dp，imeBottom 归零）：
        //    垫 = 0，Spacer 已把卡片推到窗口底边即键盘顶边（356 在此情形下
        //    误加了导航垫、卡片会浮在键盘上方 16dp，本批一并修正）；
        // ③ 悬浮键盘（键盘在场预期成立、窗口未收缩、imeBottom = 0）：竖屏
        //    垫 = 窗口高 × 50%，把组顶到屏幕上半区、躲开底部悬浮键盘（悬浮
        //    键盘位置／尺寸系统不提供，50% 是保守兜底：覆盖底部半屏内任意
        //    高度的悬浮键盘；被用户拖到屏幕中部的悬浮键盘仍可能压住，此为
        //    系统能力边界、如实标注）；
        // ④ 其余（键盘收起／结果态）：垫 = 导航栏 clearance（356 原口径不变）。
        // 键盘在场预期 = 输入态且（胶囊聚焦，或 Planning／NeedsInput——这两
        // 态键盘按设计保持升起）；Reply／Error 等态键盘按设计已收，走 ④。
        val imeBottomDp = with(density) { imeBottomPx.toDp() }
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val windowResized = maxHeight < screenHeightDp - 48.dp
        val keyboardExpected = intentInputMode && (intentFocused ||
            intentState is com.bobot.ailauncher.core.intent.IntentUiState.Planning ||
            intentState is com.bobot.ailauncher.core.intent.IntentUiState.NeedsInput)
        val focusBottomPadResolved = resolveFocusBottomPad(
            imeBottom = imeBottomDp,
            windowResized = windowResized,
            keyboardExpected = keyboardExpected,
            isPortrait = !isLandscapeHome,
            windowHeight = maxHeight,
            navClearance = navClearanceDp
        )
        // 退态期间冻结垫高：退出起步先翻 isExiting、后清焦点（同一 funnel
        // 内 beginExit 在 clearFocus 之前），不冻结则清焦点后
        // keyboardExpected 翻 false、垫高瞬间掉回导航垫，卡片在宠物起步前
        // 跳变（悬浮竖屏约 50% 窗口高），反向位移无从渲染；冻结后卡片原地
        // 不动、宠物从原位走回 Dock，退态结束（isExiting 翻 false）后恢复
        // 按信号计算。
        var frozenExitPad by remember { mutableStateOf<Dp?>(null) }
        androidx.compose.runtime.LaunchedEffect(focusBottomPadResolved, perchTransition.isExiting) {
            if (!perchTransition.isExiting) frozenExitPad = focusBottomPadResolved
        }
        val focusBottomPad = if (perchTransition.isExiting) {
            frozenExitPad ?: navClearanceDp
        } else {
            focusBottomPadResolved
        }
        // 横屏悬浮键盘：卡片改左区布局（见 IntentEntry 的 landscapeFloating），
        // 躲开右侧悬浮键盘；停靠键盘／键盘收起时仍用 520dp 居中（停靠键盘
        // 占满底边、无左右遮挡）。
        val landscapeFloating = isLandscapeHome && keyboardExpected &&
            !windowResized && imeBottomPx == 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(
                        WindowInsetsSides.Start + WindowInsetsSides.End
                    )
                )
                .padding(top = if (isLandscapeHome) maxOf(16.dp, statusBarTopDp) else 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 输入态专注·集成：权重 Spacer 把专注态的 PetZone（可见内容
            // 只剩意图卡片）推到窗口底边——adjustResize 下窗口底边即键盘
            // 顶边，组随键盘同进同退；进态冻结位移量在布局后实测锚点，
            // 故卡片落底不影响宠物起步落点。
            if (focusGroupVisible) {
                Spacer(modifier = Modifier.weight(1f))
            }
            // v0.68.44（Bob）：杂志封面模式
            // v0.68.96（F-01 退役）：PetZone 的非封面分支已删除，todayCardAlpha
            // 参数随之移除——该淡出原本只作用于已退役的 TODAY 卡，封面形态从未吃到。
            PetZone(
                // v0.68.101（单宠物原则）：讲述者七仔撤出（PetZone 已纯文字
                // 简报）——进输入的唯一门是 Dock 上沿坐姿七仔；inputActive
                // 驱动简报紧凑（D-1）与横屏 hero 让位。
                inputActive = intentInputMode,
                // v0.68.99（R-1 修订）：输入态填充 PetZone 的 intentSlot（.97
                // 槽位：hero 与会行之间、横屏左区）——静态时 AnimatedVisibility
                // 零高度、封面只剩简报+Dock；包裹 Box 捕获意图区范围供 .93
                // 点外部收焦观察者用。早先的底部浮层在横屏跨进 hero 区
                // （矩阵 landphone 实测 overlap），流式槽位靠分区天然不重叠。
                intentSlot = {
                    // v0.68.102（310 冻结修复·入场削峰）：槽位进出只走 fade，
                    // 不再 expandVertically/shrinkVertically——高度动画会让
                    // 简报列在进态首帧逐帧重测（与 IME resize、hero 紧凑、
                    // 伴随七仔首次挂载叠加成布局风暴，CI 实证进态后进程
                    // 死亡/楔死、screencap 与层级读取全失效）。fade 不改变
                    // 尺寸，槽位一帧到位，语义与呈现内容不变。
                    androidx.compose.animation.AnimatedVisibility(
                        // 退态进行中仍挂载（mode 已翻转、宠物未落地前不卸载），
                        // 否则意图宠物随翻转瞬间消失、反向位移无从渲染。
                        visible = intentInputMode || perchTransition.isExiting,
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .onGloballyPositioned {
                                    intentBoundsInRoot = it.boundsInRoot()
                                }
                        ) {
                            // F 批（输入态轻量化）：胶囊左侧 Row 站位退役
                            // ——IntentEntry 独占整宽；2026-10-25 意图卡片统一：
                            // 七仔改栖坐意图卡片上沿（Dock 宠物本体 HybridPet
                            // 换 perch，全屏唯一、testTag pet_qizai 不变）；
                            // 整区在意图区 bounds 内，点它不触发
                            // 点外退出。
                            // freeze3 焦点门（§9 削峰）口径保留：requestFocusOnMount
                            // 仍须过 intentFocusGateOpen。
                            IntentEntry(
                                state = intentState,
                                onSubmit = onIntentSubmit,
                                onConfirm = onIntentConfirm,
                                onDismiss = onIntentDismiss,
                                onCancelPlanning = onIntentCancelPlanning,
                                onOpenSettings = onOpenSettings,
                                onOpenPermissionSettings = { perm ->
                                    openPermissionSettings(context, perm)
                                },
                                onFocusChanged = { intentFocused = it },
                                fieldValue = intentFieldValue,
                                onFieldValueChange = { intentFieldValue = it },
                                requestFocusOnMount = intentRequestFocus && intentFocusGateOpen,
                                // 退出 funnel：IntentEntry 的显式退出（含 D 的 ✕
                                // 集成时透传的入口）与旧 onRequestExitInput 同走
                                // onExitFocusRequest，不再直通 onExitIntentInput。
                                perchTransition = perchTransition,
                                onExitFocusRequest = onExitFocusRequest,
                                focusBubbleActive = intentInputMode,
                                landscapeFloating = landscapeFloating,
                                onRequestExitInput = onExitFocusRequest,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            )
            // 输入态专注·修复：组底边垫高见上方四路分流（resolveFocusBottomPad），
            // 退态期间冻结值——此处不再按 imeBottomPx == 0 一刀切（窗口已收缩
            // 时 imeBottom 同样归零，356 在此误加导航垫、卡片浮在键盘上方）。
            if (focusGroupVisible) {
                Spacer(modifier = Modifier.height(focusBottomPad))
            }
        }
        // v0.68.99：返回退出输入态——IME 在场时不启用（那一按归输入法收键盘，
        // 与既有「BACK 先收键盘」口径一致，冒烟 §9/§10 依赖此序）；有在场状态
        // 时也不启用（思考/回复不被 Back 打断，走各自收起路径）。
        // imeBottomPx 复用顶部已提升的值（退态第①步的 IME 归零等待同源）。
        // Back 进行中重复按下由 requestExitFocus 的 isExiting 护栏挡下，
        // 故此处不另加 isExiting 到 enabled（禁用会把 Back 交给系统，危险）。
        androidx.activity.compose.BackHandler(
            enabled = intentInputMode && imeBottomPx == 0 &&
                !intentStateHoldsInput(intentState)
        ) { onExitFocusRequest() }
        // v0.36.0：无障碍权限引导卡
        panelGuideMode?.let { mode ->
            PanelPermissionGuide(
                compact = mode == 1,
                onGoSettings = {
                    if (mode == 0) UiPrefs.setPanelGuideSeen(context)
                    else UiPrefs.setPanelGuideReminded(context)
                    panelGuideMode = null
                    goAccessibilitySettings()
                },
                onDismiss = {
                    if (mode == 0) UiPrefs.setPanelGuideSeen(context)
                    else UiPrefs.setPanelGuideReminded(context)
                    panelGuideMode = null
                }
            )
        }
        // v0.68.32：扇形选择器已移到 MainScreen 最顶层绘制（盖住 Dock）
        // v0.68.39：诊断浮层已删除
    }
}

/**
 * v0.36.0：下滑手势的无障碍权限引导卡——半透明遮罩 + 白卡片（跟行左滑引导同风格）。
 * 文案跟手势用途直接挂钩，不说通用话术。
 */
@Composable
private fun PanelPermissionGuide(
    compact: Boolean,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (compact) "还没开启权限" else "下滑手势需要开个权限",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
                Text(
                    text = if (compact)
                        "无障碍里的「AI 桌面」还没开，下滑手势暂时用不了。"
                    else
                        "下滑打开通知中心、斜滑打开控制中心，都要借用系统的无障碍能力。请在无障碍设置中开启「AI 桌面」，只用来帮你打开这两个面板，不会读取任何内容。",
                    fontSize = 13.sp,
                    color = AILauncherColors.current.Body
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (compact) "知道了" else "暂不",
                            color = AILauncherColors.current.Hint
                        )
                    }
                    TextButton(onClick = onGoSettings) {
                        Text(
                            text = "去开启",
                            color = AILauncherColors.current.Accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.68.99（主页重构 R-1）：意图「在场状态」——这些状态下输入胶囊必须留在
 * 屏上（思考中 / 待确认 / 缺权限 / 在追问 / 回复或错误待用户处理），
 * 输入态不退出。Idle / Editing（无事发生）不算在场。
 */
internal fun intentStateHoldsInput(
    state: com.bobot.ailauncher.core.intent.IntentUiState
): Boolean = when (state) {
    is com.bobot.ailauncher.core.intent.IntentUiState.Planning,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsConfirmation,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsPermission,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsInput,
    is com.bobot.ailauncher.core.intent.IntentUiState.Reply,
    is com.bobot.ailauncher.core.intent.IntentUiState.Error -> true
    is com.bobot.ailauncher.core.intent.IntentUiState.Idle,
    is com.bobot.ailauncher.core.intent.IntentUiState.Editing -> false
}

/**
 * 输入态专注·修复（2026-10-26，356 真机反馈）：专注组底边垫高决策（纯函数，单测锁死）。
 * 四路按序分流，互斥且全覆盖——调用方只传信号，不在这里再算窗口／键盘状态：
 *  - imeBottom > 0：停靠键盘且窗口未收缩，垫 = 键盘高，卡片精确坐键盘顶边（0dp 贴合）；
 *  - windowResized：停靠键盘且窗口已收缩（imeBottom 已归零），垫 = 0，权重 Spacer
 *    已把卡片推到窗口底边即键盘顶边（356 在此误加导航垫的回归锁）；
 *  - keyboardExpected && isPortrait：悬浮键盘兜底（窗口未收缩、imeBottom = 0、键盘
 *    在场预期成立），垫 = 窗口高 × 50%，把组顶到屏幕上半区；
 *  - 其余：键盘收起／结果态，垫 = 导航栏 clearance（356 原口径）。
 * 注意：悬浮键盘的横屏情形不走本函数（横屏垫仍为导航垫，避让改走卡片左区布局，
 * 见 resolveLandscapeCardWidth），故 isPortrait 门只管竖屏兜底。
 */
internal fun resolveFocusBottomPad(
    imeBottom: Dp,
    windowResized: Boolean,
    keyboardExpected: Boolean,
    isPortrait: Boolean,
    windowHeight: Dp,
    navClearance: Dp,
): Dp = when {
    imeBottom > 0.dp -> imeBottom
    windowResized -> 0.dp
    keyboardExpected && isPortrait -> windowHeight * 0.5f
    else -> navClearance
}

/**
 * 输入态专注·修复（2026-10-26，356 真机反馈）：横屏卡片宽决策（纯函数，单测锁死）。
 *  - landscapeFloating = false：沿用 356 口径，封顶 520dp 居中；
 *  - landscapeFloating = true：左区布局，宽 = min(520dp, 屏宽 × 50%)——按 Bob 横屏
 *    截图实测校准：其悬浮键盘左缘约在屏宽 52.6% 处，50% 封顶使卡片内容右缘
 *    （卡片宽 − 16dp 内容边距）落在键盘左缘左侧；仅底部 24dp 外延区右尖可能
 *    触到键盘圆角（无内容，不改外延几何）。被用户改大／拖位的悬浮键盘仍可能
 *    压住，此为系统不提供键盘位置下的能力边界。
 */
internal fun resolveLandscapeCardWidth(
    screenWidth: Dp,
    landscapeFloating: Boolean,
): Dp = if (landscapeFloating) {
    minOf(520.dp, screenWidth * 0.5f)
} else {
    minOf(520.dp, screenWidth - 40.dp)
}

/**
 * v0.68.101（D-2）：状态落定退出（纯函数，单测锁死）：在场状态从持有
 * 变不持有、且当前未聚焦 → 输入态收起。它是提交后生命周期的收口——
 * 用户提交后焦点已被释放，回复/确认呈现完自然落定关闭；打字不改变
 * 协调器状态，IME 焦点抖动无从触发本通道。
 */
internal fun intentSettleShouldExit(
    inputMode: Boolean,
    prevHolds: Boolean,
    holdsNow: Boolean,
    focused: Boolean
): Boolean = inputMode && prevHolds && !holdsNow && !focused

/**
 * v0.68.101（D-2）：滞留兜底（纯函数，单测锁死）：进态后从未聚焦、
 * 文本为空、无在场状态——三条全中才判定「会话根本没开始过」，允许
 * 延迟收起。从未聚焦是进入会话的必经之路（打字前必聚焦），故真实
 * 会话永不命中；旧 intentInputShouldExit（失焦边沿推断）已删除。
 */
internal fun intentInputStranded(
    hasFocused: Boolean,
    textEmpty: Boolean,
    holdsNow: Boolean
): Boolean = !hasFocused && textEmpty && !holdsNow
