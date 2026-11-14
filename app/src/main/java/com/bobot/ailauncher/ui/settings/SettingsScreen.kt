@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.settings

import android.content.Intent
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import com.bobot.ailauncher.data.ConnectorState
import com.bobot.ailauncher.data.SmartCardRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.SMART_SOURCE_ORDER
import com.bobot.ailauncher.data.buildConnectorStates
import com.bobot.ailauncher.data.connectedSourceCount
import com.bobot.ailauncher.data.connectorStatusText
import com.bobot.ailauncher.data.isDialerRowEnabled
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
import com.bobot.ailauncher.ui.settings.isDefaultLauncher
import com.bobot.ailauncher.ui.settings.isNotificationAccessGranted
import com.bobot.ailauncher.ui.settings.isSmsGranted
import com.bobot.ailauncher.ui.settings.openHomeSettings
import com.bobot.ailauncher.ui.settings.openNotificationSettings
import com.bobot.ailauncher.ui.settings.rememberCalendarPermissionOpener
import com.bobot.ailauncher.ui.settings.rememberPermissionState
import com.bobot.ailauncher.ui.settings.rememberSmsPermissionOpener
import com.bobot.ailauncher.util.rebindListener
import com.bobot.ailauncher.ui.home.clearIntentFocusOnOutsideTap
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 「让助手更聪明」卡收起行文案（纯函数，2026-11-14 Bob 定：与来源卡同一口径，
 * 收起只留一行 + chevron）：已填 Key 写「大模型已经配好了」，没填写卡名；
 * 展开态标题恒为卡名。说明文字只在展开态出现，见下面卡片内。
 */
fun llmCardTitle(expanded: Boolean, apiKeyBlank: Boolean): String =
    if (!expanded && !apiKeyBlank) "大模型已经配好了" else "让助手更聪明"

/**
 * 来源卡收起行描述（纯函数，2026-11-14 Bob 定：收起加一行，告诉用户接上
 * 这些来源会起什么作用；展开态完整说明不动）：接上之后桌面会主动提醒。
 * 收起只在 4/4 时出现，描述不带数字——数字已在标题行。
 */
fun sourcesCardCollapsedHint(): String = "接上之后，桌面会主动提醒你该做的事。"

/**
 * 「让助手更聪明」卡收起行描述（纯函数，2026-11-14 Bob 定：收起加一行，
 * 告诉用户配好大模型会起什么作用；展开态完整说明不动）：没填 Key 写配好
 * 之后的效果，填了写已经配好的效果。
 */
fun llmCardCollapsedHint(apiKeyBlank: Boolean): String =
    if (apiKeyBlank) "配好之后，助手能听懂你说话、帮你办事。" else "配好了，助手能听懂你说话、帮你办事。"

/**
 * 版本卡上行（更新行）主文案（纯函数，2026-11-14 Bob 定：检查更新 +
 * 加入测试计划合成一张卡）：检查中 / 下载中 / 下完 / 失败 / 有新版 /
 * 平时六态，与原来检查更新卡逐字一致；合并后行内复用。
 */
fun versionUpdateTitle(
    checking: Boolean,
    downloading: Boolean,
    downloadDone: Boolean,
    downloadFailed: Boolean,
    updateVersionName: String?,
): String = when {
    checking -> "正在检查..."
    downloading -> "正在下载 v${updateVersionName ?: ""}"
    downloadDone -> "下载完成"
    downloadFailed -> "下载失败"
    updateVersionName != null -> "发现新版本 v$updateVersionName"
    else -> "检查更新"
}

/**
 * 版本卡上行副文案（纯函数）：下完「点按安装 vX」/ 失败「点按重试」/
 * 有新版更新说明 / 平时「当前版本 vX」。下载中返回 null——下载中副文
 * 是进度条 + downloadStatusText，不走这里。与原来检查更新卡逐字一致。
 */
fun versionUpdateHint(
    downloading: Boolean,
    downloadDone: Boolean,
    downloadFailed: Boolean,
    updateVersionName: String?,
    changelog: String?,
    currentVersionName: String,
): String? = when {
    downloading -> null
    downloadDone -> "点击安装 v${updateVersionName ?: ""}"
    downloadFailed -> "点击重试"
    updateVersionName != null -> changelog
    else -> "当前版本 v$currentVersionName"
}

/**
 * 版本卡上行是否可点（纯函数）：检查中 / 下载中点按无反应，其余各态
 * 都有动作（检查 / 下载 / 安装 / 重试）。
 */
fun isVersionUpdateRowClickable(checking: Boolean, downloading: Boolean): Boolean =
    !checking && !downloading

/**
 * 设置页（v0.57.0 重构）：
 * - 只剩三项：让助手更聪明（收起）/ 关于 / 接收测试版更新（v0.68.80）
 * - 电量守护默认开启，不设开关；Dock 显示纯手势；排序默认智能
 * - 文案全部用户视角
 */
@Composable
fun SettingsScreen(
    onOpenJumpLog: () -> Unit = {}
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
    // 智能区来源卡用户点过的收起态（2026-11-14 Bob 定：4/4 默认收起）；
    // null 表从未点过——首次接满 4/4 时默认收起。进设置页重读 prefs。
    var sourcesCollapsedOverride by remember { mutableStateOf(UiPrefs.getSourcesCardCollapsed(context)) }
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

            // 上行文案走纯函数（单测锁死，见 VersionCardTest）：主文六态、
            // 副文四态，与原来检查更新卡逐字一致。
            val updateTitle = versionUpdateTitle(
                checking, downloading, dlDone, dlFailed, updateInfo?.versionName
            )
            val updateHint = versionUpdateHint(
                downloading, dlDone, dlFailed,
                updateInfo?.versionName, updateInfo?.changelog,
                BuildConfig.VERSION_NAME
            )

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

            // 版本（2026-11-14 Bob 定：检查更新 + 加入测试计划合成一张卡，
            // 两行、中间一条细分隔线；上行更新各态与点按行为原样保留，
            // 下行只留标题 + 开关，说明文字不要了）
            var betaOptIn by remember { mutableStateOf(OtaUpdater.isBetaOptIn(context)) }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
            ) {
                Column {
                    // 上行：更新行——点按只走更新（F-10：可点态补 tick；
                    // 检查中/下载中不可点，不震，与视觉禁用一致）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onCardClick != null) {
                                onCardClick?.let { view.tickHaptic(); it() }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = updateTitle,
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
                                    text = updateHint.orEmpty(),
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Accent
                                )
                                dlFailed -> {
                                    Text(
                                        text = updateHint.orEmpty(),
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
                                    text = updateHint.orEmpty(),
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                else -> Text(
                                    text = updateHint.orEmpty(),
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
                // 两行之间的细分隔线（与应用弹窗同一口径：
                // HorizontalDivider + 横向 16.dp + 主题 Divider 色）
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = AILauncherColors.current.Divider
                )
                // 下行：测试计划行——只有开关切换（v0.68.80：开启后可收到
                // prerelease 测试版推送）；说明文字不要了，开关本身就是意思。
                // 点按开关只走测试计划，不触发上行更新；下载中开关照常用。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "加入测试计划",
                        fontSize = AILauncherType.BodyLarge.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = AILauncherColors.current.Title,
                        modifier = Modifier.weight(1f)
                    )
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
        }

        // 分区：智能——智能卡片的四个本机连接器就是智能的源（447 起，设计稿 §2/§5）
        // 各自单独接、有明确理由、随时可断；数据只在手机上处理，不发到别处。
        // 2026-11-08 Bob 定：分区改名“智能”（不再叫“提醒来源”），区顶显示
        // 智能等级（已经接上了 N/4 个来源），行序按源对智能的提升比例（消息>通知>
        // 日历>电话拨号器）；默认桌面不是源，不在本区（Bob 2026-11-14 07:33：
        // 独立成区，放在智能之后、关于之前）。
        // 联系人已取消（第十四批，2026-11-10 Bob 定：不是必须的，不申请
        // READ_CONTACTS）——没有联系人行，等级按四个源算。
        // 448 合并：旧“权限”分区并入本分区——日历、通知只出现一次（原两处重叠）。
        // 引导里跳过/拒绝后随时可补授权的承诺仍由本分区兑现。
        // 第十三批（Bob 2026-11-10 定：双重关全改掉）：开关只显示接没接上
        // （接上＝权限给了），点它走授权/去系统设置，不再是一道关；等级只认权限。
        item {
            SettingsSectionTitle("智能")
        }

        item {
            val calendarGranted = rememberPermissionState { isCalendarGranted(context) }
            val notificationGranted = rememberPermissionState { isNotificationAccessGranted(context) }
            val smsGranted = rememberPermissionState { isSmsGranted(context) }
            val openCalendar = rememberCalendarPermissionOpener(calendarGranted)
            val openSms = rememberSmsPermissionOpener(smsGranted)
            // 应用运行中去设置里开通知读取权限时系统不回调，在这里重绑监听
            OnGrantedTransition(notificationGranted.value) { rebindListener(context) }
            // 消息刚接上就立即重读短信（不然卡片要等下次回桌面才出现）
            OnGrantedTransition(smsGranted.value) {
                SmartCardRepository.refreshSms(context, force = true)
            }
            // 显著披露：先讲清要什么、为什么，用户点“同意”后再弹系统框
            // （设计稿 §5.4：显著披露在第一次请求权限之前）。
            var disclosure by remember { mutableStateOf<String?>(null) }
            val connectorStates = remember(
                calendarGranted.value, notificationGranted.value,
                smsGranted.value
            ) { buildConnectorStates(context) }
            val byKey = connectorStates.associateBy { it.key }
            // 开关只显示接没接上（第十三批，Bob 2026-11-10 定：双重关全改掉）：
            // 接上＝权限给了，开关不再是一道关。点没接上的开关 → 走该来源
            // 既有的接上流程（显著披露/系统框/跳系统设置页，按设计稿 §5.2，
            // 不另起一套）；用户取消或拒绝，权限仍未授，开关仍为关。
            // 点已接上的开关 → 跳系统设置页：权限在那里管，关掉回来后开关
            // 跟着变（断开＝去系统里撤权限，应用内没有撤权限的开关了；
            // 点按必须有反应，不静默无动作）。
            fun openConnectorSettings(key: String) {
                when (key) {
                    UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE ->
                        openNotificationSettings(context)
                    else -> openAppDetailsSettings(context)
                }
            }
            fun connect(key: String) {
                when (key) {
                    UiPrefs.CONNECTOR_NOTIFICATION, UiPrefs.CONNECTOR_PHONE ->
                        openNotificationSettings(context)
                    UiPrefs.CONNECTOR_CALENDAR -> openCalendar()
                    UiPrefs.CONNECTOR_MESSAGE -> disclosure = "sms"
                }
            }
            fun onConnectorToggle(state: ConnectorState, turnOn: Boolean) {
                if (turnOn) connect(state.key) else openConnectorSettings(state.key)
            }
            // 智能等级（设计稿 §2，2026-11-08 Bob 定）：已经接上了 N/4 个来源，
            // 数字随接上/断开实时变（connectorStates 本就随权限状态重算；第十三批起只认权限）。
            // 2026-11-14 Bob 定：4 个源全接上时这块默认收起，收起是标题行 +
            // 一行描述 + chevron（描述写接上之后的效果，sourcesCardCollapsedHint）；
            // 没接满时保持展开，方便接上剩下的。收起/展开记下来，下次进
            // 设置页还按上次的来；4/4 首次接满（从未点过）默认收起。
            val connectedCount = connectedSourceCount(connectorStates)
            val allSourcesConnected = connectedCount == SMART_SOURCE_ORDER.size
            val sourcesCollapsed = UiPrefs.effectiveSourcesCardCollapsed(
                allSourcesConnected, sourcesCollapsedOverride
            )
            Column {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 展开/收起只认标题行（与「让助手更聪明」卡同一口径 F-08）；
                        // 4/4 时标题行可点（带 tick），没接满时不可点、保持展开。
                        Row(
                            modifier = if (allSourcesConnected) {
                                Modifier.fillMaxWidth().clickable {
                                    view.tickHaptic()
                                    val next = !sourcesCollapsed
                                    sourcesCollapsedOverride = next
                                    UiPrefs.setSourcesCardCollapsed(context, next)
                                }
                            } else {
                                Modifier.fillMaxWidth()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已经接上了 $connectedCount/${SMART_SOURCE_ORDER.size} 个来源",
                                fontSize = AILauncherType.BodyLarge.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.current.Title,
                                modifier = Modifier.weight(1f)
                            )
                            if (allSourcesConnected) {
                                Icon(
                                    if (sourcesCollapsed) Icons.Filled.KeyboardArrowDown
                                    else Icons.Filled.KeyboardArrowUp,
                                    contentDescription = if (sourcesCollapsed) "展开" else "收起",
                                    tint = AILauncherColors.current.Hint
                                )
                            }
                        }
                        if (sourcesCollapsed) {
                            // 2026-11-14 Bob 定：收起加一行描述，告诉用户接上
                            // 这些来源会起什么作用；一行写完，展开态说明不动。
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = sourcesCardCollapsedHint(),
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!sourcesCollapsed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { connectedCount / SMART_SOURCE_ORDER.size.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "智能就来自下面这几处。每多接一处，智能就强一点。每个都能单独接、随时断开。数据只在这台手机上处理，不会发到别处。",
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            byKey[UiPrefs.CONNECTOR_MESSAGE]?.let { state ->
                                SettingsConnectorRow(
                                    state = state,
                                    onToggle = { onConnectorToggle(state, it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            byKey[UiPrefs.CONNECTOR_NOTIFICATION]?.let { state ->
                                SettingsConnectorRow(
                                    state = state,
                                    onToggle = { onConnectorToggle(state, it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            byKey[UiPrefs.CONNECTOR_CALENDAR]?.let { state ->
                                SettingsConnectorRow(
                                    state = state,
                                    onToggle = { onConnectorToggle(state, it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            byKey[UiPrefs.CONNECTOR_PHONE]?.let { state ->
                                // 电话拨号器开关跟着通知走（设计稿 §2，2026-11-08 Bob 定；
                                // 第十三批改为跟着通知的权限走）：它的 granted 本就是
                                // 从通知推导的，直接按 state 渲染；通知没接上时行变灰
                                // 点不动（isDialerRowEnabled），不能单独开。
                                SettingsConnectorRow(
                                    state = state,
                                    enabled = isDialerRowEnabled(connectorStates),
                                    onToggle = { onConnectorToggle(state, it) }
                                )
                            }
                        }
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
        }

        // 大模型设置（Bob 2026-11-14 00:11）：从助手分区整块挪到智能分区——
        // Key 输入框 + DeepSeek 链接 + 接口地址 + 模型名 + 测试连接 + Key 存放形态
        // 明示一起搬；助手分区随之取消（只剩这一块）。
        // 让助手更聪明（收起一层）：2026-11-14 Bob 定，收起样式与来源卡一致——
        // 收起是标题行 + 一行描述 + chevron，完整说明文字挪进展开块；已填 Key
        // 时收起行写「大模型已经配好了」，没填写卡名（llmCardTitle），描述行
        // 写配好之后的效果（llmCardCollapsedHint）。
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
                    // 收起行与来源卡同一口径：标题行 + 一行描述 + chevron
                    // （llmCardTitle + llmCardCollapsedHint），完整说明文字
                    // 只在展开块里。
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
                                text = llmCardTitle(llmExpanded, apiKey.isBlank()),
                                fontSize = AILauncherType.BodyLarge.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = AILauncherColors.current.Title
                            )
                            if (!llmExpanded) {
                                // 2026-11-14 Bob 定：收起加一行描述，告诉用户配好
                                // 大模型会起什么作用；一行写完，展开态说明不动。
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = llmCardCollapsedHint(apiKey.isBlank()),
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            if (llmExpanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (llmExpanded) "收起" else "展开",
                            tint = AILauncherColors.current.Hint
                        )
                    }
                    if (llmExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "填上大模型 API Key，助手能听懂你说话、帮你办事。不填也能用，只是没那么聪明。",
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Hint,
                            lineHeight = 18.sp
                        )
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
                        TextButton(
                            onClick = {
                                view.tickHaptic()
                                val web = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://platform.deepseek.com/api_keys")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(web) }
                                    .onFailure { IslandToasts.show("没有可用的浏览器") }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "去 DeepSeek 开放平台注册 / 查看 Key",
                                fontSize = AILauncherType.Caption.fontSize,
                                color = AILauncherColors.current.Accent
                            )
                        }
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

        // 分区：默认桌面（Bob 2026-11-14 07:33）：从智能分区搬出来独立成区，
        // 只放这一行，放在智能之后、关于之前。行本身不动（无开关，只有状态＋去设置）。
        item {
            SettingsSectionTitle("默认桌面")
        }

        item {
            val defaultGranted = rememberPermissionState { isDefaultLauncher(context) }
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

        // 分区：关于——落款
        item {
            SettingsSectionTitle("关于")
        }

        // 未读跳转日志（Bob 2026-11-11 21:59：删质检、加日志）：点开看
        // 最近点按记录（只读）+ 分享。只加这一行，放在“关于”区——
        // 版本/智能/默认桌面都是功能区，关于是唯一收杂项的区；474 删掉的
        // 诊断区不重建；落款词标仍在最末尾。
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .shadow(
                        8.dp,
                        RoundedCornerShape(20.dp),
                        spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f)
                    ),
                onClick = {
                    view.tickHaptic()
                    onOpenJumpLog()
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "未读跳转日志",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点未读行后有没有打开应用、为什么没打开，记在这里",
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Hint,
                            lineHeight = 18.sp
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = AILauncherColors.current.Hint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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
 * 标题＋它提供什么＋开关＋状态行。开关只显示接没接上（第十三批：接上＝权限给了，
 * 开关不再是一道关；不许出现“开关开着、状态写着未接上”）；状态文字只做展示、
 * 不可点，不再有“去开启/接上”文字按钮。点没接上的开关走该来源的接上流程，
 * 点已接上的开关跳系统设置页（在那里可以关掉这个权限）。电话拨号器无权限：
 * 开关反映通知的状态，通知没接上时行变灰点不动（见 isDialerRowEnabled），
 * 不能单独开。触控：开关走 Material3 默认 48dp 热区。
 * 电话拨号器行跟着通知走（设计稿 §2，2026-11-08 Bob 定）：通知没接上时该行
 * enabled=false——标题/说明/状态文字走 Hint 色、开关用 Material3 自身的
 * 禁用态、点不动；通知接上时行恢复可点。
 */
@Composable
private fun SettingsConnectorRow(
    state: ConnectorState,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val view = LocalView.current
    val connected = state.granted
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    fontSize = AILauncherType.Body.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) AILauncherColors.current.Title
                        else AILauncherColors.current.Hint
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
                onCheckedChange = { view.tickHaptic(); onToggle(it) },
                enabled = enabled
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
 * 默认桌面不是智能的源；2026-11-14 Bob 定：独立成区（智能之后、关于之前），行本身不动）：
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
