package com.bobot.ailauncher.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.bobot.ailauncher.BuildConfig
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * 大模型设置：API Key / Base URL / 模型名，存 SharedPreferences "llm"。
 * 「测试连接」发一个极简请求验证连通性。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(LlmConfig.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(LlmConfig.getBaseUrl(context)) }
    var model by remember { mutableStateOf(LlmConfig.getModel(context)) }
    var testing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideOnBlur = Modifier.onFocusChanged { if (!it.isFocused) keyboardController?.hide() }

    fun persist() = LlmConfig.save(context, apiKey, baseUrl, model)

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
                    text = "大模型设置",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title
                )
            }
        }
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
        item { Spacer(modifier = Modifier.height(8.dp)) }

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
        item { Spacer(modifier = Modifier.height(24.dp)) }

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
