package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.intent.IntentCoordinator
import com.bobot.ailauncher.core.intent.IntentUiState
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppSearchIndex
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.PetMood
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.pet.CalEvent
import com.bobot.ailauncher.ui.pet.TodayEventsHolder
import com.bobot.ailauncher.ui.pet.joinCalendarEvent
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 结果条槽位高度上限：三种内容（本地结果/回复错误/权限卡）共用，超限不挤压 Dock。 */
private val RESULT_BAR_MAX_HEIGHT = 180.dp

/**
 * 意图胶囊输入行（2026-10-21 新规格：胶囊与应用中心搜索合二为一的同一组件）。
 *
 * 首页与应用中心共用这一行：✨ + 输入框 + 金色提交按钮，无麦克风。
 * 组件本身只管输入与提交；"打字时出什么"由宿主决定——
 * 首页在胶囊下方结果条出本地应用直达，应用中心用同一文本过滤网格。
 *
 * [showClearButton]（2026-10-21 Bob 批准）：仅应用中心变体开启——有文本时在提交钮左侧
 * 出 ✕ 一键清空（点后文本清空、网格恢复全量）；首页胶囊保持原设计不开，避免和提交钮抢位。
 *
 * 输入状态用 [TextFieldValue]（意图改进一批）：为了感知中文输入法的 composing
 * （拼音组合）区间——组合未上屏时金色钮与 IME Done 都不许提交半成品文本；
 * 宿主侧据此在组合期间暂停本地搜索更新并做防抖。首页与应用中心同享这套语义。
 */
@Composable
fun IntentCapsuleField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "想做什么？",
    planning: Boolean = false,
    showClearButton: Boolean = false,
    // R3-C：焦点变化回调（首页用来驱动七仔倾听姿态；应用中心不传，保持纯过滤）
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    // composing 中（拼音未上屏）禁止提交：组合区间的拉丁字母不是用户最终输入
    val composing = value.composition != null
    val canSubmit = value.text.isNotBlank() && !planning && !composing
    Row(
        modifier = modifier
            // R3-A：最小高度策略——fontScale 放大时允许胶囊长高，不定死 56dp 裁字；
            // 内容最高项是提交钮触控外框 48dp（R3-C），< 56dp 下限，外观仍是原规格 56dp
            .heightIn(min = 56.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = AILauncherColors.Title.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(28.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = AILauncherColors.Accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                // R3-C 无障碍：Planning 时给 TalkBack 一个状态说明（"七仔思考中"），
                // 不必等结果条出现才知道提交已受理
                .semantics {
                    if (planning) stateDescription = "七仔思考中"
                },
            enabled = !planning,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = AILauncherColors.Title
            ),
            cursorBrush = SolidColor(AILauncherColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = AILauncherColors.Hint,
                            // R3-A：大字体下占位文案单行省略，不溢出胶囊
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(10.dp))
        // ✕ 一键清空（仅应用中心变体开启）：有文本时才出现，清空走 onValueChange(空值)，
        // 宿主状态层（应用中心 query）随之回空、网格自动恢复，不另设回调。
        // R3-C 无障碍：触控目标 48dp（外框可点），视觉小圆仍 28dp，不改外观尺寸感。
        if (showClearButton && value.text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onValueChange(TextFieldValue("")) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = AILauncherColors.Hint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
        }
        // 提交区：Planning 时换轻量进度（文字保留、不可重复提交）
        // R3-C 无障碍：触控目标 48dp（外框可点），金色视觉圆仍 40dp
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(enabled = canSubmit) { onSubmit() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSubmit || planning) AILauncherColors.Accent
                        else AILauncherColors.Accent.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (planning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "提交",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * R2 UI（文档二 P0 + 2026-10-21 新规格）：首页意图入口——封面与 TODAY 紧凑条之间。
 *
 * 一个入口、两层响应：
 * - 本地直达层：打字时在胶囊下方「七仔结果条」实时出本地应用匹配
 *   （复用应用中心同一套 AppSearchIndex 匹配与同一应用数据源），只展示、点选才打开，
 *   绝不自动执行；提交后本地结果收起，用户再改文本才重新出。
 * - 意图层：提交（按钮/IME Done）走 [IntentCoordinator]，与本地层互不干扰。
 *
 * 呈现规则（Bob 拍板）：
 * - 只有 NeedsConfirmation（中/高风险动作确认，安全门）用 AlertDialog，全 App 意图链路仅此一种弹窗；
 *   其余状态（回复/错误/缺权限）一律非模态，与本地结果共用胶囊下方同一个结果条槽位。
 * - 胶囊右侧只有提交按钮，不做麦克风（语音胶囊已于 10-18 全部清除）。
 *
 * 状态由 [IntentCoordinator] 驱动，本组件只读渲染 + 转发用户动作，不碰 LLM/执行细节。
 */
@Composable
fun IntentEntry(
    state: IntentUiState,
    onSubmit: (String) -> Unit,
    onConfirm: (ActionRequest) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissionSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 输入状态（意图改进一批）：String → TextFieldValue，感知中文输入法 composing
    // 区间；text 是其已上屏文本的派生读法，下游沿用。
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val text = fieldValue.text
    val composing = fieldValue.composition != null
    // 已提交的文本：与当前文本相同期间不再出本地结果（提交后清掉本地层）
    var submittedText by remember { mutableStateOf<String?>(null) }
    val planning = state is IntentUiState.Planning
    // R3-C：胶囊焦点（驱动七仔倾听姿态）
    var capsuleFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // 提交即收键盘（意图改进一批）：提交/点开结果/回复错误呈现后统一走这里——
    // 清焦点 + 收 IME，用户不用再手动收键盘。
    fun releaseInputFocus() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // 本地搜索词（意图改进一批·防抖+组合暂停）：fieldValue 每次变化重启计时，
    // 组合中（拼音未上屏）直接返回不落定——组合区间的拉丁串不触发搜索重算，
    // 组合结束/停键 120ms 后才把文本落定为搜索词（与索引懒构建共用同一落定值）。
    var committedQuery by remember { mutableStateOf("") }
    LaunchedEffect(fieldValue) {
        if (fieldValue.composition != null) return@LaunchedEffect
        delay(120)
        committedQuery = fieldValue.text
    }

    // 本地直达层数据源：与应用中心 azApps 完全一致（listLaunchableApps 去自己、去隐藏），
    // 匹配复用 AppSearchIndex.build/search 同一实现，不另写一套。
    // R3-D：懒构建——原实现在首页首帧同步查 PackageManager + 加载拼音字典建索引，
    // 压在启动路径上；改为用户第一次真打字时才在 IO 线程建（建好前本地层暂不出
    // 结果，意图层提交不受影响），启动路径零索引成本。原始应用列表同时供建议面
    // 的「此刻常用」复用（launchableApps），避免重复查 PackageManager。
    var launchableApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var localIndex by remember { mutableStateOf<List<AppSearchIndex.Entry>?>(null) }
    // v0.68.88·保持更新：索引/应用列表按 InstalledApps.epoch 失效——此前懒建后
    // 永不刷新，新装的应用在首页永远搜不到（卸了的还留在结果里），必须等进程
    // 重启；现在装/卸一发生，epoch 变化，下一次查询即在 IO 线程按新清单重建。
    var indexEpoch by remember { mutableIntStateOf(-1) }
    val installedEpoch by InstalledApps.epoch.collectAsState()
    LaunchedEffect(Unit) { InstalledApps.ensureRegistered(context) }
    LaunchedEffect(committedQuery, installedEpoch) {
        if (committedQuery.isNotBlank() && (localIndex == null || indexEpoch != installedEpoch)) {
            val built = withContext(Dispatchers.IO) {
                val hidden = HiddenApps.getHidden(context)
                val apps = listLaunchableApps(context).filter {
                    it.packageName != context.packageName && it.packageName !in hidden
                }
                apps to AppSearchIndex.build(context, apps)
            }
            launchableApps = built.first
            localIndex = built.second
            indexEpoch = installedEpoch
        }
    }
    val localResults = remember(committedQuery, localIndex, submittedText) {
        val idx = localIndex
        if (committedQuery.isBlank() || committedQuery == submittedText || idx == null) emptyList()
        else AppSearchIndex.search(context, committedQuery, idx).take(4)
    }

    fun submitIfReady() {
        // 组合中不提交：拼音未上屏的文本不是用户的最终输入（金色钮与 IME Done 同守卫，
        // 胶囊组件内的 canSubmit 也已挡住，这里是宿主侧的第二道）
        if (text.isNotBlank() && !planning && !composing) {
            submittedText = text
            onSubmit(text)
            releaseInputFocus()
        }
    }

    // 零打字第一屏（意图改进一批）：胶囊聚焦且文本为空时，结果条槽位最低优先序
    // 出建议面——「此刻常用」Top 应用（AppUsageTracker.topApps：频次+当前时段亲和）
    // + 下一会议一行（若有）。会议数据经 TodayEventsHolder 从 PetZone 共享
    // （原本只活在 PetZone 的 Composable 状态里）；建议面只展示，点选才打开/入会。
    val nextEvent by TodayEventsHolder.nextEvent.collectAsState()
    var suggestionApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(capsuleFocused, installedEpoch) {
        if (capsuleFocused) {
            // 建议面的应用列表与索引同 epoch 口径：装/卸后别再端已卸载的应用
            val apps = if (launchableApps == null || indexEpoch != installedEpoch) {
                withContext(Dispatchers.IO) {
                    val hidden = HiddenApps.getHidden(context)
                    listLaunchableApps(context).filter {
                        it.packageName != context.packageName && it.packageName !in hidden
                    }
                }.also { launchableApps = it }
            } else {
                launchableApps!!
            }
            suggestionApps = withContext(Dispatchers.IO) {
                AppUsageTracker.topApps(context, apps, 4)
            }
        }
    }
    // 布局防线（Bob 2026-10-21）：最紧配置（字体 1.3 + 矮屏）下建议面先降级自身
    // ——砍示例句、Top 减到 2，绝不挤压胶囊/TODAY/Dock 等其他槽位；
    // 字体放大（>1.15）时先砍示例句（文字行变高，示例是第一牺牲位）。
    val densityNow = LocalDensity.current
    val configurationNow = LocalConfiguration.current
    val tightLayout = densityNow.fontScale >= 1.3f &&
        configurationNow.screenHeightDp <= 800
    val showSuggestionExample = densityNow.fontScale <= 1.15f
    val visibleSuggestionApps = if (tightLayout) suggestionApps.take(2) else suggestionApps

    // R3-C 七仔反应（对话感）：姿态只跟状态走，不说话、不弹气泡，克制优先——
    // Planning=思考（SORTING+头顶点点）> 本地结果/回复=呈现（HAPPY 原地放大弹跳"找到了"）
    // > 聚焦编辑=倾听（FETCH 上浮举手"说吧")；错误/权限不给姿态（出错不装开心，
    // 回落倾听或整理员原姿态）。呈现不用 FILE：FILE 自带 +104dp 右移（归档动线），
    // 在封面右侧位模板会把七仔推出屏幕（冒烟 intent-present 实拍翻车后改）。
    // 姿态经 PetRepository 覆盖层下发，IntentEntry 离场时清掉，不污染通知流程。
    val presenting = localResults.isNotEmpty() || state is IntentUiState.Reply
    val intentPose = when {
        planning -> PetMood.SORTING
        presenting -> PetMood.HAPPY
        // 追问态（意图改进二批）：七仔在等你补参数——倾听姿态，不装开心也不思考
        state is IntentUiState.NeedsInput -> PetMood.FETCH
        capsuleFocused -> PetMood.FETCH
        else -> null
    }
    LaunchedEffect(intentPose, planning) {
        PetRepository.setIntentReaction(intentPose, thinking = planning)
    }
    DisposableEffect(Unit) {
        onDispose { PetRepository.clearIntentReaction() }
    }

    // 回复小条短时呈现：5 秒后自动收起（协调器 dismiss 回编辑态，原话不丢）
    LaunchedEffect(state) {
        // 追问态（意图改进二批）：清空输入框等用户补参数——原话由协调器保存，
        // 关掉追问条 dismiss 时带回；文本清空后本地层也不会拿原话再出结果。
        if (state is IntentUiState.NeedsInput) {
            fieldValue = TextFieldValue("")
            submittedText = null
        }
        // 提交即收键盘（意图改进一批）：回复/错误一呈现就释放焦点、收 IME——
        // 结果已经端出来了，输入的使命完成，用户不用手动收键盘。
        if (state is IntentUiState.Reply || state is IntentUiState.Error) {
            releaseInputFocus()
        }
        if (state is IntentUiState.Reply) {
            delay(5_000)
            onDismiss()
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // ---- 胶囊本体（与应用中心同一组件） ----
        IntentCapsuleField(
            value = fieldValue,
            onValueChange = { fieldValue = it },
            onSubmit = { submitIfReady() },
            planning = planning,
            onFocusChanged = { capsuleFocused = it },
            modifier = Modifier.fillMaxWidth()
        )

        // ---- 结果条槽位（非模态）：本地结果 / 回复 / 错误 / 权限说明共用 ----
        // 优先序：权限说明（需用户处理）> 本地结果（用户已改文本继续打字）> 回复/错误（对应已提交文本）
        when (val s = state) {
            is IntentUiState.NeedsPermission -> {
                PermissionExplainCard(
                    message = s.message,
                    onGoSettings = {
                        onOpenPermissionSettings(s.permission)
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            }
            // 追问条（意图改进二批）：优先序仅次于权限说明——用户正被问着，
            // 本地结果/建议面都让位；常驻不自动收起，✕ = 放弃这单（协调器清 pending）
            is IntentUiState.NeedsInput -> {
                NeedsInputStrip(
                    question = s.question,
                    onClose = onDismiss
                )
            }
            else -> {
                if (localResults.isNotEmpty() && state !is IntentUiState.NeedsConfirmation) {
                    LocalResultsBar(
                        apps = localResults,
                        onOpen = { app ->
                            // 点开即收键盘（意图改进一批）：应用要打开了，先释放输入
                            releaseInputFocus()
                            launchLocalApp(context, app)
                        }
                    )
                } else when (s) {
                    is IntentUiState.Reply -> {
                        StatusStrip(
                            text = s.text,
                            onClose = onDismiss
                        )
                    }
                    is IntentUiState.Error -> {
                        val noKey = s.message == IntentCoordinator.NO_KEY_MESSAGE
                        StatusStrip(
                            text = s.message,
                            actionLabel = if (noKey) "去设置" else null,
                            onAction = if (noKey) onOpenSettings else null,
                            onClose = onDismiss
                        )
                    }
                    else -> {
                        // 建议面（意图改进一批）：槽位最低优先序——仅在聚焦 + 空文本
                        // + Idle/Editing 时出现；文本一旦非空，本地结果立即接管槽位。
                        // 会议只取尚未结束的（holder 里可能是刚过期的上一场）。
                        val upcomingEvent = nextEvent?.takeIf {
                            it.end > System.currentTimeMillis()
                        }
                        if (capsuleFocused && text.isEmpty() &&
                            (s is IntentUiState.Idle || s is IntentUiState.Editing) &&
                            (visibleSuggestionApps.isNotEmpty() || upcomingEvent != null)
                        ) {
                            SuggestionsBar(
                                apps = visibleSuggestionApps,
                                event = upcomingEvent,
                                showExample = showSuggestionExample,
                                onOpenApp = { app ->
                                    releaseInputFocus()
                                    launchLocalApp(context, app)
                                },
                                onOpenEvent = { e -> joinCalendarEvent(context, e) },
                                onExample = {
                                    // 示例句：点 = 填入并提交（走与手输提交同一路径）
                                    fieldValue = TextFieldValue(SUGGESTION_EXAMPLE)
                                    if (!planning) {
                                        submittedText = SUGGESTION_EXAMPLE
                                        onSubmit(SUGGESTION_EXAMPLE)
                                        releaseInputFocus()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 唯一弹窗：中/高风险动作确认（安全门，Bob 拍板归类） ----
    if (state is IntentUiState.NeedsConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "确认执行？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
            },
            text = {
                Text(
                    text = state.message,
                    fontSize = 14.sp,
                    color = AILauncherColors.Body
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(state.request) }) {
                    Text(
                        text = "执行",
                        color = AILauncherColors.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消", color = AILauncherColors.Hint)
                }
            }
        )
    }
}

/**
 * 七仔结果条·本地直达：打字时实时出的本地应用匹配（图标+名称，最多 4 个）。
 * 与回复/错误小条同一槽位、同一 Card 语言；文案以七仔口吻呈现——结果是七仔端出来的。
 * 只展示，点选才打开，绝不自动执行。
 *
 * 高度贴合内容（2026-10-21 Bob 批准）：文字关字体内边距（includeFontPadding=false）+
 * 显式行高 + 收紧内边距，消除单个结果时卡片下方的大片空白；整体上限 180dp
 * （[RESULT_BAR_MAX_HEIGHT]），超限条内滚动，不挤 Dock。
 *
 * F4 保底高度（R4 矩阵发现，Bob 定修法）：结果条是封面 Column 流的末位元素，
 * 矮屏+高模板时只分到剩余高度——MINIMAL + 1080×1920 + 字体 1.3 实测仅 78.5dp，
 * 单行结果被从中间切掉（结果项实测 42.3dp < 48dp、名称标签绘出条外）。
 * 下限把「标题行 + 图标 + 名称标签行」完整算入（构成见 [LocalResultsBar] 内注释），
 * 用 requiredHeightIn 顶住父级剩余高度的钳制（普通 heightIn(min) 会被钳回剩余值）；
 * 只加下限，上限与条内滚动不变。
 */
@Composable
private fun LocalResultsBar(
    apps: List<AppInfo>,
    onOpen: (AppInfo) -> Unit
) {
    // 保底高度构成：固定部分 69dp = 容器上下内边距 16 + 结果行上距 6
    // + 结果项（上下内边距 8 + 图标 36 + 标签上距 3）；文字部分 = 标题行高 15sp
    // + 标签行高 13sp（按 sp 换算，fontScale 多大都完整算入）；另加 4dp 余量
    // （R4 dump 实测 TextView 高度比 lineHeight 多约 2.6dp，字体内边距关不干净）。
    val density = LocalDensity.current
    val minBarHeight = 73.dp + with(density) { 15.sp.toDp() + 13.sp.toDp() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .requiredHeightIn(min = minBarHeight, max = RESULT_BAR_MAX_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            .verticalScroll(rememberScrollState())
            // R3-C 无障碍：结果一出现 TalkBack 礼貌播报（标题+应用名），不用手动找焦点
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "七仔找到了这些，点一下打开",
            fontSize = 12.sp,
            color = AILauncherColors.Hint,
            style = TextStyle(
                lineHeight = 15.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                ResultAppItem(app = app, onOpen = onOpen)
            }
        }
    }
}

/** 结果条与建议面共用的应用项：图标 + 名称，点选才打开（触控区 64dp 宽、约 60dp 高 ≥48dp）。 */
@Composable
private fun ResultAppItem(
    app: AppInfo,
    onOpen: (AppInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(app) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Text(
            text = app.label.toString(),
            fontSize = 11.sp,
            color = AILauncherColors.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                lineHeight = 13.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
        )
    }
}

/** 建议面唯一一条示例句（克制：只放一条，不做推广位）；点它 = 填入并提交。 */
private const val SUGGESTION_EXAMPLE = "打车去机场"

/**
 * 七仔结果条·零打字建议面（意图改进一批）：胶囊刚聚焦、一个字还没打时端出来的
 * 「此刻常用」——Top 应用（频次+时段亲和，与本地结果同一 [ResultAppItem]，点一下
 * 直达打开）+ 下一会议一行（若有，与封面「接下来」条同一文案语言，点按走
 * [joinCalendarEvent] 同一条入会路径）。只展示不执行，点选才发生动作。
 *
 * 布局防线（Bob 2026-10-21）：与本地结果共用同一槽位、同一 180dp 上限
 * （[RESULT_BAR_MAX_HEIGHT]），超限条内滚动，不新开浮层、不挤其他槽位；
 * 最紧配置的降级（Top 减量、砍示例句）在 IntentEntry 按配置算好传入；
 * 会议在场时不再叠示例句（内容优先序：会议 > 示例，示例只是发现引导）。
 * 触控目标：应用项与本地结果同构 ≥48dp，会议行/示例行高度下限 48dp。
 */
@Composable
private fun SuggestionsBar(
    apps: List<AppInfo>,
    event: CalEvent?,
    showExample: Boolean,
    onOpenApp: (AppInfo) -> Unit,
    onOpenEvent: (CalEvent) -> Unit,
    onExample: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            .verticalScroll(rememberScrollState())
            // 与本地结果同款：出现时 TalkBack 礼貌播报，不用手动找焦点
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (event != null) {
            // 会议行：与封面 NextUpStrip 同一文案语言（接下来 · 时间 / 标题 · 点一下加入）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenEvent(event) }
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "接下来 · ${formatEventTime(event.begin)}",
                    fontSize = 12.sp,
                    color = AILauncherColors.Accent,
                    style = TextStyle(
                        lineHeight = 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
                Text(
                    text = "${event.title.ifBlank { "日程" }} · 点一下加入",
                    fontSize = 13.sp,
                    color = AILauncherColors.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        lineHeight = 16.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (apps.isNotEmpty()) {
            Text(
                text = "七仔猜你现在想用",
                fontSize = 12.sp,
                color = AILauncherColors.Hint,
                style = TextStyle(
                    lineHeight = 15.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = if (event != null) Modifier.padding(top = 6.dp) else Modifier
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                apps.forEach { app ->
                    ResultAppItem(app = app, onOpen = onOpenApp)
                }
            }
        }
        if (showExample && event == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExample() },
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "试试说：「$SUGGESTION_EXAMPLE」",
                    fontSize = 12.sp,
                    color = AILauncherColors.Accent,
                    style = TextStyle(
                        lineHeight = 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/** 会议开始时间 HH:mm（与封面条/提醒文案同一格式口径）。 */
private fun formatEventTime(beginMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(beginMillis))

/** 本地直达打开应用：与应用中心 launchApp 同一做法（记使用统计 + 系统启动意图 + 失败 toast）。 */
private fun launchLocalApp(context: Context, app: AppInfo) {
    AppUsageTracker.recordLaunch(context, app.packageName)
    try {
        val intent = context.packageManager
            .getLaunchIntentForPackage(app.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) context.startActivity(intent)
        else IslandToasts.show("无法打开「${app.label}」")
    } catch (_: Exception) {
        IslandToasts.show("「${app.label}」暂不可用")
    }
}

/** 胶囊下方短时小条：回复/错误共用，可带一个动作（如"去设置"），可关。 */
@Composable
private fun StatusStrip(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            // R3-C 无障碍：回复/错误出现时 TalkBack 礼貌播报条内文案
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = AILauncherColors.Body,
            // 短时小条：最多 3 行，超长截断，保证结果条高度有上限（见 RESULT_BAR_MAX_HEIGHT）
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = AILauncherColors.Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
        // R3-C 无障碍：关闭钮触控目标 48dp（外框可点），视觉圆仍 32dp
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = AILauncherColors.Hint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 七仔结果条·缺参追问条（意图改进二批）：必填参数没凑齐时七仔在这里把问题问出来，
 * 常驻不自动收起——用户下一句提交即按参数补进 pending 动作（协调器状态契约）；
 * ✕ 关掉 = 放弃这一单（协调器 dismiss 清 pending 回编辑态）。
 * 与回复/错误小条同一槽位、同一 Card 语言；高度守 [RESULT_BAR_MAX_HEIGHT]，
 * 关闭钮触控 48dp（同 StatusStrip 的 R3-C 口径）。
 */
@Composable
private fun NeedsInputStrip(
    question: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.88f))
            // 问题出现时 TalkBack 礼貌播报，用户不用找焦点也知道被问了什么
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = question,
            fontSize = 13.sp,
            color = AILauncherColors.Title,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "取消这件事",
                    tint = AILauncherColors.Hint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 缺权限说明卡（非模态）：与 HomeScreen 无障碍引导卡同风格——先讲清用途，再给"去开启"。 */
@Composable
private fun PermissionExplainCard(
    message: String,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.Card.copy(alpha = 0.92f))
            .verticalScroll(rememberScrollState())
            // R3-C 无障碍：权限说明出现时 TalkBack 礼貌播报
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "需要开启一个权限",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AILauncherColors.Title
        )
        Text(
            text = message,
            fontSize = 13.sp,
            color = AILauncherColors.Body
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = "暂不", color = AILauncherColors.Hint, fontSize = 13.sp)
            }
            TextButton(onClick = onGoSettings) {
                Text(
                    text = "去开启",
                    color = AILauncherColors.Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * 按权限类型跳对应系统设置页（参考 ActionEngine 可能给出的权限标识做尽力映射，
 * 无法识别时落到本应用详情页，不静默失败）。当前 capabilities.json 尚无能力声明
 * permission，此路径未在真实触发下验证过。
 */
internal fun openPermissionSettings(context: Context, permission: String) {
    val p = permission.lowercase()
    val intent = when {
        p.contains("notification") -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        p.contains("accessibility") || p.contains("bind_accessibility") ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        p.contains("usage") -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        p.contains("overlay") || p.contains("alert_window") -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        else -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // 个别 ROM 没有对应设置页：落到应用详情，不让用户点空
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { }
    }
}
