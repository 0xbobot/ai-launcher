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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * v0.68.65（Bob）：横屏应用中心 v3——按 Bob 想法重做。
 * - 左：A-Z 全高字母数列（占满高度，无顶栏分割）
 * - 顶：紧凑搜索（居中 480dp，不占满宽）+ INDEX·款数
 * - 主：横向滑动，每字母一列（240dp），列内应用纵排，列可独立纵滚
 * - 点字母 → 横向平滑滚动到对应列
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

    // 字母 → LazyRow item index
    val letterToIndex = remember(azGroups) {
        azGroups.mapIndexed { idx, (letter, _) -> letter to idx }.toMap()
    }

    Row(modifier = modifier.fillMaxSize()) {
        // 左：A-Z 全高数列
        Column(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(AILauncherColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            letters.forEach { letter ->
                val isSel = letter == selectedLetter
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) AILauncherColors.Accent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                        .clickable {
                            selectedLetter = letter
                            letterToIndex[letter]?.let { idx ->
                                scope.launch { rowState.animateScrollToItem(idx) }
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
        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(AILauncherColors.Divider)
        )
        // 右：内容区
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // 紧凑顶栏：标题 + 居中搜索（不占满宽）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
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
                // 紧凑搜索框（480dp，不占满）
                Row(
                    modifier = Modifier
                        .width(480.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
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
            // 横向字母列
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = azGroups.size,
                    key = { idx -> "col:${azGroups[idx].first}" }
                ) { idx ->
                    val (letter, apps) = azGroups[idx]
                    // 每字母一列（240dp宽），列内可纵滚
                    Column(
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = letter.toString(),
                            fontFamily = FontFamily.Serif,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AILauncherColors.Title.copy(alpha = 0.4f),
                            modifier = Modifier.padding(
                                start = 8.dp, bottom = 8.dp
                            )
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onLaunch(app) }
                                        .padding(8.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
