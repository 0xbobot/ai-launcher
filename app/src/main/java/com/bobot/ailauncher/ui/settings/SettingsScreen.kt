package com.bobot.ailauncher.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.BuildConfig
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.data.BatteryGuardPrefs
import com.bobot.ailauncher.service.BatteryGuardService
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * 大模型设置：API Key / Base URL / 模型名，存 SharedPreferences "llm"。
 * 「测试连接」发一个极简请求验证连通性。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    dockVisible: Boolean = true,
    onDockVisibleChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(LlmConfig.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(LlmConfig.getBaseUrl(context)) }
    var model by remember { mutableStateOf(LlmConfig.getModel(context)) }
    var testing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideOnBlur = Modifier.onFocusChanged { if (!it.isFocused) keyboardController?.hide() }

    fun persist() = LlmConfig.save(context, apiKey, baseUrl, model)

    // v0.36.0：打开低电量守护时，一并申请通知权限（Android 13+）——
    // 之前只开了开关但没申请权限，提醒发不出来，用户会觉得功能没了
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不强求：服务照常跑，只是弹窗出不来 */ }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AILauncherColors.Title
                    )
                }
                Text(
                    text = "设置",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title
                )
            }
        }
        // v0.36.0：按主题分 section
        item { SectionTitle("大模型") }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "意图理解由大模型驱动（OpenAI 兼容接口）。不填 Key 时，意图框退化为关键词匹配演示版。",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; persist() },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-…", color = AILauncherColors.Hint) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; persist() },
                        label = { Text("Base URL") },
                        placeholder = { Text(LlmConfig.DEFAULT_BASE_URL, color = AILauncherColors.Hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; persist() },
                        label = { Text("模型") },
                        placeholder = { Text(LlmConfig.DEFAULT_MODEL, color = AILauncherColors.Hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                    )
                    Text(
                        text = "如 deepseek-flash / deepseek-v4-pro",
                        fontSize = 12.sp,
                        color = AILauncherColors.Hint
                    )
                    Button(
                        onClick = {
                            if (testing) return@Button
                            persist()
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "请先填写 API Key", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            testing = true
                            scope.launch {
                                // testConnection 返回 null=成功，否则为可直接展示的错误文案
                                //（如 "400: The model 'deepseek-flash' does not exist"）
                                val err = LlmRouter.testConnection(
                                    baseUrl.ifBlank { LlmConfig.DEFAULT_BASE_URL },
                                    apiKey,
                                    model.ifBlank { LlmConfig.DEFAULT_MODEL }
                                )
                                testing = false
                                Toast.makeText(
                                    context,
                                    err ?: "连接成功",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.Accent)
                    ) {
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                }
            }
        }
        item { SectionTitle("桌面与 Dock") }

        // v0.16：Dock 显示开关（D1 右滑隐藏后，在这里重新打开）
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "显示 Dock",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Text(
                            text = "关闭后首页不再显示应用 Dock",
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    Switch(
                        checked = dockVisible,
                        onCheckedChange = onDockVisibleChange
                    )
                }
            }
        }

        // v0.20：Dock 智能排序开关（PRD §九：AI 可以推荐，不能强行改变）
        item {
            var smartSort by remember { mutableStateOf(UiPrefs.getDockSmartSort(context)) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dock 智能排序",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Text(
                            text = "按使用频次和当前时段排列常用应用；关闭后按名称排列",
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    Switch(
                        checked = smartSort,
                        onCheckedChange = {
                            smartSort = it
                            UiPrefs.setDockSmartSort(context, it)
                        }
                    )
                }
            }
        }

        // v0.35.0：Dock 置顶管理——用户手动固定的应用，固定排在 Dock 最前面；
        // 可调顺序、可移除（在应用列表左滑点 ★ 即可添加）
        item {
            DockPinnedManager()
        }

        item { SectionTitle("手势") }
        // 惯用手：决定 A-Z 导航 rail 在哪一侧、按住时往哪边偏移
        item {
            var handed by remember { mutableStateOf(UiPrefs.getHanded(context)) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "惯用手",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.Title
                    )
                    Text(
                        text = "按住 A-Z 导航时，字母和气泡会往拇指反方向偏移，不被拇指盖住。",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            UiPrefs.Handed.LEFT to "左手",
                            UiPrefs.Handed.RIGHT to "右手"
                        ).forEach { (h, label) ->
                            val selected = handed == h
                            Button(
                                onClick = {
                                    handed = h
                                    UiPrefs.setHanded(context, h)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) AILauncherColors.Accent
                                    else AILauncherColors.Divider,
                                    contentColor = if (selected) Color.White
                                    else AILauncherColors.Title
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("通知与电量") }
        // v0.28.0：低电量守护——只做温和提醒，零干预
        item {
            var guardOn by remember { mutableStateOf(BatteryGuardPrefs.isEnabled(context)) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "低电量守护",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Switch(
                            checked = guardOn,
                            onCheckedChange = {
                                guardOn = it
                                BatteryGuardPrefs.setEnabled(context, it)
                                if (it) {
                                    // v0.36.0：Android 13+ 先申请通知权限，否则提醒发不出
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    BatteryGuardService.start(context)
                                } else BatteryGuardService.stop(context)
                            }
                        )
                    }
                    Text(
                        text = "七仔的能量=手机电量。能量过低时温和提醒你（10% 和 5% 各一次），只提醒、不做任何自动操作。",
                        fontSize = 13.sp,
                        color = AILauncherColors.Hint
                    )
                    if (guardOn) {
                        Text(
                            text = "需要通知权限才能在看视频、玩游戏时弹出提醒。",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                }
            }
        }
        item { SectionTitle("应用") }
        // 已隐藏应用：在应用中心右滑隐藏的应用，在这里恢复
        item {
            val hiddenVersion = HiddenApps.version.intValue
            val hiddenPkgs = remember(hiddenVersion) { HiddenApps.getHidden(context) }
            var expanded by remember { mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "已隐藏应用（${hiddenPkgs.size}）",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.Title
                            )
                            Text(
                                text = "在应用中心右滑隐藏的应用，可在这里恢复显示",
                                fontSize = 13.sp,
                                color = AILauncherColors.Hint
                            )
                        }
                        Text(
                            text = if (expanded) "收起" else "展开",
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (hiddenPkgs.isEmpty()) {
                                Text(
                                    text = "暂无已隐藏应用",
                                    fontSize = 13.sp,
                                    color = AILauncherColors.Hint,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                val pm = context.packageManager
                                hiddenPkgs.sorted().forEach { pkg ->
                                    val label = try {
                                        pm.getApplicationLabel(
                                            pm.getApplicationInfo(pkg, 0)
                                        ).toString()
                                    } catch (_: Exception) {
                                        pkg
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            color = AILauncherColors.Body,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            HiddenApps.unhide(context, pkg)
                                        }) {
                                            Text(
                                                text = "恢复",
                                                fontSize = 13.sp,
                                                color = AILauncherColors.Accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("通用") }

        // OTA：手动检查更新
        item {
            var checking by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                onClick = {
                    if (checking) return@Card
                    checking = true
                    scope.launch {
                        when (val r = OtaUpdater.checkForUpdateResult(context)) {
                            is OtaCheckResult.UpdateAvailable -> updateInfo = r.info
                            OtaCheckResult.UpToDate ->
                                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                            OtaCheckResult.Failed ->
                                Toast.makeText(context, "检查失败，请稍后再试", Toast.LENGTH_SHORT).show()
                        }
                        checking = false
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "检查更新",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Text(
                            text = "当前版本 v${BuildConfig.VERSION_NAME}",
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AILauncherColors.Accent
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AILauncherColors.Hint
                        )
                    }
                }
            }
            updateInfo?.let { info ->
                UpdateDialog(info = info, onDismiss = { updateInfo = null })
            }
        }
    }
}

/**
 * v0.36.0：设置页分 section 小标题
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AILauncherColors.Hint,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

/**
 * v0.35.0：Dock 置顶管理——列出用户置顶的应用（按 Dock 显示顺序），
 * 支持上移/下移调整顺序、移除置顶。置顶为空时给出去应用列表添加的提示。
 */
@Composable
private fun DockPinnedManager() {
    val context = LocalContext.current
    val dockTick by UiPrefs.dockTick.collectAsState()
    val pinnedPkgs = remember(dockTick) { UiPrefs.getDockPinned(context) }
    val pinnedApps = remember(dockTick) {
        val all = listLaunchableApps(context)
        pinnedPkgs.mapNotNull { pkg -> all.find { it.packageName == pkg } }
    }

    fun persist(pkgs: List<String>) = UiPrefs.setDockPinned(context, pkgs)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Dock 置顶",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.Title
            )
            Text(
                text = "置顶的应用固定排在 Dock 最前面。在应用列表左滑点 ★ 即可添加，最多 10 个。",
                fontSize = 13.sp,
                color = AILauncherColors.Hint
            )
            if (pinnedApps.isEmpty()) {
                Text(
                    text = "还没有置顶应用",
                    fontSize = 14.sp,
                    color = AILauncherColors.Hint,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                pinnedApps.forEachIndexed { index, app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 13.sp,
                            color = AILauncherColors.Hint,
                            modifier = Modifier.width(20.dp)
                        )
                        AppIconImage(
                            drawable = app.icon,
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val cur = pinnedPkgs.toMutableList()
                                    val item = cur.removeAt(index)
                                    cur.add(index - 1, item)
                                    persist(cur)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "上移",
                                tint = if (index > 0) AILauncherColors.Title else AILauncherColors.Hint.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < pinnedPkgs.size - 1) {
                                    val cur = pinnedPkgs.toMutableList()
                                    val item = cur.removeAt(index)
                                    cur.add(index + 1, item)
                                    persist(cur)
                                }
                            },
                            enabled = index < pinnedPkgs.size - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "下移",
                                tint = if (index < pinnedPkgs.size - 1) AILauncherColors.Title else AILauncherColors.Hint.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                persist(pinnedPkgs.filter { it != app.packageName })
                                Toast.makeText(context, "已从 Dock 移出", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移出 Dock",
                                tint = Color(0xFFD16A6A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
