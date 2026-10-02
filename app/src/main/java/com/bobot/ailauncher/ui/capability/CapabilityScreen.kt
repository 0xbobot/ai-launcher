package com.bobot.ailauncher.ui.capability

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.ResolvedCapability
import com.bobot.ailauncher.data.ResolvedGroup
import com.bobot.ailauncher.data.getFrequentApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors

/** capabilities.json 里的分组 iconName → Material Icon */
fun capabilityIcon(name: String): ImageVector = when (name) {
    "directions_car" -> Icons.Filled.DirectionsCar
    "payments" -> Icons.Filled.Payments
    "work" -> Icons.Filled.Work
    "home" -> Icons.Filled.Home
    "shopping_bag" -> Icons.Filled.ShoppingBag
    else -> Icons.Filled.AutoAwesome
}

@Composable
fun CapabilityScreen(onOpenAllApps: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    // 本机已安装的真实应用（按 capabilities.json 的包名匹配）；空分组已被丢弃
    val resolvedGroups = remember { CapabilityRegistry.installedCapabilities(context) }
    var query by remember { mutableStateOf("") }
    val frequent = remember { getFrequentApps(context) }
    var expandedGroups by remember { mutableStateOf(setOf("travel")) } // 出行默认展开

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
}

private fun launchResolvedApp(context: Context, app: ResolvedCapability) {
    val ok = CapabilityRegistry.launchResolved(context, app)
    if (!ok) {
        Toast.makeText(context, "「${app.label}」暂不可用", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ResolvedGroupCard(
    group: ResolvedGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLaunch: (ResolvedCapability) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    capabilityIcon(group.iconName),
                    contentDescription = null,
                    tint = AILauncherColors.Accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = group.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title,
                    modifier = Modifier.weight(1f)
                )
                if (!expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        group.apps.take(3).forEach { app ->
                            PackageIconPreview(icon = app.icon)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = AILauncherColors.Hint
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
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

/** 折叠行里的 3 个小 App 图标预览（真实图标） */
@Composable
private fun PackageIconPreview(icon: Drawable) {
    val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
    )
}
