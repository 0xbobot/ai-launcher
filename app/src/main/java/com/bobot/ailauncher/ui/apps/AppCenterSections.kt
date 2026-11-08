// AppCenterContent 拆分段：由 AppCenter.kt 202–2523 行的 AppCenterContent
// 拆出（VerifyError 修复：单 composable 约 2300 行、寄存器 v285，疑似超大方法
// 触发 Compose 编译器 codegen 问题）。只拆方法，不改行为、不改 UI：
// 每段均为原样移动 + 参数化；局部函数与状态语义一字不动。
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.PetMotion
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 手动吸顶状态（v0.41.8）：当前组首行刚开始滚出顶部（offset>0），悬停 overlay
 * 就接管显示；下一组首行接近顶部时把悬停字母往上顶走（Bob：滚动悬停/顶走）。
 * 原 AppCenterContent 542–774 行整段移出，只拆方法：remember /
 * derivedStateOf / animateFloatAsState 的 key 与依赖一字不动。
 */
data class AppCenterStuckState(
    val stuckLetter: Char?,
    val stuckPushPx: Float,
    val animatedPushPx: Float,
    val stuckHidden: Boolean,
    val stuckFold: Boolean,
    val foldPushPx: Float,
    val animatedFoldPushPx: Float,
    val stuckDock: Boolean,
    val dockPushPx: Float,
    val animatedDockPushPx: Float,
    val stuckLetterAlpha: Float,
    val stuckDockAlpha: Float,
    val stuckHiddenAlpha: Float,
    val stuckFoldAlpha: Float,
    val stuckUnread: Boolean,
)

@Composable
fun rememberAppCenterStuckState(
    listState: LazyListState,
    azGroups: List<Pair<Char, List<AppInfo>>>,
    letters: List<Char>,
    letterAnchors: Map<Char, Int>,
    foldVisible: Boolean,
    foldHeaderIndex: Int,
    hiddenApps: List<AppInfo>,
    hiddenUnlocked: Boolean,
    dockApps: List<AppInfo>,
    unreadVisible: Boolean,
    unreadFirstIndex: Int,
    reduceMotion: Boolean,
    density: Density,
): AppCenterStuckState {
    // v0.41.8：手动吸顶——当前组首行刚开始滚出顶部（offset>0），悬停 overlay
    // 就接管显示；下一组首行接近顶部时把悬停字母往上顶走（Bob：滚动悬停/顶走）
    val stuckLetter: Char? by remember(azGroups, foldVisible, foldHeaderIndex) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            // 折叠组（419）：滚进折叠段（含其后）后不显示字母幽灵，改由
            // 折叠幽灵接管（折叠段后是隐藏区，隐藏幽灵另有分支）。
            if (foldVisible && firstIdx >= foldHeaderIndex) return@derivedStateOf null
            val current = letterAnchors.entries
                .filter { it.value <= firstIdx }
                .maxByOrNull { it.value }
                ?.key ?: return@derivedStateOf null
            // 427（Bob：A 先隐藏再弹出来）：首行刚开始滚出（offset>0）就接管，
            // 不再等首行完全滚出——行内字母在 offset 44dp 已被完全裁掉
            // （60dp 行、字母顶边 16dp、高约 28dp），旧条件到 60dp 才接管，
            // 中间 16dp 两个字母都不显示，overlay 又是硬出现，所以闪；
            // 提前接管后 overlay 落在行内字母原位（offset==0 时行内字母顶边
            // 正好 16dp），行内字母随行滑走，顺滑吸顶。offset==0 时仍不悬停
            // （行内字母正在原位显示）。
            val anchor = letterAnchors[current] ?: 0
            if (firstIdx > anchor) current
            else if (firstIdx == anchor && listState.firstVisibleItemScrollOffset > 0) current
            else null
        }
    }
    // 顶走位移：下一组首行进入顶部区域时，悬停字母被往上顶（负值）；
    // 读 firstVisibleItemScrollOffset 订阅逐像素滚动，保证顶走动画跟手。
    // 外层 Box 有 clipToBounds，顶出去的部分会被裁掉，不会跑到搜索栏上面（Bob）
    val stuckPushPx: Float by remember(azGroups, hiddenApps, hiddenUnlocked, foldVisible, foldHeaderIndex) {
        derivedStateOf {
            val cur = stuckLetter ?: return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val order = letters
            val next = order.getOrNull(order.indexOf(cur) + 1)
            // v0.41.28（Bob）：隐藏分组头也要顶走悬停字母——最后一个字母后面是隐藏头，
            // 用 key 找隐藏头 item，否则幽灵眼睛会和 Z 重叠
            val off = if (next != null) {
                val nextAnchor = letterAnchors[next] ?: return@derivedStateOf 0f
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == nextAnchor }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            } else if (foldVisible) {
                // 折叠组（419）：最后一个字母后面是折叠组头（不是隐藏头），
                // 组头接近顶部时把悬停字母顶走
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == foldHeaderIndex }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            } else {
                if (hiddenApps.isEmpty()) return@derivedStateOf 0f
                // v0.41.32：隐藏头已并入第一行，找第一个隐藏 item
                val firstHiddenKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                    else "hidden-locked"
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == firstHiddenKey }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            }
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    // v0.41.30（Bob）：顶走位移动画化——字母切换时不跳变，推到位置停住不弹回
    // 应用中心升级：减动态开启时直接贴合
    val animatedPushPx by animateFloatAsState(
        targetValue = stuckPushPx,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "stuckPush"
    )
    // v0.41.28（Bob）：隐藏分组的幽灵眼也要悬停——隐藏头刚开始滚出
    // 就接管（427，同字母：等整行滚出才接管会在行内眼被裁掉后留 12dp
    // 左右的无幽灵窗口）；幽灵眼接管悬停，直到滚出隐藏分组
    val stuckHidden: Boolean by remember(hiddenApps, hiddenUnlocked) {
        derivedStateOf {
            if (hiddenApps.isEmpty()) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            // v0.41.32：隐藏头已并入第一行
            val firstKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                else "hidden-locked"
            val first = vis.firstOrNull { it.key == firstKey }
            val firstGone = first == null || first.offset < 0
            val hiddenVisible = vis.any {
                it.key == "hidden-locked" ||
                (it.key is String && (it.key as String).startsWith("h:"))
            }
            firstGone && hiddenVisible
        }
    }
    // 折叠组（419）：折叠段幽灵——组头刚开始滚出就接管（427，同字母），
    // 直到滚出折叠段；与 stuckHidden 同理（组头已走＋段内仍有可见项）
    val stuckFold: Boolean by remember(foldVisible, foldHeaderIndex) {
        derivedStateOf {
            if (!foldVisible) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            val first = vis.firstOrNull { it.key == "fold-header" }
            val firstGone = first == null || first.offset < 0
            val foldInView = vis.any {
                it.key == "fold-header" || it.key == "sp:fold" || it.key == "fold-foot" ||
                    (it.key is String && (it.key as String).startsWith("f:"))
            }
            firstGone && foldInView
        }
    }
    // 折叠组（419）：折叠幽灵被隐藏头顶走（与字母顶走逻辑一致）；
    // 无隐藏应用时不顶（幽灵随滚出自然消失，沿用隐藏为空时的旧口径）
    val foldPushPx: Float by remember(foldVisible, hiddenApps, hiddenUnlocked) {
        derivedStateOf {
            if (!stuckFold) return@derivedStateOf 0f
            if (hiddenApps.isEmpty()) return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val firstHiddenKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                else "hidden-locked"
            val off = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == firstHiddenKey }
                ?.offset?.toFloat() ?: return@derivedStateOf 0f
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    val animatedFoldPushPx by animateFloatAsState(
        targetValue = foldPushPx,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "foldPush"
    )
    // v0.41.31（Bob）：Dock 分区幽灵 ★ 悬停——第一行刚开始滚出就接管
    // （427，同字母），直到滚出 Dock 分区；与 stuckHidden 同理
    // v0.41.32：dock 头已并入第一行
    val stuckDock: Boolean by remember(dockApps) {
        derivedStateOf {
            if (dockApps.isEmpty()) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            // v0.41.32：dock 头已并入第一行
            val firstKey = "d:${dockApps.first().packageName}"
            val first = vis.firstOrNull { it.key == firstKey }
            val firstGone = first == null || first.offset < 0
            val dockVisible = vis.any {
                it.key is String && (it.key as String).startsWith("d:")
            }
            firstGone && dockVisible
        }
    }
    // v0.41.31：Dock 悬停被 A 顶走（与字母顶走逻辑一致）
    val dockPushPx: Float by remember(dockApps, azGroups, unreadVisible, unreadFirstIndex) {
        derivedStateOf {
            if (!stuckDock) return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            // 未读组在时，Dock 悬停由未读首行顶走（未读组紧跟 Dock 之后；
            // 464 起组头已删，顶走锚点就是未读第一行）；
            // 否则仍由首个字母组顶走（旧口径）。
            val off = if (unreadVisible) {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == unreadFirstIndex }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            } else {
                val firstLetter = letters.firstOrNull() ?: return@derivedStateOf 0f
                val anchor = letterAnchors[firstLetter] ?: return@derivedStateOf 0f
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            }
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    val animatedDockPushPx by animateFloatAsState(
        targetValue = dockPushPx,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "dockPush"
    )
    // 427：悬停幽灵淡入——接管点提前到“刚开始滚出”后，overlay 出现时
    // 行内幽灵还在视口内滑走；alpha 从 0 淡入（与 stuckPushPx 同一
    // PetMotion.PadStandard），不硬弹出来。减动态时直接贴合
    // （沿用既有口径）。alpha 状态提在分支外，使分支挂载时已有 0 的
    // 初值可淡入；分支卸载即瞬时消失（此时行内幽灵正在原位显示，
    // 或下一分支已接管，看不出）。
    val stuckLetterAlpha by animateFloatAsState(
        targetValue = if (stuckLetter != null) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "stuckLetterAlpha"
    )
    val stuckDockAlpha by animateFloatAsState(
        targetValue = if (stuckDock) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "stuckDockAlpha"
    )
    val stuckHiddenAlpha by animateFloatAsState(
        targetValue = if (stuckHidden) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "stuckHiddenAlpha"
    )
    val stuckFoldAlpha by animateFloatAsState(
        targetValue = if (stuckFold) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "stuckFoldAlpha"
    )
    // 未读组：未读段幽灵——首行刚开始滚出就隐藏行内信封（427，同字母/折叠），
    // 直到滚出未读段；与 stuckFold 同理（首行已走＋段内仍有可见项）。
    // 464 起组头已删（Bob：组头“未读 · N 个”不重要），吸顶“未读”标题
    // 一并取消，stuckUnread 不再交接给任何吸顶标题，只作行内信封的
    // 隐藏信号：首行完整可见时信封在，首行一开始滚出信封即消失
    // （不留吸顶标题占位，也不与 Dock ★ 悬停重叠）。
    val stuckUnread: Boolean by remember(unreadVisible, unreadFirstIndex) {
        derivedStateOf {
            if (!unreadVisible) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            val first = vis.firstOrNull { it.index == unreadFirstIndex }
            val firstGone = first == null || first.offset < 0
            val unreadInView = vis.any {
                it.key == "sp:unread" ||
                    (it.key is String && (it.key as String).startsWith("u:"))
            }
            firstGone && unreadInView
        }
    }
    return AppCenterStuckState(
        stuckLetter = stuckLetter,
        stuckPushPx = stuckPushPx,
        animatedPushPx = animatedPushPx,
        stuckHidden = stuckHidden,
        stuckFold = stuckFold,
        foldPushPx = foldPushPx,
        animatedFoldPushPx = animatedFoldPushPx,
        stuckDock = stuckDock,
        dockPushPx = dockPushPx,
        animatedDockPushPx = animatedDockPushPx,
        stuckLetterAlpha = stuckLetterAlpha,
        stuckDockAlpha = stuckDockAlpha,
        stuckHiddenAlpha = stuckHiddenAlpha,
        stuckFoldAlpha = stuckFoldAlpha,
        stuckUnread = stuckUnread,
    )
}

/**
 * 各列表段共用的行动作参数（第 3–8 条列表段共用）。
 * popupApp / expandedActionsPkg / revealedHidePkg 是调用点的可变状态：
 * 值直接读，写走 setter lambda。
 */
data class AppCenterRowActions(
    val popupApp: AppInfo?,
    val onPopupAppChange: (AppInfo?) -> Unit,
    val expandedActionsPkg: String?,
    val onExpandedActionsChange: (String?) -> Unit,
    val revealedHidePkg: String?,
    val onRevealedHideChange: (String?) -> Unit,
    val hapticView: View,
    val context: Context,
    val onLaunchApp: (AppInfo) -> Boolean,
    val onHideApp: (AppInfo) -> Unit,
)

/**
 * 刊头 + 搜索胶囊 + morph 齿轮 + 问助手行（原 1091–1281 行）。
 * intentFocused / intentBoundsInRoot / morphButtonBoundsInRoot 是调用点的
 * 可变状态：读走值、写走 setter lambda，语义不变。
 */
@Composable
fun AppCenterHeaderSection(
    isLandscapeAc: Boolean,
    catalog: AppCatalog.Snapshot?,
    displayResults: List<AppInfo>?,
    azApps: List<AppInfo>,
    queryField: TextFieldValue,
    onQueryFieldChange: (TextFieldValue) -> Unit,
    onAskQizai: (String) -> Unit,
    onThrottledAsk: (String) -> Unit,
    intentFocused: Boolean,
    onIntentFocusedChange: (Boolean) -> Unit,
    onIntentBoundsChange: (Rect) -> Unit,
    onMorphButtonBoundsChange: (Rect) -> Unit,
    focusManager: FocusManager,
    onOpenSettings: () -> Unit,
    onPullDownState: State<() -> Unit>,
    onHeaderSwipeRightState: State<() -> Unit>,
    density: Density,
) {
    val query = queryField.text
    // ---- Header（v0.68.44 杂志刊头，Bob 选 A）：INDEX kicker + 宋体"全部应用" + 数量 ----
    // 顶栏下滑 → D2；顶栏右滑 → 回记住的 Dock 档（手势签名：右滑=更少）
    val headThreshPx = with(density) { 80.dp.toPx() }
    Column(
        modifier = Modifier.pointerInput(Unit) {
            var accumY = 0f
            var fired = false
            detectVerticalDragGestures(
                onDragStart = { accumY = 0f; fired = false },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    if (fired) return@detectVerticalDragGestures
                    accumY += dragAmount
                    if (accumY > headThreshPx) {
                        fired = true
                        onPullDownState.value()
                    }
                }
            )
        }
    ) {
        // v0.68.66（Bob）：横屏时顶栏+搜索全由 AppCenterLandscape 提供，这里只留竖屏
        // Bob 2026-11-03：编辑态不再换版面——刊头常驻（键盘弹出也不隐藏）；
        // 顶栏左轴 28→20dp，与搜索行统一 20/12dp 口径
        if (!isLandscapeAc) {
        // v0.68.44（Bob）：杂志刊头
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp)
        ) {
            Text(
                text = "全部应用",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = AILauncherType.Display.fontSize,
                letterSpacing = 2.sp,
                color = AILauncherColors.current.Title
            )
            Text(
                // 应用中心升级：catalog 为空时不显示"0 款"空列表，改弱提示
                // Bob 2026-11-03：数字跟当前列表走——搜索态显示过滤后条数
                // 反馈闭环：LLM 重试出结果后，当前列表是 displayResults。
                text = if (catalog == null) "正在加载…"
                    else "${displayResults?.size ?: azApps.size} 款 · 右侧滑字母直达",
                fontSize = AILauncherType.Caption.fontSize,
                color = AILauncherColors.current.Hint,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        }  // else 竖屏刊头结束
        // 本搜索区仅竖屏组合；横屏由 AppCenterLandscape 组合（F2 起同一胶囊契约）
        if (!isLandscapeAc) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp)
                .pointerInput(Unit) {
                    // 顶栏右滑 → 回到记住的 Dock 行数（手势签名：右滑=更少）
                    var accumX = 0f
                    var fired = false
                    val hPx = with(density) { 48.dp.toPx() }
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; fired = false },
                        onDragCancel = { fired = true },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (fired) return@detectHorizontalDragGestures
                            accumX += dragAmount
                            if (accumX > hPx) {
                                fired = true
                                onHeaderSwipeRightState.value()
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // R2 UI（2026-10-21 新规格）：原搜索框删除，换成与首页同一个意图胶囊组件——
            // 一个入口两层响应：打字走本地层过滤网格（状态层 query/searchResults 不变）。
            // 应用中心胶囊无提交钮（Bob 2026-11-03：箭头仅收键盘、无实际功能，
            // 随首页意图入口一并删）；IME Done 与问七仔行仍 = 问七仔。
            IntentCapsuleField(
                value = queryField,
                onValueChange = onQueryFieldChange,
                onSubmit = {
                    val q = query.trim()
                    if (q.isNotEmpty()) onAskQizai(q)
                },
                // 425（Bob 2026-11-04，参考视频）：里侧清空去掉，挪到外面齿轮位——
                // 聚焦变 X、失焦回齿轮，全程只剩外面这一个清空（见下齿轮 morph）。
                showClearButton = false,
                showSubmitButton = false,
                // 428 滚动关键字：应用中心箭头只作关键字的运行钮——点箭头只填入
                // 关键字、网格随之过滤，不走 onSubmit（其 onSubmit 是 onAskQizai，
                // 会回首页跑意图，与“按关键字过滤应用”相悖）。
                keywordSubmitCallsOnSubmit = false,
                fusedBackground = true,
                // v0.68.93：焦点外报 + 范围捕获（根 Box 点外部观察者用）
                onFocusChanged = { onIntentFocusedChange(it) },
                modifier = Modifier.weight(1f)
                    .onGloballyPositioned { onIntentBoundsChange(it.boundsInRoot()) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 425（Bob 2026-11-04，参考视频）：齿轮位 morph——失焦是设置齿轮原样，
            // 聚焦（输入态）就地变清空；只换图标＋动作＋文案，圆盘／位置／尺寸／testTag 一律不动。
            // 点 X：有字清空（留输入态，X 还在）、没字退焦（键盘收起，自动变回齿轮）；
            // 429（Bob 2026-11-05：箭头填入后失焦有字）：触发改成“聚焦 或 有字”就变 ✕，
            // 没字且失焦才回齿轮——填入后不用聚焦也能看到 ✕、一点就清空。
            // 返回／点外面／点完成／点开应用都会失焦，变回齿轮不用每个出口单独处理。
            // v0.41.9（Bob 选 B）：设置齿轮缩小弱化，仍在右上
            // 426（Bob 2026-11-04 点 X 回设置页）：按钮不可聚焦＋记 bounds——
            // 点 X 时焦点不能从输入框被抢走（否则 morph 先变回齿轮、点击读到
            // false 走设置），且按钮位要从点外面清焦里排除（见根 Box 观察者）。
            // 圆盘／位置／尺寸／testTag 一律不动，只加这两项。
            IconButton(
                onClick = {
                    // 429：有字点 ✕ 只清空文本（不进设置页、不强制聚焦）；没字且聚焦时
                    // 点 ✕ 退焦（回齿轮）；没字且失焦时点齿轮进设置（不变）。
                    if (query.isNotEmpty()) onQueryFieldChange(TextFieldValue(""))
                    else if (intentFocused) focusManager.clearFocus()
                    else onOpenSettings()
                },
                // 体检批D ⑥：冒烟语义定位锚点（应用中心设置齿轮）
                modifier = Modifier.testTag("appcenter_settings")
                    .focusProperties { canFocus = false }
                    .onGloballyPositioned { onMorphButtonBoundsChange(it.boundsInRoot()) }
            ) {
                // Bob 2026-11-03 原型样式：齿轮坐浅色圆盘（原型同款）；
                // 触控框仍是 IconButton 48dp，圆盘 40dp 只改视觉
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AILauncherColors.current.Title.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (intentFocused || query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空",
                            tint = AILauncherColors.current.Title,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = AILauncherColors.current.Hint.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        // R2 UI（10-21 新规格）：「问七仔」行——有输入时出现，整句交给助手理解（回首页提交协调器）
        // 意图改进一批：本行跟随原始文本即时出现（不等防抖落定），文案与提交都用原句
        // 应用中心升级：去 Card 底降重——文字改 Title 75%，金色只留小箭头
        // 一处线索；连点 500ms 节流
        if (query.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 4.dp)
                    .clickable { onThrottledAsk(query.trim()) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "问助手：「${query.trim()}」",
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Title.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    // 体检批D ⑨：装饰图标（同问七仔行）标装饰语义
                    contentDescription = null,
                    tint = AILauncherColors.current.Accent,
                    modifier = Modifier
                        .size(16.dp)
                        .semantics { invisibleToUser() }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        }  // if (!isLandscapeAc) 搜索区结束（v0.68.72 新增，R2 改同组件）
    }  // Header Column 闭合
}

/**
 * LazyColumn 搜索结果分支（原 1390–1505 行）：空态 + 结果行 + 尾部间距。
 */
fun LazyListScope.AppCenterSearchResultsSection(
    results: List<AppInfo>,
    filterQuery: String,
    catalog: AppCatalog.Snapshot?,
    marketGuideTerm: String?,
    onMarketGuideClick: () -> Unit,
    showRetryPrompt: Boolean,
    retryLoading: Boolean,
    onRetryClick: () -> Unit,
    deepLinkSupportedPkgs: Set<String>,
    actions: AppCenterRowActions,
) {
    // 搜索态：只显示搜索结果（拼音/首字母/自然语言/模糊）
    if (results.isEmpty()) {
        item(key = "search-empty") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    // 应用中心升级：索引缺失与真无匹配区分文案
                    // 搜索同义词批（Bob 2026-11-05）：空态居中、一行——
                    // 去掉跟随应用行的 72/64dp 大缩进，改全宽居中、
                    // maxLines=1+省略号；文案略缩（“相关的”→“相关”）
                // 以求一行放得下。字号不变（Callout）。
                text = if (catalog == null) "应用列表还没准备好，请稍后再试"
                    else "没有找到「${filterQuery.trim()}」相关应用",
                fontSize = AILauncherType.Callout.fontSize,
                color = AILauncherColors.current.Hint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            )
                // 反馈闭环（Bob 2026-11-06）：0 结果且意图没被识别时，
                // 在空态文案下给“有应用能打开，重试”——点一下大模型
                // 再确认一次。文案就是 Bob 给的话，不用内部词。
                // 443：大模型已认出意图、但本机没装对应应用时，
                // 重试行不再出现（再点也是同一结论），改出应用市场
                // 指引行——点一下去应用市场搜相关应用、自己安装。
                val guideTerm = marketGuideTerm
                if (guideTerm != null) {
                    MarketGuideRow(
                        term = guideTerm,
                        onClick = onMarketGuideClick
                    )
                } else if (showRetryPrompt) {
                    FeedbackRetryRow(
                        loading = retryLoading,
                        onClick = onRetryClick
                    )
                }
            }
        }
    } else {
        items(results, key = { "s:${it.packageName}" }) { app ->
            // v0.68.53（Bob）：生长式——长按行直接在下方展开快捷卡，
            // 同一张圆角卡（20.dp），不重复图标标题，像从行里长出来
            val isPopupExpanded = actions.popupApp?.packageName == app.packageName
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isPopupExpanded) AILauncherColors.current.Card
                        else Color.Transparent
                    )
                    // 应用中心升级：行展开去 animateContentSize，
                    // 只留 AnimatedVisibility（避免双动画叠加）
            ) {
                AppRow(
                    app = app,
                    onLaunch = { actions.onLaunchApp(app) },
                    compact = true,
                    onLongClick = {
                        actions.hapticView.confirmHaptic()
                        actions.onPopupAppChange(app)
                    },
                    showInfoIcon = isPopupExpanded,
                    onInfoClick = {
                        openAppDetailsPage(actions.context, app.packageName)
                        actions.onPopupAppChange(null)
                    },
                    onHide = actions.onHideApp,
                    // 内容深链标识：这句查询下点按会走深链直达的
                    // 应用才出「直达」标签（本句算好的集合里查包名）。
                    showDeepLinkBadge = deepLinkSupportedPkgs.contains(app.packageName),
                    actionsVisible = actions.expandedActionsPkg == app.packageName,
                    onActionsVisibleChange = { expanded ->
                        // v0.41.23：与 revealedHidePkg 互斥
                        if (expanded) {
                            actions.onExpandedActionsChange(app.packageName)
                            actions.onRevealedHideChange(null)
                        } else if (actions.expandedActionsPkg == app.packageName) {
                            actions.onExpandedActionsChange(null)
                        }
                    },
                    hideVisible = actions.revealedHidePkg == app.packageName,
                    onHideVisibleChange = { visible ->
                        if (visible) {
                            actions.onRevealedHideChange(app.packageName)
                            actions.onExpandedActionsChange(null)
                        } else if (actions.revealedHidePkg == app.packageName) {
                            actions.onRevealedHideChange(null)
                        }
                    }
                )
                AnimatedVisibility(
                    visible = isPopupExpanded,
                    enter = expandVertically(
                        animationSpec = tween(
                            durationMillis = com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                            easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically() +
                        fadeOut()
                ) {
                    AppShortcutInline(
                        app = app,
                        onDismiss = { actions.onPopupAppChange(null) }
                    )
                }
            }
        }
    }
    item { Spacer(modifier = Modifier.height(16.dp)) }
}

/**
 * Dock 拖动排序步进（纯函数，单测锁死在 DockDragTest）：
 * 根据累计偏移算目标位；返回 (目标位, 回扣后偏移)，不够一行返回 null。
 * 行高由调用方传（生产是 64dp = 60dp 行 + 4dp 间距，2026-11-03 一屏
 * 8 个后定的数）。
 */
internal fun dockDragStep(
    curIdx: Int,
    offsetY: Float,
    rowH: Float,
    size: Int,
): Pair<Int, Float>? {
    val targetIdx = (curIdx + (offsetY / rowH).toInt()).coerceIn(0, size - 1)
    if (targetIdx == curIdx) return null
    return targetIdx to (offsetY - (targetIdx - curIdx) * rowH)
}

/**
 * Dock 段（原 1507–1664 行）。拖拽本地状态（dockOrder / draggingDockPkg /
 * dockDragOffsetY）及同步用 LaunchedEffect 留在 AppCenterContent（LazyListScope
 * 段函数不是 @Composable 语境，不能持有 remember/LaunchedEffect），这里只接参数。
 */
fun LazyListScope.AppCenterDockSection(
    dockApps: List<AppInfo>,
    dockOrder: SnapshotStateList<AppInfo>,
    draggingDockPkg: String?,
    onDraggingDockPkgChange: (String?) -> Unit,
    dockDragOffsetY: Float,
    onDockDragOffsetYChange: (Float) -> Unit,
    density: Density,
    actions: AppCenterRowActions,
) {
    // v0.25.7：去掉"全部应用"标题（Bob）
    // v0.41.31（Bob）：Dock 分区——A 之前，像隐藏分区一样；
    // 为空时不显示；幽灵 ★ 头（与隐藏头眼图标样式一致）
    if (dockApps.isNotEmpty()) {
        // v0.41.32（Bob）：幽灵 ★ 与第一行同行（像字母那样），不单独占行
        itemsIndexed(dockOrder, key = { _, app -> "d:${app.packageName}" }) { index, app ->
            // v0.56.1（Bob）：手柄若因重组被重建、正在拖，保存顺序（防手势取消丢保存）
            // 463 拆分回归：onDispose 读的是段参数快照（建 effect 时的旧值，恒为
            // null），保存分支永远走不到；改用 rememberUpdatedState 读最新值。
            val latestDraggingDockPkg by rememberUpdatedState(draggingDockPkg)
            DisposableEffect(app.packageName) {
                onDispose {
                    if (latestDraggingDockPkg == app.packageName) {
                        UiPrefs.setDockPinned(actions.context, dockOrder.map { it.packageName })
                        onDraggingDockPkgChange(null)
                        onDockDragOffsetYChange(0f)
                    }
                }
            }
            // v0.41.34（Bob）：拖动中的行跟随手指，其他行自动让位
            val isDockDragging = draggingDockPkg == app.packageName
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDockDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDockDragging) dockDragOffsetY.roundToInt() else 0) }
            ) {
                // v0.55.3（Bob）：幽灵常驻（alpha=0 占位），避免 index 变化时子项增减
                // 导致拖动手柄的 pointerInput 重建、手势取消、顺序没保存
                Icon(
                    imageVector = Icons.Filled.Star,
                    // 体检批D ⑨：幽灵占位（alpha 0/0.14 纯视觉）标装饰语义
                    contentDescription = null,
                    tint = AILauncherColors.current.Title.copy(alpha = if (index == 0) 0.14f else 0f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .size(36.dp)
                        .semantics { invisibleToUser() }
                )
                // v0.68.53（Bob）：生长式
                val isPopupExpandedX = actions.popupApp?.packageName == app.packageName
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isPopupExpandedX) AILauncherColors.current.Card
                            else Color.Transparent
                        )
                ) {
                AppRow(
                    app = app,
                    onLaunch = { actions.onLaunchApp(app) },
                    compact = true,
                    onLongClick = {
                        actions.hapticView.confirmHaptic()
                        actions.onPopupAppChange(app)
                    },
                    showInfoIcon = isPopupExpandedX,
                    onInfoClick = {
                        openAppDetailsPage(actions.context, app.packageName)
                        actions.onPopupAppChange(null)
                    },
                    onHide = actions.onHideApp,
                    actionsVisible = actions.expandedActionsPkg == app.packageName,
                    onActionsVisibleChange = { expanded ->
                        if (expanded) {
                            actions.onExpandedActionsChange(app.packageName)
                            actions.onRevealedHideChange(null)
                        } else if (actions.expandedActionsPkg == app.packageName) {
                            actions.onExpandedActionsChange(null)
                        }
                    },
                    hideVisible = actions.revealedHidePkg == app.packageName,
                    onHideVisibleChange = { visible ->
                        if (visible) {
                            actions.onRevealedHideChange(app.packageName)
                            actions.onExpandedActionsChange(null)
                        } else if (actions.revealedHidePkg == app.packageName) {
                            actions.onRevealedHideChange(null)
                        }
                    }
                )
                AnimatedVisibility(
                    visible = isPopupExpandedX,
                    enter = expandVertically() +
                        fadeIn(),
                    exit = shrinkVertically() +
                        fadeOut()
                ) {
                    AppShortcutInline(
                        app = app,
                        onDismiss = { actions.onPopupAppChange(null) }
                    )
                }
                }
                // v0.41.34（Bob）：拖动手柄——简洁三横线，Hint 色 18dp，无背景；
                // 按住上下拖动排序，松手保存（不再去设置里调）
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "拖动排序",
                    tint = AILauncherColors.current.Hint,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 88.dp)
                        .size(18.dp)
                        .pointerInput(app.packageName) {
                            // 463 拆分回归：段函数接的是 dockDragOffsetY 快照
                            // （Float 参数），pointerInput 只在 key 变化时重建，
                            // onDrag 读到的永远是建块时的旧值（0f）——累计偏移
                            // 算不出，targetIdx 恒等于 curIdx，拖动永远换不了位
                            // （行只剩“最新一次位移”的抖动，松手弹回原位）。
                            // 拆分前这里直接读写委托属性（每次读都走 state.value，
                            // 天然最新）。累计偏移改由手势域内局部变量持有、并
                            // 镜像到状态（渲染用），恢复拆分前语义；步进算式抽成
                            // 纯函数 dockDragStep（单测锁死）。
                            var accOffsetY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    accOffsetY = 0f
                                    onDraggingDockPkgChange(app.packageName)
                                    onDockDragOffsetYChange(0f)
                                    actions.hapticView.tickHaptic()
                                },
                                onDragEnd = {
                                    UiPrefs.setDockPinned(
                                        actions.context,
                                        dockOrder.map { it.packageName }
                                    )
                                    onDraggingDockPkgChange(null)
                                    onDockDragOffsetYChange(0f)
                                },
                                onDragCancel = {
                                    // v0.44.3：取消也保存当前顺序，不重置（拖到首位时手势易被取消）
                                    UiPrefs.setDockPinned(
                                        actions.context,
                                        dockOrder.map { it.packageName }
                                    )
                                    onDraggingDockPkgChange(null)
                                    onDockDragOffsetYChange(0f)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accOffsetY += dragAmount.y
                                    onDockDragOffsetYChange(accOffsetY)
                                    // v0.55.2：步进 = 行 60dp + 4dp 间距 = 64dp
                                    // （Bob 2026-11-03：一屏 8 个；旧 56dp 是 52dp 行时代的数，已作废）
                                    val rowH = with(density) {
                                        64.dp.toPx()
                                    }
                                    val curIdx = dockOrder.indexOfFirst {
                                        it.packageName == app.packageName
                                    }
                                    if (curIdx >= 0) {
                                        dockDragStep(curIdx, accOffsetY, rowH, dockOrder.size)?.let { (targetIdx, rebated) ->
                                            val item = dockOrder.removeAt(curIdx)
                                            dockOrder.add(targetIdx, item)
                                            accOffsetY = rebated
                                            onDockDragOffsetYChange(accOffsetY)
                                        }
                                    }
                                }
                            )
                        }
                )
            }
        }
        item(key = "sp:dock") { Spacer(modifier = Modifier.height(16.dp)) }
    }

}

/**
 * 未读组段（原 1682–1780 行）。
 */
fun LazyListScope.AppCenterUnreadSection(
    unreadApps: List<AppInfo>,
    unreadPreviews: Map<String, String>,
    unreadDisplayNames: Map<String, String> = emptyMap(),
    activeNotifPkgs: Set<String>,
    onClearUnreadNotifications: (String) -> Unit,
    unreadVisible: Boolean,
    stuckUnread: Boolean,
    actions: AppCenterRowActions,
) {
    // 未读组（Bob 2026-11-07 晚）：A 组之前最优先；始终展开、不折叠；
    // 464 起组头已删（Bob 2026-11-08 18:37：“未读 · N 个”没那么重要，
    // 删除吧）——段内只有 N 行 + 组间呼吸，首行就是组的开头；
    // 行复用 AppRow（角标照常画、长按菜单与生长式展开都在），
    // 组内按未读数从大到小排；
    // N==0 时整组不出现，列表仍从 A 开始。搜索态不走这里（搜索
    // 结果分支在上），取消搜索后落点见 LaunchedEffect(searching)。
    if (unreadVisible) {
        itemsIndexed(unreadApps, key = { _, app -> "u:${app.packageName}" }) { index, app ->
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                // 幽灵信封（Bob 2026-11-08）：未读组首行左边大号淡色信封，
                // 与字母组幽灵字母同一样式（32dp、Title 色 35% 透明）、同位置
                // （居左、start 24dp）；首行刚开始滚出即隐藏（stuckUnread，
                // 464 起锚在首行，不再有吸顶“未读”标题可交接）。
                // 信封是未读组在 rail 里的符号，两处同一符号保持一致。
                if (index == 0 && !stuckUnread) {
                    UnreadGlyph(
                        color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .size(32.dp)
                    )
                }
                // 左滑清理（Bob 2026-11-08）：有在用通知的行可左滑取消通知；
                // 无在用通知的行（短信库口径、飞书无通知）不启用左滑。
                // 行内滑动浮层（★/眼睛）关掉，避免两套横滑抢事件。
                // 466（Bob 2026-11-08 20:02 左滑清理蒙层为啥全宽）：蒙层原先挂在
                // 带 padding 的 Column 外面、没继承 72/64dp 缩进，铺满全宽；
                // 现把 Column 外包、ClearableUnreadRow 只包 AppRow 本身，
                // 蒙层继承缩进、只盖行内容（与 AppRow 蒙层同宽）。
                // padding 的单一来源仍是这里的 Column，蒙层跟着它走。
                val isPopupExpandedX = actions.popupApp?.packageName == app.packageName
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isPopupExpandedX) AILauncherColors.current.Card
                            else Color.Transparent
                        )
                ) {
                    ClearableUnreadRow(
                        enabled = app.packageName in activeNotifPkgs,
                        onClear = { onClearUnreadNotifications(app.packageName) }
                    ) {
                        AppRow(
                            app = app,
                            // 打开 + 清除（Bob 2026-11-08 23:20 澄清：点未读行打开应用
                            // 后消息没被清除——原来行点按只走开应用、不取消通知）：
                            // 像点通知栏通知一样，成功打开且有在用通知时，走左滑
                            // 清理同一条取消路径（onClearUnreadNotifications）。
                            // 短信行无在用通知（不在 activeNotifPkgs 里），只打开、
                            // 不调取消，行等短信在应用内读完、库计数降了才离开未读组。
                            // 打开失败（同一应用 500ms 内重复点按、或启动抛异常）
                            // 不取消——用户没读到，通知留着。
                            onLaunch = {
                                val opened = actions.onLaunchApp(app)
                                if (opened && app.packageName in activeNotifPkgs) {
                                    onClearUnreadNotifications(app.packageName)
                                }
                            },
                            compact = true,
                            displayLabel = unreadDisplayNames[app.packageName],
                            preview = unreadPreviews[app.packageName],
                            swipeActionsEnabled = false,
                            onLongClick = {
                                actions.hapticView.confirmHaptic()
                                actions.onPopupAppChange(app)
                            },
                            showInfoIcon = isPopupExpandedX,
                            onInfoClick = {
                                openAppDetailsPage(actions.context, app.packageName)
                                actions.onPopupAppChange(null)
                            },
                            onHide = actions.onHideApp,
                            actionsVisible = actions.expandedActionsPkg == app.packageName,
                            onActionsVisibleChange = { expanded ->
                                actions.onExpandedActionsChange(if (expanded) app.packageName else if (actions.expandedActionsPkg == app.packageName) null else actions.expandedActionsPkg)
                            }
                        )
                        AnimatedVisibility(
                            visible = isPopupExpandedX,
                            enter = expandVertically() +
                                fadeIn(),
                            exit = shrinkVertically() +
                                fadeOut()
                        ) {
                            AppShortcutInline(
                                app = app,
                                onDismiss = { actions.onPopupAppChange(null) }
                            )
                        }
                    }
                }
            }
        }
        item(key = "sp:unread") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * 字母组段（原 1781–1860 行）。
 * 注：catalog 加载中 item（原 1665–1681 行）在列表顺序上位于未读组之前，
 * 仍留在 AppCenterContent 内原位，未随本段移动（顺序不变）。
 */
fun LazyListScope.AppCenterLetterGroupsSection(
    azGroups: List<Pair<Char, List<AppInfo>>>,
    stuckLetter: Char?,
    isLandscapeAc: Boolean,
    actions: AppCenterRowActions,
) {
    azGroups.forEach { (letter, apps) ->
        itemsIndexed(apps, key = { _, app -> "a:${app.packageName}" }) { index, app ->
            // v0.41.7：行级 padding（列表不再统一 padding）
            // 应用中心升级：行 Box 加 clipToBounds——字体缩放下幽灵
            // 字母不侵入上下行（字号不动）
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                // v0.68.61：横屏隐藏幽灵字母（省竖向空间，不乱）
                // Bob 2026-11-03：编辑态不再隐藏幽灵字母；透明度
                // 0.20 → 0.35（Bob 定稿④：可以动、效果优先）
                // 429（Bob 2026-11-05 晚：427 接管提前＋淡入后留了同字母重影，
                // 悬停 overlay 与行内字母同时显示 A+A）——行内字母只在 overlay
                // 没显示本组时才画（接管期间只留 overlay 一个）；行完全滚回
                // 视口（stuckLetter==null）时行内字母再出现。顶走过渡（overlay
                // 的 A 被下一组 B 行顶上去、A 与 B 短暂同现）是标准吸顶、保留。
                if (index == 0 && !isLandscapeAc && stuckLetter != letter) {
                    Text(
                        text = letter.toString(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 40.sp,
                        // Bob 2026-11-03 原型比例：幽灵字母收小（52dp 行内不撑高，
                        // 大写高约 0.7em，40sp 字形约 28dp）
                        lineHeight = 40.sp,
                        // v0.68.44（Bob）：杂志章节感——放大加深
                        color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                    )
                }
                // v0.68.53（Bob）：生长式
                val isPopupExpandedX = actions.popupApp?.packageName == app.packageName
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isPopupExpandedX) AILauncherColors.current.Card
                            else Color.Transparent
                        )
                ) {
                AppRow(
                    app = app,
                    onLaunch = { actions.onLaunchApp(app) },
                    compact = true,
                    onLongClick = {
                        actions.hapticView.confirmHaptic()
                        actions.onPopupAppChange(app)
                    },
                    showInfoIcon = isPopupExpandedX,
                    onInfoClick = {
                        openAppDetailsPage(actions.context, app.packageName)
                        actions.onPopupAppChange(null)
                    },
                    onHide = actions.onHideApp,
                    actionsVisible = actions.expandedActionsPkg == app.packageName,
                    onActionsVisibleChange = { expanded ->
                        actions.onExpandedActionsChange(if (expanded) app.packageName else if (actions.expandedActionsPkg == app.packageName) null else actions.expandedActionsPkg)
                    }
                )
                AnimatedVisibility(
                    visible = isPopupExpandedX,
                    enter = expandVertically() +
                        fadeIn(),
                    exit = shrinkVertically() +
                        fadeOut()
                ) {
                    AppShortcutInline(
                        app = app,
                        onDismiss = { actions.onPopupAppChange(null) }
                    )
                }
            }
            }
        }
        item(key = "sp:$letter") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * 折叠组段（原 1861–1984 行）。421 安静版双写逻辑（本地态 + UiPrefs）原样保留。
 */
fun LazyListScope.AppCenterFoldSection(
    foldMembers: List<AppInfo>,
    foldVisible: Boolean,
    foldExpandedState: Boolean,
    onFoldExpandedChange: (Boolean) -> Unit,
    foldLastDeclinedN: Int,
    onFoldLastDeclinedNChange: (Int) -> Unit,
    inactiveTargets: List<AppInfo>,
    actions: AppCenterRowActions,
) {
    // 折叠组（421 安静版）：主列表末尾、A-Z 之后、隐藏区之前；平时只占
    // 组头一行（“不常用 · N 个”＋收展 chevron），展开时组内为正常
    // 应用行（复用 AppRow：能打开、长按菜单都在），按 A-Z 行内顺序
    // （拼音排序）排列，行后跟组底确认行；N>0 且未压制时自动出现
    // （默认收着），压制时成员回到 A-Z 里。
    // 组头 chevron 是组自身的收展标示，不是应用行箭头（416 删的是
    // 应用行尾箭头，两者不是同一元素）。
    if (foldVisible) {
        item(key = "fold-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val next = !foldExpandedState
                        onFoldExpandedChange(next)
                        UiPrefs.setFoldExpanded(actions.context, next)
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "不常用 · ${foldMembers.size} 个",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    color = AILauncherColors.current.Title,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (foldExpandedState) Icons.Outlined.KeyboardArrowUp
                        else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (foldExpandedState) "收起不常用" else "展开不常用",
                    tint = AILauncherColors.current.Hint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (foldExpandedState) {
            itemsIndexed(foldMembers, key = { _, app -> "f:${app.packageName}" }) { _, app ->
                Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                    // 折叠组内无幽灵字母（不是字母组）；行结构与 A-Z 行一致
                    val isPopupExpandedX = actions.popupApp?.packageName == app.packageName
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isPopupExpandedX) AILauncherColors.current.Card
                                else Color.Transparent
                            )
                    ) {
                        AppRow(
                            app = app,
                            onLaunch = { actions.onLaunchApp(app) },
                            compact = true,
                            onLongClick = {
                                actions.hapticView.confirmHaptic()
                                actions.onPopupAppChange(app)
                            },
                            showInfoIcon = isPopupExpandedX,
                            onInfoClick = {
                                openAppDetailsPage(actions.context, app.packageName)
                                actions.onPopupAppChange(null)
                            },
                            onHide = actions.onHideApp,
                            actionsVisible = actions.expandedActionsPkg == app.packageName,
                            onActionsVisibleChange = { expanded ->
                                actions.onExpandedActionsChange(if (expanded) app.packageName else if (actions.expandedActionsPkg == app.packageName) null else actions.expandedActionsPkg)
                            }
                        )
                        AnimatedVisibility(
                            visible = isPopupExpandedX,
                            enter = expandVertically() +
                                fadeIn(),
                            exit = shrinkVertically() +
                                fadeOut()
                        ) {
                            AppShortcutInline(
                                app = app,
                                onDismiss = { actions.onPopupAppChange(null) }
                            )
                        }
                    }
                }
            }
        }
        // 折叠组（421 安静版）：组底确认行——展开态才有；不点就是
        // 认了（组一直留着），点“取消折叠”即压制＋收起，组消失、
        // 成员回 A-Z，N 再多 10 个时安静重现（不弹任何东西）。
        if (foldExpandedState) {
            item(key = "fold-foot") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 64.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "这些应用 90 天没打开，已折起来",
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Hint,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        UiPrefs.setFoldLastDeclinedN(actions.context, inactiveTargets.size)
                        onFoldLastDeclinedNChange(inactiveTargets.size)
                        onFoldExpandedChange(false)
                        UiPrefs.setFoldExpanded(actions.context, false)
                    }) {
                        Text(
                            text = "取消折叠",
                            color = AILauncherColors.current.Body,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        item(key = "sp:fold") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * 隐藏组段（原 1985–2174 行）：锁定占位 / 解锁后列表 / 一键隐藏 90 天不活跃。
 */
fun LazyListScope.AppCenterHiddenSection(
    hiddenApps: List<AppInfo>,
    hiddenUnlocked: Boolean,
    inactiveTargets: List<AppInfo>,
    azApps: List<AppInfo>,
    onUnlockHiddenApps: () -> Unit,
    onUnhideApp: (AppInfo) -> Unit,
    actions: AppCenterRowActions,
) {
    // v0.41.18（Bob）：隐藏分组——主列表末尾，像 ABCD 分组一样；
    // v0.41.20（Bob）：不显示"已隐藏"标题文字，只留幽灵图标。
    // 未解锁显示锁定占位，解锁后应用可直接打开（不用先恢复）。
    // 智能 v3：一键隐藏按钮与隐藏块并列——隐藏为空但 N>0 时也进门
    // （即首次清理时可见）；锁门仍只看 hiddenApps（见下）。
    if (hiddenApps.isNotEmpty() || inactiveTargets.isNotEmpty()) {
        // v0.41.32（Bob）：幽灵眼与第一行同行，不单独占行
        // 智能 v3：隐藏为空时不画锁占位（否则 N>0 且无隐藏时会冒出
        // “点击解锁查看”）；锁占位只在确实有隐藏应用时出现。
        if (hiddenApps.isNotEmpty() && !hiddenUnlocked) {
            // 锁定占位：点按触发面部/指纹
            // v0.41.21（Bob）：锁定占位按应用行样式——40dp 圆角锁图标 + 15sp 文字，
            // 与上方应用行视觉统一；点按整行触发面部/指纹解锁。
            item(key = "hidden-locked") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        // 体检批D ⑨：幽灵占位（alpha 0.14 纯视觉）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Title.copy(alpha = 0.14f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .size(36.dp)
                            .semantics { invisibleToUser() }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUnlockHiddenApps() }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AILauncherColors.current.Divider),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            // 体检批D ⑨：装饰图标（同行文字「点击解锁查看」已表达）标装饰语义
                            contentDescription = null,
                            tint = AILauncherColors.current.Hint,
                            modifier = Modifier
                                .size(22.dp)
                                .semantics { invisibleToUser() }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "点击解锁查看",
                        fontSize = AILauncherType.BodyLarge.fontSize,
                        color = AILauncherColors.current.Title
                    )
                    }
                }
            }
        } else {
            // 解锁后：隐藏应用像正常应用一样直接打开
            // v0.41.32：幽灵眼与第一行同行
            itemsIndexed(hiddenApps, key = { _, app -> "h:${app.packageName}" }) { index, app ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        // 体检批D ⑨：幽灵占位（alpha 0/0.14 纯视觉）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Title.copy(alpha = if (index == 0) 0.14f else 0f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .size(36.dp)
                            .semantics { invisibleToUser() }
                    )
                    // v0.68.53（Bob）：生长式
                    val isPopupExpandedX = actions.popupApp?.packageName == app.packageName
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isPopupExpandedX) AILauncherColors.current.Card
                                else Color.Transparent
                            )
                    ) {
                    AppRow(
                        app = app,
                        onLaunch = { actions.onLaunchApp(app) },
                        compact = true,
                        onLongClick = {
                            actions.hapticView.confirmHaptic()
                            actions.onPopupAppChange(app)
                        },
                        showInfoIcon = isPopupExpandedX,
                        onInfoClick = {
                            openAppDetailsPage(actions.context, app.packageName)
                            actions.onPopupAppChange(null)
                        },
                        onHide = {}, // 隐藏分组内：右滑=取消隐藏
                        onUnhide = onUnhideApp,
                        actionsVisible = actions.expandedActionsPkg == app.packageName,
                        onActionsVisibleChange = { expanded ->
                            // v0.41.23：与 revealedHidePkg 互斥
                            if (expanded) {
                                actions.onExpandedActionsChange(app.packageName)
                                actions.onRevealedHideChange(null)
                            } else if (actions.expandedActionsPkg == app.packageName) {
                                actions.onExpandedActionsChange(null)
                            }
                        },
                        hideVisible = actions.revealedHidePkg == app.packageName,
                        onHideVisibleChange = { visible ->
                            if (visible) {
                                actions.onRevealedHideChange(app.packageName)
                                actions.onExpandedActionsChange(null)
                            } else if (actions.revealedHidePkg == app.packageName) {
                                actions.onRevealedHideChange(null)
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = isPopupExpandedX,
                        enter = expandVertically() +
                            fadeIn(),
                        exit = shrinkVertically() +
                            fadeOut()
                    ) {
                        AppShortcutInline(
                            app = app,
                            onDismiss = { actions.onPopupAppChange(null) }
                        )
                    }
                    }
                }
            }
        }
        // 智能 v3：一键隐藏 90 天不活跃——隐藏列表之后；仅 N>0 时出现；
        // 点按时实时重算目标（防点按前刚启动过），用现有 HiddenApps.hide
        // 逐个隐藏；隐藏后版本 +1，列表刷新、N 归零，按钮随之消失。
        // 固定位绝不计入（显示与点按两次都按置顶集合排除）。
        if (inactiveTargets.isNotEmpty()) {
            item(key = "hide-inactive") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val pinned = UiPrefs.getDockPinned(actions.context).toSet()
                            val hiddenNow = HiddenApps.getHidden(actions.context)
                            val now = System.currentTimeMillis()
                            val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000
                            azApps.filter { app ->
                                app.packageName !in pinned &&
                                    app.packageName !in hiddenNow &&
                                    AppUsageTracker.lastUsed(actions.context, app.packageName).let { it == 0L || now - it > ninetyDaysMs }
                            }.forEach { HiddenApps.hide(actions.context, it.packageName) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AILauncherColors.current.Accent,
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "一键隐藏 90 天不活跃的应用（${inactiveTargets.size} 个）",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "藏起来的应用随时可以在这里恢复",
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Hint
                    )
                }
            }
        }
        item(key = "sp:hidden") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * 吸顶覆盖层（原 2178–2249 行）：Dock ★ / 隐藏眼 / 折叠“不常用” /
 * 字母幽灵。464 起未读组无吸顶标题（组头已删，Bob 2026-11-08 18:37），
 * 未读段不参与吸顶 overlay；行内信封的隐藏仍由 stuckUnread 控制。
 * BoxScope：overlay 用 align(TopStart) 定位。
 * stuckState 在函数内一次性解构，段内引用名与原来一致。
 */
@Composable
fun BoxScope.AppCenterStuckOverlays(
    stuckState: AppCenterStuckState,
    searching: Boolean,
    hiddenUnlocked: Boolean,
    isLandscapeAc: Boolean,
) {
    val (
        stuckLetter, stuckPushPx, animatedPushPx, stuckHidden, stuckFold,
        foldPushPx, animatedFoldPushPx, stuckDock, dockPushPx, animatedDockPushPx,
        stuckLetterAlpha, stuckDockAlpha, stuckHiddenAlpha, stuckFoldAlpha
    ) = stuckState
    // 注：stuckUnread 不在此解构——它只驱动未读首行行内信封的隐藏
    // （AppCenterUnreadSection），吸顶 overlay 里不再用它。
    // v0.41.8：悬停幽灵字母——当前组首行刚开始滚出就接管显示（427），
    // 下一组首行接近时被顶走；外层 clipToBounds 保证不会顶到搜索栏上面（Bob）。
    // 应用中心升级：透明度跟行内幽灵字母同为 0.35；Bob 2026-11-03 起编辑态不再隐藏
    val sl = stuckLetter
    // Bob 2026-11-03：搜索态无分组可指示，悬停字母不显示
    // （扁平结果无组概念；仅键盘弹出未输入时仍显示）
    if (!searching) {
        if (stuckDock) {
            // v0.41.31：Dock 分区悬停——幽灵 ★（与第一行幽灵一致）
            Icon(
                imageVector = Icons.Filled.Star,
                // 体检批D ⑨：悬停幽灵（纯视觉）标装饰语义
                contentDescription = null,
                tint = AILauncherColors.current.Title.copy(alpha = 0.14f * stuckDockAlpha),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
                    .size(36.dp)
                    .semantics { invisibleToUser() }
                    .offset { IntOffset(0, animatedDockPushPx.roundToInt()) }
            )
        } else if (stuckHidden) {
            // 隐藏分组悬停：幽灵眼（与隐藏头里的图标一致）
            Icon(
                imageVector = if (hiddenUnlocked) Icons.Filled.VisibilityOff
                    else Icons.Filled.VisibilityOff,
                // 体检批D ⑨：悬停幽灵（纯视觉）标装饰语义
                contentDescription = null,
                tint = AILauncherColors.current.Title.copy(alpha = 0.14f * stuckHiddenAlpha),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
                    .size(36.dp)
                    .semantics { invisibleToUser() }
            )
        } else if (stuckFold) {
            // 折叠组（419）：折叠段悬停——“不常用”文字（不是字母），
            // 永远报你在哪一段；Callout 档（14sp）以便 3 字塞进幽灵留白
            Text(
                text = "不常用",
                fontSize = AILauncherType.Callout.fontSize,
                color = AILauncherColors.current.Title.copy(alpha = 0.35f * stuckFoldAlpha),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
                    .offset { IntOffset(0, animatedFoldPushPx.roundToInt()) }
            )
        } else if (sl != null && !isLandscapeAc) {
            Text(
                text = sl.toString(),
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                color = AILauncherColors.current.Title.copy(alpha = 0.35f * stuckLetterAlpha),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
                    .offset { IntOffset(0, animatedPushPx.roundToInt()) }
            )
        }
    }
}

/**
 * Rail 段（原 2250–2436 行）：LaunchedEffect(glideTarget) + AppRail + WaveRail。
 * 仅 rail 用的本地状态（mirrorIndex / glideTarget / latestGlideLetter /
 * housePull / eyePull 等）一并移入本函数；门条件原样保留在函数内。
 */
@Composable
fun BoxScope.AppCenterRailSection(
    listState: LazyListState,
    letters: List<Char>,
    letterAnchors: Map<Char, Int>,
    dockApps: List<AppInfo>,
    hiddenApps: List<AppInfo>,
    foldVisible: Boolean,
    unreadVisible: Boolean,
    foldHeaderIndex: Int,
    hiddenHeaderIndex: Int,
    unreadFirstIndex: Int,
    homeTarget: UiPrefs.HomeRailTarget?,
    searchResults: List<AppInfo>?,
    flashTick: Int,
    flashTarget: String,
    scope: CoroutineScope,
    hapticView: View,
    hiddenUnlocked: Boolean,
    onHeaderSwipeRight: () -> Unit,
    onUnlockHiddenApps: () -> Unit,
) {
    // 应用中心底部上滑已删除（Bob 2026-11-03：避免操作干扰）。
    // Rail：波浪字母导航（v0.24，学 Niagara/参考视频）——只做定位
    // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp）
    // Bob 2026-11-03 回滚：字母导航回 409 加过滤前（字母/锚点只按完整列表；搜索态 rail 按上一条隐藏）
    // v0.24：点按跳字母（居中）；拖动时波浪跟手，列表跟手滚动切换字母
    // v0.40.2：右侧一条可见 rail + 左侧隐形触发区（Bob）——
    // 左边不摆 rail（摆两个很奇怪），但左侧滑动同样触发字母切换，
    // 触发后是右侧 rail 亮起波浪跟随；应用列表左侧缩进给手指留空间。
    // v0.67.11（Bob）：主屏 picker 会话中隐藏自己的 rail，避免与 picker 重叠
    // Bob 2026-11-03：搜索后隐藏字母导航——可见 rail 与左侧隐形触发区
    // 同在此门内，一并隐藏；搜索结束恢复全量字母
    // 折叠组（419）：字母全被折掉时 rail 仍可达折叠专位（否则 Fold
    // 专位在恰恰需要它时不可达）；空目录时 letters 与 fold 皆空，门仍关。
    if ((letters.isNotEmpty() || foldVisible || unreadVisible) && homeTarget == null && searchResults == null) {
        // 左侧隐形区触发时，右侧可见 rail 跟随显示波浪
        var mirrorIndex by remember { mutableStateOf<Int?>(null) }
        // Niagara 做法：直接滚完整列表，不做内容过滤；松手时列表本来就在位置上，零位移
        // v0.68.93（布局与交互 Loop 一批②）：落位改「最新目标合并」——旧实现每过一个
        // 字母就 cancel 旧协程再新起一个：近字母 animateScrollToItem（动画扫过中间
        // 内容）、远字母 scrollToItem（瞬跳），拖动中两者交替取消/重启，列表一帧
        // 一变，肉眼就是刷新感/乱（Bob 真机点名）。主流桌面（iOS 通讯录/小米索引）
        // 的拖动态只认最新字母：这里只记目标，单个 effect 等一帧把同帧内的多次
        // 改向合并成一次瞬时落位，列表位置与手指字母严格对应，不再有动画扫掠
        // 与重启风暴；点按字母同走此路（瞬时落位，与通讯录一致）。
        var glideTarget by remember { mutableStateOf<Char?>(null) }
        var latestGlideLetter by remember { mutableStateOf<Char?>(null) }
        LaunchedEffect(glideTarget) {
            val letter = glideTarget ?: return@LaunchedEffect
            withFrameNanos { }
            val anchor = letterAnchors[letter] ?: return@LaunchedEffect
            if (listState.firstVisibleItemIndex != anchor ||
                listState.firstVisibleItemScrollOffset != 0
            ) {
                listState.scrollToItem(anchor)
            }
        }
        val glideToLetter: (Char) -> Unit = { letter ->
            latestGlideLetter = letter
            glideTarget = letter
        }
        // v0.68.6（Bob）：房子停留确认——滑到房子后停留450ms才回首页，
        // v0.68.20（Bob）：手势驱动——拉拽进度替代停留时长
        var housePull by remember { mutableStateOf(0f) }
        var eyePull by remember { mutableStateOf(0f) }
        var housePullCommitted by remember { mutableStateOf(false) }
        var eyePullCommitted by remember { mutableStateOf(false) }
        // v0.68.21：触发后需松手才重新 armed，防手指不松连触两次
        var housePullArmed by remember { mutableStateOf(true) }
        var eyePullArmed by remember { mutableStateOf(true) }
        var lastHousePull by remember { mutableStateOf(0f) }
        var lastEyePull by remember { mutableStateOf(0f) }
        val stopGlide: () -> Unit = {
            // v0.68.93：松手收尾——最后一次改向的目标可能还差一帧没落位
            // （effect 等帧期间松手），按最后记录的字母补一次瞬时落位，
            // 已在位则无操作；下面的 pull 复位沿旧口径。
            latestGlideLetter?.let { letter ->
                val anchor = letterAnchors[letter]
                if (anchor != null && listState.firstVisibleItemIndex != anchor) {
                    scope.launch { listState.scrollToItem(anchor) }
                }
            }
            if (!housePullCommitted) {
                housePull = 0f
                lastHousePull = 0f
            }
            if (!eyePullCommitted) {
                eyePull = 0f
                lastEyePull = 0f
            }
            housePullArmed = true
            eyePullArmed = true
        }
        // v0.41.31（Bob）：Rail 顶部 ★ → Dock 分区，底部眼 → 隐藏分区
        // v0.67.12（Bob）：改用共享 AppRail，单一样式单布局
        // v0.68.6（Bob）：房子停留确认——滑到房子后停留450ms才回首页，
        // 给操作反应时间，避免滑过时误触太突然；手指移开或松手即取消
        // v0.67.13（Bob）：房子/收藏/隐藏做进 rail 滑动项，一条导航滑到哪去哪
        // 字母导航完全统一（Bob 2026-10-25 追问）：项目表走三处共用的
        // buildRailItems（RailItems.kt），构造与此前逐字一致，仅换调用。
        // Bob 2026-11-03 回滚：👁 门回 409 加过滤前——只看隐藏分区非空
        // （搜索态 rail 按上一条已隐藏，此门不再区分搜索态）
        val railItems = remember(dockApps, letters, hiddenApps, foldVisible, unreadVisible) {
            buildRailItems(dockApps.isNotEmpty(), letters, hiddenApps.isNotEmpty(), hasFold = foldVisible, hasUnread = unreadVisible)
        }
        // 收藏/隐藏时的脉冲目标
        val pulseItem = when (flashTarget) {
            "star" -> RailItem.Star
            "eye" -> RailItem.Eye
            else -> null
        }
        val onRailItem: (RailItem) -> Unit = { item ->
            // 466（Bob 2026-11-08 20:11 rail 滑不到收藏和未读）：非字母项
            // 不是 glide——松手时 stopGlide 不许弹回任何字母。latestGlideLetter
            // 只在字母路径（glideToLetter）赋值、从未清掉，滑过字母再到
            // ★/✉ 时松手补位会把列表弹回上一个字母；这里清掉两者，松手
            // 补位便不再触发。竞态：清 glideTarget 会取消 LaunchedEffect 里
            // 待落位的字母（effect 重启后 glideTarget 为 null 即返回）；
            // 若字母已落位，★/✉ 的 scrollToItem 在后发出、以 ★/✉ 为准。
            // 字母路径不动（glideToLetter 重新记下），字母间拖动仍跟手。
            if (item !is RailItem.Letter) {
                latestGlideLetter = null
                glideTarget = null
            }
            when (item) {
                // v0.68.20：房子不再用停留触发，改上拉手势（onHousePull）
                is RailItem.House -> {}
                // 应用中心升级：★/👁 改瞬时落位，与字母一致
                // （此前动画滚动与字母瞬跳交替）
                is RailItem.Star -> scope.launch { listState.scrollToItem(0) }
                is RailItem.Eye -> scope.launch { listState.scrollToItem(hiddenHeaderIndex) }
                // 折叠组（419）：收纳盒专位 → 折叠组头锚点
                is RailItem.Fold -> scope.launch { listState.scrollToItem(foldHeaderIndex) }
                // 未读组：信封专位 → 未读首行锚点（464 起组头已删）
                is RailItem.Unread -> scope.launch { listState.scrollToItem(unreadFirstIndex) }
                is RailItem.Letter -> glideToLetter(item.char)
                is RailItem.Separator -> {}
            }
        }
        // v0.68.20（Bob）：房子上拉——手指驱动 0→1，到阈值直接回首页；滑回则反向
        // v0.68.25（Bob）：消散段先去掉，到阈值直接回主页
        val onHousePull: (Float) -> Unit = { p ->
            if (!housePullCommitted && housePullArmed) {
                val cp = p.coerceIn(0f, 1f)
                housePull = cp
                if (cp >= 0.5f && lastHousePull < 0.5f) {
                    hapticView.tickHaptic()
                }
                if (cp >= 1f) {
                    housePullCommitted = true
                    housePullArmed = false
                    hapticView.confirmHaptic()
                    housePull = 0f
                    lastHousePull = 0f
                    housePullCommitted = false
                    onHeaderSwipeRight()
                }
                lastHousePull = cp
            }
        }
        // v0.68.20（Bob）：眼睛下拉——到阈值直接弹生物识别，带动画+震动
        val onEyePull: (Float) -> Unit = { p ->
            if (!eyePullCommitted && eyePullArmed && !hiddenUnlocked) {
                val cp = p.coerceIn(0f, 1f)
                eyePull = cp
                if (cp >= 0.5f && lastEyePull < 0.5f) {
                    hapticView.tickHaptic()
                }
                if (cp >= 1f) {
                    eyePullCommitted = true
                    eyePullArmed = false
                    hapticView.confirmHaptic()
                    onUnlockHiddenApps()
                    eyePull = 0f
                    lastEyePull = 0f
                    eyePullCommitted = false
                }
                lastEyePull = cp
            }
        }
        AppRail(
            items = railItems,
            onActiveItem = onRailItem,
            onRelease = stopGlide,
            forcedActiveIndex = mirrorIndex,
            pulseTick = flashTick,
            pulseItem = pulseItem,
            housePull = housePull,
            eyePull = eyePull,
            onHousePull = onHousePull,
            onEyePull = onEyePull,
            // 应用中心升级（Bob 定稿①）：竖屏跟进 B 方案全套——
            // 14sp / 16sp 行高 / 1.0 / Normal（329 起），与横屏同款
            // （railFraction 仍 0.85；高度改跟随列表容器，bottom 70dp 保留）
            railHeight = null,
            railFraction = 0.85f,
            letterFontSize = 14.sp,
            letterLineHeight = 16.sp,
            letterAlpha = 1f,
            letterFontWeight = FontWeight.Normal,
            railWidth = 48.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 72.dp)
        )
        WaveRail(
            items = railItems,
            side = RailSide.LEFT,
            visible = false,
            railWidth = 56.dp,
            onActiveItem = { item ->
                mirrorIndex = railItems.indexOf(item)
                onRailItem(item)
            },
            onRelease = { mirrorIndex = null },
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight()
        )
        // v0.41.20（Bob）：右下角眼睛按钮已删除——隐藏分组在主列表末尾，
        // 直接滚动到底就能看到（未解锁显示锁定占位）。
    }
}

/**
 * 选择器 bottom sheet（原 2460–2514 行）。挂在 AppCenterContent 层，两向共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCenterChooserSheet(
    chooserApps: List<AppInfo>,
    onChooserPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AILauncherColors.current.Card,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "这个场景要用什么应用去响应？",
                fontSize = AILauncherType.BodyLarge.fontSize,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.current.Title,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(chooserApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChooserPick(app) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = app.label.toString(),
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            color = AILauncherColors.current.Title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
