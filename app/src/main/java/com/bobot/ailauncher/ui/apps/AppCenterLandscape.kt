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
 * - 删竖屏遗留的顶栏搜索，只留一个紧凑搜索
 * - 左 A-Z 全高数列：点按 + 按住上下滑动快速定位（drag → 字母 → 横滚）
 * - 应用区：竖排流——算好一列能排几个，排不满往右续列，不嵌套滚动
 */
@Composable
fun AppCenterLandscape(
    azGroups: List<Pair<Char, List<AppInfo>>>,
    letters: List<Char>,
    totalCount: Int,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rowState = rememberLazyListState()
    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    // 扁平流：Header + Apps，逐字母拼接
    val flatFlow = remember(azGroups) {
        val list = mutableListOf<FlowItem>()
        azGroups.forEach { (letter, apps) ->
            list.add(FlowItem.Header(letter))
            apps.forEach { app -> list.add(FlowItem.App(app)) }
        }
        list
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowH = 62.dp
        val topBarH = 56.dp
        // 一列能排的 item 数（Header 占 1 格）
        val rowsPerCol = ((maxHeight - topBarH - 16.dp) / rowH).toInt().coerceAtLeast(4)

        // 切列
        val columns = remember(flatFlow, rowsPerCol) {
            flatFlow.chunked(rowsPerCol)
        }
        // 字母 → 列索引
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
            // 左：A-Z 全高数列（点按 + 滑动定位）
            LetterRail(
                letters = letters,
                selectedLetter = selectedLetter,
                onLetter = ::scrollToLetter,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(AILauncherColors.Divider)
            )
            // 右：内容区
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 紧凑顶栏（唯一的搜索）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarH)
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
                            .background(Color.White.copy(alpha = 0.7f))
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
                                        Text(
                                            text = item.letter.toString(),
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AILauncherColors.Title.copy(alpha = 0.4f),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .padding(start = 8.dp)
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
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(10.dp))
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
        }
    }
}

/**
 * 左侧字母轨：点按 + 按住上下滑动快速定位。
 */
@Composable
private fun LetterRail(
    letters: List<Char>,
    selectedLetter: Char?,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    var railHeightPx by remember { mutableStateOf(0) }

    fun letterAt(yPx: Float): Char? {
        if (railHeightPx <= 0 || letters.isEmpty()) return null
        val idx = ((yPx / railHeightPx) * letters.size).toInt()
            .coerceIn(0, letters.size - 1)
        return letters[idx]
    }

    Column(
        modifier = modifier
            .background(AILauncherColors.Background)
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> letterAt(offset.y)?.let(onLetter) },
                    onVerticalDrag = { change, _ ->
                        letterAt(change.position.y)?.let(onLetter)
                        change.consume()
                    }
                )
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            val isSel = letter == selectedLetter
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSel) AILauncherColors.Accent.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable { onLetter(letter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) AILauncherColors.Title else AILauncherColors.Hint
                )
            }
        }
    }
}

private sealed interface FlowItem {
    data class Header(val letter: Char) : FlowItem
    data class App(val app: AppInfo) : FlowItem
}
