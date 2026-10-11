package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.apps.loadAppShortcuts
import com.bobot.ailauncher.ui.apps.loadShortcutIcon
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.65.2：快捷操作从 TODAY 卡片移到七仔气泡。
 * - 点七仔 → 气泡展开为快捷面板：一排小图标（36dp）+ 10sp 标签，点即直达
 * - 面板底部小 "+ 添加"（虚线圆）→ 三步添加流程（选应用→选快捷方式→输标签）
 * - 长按图标 → 删除（震动+确认）
 * - 数据层 QuickActionsPrefs 复用不动
 * - 首次点七仔时七仔引导："点图标直达，+ 可以添加常用按钮"
 */

// ============ 数据（v0.65.0，不动） ============

data class QuickActionItem(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val shortcutId: String?, // null = 打开主应用
    val label: String,
    val iconUri: String? = null
)

object QuickActionsPrefs {
    private const val PREFS = "quick_actions_prefs"
    private const val KEY_LIST = "quick_actions_json"

    fun load(context: Context): List<QuickActionItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    QuickActionItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        packageName = o.getString("packageName"),
                        shortcutId = o.optString("shortcutId", null)?.takeIf { it.isNotEmpty() },
                        label = o.optString("label", ""),
                        iconUri = o.optString("iconUri", null)?.takeIf { it.isNotEmpty() }
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, list: List<QuickActionItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("shortcutId", item.shortcutId ?: JSONObject.NULL)
                put("label", item.label)
                put("iconUri", item.iconUri ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }
}

// ============ 图标 / 启动（v0.65.0，不动） ============

private fun Drawable.toImageBitmap(): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp.asImageBitmap()
}

/** 快捷按钮图标：快捷方式图标优先，取不到/主应用则用应用图标；图标实时解析不持久化 */
@Composable
private fun QuickActionIcon(item: QuickActionItem, size: Dp) {
    val context = LocalContext.current
    val bitmap = remember(item.packageName, item.shortcutId) {
        try {
            val d: Drawable? = if (item.shortcutId != null) {
                loadShortcutIcon(context, item.packageName, item.shortcutId)
                    ?: appIconOrNull(context, item.packageName)
            } else {
                appIconOrNull(context, item.packageName)
            }
            d?.toImageBitmap()
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = item.label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE5E0D8))
        )
    }
}

private fun appIconOrNull(context: Context, packageName: String): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) { null }
}

private fun launchQuickAction(context: Context, item: QuickActionItem) {
    try {
        if (item.shortcutId != null) {
            val lm = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            lm.startShortcut(item.packageName, item.shortcutId, null, null, Process.myUserHandle())
        } else {
            val intent = context.packageManager
                .getLaunchIntentForPackage(item.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) context.startActivity(intent)
            else PetRepository.say("打不开这个应用，可能已卸载")
        }
    } catch (_: Exception) {
        PetRepository.say("打不开这个，快检查一下应用还在不在")
    }
}

// ============ v0.65.5：七仔装备——拟物化快捷方式 ============
/**
 * v0.65.5：不要盒子/气泡。快捷图标像"武器装备"一样长在七仔周围——
 * 左右腰间、右上（背着）、左下四个槽位，图标 36dp 圆形 + 4dp 阴影，
 * 每个轻微不同角度，像挂在身上。标签 9sp 无背景。
 * 超 4 个时前 3 个环绕 + 第 4 槽显示"···"，点开展开全部（下方横向浮层，无背景）。
 * 调用方 modifier 对齐七仔位置：align(TopCenter).padding(top = 24.dp)。
 */

/** 装备槽位：相对七仔中心的偏移 + 旋转角度（像挂在身上） */
private data class EquipSlot(val x: Dp, val y: Dp, val rotation: Float)

private val EquipSlots = listOf(
    EquipSlot((-72).dp, 16.dp, -8f),   // 左腰
    EquipSlot(72.dp, 16.dp, 8f),       // 右腰
    EquipSlot(48.dp, (-50).dp, 12f),   // 右上（背着的武器）
    EquipSlot((-56).dp, 58.dp, -10f),  // 左下
)

@Composable
fun QizaiEquipment(
    modifier: Modifier = Modifier,
    onLaunch: () -> Unit = {} // 启动后由调用方决定是否收起
) {
    val context = LocalContext.current
    val view = LocalView.current
    var items by remember { mutableStateOf(QuickActionsPrefs.load(context)) }
    var showAddFlow by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<QuickActionItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    // v0.65.5：首次见到装备 → 七仔介绍（只一次），4 秒后提示消失
    var showEquipHint by remember {
        mutableStateOf(GuideManager.tryShow(context, GuideManager.Id.EQUIPMENT))
    }
    if (showEquipHint) {
        LaunchedEffect(Unit) {
            delay(4000)
            showEquipHint = false
        }
    }

    val overflow = items.size > 4
    val ringItems = if (overflow) items.take(3) else items.take(4)

    Box(modifier = modifier) {
        // 装备环：100dp 盒子与七仔同框，图标用 offset 挂在周围
        if (!expanded) {
            Box(modifier = Modifier.size(100.dp)) {
                ringItems.forEachIndexed { index, item ->
                    val slot = EquipSlots[index]
                    key(item.id) {
                        EquipButton(
                            item = item,
                            rotation = slot.rotation,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(slot.x, slot.y),
                            onClick = {
                                launchQuickAction(context, item)
                                onLaunch()
                            },
                            onLongPress = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                deleteTarget = item
                            }
                        )
                    }
                }
                // 超 4 个：第 4 槽显示"···"，点开展开全部
                if (overflow) {
                    val slot = EquipSlots[3]
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(slot.x, slot.y)
                            .graphicsLayer { rotationZ = slot.rotation }
                            .size(36.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { expanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u00B7\u00B7\u00B7", fontSize = 14.sp, color = Color(0xFF8A837C))
                    }
                }
                // + 添加：右下方小圆圈虚线边
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(56.dp, 58.dp)
                        .clickable { showAddFlow = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFAEAEB2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 16.sp, color = Color(0xFFAEAEB2))
                    }
                }
                // 首次提示：七仔介绍小装备
                if (showEquipHint) {
                    Text(
                        text = "这些是我的小装备，点一下就能用",
                        fontSize = 11.sp,
                        color = Color(0xFF8A837C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 96.dp)
                    )
                }
            }
        } else {
            // 展开态：全部图标横向浮层（无背景），在七仔下方
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 108.dp)
                    .widthIn(max = 300.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                items.forEach { item ->
                    key(item.id) {
                        EquipButton(
                            item = item,
                            rotation = 0f,
                            onClick = {
                                launchQuickAction(context, item)
                                onLaunch()
                            },
                            onLongPress = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                deleteTarget = item
                            }
                        )
                    }
                }
                // + 添加
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showAddFlow = true }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFAEAEB2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 18.sp, color = Color(0xFFAEAEB2))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "添加", fontSize = 9.sp, color = Color(0xFFAEAEB2), maxLines = 1)
                }
                // 收起
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { expanded = false }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u2039", fontSize = 20.sp, color = Color(0xFF8A837C))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "收起", fontSize = 9.sp, color = Color(0xFFAEAEB2), maxLines = 1)
                }
            }
        }
    }

    // 添加流程（三步：选应用→选快捷方式→输标签）
    if (showAddFlow) {
        QuickActionAddFlow(
            onDismiss = { showAddFlow = false },
            onSaved = { item ->
                items = items + item
                QuickActionsPrefs.save(context, items)
                showAddFlow = false
                PetRepository.say("已添加「${item.label}」")
            }
        )
    }

    // 删除确认（长按图标 → 震动+确认）
    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个小装备？") },
            text = { Text("「${target.label}」") },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    items = items.filter { it.id != target.id }
                    QuickActionsPrefs.save(context, items)
                    deleteTarget = null
                    PetRepository.say("已删除")
                }) { Text("删除", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/** v0.65.5：装备按钮——36dp 圆形图标 + 4dp 阴影 + 轻微旋转 + 9sp 标签（无背景） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EquipButton(
    item: QuickActionItem,
    rotation: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            QuickActionIcon(item = item, size = 28.dp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            fontSize = 9.sp,
            color = Color(0xFF5A544E),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 56.dp)
        )
    }
}


// ============ 添加流程：选应用 → 选快捷方式 → 输标签（v0.65.0，不动） ============

@Composable
private fun QuickActionAddFlow(
    onDismiss: () -> Unit,
    onSaved: (QuickActionItem) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var search by remember { mutableStateOf("") }
    var pickedApp by remember { mutableStateOf<AppInfo?>(null) }
    var shortcuts by remember { mutableStateOf<List<ShortcutInfo>?>(null) }
    var pickedShortcut by remember { mutableStateOf<ShortcutInfo?>(null) } // null=打开主应用
    var label by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            try {
                listLaunchableApps(context).sortedBy { it.label.toString() }
            } catch (_: Exception) { emptyList() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.97f))
                .padding(16.dp)
        ) {
            when (step) {
                0 -> {
                    val all = apps
                    Column {
                        Text("选择应用", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("搜索", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        if (all == null) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val filtered = remember(search, all) {
                                if (search.isBlank()) all
                                else all.filter { it.label.contains(search, ignoreCase = true) }
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                items(filtered, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                pickedApp = app
                                                label = app.label.toString()
                                                pickedShortcut = null
                                                shortcuts = null // 切应用时清掉旧快捷方式列表
                                                step = 1
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconDrawable(drawable = app.icon, size = 32.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = app.label.toString(),
                                            fontSize = 14.sp,
                                            color = Color(0xFF1C1C1E),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val app = pickedApp
                    Column {
                        Text("选择快捷方式", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app?.label?.toString().orEmpty(),
                            fontSize = 12.sp,
                            color = Color(0xFFAEAEB2)
                        )
                        Spacer(Modifier.height(8.dp))
                        // 加载快捷方式
                        LaunchedEffect(app?.packageName) {
                            val pkg = app?.packageName ?: return@LaunchedEffect
                            shortcuts = withContext(Dispatchers.IO) {
                                try {
                                    loadAppShortcuts(context, pkg)
                                } catch (_: Exception) { emptyList() }
                            }
                        }
                        val list = shortcuts
                        if (app == null || list == null) {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                // 打开主应用（默认首选）
                                item(key = "main") {
                                    ShortcutRow(
                                        icon = {
                                            app.icon.let { d ->
                                                val bmp = remember(d) {
                                                    try { d.toImageBitmap() } catch (_: Exception) { null }
                                                }
                                                if (bmp != null) Image(
                                                    bitmap = bmp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                                                )
                                            }
                                        },
                                        title = "打开主应用",
                                        subtitle = app.label.toString(),
                                        onClick = {
                                            pickedShortcut = null
                                            label = app.label.toString()
                                            step = 2
                                        }
                                    )
                                }
                                items(list, key = { it.id }) { sc ->
                                    ShortcutRow(
                                        icon = {
                                            ShortcutIconDrawable(
                                                context = context,
                                                packageName = app.packageName,
                                                shortcutId = sc.id,
                                                size = 30.dp
                                            )
                                        },
                                        title = sc.shortLabel?.toString().orEmpty().ifEmpty { sc.id },
                                        subtitle = sc.longLabel?.toString().orEmpty(),
                                        onClick = {
                                            pickedShortcut = sc
                                            label = sc.shortLabel?.toString().orEmpty()
                                                .ifEmpty { app.label.toString() }
                                            step = 2
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { step = 0 }) { Text("‹ 返回选应用", fontSize = 13.sp) }
                    }
                }
                else -> {
                    Column {
                        Text("起个短名字", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = label,
                            onValueChange = { if (it.length <= 8) label = it },
                            singleLine = true,
                            placeholder = { Text("如：拍照、语音聊天", fontSize = 14.sp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (label.isNotBlank()) {
                                    val app = pickedApp ?: return@KeyboardActions
                                    onSaved(
                                        QuickActionItem(
                                            packageName = app.packageName,
                                            shortcutId = pickedShortcut?.id,
                                            label = label.trim()
                                        )
                                    )
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { step = 1 }) { Text("‹ 上一步") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val app = pickedApp ?: return@TextButton
                                    if (label.isBlank()) {
                                        PetRepository.say("起个名字吧")
                                        return@TextButton
                                    }
                                    onSaved(
                                        QuickActionItem(
                                            packageName = app.packageName,
                                            shortcutId = pickedShortcut?.id,
                                            label = label.trim()
                                        )
                                    )
                                }
                            ) { Text("保存", color = Color(0xFF007AFF)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 14.sp, color = Color(0xFF1C1C1E), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, fontSize = 12.sp, color = Color(0xFFAEAEB2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppIconDrawable(drawable: Drawable, size: Dp) {
    val bmp = remember(drawable) {
        try { drawable.toImageBitmap() } catch (_: Exception) { null }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE5E0D8)))
    }
}

@Composable
private fun ShortcutIconDrawable(context: Context, packageName: String, shortcutId: String, size: Dp) {
    val bmp = remember(packageName, shortcutId) {
        try { loadShortcutIcon(context, packageName, shortcutId)?.toImageBitmap() } catch (_: Exception) { null }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5E0D8)))
    }
}
