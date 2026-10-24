@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
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
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.ui.pet.TodayEventsHolder
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
    onFocusChanged: (Boolean) -> Unit = {},
    // v0.68.99（主页重构 R-1）：挂载即聚焦——首页输入态由「点七仔格/讲述者」
    // 进入，用户点一次就应能打字（键盘随聚焦弹起）；应用中心不传，保持点按
    // 聚焦的原行为；「问七仔」自动带起的思考/回复呈现也不传（不弹键盘）。
    requestFocusOnMount: Boolean = false,
    // F 批：实心背景——首页胶囊坐在一体化面板里、七仔下半身垫在胶囊之后，
    // 0.88 半透明底会让七仔透影；首页传 true 用不透明 Card 色（应用中心不传，
    // 保持原半透明观感）。
    solidBackground: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    // v0.68.102（310 冻结修复二轮）：key 改为 requestFocusOnMount 本身——
    // 宿主（HomeScreen）的进态错峰门在进态合成落定后才把 true 送进来，
    // 本 Effect 须在 prop 翻 true 时才发聚焦请求（原 Unit key 只读首值）。
    // F 批注：sharedFocusRequester 随槽位面板退役（无调用方再传），
    // focusRequester 回到组件自持；错峰门 keying 原样保留。
    LaunchedEffect(requestFocusOnMount) {
        if (requestFocusOnMount) focusRequester.requestFocus()
    }
    // composing 中（拼音未上屏）禁止提交：组合区间的拉丁字母不是用户最终输入
    val composing = value.composition != null
    val canSubmit = value.text.isNotBlank() && !planning && !composing
    Row(
        modifier = modifier
            // F 批：几何网锚点（整胶囊 bounds——layout_check 的坐沿/包含
            // 断言以它为准，不拿 intent_input 的文本区凑数）
            .testTag("intent_capsule")
            // R3-A：最小高度策略——fontScale 放大时允许胶囊长高，不定死 56dp 裁字；
            // 内容最高项是提交钮触控外框 48dp（R3-C），< 56dp 下限，外观仍是原规格 56dp
            .heightIn(min = 56.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (solidBackground) AILauncherColors.current.Card
                else AILauncherColors.current.Card.copy(alpha = 0.88f)
            )
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            // 体检批D ⑨：装饰图标（输入框用途由占位文案表达）——明确标装饰语义，
            // 不进无障碍树，避免 TalkBack 在输入框旁多读一个无名节点
            contentDescription = null,
            tint = AILauncherColors.current.Accent,
            modifier = Modifier
                .size(18.dp)
                .semantics { invisibleToUser() }
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                // 体检批D ⑥：冒烟语义定位锚点（testTag 经根容器 testTagsAsResourceId
                // 暴露为 uiautomator resource-id；对用户界面零影响）
                .testTag("intent_input")
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
                color = AILauncherColors.current.Title
            ),
            cursorBrush = SolidColor(AILauncherColors.current.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = AILauncherColors.current.Hint,
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
                    // 体检批D ⑥：冒烟语义定位锚点（清空钮）
                    .testTag("intent_clear")
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
                        tint = AILauncherColors.current.Hint,
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
                // 体检批D ⑥：冒烟语义定位锚点（提交钮）
                .testTag("intent_submit")
                .clickable(enabled = canSubmit) { onSubmit() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSubmit || planning) AILauncherColors.current.Accent
                        else AILauncherColors.current.Accent.copy(alpha = 0.35f)
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
/**
 * v0.68.93（布局与交互 Loop 一批①·Bob 真机点名的 bug）：点胶囊外释放焦点。
 *
 * 根因：Compose 没有「点外部自动清焦」——[IntentEntry] 的 releaseInputFocus 只在
 * 提交/点结果/回复呈现时从内部调，点首页空白、七仔、Dock 时没有任何一层清焦，
 * 键盘收不掉，用户被困在编辑态（系统 Back 之外无路可退）。
 *
 * 宿主把这个观察者挂在根容器上：它在 Initial pass 只观察按下位置、从不消费
 * 事件，既有手势（拖拽/扇形/点按）零影响；胶囊聚焦期间，按下点落在胶囊范围
 * 外才 clearFocus + 收键盘，回到非编辑呈现。已输入文本一个字不动（保留口径
 * 沿旧：文本留在框里，dismiss 时协调器带回）。
 *
 * [targetBoundsInHost] 须返回胶囊在本宿主节点本地坐标系中的范围（宿主用
 * boundsInRoot 减自身 positionInRoot 换算）。
 */
fun Modifier.clearIntentFocusOnOutsideTap(
    focused: () -> Boolean,
    targetBoundsInHost: () -> Rect?,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    // v0.68.101（D-2）：真实外点事件回调——宿主据此退出输入态。退出判定
    // 从此走「用户动作事件」而非「焦点变 false 的边沿推断」：IME 切面板/
    // 删字造成的瞬时焦点抖动不再能把输入态误退（Bob 真机两幕实证）。
    onOutsideTap: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press) continue
            if (!focused()) continue
            val bounds = targetBoundsInHost() ?: continue
            val pos = event.changes.firstOrNull()?.position ?: continue
            if (!bounds.contains(pos)) {
                focusManager.clearFocus()
                keyboardController?.hide()
                onOutsideTap()
            }
        }
    }
}

@Composable
fun IntentEntry(
    state: IntentUiState,
    onSubmit: (String) -> Unit,
    onConfirm: (ActionRequest) -> Unit,
    onDismiss: () -> Unit,
    // v0.68.98（F-06）：Planning 态取消——宿主 cancel 在途 Job + 协调器作废结果，
    // 与 onDismiss（关反馈条）语义不同，故单列
    onCancelPlanning: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenPermissionSettings: (String) -> Unit,
    // v0.68.93：焦点变化外报（宿主据此挂「点外部释放焦点」观察者，见 clearIntentFocusOnOutsideTap）
    onFocusChanged: (Boolean) -> Unit = {},
    // v0.68.99（主页重构 R-1）：输入文本状态上提到宿主（HomeScreen）持有——
    // 静态主页不再常驻胶囊，IntentEntry 随输入态挂载/卸载；文本放宿主，
    // 收起再进入不吞字（沿 .93「已输入文本一个字不动」口径）。composing
    // 区间（TextFieldValue）语义不变，下游沿用。
    fieldValue: TextFieldValue,
    onFieldValueChange: (TextFieldValue) -> Unit,
    // 进入输入态时是否挂载即聚焦（点坐姿七仔 = 要打字，true；
    // 协调器在场状态自动带起的呈现 = false，不弹键盘）
    requestFocusOnMount: Boolean = false,
    // v0.68.101（D-2）：显式退出输入态请求——点本地结果/建议应用打开 App
    // 是「事办完了」的明确动作，由宿主收起输入态（文本保留）。原先靠
    // 「失焦→退出」的边沿推断，IME 抖动会误伤，现已拆除（见 HomeScreen）。
    onRequestExitInput: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val text = fieldValue.text
    val composing = fieldValue.composition != null
    // 已提交的文本：与当前文本相同期间不再出本地结果（提交后清掉本地层）
    var submittedText by remember { mutableStateOf<String?>(null) }
    val planning = state is IntentUiState.Planning
    // R3-C：胶囊焦点（驱动七仔倾听姿态）
    var capsuleFocused by remember { mutableStateOf(false) }

    // F 批（槽位 UX 退役）：R-2 的模板/槽位状态（templateFill /
    // activeSlotParam / panelSeed）已全删——发送一律走自由文本，缺参由
    // 协调器 NeedsInput 追问兜底。会话焦点即胶囊焦点本身：宿主的失焦
    // 退出判定（.99）与点外收焦（.93）以它为准。
    val sessionFocused = capsuleFocused
    LaunchedEffect(sessionFocused) { onFocusChanged(sessionFocused) }

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
        // F 批：发送只有自由文本一条路（R-2 的模板拼句/未填拦截已退役，
        // 缺参由协调器 NeedsInput 追问兜底）。
        // 组合中不提交：拼音未上屏的文本不是用户的最终输入（金色钮与 IME Done 同守卫，
        // 胶囊组件内的 canSubmit 也已挡住，这里是宿主侧的第二道）
        if (text.isNotBlank() && !planning && !composing) {
            submittedText = text
            onSubmit(text)
            releaseInputFocus()
        }
    }

    // 零打字数据（意图改进一批，R-2 沿用）：甲板常用 Top 应用
    // （AppUsageTracker.topApps：频次+当前时段亲和）+ 下一会议（经
    // TodayEventsHolder 从 PetZone 共享）——会议只供甲板示例派生，
    // 甲板本身不出会议行（简报会行是会议的唯一呈现，.93 去重契约
    // 由此天然满足，不再需要 eventId 比对）。
    val nextEvent by TodayEventsHolder.nextEvent.collectAsState()
    var suggestionApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(sessionFocused, installedEpoch) {
        if (sessionFocused) {
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
    // 布局防线（Bob 2026-10-21，R-2 沿用并扩展到甲板；F 批口径不变）：
    // 内容先降级自身，绝不挤压胶囊/TODAY/Dock 等其他槽位——示例行是
    // 第一牺牲位（字体放大 >1.15 时收起，沿旧建议面口径）；应用行在
    // 最紧配置（字体 1.3 + 矮屏）减到 2 个。短高形态（横屏）不另收示例
    // （示例行横向滚动自适应）；高度靠流式列自然裁剪末位（会行在列尾
    // 先让位），甲板/胶囊居列首不被裁，几何网在 landphone 腿对输入态
    // 实测兜底（v0.68.101 矩阵首跑定案）。
    val densityNow = LocalDensity.current
    val configurationNow = LocalConfiguration.current
    val tightLayout = densityNow.fontScale >= 1.3f &&
        configurationNow.screenHeightDp <= 800
    val showDeckExamples = densityNow.fontScale <= 1.15f
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
        // R-2：倾听姿态跟会话焦点走（面板里选值/填字时七仔同样在听）
        sessionFocused -> PetMood.FETCH
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
            onFieldValueChange(TextFieldValue(""))
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

    // ---- 甲板示例来源（F 批：模板只剩「示例整句」的派生职责） ----
    // 模板来源：能力注册表的参数 schema（daohang/dache 的 to 槽位）+
    // 下一场会派生（提醒模板）；注册表加载幂等，remember 一次即可。
    val slotCaps = remember {
        CapabilityRegistry.load(context)
        CapabilityRegistry.find("daohang") to CapabilityRegistry.find("dache")
    }
    val deckExamples = remember(slotCaps, nextEvent) {
        SlotTemplates.buildExamples(
            slotCaps.first, slotCaps.second,
            nextEvent?.takeIf { it.end > System.currentTimeMillis() },
            System.currentTimeMillis()
        )
    }

    // F 批·点选填充：点示例 = 把模板整句（exampleFillText：有默认值代入
    // 默认值、无默认值的槽位以 label 原文呈现）写入输入框，光标落文末、
    // 焦点留在胶囊可继续编辑；文本变非空后甲板按 showDeck 规则让位给
    // 本地结果（与现行衔接，不另设状态）。
    fun pickExample(t: SlotTemplate) {
        val sentence = t.exampleFillText()
        onFieldValueChange(
            TextFieldValue(sentence, TextRange(sentence.length))
        )
    }

    val idleOrEditing = state is IntentUiState.Idle || state is IntentUiState.Editing
    // 甲板可见：仅在聚焦 + 空文本时（打字后本地结果接管下方槽位，甲板
    // 让位）。状态须为编辑系——思考/回复/追问/权限态在场时甲板收起，
    // 不与结果槽位抢呈现。
    val showDeck = idleOrEditing && sessionFocused && text.isEmpty()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
      // ---- F 批·一体化面板：甲板 + 胶囊（含坐沿七仔）+ 结果槽位同处一面 ----
      // 单一 Card 背景 + 24dp 圆角 + 主题描边，内部三区不再各带卡片底。
      // 面板是静态容器：无任何尺寸/高度动画（310 冻结教训），进出仍由
      // HomeScreen 的 fade-only AnimatedVisibility 承担。面板只活在既有
      // 意图区内，不新开浮层、不另起列。
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intent_panel")
            .clip(RoundedCornerShape(24.dp))
            .background(AILauncherColors.current.Card)
            .border(1.dp, AILauncherColors.current.Divider, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // ---- 输入甲板：面板上区（应用推荐 + 示例素行） ----
        if (showDeck) {
            Column(
                modifier = Modifier.testTag("deck_root"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleSuggestionApps.isNotEmpty()) {
                    DeckAppsBlock(
                        apps = visibleSuggestionApps,
                        onOpenApp = { app ->
                            releaseInputFocus()
                            // v0.68.101（D-2）：甲板应用即原建议应用，同样显式
                            // 退出输入态（明确动作，不依赖失焦边沿推断）
                            onRequestExitInput()
                            launchLocalApp(context, app)
                        }
                    )
                }
                if (showDeckExamples) {
                    DeckExamplesRow(
                        examples = deckExamples,
                        onPick = { pickExample(it) }
                    )
                }
            }
        }

        // ---- 胶囊行：七仔坐在胶囊上沿（F 批定稿） ----
        // 几何：七仔总高 110dp，先画（z 在胶囊之后）；胶囊上方留 68dp
        // 头部空间，七仔下部 42dp（≈38%）被胶囊本体盖住 → 可见高 68dp
        // ≈ 胶囊高（56dp）的 1.2 倍，读出「坐在上沿」。水平中心 = 胶囊
        // 左缘 + 25% 胶囊宽。静态 Box 布局，无尺寸动画（310 教训）。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val petSize = 110.dp
            InputPetCompanion(
                size = petSize,
                modifier = Modifier.offset(
                    x = maxWidth * 0.25f - petSize / 2,
                    y = 0.dp
                )
            )
            Column {
                Spacer(modifier = Modifier.height(68.dp))
                // 胶囊本体（与应用中心同一组件；面板语境用实心底，
                // 防七仔下半身透影）
                IntentCapsuleField(
                    value = fieldValue,
                    onValueChange = { nv -> onFieldValueChange(nv) },
                    onSubmit = { submitIfReady() },
                    planning = planning,
                    requestFocusOnMount = requestFocusOnMount,
                    onFocusChanged = { capsuleFocused = it },
                    solidBackground = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---- 结果条槽位（非模态）：本地结果 / 回复 / 错误 / 权限说明共用 ----
        // 各 Strip 在面板内已去自身卡片底（透明，保留内边距与排版）。
        // 优先序：权限说明（需用户处理）> 本地结果（用户已改文本继续打字）> 回复/错误（对应已提交文本）
        Column(modifier = Modifier.testTag("intent_result_slot")) {
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
            // 思考条（v0.68.98·F-06）：Planning 态此前在槽位里什么都不渲染，
            // 用户只能干等（最长 15 秒超时）且无出口；现在出「想想中 + 取消」——
            // 取消走 onCancelPlanning 真作废在途规划（非模态，与其他条同一槽位）
            is IntentUiState.Planning -> {
                PlanningStrip(onCancel = onCancelPlanning)
            }
            else -> {
                if (localResults.isNotEmpty() &&
                    state !is IntentUiState.NeedsConfirmation
                ) {
                    LocalResultsBar(
                        apps = localResults,
                        onOpen = { app ->
                            // 点开即收键盘（意图改进一批）：应用要打开了，先释放输入
                            // v0.68.101（D-2）：同时显式退出输入态（明确动作，不再
                            // 依赖失焦边沿推断）
                            releaseInputFocus()
                            onRequestExitInput()
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
                        // 零打字呈现已迁到胶囊上方甲板（R-2）：本槽位在
                        // Idle/Editing 且无本地结果时保持空——建议面
                        // （SuggestionsBar）就此退役，其封面职责到此为止；
                        // 会议行随之消失，简报会行是会议的唯一呈现
                        // （.93 去重契约由「甲板不出会议行」天然满足）。
                    }
                }
            }
        }
        } // 结果槽位 Column
      } // 一体化面板 Column
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
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = state.message,
                    fontSize = 14.sp,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(state.request) }) {
                    Text(
                        text = "执行",
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消", color = AILauncherColors.current.Hint)
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
            // F 批：面板内透明——面板已提供统一背景，本条不再自带卡片底
            .requiredHeightIn(min = minBarHeight, max = RESULT_BAR_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            // R3-C 无障碍：结果一出现 TalkBack 礼貌播报（标题+应用名），不用手动找焦点
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "七仔找到了这些，点一下打开",
            fontSize = 12.sp,
            color = AILauncherColors.current.Hint,
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

/** 结果条与甲板共用的应用项：图标 + 名称，点选才打开（触控区 64dp 宽、约 60dp 高 ≥48dp）。 */
@Composable
fun ResultAppItem(
    app: AppInfo,
    onOpen: (AppInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            // 体检批D ⑥：冒烟语义定位锚点（结果条/建议面应用项，文档序即布局序）
            .testTag("result_item")
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
            color = AILauncherColors.current.Body,
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
            // F 批：面板内透明——面板已提供统一背景，本条不再自带卡片底
            // R3-C 无障碍：回复/错误出现时 TalkBack 礼貌播报条内文案
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = AILauncherColors.current.Body,
            // 短时小条：最多 3 行，超长截断，保证结果条高度有上限（见 RESULT_BAR_MAX_HEIGHT）
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = AILauncherColors.current.Accent,
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
                    tint = AILauncherColors.current.Hint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 七仔结果条·思考条（v0.68.98·F-06）：Planning 态的槽位呈现——「七仔想想中…」
 * + 「取消」文字钮。此前 Planning 在结果槽位落入 else 空渲染，用户面对最长
 * 15 秒的无反馈干等、还没有出口；取消走宿主真取消（Job cancel + 协调器代际
 * 作废，迟到结果不上屏）。与回复/错误小条同一槽位、同一 Card 语言，非模态。
 * 无障碍：liveRegion 礼貌播报「思考中」，取消钮是真 TextButton（触控 ≥48dp 由
 * Material 保证），另加 testTag 供冒烟定位。
 */
@Composable
private fun PlanningStrip(
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            // F 批：面板内透明——面板已提供统一背景，本条不再自带卡片底
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "七仔想想中…",
            fontSize = 13.sp,
            color = AILauncherColors.current.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag("planning_cancel")
        ) {
            Text(
                text = "取消",
                color = AILauncherColors.current.Accent,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
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
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            // F 批：面板内透明——面板已提供统一背景，本条不再自带卡片底
            // 问题出现时 TalkBack 礼貌播报，用户不用找焦点也知道被问了什么
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = question,
            fontSize = 13.sp,
            color = AILauncherColors.current.Title,
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
                    tint = AILauncherColors.current.Hint,
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
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            // F 批：面板内透明——面板已提供统一背景，本卡不再自带卡片底
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
            color = AILauncherColors.current.Title
        )
        Text(
            text = message,
            fontSize = 13.sp,
            color = AILauncherColors.current.Body
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = "暂不", color = AILauncherColors.current.Hint, fontSize = 13.sp)
            }
            TextButton(onClick = onGoSettings) {
                Text(
                    text = "去开启",
                    color = AILauncherColors.current.Accent,
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
