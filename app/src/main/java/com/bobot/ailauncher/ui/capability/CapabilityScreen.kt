package com.bobot.ailauncher.ui.capability

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppClassifier
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.ClassifyResult
import com.bobot.ailauncher.data.CustomCategories
import com.bobot.ailauncher.data.ResolvedCapability
import com.bobot.ailauncher.data.ResolvedGroup
import com.bobot.ailauncher.data.getFrequentApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

@Composable
fun CapabilityScreen(onOpenAllApps: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    // 本机已安装的真实应用（按 capabilities.json 的包名匹配）；空分组已被丢弃
    val resolvedGroups = remember(refreshTick) { CapabilityRegistry.installedCapabilities(context) }
    var query by remember { mutableStateOf("") }
    val frequent = remember { getFrequentApps(context) }
    var expandedGroups by remember { mutableStateOf(setOf("travel")) } // 出行默认展开
    // AI 智能分类状态
    var classifying by remember { mutableStateOf(false) }
    var classifyProgress by remember { mutableStateOf(0 to 0) }

    fun startAiClassify() {
        if (classifying) return
        classifying = true
        classifyProgress = 0 to 0
        scope.launch {
            when (val r = AppClassifier.classifyAll(context) { done, total ->
                classifyProgress = done to total
            }) {
                is ClassifyResult.Ok -> {
                    CustomCategories.setMappings(context, r.mapping)
                    refreshTick++
                    Toast.makeText(context, "已智能分类 ${r.classifiedCount} 个应用", Toast.LENGTH_SHORT).show()
                }
                is ClassifyResult.Err -> {
                    Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                }
            }
            classifying = false
        }
    }

    val searchResults = remember(query, resolvedGroups) {
        val q = query.trim()
        if (q.isBlank()) null
        else resolvedGroups.flatMap { g -> g.apps.map { g to it } }
            .filter { (group, app) ->
                app.label.contains(q, ignoreCase = true) ||
                    app.capability.label.contains(q) ||
                    group.label.contains(q)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "能力",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = AILauncherColors.Title
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "按你要做的事组织 · 只显示你装了的应用",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "大模型设置",
                        tint = AILauncherColors.Hint
                    )
                }
                TextButton(onClick = { startAiClassify() }) {
                    Text(
                        text = "AI 智能分类",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                }
            }
        }
        item {
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
                        placeholder = { Text("想做什么", color = AILauncherColors.Hint) },
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
        }

        if (searchResults != null) {
            items(searchResults, key = { (_, app) -> app.packageName }) { (group, app) ->
                SearchResultRow(
                    groupLabel = group.label,
                    app = app,
                    onLaunch = { launchResolvedApp(context, app) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        } else {
            // 常用：横向一排真实应用图标
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "常用",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AILauncherColors.Title
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(frequent, key = { it.packageName }) { app ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp)
                                ) {
                                    AppIconImage(
                                        drawable = app.icon,
                                        contentDescription = app.label.toString(),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                AppUsageTracker.recordLaunch(context, app.packageName)
                                                val intent = context.packageManager
                                                    .getLaunchIntentForPackage(app.packageName)
                                                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                if (intent != null) context.startActivity(intent)
                                            }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = app.label.toString(),
                                        fontSize = 11.sp,
                                        color = AILauncherColors.Body,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 意图分组：只渲染本机已安装的真实应用
            items(resolvedGroups, key = { it.id }) { group ->
                val expanded = group.id in expandedGroups
                ResolvedGroupCard(
                    group = group,
                    expanded = expanded,
                    onToggle = {
                        expandedGroups =
                            if (expanded) expandedGroups - group.id
                            else expandedGroups + group.id
                    },
                    onLaunch = { app -> launchResolvedApp(context, app) }
                )
            }
            item {
                TextButton(
                    onClick = onOpenAllApps,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "全部应用 · 按字母查看",
                        color = AILauncherColors.Hint,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        // AI 智能分类进度框
        if (classifying) {
            val (done, total) = classifyProgress
            AlertDialog(
                onDismissRequest = {},
                title = { Text("AI 智能分类", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (total > 0) "正在智能分类…$done/$total" else "正在智能分类…",
                            fontSize = 14.sp,
                            color = AILauncherColors.Body
                        )
                    }
                },
                confirmButton = {}
            )
        }
    }
}

private fun launchResolvedApp(context: Context, app: ResolvedCapability) {
    val ok = CapabilityRegistry.launchResolved(context, app)
    if (ok) AppUsageTracker.recordLaunch(context, app.packageName)
    else {
        Toast.makeText(context, "「${app.label}」暂不可用", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 意图分组行（v0.9 扁平列表样式，按原型 v4 修正）：
 * - 收起态：54dp 单行，透明背景无 Card 包裹，行底一条两端渐隐的细分割线。
 *   左侧色点 + 弱化分类名（13sp 灰）+ 数量；右侧 36dp 真实图标分开排布
 *   （可直接点启动），超出 3 个时末尾放该分类色的 "+n" 描边胶囊。
 *   去掉独立箭头：胶囊点击 / 整行点击都切换展开收起。
 * - 展开态：网格布局不变（4 列图标 + 应用名）。
 */
@Composable
private fun ResolvedGroupCard(
    group: ResolvedGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLaunch: (ResolvedCapability) -> Unit
) {
    val catColor = groupColor(group.id)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clickable { onToggle() }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = group.label,
                    fontSize = 13.sp,
                    color = AILauncherColors.Hint,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = group.apps.size.toString(),
                    fontSize = 11.sp,
                    color = AILauncherColors.Hint,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!expanded) {
                    CollapsedAppStrip(
                        apps = group.apps,
                        catColor = catColor,
                        onLaunch = onLaunch,
                        onExpand = onToggle
                    )
                } else {
                    Text(
                        text = "收起",
                        fontSize = 13.sp,
                        color = catColor,
                        modifier = Modifier.clickable { onToggle() }
                    )
                }
            }
            // 收起态行底细分割线（两端渐隐，参考原型 .shelf1-line）
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.12f to AILauncherColors.Divider,
                                0.88f to AILauncherColors.Divider,
                                1f to Color.Transparent
                            )
                        )
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    group.apps.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { app ->
                                ResolvedAppCell(
                                    app = app,
                                    onLaunch = { onLaunch(app) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
}

/** 分组色（v4：色点 / +n 胶囊描边用；自定义分组走默认灰） */
fun groupColor(id: String): Color = when (id) {
    "ai" -> Color(0xFF5B8DEF)
    "social" -> Color(0xFF3CB54A)
    "travel" -> Color(0xFFE8933D)
    "pay" -> Color(0xFFB45BE8)
    "work" -> Color(0xFF4A7DDB)
    "life" -> Color(0xFF2BB5A0)
    "shop" -> Color(0xFFE86A8A)
    else -> Color(0xFF8A8A93)
}

/**
 * v4 收起态右侧：36dp 真实图标分开排布（直接点启动），
 * 超出 3 个时末尾放 "+n" 分类色描边胶囊（点击展开）。
 */
@Composable
private fun CollapsedAppStrip(
    apps: List<ResolvedCapability>,
    catColor: Color,
    onLaunch: (ResolvedCapability) -> Unit,
    onExpand: () -> Unit
) {
    val shown = if (apps.size > 3) apps.take(3) else apps
    val hidden = apps.size - shown.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        shown.forEach { app ->
            AppIconImage(
                drawable = app.icon,
                contentDescription = app.label.toString(),
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .border(
                        1.dp,
                        Color.Black.copy(alpha = 0.06f),
                        RoundedCornerShape(11.dp)
                    )
                    .clickable { onLaunch(app) }
            )
        }
        if (hidden > 0) {
            MoreCapsule(count = hidden, catColor = catColor, onClick = onExpand)
        }
    }
}

/** "+n" 分类色描边胶囊：点击切换展开 / 收起 */
@Composable
private fun MoreCapsule(
    count: Int,
    catColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .defaultMinSize(minWidth = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, catColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .background(catColor.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = catColor
        )
    }
}

/** 展开态：真实应用图标 + 应用名 */
@Composable
private fun ResolvedAppCell(
    app: ResolvedCapability,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onLaunch() }
            .padding(vertical = 4.dp)
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label.toString(),
            fontSize = 12.sp,
            color = AILauncherColors.Body,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchResultRow(
    groupLabel: String,
    app: ResolvedCapability,
    onLaunch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLaunch() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                drawable = app.icon,
                contentDescription = app.label.toString(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
                Text(text = groupLabel, fontSize = 12.sp, color = AILauncherColors.Hint)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AILauncherColors.Hint)
        }
    }
}

