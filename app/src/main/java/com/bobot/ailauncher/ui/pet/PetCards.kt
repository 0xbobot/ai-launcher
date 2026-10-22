package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.PetCat
import com.bobot.ailauncher.data.PetItem
import com.bobot.ailauncher.data.PetRepository
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.theme.AILauncherType

/**
 * 体检批 C 从 PetZone.kt 拆出：宠物呈现卡与说话气泡。纯搬移，逻辑/文案/数值逐字未动。
 */

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
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card.copy(alpha = 0.92f)),
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
                        fontSize = AILauncherType.Callout.fontSize,
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
                    text = if (armed) "选一个操作，或右滑收回" else "左滑 → 更多操作 · 右滑 → 记入 Today",
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
            IslandToasts.show("已复制")
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
        IslandToasts.show("「$appName」")
        return
    }
    try {
        AppUsageTracker.recordLaunch(context, packageName)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            IslandToasts.show("无法打开「$appName」")
        }
    } catch (_: Exception) {
        IslandToasts.show("无法打开「$appName」")
    }
}


/**
 * v0.47.0：七仔说话气泡——Mii 风（干净圆润白气泡+小尾巴）+ 游戏化（弹性弹出+打字机）。
 * 七仔有什么话要说，都走这里统一呈现。
 */
@Composable
internal fun SpeechBubble(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    // v0.64.5：编辑模式下点气泡退出编辑模式
    onClick: (() -> Unit)? = null
) {
    // 打字机：一字一字蹦出来（RPG 对话感）
    var shownChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shownChars = 0
        for (i in 1..text.length) {
            delay(40)
            shownChars = i
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
        ) + fadeIn(),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // v0.65.4：小尾巴指向上方七仔（气泡在七仔下方）
            // 玻璃试点批：尾巴填充与气泡体同色（浅色下同为白，与原 Color.White 一致；
            // 颜色在组合作用域先读好，Canvas 绘制 lambda 内不能读 CompositionLocal）
            val tailColor = AILauncherColors.current.Card
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(18.dp, 10.dp)
            ) {
                val tailPath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, 0f)
                    close()
                }
                drawPath(tailPath, tailColor)
            }
            // 玻璃试点批：气泡体改强玻璃——Box + 手动柔和阴影（与 TODAY 卡同手法，
            // v0.63.1 口径不用 Material3 Card）；模拟档 fallback 回原 Card 实色。
            // 形状（18dp）、最大宽 240dp、内边距与文字一律不动，只换材质。
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .glassSurface(
                        kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                        cornerRadius = 18.dp,
                        tintColor = AILauncherColors.current.Card,
                        fallbackColor = AILauncherColors.current.Card
                    )
                    .then(if (onClick != null) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() } else Modifier)
            ) {
                Text(
                    text = text.take(shownChars),
                    fontSize = AILauncherType.Footnote.fontSize,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
