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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.BuildConfig
import com.bobot.ailauncher.data.CrashLog
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmKeyStore
import com.bobot.ailauncher.data.LlmConnectionTester
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.ui.components.downloadStatusText
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import com.bobot.ailauncher.ui.home.clearIntentFocusOnOutsideTap
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
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
    // v0.68.100（卫生批）：Key 存放形态（加密 / 明文降级）——保存后刷新，卡内明示
    var keyStatus by remember { mutableStateOf(LlmConfig.keyStorageStatus(context)) }
    var testing by remember { mutableStateOf(false) }
    var llmExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideOnBlur = Modifier.onFocusChanged { if (!it.isFocused) keyboardController?.hide() }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    // v0.68.98（F-08）：焦点礼仪与 .93 首页同一套语义——三个输入框只有 hideOnBlur
    // （失焦才收键盘）而页面没有点外部清焦，点空白处焦点不走、键盘收不掉。
    // 在根容器复用 clearIntentFocusOnOutsideTap 非消费观察者：任一输入框聚焦时，
    // 按下点落在「当前聚焦框」范围外才清焦 + 收键盘（框间互点不误伤，文本不动）。
    // focusedField/fieldBounds 由各输入框的 onFocusChanged/onGloballyPositioned 维护。
    var focusedField by remember { mutableStateOf(-1) }
    var fieldBounds by remember { mutableStateOf(mapOf<Int, Rect>()) }
    var rootPosInRoot by remember { mutableStateOf(Offset.Zero) }
    fun fieldFocusMods(index: Int): Modifier = Modifier
        .onFocusChanged {
            if (it.isFocused) focusedField = index
            else if (focusedField == index) focusedField = -1
        }
        .onGloballyPositioned { fieldBounds = fieldBounds + (index to it.boundsInRoot()) }

    // 收起「让七仔更聪明」时输入区连同焦点一起卸载：主动清焦 + 收键盘，
    // 不把键盘留在已消失的输入框上（F-08：打开子面板/收起时的焦点处理）
    LaunchedEffect(llmExpanded) {
        if (!llmExpanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun persist() {
        LlmConfig.save(context, apiKey, baseUrl, model)
        keyStatus = LlmConfig.keyStorageStatus(context)
    }

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
            // 刘海避让（Bob 2026-10-25 全页检查）：横屏下挖孔移到两侧边，行内容
            // 左右 20dp 顶不到横屏 cutout 侧边 inset；两侧直接吃 displayCutout
            // inset——竖屏侧边 inset 为 0，竖屏布局零变化。
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Start, WindowInsetsSides.End
                )
            )
            .onGloballyPositioned { rootPosInRoot = it.positionInRoot() }
            .clearIntentFocusOnOutsideTap(
                focused = { focusedField >= 0 },
                targetBoundsInHost = {
                    fieldBounds[focusedField]?.let { b ->
                        Rect(
                            b.left - rootPosInRoot.x, b.top - rootPosInRoot.y,
                            b.right - rootPosInRoot.x, b.bottom - rootPosInRoot.y
                        )
                    }
                },
                focusManager = focusManager,
                keyboardController = keyboardController
            )
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
                        // F-08：离开设置页前先清焦 + 收键盘，不把键盘带到上一页
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onBack()
                        }
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
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // F-08：展开/收起只认标题行——此前整卡 clickable，编辑中点到
                    // 卡片空白区就把输入区连同焦点一起卸载，打断编辑上下文。
                    // F-10：点按补 tick（走 Haptics 两档门面）。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.tickHaptic()
                                llmExpanded = !llmExpanded
                            },
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
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur).then(fieldFocusMods(0))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; persist() },
                            label = { Text("接口地址") },
                            placeholder = { Text(LlmConfig.DEFAULT_BASE_URL, color = AILauncherColors.current.Hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur).then(fieldFocusMods(1))
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
                            modifier = Modifier.fillMaxWidth().then(hideOnBlur).then(fieldFocusMods(2))
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
                                // F-10：主按钮 = confirm 档
                                view.confirmHaptic()
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
                        // v0.68.100（卫生批）：Key 存放形态明示——加密时低调一行，
                        // 明文降级时醒目提示 + 给得出下一步，不再静默回退
                        when (keyStatus.mode) {
                            LlmKeyStore.StorageMode.ENCRYPTED -> Text(
                                text = "✓ API Key 已加密存储在本机",
                                fontSize = 12.sp,
                                color = AILauncherColors.current.Hint,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            LlmKeyStore.StorageMode.PLAIN_FALLBACK -> Column(
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                Text(
                                    text = "本机密钥库异常，API Key 现在是明文存储",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "重启手机后回到这里，Key 会自动尝试恢复加密；" +
                                        "仍不行的话，删掉 Key 重新粘贴保存一次。",
                                    fontSize = 12.sp,
                                    color = AILauncherColors.current.Hint,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            LlmKeyStore.StorageMode.EMPTY -> Unit
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
                // F-10：整卡点按补 tick（不可点态不震，与视觉禁用一致）
                onClick = { onCardClick?.let { view.tickHaptic(); it() } }
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
                                            view.tickHaptic()
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
                            // F-10：开关切换 = confirm 档
                            view.confirmHaptic()
                            betaOptIn = it
                            OtaUpdater.setBetaOptIn(context, it)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // v0.68.100（卫生批）：崩溃记录——侧载没有 Play Console，崩溃在这里
        // 可见：本地留痕（环形 10 份）、可看全文、可经系统分享发给开发者、可删。
        // 未读时标题带红点，进列表即标记已读（与冷启动提示一次的水位联动）。
        item {
            var crashExpanded by remember { mutableStateOf(false) }
            var crashRecords by remember { mutableStateOf(CrashLog.list(context)) }
            var crashUnread by remember { mutableStateOf(CrashLog.unreadCount(context)) }
            var openedCrash by remember { mutableStateOf<Long?>(null) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.tickHaptic()
                                crashExpanded = !crashExpanded
                                if (crashExpanded) {
                                    crashRecords = CrashLog.list(context)
                                    CrashLog.markAllRead(context)
                                    crashUnread = 0
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "崩溃记录",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AILauncherColors.current.Title
                                )
                                if (crashUnread > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    crashRecords.isEmpty() -> "暂无记录，七仔运行得很稳"
                                    crashUnread > 0 -> "有 $crashUnread 条新记录，点开查看"
                                    else -> "共 ${crashRecords.size} 条，点开查看或分享"
                                },
                                fontSize = 13.sp,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            if (crashExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (crashExpanded) "收起" else "展开",
                            tint = AILauncherColors.current.Hint
                        )
                    }
                    if (crashExpanded && crashRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        crashRecords.forEach { record ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openedCrash =
                                            if (openedCrash == record.epochMillis) null
                                            else record.epochMillis
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(record.epochMillis)),
                                    fontSize = 13.sp,
                                    color = AILauncherColors.current.Title
                                )
                                Text(
                                    text = record.title,
                                    fontSize = 12.sp,
                                    color = AILauncherColors.current.Hint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (openedCrash == record.epochMillis) {
                                Text(
                                    text = record.content,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AILauncherColors.current.Body,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                                TextButton(
                                    onClick = {
                                        view.tickHaptic()
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "AI桌面崩溃记录")
                                            putExtra(Intent.EXTRA_TEXT, record.content)
                                        }
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(send, "分享崩溃记录")
                                            )
                                        }.onFailure {
                                            IslandToasts.show("没有可用的分享方式")
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "分享这条",
                                        fontSize = 13.sp,
                                        color = AILauncherColors.current.Accent
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    view.tickHaptic()
                                    CrashLog.deleteAll(context)
                                    crashRecords = emptyList()
                                    openedCrash = null
                                }
                            ) {
                                Text(
                                    text = "全部删除",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
