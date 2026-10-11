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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
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

// ============ v0.65.2：七仔气泡里的快捷面板 ============

/**
 * 七仔气泡展开的快捷面板：白底圆角 + 小尾巴（和 SpeechBubble 同风格）。
 * 一排小图标（36dp）+ 10sp 标签，横向滚动；点即直达，长按删除。
 * 底部小 "+ 添加"（虚线圆）进三步添加流程。
 */
@Composable
fun QuickActionsBubble(
    modifier: Modifier = Modifier,
    onLaunch: () -> Unit = {} // 启动后由调用方决定是否收起面板
) {
    val context = LocalContext.current
    val view = LocalView.current
    var items by remember { mutableStateOf(QuickActionsPrefs.load(context)) }
    var showAddFlow by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<QuickActionItem?>(null) }
    // v0.65.2：首次展开面板 → 顶部小提示（只一次）
    var showHint by remember {
        mutableStateOf(GuideManager.tryShow(context, GuideManager.Id.QUICK_PANEL))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showHint) {
                    Text(
                        text = "点图标直达，+ 可以添加常用按钮",
                        fontSize = 11.sp,
                        color = Color(0xFFAEAEB2),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    items.forEach { item ->
                        key(item.id) {
                            QuickBubbleButton(
                                item = item,
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
                    // + 添加（虚线圆）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showAddFlow = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = Color(0xFFAEAEB2),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "+", fontSize = 20.sp, color = Color(0xFFAEAEB2))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "添加",
                            fontSize = 10.sp,
                            color = Color(0xFFAEAEB2),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        // 小尾巴指向七仔（和 SpeechBubble 同风格）
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(18.dp, 10.dp)
        ) {
            val tailPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tailPath, Color.White)
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

/** 气泡里的快捷按钮：36dp 图标 + 10sp 标签；点即直达，长按删除 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickBubbleButton(
    item: QuickActionItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(2.dp)
    ) {
        QuickActionIcon(item = item, size = 36.dp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            fontSize = 10.sp,
            color = Color(0xFF3A3A3A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 52.dp),
            textAlign = TextAlign.Center
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
