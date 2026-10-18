package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * v0.68.62（Bob）：横屏双栏应用中心——左 A-Z 索引 + 右 3 列网格。
 * 横屏的宽就该这么用，索引常显不用挤右边缘。
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
    var selectedLetter by remember(letters) {
        mutableStateOf(letters.firstOrNull() ?: 'A')
    }
    val appsForLetter = remember(selectedLetter, azGroups) {
        azGroups.find { it.first == selectedLetter }?.second ?: emptyList()
    }

    Row(modifier = modifier.fillMaxSize()) {
        // 左：A-Z 索引
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "INDEX",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = AILauncherColors.Accent,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            letters.forEach { letter ->
                val isSel = letter == selectedLetter
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) AILauncherColors.Accent.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSel) 2.dp else 0.dp,
                            color = if (isSel) AILauncherColors.Accent else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedLetter = letter },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        fontSize = 16.sp,
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
        // 右：3 列网格
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // 紧凑头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INDEX · 全部应用",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.Accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$totalCount 款",
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$selectedLetter · ${appsForLetter.size} 款",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appsForLetter, key = { it.packageName }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLaunch(app) }
                            .padding(12.dp)
                    ) {
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
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
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
