package com.bobot.ailauncher.ui.apps

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全部应用列表（含 A-Z 快速索引）。
 * - 按首字母分组（A-Z，非 A-Z 开头归 "#"），stickyHeader 分组头
 * - 右侧 A-Z 纵条：点按/拖动按 y 坐标定位字母，scrollToItem 跳转；
 *   拖动时中央悬浮大字母指示器，松手 600ms 后渐隐
 * - 搜索态隐藏索引条
 *
 * @param onClose 抽屉模式：非空时顶部显示把手（下滑关闭）+ 关闭按钮；为空时显示普通大标题。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val apps = remember {
        val collator = Collator.getInstance(Locale.CHINA)
        listLaunchableApps(context).sortedWith { a, b ->
            collator.compare(a.label.toString(), b.label.toString())
        }
    }
    val searching = query.trim().isNotBlank()
    val filtered = remember(query, apps) {
        val q = query.trim()
        if (q.isBlank()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    // 首字母分组：A-Z 在前，"#"（非字母开头）最后
    val groups: List<Pair<Char, List<AppInfo>>> = remember(filtered, searching) {
        if (searching) return@remember emptyList()
        val map = linkedMapOf<Char, MutableList<AppInfo>>()
        filtered.forEach { app ->
            val c = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (c in 'A'..'Z') c else '#'
            map.getOrPut(key) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    // 字母 → LazyColumn 首项 index（计入 stickyHeader）
    val letterIndex = remember(groups) {
        val m = mutableMapOf<Char, Int>()
        var idx = 0
        groups.forEach { (letter, list) ->
            m[letter] = idx
            idx += 1 + list.size
        }
        m
    }
    val listState = rememberLazyListState()
    val letters = remember { ('A'..'Z').toList() + '#' }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun jumpTo(letter: Char) {
        activeLetter = letter
        val sortedKeys = letterIndex.keys.sortedWith(compareBy({ it == '#' }, { it }))
        val targetKey = if (letterIndex.containsKey(letter)) letter
        else sortedKeys.firstOrNull { it != '#' && it >= letter } ?: sortedKeys.lastOrNull()
        targetKey?.let { key ->
            letterIndex[key]?.let { idx ->
                scope.launch { listState.scrollToItem(idx) }
            }
        }
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(600)
            activeLetter = null
        }
    }

    fun launchApp(packageName: String) {
        val intent: Intent? = context.packageManager
            .getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AILauncherColors.Background)
            .padding(horizontal = 20.dp)
    ) {
        if (onClose != null) {
            var dragAccum by remember { mutableFloatStateOf(0f) }
            val closeThresholdPx = with(density) { 90.dp.toPx() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { dragAccum = 0f },
                            onDragCancel = { dragAccum = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f) {
                                    dragAccum += dragAmount
                                    change.consume()
                                }
                                if (dragAccum > closeThresholdPx) {
                                    dragAccum = 0f
                                    onClose()
                                }
                            }
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AILauncherColors.Divider)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AILauncherColors.Hint
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = "全部应用",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AILauncherColors.Hint)
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索应用", color = AILauncherColors.Hint) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (searching) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(app = app, onLaunch = { launchApp(app.packageName) })
                    }
                } else {
                    groups.forEach { (letter, list) ->
                        item(key = "header-$letter") {
                            Text(
                                text = letter.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AILauncherColors.Hint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AILauncherColors.Background)
                                    .padding(vertical = 4.dp)
                            )
                        }
                        items(list, key = { it.packageName }) { app ->
                            AppRow(app = app, onLaunch = { launchApp(app.packageName) })
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
            // 右侧 A-Z 快速索引条（搜索态隐藏）
            if (!searching && groups.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                ) {
                    // maxHeight 只在 BoxWithConstraints 内容作用域可见，
                    // 必须在 pointerInput 外先算好像素高度再传入手势闭包
                    val hPx = with(density) { maxHeight.toPx() }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .pointerInput(letters) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val i = ((offset.y / hPx) * letters.size)
                                            .toInt().coerceIn(0, letters.size - 1)
                                        jumpTo(letters[i])
                                    }
                                )
                            }
                            .pointerInput(letters) {
                                detectVerticalDragGestures(
                                    onDragEnd = { /* hideJob 的 600ms 计时负责渐隐 */ },
                                    onVerticalDrag = { change, _ ->
                                        change.consume()
                                        val i = ((change.position.y / hPx) * letters.size)
                                            .toInt().coerceIn(0, letters.size - 1)
                                        jumpTo(letters[i])
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        letters.forEach { letter ->
                            Text(
                                text = letter.toString(),
                                fontSize = 10.sp,
                                color = if (letter == activeLetter) AILauncherColors.Accent
                                else AILauncherColors.Hint,
                                fontWeight = if (letter == activeLetter) FontWeight.Bold
                                else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            // 中央悬浮大字母指示器（alpha 动画实现渐显渐隐）
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (activeLetter != null) 1f else 0f,
                label = "letterIndicatorAlpha"
            )
            if (indicatorAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AILauncherColors.Accent)
                        .alpha(indicatorAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeLetter?.toString().orEmpty(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onLaunch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLaunch() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.label.toString(),
            fontSize = 16.sp,
            color = AILauncherColors.Title
        )
    }
}
