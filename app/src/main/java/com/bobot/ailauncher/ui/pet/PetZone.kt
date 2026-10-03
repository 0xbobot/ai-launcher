package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.GlassTextShadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 宠物区：桌台（宠物本体/ding/思考点/sort-tag/carry）。
 * 放在首页顶栏下方。
 */
@Composable
fun PetZone(
    // v0.29.0：升级提醒宠物化
    hasUpgrade: Boolean = false,
    onUpgradeTap: () -> Unit = {}
) {
    val mood by PetRepository.mood.collectAsState()
    val mouth by PetRepository.mouth.collectAsState()
    val dingText by PetRepository.dingText.collectAsState()
    val carryText by PetRepository.carryText.collectAsState()
    val carryToRight by PetRepository.carryToRight.collectAsState()
    val showDots by PetRepository.showDots.collectAsState()
    val sortText by PetRepository.sortText.collectAsState()
    // v0.19：语义状态——SLEEPY 时眼睛保持闭合（夜晚睡觉）
    val petState by PetRepository.petState.collectAsState()
    // v0.29.0：新通知送达 / 会议临近
    val deliverTick by PetRepository.deliverTick.collectAsState()
    val deliverApp by PetRepository.deliverApp.collectAsState()
    val meetingSoon by PetRepository.meetingSoon.collectAsState()
    var showDeliver by remember { mutableStateOf(false) }
    // 送达徽标显示 3 秒
    LaunchedEffect(deliverTick) {
        if (deliverTick > 0) {
            showDeliver = true
            delay(3000)
            showDeliver = false
        }
    }
    var blinking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // v0.25.6 P0：点按果冻（纯视觉反馈，Bob 拍板）
    var jellyTick by remember { mutableIntStateOf(0) }

    // 定时眨眼
    LaunchedEffect(Unit) {
        while (true) {
            delay(4100)
            blinking = true
            delay(150)
            blinking = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 桌台
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            // ding：alpha 淡入淡出（Box 内不用 AnimatedVisibility，避免 scope 重载解析问题）
            val dingAlpha by animateFloatAsState(
                targetValue = if (dingText != null) 1f else 0f,
                label = "dingAlpha"
            )
            if (dingText != null || dingAlpha > 0.02f) {
                Text(
                    text = dingText.orEmpty(),
                    fontSize = 12.5.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(dingAlpha)
                        .padding(top = 4.dp)
                        .background(
                            Color(0xFF141428).copy(alpha = 0.72f),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            // sort-tag：分类决策展示
            val sortAlpha by animateFloatAsState(
                targetValue = if (sortText != null) 1f else 0f,
                label = "sortAlpha"
            )
            if (sortText != null || sortAlpha > 0.02f) {
                Text(
                    text = sortText.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(sortAlpha)
                        .padding(top = 40.dp)
                        .background(
                            Color(0xFF141428).copy(alpha = 0.78f),
                            RoundedCornerShape(99.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
            // 思考省略号
            if (showDots) {
                ThinkDots(modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp))
            }
            // v0.29.0：升级礼物盒——有新版时七仔头顶抱礼物盒
            if (hasUpgrade) {
                GiftBox(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
            // v0.29.0：新通知送达——头顶冒出应用名小徽标（3 秒）
            if (showDeliver && deliverApp != null) {
                Text(
                    text = deliverApp!!,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .background(Color(0xFF5C8DEF), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            // v0.29.0：会议临近——七仔头顶会议提醒
            if (meetingSoon != null) {
                Text(
                    text = "15 分钟后：$meetingSoon",
                    fontSize = 11.sp,
                    color = AILauncherColors.Title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .background(Color(0xFFFFE9A8), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            // 宠物本体
            PetView(
                mood = mood,
                mouth = mouth,
                blinking = blinking,
                sleepy = petState == PetState.SLEEPY,
                jellyTick = jellyTick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(100.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // v0.29.0：有升级时点按 → 弹更新对话框；否则果冻
                        if (hasUpgrade) onUpgradeTap()
                        else jellyTick++
                    }
            )
            // P0：落地阴影（奶油白在暖灰底上加对比）
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 92.dp)
                    .size(width = 64.dp, height = 12.dp)
                    .background(
                        Color.Black.copy(alpha = 0.12f),
                        CircleShape
                    )
            )
            // carry 小纸条
            val carryAlpha by animateFloatAsState(
                targetValue = if (carryText != null) 1f else 0f,
                label = "carryAlpha"
            )
            if (carryText != null || carryAlpha > 0.02f) {
                val carryX by animateFloatAsState(
                    targetValue = if (carryToRight) 100f else 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.8f
                    ),
                    label = "carryX"
                )
                val density = LocalDensity.current
                Text(
                    text = carryText.orEmpty(),
                    fontSize = 11.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(carryAlpha)
                        .offset {
                            IntOffset(
                                with(density) { carryX.dp.toPx() }.roundToInt(),
                                with(density) { 34.dp.toPx() }.roundToInt()
                            )
                        }
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** 思考中的三个跳动点 */
@Composable
private fun ThinkDots(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { i ->
            var up by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(i * 150L)
                while (true) {
                    up = !up
                    delay(450)
                }
            }
            val dy by animateFloatAsState(
                targetValue = if (up) -9f else 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = 0.6f
                ),
                label = "dot$i"
            )
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, with(density) { dy.dp.toPx() }.roundToInt()) }
                    .size(9.dp)
                    .background(Color.White, RoundedCornerShape(99.dp))
            )
        }
    }
}

/**
 * 宠物呈现卡片：展示一次。
 * 手势：左滑 = 更多（出现操作按钮），右滑 = 更少（收回成标签）。
 * 隐私类：未展开前标题正文打码。
 */
@Composable
fun PetPresentedCard() {
    val item by PetRepository.presented.collectAsState()
    val armed by PetRepository.cardArmed.collectAsState()
    val density = LocalDensity.current
    val threshPx = remember(density) { with(density) { 56.dp.toPx() } }

    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically { with(density) { 26.dp.toPx() }.roundToInt() } + fadeIn(),
        exit = fadeOut() + slideOutVertically { with(density) { 20.dp.toPx() }.roundToInt() }
    ) {
        val cur = item ?: return@AnimatedVisibility
        val masked = cur.cat == PetCat.PRIV && !armed
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .pointerInput(cur.id, armed) {
                    var accumX = 0f
                    var fired = false
                    detectHorizontalDragGestures(
                        onDragStart = { accumX = 0f; fired = false },
                        onDragCancel = { fired = true },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (fired) return@detectHorizontalDragGestures
                            accumX += dragAmount
                            if (kotlin.math.abs(accumX) > threshPx) {
                                fired = true
                                if (accumX < 0) PetRepository.armCard()      // 左滑=多
                                else PetRepository.fileToTab()              // 右滑=少
                            }
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${cur.cat.tabEmoji} ${cur.appName}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cur.cat.color,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = cur.cat.cnName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(cur.cat.color, RoundedCornerShape(99.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                if (cur.isBatchSummary) {
                    // v0.16：汇总卡片——多条分行列出（隐私类保持打码）
                    cur.batchItems.forEach { sub ->
                        Text(
                            text = if (sub.cat == PetCat.PRIV) "${sub.cat.tabEmoji} ${sub.appName} · •••"
                            else "${sub.cat.tabEmoji} ${sub.appName} · ${sub.title}",
                            fontSize = 12.5.sp,
                            color = Color(0xFF6B6B76),
                            lineHeight = 19.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = if (masked) "••••••" else cur.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2C34)
                    )
                    if (cur.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (masked) "••••••（左滑查看完整内容）" else cur.text,
                            fontSize = 12.5.sp,
                            color = Color(0xFF6B6B76),
                            lineHeight = 18.sp
                        )
                    }
                }
                AnimatedVisibility(visible = armed) {
                    PetCardActions(item = cur)
                }
                Text(
                    text = if (armed) "选一个操作，或右滑收回" else "左滑 → 更多操作 · 右滑 → 收到右侧",
                    fontSize = 10.5.sp,
                    color = Color(0xFFA0A0AD),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** 卡片操作按钮：按分类给 2-3 个 */
@Composable
private fun PetCardActions(item: PetItem) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val actions = remember(item.cat, item.isBatchSummary) {
        // v0.16：汇总卡片只有一个"全部完成"
        if (item.isBatchSummary) listOf("全部完成" to false)
        else when (item.cat) {
            PetCat.IMP -> listOf("打开应用" to true, "标为已读" to false, "完成" to false)
            PetCat.WORK -> listOf("打开应用" to true, "完成" to false)
            PetCat.FUN -> listOf("打开应用" to true, "完成" to false)
            PetCat.PRIV -> listOf("复制验证码" to true, "打开应用" to false, "完成" to false)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        actions.forEach { (label, primary) ->
            if (primary) {
                Button(
                    onClick = { onPetAction(context, clipboard, item, label) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = { onPetAction(context, clipboard, item, label) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 12.5.sp, color = Color(0xFF5A4BD6))
                }
            }
        }
    }
}

private fun onPetAction(
    context: Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    item: PetItem,
    label: String
) {
    when (label) {
        "复制验证码" -> {
            clipboard.setText(AnnotatedString(item.text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
        "打开应用" -> {
            openAppForPet(context, item.packageName, item.appName)
            PetRepository.completeItem("打开应用")
        }
        "标为已读", "完成", "全部完成" -> PetRepository.completeItem(label)
    }
}

/** 打开应用（计入常用统计）；日程类没有包名则只 toast */
private fun openAppForPet(context: Context, packageName: String, appName: String) {
    if (packageName.isBlank()) {
        Toast.makeText(context, "「$appName」", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        AppUsageTracker.recordLaunch(context, packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "无法打开「$appName」", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 右侧标签栏：四个文件夹从右边缘露出一点。
 * 手势：标签左滑 = 拉进屏幕展开卡片；标签右滑 = 推出屏幕完成清空。
 * D3 打开时隐藏（避免和 A-Z rail 冲突），由调用方传 dockHidden 控制。
 */
@Composable
fun PetTabsOverlay(dockHidden: Boolean) {
    val filed by PetRepository.filed.collectAsState()
    val density = LocalDensity.current
    val view = LocalView.current

    AnimatedVisibility(
        visible = dockHidden,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PetCat.values().forEach { cat ->
                    val count = filed[cat]?.size ?: 0
                    var dragX by remember { mutableStateOf(0f) }
                    val empty = count == 0
                    // v0.16：系统返回手势冲突——手指按下标签时把该标签 rect 设为
                    // 系统手势排除区（小而一定被系统接受），抬起/取消后清除。
                    // rect 取 view 本地坐标（exclusionRects 要求 view 坐标系）。
                    var tabRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 56.dp)
                            .offset {
                                IntOffset(
                                    with(density) { (36.dp.toPx() + dragX).roundToInt() },
                                    0
                                )
                            }
                            .alpha(if (empty) 0.35f else 1f)
                            .background(
                                cat.color,
                                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                            )
                            .onGloballyPositioned { coords ->
                                val winPos = coords.localToWindow(Offset.Zero)
                                val loc = IntArray(2)
                                view.getLocationInWindow(loc)
                                val l = (winPos.x - loc[0]).roundToInt()
                                val t = (winPos.y - loc[1]).roundToInt()
                                tabRect = android.graphics.Rect(
                                    l, t,
                                    l + coords.size.width, t + coords.size.height
                                )
                            }
                            .pointerInput(cat, empty) {
                                // 排除区管理：按下即设，抬起/取消即清（与拖拽检测并行）
                                if (empty) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown()
                                    tabRect?.let {
                                        view.systemGestureExclusionRects = listOf(it)
                                    }
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        view.systemGestureExclusionRects = emptyList()
                                    }
                                }
                            }
                            .pointerInput(cat, empty) {
                                // 点按展开（备用入口，不与左滑冲突）
                                if (empty) return@pointerInput
                                detectTapGestures(onTap = { PetRepository.expandFromTab(cat) })
                            }
                            .pointerInput(cat, empty) {
                                if (empty) return@pointerInput
                                var accumX = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumX = 0f; dragX = 0f },
                                    onDragCancel = { dragX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumX += dragAmount
                                        dragX = (dragX + dragAmount).coerceIn(-120f, 80f)
                                    },
                                    onDragEnd = {
                                        val dxDp = dragX / density.density
                                        dragX = 0f
                                        if (dxDp < -48) PetRepository.expandFromTab(cat)  // 左滑=多
                                        else if (dxDp > 48) PetRepository.completeTab(cat) // 右滑=少
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(text = cat.tabEmoji, fontSize = 15.sp)
                            Text(
                                text = cat.tabLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        if (!empty) {
                            Text(
                                text = "$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-16).dp, y = (-7).dp)
                                    .background(Color(0xFFFF4D4F), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * v0.29.0：升级礼物盒——Canvas 手绘，七仔头顶。
 * 有新版时显示，点按七仔弹更新对话框。
 */
@Composable
private fun GiftBox(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(44.dp, 40.dp)
    ) {
        val w = size.width
        val h = size.height
        val boxColor = androidx.compose.ui.graphics.Color(0xFFE8734A)
        val ribbonColor = androidx.compose.ui.graphics.Color(0xFFFFD66B)
        // 盒身
        drawRoundRect(
            color = boxColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        // 盒盖
        drawRoundRect(
            color = boxColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        // 纵丝带
        drawRect(
            color = ribbonColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.78f)
        )
        // 横丝带（盖上）
        drawRect(
            color = ribbonColor,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.08f)
        )
        // 蝴蝶结（两个圆）
        drawCircle(
            color = ribbonColor,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.14f)
        )
        drawCircle(
            color = ribbonColor,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.14f)
        )
    }
}
