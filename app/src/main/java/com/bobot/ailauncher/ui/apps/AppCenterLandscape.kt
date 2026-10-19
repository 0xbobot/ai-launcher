package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * v0.68.66（Bob）：横屏应用中心 v4。
 * v0.68.73（Bob）：字母导航移到右侧（同竖屏）+ 显示完整 A-Z；
 *   字母头占一个完整应用行位（68.dp），融入应用流。
 * - 右 A-Z 全高数列：点按 + 按住上下滑动快速定位（drag → 字母 → 横滚）
 * - 应用区：竖排流——字母占首行位，一列排满往右续列（续列无头），不嵌套滚动
 */
@Composable
fun AppCenterLandscape(
    azGroups: List<Pair<Char, List<AppInfo>>>,
    letters: List<Char>,
    totalCount: Int,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rowState = rememberLazyListState()
    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowH = 68.dp
        // v0.68.73：字母头占一个完整行位（rowH），与应用同高
        val rowsPerCol = ((maxHeight - 8.dp) / rowH).toInt().coerceAtLeast(4)

        // 按字母切列：每个字母从新列顶部开始，字母头占首行位（Bob）
        // 首列：[字母头 rowH][应用...]；续列：无头，应用直接从顶部开始
        val columns = remember(azGroups, rowsPerCol) {
            val cols = mutableListOf<MutableList<FlowItem>>()
            var cur = mutableListOf<FlowItem>()
            fun colCap(): Int = rowsPerCol
            fun flush() {
                if (cur.isNotEmpty()) { cols.add(cur); cur = mutableListOf() }
            }
            azGroups.forEach { (letter, apps) ->
                flush()  // 新字母新起一列
                cur.add(FlowItem.Header(letter))
                var cap = colCap() - 1  // 字母头已占 1 行
                var count = 0
                apps.forEach { app ->
                    if (count >= cap) {
                        flush()
                        // 续列：无头，应用直接顶头排
                        cap = colCap()
                        count = 0
                    }
                    cur.add(FlowItem.App(app)); count++
                }
            }
            flush()
            cols.map { it.toList() }
        }
        // 字母 → 列索引（取该字母首次出现的列）
        val letterToCol = remember(columns) {
            val map = mutableMapOf<Char, Int>()
            columns.forEachIndexed { colIdx, col ->
                col.forEach { item ->
                    if (item is FlowItem.Header && !map.containsKey(item.letter)) {
                        map[item.letter] = colIdx
                    }
                }
            }
            map
        }

        fun scrollToLetter(letter: Char) {
            selectedLetter = letter
            letterToCol[letter]?.let { idx ->
                scope.launch { rowState.animateScrollToItem(idx) }
            }
        }

        Row(Modifier.fillMaxSize()) {
            // 左：内容区
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 紧凑顶栏（唯一的搜索）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INDEX",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = AILauncherColors.Accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$totalCount 款",
                        fontSize = 12.sp,
                        color = AILauncherColors.Hint
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .width(440.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(AILauncherColors.Card.copy(alpha = 0.7f))
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = AILauncherColors.Hint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "搜索应用",
                            fontSize = 14.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 设置齿轮（Bob：横屏时竖屏的齿轮拿掉，放这里）
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.Hint,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onOpenSettings() }
                            .padding(2.dp)
                    )
                }
                // 应用竖排流（单向横滚，不嵌套）
                LazyRow(
                    state = rowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = columns.size,
                        key = { idx -> "col:$idx" }
                    ) { idx ->
                        Column(
                            modifier = Modifier
                                .width(250.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            columns[idx].forEach { item ->
                                when (item) {
                                    is FlowItem.Header -> {
                                        // v0.68.73（Bob）：字母头占一个完整应用行位，融入应用流
                                        Text(
                                            text = item.letter.toString(),
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AILauncherColors.Title,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 12.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.App -> {
                                        val app = item.app
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowH)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onLaunch(app) }
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            AppIconImage(
                                                drawable = app.icon,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = app.label.toString(),
                                                fontSize = 14.sp,
                                                color = AILauncherColors.Title,
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
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(AILauncherColors.Divider)
            )
            // v0.68.73（Bob）：右：A-Z 全高数列（点按 + 滑动定位，同竖屏）
            LetterRail(
                letters = letters,
                selectedLetter = selectedLetter,
                onLetter = ::scrollToLetter,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * v0.68.73（Bob）：右侧字母轨，显示完整 A-Z，点按 + 按住上下滑动快速定位。
 * 无应用的字母置灰不可点。
 */
@Composable
private fun LetterRail(
    letters: List<Char>,  // 有应用的字母（用于定位）
    selectedLetter: Char?,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    var railHeightPx by remember { mutableStateOf(0) }
    // v0.68.73：完整 A-Z，方便滑动定位
    val fullAlphabet = remember { ('A'..'Z').toList() }
    val letterSet = remember(letters) { letters.toSet() }

    fun letterAt(yPx: Float): Char? {
        if (railHeightPx <= 0 || fullAlphabet.isEmpty()) return null
        val idx = ((yPx / railHeightPx) * fullAlphabet.size).toInt()
            .coerceIn(0, fullAlphabet.size - 1)
        val c = fullAlphabet[idx]
        return if (letterSet.contains(c)) c else null
    }

    Column(
        modifier = modifier
            .background(AILauncherColors.Background)
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(fullAlphabet) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> letterAt(offset.y)?.let(onLetter) },
                    onVerticalDrag = { change, _ ->
                        letterAt(change.position.y)?.let(onLetter)
                        change.consume()
                    }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        fullAlphabet.forEach { letter ->
            val hasApps = letterSet.contains(letter)
            val isSel = letter == selectedLetter
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSel) AILauncherColors.Accent.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable(enabled = hasApps) { onLetter(letter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSel -> AILauncherColors.Title
                        hasApps -> AILauncherColors.Hint
                        else -> AILauncherColors.Hint.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

private sealed interface FlowItem {
    data class Header(val letter: Char) : FlowItem
    data class App(val app: AppInfo) : FlowItem
}
