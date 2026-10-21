@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.settings

import android.content.Intent
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
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.bobot.ailauncher.data.LlmConnectionTester
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.ui.components.downloadStatusText
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 设置页（v0.57.0 重构）：
 * - 只剩三项：让七仔更聪明（收起）/ 关于 / 接收测试版更新（v0.68.80）
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
        // v0.68.45：contentPadding 归零，各 item 自带 padding——刊头和应用中心像素对齐
        modifier = Modifier.fillMaxSize()
            .background(AILauncherColors.current.GlassCardStrong)
            .statusBarsTopPadding()
            .then(swipeUpToHome),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // v0.68.45：杂志刊头——和应用中心完全一致（28.dp 左 / 8.dp 顶 / 同款 kicker / 32.sp 宋体）
            // v0.68.46：返回做成 kicker 的一部分（‹ CONFIG）——整行可点，无独立按钮，刊头干净
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp, top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack
                    )
                ) {
                    Text(
                        text = "‹ ",
                        fontSize = 14.sp,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Bold
                    )
                    com.bobot.ailauncher.ui.apps.MastheadKicker(text = "CONFIG · 设置")
                }
                Text(
                    text = "设置",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title
                )
            }
        }

        // 让七仔更聪明（收起一层）
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable { llmExpanded = !llmExpanded }
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
                                color = AILauncherColors.current.Title
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "填上大模型 Key，七仔能听懂你说话、帮你办事。不填也能用，只是没那么聪明。",
                                fontSize = 13.sp,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            if (llmExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (llmExpanded) "收起" else "展开",
                            tint = AILauncherColors.current.Hint
                        )
                    }
                    if (llmExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; persist() },
                            label = { Text("大模型 Key") },
                            placeholder = { Text("去 DeepSeek / OpenAI 官网申请一个，粘过来就行", color = AILauncherColors.current.Hint) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; persist() },
                            label = { Text("接口地址") },
                            placeholder = { Text(LlmConfig.DEFAULT_BASE_URL, color = AILauncherColors.current.Hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Text(
                            text = "一般不用改。用别的服务商才需要换",
                            fontSize = 12.sp,
                            color = AILauncherColors.current.Hint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; persist() },
                            label = { Text("用哪个模型") },
                            placeholder = { Text(LlmConfig.DEFAULT_MODEL, color = AILauncherColors.current.Hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur)
                        )
                        Text(
                            text = "填你 Key 对应的模型名，比如 deepseek-flash",
                            fontSize = 12.sp,
                            color = AILauncherColors.current.Hint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (testing) return@Button
                                persist()
                                if (apiKey.isBlank()) {
                                    IslandToasts.show("请先填写大模型 Key")
                                    return@Button
                                }
                                testing = true
                                scope.launch {
                                    val err = LlmConnectionTester.testConnection(
                                        baseUrl.ifBlank { LlmConfig.DEFAULT_BASE_URL },
                                        apiKey,
                                        model.ifBlank { LlmConfig.DEFAULT_MODEL }
                                    )
                                    testing = false
                                    IslandToasts.show(err ?: "连接成功，七仔变聪明了")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.current.Accent)
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
                            IslandToasts.show("安装包不完整，重新下载")
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
                                        else -> IslandToasts.show("检查失败，请稍后再试")
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
                                    IslandToasts.show("已经是最新版本了")
                                OtaCheckResult.Failed ->
                                    IslandToasts.show("检查失败，请稍后再试")
                            }
                            checking = false
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
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
                            color = AILauncherColors.current.Title
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
                                    text = downloadStatusText(
                                        dlState as OtaDownloader.State.Downloading
                                    ),
                                    fontSize = 13.sp,
                                    color = AILauncherColors.current.Hint
                                )
                            }
                            dlDone -> Text(
                                text = "点击安装 v${updateInfo?.versionName ?: ""}",
                                fontSize = 13.sp,
                                color = AILauncherColors.current.Accent
                            )
                            dlFailed -> {
                                Text(
                                    text = "点击重试",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
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
                                                IslandToasts.show("无法打开浏览器")
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "手动下载（浏览器打开）",
                                            fontSize = 13.sp,
                                            color = AILauncherColors.current.Accent
                                        )
                                    }
                                }
                            }
                            updateInfo != null -> Text(
                                text = updateInfo!!.changelog,
                                fontSize = 13.sp,
                                color = AILauncherColors.current.Hint,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            else -> Text(
                                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                                fontSize = 13.sp,
                                color = AILauncherColors.current.Hint
                            )
                        }
                    }
                    if (checking || downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AILauncherColors.current.Accent
                        )
                    } else if (onCardClick != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            // 体检批D ⑨：装饰（整卡可点、状态由卡内文字表达）标装饰语义
                            contentDescription = null,
                            tint = AILauncherColors.current.Hint,
                            modifier = Modifier.semantics { invisibleToUser() }
                        )
                    }
                }
            }
        }

        // v0.68.80：测试版开关——开启后可收到 prerelease 测试版推送
        item {
            var betaOptIn by remember { mutableStateOf(OtaUpdater.isBetaOptIn(context)) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "接收测试版更新",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "开启后可收到测试版推送，适合尝鲜；默认只收正式版",
                            fontSize = 13.sp,
                            color = AILauncherColors.current.Hint
                        )
                    }
                    Switch(
                        checked = betaOptIn,
                        onCheckedChange = {
                            betaOptIn = it
                            OtaUpdater.setBetaOptIn(context, it)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
