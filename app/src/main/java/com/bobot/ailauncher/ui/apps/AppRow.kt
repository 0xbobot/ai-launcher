@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.PetMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.NotificationRepository
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppBadge
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 应用行（从已删除的 AllAppsScreen.kt 迁移，v0.22 分类取消后去掉"移到分组"）。
 * 左滑=更多（快捷操作），右滑=更少（隐藏，需确认）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    app: AppInfo,
    onLaunch: () -> Unit,
    onLongClick: () -> Unit,
    onHide: (AppInfo) -> Unit,
    actionsVisible: Boolean,
    onActionsVisibleChange: (Boolean) -> Unit,
    // v0.41.20（Bob）：右滑=隐藏或取消隐藏——隐藏分组内的应用传 onUnhide，
    // 普通应用 onUnhide 为 null，右滑=隐藏。
    onUnhide: ((AppInfo) -> Unit)? = null,
    // v0.41.23（Bob v4 统一版）：右滑眼睛的保持状态（与 actionsVisible 互斥，列表级管理）
    hideVisible: Boolean = false,
    onHideVisibleChange: (Boolean) -> Unit = {},
    // 应用中心升级：紧凑行——搜索态 / 键盘弹出时纵向 padding 12→8dp
    // （行高 64→56dp），仅调用方传参，默认行不动
    compact: Boolean = false,
    // v0.68.180（Bob）：长按菜单展开时，行尾出 ⓘ（与 Dock 标题行图标左、
    // 名字中、ⓘ 右一致；不要单独加一行菜单）。点 ⓘ 走 onInfoClick
    // （系统应用信息页），不触发行点按；收起时 ⓘ 消失。
    showInfoIcon: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    // 内容深链标识（Bob 2026-11-05 晚）：搜索结果行里，点按会走深链直达
    // 内容的应用出「直达」小标签；只打开首页的没有。调用方按当前查询算好
    // 再传（CapabilityResolution.supportsDeepLink），默认不显示。
    showDeepLinkBadge: Boolean = false,
    // 未读预览行（Bob 2026-11-08）：应用名下弱化小字单行预览（最新通知正文
    // 或未读短信片段，调用方算好再传）；null 时只留应用名，字母组行不受影响。
    preview: String? = null,
    // 未读组左滑=清理（Bob 2026-11-08）：未读行外层包左滑清理行时
    // 传 false，禁用本行的滑动浮层（★/眼睛），避免两套横滑手势抢事件；
    // 禁用后该行在未读组内暂不能收藏/隐藏（读完回字母组后恢复），见收据。
    // 默认 true，字母组行行为一字不变。
    swipeActionsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticView = LocalView.current

    // v0.41.11：openAppDetails/uninstallApp 已移到长按菜单（AppPopupMenu），此处删除

    // v0.35.0：Dock 置顶切换——已置顶则移出，未置顶则追加到末尾（Dock 里按此顺序排最前）
    val dockTick by UiPrefs.dockTick.collectAsState()
    val isPinned = remember(dockTick) { UiPrefs.getDockPinned(context).contains(app.packageName) }
    // 未读角标（Bob 2026-11-07 晚）：通知监听汇总的该包条数；>0 才显示。
    val badgeCounts by NotificationRepository.badgeCounts.collectAsState()
    val badgeCount = badgeCounts[app.packageName] ?: 0

    fun togglePin() {
        val cur = UiPrefs.getDockPinned(context).toMutableList()
        if (app.packageName in cur) {
            cur.remove(app.packageName)
            UiPrefs.setDockPinned(context, cur)
            IslandToasts.show("已从 Dock 移出")
        } else {
            if (cur.size >= 10) {
                IslandToasts.show("Dock 最多置顶 10 个")
                return
            }
            cur.add(app.packageName)
            UiPrefs.setDockPinned(context, cur)
            UiPrefs.flashRail("star")
            IslandToasts.show("已加到 Dock")
        }
    }

    // v0.41.30（Bob 统一双钮版）：左右滑同一效果——内容（图标+标题）弱化到 30%，位置不动；
    // 蒙层中央并排双钮：眼睛（隐藏/取消隐藏）+ ★（收藏/取消收藏），简洁线条图标无底色，居中。
    // 第一段（>56dp 松手）保持蒙层可点；第二段（>120dp）主按钮脉冲，松手直接触发。
    // 左滑到底=★，右滑到底=眼睛。
    val scope = rememberCoroutineScope()
    val dimAnim = remember { Animatable(0f) } // 弱化进度 0→1
    var dragDist by remember { mutableStateOf(0f) } // 左滑为正，右滑为负
    var isDragging by remember { mutableStateOf(false) }
    val revealPx = with(density) { 56.dp.toPx() }
    val fullPx = with(density) { 120.dp.toPx() }
    val maxDragPx = with(density) { 160.dp.toPx() }
    val settleSpring = PetMotion.DragSettle

    // 弱化进度：拖动时跟手，松手后弹簧动画到目标
    val dragDim = (kotlin.math.abs(dragDist) / revealPx).coerceIn(0f, 1f)
    val dimProgress = if (isDragging) dragDim else dimAnim.value
    val contentAlpha = 1f - dimProgress * 0.7f // 最低 30%

    // 第二段武装：左滑到底=★，右滑到底=眼睛；主按钮循环 pulse
    val armedPrimary: String? = when {
        !isDragging -> null
        dragDist > fullPx -> "star"
        dragDist < -fullPx -> "eye"
        else -> null
    }
    // v0.41.31（Bob）：armed pulse——1.0↔1.2 循环，不再是静态 1.3
    val pulseTransition = rememberInfiniteTransition(label = "armedPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val starArmedScale = if (armedPrimary == "star") pulseScale else 1f
    val eyeArmedScale = if (armedPrimary == "eye") pulseScale else 1f

    // v0.41.31（Bob）：双图标 staggered spring 进入——眼睛先（0ms），★ 晚 60ms；
    // scale 0.6→1.0 + alpha 0→1，MediumBouncy
    val eyeEntry = remember { Animatable(0f) }
    val starEntry = remember { Animatable(0f) }
    val entrySpring = PetMotion.PopBounce
    LaunchedEffect(actionsVisible) {
        if (actionsVisible && !isDragging) {
            // 从当前弱化进度开始，避免跳变
            val from = dimProgress
            launch {
                eyeEntry.snapTo(from)
                eyeEntry.animateTo(1f, entrySpring)
            }
            launch {
                delay(60)
                starEntry.snapTo(from)
                starEntry.animateTo(1f, entrySpring)
            }
        } else if (!actionsVisible) {
            eyeEntry.snapTo(0f)
            starEntry.snapTo(0f)
        }
    }
    // 图标显示进度：保持态用 entry 动画，拖动中跟手
    val eyeP = if (actionsVisible && !isDragging) eyeEntry.value else dimProgress
    val starP = if (actionsVisible && !isDragging) starEntry.value else dimProgress

    // v0.41.31（Bob）：点按反馈——scale 1.0→0.75→1.0 快速回弹 + 震动；
    // 图标做完回弹后，蒙层再淡出，内容恢复
    var tappedIcon by remember { mutableStateOf<String?>(null) }
    val tapScale = remember { Animatable(1f) }
    fun hideAll() {
        dragDist = 0f
        onActionsVisibleChange(false)
        onHideVisibleChange(false)
        scope.launch { dimAnim.animateTo(0f, settleSpring) }
    }

    fun onIconTap(which: String, action: () -> Unit) {
        if (tappedIcon != null) return
        tappedIcon = which
        hapticView.tickHaptic()
        scope.launch {
            tapScale.snapTo(1f)
            tapScale.animateTo(0.75f, tween(80))
            tapScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
            )
            tappedIcon = null
            action()
            hideAll()
        }
    }


    fun settleDim(from: Float, to: Float) {
        scope.launch {
            dimAnim.snapTo(from)
            dimAnim.animateTo(to, settleSpring)
        }
    }

    // 外部收起（如另一行展开）时直接复原
    LaunchedEffect(actionsVisible, hideVisible) {
        if (!actionsVisible && !hideVisible) {
            dragDist = 0f
            dimAnim.snapTo(0f)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 未读组左滑=清理时禁用本行滑动（外层左滑清理行接管横滑）；
                // pointerInput 空转时不消费事件，点按/长按照常走，横滑落到外层。
                .pointerInput(app.packageName, swipeActionsEnabled) {
                    if (swipeActionsEnabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragCancel = {
                            isDragging = false
                            val from = dragDim
                            dragDist = 0f
                            settleDim(from, 0f)
                        },
                        onDragEnd = {
                            isDragging = false
                            val from = dragDim
                            when {
                                dragDist > fullPx -> {
                                    // 左滑到底 → ★（收藏/取消收藏）
                                    togglePin()
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                                dragDist < -fullPx -> {
                                    // 右滑到底 → 眼睛（隐藏/取消隐藏）
                                    if (onUnhide != null) onUnhide(app) else onHide(app)
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                                dragDist > revealPx || dragDist < -revealPx -> {
                                    // 第一段 → 保持蒙层+双钮（方向无关，保持标记互斥由 AppCenter 保证）
                                    dragDist = 0f
                                    onActionsVisibleChange(true)
                                    settleDim(from, 1f)
                                }
                                else -> {
                                    dragDist = 0f
                                    settleDim(from, 0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // 左滑 dragAmount 为负，转成正数距离
                            dragDist = (dragDist - dragAmount)
                                .coerceIn(-maxDragPx, maxDragPx)
                        }
                    )
                    }
                }
        ) {
            // 内容：静态不位移，弱化到 30%
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (actionsVisible || hideVisible) hideAll()
                            else onLaunch()
                        },
                        onLongClick = onLongClick
                    )
                    // v0.41.0（B 方案）：行距拉大——纵向 padding 12dp，图标 40dp，呼吸感
                    // Bob 2026-11-03：一屏 8 个——图标 44dp 不变，紧凑纵向 4→8dp（行高 60dp），行间距 4dp（步进 64dp）
                    .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 未读角标：叠在图标右上角、微微压住角；纯视觉覆盖层，不抢行点按；
                // 拖动弱化时跟内容一起淡（与图标同 contentAlpha）。
                Box(modifier = Modifier.size(44.dp)) {
                    AppIconImage(
                        drawable = app.icon,
                        contentDescription = app.label.toString(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .graphicsLayer { alpha = contentAlpha }
                    )
                    if (badgeCount > 0) {
                        AppBadge(
                            count = badgeCount,
                            height = 18.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .graphicsLayer { alpha = contentAlpha }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 未读预览行（Bob 2026-11-08）：应用名下弱化小字单行预览，不抢应用名；
                // 无预览时只留应用名。ⓘ/「直达」在行尾时名字列占满剩余宽（与原来单 Text
                // 的 weight 口径一致），预览跟名字同宽、单行省略；拖动弱化时跟内容一起淡。
                Column(
                    modifier = if ((showInfoIcon && onInfoClick != null) || showDeepLinkBadge) Modifier.weight(1f) else Modifier
                ) {
                    Text(
                        text = app.label.toString(),
                        fontSize = AILauncherType.BodyLarge.fontSize,
                        color = AILauncherColors.current.Title.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (preview != null) {
                        Text(
                            text = preview,
                            fontSize = AILauncherType.Caption.fontSize,
                            lineHeight = AILauncherType.Caption.lineHeight,
                            color = AILauncherColors.current.Hint.copy(alpha = contentAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // 内容深链标识：小字标签「直达」——点按这家直接进到查询的那份
                // 内容。只在搜索结果行出现（调用方按查询算好再传）；拖动弱化
                // 时跟内容一起淡；本身不是可点目标，不抢行点按。
                if (showDeepLinkBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DeepLinkBadge(modifier = Modifier.graphicsLayer { alpha = contentAlpha })
                }
                // v0.68.180：展开态行尾 ⓘ（Dock 标题行同款位置与图标）。
                // IconButton 自带 48dp 触控框；内层 clickable 消费点按，不触发行点按。
                if (showInfoIcon && onInfoClick != null) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            // 体检批D ⑨：装饰（展开态的菜单语境已表达入口）标装饰语义
                            contentDescription = null,
                            tint = AILauncherColors.current.Hint,
                            modifier = Modifier
                                .size(22.dp)
                                .semantics { invisibleToUser() }
                        )
                    }
                }
                // Bob 2026-11-03：行尾轻量箭头已删（无独立点击动作，纯装饰；
                // 删 UI 本身，不只删回调）。展开行只留 ⓘ（413 行为不变）。
            }

            // 蒙层：透明全行覆盖，中央并排双钮；点空白处复原
            if (dimProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AILauncherColors.current.Card.copy(alpha = 0.8f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { hideAll() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // v0.41.34（Bob）：蒙版和应用行一样大——整行白 80%，双钮居中
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 眼睛：隐藏/取消隐藏（简洁线条，无底色；32dp + 图标形投影）
                        // v0.41.31：scale = entry(0.6→1.0) * tap回弹 * armed pulse；alpha 跟 entry
                        val eyeBase = 0.6f + 0.4f * eyeP
                        val eyeTap = if (tappedIcon == "eye") tapScale.value else 1f
                        // v0.44.2（Bob）：方向反了——手指右滑（dragDist<0，手指往右），眼睛往右跟（translationX 为正）
                        val eyeFollowX = if (isDragging && dragDist < 0) -dragDist * 0.5f else 0f
                        val eyeDirAlpha = if (isDragging) { if (dragDist < 0) 1f else 0f } else 1f
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    val s = eyeBase * eyeTap * eyeArmedScale
                                    scaleX = s
                                    scaleY = s
                                    alpha = eyeP * eyeDirAlpha
                                    translationX = eyeFollowX
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        onIconTap("eye") {
                                            if (onUnhide != null) onUnhide(app) else onHide(app)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // 图标形投影：深色副本下移 2dp
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (onUnhide != null) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    // 体检批D ⑨：投影副本（正下方同形图标带正式描述）标装饰语义
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .semantics { invisibleToUser() }
                                )
                                Icon(
                                    imageVector = if (onUnhide != null) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (onUnhide != null) "取消隐藏" else "隐藏",
                                    tint = Color(0xFF5A6C7D),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        // ★：收藏/取消收藏（简洁线条无底色；已在 Dock 用实心金；32dp + 投影）
                        val starBase = 0.6f + 0.4f * starP
                        val starTap = if (tappedIcon == "star") tapScale.value else 1f
                        // v0.44.2（Bob）：手指左滑（dragDist>0，手指往左），★ 往左跟（translationX 为负）
                        val starFollowX = if (isDragging && dragDist > 0) -dragDist * 0.5f else 0f
                        val starDirAlpha = if (isDragging) { if (dragDist > 0) 1f else 0f } else 1f
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    val s = starBase * starTap * starArmedScale
                                    scaleX = s
                                    scaleY = s
                                    alpha = starP * starDirAlpha
                                    translationX = starFollowX
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { onIconTap("star") { togglePin() } }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    // 体检批D ⑨：投影副本（正下方同形图标带正式描述）标装饰语义
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .semantics { invisibleToUser() }
                                )
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = if (isPinned) "移出 Dock" else "加到 Dock",
                                    // 未收藏用半透明金（线条感），已收藏用实金
                                    tint = AILauncherColors.current.Accent.copy(alpha = if (isPinned) 1f else 0.45f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // v0.41.27：方案 B——内容滑动露出底层按钮；右滑隐藏确认框已删除，点眼睛直接隐藏
}

/**
 * 内容深链标识（竖屏 AppRow 与横屏 LandscapeAppRow 共用）：小字标签「直达」。
 * 样式：Caption（12sp，token 最小字号）＋ Hint 色 ＋ 细线描边圆角、无底色，
 * 不抢眼；不用 emoji、不用金色（金色只留主操作）；横向内边距 8dp（8dp 网格），
 * 纵向不另加内边距（字行高 18sp＋描边即约 20dp 高，行内垂直居中）。
 */
@Composable
internal fun DeepLinkBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = AILauncherColors.current.Hint.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "直达",
            fontSize = AILauncherType.Caption.fontSize,
            lineHeight = AILauncherType.Caption.lineHeight,
            color = AILauncherColors.current.Hint,
            maxLines = 1
        )
    }
}

/** 分组 key：a-z/A-Z→大写；中文→拼音首字母大写（GB2312 区位边界法，无需第三方库）；数字及其他→'#' */
internal fun groupKey(label: CharSequence, pinyinInitialOf: (Char) -> Char): Char {
    val c = label.firstOrNull() ?: return '#'
    if (c in 'a'..'z') return c.uppercaseChar()
    if (c in 'A'..'Z') return c
    if (c in '0'..'9') return '#'
    return pinyinInitialOf(c)
}

/** 汉字拼音首字母：GB2312 编码区位与拼音首字母边界对照（i/u/v 不做声母，23 个字母） */
internal fun pinyinInitial(c: Char): Char {
    return try {
        val bytes = c.toString().toByteArray(charset("GB2312"))
        if (bytes.size < 2) return '#'
        val secPos = (bytes[0].toInt() and 0xFF) * 100 + (bytes[1].toInt() and 0xFF) - 16160
        // 上式 = ((b0-160)*100 + (b1-160))，即区位码
        val bounds = intArrayOf(
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
            3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
            4390, 4558, 4684, 4925, 5249, 5590
        )
        val letters = "abcdefghjklmnopqrstwxyz"
        for (i in bounds.indices.reversed()) {
            if (secPos >= bounds[i]) return letters[i].uppercaseChar()
        }
        '#'
    } catch (_: Exception) {
        '#'
    }
}
