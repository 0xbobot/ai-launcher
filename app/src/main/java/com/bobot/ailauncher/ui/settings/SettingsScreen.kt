@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.text.font.FontFamily
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
import com.bobot.ailauncher.data.UPDATE_INSTALL_LABEL
import com.bobot.ailauncher.data.UPDATE_RETRY_LABEL
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
 * 智能区合并卡两行可见态（纯函数状态机，2026-11-14 Bob 定：两行各展开各的）：
 * 来源行收起与否 = effectiveSourcesCardCollapsed（没接满强制展开）；
 * 大模型行展开与否 = llmExpanded 原值。一行点按只翻转本行——来源行点按
 * 改 storedCollapsed，大模型行点按改 llmExpanded，另一行原样不动。
 */
data class SmartSectionRowsVisible(val sourcesCollapsed: Boolean, val llmExpanded: Boolean)

fun smartSectionRowsVisible(
    allConnected: Boolean,
    sourcesStoredCollapsed: Boolean?,
    llmExpanded: Boolean,
): SmartSectionRowsVisible = SmartSectionRowsVisible(
    sourcesCollapsed = UiPrefs.effectiveSourcesCardCollapsed(allConnected, sourcesStoredCollapsed),
    llmExpanded = llmExpanded,
)

/**
 * 版本卡上行（更新行）主文案（纯函数，2026-11-14 Bob 定：检查更新 +
 * 加入测试计划合成一张卡）：检查中 / 下载中 / 下完 / 失败 / 有新版 /
 * 平时六态，逐字锁死；合并后行内复用。
 */
fun versionUpdateTitle(
    checking: Boolean,
    downloading: Boolean,
    downloadDone: Boolean,
    downloadFailed: Boolean,
    updateVersionName: String?,
): String = when {
    checking -> "正在帮你检查…"
    downloading -> "正在下载 v${updateVersionName ?: ""}"
    downloadDone -> "已经下载好了"
    downloadFailed -> "这次没下载下来"
    updateVersionName != null -> "有新版本 v$updateVersionName，可以更新了"
    else -> "检查更新"
}

/**
 * 版本卡上行副文案（纯函数）：有新版更新说明 / 平时「当前版本 vX」。
 * 下完／失败／下载中返回 null——下完与失败态的动作已由行内按钮
 * （安装／重试）承担，不再用副文教用户点哪里；下载中副文是进度条
 * + downloadStatusText，不走这里。逐字锁死。
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
    downloadDone -> null
    downloadFailed -> null
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
 * 版本卡下行（加入测试计划）副文案（纯函数，2026-11-14 Bob 定：
 * 不要一句写死——随开关状态变，告诉用户升级会升到什么版本）。
 * 两个轨道的最新版本号来源不同：测试轨道 = update.json 的 versionName
 * （复用更新行已有的检查结果，不为副文单独再发 update.json 请求）；
 * 正式轨道 = releases/latest.json 的 versionName（设置页打开时读一次
 * 存 prefs，见 OtaUpdater.refreshFormalLatest）。
 * 比较用 versionCode，versionName 只做展示；五种情况逐字锁死（见
 * VersionCardTest）。任一轨道版本号未知 → 兜底通用句。
 */
fun betaPlanHint(
    betaOptIn: Boolean,
    latestTestVersion: OtaInfo?,
    latestFormalVersion: OtaInfo?,
    currentVersionCode: Int,
): String {
    if (betaOptIn) {
        val test = latestTestVersion ?: return "开着收测试版，关掉只收正式版。"
        return if (test.versionCode > currentVersionCode) {
            "开着，下次升级会升到测试版 v${test.versionName}。"
        } else {
            "开着，已经是最新测试版了。"
        }
    }
    val formal = latestFormalVersion ?: return "开着收测试版，关掉只收正式版。"
    return if (formal.versionCode > currentVersionCode) {
        "关着，下次升级只会升到正式版 v${formal.versionName}。"
    } else {
        "关着，已经是最新正式版了。"
    }
}

/**
 * 「分享给朋友」的分享文本（纯函数，2026-11-18 Bob 定放关于区）：
 * 分享的是下载页地址，不是 APK 直链——对方打开是下载页（有介绍、
 * 自己选版本下），不是直接下一个安装包。文案逐字锁死
 *（见 ShareFriendsTextTest）。
 */
const val SHARE_FRIENDS_DOWNLOAD_PAGE_URL = "https://bobot.is-a.dev/ai-launcher/"

fun shareFriendsText(): String =
    "我在用的 AI 桌面，下载页在这里：$SHARE_FRIENDS_DOWNLOAD_PAGE_URL"

/**
 * 设置页（2026-11-23 Bob 定 v3-B 两大组重组）：
 * - 两组：桌面（版本＋默认桌面）/ 智能（智能＋关于），各一张大卡；
 *   卡内小标签（版本、默认桌面、关于）保留，文案一字不动；
 * - 横屏两张卡并排等高，竖屏同一分组纵向堆叠、单列满宽；
 * - 未读打开记录与崩溃记录已删（Bob：中间产物），入口与查看屏一并删；
 *   关于只剩分享给朋友＋联系我；落款只留 bobot 词标。
 */
@Composable
fun SettingsScreen() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
    val hideOnBlur = Modifier.onFocusChanged { if (!it.isFocused) keyboardController?.hide() }

    // 设置页底部上滑回主页已删除（Bob 2026-11-03：避免操作干扰，与应用中心同批）
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 横竖屏同一分组：横屏两卡并排，竖屏纵向堆叠（HomeScreen 同款 maxWidth > maxHeight 判定）
        val isLandscape = maxWidth > maxHeight
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

            // 两大组：桌面 / 智能（2026-11-23 Bob 定 v3-B）。横屏两张卡并排等高，
            // 竖屏同一分组纵向堆叠；组内行与展开态原样保留。
            item {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DesktopGroupCard(modifier = Modifier.weight(1f).fillMaxHeight())
                        SmartGroupCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            hideOnBlur = hideOnBlur,
                            fieldFocusMods = { index -> fieldFocusMods(index) }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DesktopGroupCard(modifier = Modifier.fillMaxWidth())
                        SmartGroupCard(
                            modifier = Modifier.fillMaxWidth(),
                            hideOnBlur = hideOnBlur,
                            fieldFocusMods = { index -> fieldFocusMods(index) }
                        )
                    }
                }
            }

            // 落款：只留手写体 bobot 词标（Bob 2026-11-07 发来的图，
            // 同款矢量版 bobot-wordmark.svg 的透明底渲染，1372x632，#a13f04）。
            // 词标是图片资产（res/drawable/bobot_wordmark.png），不是排版文字；
            // 联系邮箱已挪进关于区“联系我”一行，这里不再放邮箱。
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
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * 设置页分组大卡（2026-11-23 v3-B）：组标题（宋体 Bold 20sp）+ 组内内容。
 * 壳与现版卡片同一口径（20dp 圆角、主题 Card 色、8dp 阴影）。
 */
@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
    ) {
        Column {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = AILauncherType.Title.fontSize,
                letterSpacing = 2.sp,
                color = AILauncherColors.current.Title,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            content()
        }
    }
}

/** 桌面组：版本（检查更新 + 加入测试计划）+ 默认桌面。 */
@Composable
private fun DesktopGroupCard(modifier: Modifier = Modifier) {
    SettingsGroupCard(modifier = modifier, title = "桌面") {
        SettingsGroupSubLabel("版本")
        VersionSectionBody()
        SettingsGroupSubLabel("默认桌面")
        DefaultDesktopSectionBody()
    }
}

/** 智能组：智能（来源 + 大模型）+ 关于（分享给朋友 + 联系我）。 */
@Composable
private fun SmartGroupCard(
    modifier: Modifier = Modifier,
    hideOnBlur: Modifier,
    fieldFocusMods: (Int) -> Modifier
) {
    SettingsGroupCard(modifier = modifier, title = "智能") {
        SmartSectionBody(hideOnBlur = hideOnBlur, fieldFocusMods = fieldFocusMods)
        SettingsGroupSubLabel("关于")
        AboutSectionBody()
    }
}

/**
 * 版本区内容（不含卡片壳）：更新行 + 分隔线 + 测试计划行。
 * 逻辑与文案原样保留（2026-11-23 v3-B：只换壳，行不动）。
 */
@Composable
private fun VersionSectionBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }
    val dlState by OtaDownloader.state.collectAsState()

    // 下行副文的两个版本号来源：测试轨道复用更新行已有的检查结果
    // （手动检查的 updateInfo / 每天自动检查的 latestAvailable，
    // 不为副文单独再发 update.json 请求）；正式轨道读 prefs 里存的
    // 上次读到的 releases/latest.json，设置页打开时刷新一次
    // （LaunchedEffect 一次，刷新本身在 Dispatchers.IO、失败全吞）。
    val latestAvailable by OtaUpdater.latestAvailable.collectAsState()
    var formalLatest by remember { mutableStateOf(OtaUpdater.formalLatest(context)) }
    LaunchedEffect(Unit) {
        OtaUpdater.refreshFormalLatest(context)
        formalLatest = OtaUpdater.formalLatest(context)
    }

    val downloading = dlState is OtaDownloader.State.Downloading
    val dlProgress = (dlState as? OtaDownloader.State.Downloading)?.progress ?: 0f
    val dlDone = dlState is OtaDownloader.State.Success
    val dlFailed = dlState is OtaDownloader.State.Failed

    // 已知的新版本（第四十五批，设计 §9）：手动检查的 updateInfo
    // 优先，每天自动检查的 latestAvailable 兜底——两个来源发现的
    // 新版都一样：行内显示「有新版本 vX.X.X」＋「去更新」按钮，
    // 点它直接启动后台下载（同一个 OtaDownloader.start，与升级卡
    // 里的「去更新」是同一个动作），不弹对话框。
    val availableInfo = updateInfo ?: latestAvailable

    // 上行文案走纯函数（单测锁死，见 VersionCardTest）：主文六态、
    // 副文四态，与原来检查更新卡逐字一致。
    val updateTitle = versionUpdateTitle(
        checking, downloading, dlDone, dlFailed, availableInfo?.versionName
    )
    val updateHint = versionUpdateHint(
        downloading, dlDone, dlFailed,
        availableInfo?.versionName, availableInfo?.changelog,
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
                    IslandToasts.show("安装包不完整，我再重新下载一次")
                    OtaDownloader.resetIfNotDownloading()
                    availableInfo?.let { OtaDownloader.start(context, it) }
                }
            }
        }
        dlFailed -> {
            {
                availableInfo?.let { OtaDownloader.start(context, it) }
                    ?: run {
                        checking = true
                        scope.launch {
                            when (val r = OtaUpdater.checkForUpdateResult(context)) {
                                is OtaCheckResult.UpdateAvailable -> {
                                    updateInfo = r.info
                                    OtaUpdater.latestAvailable.value = r.info
                                    OtaDownloader.start(context, r.info)
                                }
                                else -> IslandToasts.show("这次没检查成，稍后再试试吧")
                            }
                            checking = false
                        }
                    }
            }
        }
        availableInfo != null -> {
            { OtaDownloader.start(context, availableInfo!!) }
        }
        else -> {
            {
                checking = true
                scope.launch {
                    when (val r = OtaUpdater.checkForUpdateResult(context)) {
                        is OtaCheckResult.UpdateAvailable -> {
                            // 手动检查发现新版：除了本地 updateInfo，
                            // 也写进 latestAvailable——升级卡的出现
                            // 条件就是 latestAvailable 非空（设计 §3，
                            // 自动检查或手动检查发现了新版都出卡）。
                            updateInfo = r.info
                            OtaUpdater.latestAvailable.value = r.info
                        }
                        OtaCheckResult.UpToDate ->
                            IslandToasts.show("你用的已经是最新版本了")
                        OtaCheckResult.Failed ->
                            IslandToasts.show("这次没检查成，稍后再试试吧")
                    }
                    checking = false
                }
            }
        }
    }

    // 版本（2026-11-14 Bob 定：检查更新 + 加入测试计划合成一张卡，
    // 两行、中间一条细分隔线；上行更新各态与点按行为原样保留，
    // 下行标题 + 动态副文 + 开关）
    var betaOptIn by remember { mutableStateOf(OtaUpdater.isBetaOptIn(context)) }
    // 下行副文随开关状态变（betaPlanHint 五种情况）：betaOptIn 是
    // 屏幕持有的 state，开关一拨副文立刻跟着变；测试轨道取更新行
    // 已有的检查结果（updateInfo 优先，自动检查的 latestAvailable
    // 兜底），正式轨道取 prefs 里的上次读数。
    val betaHint = betaPlanHint(
        betaOptIn,
        updateInfo ?: latestAvailable,
        formalLatest,
        BuildConfig.VERSION_CODE
    )
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
                    // 下完态：副文已删，行内直接给「安装」按钮（与
                    // 「去更新」同款 TextButton）；整行可点＋按钮双通道，
                    // 点按钮与点整行是同一个安装动作（onCardClick）。
                    dlDone -> TextButton(
                        onClick = {
                            view.tickHaptic()
                            onCardClick?.invoke()
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = UPDATE_INSTALL_LABEL,
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Accent
                        )
                    }
                    dlFailed -> {
                        // 失败态：副文已删，行内直接给「重试」按钮（与
                        // 「去更新」同款）；标题「这次没下载下来」留作
                        // 失败原因；「用浏览器自己下载」按钮不动。
                        TextButton(
                            onClick = {
                                view.tickHaptic()
                                onCardClick?.invoke()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = UPDATE_RETRY_LABEL,
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Accent
                            )
                        }
                        val apkUrl = availableInfo?.apkUrl
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
                                        IslandToasts.show("浏览器打不开，换个方式试试吧")
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "用浏览器自己下载",
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Accent
                                )
                            }
                        }
                    }
                    availableInfo != null -> Text(
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
                // 「去更新」按钮（第四十五批，设计 §9）：查到新版时
                // 行内直接给一个按钮（不只靠整行点按），点它直接
                // 启动后台下载（同一个 OtaDownloader.start，与
                // 升级卡里的「去更新」是同一个动作），不弹对话框。
                // 只在有新版、且不在检查／下载／下完／失败态时出现。
                if (availableInfo != null && !checking &&
                    !downloading && !dlDone && !dlFailed
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            view.tickHaptic()
                            OtaDownloader.start(context, availableInfo)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "去更新",
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Accent
                        )
                    }
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
        // 下行：测试计划行——标题 + 动态副文 + 开关（2026-11-14 Bob 定：
        // 副文随开关状态变，告诉用户升级会升到什么版本，与上行
        // 「当前版本 vX」同一口径）。点按开关只走测试计划，
        // 不触发上行更新；下载中开关照常用。
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
                    text = betaHint,
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

/**
 * 智能区内容（不含卡片壳）：来源行 + 分隔线 + 大模型行 + 短信显著披露弹窗。
 * 逻辑与文案原样保留（2026-11-23 v3-B：只换壳，行不动）。
 */
@Composable
private fun SmartSectionBody(
    hideOnBlur: Modifier,
    fieldFocusMods: (Int) -> Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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

    fun persist() {
        LlmConfig.save(context, apiKey, baseUrl, model)
        keyStatus = LlmConfig.keyStorageStatus(context)
    }

    // 收起「让助手更聪明」时输入区连同焦点一起卸载：主动清焦 + 收键盘，
    // 不把键盘留在已消失的输入框上（F-08：打开子面板/收起时的焦点处理）
    LaunchedEffect(llmExpanded) {
        if (!llmExpanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
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
    // chevron；没接满时保持展开，方便接上剩下的。收起/展开记下来，下次进
    // 设置页还按上次的来；4/4 首次接满（从未点过）默认收起。
    val connectedCount = connectedSourceCount(connectorStates)
    val allSourcesConnected = connectedCount == SMART_SOURCE_ORDER.size
    // 合并卡两行可见态走纯函数状态机（smartSectionRowsVisible）：
    // 来源行收起态与大模型行展开态各算各的，互不干扰。
    val rowsVisible = smartSectionRowsVisible(
        allSourcesConnected, sourcesCollapsedOverride, llmExpanded
    )
    val sourcesCollapsed = rowsVisible.sourcesCollapsed
    Column {
        // 上行：来源——内容、文案、持久化、纯函数、没接满不可点
        // 全部原样保留，只是从独立卡片收进这张卡的上行；点标题行
        // 展开/收起只影响本行（2026-11-14 Bob 定，与版本区同款）。
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
            if (!sourcesCollapsed) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { connectedCount / SMART_SOURCE_ORDER.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "数据只在这台手机上处理，不会发到别处。",
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
        // 两行之间的细分隔线（与版本区同款：HorizontalDivider +
        // 横向 16.dp + 主题 Divider 色）
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = AILauncherColors.current.Divider
        )
        // 下行：大模型——内容、文案、纯函数、清焦清键盘行为全部原样
        // 保留，只是从独立卡片收进这张卡的下行；点标题行展开/收起
        // 只影响本行（2026-11-14 Bob 定，与版本区同款；大模型卡
        // 原 2026-11-14 00:11 从助手分区整块挪到智能分区）。
        Column(modifier = Modifier.padding(16.dp)) {
            // F-08：展开/收起只认标题行——此前整卡 clickable，编辑中点到
            // 卡片空白区就把输入区连同焦点一起卸载，打断编辑上下文。
            // F-10：点按补 tick（走 Haptics 两档门面）。
            // 收起行与来源卡同一口径：标题行 + chevron
            // （llmCardTitle），完整说明文字只在展开块里。
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
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; persist() },
                    label = { Text("大模型密钥") },
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
                    label = { Text("服务器地址") },
                    placeholder = { Text(LlmConfig.DEFAULT_BASE_URL, color = AILauncherColors.current.Hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().then(hideOnBlur).then(fieldFocusMods(1))
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
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (testing) return@Button
                        // F-10：主按钮 = confirm 档
                        view.confirmHaptic()
                        persist()
                        if (apiKey.isBlank()) {
                            IslandToasts.show("先把大模型密钥填上吧")
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
                            IslandToasts.show(err ?: "连上了，没问题")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.current.Accent)
                ) {
                    Text(if (testing) "正在试，稍等一下" else "试试能不能连上")
                }
                // v0.68.100（卫生批）：Key 存放形态明示——加密时低调一行，
                // 明文降级时醒目提示 + 给得出下一步，不再静默回退
                // 加密态不说话（正常态无消息就是好消息）；只在明文降级
                // 时醒目提示 + 给得出下一步（文案审计第 1 批）。
                if (keyStatus.mode == LlmKeyStore.StorageMode.PLAIN_FALLBACK) Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "出了点问题，你的密钥现在没加上保护就存着了",
                        fontSize = AILauncherType.Footnote.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "重启手机后再回到这里，我会再试一次给它加上保护；" +
                            "还不行的话，就删掉重新粘贴保存一次。",
                        fontSize = AILauncherType.Caption.fontSize,
                        color = AILauncherColors.current.Hint,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

        }
    }
    if (disclosure == "sms") {
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = {
                Text(
                    text = "让我读读你的短信",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "我要读短信里的账单和体检预约提醒。只看最近 7 天的短信，只找账单和预约这两类，看完不留原文，也不会发到别处。",
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

/** 默认桌面行（不含卡片壳）：状态＋去设置。 */
@Composable
private fun DefaultDesktopSectionBody() {
    val context = LocalContext.current
    val defaultGranted = rememberPermissionState { isDefaultLauncher(context) }
    Column(modifier = Modifier.padding(16.dp)) {
        SettingsPermissionRow(
            title = "默认桌面",
            granted = defaultGranted.value,
            doneText = "已经设为默认",
            todoText = "还没设为默认",
            actionText = "去设置",
            onOpen = { openHomeSettings(context) }
        )
    }
}

/** 联系邮箱（2026-11-23 Bob 定：关于区“联系我”行的收件地址）。 */
const val CONTACT_EMAIL = "0xbobot@gmail.com"

/**
 * 关于区内容（不含卡片壳）：分享给朋友 + 联系我（2026-11-23 Bob 定）。
 * 未读打开记录与崩溃记录已删（Bob：中间产物）。行只有标题＋右箭头。
 */
@Composable
private fun AboutSectionBody() {
    val context = LocalContext.current
    val view = LocalView.current
    AboutRow(
        title = "分享给朋友",
        onClick = {
            view.tickHaptic()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AI 桌面")
                putExtra(Intent.EXTRA_TEXT, shareFriendsText())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(send, "分享给朋友"))
            }.onFailure { IslandToasts.show("没有能分享的应用，换个方式试试吧") }
        }
    )
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = AILauncherColors.current.Divider
    )
    AboutRow(
        title = "联系我",
        onClick = {
            view.tickHaptic()
            val mail = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:$CONTACT_EMAIL")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(mail) }
                .onFailure { IslandToasts.show("没有能发邮件的应用，换个方式试试吧") }
        }
    )
}

/** 关于区一行：标题＋右箭头（与分享给朋友行同一口径）。 */
@Composable
private fun AboutRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = AILauncherType.BodyLarge.fontSize,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.current.Title,
                lineHeight = 22.sp
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


/**
 * 设置 → 智能区的一行（447；2026-11-08 Bob 真机 verdict 后改：每行只有一个控件）。
 * 标题＋它提供什么＋开关＋状态行。开关只显示接没接上（第十三批：接上＝权限给了，
 * 开关不再是一道关；不许出现“开关开着、状态写着还没接上”）；状态文字只做展示、
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
                // desc 只在未接上时显示：已接上时开关开态＋绿色状态行已足够
                // 表达“已接上”，不再重复读一遍接上有何用（文案审计第 1 批）。
                if (!connected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.desc,
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Hint,
                        lineHeight = 18.sp
                    )
                }
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

/** 组内小标签（版本 / 默认桌面 / 关于）：v3-B 稿同一口径（13sp 半粗、标题色）。 */
@Composable
private fun SettingsGroupSubLabel(text: String) {
    Text(
        text = text,
        fontSize = AILauncherType.Footnote.fontSize,
        fontWeight = FontWeight.SemiBold,
        color = AILauncherColors.current.Title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
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
