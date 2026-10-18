package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * v0.68.64（Bob）：横屏应用中心 v2——平滑滚动版。
 * 左 A-Z 索引，点字母平滑滚动到分组（animateScrollToItem），不替换内容。
 * 右 3 列网格，组头跨列。
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
    val gridState = rememberLazyGridState()

    // 构建扁平列表：[(header, letter), (app, app), ...]，记录每个字母的起始 index
    val flatItems = remember(azGroups) {
        val list = mutableListOf<GridItem>()
        azGroups.forEach { (letter, apps) ->
            list.add(GridItem.Header(letter))
            apps.forEach { app -> list.add(GridItem.App(app)) }
        }
        list
    }
    val letterToIndex = remember(flatItems) {
        val map = mutableMapOf<Char, Int>()
        flatItems.forEachIndexed { idx, item ->
            if (item is GridItem.Header && !map.containsKey(item.letter)) {
                map[item.letter] = idx
            }
        }
        map
    }

    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    Row(modifier = modifier.fillMaxSize()) {
        // 左：A-Z 索引
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "INDEX",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = AILauncherColors.Accent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            letters.forEach { letter ->
                val isSel = letter == selectedLetter
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (isSel) AILauncherColors.Accent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .clickable {
                            selectedLetter = letter
                            letterToIndex[letter]?.let { idx ->
                                scope.launch {
                                    gridState.animateScrollToItem(idx)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) AILauncherColors.Title else AILauncherColors.Hint
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(AILauncherColors.Divider)
        )
        // 右：3 列网格
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INDEX · 全部应用",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.Accent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$totalCount 款",
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    count = flatItems.size,
                    key = { idx ->
                        when (val it = flatItems[idx]) {
                            is GridItem.Header -> "h:${it.letter}"
                            is GridItem.App -> "a:${it.app.packageName}"
                        }
                    },
                    span = { idx ->
                        if (flatItems[idx] is GridItem.Header) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    }
                ) { idx ->
                    when (val item = flatItems[idx]) {
                        is GridItem.Header -> {
                            Text(
                                text = item.letter.toString(),
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = AILauncherColors.Title.copy(alpha = 0.35f),
                                modifier = Modifier.padding(
                                    start = 12.dp, top = 12.dp, bottom = 4.dp
                                )
                            )
                        }
                        is GridItem.App -> {
                            val app = item.app
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onLaunch(app) }
                                    .padding(10.dp)
                            ) {
                                AppIconImage(
                                    drawable = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = app.label.toString(),
                                    fontSize = 14.sp,
                                    color = AILauncherColors.Title,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private sealed interface GridItem {
    data class Header(val letter: Char) : GridItem
    data class App(val app: AppInfo) : GridItem
}
