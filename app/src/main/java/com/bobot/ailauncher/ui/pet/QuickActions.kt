package com.bobot.ailauncher.ui.pet

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.apps.loadAppShortcuts
import com.bobot.ailauncher.ui.apps.loadShortcutIcon
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * v0.65.0：TODAY 底部快捷操作组。
 * - 空状态显示 "+ 添加"；点 + → 选应用 → 选快捷方式/打开主应用 → 输短标签 → 保存
 * - 最多直接显示 4 个，更多横向滚动
 * - 点击：有 shortcutId 用 LauncherApps.startShortcut()，否则打开主应用
 * - 普通模式长按删除（震动+确认）；编辑模式拖拽排序、× 角标删除
 * - 数据 DataStore（SharedPreferences）JSON 持久化
 * - 无"快捷操作"标题文字，首次由七仔说话介绍
 */

// ============ 数据 ============

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

// ============ 图标 / 启动 ============

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
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
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

// ============ 主入口：快捷操作区 ============

@Composable
fun QuickActionsRow(
    editMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var items by remember { mutableStateOf(QuickActionsPrefs.load(context)) }
    var showAddFlow by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<QuickActionItem?>(null) }

    // v0.65.0：首次见到快捷操作区 → 七仔说话介绍（只一次，无标题文字）
    LaunchedEffect(Unit) {
        if (GuideManager.tryShow(context, GuideManager.Id.QUICK_ACTIONS)) {
            PetRepository.say("底部点 + 可以添加常用按钮，比如一键拍照、语音聊天")
        }
    }

    // ---- 编辑模式横向拖拽排序（相邻交换，与组拖拽同思路） ----
    val scope = rememberCoroutineScope()
    val buttonWidths = remember { mutableStateMapOf<String, Float>() }
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val dragDx = remember { Animatable(0f) }
    val renderIds = dragOrder ?: items.map { it.id }
    val renderItems = remember(renderIds, items) {
        renderIds.mapNotNull { id -> items.find { it.id == id } }
    }

    fun endDrag(save: Boolean) {
        val o = dragOrder
        if (save && o != null) {
            val reordered = o.mapNotNull { id -> items.find { it.id == id } }
            if (reordered.size == items.size) {
                items = reordered
                QuickActionsPrefs.save(context, reordered)
            }
        }
        scope.launch {
            dragDx.animateTo(0f)
            dragId = null
            dragOrder = null
        }
    }

    fun onDrag(id: String, change: PointerInputChange, amount: Offset) {
        change.consume()
        val cur = dragOrder ?: return
        var idx = cur.indexOf(id)
        if (idx < 0) return
        val myW = buttonWidths[id] ?: 0f
        var newDx = dragDx.value + amount.x
        var newOrder = cur
        // 向左：与前一个交换（拖过相邻中心即换，补偿 offset 保视觉连续）
        if (idx > 0 && newDx < 0) {
            val prevId = cur[idx - 1]
            val centerDist = myW / 2 + gapPx + (buttonWidths[prevId] ?: 0f) / 2
            if (-newDx >= centerDist && centerDist > 0) {
                newOrder = cur.toMutableList().also { Collections.swap(it, idx, idx - 1) }
                newDx += centerDist
                idx -= 1
            }
        }
        // 向右：与后一个交换
        if (idx < newOrder.size - 1 && newDx > 0) {
            val nextId = newOrder[idx + 1]
            val centerDist = myW / 2 + gapPx + (buttonWidths[nextId] ?: 0f) / 2
            if (newDx >= centerDist && centerDist > 0) {
                newOrder = newOrder.toMutableList().also { Collections.swap(it, idx, idx + 1) }
                newDx -= centerDist
            }
        }
        if (newOrder !== cur) dragOrder = newOrder
        scope.launch { dragDx.snapTo(newDx) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 最多直接显示 4 个：按钮最大宽度 = 可用宽度/4
        val buttonMaxW = (maxWidth - 24.dp) / 4
        if (items.isEmpty()) {
            // 空状态：+ 添加（居左，不占整行）
            AddQuickButton(onClick = { showAddFlow = true })
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                renderItems.forEach { item ->
                    key(item.id) {
                        val dragging = dragId == item.id
                        QuickActionButton(
                            item = item,
                            editMode = editMode,
                            maxWidth = buttonMaxW,
                            isDragging = dragging,
                            dragDxPx = if (dragging) dragDx.value else 0f,
                            onWidthMeasured = { w -> if (buttonWidths[item.id] != w) buttonWidths[item.id] = w },
                            onClick = { launchQuickAction(context, item) },
                            onLongPressDelete = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                deleteTarget = item
                            },
                            onDeleteBadge = { deleteTarget = item },
                            onDragStart = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                dragId = item.id
                                dragOrder = items.map { it.id }
                                scope.launch { dragDx.snapTo(0f) }
                            },
                            onDrag = { change, amount -> onDrag(item.id, change, amount) },
                            onDragEnd = { endDrag(save = true) }
                        )
                    }
                }
                // 末尾 + 添加（小尺寸）
                AddQuickButtonSmall(onClick = { showAddFlow = true })
            }
        }
    }

    // 添加流程
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

    // 删除确认
    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个快捷按钮？") },
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

@Composable
private fun QuickActionButton(
    item: QuickActionItem,
    editMode: Boolean,
    maxWidth: Dp,
    isDragging: Boolean,
    dragDxPx: Float,
    onWidthMeasured: (Float) -> Unit,
    onClick: () -> Unit,
    onLongPressDelete: () -> Unit,
    onDeleteBadge: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .then(if (isDragging) Modifier.zIndex(1f) else Modifier)
            .then(
                if (isDragging) Modifier.graphicsLayer {
                    scaleX = 1.06f
                    scaleY = 1.06f
                } else Modifier
            )
            .then(if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier)
            .then(
                if (isDragging && dragDxPx != 0f) Modifier.offset {
                    IntOffset(dragDxPx.roundToInt(), 0)
                } else Modifier
            )
            .onSizeChanged { onWidthMeasured(it.width.toFloat()) }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .then(
                if (editMode) {
                    Modifier.pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, amount -> onDrag(change, amount) }
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongPressDelete
                    )
                }
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuickActionIcon(item = item, size = 22.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.label,
                fontSize = 13.sp,
                color = Color(0xFF1C1C1E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (editMode) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30).copy(alpha = 0.9f))
                        .clickable { onDeleteBadge() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "×", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
@Composable
private fun AddQuickButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "+", fontSize = 16.sp, color = Color(0xFF8E8E93))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "添加", fontSize = 13.sp, color = Color(0xFF8E8E93))
    }
}

@Composable
private fun AddQuickButtonSmall(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+", fontSize = 18.sp, color = Color(0xFF8E8E93))
    }
}

// ============ 添加流程：选应用 → 选快捷方式 → 输标签 ============

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
