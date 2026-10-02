package com.bobot.ailauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 上拉 Dock（v0.11，替代 v0.9 的 DockBar + 三档抽屉）。
 *
 * - 默认首页无 Dock：底部只露出一根横线手柄（34dp）+ 页面圆点
 * - 按住横线上下拖动，四档，松手按速度/位置吸附最近档；点按横线跳到下一档
 * - 第一档：独立悬浮 Dock 卡片（iOS dock 式：左右 16dp、底部 16dp 边距，严格水平居中，
 *   完全脱离屏幕底部），4 个常用大图标（56dp）
 * - 第二档：同一张卡片变高，同一批常用重排成 10 个小图标（2×5，46dp）；
 *   一二档是替换关系：拖动过程中两套排布按进度 q 交叉淡入淡出 + 缩放
 * - 第三档：卡片贴边撑满变全屏（边距→0、底部圆角→0，横线隐藏）；
 *   顶部"常用"横滑一行（10 个）+ 下面全部应用 A-Z 列表 + 右侧弧形 A-Z 导航；搜索框不做
 * - 形变过渡：d2→d3 过程中边距/圆角/底色连续插值，松手弹簧吸附
 * - 全屏时按住顶部"常用"区域可下滑收起；全屏时列表到顶继续下滑 → sheet 接管收起
 * - 玻璃拟态：半透明卡片 + 白色描边；API 31+ 叠加窗口真实背景模糊
 */
enum class PullUpDetent { Handle, Dock4, Dock10, Full }

@Composable
fun PullUpDock(
    detent: PullUpDetent,
    onDetentChange: (PullUpDetent) -> Unit,
    onOpennessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHpx = constraints.maxHeight.toFloat()
        if (screenHpx <= 0f) return@BoxWithConstraints
        val handlePx = with(density) { 34.dp.toPx() }
        // 档位高度由内容实际测量得出（严格压缩，不写死过大）
        var dock4Hpx by remember { mutableFloatStateOf(0f) }
        var dock10Hpx by remember { mutableFloatStateOf(0f) }
        val d1px = handlePx + dock4Hpx
        val d2px = handlePx + dock10Hpx
        fun targetFor(d: PullUpDetent): Float = when (d) {
            PullUpDetent.Handle -> screenHpx - handlePx
            PullUpDetent.Dock4 -> screenHpx - d1px
            PullUpDetent.Dock10 -> screenHpx - d2px
            PullUpDetent.Full -> 0f
        }

        val offsetY = remember(screenHpx) { Animatable(screenHpx - handlePx) }
        var dragging by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        // 常用应用（回到某档时刷新排序）
        val allApps = remember {
            listLaunchableApps(context).filter { it.packageName != context.packageName }
        }
        var usageTick by remember { mutableIntStateOf(0) }
        val top10 = remember(allApps, usageTick) {
            AppUsageTracker.topApps(context, allApps, 10)
        }
        val top4 = remember(top10) { top10.take(4) }

        fun launchApp(app: AppInfo) {
            AppUsageTracker.recordLaunch(context, app.packageName)
            usageTick++
            try {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (intent != null) context.startActivity(intent)
                else Toast.makeText(context, "无法打开「${app.label}」", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "无法打开「${app.label}」", Toast.LENGTH_SHORT).show()
            }
        }

        // 外部档位变化 → 弹簧动画过去
        LaunchedEffect(detent) {
            if (detent != PullUpDetent.Handle) usageTick++
        }
        // 档位目标（含内容测量完成后的修正）→ 弹簧动画过去
        LaunchedEffect(detent, screenHpx, d1px, d2px) {
            offsetY.animateTo(
                targetFor(detent),
                spring(stiffness = Spring.StiffnessMediumLow)
            )
        }

        // 上报开合度（MainScreen 用来渐隐页面圆点）
        LaunchedEffect(screenHpx) {
            snapshotFlow {
                ((screenHpx - offsetY.value - handlePx) / (screenHpx - handlePx))
                    .coerceIn(0f, 1f)
            }
                .distinctUntilChanged { a, b -> abs(a - b) < 0.005f }
                .collect { onOpennessChange(it) }
        }

        // 首次引导：横线轻微上跳一次，提示可以拖（每设备一次）
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("pullup_coach", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("coach_shown", false)) {
                delay(1500)
                if (!dragging && detent == PullUpDetent.Handle) {
                    val bouncePx = with(density) { 80.dp.toPx() }
                    offsetY.animateTo(
                        screenHpx - handlePx - bouncePx,
                        spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    offsetY.animateTo(
                        screenHpx - handlePx,
                        spring(stiffness = Spring.StiffnessMediumLow)
                    )
                }
                prefs.edit().putBoolean("coach_shown", true).apply()
            }
        }

        val velocityTracker = remember { VelocityTracker() }
        fun settle(velocity: Float) {
            scope.launch {
                val visiblePx = screenHpx - offsetY.value
                val ordered = listOf(
                    PullUpDetent.Handle to handlePx,
                    PullUpDetent.Dock4 to d1px,
                    PullUpDetent.Dock10 to d2px,
                    PullUpDetent.Full to screenHpx
                )
                val target = when {
                    velocity < -600f ->
                        ordered.filter { it.second > visiblePx + 1f }
                            .minByOrNull { it.second }?.first ?: PullUpDetent.Full
                    velocity > 600f ->
                        ordered.filter { it.second < visiblePx - 1f }
                            .maxByOrNull { it.second }?.first ?: PullUpDetent.Handle
                    else ->
                        ordered.minByOrNull { abs(it.second - visiblePx) }!!.first
                }
                if (target == detent) {
                    // 回到同一档：直接吸附（LaunchedEffect 不会因 detent 未变而触发）
                    offsetY.animateTo(
                        targetFor(target),
                        spring(stiffness = Spring.StiffnessMediumLow)
                    )
                } else {
                    onDetentChange(target)
                }
            }
        }

        fun Modifier.pullDrag() = pointerInput(screenHpx) {
            detectVerticalDragGestures(
                onDragStart = { _ ->
                    dragging = true
                    velocityTracker.resetTracking()
                },
                onDragEnd = {
                    dragging = false
                    val v = try {
                        velocityTracker.calculateVelocity().y
                    } catch (_: Exception) {
                        0f
                    }
                    settle(v)
                },
                onDragCancel = {
                    dragging = false
                    settle(0f)
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val t = (offsetY.value + dragAmount)
                        .coerceIn(0f, screenHpx - handlePx)
                    scope.launch { offsetY.snapTo(t) }
                }
            )
        }
        fun Modifier.tapCycle() = pointerInput(detent) {
            detectTapGestures(onTap = {
                onDetentChange(
                    when (detent) {
                        PullUpDetent.Handle -> PullUpDetent.Dock4
                        PullUpDetent.Dock4 -> PullUpDetent.Dock10
                        PullUpDetent.Dock10 -> PullUpDetent.Full
                        PullUpDetent.Full -> PullUpDetent.Handle
                    }
                )
            })
        }

        // 全屏时列表到顶继续下滑 → sheet 接管收起
        val nested = remember(screenHpx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput || dragging) return Offset.Zero
                    val atTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    if (available.y > 0f && atTop && offsetY.value > 1f) {
                        val t = (offsetY.value + available.y)
                            .coerceIn(0f, screenHpx - handlePx)
                        val consumed = t - offsetY.value
                        if (consumed > 0.5f) {
                            scope.launch { offsetY.snapTo(t) }
                            return Offset(0f, consumed)
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        // ---- 由 offsetY 派生的连续动画量（拖动跟手，松手弹簧） ----
        val visiblePx = screenHpx - offsetY.value
        // 一二档交叉替换进度（档位高度由内容测量得出）
        val q = if (d2px > d1px + 1f) {
            ((visiblePx - d1px) / (d2px - d1px)).coerceIn(0f, 1f)
        } else 0f
        // d2→d3 形变进度：边距/圆角/底色连续插值；全屏时圆角全部→0 真正 bleed
        val p = ((visiblePx - d2px) / (screenHpx - d2px).coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        val sideM = 16.dp * (1f - p)
        val cornerR = 24.dp * (1f - p)
        // 玻璃拟态：悬浮态更透（壁纸透出），全屏态更实（列表可读）
        val cardBg = lerp(
            Color.White.copy(alpha = 0.66f),
            AILauncherColors.GlassCardStrong,
            p
        )
        val cardShape = RoundedCornerShape(cornerR)
        val showFull = p > 0.5f
        // iOS dock 式独立悬浮：卡片底部与屏幕底边保持 16dp 间隙（p→1 时归零全屏），
        // 卡片高度 = 当前可见高度，左右边距对称 → 严格水平居中
        val bottomGapPx = with(density) { 16.dp.toPx() } * (1f - p)
        val cardTopYPx = screenHpx - bottomGapPx - visiblePx

        // 背景 scrim（接近全屏时出现，点击回到 Dock4）
        if (p > 0.02f && !dragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f * p))
                    .clickable { onDetentChange(PullUpDetent.Dock4) }
            )
        }

        // 卡片本体：独立悬浮（不再整屏位移），高度 = 当前可见高度
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = cardTopYPx }
                    .padding(start = sideM, end = sideM)
                    .height(with(density) { visiblePx.toDp() })
                    .shadow((10 * (1f - p)).dp, cardShape)
                    .clip(cardShape)
                    .background(cardBg)
                    .border(
                        BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.45f * (1f - p))
                        ),
                        cardShape
                    )
            ) {
                if (!showFull) {
                    // 抓手横线（可点按跳档、可拖）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .pullDrag()
                            .tapCycle(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(AILauncherColors.Grabber)
                        )
                    }
                    // 一二档：同一批常用的两种排布，交叉淡入淡出 + 缩放（替换非叠加）；
                    // wrapContentHeight(unbounded=true) 让两套排布在卡片高度约束下仍量出自然高度，
                    // 档位高度 d1/d2 才准确（否则第二档会被裁剪后的尺寸污染）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true)
                            .pullDrag()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { dock4Hpx = it.height.toFloat() }
                                .graphicsLayer {
                                    alpha = 1f - q
                                    val s = 1f - 0.12f * q
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            DockRow4(apps = top4, onLaunch = ::launchApp)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { dock10Hpx = it.height.toFloat() }
                                .graphicsLayer {
                                    alpha = q
                                    val s = 0.88f + 0.12f * q
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            DockGrid10(apps = top10, onLaunch = ::launchApp)
                        }
                    }
                } else {
                    // 全屏：常用横滑一行 + 全部应用（横线隐藏，顶部"常用"区可下滑收起）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .pullDrag(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "常用",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp
                        )
                    ) {
                        items(top10, key = { it.packageName }) { app ->
                            AppIconImage(
                                drawable = app.icon,
                                contentDescription = app.label.toString(),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .clickable { launchApp(app) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "全部应用",
                        fontSize = 12.sp,
                        color = AILauncherColors.Hint,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AllAppsContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        listState = listState,
                        nestedScrollConnection = nested,
                        showSearch = false,
                        showIndexBar = true,
                        topPadding = 0.dp,
                        indexBarHeightFraction = 0.6f
                    )
                }
            }
        }
    }
}

/** 第一档：4 个常用大图标（56dp），只留图标 */
@Composable
private fun DockRow4(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEach { app ->
            AppIconImage(
                drawable = app.icon,
                contentDescription = app.label.toString(),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLaunch(app) }
            )
        }
    }
}

/** 第二档：同一批常用重排成 10 个小图标（2×5，46dp），只留图标 */
@Composable
private fun DockGrid10(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { app ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { onLaunch(app) }
                        )
                    }
                }
            }
        }
    }
}
