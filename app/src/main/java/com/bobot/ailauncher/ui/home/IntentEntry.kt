@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.ActionUsageTracker
import com.bobot.ailauncher.data.AppSearchIndex
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.PetMotion
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

// 428 滚动关键字：空闲时每条关键字在输入框内停留的时长（轮换节拍）。
private const val KEYWORD_ROTATE_INTERVAL_MS = 3000L

/**
 * 意图胶囊输入行（2026-10-21 新规格；意图清理后仅应用中心在用）。
 *
 * 应用中心搜索框：输入框 + 金色提交按钮，无麦克风。
 * 组件本身只管输入与提交；打字时宿主用同一文本过滤网格。
 * 提交（金色钮/IME Done）只收键盘，不再问大模型（Bob 2026-11-08 23:38
 * 清理令）；无匹配时的兜底（空态“有应用能打开，重试”→ 大模型再确认）
 * 由应用中心宿主另行触发，不在本组件内。
 *
 * 输入状态用 [TextFieldValue]（意图改进一批）：为了感知中文输入法的 composing
 * （拼音组合）区间——组合未上屏时金色钮与 IME Done 都不许提交半成品文本。
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
    // 应用中心变体不画提交钮（Bob 2026-11-03：箭头仅收键盘、无实际功能，
    // 随首页意图入口一并删；首页默认 true 不变）
    showSubmitButton: Boolean = true,
    // 应用中心变体（Bob 2026-11-03 原型样式，取代 409 融合）：Card 白 pill +
    // 8dp 阴影、无描边，框内放大镜；主题 Card 色随深浅档走；首页默认 false 不变
    fusedBackground: Boolean = false,
    // R3-C：焦点变化回调（首页用来驱动胶囊焦点状态；应用中心不传，保持纯过滤）
    onFocusChanged: (Boolean) -> Unit = {},
    // v0.68.99（主页重构 R-1）：挂载即聚焦——首页输入态由「点意图胶囊」
    // 进入，用户点一次就应能打字（键盘随聚焦弹起）；应用中心不传，保持点按
    // 聚焦的原行为；「问七仔」自动带起的思考/回复呈现也不传（不弹键盘）。
    requestFocusOnMount: Boolean = false,
    // F 批：面板语境背景——首页胶囊坐在一体化面板里，0.88 半透明底在面板上
    // 显脏；首页传 true（应用中心不传，保持原半透明观感）。
    // 2026-10-25 意图胶囊去深色（Bob 23:18 真机反馈）：true 原先用不透明
    // Card 色——深色模式下 Card 是近黑 0xFF2A2723，实拍（40,39,35）证实
    // 实心黑 pill 压在半透明玻璃卡片上打架；现改走卡片同一套玻璃
    // （STRONG + dockLight，与面板逐项对齐），与卡片同材质同色调。
    // 2026-10-27 设计精致化 P1-3：上段玻璃口径作废——面板已是强玻璃，胶囊
    // 再套强玻璃＋方向高光即玻璃套玻璃打架；现改平底（见 solidBackground 分支），
    // 靠明度差＋光标表达可输入。
    solidBackground: Boolean = false,
    // 意图页优化（2026-10-30，Bob 已批 §二 A1）：提交钮的无障碍文案——
    // 首页默认“交给助手去办”（点它就是把这句话交给助手办，不是下一页、
    // 不是搜一下）；应用中心胶囊无提交钮，不传。
    submitContentDescription: String = "交给助手去办",
    // 428 滚动关键字：空输入显示关键字时，点提交箭头是否在填入后走 onSubmit。
    // 应用中心传 false——只填入、网格随之过滤（应用中心箭头是关键字的运行钮，
    // 其 onSubmit 只是收键盘，与“按关键字过滤应用”相悖，故不走 onSubmit）。
    keywordSubmitCallsOnSubmit: Boolean = true
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
    // 428 滚动关键字：空闲时输入框内轮换显示能力关键字（取代静态占位），
    // 点提交箭头填入并提交；只在空输入且未聚焦时转动，一聚焦／一打字就停。
    val context = LocalContext.current
    val installedEpoch by InstalledApps.epoch.collectAsState()
    // 关键字池：首页示例行同一池（SlotTemplates.buildExpandedExamples），
    // nextEvent 传 null（会议提醒是上下文派生，不进轮换）；按
    // filterExecutable 只留本机能做的能力，不许诺做不到的；顺序把 Bob 点的
    // 三条（导航／刷视频／聊天）放最前面，其余按池子原顺序。
    val staticKeywordPool = remember(installedEpoch) {
        CapabilityRegistry.load(context)
        val executableIds = CapabilityRegistry.executableCapabilities(context).map { it.id }.toSet()
        val pool = SlotTemplates.buildExpandedExamples(
            CapabilityRegistry.find("daohang"),
            CapabilityRegistry.find("dache"),
            null,
            System.currentTimeMillis()
        )
        val filtered = SlotTemplates.filterExecutable(pool, executableIds)
        val headIds = listOf("nav", "shipin", "liaotian")
        val head = headIds.mapNotNull { id -> filtered.firstOrNull { it.id == id } }
        head + filtered.filter { it.id !in headIds }
    }
    // 智能滚动关键字：用户当前时间最相关的个性动作（最多 3 条）在前、静态
    // 能力词在后；没历史／当前时间没匹配的动作时就是纯静态池（行为不变）。
    // 个性动作随记录与时间变——remember key 跟动作版本与当前星期×小时走，
    // 记一条／换一小时就重算；平时不重算，轮换节拍不受影响。个性动作只留
    // 本机仍能做的能力（与静态池同一过滤口径）；点按方式与 B 方案一致
    // （填入＋触发，箭头逻辑一字不动）。
    val actionVersion by ActionUsageTracker.version.collectAsState()
    val (currentDow, currentHour) = ActionUsageTracker.currentDowHour()
    val keywordPool = remember(installedEpoch, currentDow, currentHour, actionVersion) {
        val executableIds = CapabilityRegistry.executableCapabilities(context).map { it.id }.toSet()
        val personalized = ActionUsageTracker.topActions(context)
            .filter { it.capabilityId in executableIds }
            .map { action ->
                val template = staticKeywordPool.firstOrNull { it.capabilityId == action.capabilityId }
                val label = CapabilityRegistry.find(action.capabilityId)?.label ?: action.capabilityId
                SlotTemplates.fixedTemplate(
                    id = "action_${action.capabilityId}_${action.destination.orEmpty()}",
                    capabilityId = action.capabilityId,
                    sentence = SlotTemplates.actionPhrase(template, label, action.destination)
                )
            }
        SlotTemplates.mergeKeywordPools(personalized, staticKeywordPool)
    }
    var isCapsuleFocused by remember { mutableStateOf(false) }
    var keywordIndex by remember { mutableIntStateOf(0) }
    // 只在空闲时滚动：空输入且未聚焦（键盘没起来）；一聚焦／一打字就停。
    val rotating = value.text.isEmpty() && !isCapsuleFocused && keywordPool.isNotEmpty()
    val currentKeywordText: String? = if (rotating) {
        keywordPool.getOrNull(keywordIndex % keywordPool.size)?.exampleFillText()
    } else null
    val reduceMotion = rememberReduceMotion()
    // 轮换节拍：每条 KEYWORD_ROTATE_INTERVAL_MS 一换；rotating 翻 false 时
    // effect 随 key 取消，计时立即停，不轮询、不另起协程。
    LaunchedEffect(rotating, keywordPool) {
        if (!rotating) return@LaunchedEffect
        while (true) {
            delay(KEYWORD_ROTATE_INTERVAL_MS)
            if (keywordPool.isEmpty()) return@LaunchedEffect
            keywordIndex = (keywordIndex + 1) % keywordPool.size
        }
    }
    // 关键字提交：填入后须等状态回流（宿主 fieldValue 已是关键字）再走
    // onSubmit，否则宿主读到的还是旧空文本；LaunchedEffect(value.text)
    // 在回流后的重组跑这一步。填入被宿主丢弃时（现两宿主都直通状态，
    // 不会发生），pending 在下次文本变化时清掉，不误触发。
    var pendingKeywordSubmit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(value.text) {
        val pending = pendingKeywordSubmit
        if (pending != null) {
            pendingKeywordSubmit = null
            if (value.text == pending) onSubmit()
        }
    }
    // composing 中（拼音未上屏）禁止提交：组合区间的拉丁字母不是用户最终输入
    val composing = value.composition != null
    // 428：空输入显示关键字时箭头可用（点它填入关键字并提交）；有字时箭头是
    // 正常提交（现状不变）。关键字只在空输入未聚焦时出现，故此时无 composing。
    val canSubmit = !planning && !composing &&
        (value.text.isNotBlank() || currentKeywordText != null)
    Row(
        modifier = modifier
            // F 批：几何网锚点（整胶囊 bounds——layout_check 的坐沿/包含
            // 断言以它为准，不拿 intent_input 的文本区凑数）
            .testTag("intent_capsule")
            // R3-A：最小高度策略——fontScale 放大时允许胶囊长高，不定死裁字；
            // Bob 2026-11-03：胶囊高与设置控件同高——设置齿轮触控框 48dp
            // （圆盘视觉 40dp 居中其中），R3-C 提交/清空钮触控外框同为 48dp；
            // 下限取 48dp 时两处胶囊各态（空闲/打字/首页）皆稳定 48dp、
            // 不跳高；40dp 下限会使应用中心打字态跳回 48dp、首页恒 48dp，
            // 成两套高度，故不取圆盘视觉值。两处同标。
            .heightIn(min = 48.dp)
            // 2026-10-28 意图页设计评审第 1 轮 P2-1：面板语境（solidBackground）
            // 下删 8dp 阴影（玻璃面板内多一层脏边）；应用中心变体（原型样式）
            // 恢复 8dp 阴影（原型白 pill 同款），首页变体一字不动。
            .then(
                if (solidBackground) Modifier
                else Modifier.shadow(8.dp, RoundedCornerShape(28.dp), spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f))
            )
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (fusedBackground) {
                    // 应用中心变体（Bob 2026-11-03 原型样式，取代 409 融合）：
                    // Card 白 pill、无描边；主题 Card 色随深浅档走，深色下仍是
                    // pill 与底色的明度差；只改表面，交互不动
                    Modifier.background(
                        AILauncherColors.current.Card,
                        RoundedCornerShape(28.dp)
                    )
                } else if (solidBackground) {
                    // 首页变体（2026-10-27 设计精致化 P1-3：弱化玻璃套玻璃）：
                    // 面板已是强玻璃，胶囊不再套第二层强玻璃＋方向高光（两层打架），
                    // 改平底：仅明度差（比面板玻璃更实一档）＋金色光标＋占位文案
                    // 表达可输入；金色发送键仍是唯一强强调。应用中心变体不动。
                    // 平底不透明度 0.55：比示例／结果衬底 0.50 高一档（主输入
                    // 区仍是卡内最实的一层），又低于实心卡色，避开 2026-10-25
                    // 实心黑 pill 压玻璃的旧问题。
                    // 2026-10-28 意图页设计评审第 1 轮 P1-1：0.72→0.55（衬底
                    // 0.50 之上一档），胶囊正文 Title 在新平底上复测 7.54:1。
                    Modifier.background(
                        AILauncherColors.current.Card.copy(alpha = 0.55f),
                        RoundedCornerShape(28.dp)
                    )
                } else {
                    Modifier.background(AILauncherColors.current.Card.copy(alpha = 0.88f))
                }
            )
            .clickable { focusRequester.requestFocus() }
            // 去 AI 味（2026-10-31）：星标图标已删；start padding 18→46dp（原 18dp 内边＋18dp 图标＋10dp 间隙），
            // 胶囊文字左缘仍落在 386 轴线（内容左缘＋46dp）上，不左移。
            // 应用中心变体（Bob 2026-11-03 原型样式）：start 回 18dp，框内放大镜
            // 20dp＋间隙 10dp 补回 48dp，文字轴线一字不动。
            // 429（Bob 2026-11-05：箭头按钮胶囊内右对齐、工整点）——end padding
            // 随箭头有无走：箭头在时 0dp，箭头盘（40dp）在 48dp 触控框内居中、
            // 右缘距胶囊右缘 4dp，与上下 4dp 余量齐平，读成右对齐；箭头不在时
            // 回 16dp，文字右缘不贴胶囊边（应用中心打字态）。
            .padding(
                start = if (fusedBackground) 18.dp else 48.dp,
                end = if (showSubmitButton || currentKeywordText != null) 0.dp else 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用中心变体（Bob 2026-11-03 原型样式）：框内放大镜（装饰语义，
        // 不进无障碍树；输入框用途由占位文案表达）
        if (fusedBackground) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AILauncherColors.current.Hint,
                modifier = Modifier
                    .size(20.dp)
                    .semantics { invisibleToUser() }
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        // 占位文案色（静态占位与滚动关键字共用）：
        // 2026-10-25 胶囊去深色连带：玻璃胶囊是中间调（深色模式
        // 下深色 Hint 中灰约 0xFF6B6560 在其上对比度仅约 1.1:1、
        // 几乎看不见），占位改 Title 降 alpha——深浅两档在玻璃
        // 上都可读；应用中心变体（实心底）沿用 Hint，一字不动。
        // 2026-10-27 暗黑可读：深色 Title 只降到 0.85（0.6 在玻璃上发虚），浅色仍 0.6。
        // 2026-10-28 意图页设计评审第 2 轮 P2-2（与 P1-8 同根因）：
        // 浅色占位在蜜桃色底实测约 3.87:1＜4.5，浅色亦抬到 0.85
        // （与深色同值、仍弱于正文 Title 全 alpha），同底复测约 7.47:1。
        val placeholderColor = if (solidBackground) AILauncherColors.current.Title.copy(alpha = 0.85f)
            else AILauncherColors.current.Hint
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                // 体检批D ⑥：冒烟语义定位锚点（testTag 经根容器 testTagsAsResourceId
                // 暴露为 uiautomator resource-id；对用户界面零影响）
                .testTag("intent_input")
                .focusRequester(focusRequester)
                .onFocusChanged {
                    // 428：胶囊内部也记焦点——关键字只在未聚焦时轮换；宿主回调原样转发。
                    isCapsuleFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                // R3-C 无障碍：Planning 时给 TalkBack 一个状态说明（"思考中"），
                // 不必等结果条出现才知道提交已受理
                .semantics {
                    if (planning) stateDescription = "思考中"
                },
            enabled = !planning,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = AILauncherType.Body.fontSize,
                color = AILauncherColors.current.Title
            ),
            // 意图页优化（2026-10-30，Bob 已批 §二 A4）：光标不再用金色——
            // 整页金色只留给提交主操作（§四光标例外本次不批准）；光标改
            // Title 色（与输入文字同色，仍一眼可辨），深浅两档同款。
            // 本组件首页与应用中心共用，光标口径随之统一，不另分叉。
            cursorBrush = SolidColor(AILauncherColors.current.Title),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        if (currentKeywordText != null) {
                            // 428 滚动关键字：空闲时轮换显示能力关键字（取代静态占位）；
                            // 纵向滚动（当前条往上滑出、下一条从下滑入），减动态时直接贴合。
                            AnimatedContent(
                                targetState = currentKeywordText,
                                contentAlignment = Alignment.CenterStart,
                                transitionSpec = {
                                    if (reduceMotion) {
                                        fadeIn(animationSpec = tween(0)) togetherWith
                                            fadeOut(animationSpec = tween(0))
                                    } else {
                                        (slideInVertically(animationSpec = PetMotion.KeywordSlide) { it } +
                                            fadeIn(tween(PetMotion.FadeQuick, easing = MotionTokens.Easing))) togetherWith
                                        (slideOutVertically(animationSpec = PetMotion.KeywordSlide) { -it } +
                                            fadeOut(tween(PetMotion.FadeQuick, easing = MotionTokens.Easing)))
                                    }
                                },
                                label = "keywordRotate"
                            ) { keyword ->
                                Text(
                                    text = keyword,
                                    fontSize = AILauncherType.Body.fontSize,
                                    color = placeholderColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = placeholder,
                                fontSize = AILauncherType.Body.fontSize,
                                color = placeholderColor,
                                // R3-A：大字体下占位文案单行省略，不溢出胶囊
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
        }
        // 提交区：Planning 时换轻量进度（文字保留、不可重复提交）
        // R3-C 无障碍：触控目标 48dp（外框可点），金色视觉圆仍 40dp
        // 应用中心变体不画此钮（见 showSubmitButton）
        // 428 滚动关键字：应用中心 showSubmitButton=false 时，箭头只在空输入
        // 显示关键字时出现（关键字的运行钮）；一打字／一聚焦就藏回（408 决定
        // 不动：应用中心打字搜索不用箭头）。首页 showSubmitButton=true，箭头常显不变。
        if (showSubmitButton || currentKeywordText != null) Box(
            modifier = Modifier
                .size(48.dp)
                // 体检批D ⑥：冒烟语义定位锚点（提交钮）
                .testTag("intent_submit")
                .clickable(enabled = canSubmit) {
                    // 428：关键字在场时点箭头 = 填入当前关键字并提交（首页跑意图；
                    // 应用中心只填入、网格随之过滤，见 keywordSubmitCallsOnSubmit）。
                    // 点输入框本身只聚焦（Row 的 clickable），绝不填入、不触发。
                    val kw = currentKeywordText
                    if (kw != null && value.text.isEmpty()) {
                        onValueChange(TextFieldValue(kw, TextRange(kw.length)))
                        if (keywordSubmitCallsOnSubmit) pendingKeywordSubmit = kw
                    } else {
                        onSubmit()
                    }
                },
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
                        // 2026-10-28 意图页设计评审第 1 轮 P1-3：规划态进度圈
                        // 同步改深色（白圈落在金盘上同样偏低），与提交箭头同色。
                        color = AILauncherColors.Light.Title,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = submitContentDescription,
                        // 2026-10-28 意图页设计评审第 1 轮 P1-3：白箭头 on 金盘
                        // 仅 2.42:1，改主题 onPrimary 深色字 #1F1D1A（同金盘
                        // 6.94:1；Light.Title 即该字面值，与深浅主题无关，
                        // 金盘深浅两档同色故不跟 current 走）。
                        tint = AILauncherColors.Light.Title,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * v0.68.93（布局与交互 Loop 一批①·Bob 真机点名的 bug）：点胶囊外释放焦点。
 *
 * 根因：Compose 没有「点外部自动清焦」——胶囊聚焦后，点框外空白、Dock 时
 * 没有任何一层清焦，键盘收不掉，用户被困在编辑态（系统 Back 之外无路可退）。
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
    onOutsideTap: () -> Unit = {},
    // 426（Bob 2026-11-04 点 X 回设置页）：附加排除范围——胶囊外但不算
    // “外面”的按钮位（应用中心 morph 清空钮在胶囊框外，点它不能算点外面
    // 清焦点，否则点 X 时焦点先丢、点击读到已失焦就走设置分支）。不传则
    // 为空，首页／宠物／设置调用点行为一字不动。
    extraBoundsInHost: () -> List<Rect> = { emptyList() }
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press) continue
            if (!focused()) continue
            val bounds = targetBoundsInHost() ?: continue
            val pos = event.changes.firstOrNull()?.position ?: continue
            if (!bounds.contains(pos) && extraBoundsInHost().none { it.contains(pos) }) {
                focusManager.clearFocus()
                keyboardController?.hide()
                onOutsideTap()
            }
        }
    }
}
