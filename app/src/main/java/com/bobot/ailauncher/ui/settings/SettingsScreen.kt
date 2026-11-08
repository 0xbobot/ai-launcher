@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.settings

import android.content.Intent
import android.provider.Telephony
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.BuildConfig
import com.bobot.ailauncher.R
import com.bobot.ailauncher.data.BadgeProbe
import com.bobot.ailauncher.data.ConnectorState
import com.bobot.ailauncher.data.ConnectorToggleAction
import com.bobot.ailauncher.data.CrashLog
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SmsDiagCounts
import com.bobot.ailauncher.data.SmsRepository
import com.bobot.ailauncher.data.countSmsDiag
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.SMART_SOURCE_ORDER
import com.bobot.ailauncher.data.buildConnectorStates
import com.bobot.ailauncher.data.connectedSourceCount
import com.bobot.ailauncher.data.connectorStatusText
import com.bobot.ailauncher.data.isConnectorConnected
import com.bobot.ailauncher.data.resolveConnectorToggle
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmKeyStore
import com.bobot.ailauncher.data.LlmConnectionTester
import com.bobot.ailauncher.data.OtaCheckResult
import com.bobot.ailauncher.data.OtaDownloader
import com.bobot.ailauncher.ui.components.downloadStatusText
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.pageTextureBackground
import com.bobot.ailauncher.ui.theme.tickHaptic
import com.bobot.ailauncher.ui.settings.OnGrantedTransition
import com.bobot.ailauncher.ui.settings.isCalendarGranted
import com.bobot.ailauncher.ui.settings.isContactsGranted
import com.bobot.ailauncher.ui.settings.isDefaultLauncher
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.settings.isSmsGranted
import com.bobot.ailauncher.ui.settings.openHomeSettings
import com.bobot.ailauncher.ui.settings.openNotificationSettings
import com.bobot.ailauncher.ui.settings.rememberCalendarPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberContactsPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberPermissionState
import com.bobot.ailauncher.ui.settings.rememberSmsPermissionOpener
import com.bobot.ailauncher.util.rebindListener
import com.bobot.ailauncher.ui.home.clearIntentFocusOnOutsideTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 设置页（v0.57.0 重构）：
 * - 只剩三项：让助手更聪明（收起）/ 关于 / 接收测试版更新（v0.68.80）
 * - 电量守护默认开启，不设开关；Dock 显示纯手势；排序默认智能
 * - 文案全部用户视角
 */
@Composable
fun SettingsScreen() {
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

    // 收起「让助手更聪明」时输入区连同焦点一起卸载：主动清焦 + 收键盘，
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

    // 设置页底部上滑回主页已删除（Bob 2026-11-03：避免操作干扰，与应用中心同批）
    LazyColumn(
        // v0.61.1：和应用中心一样的半透明背景（GlassCardStrong，白 88%）
        // 430（Bob 2026-11-05 晚亲批“设置页面也参考这个”）：半透明底换成已批纹理背景
        // （与应用中心同一套，主题感知）。设置页是 NavHost 独立目的地、首页不在其后，
        // 88% 底透出的只是窗口底色，换不透明纹理不改变任何可见内容，只换背景层。
        // v0.68.45：contentPadding 归零，各 item 自带 padding——刊头和应用中心像素对齐
        modifier = Modifier.fillMaxSize()
            .pageTextureBackground()
            .statusBarsTopPadding()
            // 刘海避让（Bob 2026-10-25 全页检查）：横屏下挖孔移到两侧边，行内容
            // 左右 20dp 顶不到横屏 cutout 侧边 inset；两侧直接吃 displayCutout
            // inset——竖屏侧边 inset 为 0，竖屏布局零变化。
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Start + WindowInsetsSides.End
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
            ),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 杂志刊头——与应用中心完全一致（24.dp 左 / 8.dp 顶 / 32.sp 宋体）。
            // 返回箭头已删（Bob 2026-11-07）：系统返回键/手势仍经 NavController
            // 退回（设置页是 NavHost 独立目的地）；键盘由系统返回自行收起，
            // 不再需要离开前手动清焦。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp)
            ) {
                Text(
                    text = "设置",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = AILauncherType.Display.fontSize,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title
                )
            }
        }

        // 分区：助手——让助手更聪明（大模型 API Key）
        item {
            SettingsSectionTitle("助手")
        }

        // 让助手更聪明（收起一层）
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
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
                                text = "让助手更聪明",
                                fontSize = AILauncherType.BodyLarge.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.current.Title
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "填上大模型 API Key，助手能听懂你说话、帮你办事。不填也能用，只是没那么聪明。",
                                fontSize = AILauncherType.Footnote.fontSize,
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
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; persist() },
                            label = { Text("大模型 API Key") },
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
                            fontSize = AILauncherType.Caption.fontSize,
                            color = AILauncherColors.current.Hint,
                            modifier = Modifier.padding(top = 8.dp)
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
                            text = "填你 API Key 对应的模型名，比如 deepseek-flash",
                            fontSize = AILauncherType.Caption.fontSize,
                            color = AILauncherColors.current.Hint,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (testing) return@Button
                                // F-10：主按钮 = confirm 档
                                view.confirmHaptic()
                                persist()
                                if (apiKey.isBlank()) {
                                    IslandToasts.show("请先填写大模型 API Key")
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
                                    IslandToasts.show(err ?: "连接成功")
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
                                fontSize = AILauncherType.Caption.fontSize,
                                color = AILauncherColors.current.Hint,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            LlmKeyStore.StorageMode.PLAIN_FALLBACK -> Column(
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "加密出了点问题，你的 Key 现在没有加密保存",
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "重启手机后回到这里，Key 会自动尝试恢复加密；" +
                                        "仍不行的话，删掉 API Key 重新粘贴保存一次。",
                                    fontSize = AILauncherType.Caption.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            LlmKeyStore.StorageMode.EMPTY -> Unit
                        }
                    }
                }
            }
        }

        // 分区：版本（检查更新 + 加入测试计划）
        item {
            SettingsSectionTitle("版本")
        }

        // 版本
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f)),
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
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            downloading -> {
                                LinearProgressIndicator(
                                    progress = { dlProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = downloadStatusText(
                                        dlState as OtaDownloader.State.Downloading
                                    ),
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint
                                )
                            }
                            dlDone -> Text(
                                text = "点击安装 v${updateInfo?.versionName ?: ""}",
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Accent
                            )
                            dlFailed -> {
                                Text(
                                    text = "点击重试",
                                    fontSize = AILauncherType.Footnote.fontSize,
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
                                            fontSize = AILauncherType.Footnote.fontSize,
                                            color = AILauncherColors.current.Accent
                                        )
                                    }
                                }
                            }
                            updateInfo != null -> Text(
                                text = updateInfo!!.changelog,
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Hint,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            else -> Text(
                                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                                fontSize = AILauncherType.Footnote.fontSize,
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "加入测试计划",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "加入后可提前收到测试版本，适合尝鲜；默认只收正式版",
                            fontSize = AILauncherType.Footnote.fontSize,
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
        }

        // 分区：智能——智能卡片的五个本机连接器就是智能的源（447 起，设计稿 §2/§5）
        // 各自单独接、有明确理由、随时可断；数据只在手机上处理，不发到别处。
        // 2026-11-08 Bob 定：分区改名“智能”（不再叫“提醒来源”），区顶显示
        // 智能等级（已接 N/5 个源），行序按源对智能的提升比例（消息>通知>
        // 日历>电话拨号器>联系人）；默认桌面不是源，挪出本区、单独放一张卡。
        // 448 合并：旧“权限”分区并入本分区——日历、通知只出现一次（原两处重叠）。
        // 引导里跳过/拒绝后随时可补授权的承诺仍由本分区兑现。
        item {
            SettingsSectionTitle("智能")
        }

        item {
            val calendarGranted = rememberPermissionState { isCalendarGranted(context) }
            val notificationGranted = rememberPermissionState { isNotificationAccessGranted(context) }
            val smsGranted = rememberPermissionState { isSmsGranted(context) }
            val contactsGranted = rememberPermissionState { isContactsGranted(context) }
            val defaultGranted = rememberPermissionState { isDefaultLauncher(context) }
            val openCalendar = rememberCalendarPermissionOpener(calendarGranted)
            val openSms = rememberSmsPermissionOpener(smsGranted)
            val openContacts = rememberContactsPermissionOpener(contactsGranted)
            // 应用运行中去设置里开通知读取权限时系统不回调，在这里重绑监听
            OnGrantedTransition(notificationGranted.value) { rebindListener(context) }
            // 消息刚接上就立即重读短信（不然卡片要等下次回桌面才出现）
            OnGrantedTransition(smsGranted.value) {
                SmartCardRepository.refreshSms(context, force = true)
            }
            // 显著披露：先讲清要什么、为什么，用户点“同意”后再弹系统框
            // （设计稿 §5.4：显著披露在第一次请求权限之前）。
            var disclosure by remember { mutableStateOf<String?>(null) }
            // 开关切换后重算列表（开关写 UiPrefs，不走组合状态）
            var connectorTick by remember { mutableIntStateOf(0) }
            val connectorStates = remember(
                calendarGranted.value, notificationGranted.value,
                smsGranted.value, contactsGranted.value, connectorTick
            ) { buildConnectorStates(context) }
            val byKey = connectorStates.associateBy { it.key }
            fun toggle(key: String, enabled: Boolean) {
                UiPrefs.setConnectorEnabled(context, key, enabled)
                connectorTick++
            }
            // 开关就是连接/断开（设计稿 §2，2026-11-08 Bob verdict：每行只有一个控件）。
            // 走该来源既有的接上流程（显著披露/系统框/跳系统设置页，按设计稿 §5.2，
            // 不另起一套）；用户取消或拒绝，权限仍未授，开关按真实接上状态仍为关。
            // 点开关开且权限未授时先把启用意愿记下：接上成功后开关随权限翻转亮起；
            // 取消或拒绝则权限未变、开关仍为关。关开关立即停用该来源，不追问，
            // 系统权限本身不动（关开关 = 不再用这个来源，不是去系统里撤权限）。
            fun connect(key: String) {
                when (key) {
                    UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE ->
                        openNotificationSettings(context)
                    UiPrefs.CONNECTOR_CALENDAR -> openCalendar()
                    UiPrefs.CONNECTOR_MESSAGE -> disclosure = "sms"
                    UiPrefs.CONNECTOR_CONTACTS -> disclosure = "contacts"
                }
            }
            fun onConnectorToggle(state: ConnectorState, granted: Boolean, turnOn: Boolean) {
                when (resolveConnectorToggle(granted, turnOn)) {
                    ConnectorToggleAction.DISABLE -> toggle(state.key, false)
                    ConnectorToggleAction.ENABLE -> toggle(state.key, true)
                    ConnectorToggleAction.CONNECT -> { toggle(state.key, true); connect(state.key) }
                }
            }
            // 智能等级（设计稿 §2，2026-11-08 Bob 定）：已接 N/5 个源，数字随
            // 接上/断开实时变（connectorStates 本就随开关与权限状态重算）。
            val connectedCount = connectedSourceCount(connectorStates)
            Column {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "已接 $connectedCount/${SMART_SOURCE_ORDER.size} 个源",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { connectedCount / SMART_SOURCE_ORDER.size.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "智能来自这些源。每接一个，智能加一。各自单独接、随时能断。只在手机上处理，不发到别处。",
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Hint,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        byKey[UiPrefs.CONNECTOR_MESSAGE]?.let { state ->
                            SettingsConnectorRow(
                                state = state,
                                onToggle = { onConnectorToggle(state, smsGranted.value, it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        byKey[UiPrefs.CONNECTOR_NOTIFICATION]?.let { state ->
                            SettingsConnectorRow(
                                state = state,
                                onToggle = { onConnectorToggle(state, notificationGranted.value, it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        byKey[UiPrefs.CONNECTOR_CALENDAR]?.let { state ->
                            SettingsConnectorRow(
                                state = state,
                                onToggle = { onConnectorToggle(state, calendarGranted.value, it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        byKey[UiPrefs.CONNECTOR_PHONE]?.let { state ->
                            SettingsConnectorRow(
                                state = state,
                                onToggle = { onConnectorToggle(state, notificationGranted.value, it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        byKey[UiPrefs.CONNECTOR_CONTACTS]?.let { state ->
                            SettingsConnectorRow(
                                state = state,
                                onToggle = { onConnectorToggle(state, contactsGranted.value, it) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 默认桌面不是智能的源（设计稿 §2，2026-11-08 Bob 定）：不计入等级，
                // 挪出智能区、单独放一张卡；行本身不动（无开关，只有状态＋去设置）。
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsPermissionRow(
                            title = "默认桌面",
                            desc = "按 Home 键回到 AI 桌面",
                            granted = defaultGranted.value,
                            doneText = "已设为默认",
                            todoText = "尚未设为默认",
                            actionText = "去设置",
                            onOpen = { openHomeSettings(context) }
                        )
                    }
                }
            }
            if (disclosure == "sms") {
                AlertDialog(
                    onDismissRequest = { disclosure = null },
                    title = {
                        Text(
                            text = "接上消息",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                    },
                    text = {
                        Text(
                            text = "要读短信里的账单和体检预约提醒。只读近 7 天的短信，只解析账单、预约这两类，解析完不存原文、不发到别处。",
                            fontSize = AILauncherType.Body.fontSize,
                            color = AILauncherColors.current.Body
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { disclosure = null; openSms() }) {
                            Text(
                                text = "同意",
                                fontSize = AILauncherType.Callout.fontSize,
                                color = AILauncherColors.current.Accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { disclosure = null }) {
                            Text(
                                text = "取消",
                                fontSize = AILauncherType.Callout.fontSize,
                                color = AILauncherColors.current.Hint
                            )
                        }
                    }
                )
            }
            if (disclosure == "contacts") {
                AlertDialog(
                    onDismissRequest = { disclosure = null },
                    title = {
                        Text(
                            text = "接上联系人",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title
                        )
                    },
                    text = {
                        Text(
                            text = "要把未接来电的号码显示成名字。只按号码查姓名，不读其他联系人信息。",
                            fontSize = AILauncherType.Body.fontSize,
                            color = AILauncherColors.current.Body
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { disclosure = null; openContacts() }) {
                            Text(
                                text = "同意",
                                fontSize = AILauncherType.Callout.fontSize,
                                color = AILauncherColors.current.Accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { disclosure = null }) {
                            Text(
                                text = "取消",
                                fontSize = AILauncherType.Callout.fontSize,
                                color = AILauncherColors.current.Hint
                            )
                        }
                    }
                )
            }
        }

        // 分区：诊断——崩溃记录
        item {
            SettingsSectionTitle("诊断")
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
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
                                    fontSize = AILauncherType.BodyLarge.fontSize,
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when {
                                    crashRecords.isEmpty() -> "暂无记录，运行得很稳"
                                    crashUnread > 0 -> "有 $crashUnread 条新记录，点开查看"
                                    else -> "共 ${crashRecords.size} 条，点开查看或分享"
                                },
                                fontSize = AILauncherType.Footnote.fontSize,
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
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Title
                                )
                                Text(
                                    text = record.title,
                                    fontSize = AILauncherType.Caption.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            if (openedCrash == record.epochMillis) {
                                Text(
                                    text = record.content,
                                    fontSize = AILauncherType.Caption.fontSize,
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
                                        fontSize = AILauncherType.Footnote.fontSize,
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
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        // 角标诊断（Bob 2026-11-08 凌晨：我们的角标 6、系统桌面 15，差 9 条要看清去向；
        // 另：飞书 125 的中兴通道要实测，不要只推理）。只读展示，不做任何操作。
        // 数字为 0 也如实显示 0，不隐藏。包名太长只显后半段。
        // 诊断查询（短信库、ZTE provider）是阻塞调用，走后台线程跑。
        item {
            var smsDiag by remember { mutableStateOf<SmsDiagCounts?>(null) }
            var smsGranted by remember { mutableStateOf(false) }
            var defaultSmsPkg by remember { mutableStateOf<String?>(null) }
            var visibleSmsPkg by remember { mutableStateOf<String?>(null) }
            var feishuPkg by remember { mutableStateOf<String?>(null) }
            val lastBroadcast by BadgeProbe.lastBroadcast.collectAsState()
            val zteResult by BadgeProbe.zteResult.collectAsState()
            LaunchedEffect(Unit) {
                smsGranted = SmsRepository.hasSmsPermission(context)
                defaultSmsPkg = runCatching {
                    Telephony.Sms.getDefaultSmsPackage(context)
                }.getOrNull()
                val diag = withContext(Dispatchers.IO) {
                    val d = countSmsDiag(context)
                    val vis = SmsRepository.resolveVisibleSmsPackage(context)
                    val fei = BadgeProbe.resolveFeishuPackage(context)
                    Triple(d, vis, fei)
                }
                smsDiag = diag.first
                visibleSmsPkg = diag.second
                feishuPkg = diag.third
                withContext(Dispatchers.IO) {
                    val candidates = (NotificationRepository.badgeCounts.value.keys +
                        listOfNotNull(diag.third)).toSet()
                    BadgeProbe.probeZteProvider(context, candidates)
                }
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "角标诊断",
                        fontSize = AILauncherType.BodyLarge.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Title
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "只读：短信角标与飞书角标的真实数字，截个图就能对账",
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Hint,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BadgeDiagRow("读取短信权限", if (smsGranted) "已授" else "未授")
                    BadgeDiagRow("默认短信应用", shortPackageName(defaultSmsPkg))
                    BadgeDiagRow("可见短信应用", shortPackageName(visibleSmsPkg))
                    BadgeDiagRow("短信库未读（read=0）", "${smsDiag?.unreadRead0 ?: 0} 条")
                    BadgeDiagRow("短信库未看（seen=0）", "${smsDiag?.unseenSeen0 ?: 0} 条")
                    BadgeDiagRow("read=0 但 seen=1", "${smsDiag?.unreadButSeen ?: 0} 条")
                    BadgeDiagRow("收件箱总数", "${smsDiag?.inboxTotal ?: 0} 条")
                    BadgeDiagRow("最近 7 天收件箱", "${smsDiag?.inboxLast7Days ?: 0} 条")
                    BadgeDiagRow(
                        "角标广播",
                        when (val b = lastBroadcast) {
                            null -> "未收到"
                            else -> "已收到：${shortPackageName(b.pkg)}=${b.count}（${
                                java.text.SimpleDateFormat(
                                    "HH:mm", java.util.Locale.getDefault()
                                ).format(java.util.Date(b.at))
                            }）"
                        }
                    )
                    val zte = zteResult
                    BadgeDiagRow(
                        "ZTE provider",
                        when {
                            zte == null -> "探针未跑"
                            zte.queryOk || zte.callOk -> "可读"
                            else -> "不可读"
                        }
                    )
                    BadgeDiagRow(
                        "飞书数",
                        when {
                            feishuPkg == null -> "未安装"
                            zte == null -> "—"
                            else -> zte.counts[feishuPkg]?.let { "$it 条" } ?: "—"
                        }
                    )
                    if (zte != null && !(zte.queryOk || zte.callOk)) {
                        Text(
                            text = "探针详情：${zte.queryDetail}；${zte.callDetail}",
                            fontSize = AILauncherType.Caption.fontSize,
                            color = AILauncherColors.current.Hint,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        // 分区：关于——落款
        item {
            SettingsSectionTitle("关于")
        }

        // 落款 / 关于：手写体 bobot 词标（Bob 2026-11-07 发来的图，
        // 同款矢量版 bobot-wordmark.svg 的透明底渲染，1372x632，#a13f04）
        // + 联系邮箱。词标是图片资产（res/drawable/bobot_wordmark.png），
        // 不是排版文字；点邮箱打开邮件应用写信。
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.bobot_wordmark),
                    contentDescription = "bobot",
                    modifier = Modifier.width(140.dp).aspectRatio(1372f / 632f),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        view.tickHaptic()
                        val mail = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:0xbobot@gmail.com")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(mail) }
                            .onFailure { IslandToasts.show("没有可用的邮箱应用") }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "0xbobot@gmail.com",
                        fontSize = AILauncherType.Body.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

    }
}

/**
 * 设置 → 智能区的一行（447；2026-11-08 Bob 真机 verdict 后改：每行只有一个控件）。
 * 标题＋它提供什么＋开关＋状态行。开关就是连接/断开：开关状态 = 真实接上状态
 * （权限已授 + 来源启用），未接上时开关就是关的，不许出现“开关开着、状态写着
 * 未接上”；状态文字只做展示、不可点，不再有“去开启/接上”文字按钮。关开关立即
 * 停用该来源（卡片不再出该来源内容），系统权限本身不动；开开关走该来源既有的
 * 接上流程，用户取消或拒绝则开关保持关。电话拨号器无权限：开关就是纯粹的
 * 启用/停用。触控：开关走 Material3 默认 48dp 热区。
 */
@Composable
private fun SettingsConnectorRow(
    state: ConnectorState,
    onToggle: (Boolean) -> Unit
) {
    val view = LocalView.current
    val connected = isConnectorConnected(state)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    fontSize = AILauncherType.Body.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.desc,
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Hint,
                    lineHeight = 18.sp
                )
            }
            Switch(
                checked = connected,
                onCheckedChange = { view.tickHaptic(); onToggle(it) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (connected) AILauncherColors.current.Success
                    else AILauncherColors.current.Hint,
                modifier = Modifier
                    .size(16.dp)
                    .semantics { invisibleToUser() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = connectorStatusText(state),
                fontSize = AILauncherType.Footnote.fontSize,
                color = if (connected) AILauncherColors.current.Success
                    else AILauncherColors.current.Hint
            )
        }
    }
}

/**
 * 设置页分区标题：与应用中心分区标题同一语言
 * （Footnote / Medium / Hint / 左右 24dp）。
 */
/** 角标诊断行：左标签、右数值；数值过长截断，不换行挤版式。 */
@Composable
private fun BadgeDiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = AILauncherType.Body.fontSize,
            color = AILauncherColors.current.Body,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = AILauncherType.Body.fontSize,
            color = AILauncherColors.current.Title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 包名太长只显后半段（超 24 字显 … + 末 21 字）；空显示“未设置”。 */
private fun shortPackageName(pkg: String?): String {
    if (pkg.isNullOrBlank()) return "未设置"
    return if (pkg.length <= 24) pkg else "…" + pkg.takeLast(21)
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = AILauncherType.Footnote.fontSize,
        fontWeight = FontWeight.Medium,
        color = AILauncherColors.current.Hint,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

/**
 * 设置页默认桌面行（448 自旧“权限”分区并入提醒来源卡；2026-11-08 Bob 定：
 * 默认桌面不是智能的源，挪出智能区、单独放一张卡，行本身不动）：
 * 标题＋好处＋状态行＋去开启（已开启时只显示状态，不再给无意义的按钮；
 * 触控走 TextButton，热区 ≥48dp）。
 */
@Composable
private fun SettingsPermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    doneText: String,
    todoText: String,
    actionText: String,
    onOpen: () -> Unit
) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = AILauncherType.Body.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    fontSize = AILauncherType.Footnote.fontSize,
                    color = AILauncherColors.current.Hint,
                    lineHeight = 18.sp
                )
            }
            if (!granted) {
                TextButton(onClick = { view.tickHaptic(); onOpen() }) {
                    Text(
                        text = actionText,
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (granted) AILauncherColors.current.Success else AILauncherColors.current.Hint,
                modifier = Modifier
                    .size(16.dp)
                    .semantics { invisibleToUser() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (granted) doneText else todoText,
                fontSize = AILauncherType.Footnote.fontSize,
                color = if (granted) AILauncherColors.current.Success else AILauncherColors.current.Hint
            )
        }
    }
}
