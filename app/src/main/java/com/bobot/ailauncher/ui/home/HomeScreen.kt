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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.ui.theme.AILauncherType
import kotlinx.coroutines.delay
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.service.LauncherNotificationService
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import com.bobot.ailauncher.service.PanelAccessibilityService
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.ui.smartcard.SmartCardStackHost
import com.bobot.ailauncher.ui.theme.AILauncherColors
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
/**
 * v0.68.169（盲操扇形找回）：盲操手势仲裁谓词——手势起点落在意图卡片
 * 矩形或 Dock 矩形内时不转发盲操。纯函数，单测锁死。374 批的教训：
 * 谓词本身没错，错的是传入的 Dock 矩形——PullUpDock 外层 modifier 根为
 * fillMaxSize，捕获的整屏矩形使本谓词对一切起点都返回 true、扇形全屏失效。
 * 449/v1.2.2：智能卡片叠层矩形也排除在外——叠层横滑是它自己的手势，
 * 绝不能再触发扇形（448 实拍：横滑卡片时扇形“拍照”弹出、还打开了相机）。
 */
internal fun blindStartExcluded(
    start: androidx.compose.ui.geometry.Offset,
    intentRect: androidx.compose.ui.geometry.Rect?,
    dockRect: androidx.compose.ui.geometry.Rect?,
    cardStackRect: androidx.compose.ui.geometry.Rect? = null,
): Boolean =
    (intentRect?.contains(start) == true) ||
        (dockRect?.contains(start) == true) ||
        // 叠层为空时槽位零高度、bounds 零面积：isEmpty 守卫，不误拦背景手势
        //（不依赖 Rect.contains 对零面积矩形的边界语义）。
        (cardStackRect?.let { !it.isEmpty && it.contains(start) } == true)

@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit,
    cleanHome: Boolean = true,
    onBlindActiveChange: (Boolean) -> Unit = {},
    // v0.68.32：扇形选择状态上报 MainScreen（扇形绘制层级提到 Dock 之上）
    // v0.68.35：Selecting 自带动态段列表，不再传自定义标签
    // v0.68.42：上报全状态（Selecting/Dismissing 共用顶层绘制）
    onBlindSelectingChange: (BlindUiState) -> Unit = { },
    // v0.68.64：把 controller 交给 MainScreen——退出编辑态时调 exitEditMode()
    // 清钉住并同步状态（MainScreen 直接 Hidden 会导致 HomeScreen 副本残留）
    onBlindController: (BlindCaptureController) -> Unit = {},
    // v0.68.32：底部 Dock 区高度（dp），用于扇形圆心钳制
    blindDockZoneDp: Float = 0f,
    // v0.68.165（瘦条批）：Dock 精确 bounds（MainScreen 侧 onGloballyPositioned
    // 捕获，D3 时传 null）——盲操手势仲裁用：Dock 是前景 UI 不是主页背景，
    // 其区内起始的手势不转发盲操（Dock 横滑已随 370 删除，扇形不再认 Dock 区）。
    dockBoundsInRoot: androidx.compose.ui.geometry.Rect? = null,
    // R2 UI：意图入口"去设置"导航（无 Key 时引导去配大模型 Key）
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    // 449/v1.2.2（智能卡片叠层手势仲裁）：叠层范围用卡片槽位包裹 Box 的
    // boundsInRoot 捕获（与意图/Dock 同口径，见根 Box 观察者）；叠层为空
    // （无卡、无引导卡）时槽位零高度，bounds 是零面积矩形、contains 恒
    // false，不会误拦主页背景手势。
    var cardStackBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var rootPosInRoot by remember {
        mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
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
    // v0.68.35：旧"自定义☆"固定槽位已作废——扇形快捷段直接读快捷段列表，
    // 增删复用快捷段长按/+ 流程，不再需要扇形侧配置弹窗
    // v0.68.32：扇形选择状态上报 MainScreen（扇形在 MainScreen 最顶层绘制，盖住 Dock）
    // v0.68.42：上报全状态（Selecting/Dismissing）；收起动画 160ms 后通知
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
    // 监听翻 true 补推一次（Bob 2026-11-09 12:20 ①，与应用中心同款）：
    // Dock 角标画在首页，升级重启后用户先待在首页；首页 ON_RESUME 重绑
    // 也是异步 toggle，翻成 true 说明重绑落地了，这时再推一次角标，
    // 不只等下一条通知。只在 false→true 跳变时推一次，不循环。
    LaunchedEffect(Unit) {
        snapshotFlow { NotificationRepository.isConnected.value }
            .drop(1)
            .filter { it }
            .collect { LauncherNotificationService.requestBadgeRecompute() }
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
            // v0.68.17：落点跟踪已删除（触发改为"任何左滑"，不再需要真实落点）
            .pointerInput(blindCtl) {
                var totalX = 0f
                var totalY = 0f
                // v0.68.165（瘦条批）：盲操手势仲裁——盲操只认主页背景。
                // Dock 区内起始的手势不转发盲操：卡片内点滑、Dock 区手势
                // 都不再弹扇形／直开相机；纵向 fallback（上滑 D3／下滑通知）
                // 不受影响，仍按原逻辑走。
                // 449/v1.2.2：智能卡片叠层区也一样排除在外——叠层横滑是
                // 它自己的手势（detectHorizontalDragGestures 跳过沉底），
                // 父层再认一次就是 448 实拍的扇形＋相机。且叠层区的手势
                // 连纵向 fallback 也不走：斜滑带上分量时 fallback 会误开
                // 应用中心（448 视频里应用中心就是这么开的），叠层区的手
                // 势归叠层自己，纵向在叠层上本来就没意义。Dock 区的
                // 纵向 fallback 口径不动（只叠层区禁纵向）。
                var gestureExcludedFromBlind = false
                var gestureOnCardStack = false
                fun cardStackLocal(): androidx.compose.ui.geometry.Rect? =
                    cardStackBoundsInRoot?.let { b ->
                        androidx.compose.ui.geometry.Rect(
                            b.left - rootPosInRoot.x,
                            b.top - rootPosInRoot.y,
                            b.right - rootPosInRoot.x,
                            b.bottom - rootPosInRoot.y
                        )
                    }
                fun blindExclusionContains(start: androidx.compose.ui.geometry.Offset): Boolean {
                    val dockLocal = dockBoundsInRoot?.let { b ->
                        androidx.compose.ui.geometry.Rect(
                            b.left - rootPosInRoot.x,
                            b.top - rootPosInRoot.y,
                            b.right - rootPosInRoot.x,
                            b.bottom - rootPosInRoot.y
                        )
                    }
                    return blindStartExcluded(
                        start, null, dockLocal, cardStackLocal()
                    )
                }
                detectDragGestures(
                    onDragStart = { start ->
                        totalX = 0f; totalY = 0f
                        gestureOnCardStack = cardStackLocal()?.let {
                            !it.isEmpty && it.contains(start)
                        } == true
                        gestureExcludedFromBlind = blindExclusionContains(start)
                        if (!gestureExcludedFromBlind) blindCtl.onStart()
                    },
                    onDragEnd = {
                        // v0.68.0：盲操消费掉的手势不再走纵向逻辑；仲裁排除的
                        // 手势未起 blind 跟踪，不调 onEnd（恒按未消费走纵向逻辑）。
                        // 449：叠层区的手势除外——纵向也不走，归叠层自己。
                        val blindConsumed = !gestureExcludedFromBlind && blindCtl.onEnd()
                        if (!blindConsumed && !gestureOnCardStack) {
                            when {
                                // 上滑 → D3（y 为负是上滑）
                                totalY < -swipeThresholdPx -> {
                                    openDrawerState()
                                }
                                // 下滑 → 仅左下开控制中心；右下 / 纯下滑不做任何事
                                // （Bob 2026-11-08：桌面的下滑打开通知中心删除）
                                totalY > swipeThresholdPx -> {
                                    if (totalX < -xBiasPx) {
                                        if (ensurePanelService())
                                            PanelAccessibilityService.openControlCenter()
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = { if (!gestureExcludedFromBlind) blindCtl.onCancel() },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (!gestureExcludedFromBlind) {
                            change.consume()
                            // v0.68.17：盲操跟踪（首页背景左滑/右滑直接触发扇形，右滑为镜像）
                            blindCtl.onMove(change, dragAmount, totalX, totalY, size)
                        }
                        // 仲裁排除时不 consume：示例行横滚由行自身消费，
                        // 卡片内点滑不受父层干扰。
                    }
                )
            }
    ) {
        // 输入态专注（2026-10-26 本批，Bob 21:56：聚焦不用黑屏）。
        // 实色底已整个删除：聚焦态保留主页壁纸（取代 13:51“壁纸按纯色
        // 暖灰”口径），其他内容弱化或隐藏——简报／会议行经 PetZone 门控
        // 整体显隐（竖屏 None、横屏 fade）、竖屏 Dock 经 cardVisible 门
        // ＋宠物门全藏、横屏 Dock 作为宠物身体保留（见 PullUpDock）。
        // 此处不加任何纱：卡片自身玻璃保证可读；忙乱壁纸上的卡片可读性
        // 列为真机复核项。
        // v0.43.1：宠物回首页——v0.31.0 按"只留 Dock"把 PetZone 从首页移除后，
        // v0.65.8：顶部对齐（之前居中导致宠物上方大量空白）——简报贴近状态栏下方，
        // TODAY 卡片和应用图标整体上移，首屏显示更多内容。
        // 横屏刘海/状态栏避让（Bob 2026-10-25）：此前根容器只有固定 16dp 顶距、
        // 无任何显式 inset——横屏下挖孔移到左右两侧边，hero 固定 28dp 边距顶不到
        // 横屏 displayCutout 侧边 inset（打孔屏常 30-40dp+），顶端内容会顶到挖孔区。
        // 修法：两侧直接吃 displayCutout inset——竖屏侧边 inset 为 0，竖屏布局零变化；
        // 顶部横屏取 max(16dp, 状态栏高)，竖屏保持 16dp 不动。
        val isLandscapeHome = maxWidth > maxHeight
        val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
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
            // v0.68.44（Bob）：杂志封面模式
            // v0.68.96（F-01 退役）：PetZone 的非封面分支已删除，todayCardAlpha
            // 参数随之移除——该淡出原本只作用于已退役的 TODAY 卡，封面形态从未吃到。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
            PetZone(
                // 智能卡片（447）：叠层槽位——会行下方独立一段，无卡零高度，
                // 输入态与简报一起淡出。
                // 449/v1.2.2：包裹 Box 捕获叠层 boundsInRoot，供盲操手势仲裁
                // 用（叠层区的手势不转发盲操、也不走纵向 fallback）；无卡时
                // 槽位零高度、bounds 零面积，contains 恒 false，不误拦背景。
                cardStack = {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            cardStackBoundsInRoot = bounds
                            // 451：同时把叠层上下缘发给 UiPrefs，供首页 picker
                            // 触摸层断带用；无卡时槽位零高度、bounds 零面积，发 null。
                            UiPrefs.cardStackBandPx.value =
                                if (bounds.isEmpty) null
                                else (bounds.top to bounds.bottom)
                        }
                    ) { SmartCardStackHost() }
                },
            )
            }
        }
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
                .padding(horizontal = 40.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (compact) "还没开启权限" else "下滑手势需要开个权限",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
                Text(
                    text = if (compact)
                        "无障碍里的「AI 桌面」还没开，下滑手势暂时用不了。"
                    else
                        "下滑打开通知中心、斜滑打开控制中心，都要借用系统的无障碍能力。请在无障碍设置中开启「AI 桌面」，只用来帮你打开这两个面板，不会读取任何内容。",
                    fontSize = AILauncherType.Footnote.fontSize,
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
