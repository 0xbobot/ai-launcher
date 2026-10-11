package com.bobot.ailauncher.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.BuildConfig
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * 设置页（v0.57.0 重构）：
 * - 只剩两项：让七仔更聪明（收起）/ 关于
 * - 电量守护默认开启，不设开关；Dock 显示纯手势；排序默认智能
 * - 文案全部用户视角
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(LlmConfig.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(LlmConfig.getBaseUrl(context)) }
    var model by remember { mutableStateOf(LlmConfig.getModel(context)) }
    var testing by remember { mutableStateOf(false) }
    var llmExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideOnBlur = Modifier.onFocusChanged { if (!it.isFocused) keyboardController?.hide() }

    fun persist() = LlmConfig.save(context, apiKey, baseUrl, model)

    // v0.57.0：从屏幕底部上滑 → 回桌面主页（Bob：任何时候从底部上滑都要能回主页）
    // 只响应起始点在屏幕底部 1/4 区域内的上滑，避免与列表滚动冲突
    val swipeUpToHome = Modifier.pointerInput(onBack) {
        var totalY = 0f
        var startY = 0f
        // pointerInput 作用域的 size 在 detectDragGestures 回调里不可见，先存下来
        val heightPx = size.height
        detectDragGestures(
            onDragStart = { offset -> totalY = 0f; startY = offset.y },
            onDragEnd = {
                val fromBottom = startY > heightPx * 0.75f
                if (fromBottom && totalY < -120) onBack()
            },
            onDrag = { _, dragAmount ->
                // 不消费，让 LazyColumn 照常滚动
                totalY += dragAmount.y
            }
        )
    }

    LazyColumn(
        // v0.61.1：和应用中心一样的半透明背景（GlassCardStrong，白 88%）
        modifier = Modifier.fillMaxSize()
            .background(AILauncherColors.GlassCardStrong)
            .statusBarsPadding()
            .then(swipeUpToHome),
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

        // 让七仔更聪明（收起一层）
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.clickable { llmExpanded = !llmExpanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "让七仔更聪明",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.Title
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "填上大模型 Key，七仔能听懂你说话、帮你办事。不填也能用，只是没那么聪明。",
                                fontSize = 13.sp,
                                color = AILauncherColors.Hint,
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            if (llmExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (llmExpanded) "收起" else "展开",
                            tint = AILauncherColors.Hint
                        )
                    }
                    if (llmExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; persist() },
                            label = { Text("大模型 Key") },
                            placeholder = { Text("去 DeepSeek / OpenAI 官网申请一个，粘过来就行", color = AILauncherColors.Hint) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; persist() },
                            label = { Text("接口地址") },
                            placeholder = { Text(LlmConfig.DEFAULT_BASE_URL, color = AILauncherColors.Hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Text(
                            text = "一般不用改。用别的服务商才需要换",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; persist() },
                            label = { Text("用哪个模型") },
                            placeholder = { Text(LlmConfig.DEFAULT_MODEL, color = AILauncherColors.Hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Text(
                            text = "填你 Key 对应的模型名，比如 deepseek-flash",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (testing) return@Button
                                persist()
                                if (apiKey.isBlank()) {
                                    Toast.makeText(context, "请先填写大模型 Key", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                testing = true
                                scope.launch {
                                    val err = LlmRouter.testConnection(
                                        baseUrl.ifBlank { LlmConfig.DEFAULT_BASE_URL },
                                        apiKey,
                                        model.ifBlank { LlmConfig.DEFAULT_MODEL }
                                    )
                                    testing = false
                                    Toast.makeText(
                                        context,
                                        err ?: "连接成功，七仔变聪明了",
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
        }

        // 关于
        item {
            var checking by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }
            val dlState by OtaDownloader.state.collectAsState()

            val downloading = dlState is OtaDownloader.State.Downloading
            val dlProgress = (dlState as? OtaDownloader.State.Downloading)?.progress ?: 0f
            val dlDone = dlState is OtaDownloader.State.Success
            val dlFailed = dlState is OtaDownloader.State.Failed

            val onCardClick: (() -> Unit)? = when {
                checking || downloading -> null
                dlDone -> {
                    {
                        // v0.65.6：安装前复查 APK 可解析——坏包不装，转重新下载
                        val apk = OtaUpdater.downloadedApk(context)
                            ?.takeIf { OtaUpdater.isValidApk(context, it) }
                        if (apk != null) {
                            if (OtaUpdater.promptInstall(context, apk)) {
                                OtaUpdater.markInstallPrompted(context)
                            }
                        } else {
                            Toast.makeText(context, "安装包不完整，重新下载", Toast.LENGTH_SHORT).show()
                            OtaDownloader.resetIfNotDownloading()
                            updateInfo?.let { OtaDownloader.start(context, it) }
                        }
                    }
                }
                dlFailed -> {
                    {
                        updateInfo?.let { OtaDownloader.start(context, it) }
                            ?: run {
                                checking = true
                                scope.launch {
                                    when (val r = OtaUpdater.checkForUpdateResult(context)) {
                                        is OtaCheckResult.UpdateAvailable -> {
                                            updateInfo = r.info
                                            OtaDownloader.start(context, r.info)
                                        }
                                        else -> Toast.makeText(
                                            context, "检查失败，请稍后再试", Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    checking = false
                                }
                            }
                    }
                }
                updateInfo != null -> {
                    { OtaDownloader.start(context, updateInfo!!) }
                }
                else -> {
                    {
                        checking = true
                        scope.launch {
                            when (val r = OtaUpdater.checkForUpdateResult(context)) {
                                is OtaCheckResult.UpdateAvailable -> updateInfo = r.info
                                OtaCheckResult.UpToDate ->
                                    Toast.makeText(context, "已经是最新版本了", Toast.LENGTH_SHORT).show()
                                OtaCheckResult.Failed ->
                                    Toast.makeText(context, "检查失败，请稍后再试", Toast.LENGTH_SHORT).show()
                            }
                            checking = false
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                onClick = { onCardClick?.invoke() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                checking -> "正在检查..."
                                downloading -> "正在下载 v${updateInfo?.versionName ?: ""}"
                                dlDone -> "下载完成"
                                dlFailed -> "下载失败"
                                updateInfo != null -> "发现新版本 v${updateInfo!!.versionName}"
                                else -> "检查更新"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.Title
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        when {
                            downloading -> {
                                LinearProgressIndicator(
                                    progress = { dlProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "已下载 ${(dlProgress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    color = AILauncherColors.Hint
                                )
                            }
                            dlDone -> Text(
                                text = "点击安装 v${updateInfo?.versionName ?: ""}",
                                fontSize = 13.sp,
                                color = AILauncherColors.Accent
                            )
                            dlFailed -> {
                                Text(
                                    text = "点击重试",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD16A6A)
                                )
                                val apkUrl = updateInfo?.apkUrl
                                if (!apkUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(apkUrl)
                                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "手动下载（浏览器打开）",
                                            fontSize = 13.sp,
                                            color = AILauncherColors.Accent
                                        )
                                    }
                                }
                            }
                            updateInfo != null -> Text(
                                text = updateInfo!!.changelog,
                                fontSize = 13.sp,
                                color = AILauncherColors.Hint,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            else -> Text(
                                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                                fontSize = 13.sp,
                                color = AILauncherColors.Hint
                            )
                        }
                    }
                    if (checking || downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AILauncherColors.Accent
                        )
                    } else if (onCardClick != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AILauncherColors.Hint
                        )
                    }
                }
            }
        }
    }
}
