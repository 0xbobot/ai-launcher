@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.invisibleToUser
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.DockLightParams
import com.bobot.ailauncher.ui.glass.FlareShaderParams
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.ui.pet.TodayEventsHolder
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 结果条槽位高度上限：三种内容（本地结果/回复错误/权限卡）共用，超限不挤压 Dock。 */
private val RESULT_BAR_MAX_HEIGHT = 180.dp


/**
 * 意图卡片轮廓（2026-11-02，无宠物清理）：顶部就是普通 28dp 圆角，
 * 不再有肩线拱；底部圆角外延（竖屏 24dp 高＋20dp 外扩、横屏无外延）保留。
 */



/**
 * 意图结果槽位是否有内容（2026-10-28 意图页设计评审第 2 轮，纯函数、单测锁定）：
 * 必须与 IntentEntry 内结果槽位 when() 的渲染条件逐项镜像——槽位渲染什么，
 * 本函数就返回 true；槽位空渲染（无文本、无结果、无状态条）就返回 false。
 * 调用方据此决定：true → 槽位＋并入的收起一并渲染；false → 两者都不渲染，
 * 面板只剩胶囊＋示例，高度严格跟内容走。改槽位条件时须同步改这里与单测。
 */
internal fun intentHasResultContent(
    state: IntentUiState,
    showDeckApps: Boolean,
    idleOrEditing: Boolean,
    committedLiteral: String,
    submittedText: String?,
    localIndexReady: Boolean,
    showSearching: Boolean,
): Boolean = when (state) {
    is IntentUiState.NeedsPermission -> true
    is IntentUiState.NeedsInput -> true
    is IntentUiState.Planning -> true
    // 意图页优化（2026-10-30，Bob 已批 §二 B2/B3）：理解预告与正在
    // 执行同样占结果槽位——判 false 会丢掉收起并入的退出入口。
    is IntentUiState.Understood -> true
    is IntentUiState.Executing -> true
    else -> if (showDeckApps && state !is IntentUiState.NeedsConfirmation) true
    else when (state) {
        is IntentUiState.Reply -> true
        is IntentUiState.Error -> true
        else -> idleOrEditing && committedLiteral.isNotBlank() &&
            committedLiteral != submittedText &&
            (localIndexReady || showSearching)
    }
}




/** 意图卡片底部圆角外延区高度（2026-10-26 Bob 点选圆角外延，设计附录）。 */
private val INTENT_FLARE_HEIGHT = 24.dp

/**
 * 意图卡片轮廓（输入态专注·圆角外延）：
 * 顶部 = 普通 28dp 圆角（2026-11-02 起不再有肩线拱）；
 * 底部 = [flareDp] 高的外延区内两侧贝塞尔外扩 [flareExtentDp] 到全宽；
 * flareDp = 0 且 flareExtentDp = 0 时底边走纯圆角（横屏本批），不留
 * 退化贝塞尔凸起；
 * 全程单一 contour：外延在同一 Path 里续画，不 addPath、不另起
 * contour。
 *
 * 局部坐标：原点在布局左上，y 向下；布局宽 = 卡片主体宽（竖屏 320dp、
 * 横屏封顶 520dp），主体两侧边在 x=0 / x=w；外延到底边时各向外扩
 * [flareExtentDp]（竖屏 20dp = 外边距，底边恰为屏宽；横屏同理外扩 20dp，
 * 底边 560dp 居中——横屏外扩到屏边须 140dp/side，曲线比例全毁，不取）。
 * 底边 y=h 全宽直线。
 *
 * 外延贝塞尔口径（设计附录，屏坐标左档 P0=(20,kb−24)→C1=(20,kb−10.8)→
 * C2=(11,kb)→P1=(0,kb)，右镜像；此处换算到布局坐标，主体左边缘为 x=0）：
 * 左（自下而上画）：P1=(-ext,h)→C1=(-ext+11,h)→C2=(0,h−10.8)→P0=(0,h−24)；
 * 右（自上而下画）：P0=(w,h−24)→C1=(w,h−10.8)→C2=(w+ext−11,h)→P1=(w+ext,h)。
 * 切线核对：外延上端控制点与主体侧边共线（竖直相切）、下端控制点与底边
 * 共线（水平相切），G1 连续无 kink；底角不再是 28dp 圆角——外延曲线本身
 * 就是底角处理（设计附录底边档口径；顶部两角仍走 28dp 圆角）。
 */
internal class IntentCardShape(
    private val cornerDp: Dp,
    private val flareDp: Dp,
    private val flareExtentDp: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val corner = with(density) { cornerDp.toPx() }
        val flare = with(density) { flareDp.toPx() }
        val ext = with(density) { flareExtentDp.toPx() }
        val c1Drop = with(density) { 10.8.dp.toPx() }
        val c2Run = with(density) { 11.dp.toPx() }
        val w = size.width
        val h = size.height
        val yFlare = h - flare
        return Outline.Generic(Path().apply {
            moveTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            lineTo(w - corner, 0f)
            quadraticTo(w, 0f, w, corner)
            if (flare > 0f || ext > 0f) {
                lineTo(w, yFlare)
                // 右侧外延：上端竖直相切、下端水平相切。
                cubicTo(w, h - c1Drop, w + ext - c2Run, h, w + ext, h)
                // 底边：全宽直线，贴键盘顶边（0dp 缝由宿主锚定保证）。
                lineTo(-ext, h)
                // 左侧外延（反向画）：下端水平相切、上端竖直相切。
                cubicTo(-ext + c2Run, h, 0f, h - c1Drop, 0f, yFlare)
            } else {
                // 纯圆角（横屏本批 flare=0）：底边两角走 28dp 圆角（与顶角
                // 同款），不留退化贝塞尔凸起（flare=0 时原贝塞尔会在底角
                // 留 11×10.8dp 小凸起，属外延残留，必须分支掉）。
                lineTo(w, h - corner)
                quadraticTo(w, h, w - corner, h)
                lineTo(corner, h)
                quadraticTo(0f, h, 0f, h - corner)
            }
            // close() 只回到起点 (0, corner)，即左侧边竖直线，无对角线。
            close()
        })
    }
}


/**
 * 意图胶囊输入行（2026-10-21 新规格：胶囊与应用中心搜索合二为一的同一组件）。
 *
 * 首页与应用中心共用这一行：输入框 + 金色提交按钮，无麦克风。
 * 组件本身只管输入与提交；"打字时出什么"由宿主决定——
 * 首页在胶囊下方结果条出本地应用直达，应用中心用同一文本过滤网格。
 *
 * [showClearButton]（2026-10-21 Bob 批准）：仅应用中心变体开启——有文本时在提交钮左侧
 * 出 ✕ 一键清空（点后文本清空、网格恢复全量）；首页胶囊保持原设计不开，避免和提交钮抢位。
 *
 * 输入状态用 [TextFieldValue]（意图改进一批）：为了感知中文输入法的 composing
 * （拼音组合）区间——组合未上屏时金色钮与 IME Done 都不许提交半成品文本。
 * 本地搜索的有效查询不在组件内派生，由宿主各自决定：首页逐键经
 * LocalSearchGate 派生双键（字面键 = 已上屏内容/无汉字时全文；拼音
 * 流键 = 已上屏汉字换算拼音与 composing 字母接成的连续流，见
 * LocalSearchGate），应用中心过滤沿用其组合暂停口径；两侧状态互不
 * 共用、互不影响。
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
    submitContentDescription: String = "交给助手去办"
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
            .padding(start = if (fusedBackground) 18.dp else 48.dp, end = 16.dp),
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
                        Text(
                            text = placeholder,
                            fontSize = AILauncherType.Body.fontSize,
                            // 2026-10-25 胶囊去深色连带：玻璃胶囊是中间调（深色模式
                            // 下深色 Hint 中灰约 0xFF6B6560 在其上对比度仅约 1.1:1、
                            // 几乎看不见），占位改 Title 降 alpha——深浅两档在玻璃
                            // 上都可读；应用中心变体（实心底）沿用 Hint，一字不动。
                            // 2026-10-27 暗黑可读：深色 Title 只降到 0.85（0.6 在玻璃上发虚），浅色仍 0.6。
                            // 2026-10-28 意图页设计评审第 2 轮 P2-2（与 P1-8 同根因）：
                            // 浅色占位在蜜桃色底实测约 3.87:1＜4.5，浅色亦抬到 0.85
                            // （与深色同值、仍弱于正文 Title 全 alpha），同底复测约 7.47:1。
                            color = if (solidBackground) AILauncherColors.current.Title.copy(alpha = 0.85f)
                            else AILauncherColors.current.Hint,
                            // R3-A：大字体下占位文案单行省略，不溢出胶囊
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
        if (showSubmitButton) Box(
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
 * R2 UI（文档二 P0 + 2026-10-21 新规格）：首页意图入口——封面与 TODAY 紧凑条之间。
 *
 * 一个入口、两层响应：
 * - 本地直达层：打字时在胶囊下方「本地结果条」实时出本地应用匹配
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
 * 提交/点结果/回复呈现时从内部调，点首页空白、Dock 时没有任何一层清焦，
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

@Composable
fun IntentEntry(
    state: IntentUiState,
    onSubmit: (String) -> Unit,
    onConfirm: (ActionRequest) -> Unit,
    // 意图页优化（2026-10-30，Bob 已批 §二 B2）：理解预告条上
    // “就这样办”的回调（协调器 confirmUnderstood）；与弹窗确认
    // （onConfirm）分开——两单各有一次性守卫，不共用。
    onConfirmUnderstood: (ActionRequest) -> Unit = {},
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
    // 进入输入态时是否挂载即聚焦（点意图胶囊 = 要打字，true；
    // 协调器在场状态自动带起的呈现 = false，不弹键盘）
    requestFocusOnMount: Boolean = false,
    // v0.68.101（D-2）：显式退出输入态请求——点本地结果/建议应用打开 App
    // 是「事办完了」的明确动作，由宿主收起输入态（文本保留）。原先靠
    // 「失焦→退出」的边沿推断，IME 抖动会误伤，现已拆除（见 HomeScreen）。
    onRequestExitInput: () -> Unit = {},
    onExitFocusRequest: () -> Unit = onRequestExitInput,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val text = fieldValue.text
    val composing = fieldValue.composition != null
    // 已提交的文本：与当前文本相同期间不再出本地结果（提交后清掉本地层）
    var submittedText by remember { mutableStateOf<String?>(null) }
    val planning = state is IntentUiState.Planning
    // R3-C：胶囊焦点（驱动胶囊焦点状态）
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

    // 本地搜索词（意图改进一批·防抖落定；并行线 D 拼音流口径）：落定值
    // 逐键经 LocalSearchGate 派生双键——字面键沿用派生版规则（无已上屏
    // 汉字时以全文、含 composing 中未上屏拼音字母为查询；有汉字上屏后
    // 只按已上屏内容查、还在敲的拼音不拼入字面键）；拼音流键把已上屏
    // 汉字逐字换算拼音、与 composing 字母接成一条流（已上屏「微」+ 正
    // 敲 xin → 流键 weixin，全程活搜不冻结）。停键 120ms 防抖不变（与
    // 索引懒构建共用同一落定值）。旧口径「组合中直接返回、暂停落定」
    // 废止。提交侧的 composing 守卫（submitIfReady / 胶囊组件
    // canSubmit）与应用中心过滤的组合暂停（其自有状态层）不在此列、
    // 保持原样。流键换算要读拼音字典（单例缓存、与索引构建共用），
    // 落定计算放 Default 线程，免首次解析压主线程。
    var committedKeys by remember { mutableStateOf(LocalSearchKeys("", "")) }
    LaunchedEffect(fieldValue) {
        delay(120)
        committedKeys = withContext(Dispatchers.Default) {
            localSearchKeys(fieldValue) { AppSearchIndex.pinyinStreamOf(context, it) }
        }
    }

    // 本地直达层数据源：与应用中心 azApps 完全一致（listLaunchableApps 去自己、去隐藏），
    // 匹配复用 AppSearchIndex.build/search 同一实现，不另写一套。
    // R3-D：懒构建——原实现在首页首帧同步查 PackageManager + 加载拼音字典建索引，
    // 压在启动路径上；改为用户第一次真打字时才在 IO 线程建（建好前本地层暂不出
    // 结果，意图层提交不受影响），启动路径零索引成本。2026-10-25 意图卡片统一：
    // 建议面应用复用（launchableApps）已随应用推荐行删除，索引 effect 只建索引。
    var localIndex by remember { mutableStateOf<List<AppSearchIndex.Entry>?>(null) }
    // v0.68.88·保持更新：索引/应用列表按 InstalledApps.epoch 失效——此前懒建后
    // 永不刷新，新装的应用在首页永远搜不到（卸了的还留在结果里），必须等进程
    // 重启；现在装/卸一发生，epoch 变化，下一次查询即在 IO 线程按新清单重建。
    var indexEpoch by remember { mutableIntStateOf(-1) }
    val installedEpoch by InstalledApps.epoch.collectAsState()
    LaunchedEffect(Unit) { InstalledApps.ensureRegistered(context) }
    LaunchedEffect(committedKeys.literal, installedEpoch) {
        if (committedKeys.literal.isNotBlank() && (localIndex == null || indexEpoch != installedEpoch)) {
            localIndex = withContext(Dispatchers.IO) {
                val hidden = HiddenApps.getHidden(context)
                val apps = listLaunchableApps(context).filter {
                    it.packageName != context.packageName && it.packageName !in hidden
                }
                AppSearchIndex.build(context, apps)
            }
            indexEpoch = installedEpoch
        }
    }
    val localResults = remember(committedKeys, localIndex, submittedText) {
        val idx = localIndex
        val keys = committedKeys
        if (keys.literal.isBlank() || keys.literal == submittedText || idx == null) emptyList()
        else {
            // 双键各搜一遍、字面命中在前合并（流键与字面键相等时只搜一遍，
            // 与派生版严格等价）；提交后抑制沿用字面键对 submittedText 的口径。
            val literalHits = AppSearchIndex.search(context, keys.literal, idx)
            val merged = if (keys.phonetic == keys.literal) literalHits
            else mergeLiteralFirst(
                literalHits,
                AppSearchIndex.search(context, keys.phonetic, idx),
            ) { it.packageName }
            merged.take(4)
        }
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

    // 零打字数据（意图改进一批，R-2 沿用；2026-10-25 意图卡片统一后甲板
    // 不再出应用行）：下一会议只供甲板示例派生（经 TodayEventsHolder 从
    // PetZone 共享）——会议只供示例派生，甲板本身不出会议行（简报会行是
    // 会议的唯一呈现，.93 去重契约由此天然满足，不再需要 eventId 比对）。
    // 应用推荐行已随本批删除：Dock 里已有应用推荐，意图初始状态再推一遍
    // 大多数情况是重复（Bob 2026-10-25 原话；2026-10-22「同一信息只在一
    // 处呈现」原则）。原建议管线（AppUsageTracker.topApps + suggestionApps
    // 状态 + focus 拉取 effect）随之整段删除，不留死代码。
    val nextEvent by TodayEventsHolder.nextEvent.collectAsState()
    // 布局防线（Bob 2026-10-21，R-2 沿用并扩展到甲板；F 批口径不变）：
    // 内容先降级自身，绝不挤压胶囊/TODAY/Dock 等其他槽位——示例行是
    // 第一牺牲位（字体放大 >1.15 时收起）；高度靠流式列自然裁剪末位
    // （会行在列尾先让位），甲板/胶囊居列首不被裁，几何网在 landphone
    // 腿对输入态实测兜底（v0.68.101 矩阵首跑定案）。应用行已删，最紧配
    // 置的「应用减到 2 个」降级随之退役。
    val densityNow = LocalDensity.current
    val showDeckExamples = densityNow.fontScale <= 1.15f

    val executing = state is IntentUiState.Executing

    // 回复小条短时呈现：5 秒后自动收起（协调器 dismiss 回编辑态，原话不丢）
    LaunchedEffect(state) {
        // 意图页优化（2026-10-30，Bob 已批 §二 B2“改一下”）：dismiss 回
        // Editing 时，输入框必须真回到原话——否则“改一下”只是口号：
        // 直接提交路径下框里本就是原话（守卫跳过）；追问路径下框里是
        // 最后补的参数（NeedsInput 入场时已清空），须把原话写回去、
        // 光标落文末，用户改字即改原句。只在文本不同时写，不扰动
        // 用户正在敲的字（Editing 只由 dismiss 产生，不在打字中出现）。
        if (state is IntentUiState.Editing && fieldValue.text != state.text) {
            onFieldValueChange(TextFieldValue(state.text, TextRange(state.text.length)))
        }
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

    // ---- 甲板示例来源（v6 能力发散，Bob 2026-10-24 18:45） ----
    // 模板来源：能力注册表的参数 schema（daohang/dache 的 to 槽位）+
    // 下一场会派生（提醒模板）+ 能力发散固定模板（见
    // SlotTemplates.buildExpandedExamples）；注册表加载幂等，remember
    // 一次即可。整池进胶囊上方主行横滚（v6 后调整：猜你想问行已删，
    // 切分口径退役）——同一池子，不另编文案。
    val slotCaps = remember {
        CapabilityRegistry.load(context)
        CapabilityRegistry.find("daohang") to CapabilityRegistry.find("dache")
    }
    val deckExamplePool = remember(slotCaps, nextEvent) {
        SlotTemplates.buildExpandedExamples(
            slotCaps.first, slotCaps.second,
            nextEvent?.takeIf { it.end > System.currentTimeMillis() },
            System.currentTimeMillis()
        )
    }
    // v6 后调整（Bob 2026-10-24 20:31：胶囊下方「猜你想问」行删除）：
    // 主行用整池横滚，不再切分——发散覆盖面保留，不退回旧 5 项池子。
    // 推荐按本机已装能力过滤（Bob 2026-10-24 22:06；321 查日程个案）：
    // 事实源与路由器 hints 同源（CapabilityRegistry.executableCapabilities，
    // epoch 缓存：装/卸应用时 installedEpoch 变化触发重算，不轮询、
    // 不每帧重查 PackageManager）。注意不用 validIds() 全集——全集不过滤。
    // 滤空兜底：空池时推荐行整行隐藏（见下 showSlimExamples 条件），不留空白占位。
    val deckExamples = remember(deckExamplePool, installedEpoch) {
        val executableIds = CapabilityRegistry.executableCapabilities(context).map { it.id }.toSet()
        SlotTemplates.filterExecutable(deckExamplePool, executableIds)
    }

    // F 批·点选填充：点示例 = 把模板整句（exampleFillText：有默认值代入
    // 默认值、无默认值的槽位以 label 原文呈现）写入输入框，光标落文末、
    // 焦点留在胶囊可继续编辑；瘦条常驻、本地结果在其下方出现
    // （2026-10-27 瘦条批，不再以空文本隐藏推荐行）。
    fun pickExample(t: SlotTemplate) {
        val sentence = t.exampleFillText()
        onFieldValueChange(
            TextFieldValue(sentence, TextRange(sentence.length))
        )
    }

    val idleOrEditing = state is IntentUiState.Idle || state is IntentUiState.Editing
    // 瘦条可见（2026-10-27 瘦条批，Bob：推荐问法要矮、贴输入框、打字
    // 不隐藏）：编辑系（Idle／Editing）即常驻，不再以空文本为条件——
    // 打字时瘦条仍在胶囊下方，命中应用在其下方离散出现（无动画）；
    // 思考／回复／追问／权限态在场时瘦条收起，不与结果槽位抢呈现。
    // sessionFocused 本身保留供其他用途。
    // 输入态专注 D·甲板应用行判定（2026-10-26 设计 §1：专注态默认隐藏，
    // 搜索命中时才显示）：甲板应用面即卡片内结果槽位的本地结果条
    // （DeckAppsBlock 已于 2026-10-25 随意图卡片统一整段删除，不恢复）。
    // 判定 = 本地搜索命中非空，匹配规则沿用现状 localResults（字面键 +
    // 拼音流键双键、字面在前合并、最多 4 个，见上 localResults 定义），
    // 本处不新发明匹配逻辑；空文本/未命中时行根本不进槽位，不留空带。
    // 点按行为不动：释放焦点 + onRequestExitInput + 打开应用（见槽位内）。
    val showDeckApps = localResults.isNotEmpty()

    // 2026-10-27 设计精致化 P1-6：查找态——索引是打字后才在 IO 线程建，
    // 建好前与真无匹配此前都显示空白、用户只能猜。needsSearching 为真
    // （打字中、索引未建）时延迟 300ms 出“正在查找应用”（快建好则不闪）；
    // 一旦 needsSearching 翻 false（索引建好／改文本／提交）即复位；打字
    // 中途 needsSearching 恒真、effect 不重启，条子不闪烁。
    val needsSearching = idleOrEditing &&
        committedKeys.literal.isNotBlank() &&
        committedKeys.literal != submittedText &&
        localIndex == null
    var showSearching by remember { mutableStateOf(false) }
    LaunchedEffect(needsSearching) {
        if (needsSearching) {
            delay(300)
            showSearching = true
        } else {
            showSearching = false
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    // 横屏判定（卡片形状、外延 shader 共用同一值）。
    val isLandscapeCard = screenWidthDp > screenHeightDp
    // 意图卡片轮廓（输入态专注·圆角外延）：顶部普通圆角、底部外延。
    // 横屏整体（本批）：横屏纯圆角 28dp、无外延
    // （flare 0、extent 0——IntentCardShape 在 flare=0 时走真圆角底边，
    // 无外延残留、无退化贝塞尔凸起）；竖屏保留 24dp 外延＋20dp 外扩。
    // remember keyed on isLandscapeCard（横竖屏切换时形状重建）。
    val intentCardShape = remember(isLandscapeCard) {
        IntentCardShape(
            cornerDp = 28.dp,
            flareDp = if (isLandscapeCard) 0.dp else INTENT_FLARE_HEIGHT,
            flareExtentDp = if (isLandscapeCard) 0.dp else 20.dp,
        )
    }
    // 外延玻璃 shader 覆盖（本批外延颜色修复）：竖屏外延翅膀超出平圆角
    // SDF，shader 锐利层须以外延矩形 SDF 取 min 补全覆盖，否则翅膀只有
    // 模糊层、本体有锐利层，交界现色阶台阶；横屏无外延传 null（shader
    // 走原平圆角 SDF，与形状一字对齐，无残留）。
    val panelFlareShader = remember(isLandscapeCard) {
        if (isLandscapeCard) null
        else FlareShaderParams(height = INTENT_FLARE_HEIGHT, extent = 20.dp)
    }
    // 卡片宽（弹闪修复，2026-10-26）：容器相对口径——BoxWithConstraints 的
    // maxWidth 即本列实际容器宽（竖屏 = 屏宽，横屏 = PetZone 左区槽宽）；
    // 外边距 = max(20dp, (容器宽 − 520dp) / 2)，卡片 = min(520dp, 容器宽 − 40dp)
    // 居于容器内。357 的左区垫高按屏宽算、在槽内二次扣减致卡片塌到 40dp
    // 闪退（见 resolveCardHorizontalPadding），本批拆除；横屏不左区躲、
    // 不窄化，底边不钉导航垫（本批横屏垫高恒 0dp，见 resolveFocusBottomPad）。
    // 容器宽恒有界（根 fillMaxSize 链），Infinity 兜底走屏宽公式。
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val containerWidth =
            if (maxWidth != Dp.Infinity) maxWidth else screenWidthDp
        val cardPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = resolveCardHorizontalPadding(containerWidth)
        )
        // 2026-10-28 意图页设计评审第 2 轮：空输入态底边发丝线宽度——
        // 面板体宽＋两侧外延（竖屏各 20dp，横屏无外延），线压在卡片底边
        // 全宽上，见覆盖层内空输入态分支。
        val panelBottomEdgeWidth =
            containerWidth - resolveCardHorizontalPadding(containerWidth) * 2 +
                if (isLandscapeCard) 0.dp else 40.dp
    Column(modifier = Modifier.fillMaxWidth().padding(cardPadding)) {
      // ---- 外层 Box：面板 + 前面图层宠物 + 动作清单（Dock 外层 Box 同款结构） ----
      // 外层 Box 高 = 面板高（面板是唯一决定尺寸的孩子）；宠物与清单放在
      // matchParentSize 覆盖层里、用 BottomCenter + 负偏移锚到面板顶缘上方，
      // 不占布局、不推面板——面板在流中的位置与改前一致，只短了旧宠物带
      // （胶囊带高从胶囊+宠物回到胶囊本身）。
      Box(modifier = Modifier.fillMaxWidth()) {
      // ---- 2026-10-28 意图页设计评审第 2 轮（修正版规格，优先于结构方案文档） ----
      // 内容定高结构契约：面板高度严格跟内容走——结果槽位只在有内容时
      // 占位、收起只在结果在场时以并入形态出现、空输入态不留任何无内容
      // 段。hasResultContent 必须与下方结果槽位 when() 的渲染条件逐项
      // 镜像（权限／追问／思考／本地结果／回复／错误／查找中／没找到），
      // 改槽位条件时须同步改这里，否则收起并入与槽位会错位。
      // 空输入态（false）：面板只含胶囊＋示例行（＋示例隐藏时只含胶囊），
      // 不硬放收起行——没什么可收起，硬放是用功能控件当视觉锚；底边
      // 本身可见由覆盖层底边发丝线承担（见下方空输入态分支）。
      // 结果在场（true）：收起并入结果条底边——与结果槽位包进同一 wrapper、
      // 之间 0 间距，收起仍保留箭头＋“收起”字＋48dp 热区、同一退出入口；
      // 字形→卡底由 60dp 压到 42dp（竖屏：热区居中后字形下沿 10dp＋面板
      // 底垫 8dp＋外延 24dp；再压需动 48dp 热区或外延，硬约束不允许）。
      // 退出兜底（空输入态无收起行时）：返回键、键盘自动收起（376）、
      // 点外退出（HomeScreen 单一入口）三路均不依赖收起行，不存在无路可退。
      // 2026-10-29 意图页设计评审第 3 轮·兜底时序（成文规则，行为一字不动）：
      // 三路互斥、任一先到即走同一退出入口（requestExitFocus，isExiting
      // 护栏防重复），无先后排队；键盘落下本身不等于退卡片，只有 376
      // 边沿（适用时）才跟随收起。
      // ① 返回键（375）：键盘在场时第一按归输入法收键盘（平台行为）；
      // 键盘收起后的下一按按 intentBackStep 分流——取消规划／关内层／
      // 退出意图（ExitIntent 走同一入口）。
      // ② 376 键盘自动收起：仅 Idle／Editing 态的可见→隐藏边沿触发，
      // 100ms 轮询＋120ms 防抖，提交中／在场持有态／退出进行中／方向
      // 变化任一守卫拦截即不触发；触发走同一入口。边沿后约 220ms 内
      // 守卫放行则卡片跟随收起，否则卡片留在屏底（落下态规则，见
      // resolveFocusBottomPad ④分支注释）。
      // ③ 点外退出：HomeScreen 根观察者真实 Press 事件；空输入／编辑态
      // 点外直接走同一入口退出，确认／追问／回复等在场持有态点外只收
      // 键盘、不退卡片（其落定退出走状态通道）。
      // 开放项：23:23 截图前键盘如何收起、是否按过返回／完成的操作序列
      // 仍待 Bob 回复，376 在该帧未见生效不得裁决为通道失效。
      val hasResultContent: Boolean = intentHasResultContent(
          state = state,
          showDeckApps = showDeckApps,
          idleOrEditing = idleOrEditing,
          committedLiteral = committedKeys.literal,
          submittedText = submittedText,
          localIndexReady = localIndex != null,
          showSearching = showSearching
      )
      // ---- F 批·一体化面板：输入区（胶囊 + 推荐行）+ 结果槽位同处一面 ----
      // 样式与 Dock 卡片同一套（2026-10-25 意图卡片统一）：玻璃引擎 STRONG +
      // 圆角＋外延轮廓（IntentCardShape：顶部普通圆角、底部 24dp
      // 外延，2026-10-26 输入态专注定稿）+ v2 光影（dockLight）+ 无描边——与
      // Dock 面板逐项对齐（见回执对照表）。Dock 的按压显底（dockBgAlpha）是
      // 按压反馈、不是卡片静止样式，故不复制：意图面板 fallback 用 GlassCard 自然透明度、
      // tintBoost 恒 0，静止态任何档位下都可读。面板是静态容器：无任何
      // 尺寸/高度动画（310 冻结教训），进出仍由 HomeScreen 的 fade-only
      // AnimatedVisibility 承担。面板只活在既有意图区内，不新开浮层、不另起列。
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intent_panel")
            .clip(intentCardShape)
            .glassSurface(
                kind = GlassSurfaceKind.STRONG,
                cornerRadius = 28.dp,
                tintColor = AILauncherColors.current.Card,
                fallbackColor = AILauncherColors.current.GlassCard,
                tintBoost = 0f,
                shape = intentCardShape,
                dockLight = DockLightParams(),
                flareShader = panelFlareShader
            )
            // 底垫 8dp 在外延区之上：竖屏总底垫 = 8dp + 24dp 外延区，
            // 全部内容（示例行、输入框、结果槽位）收在外延区之上，不压
            // 外延曲线；横屏无外延区，总底垫 = 8dp。
            // 2026-10-28 意图页设计评审第 1 轮 P1-4：14dp→8dp（外延 24dp 不动）。
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 8.dp + if (isLandscapeCard) 0.dp else INTENT_FLARE_HEIGHT
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // ---- 输入甲板已瘦身（2026-10-25）：面板上区应用推荐整段删除 ----
        // （Dock 里已有应用推荐，意图初始状态再推一遍多为重复）；初始状态
        // 剩下的只有下方推荐问主行（能力问题，做事型推荐，不是应用推荐，
        // 不动）+ 胶囊 + 结果槽位。

        // ---- 卡片内顺序（2026-10-27 瘦条批，改输入态专注 §3 旧顺序）：
        // 输入框（56dp pill、28dp 圆角，见 IntentCapsuleField）→ 8dp →
        // 瘦条（推荐问法行）→ 12dp → 结果槽位（有内容时才出现）。胶囊与
        // 瘦条包进内层 Column（8dp 间距）：瘦条不再是面板顶部的独立一节，
        // 而是贴在输入框下方、看上去像输入框的一部分。旧带盒退役口径不变
        // （见 git 历史：负偏移曾在 346 把行推出面板裁剪区）。胶囊本体行为不动。
        // 瘦条常驻＋命中离散出现（2026-10-27 瘦条批，取代 372 的“示例↔命中
        // 互斥切换”）：瘦条编辑系即显示（不论空文本），命中应用在其下方
        // 离散出现／消失（无动画，承接 372 离散口径；372 删掉的
        // fadeIn／fadeOut＋shrinkVertically 150ms 仍是禁区）。点按仍为整句
        // 填入输入框、不直接提交（见 pickExample）。
        // “矮”的取舍如实记：瘦条视觉只是一行 13sp 文字＋琥珀点、无底色，
        // 但布局高仍是 48dp——触控底线不缩水；矮靠贴合（8dp）与去 section
        // 感实现，不是靠压热区；再矮只能压热区，需 Bob 另定。
        // 卡片总高预算：打字态卡片比 372 多瘦条 48dp＋8dp 间距（约 56dp），
        // 满结果 180dp＋收起 48dp 时顶缘风险见紧配置复核（本批未验真机；
        // 2026-10-28 第 2 轮起收起并入结果条底边、面板间距 12dp 已省，
        // 满结果面板竖屏 398dp、横屏 374dp，见第 2 轮实现收据量测表）。
        val showSlimExamples = idleOrEditing && showDeckExamples && deckExamples.isNotEmpty()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 胶囊本体（与应用中心同一组件；面板语境用卡片同款玻璃，见上）
            // 意图页优化（2026-10-30，Bob 已批 §二 A1）：首页占位文案点明
            // 提交的含义——“想办什么？交给助手”，配金色提交钮与“交给助手
            // 去办”的无障碍文案，没用过这页的人也能一眼看懂点它就是把
            // 这句话交给助手去办。应用中心胶囊无提交钮（2026-11-03 已删），占位不动。
            IntentCapsuleField(
                value = fieldValue,
                onValueChange = { nv -> onFieldValueChange(nv) },
                onSubmit = { submitIfReady() },
                placeholder = "想办什么？交给助手",
                planning = planning,
                requestFocusOnMount = requestFocusOnMount,
                onFocusChanged = { capsuleFocused = it },
                solidBackground = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (showSlimExamples) {
                // 推荐问主行：从面板内容左缘起横滚；滤空时整行隐藏（按已装能力
                // 过滤后池子可能为空，不留空白占位）
                // 意图页优化（2026-10-30，Bob 已批 §二 B1）：本地结果在场时
                // 示例行弱化（dimmed）——胶囊与结果是主角，示例不抢空间；
                // 只降视觉权重，不隐藏、不压高、不动 48dp 热区（打字时
                // 常驻的 2026-10-27 决定不动；命中后让位是 §四项，本次不做）。
                DeckExamplesRow(
                    examples = deckExamples,
                    onPick = { pickExample(it) },
                    dimmed = showDeckApps
                )
            }
        }

        // ---- v6 后调整（Bob 2026-10-24 20:31）：胶囊下方「猜你想问」行
        // 整行删除（标签 + 后续推荐素行）；面板 Column 的 spacedBy(16.dp)
        // 自动收拢间距，不留空带；结果槽位直接回到胶囊下方。 ----

        // ---- 结果条槽位（非模态）：本地结果 / 回复 / 错误 / 权限说明共用 ----
        // 各 Strip 在面板内已去自身卡片底（透明，保留内边距与排版）。
        // 优先序：权限说明（需用户处理）> 本地结果（用户已改文本继续打字）> 回复/错误（对应已提交文本）
        // 槽位上限与条内滚动（输入态专注·卡片侧）：全部结果条共用 180dp 上限
        // （RESULT_BAR_MAX_HEIGHT 原注释即“三者共用”），超限槽位内滚动、
        // 不把卡片顶出可用高；卡片总高上限（键盘顶边到状态栏避让区）由宿主
        // 锚定保证。
        // 状态槽接线（设计 §5，只改变化点）：打字态本地结果经 showDeckApps
        // 进本槽；思考态 PlanningStrip 进本槽、键盘保持升起、输入框禁用沿用
        // 现状；结果态沿用“提交即收键盘”；追问、权限（优先序最高）沿用现状；
        // 高风险确认是全链路唯一弹窗例外，专注态不另起弹窗；气泡锚点随
        // perch 走（卡片宠物上方 8dp）。
        // 结果在场时槽位与收起包进同一 wrapper（wrapper 内 0 间距）：
        // 收起即结果条底边，不再是面板末项的独立一节；空输入态整段不渲染，
        // 面板只剩胶囊＋示例，高度严格跟内容走（见上方第 2 轮结构契约）。
        if (hasResultContent) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.testTag("intent_result_slot").heightIn(max = RESULT_BAR_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
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
            // 意图页优化（2026-10-30，Bob 已批 §二 B2/B3）：理解预告与
            // 正在执行——同一结果槽位内的两个新分支，不新开容器、不另起列：
            // 理解预告条先摊开理解（办什么＋关键参数值），用户点“就这样办”
            // 才执行、点“改一下”回编辑态改原话；执行条只呈现“正在办”，
            // 同步深链交接下极短即被回复／失败取代（状态机上三段已分开）。
            is IntentUiState.Understood -> {
                UnderstoodStrip(
                    summary = s.summary,
                    onConfirm = { onConfirmUnderstood(s.request) },
                    onEdit = onDismiss
                )
            }
            is IntentUiState.Executing -> {
                ExecutingStrip(summary = s.summary)
            }
            else -> {
                if (showDeckApps &&
                    state !is IntentUiState.NeedsConfirmation
                ) {
                    LocalResultsBar(
                        apps = localResults,
                        onOpen = { app ->
                            // 点开即收键盘（意图改进一批）：应用要打开了，先释放输入
                            // v0.68.101（D-2）：同时显式退出输入态（明确动作，不再
                            // 依赖失焦边沿推断）
                            releaseInputFocus()
                            // 过渡 C：显式退出走单一入口。
                            onExitFocusRequest()
                            launchLocalApp(context, app)
                        }
                    )
                } else when (s) {
                    is IntentUiState.Reply -> {
                        // 意图页优化（2026-10-30，Bob 已批 §二 A2）：回复条
                        // 带“助手：”归属前缀——与本地结果条的“本机应用”
                        // 标题对仗，用户一眼分清哪边是本机匹配、哪边是
                        // 七仔办完事的回音；前缀是弱说明，不承载关键信息。
                        StatusStrip(
                            text = s.text,
                            attribution = "助手：",
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
                        // 2026-10-27 设计精致化 P1-6：打字中槽位不再空白——
                        // 索引未建（延迟 300ms 后）出“正在查找应用”，索引建好
                        // 且确无匹配出“没找到，可以继续输入一句话交给助手”；
                        // 提交后抑制态（keys == submittedText）与确认态不在此列。
                        // 零打字呈现已迁到胶囊上方甲板（R-2）——无文本时本槽位
                        // 仍保持空（建议面 SuggestionsBar 退役口径不变）。
                        // 判序先 index：索引建好即确无结果（showSearching 残留
                        // 帧不抢位）；索引未建且延迟已到才出查找中。
                        if (idleOrEditing && committedKeys.literal.isNotBlank() &&
                            committedKeys.literal != submittedText
                        ) {
                            if (localIndex != null) {
                                SlotStatusLine(
                                    text = "没找到，可以继续输入一句话交给助手",
                                    testTag = "intent_no_results"
                                )
                            } else if (showSearching) {
                                SlotStatusLine(
                                    text = "正在查找应用",
                                    testTag = "intent_searching"
                                )
                            }
                        }
                    }
                }
            }
        }
        } // 结果槽位 Column
        // ---- 收起并入结果条底边（2026-10-28 意图页设计评审第 2 轮） ----
        // 不再是面板末项独立一节（旧形态：12dp 面板间距＋48dp 顶对齐盒，
        // 字形→卡底 60dp 尾巴的成因）；现为 wrapper 末项、与结果槽位 0 间距，
        // 收起即结果条底边。Row 本身不动（底托 pill、箭头＋“收起”字、
        // Title、12sp／16dp）；Box 由顶对齐改居中——48dp 热区不动，
        // 字形下沿由盒内 28dp 压到 10dp，字形→卡底 60dp→42dp（竖屏）。
        // 竖屏／横屏同款；热区 ≥48dp、同一退出入口、TalkBack 一字不动。
        // 旧口径存档：2026-10-27 角 ✕ 改底部居中软胶囊；第 1 轮 P0-1 恢复
        // 底托＋Title（亮壁纸 1.90:1→7.08:1）、P1-10 字阶收敛，均沿用。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("intent_focus_exit")
                .clickable { onExitFocusRequest() },
            contentAlignment = Alignment.Center
        ) {
            // 收起简洁化（2026-10-30，Bob 385 实拍 verdict：收起按钮太臃肿、
            // 占用位置大）：去底托填充、改紧凑 chevron＋文字——视觉从
            // 68×33dp 实心 pill 收为约 60dp 宽无底色文字；触控框一律不动
            // （上方 Box 整宽 × min 48dp），可见＜热区是背景 inset 于
            // 热区内，不压触控。
            Row(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AILauncherColors.current.Title,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "收起",
                    fontSize = AILauncherType.Caption.fontSize,
                    color = AILauncherColors.current.Title
                )
            }
        }
        } // 结果＋收起 wrapper
        } // if (hasResultContent)：空输入态无槽位、无收起，面板只剩胶囊＋示例
      } // 一体化面板 Column
      // ---- 覆盖层：接触影 + 宠物 + 动作清单（面板之后画、前面图层，不 clip） ----
      // 图层顺序即绘制顺序：面板 → 接触影 → 宠物 → 动作清单。覆盖层用
      // matchParentSize，不改变外层 Box 尺寸（= 面板高），宠物与清单只向
      // 上探出、不推面板。单宠物保证：Dock 宠物由 !intentInputMode 门控
      // （PullUpDock.kt，横屏聚焦亦隐藏），意图宠物只活在 IntentEntry
      // 内（输入态挂载，横屏亦挂载在左侧卡片上、头＋卡片一个身体），
      // 静止态下屏幕上恰有一只；竖屏进态交接窗内（HomeScreen 的
      // AnimatedVisibility fade + Dock 卡片退场 fadeOut）意图宠物以
      // FadeHandoff 180ms alpha 交接滑入，属一次交接、不出现两只实体的静止并存；
      // 横屏无交接（场景整体换：Dock 先淡出、槽位后淡入；退时槽位先淡出、
      // Dock 后淡入，先后不重叠、不滑动，见 HomeScreen 横屏分支）。
      // 意图旧胶囊接触影已按 22:02 verdict 整个摘除（黑 0.14、宽 72%、
      // 高 10dp、晚 90ms 的那枚），不再有影子跟随段。
      Box(modifier = Modifier.matchParentSize()) {
        // ---- 空输入态底边发丝线（2026-10-28 意图页设计评审第 2 轮修正版） ----
        // 空输入态不硬放收起行，卡片底边本身必须可见：仅在空输入态画
        // 一条 1dp 底边线，压在卡片底边全宽上（体宽＋两侧外延，横屏无外延）。
        // 手段说明（修正版契约 3，不先禁描边／阴影）：结果在场时底边由并入
        // 的收起 pill 立住、不另画线；空输入态无 pill 可立，1dp 弱描边是
        // 玻璃材质的正常组成部分、不是新语言——也不是整圈描边（351 去边框
        // 决策不动），只画底边一线。颜色取 Title 0.25（深浅两档同值：浅色
        // 下为暗线、深色下为亮线），不拦截点按、不占布局（覆盖层内）。
        if (!hasResultContent) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(panelBottomEdgeWidth)
                    .height(1.dp)
                    .background(AILauncherColors.current.Title.copy(alpha = 0.25f))
                    .testTag("intent_empty_bottom_edge")
            )
        }
      }
      } // 外层 Box（面板）
    }
    } // BoxWithConstraints 闭合（容器相对卡片宽；AlertDialog 仍属 IntentEntry）

    // ---- 唯一弹窗：中/高风险动作确认（安全门，Bob 拍板归类） ----
    if (state is IntentUiState.NeedsConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "确认执行？",
                    fontSize = AILauncherType.Headline.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = state.message,
                    fontSize = AILauncherType.Callout.fontSize,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(state.request) }) {
                    Text(
                        text = "执行",
                        // 意图页优化（2026-10-30，Bob 已批 §二 A4）：弹窗
                        // 里的执行也不再用金色——整页金色只留胶囊提交盘；
                        // 改 Title 色，浅色底上 16.81:1，比金色字更易读。
                        color = AILauncherColors.current.Title,
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

/** 结果行滚动复位 key：包名串——结果集一变 key 即变，行重建归零。 */
internal fun localResultsScrollKey(packageNames: List<String>): String =
    packageNames.joinToString(separator = "|")

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
    // + 标签行高 15sp（名称字号现为 13sp、行高仍钉 15sp，故公式一字不动）；另加 4dp 余量
    // （R4 dump 实测 TextView 高度比 lineHeight 多约 2.6dp，字体内边距关不干净）。
    val density = LocalDensity.current
    val minBarHeight = 73.dp + with(density) { 15.sp.toDp() + 15.sp.toDp() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 2026-10-27 设计精致化 P0-1：结果区坐稳局部底色（与推荐行同源），
            // 玻璃＋花纹壁纸下标题与名称对比度不再随壁纸走；只盖本条、卡片
            // 边缘仍透玻璃。F 批“面板内透明”口径就此让位给局部衬底。
            .clip(RoundedCornerShape(20.dp))
            .background(intentContentScrim())
            .requiredHeightIn(min = minBarHeight, max = RESULT_BAR_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            // R3-C 无障碍：结果一出现 TalkBack 礼貌播报（标题+应用名），不用手动找焦点
            .semantics { liveRegion = LiveRegionMode.Polite }
            // 2026-10-27 设计精致化 P1-5：内容左轴对齐输入文字——胶囊内边距
            // 18dp＋图标 18dp＋间距 10dp＝46dp，标题与首项左缘与之一致。
            .padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            // 2026-10-27 设计精致化 P1-5：说明缩成“应用 · N 个”，更紧更好扫。
            // 意图页优化（2026-10-30，Bob 已批 §二 A2）：标题点明归属——
            // “本机应用 · N 个”，与七仔回复条的“助手：”前缀对仗，打字
            // 结果与七仔回答不再混在同一个无名条里。
            text = "本机应用 · ${apps.size} 个",
            fontSize = AILauncherType.Caption.fontSize,
            // 2026-10-28 意图页设计评审第 1 轮 P1-6：标签深色 Body 实测
            // 4.43:1、浅色 Hint 推导 3.75:1，双双不过线——深色改 Title
            // （同衬底上 7.08:1）、浅色改 Body（对白约 7.06:1），字号 12sp 不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
            style = TextStyle(
                lineHeight = 15.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        // 2026-10-27 设计精致化 P0-2：结果行可横滚——固定 64dp 一项在窄屏
        // （320dp）放不下 4 项且 Row 无换行、末项会出界；横滚后任何可用宽、
        // 任何字号档都不出界，4 项全部可达。
        // 2026-10-28 意图页设计评审第 1 轮 P1-5：结果行同理加右缘 24dp
        // 渐隐（示例行同款，不拦截点按），溢出时呈现为“图标＋渐隐”。
        // 对齐修复（2026-10-30，385 实拍）：横滚行 rememberScrollState
        // 在结果变化时不复位——旧偏移残留会把首项顶出左缘裁切（实拍：
        // 行右移约 34dp，首图标左缺约 20dp、标签只剩“sApp”）。以包名
        // 串为 key：结果集一变整行重建、滚动归零，首项永远完整可见；
        // 同一结果集内用户手动滑动不受影响（key 不变）。
        val resultsScrollKey = localResultsScrollKey(apps.map { it.packageName })
        Box(modifier = Modifier.fillMaxWidth()) {
        key(resultsScrollKey) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                ResultAppItem(app = app, onOpen = onOpen)
            }
        }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(24.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, AILauncherColors.current.Card)
                    )
                )
        )
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
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            text = app.label.toString(),
            // 2026-10-27 设计精致化 P1-5：名称 11sp→12sp，更好扫。
            // 2026-10-28 意图页设计评审第 1 轮 P2-3：名称 12sp→13sp，与
            // 标签 12sp 拉开字阶；行高仍钉 15sp，故条保底高公式与 180dp
            // 上限、单项高（4＋36＋3＋15）一字不动，不溢出。
            fontSize = AILauncherType.Footnote.fontSize,
            // 2026-10-27 暗黑可读：深色 Body 在玻璃＋壁纸上发虚，改 Title；浅色不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                lineHeight = 15.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
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
        IslandToasts.show("无法打开「${app.label}」")
    }
}

/**
 * 结果槽位短时状态行（2026-10-27 设计精致化 P1-6）：查找中／没找到——
 * 与结果条同源弱衬底、同左轴（46dp），不另起卡片；TalkBack 礼貌播报。
 */
@Composable
private fun SlotStatusLine(text: String, testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(intentContentScrim())
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag)
            .padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = AILauncherType.Footnote.fontSize,
            // 2026-10-28 意图页设计评审第 1 轮 P1-1 连带：本行坐 intentContentScrim
            // 上，衬底降到 0.50 后深色 Body 复测仅 3.76:1，故深色改 Title
            // （同衬底上 7.08:1；与回复／思考条的深色 Title 口径一致），
            // 浅色 Body 在浅色衬底上 6.72:1、不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body
        )
    }
}

/** 胶囊下方短时小条：回复/错误共用，可带一个动作（如"去设置"），可关。 */
@Composable
private fun StatusStrip(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    // 意图页优化（2026-10-30，Bob 已批 §二 A2）：归属前缀（如“助手：”），
    // 只在既有行内出现，不新开容器；为 null 时不渲染、不占位。
    attribution: String? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // F 批：面板内透明——面板已提供统一背景，本条不再自带卡片底
            // R3-C 无障碍：回复/错误出现时 TalkBack 礼貌播报条内文案
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (attribution != null) {
            Text(
                text = attribution,
                fontSize = AILauncherType.Caption.fontSize,
                // 弱说明色：归属不是关键信息，不用 Title 抢眼；浅色
                // Body 在面板玻璃上约 7.06:1，深色 Body 在深色衬底
                // 上约 7.06:1（与正文同口径，见 §一对比表）。
                color = AILauncherColors.current.Body,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(
            text = text,
            fontSize = AILauncherType.Footnote.fontSize,
            // 2026-10-27 暗黑可读：深色 Body 在玻璃＋壁纸上发虚，改 Title；浅色不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
            // 短时小条：最多 3 行，超长截断，保证结果条高度有上限（见 RESULT_BAR_MAX_HEIGHT）
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    // 意图页优化（2026-10-30，Bob 已批 §二 A4）：去设置
                    // 不再用金色——金色只留提交主操作；改 Title 色，
                    // 浅色底上 16.81:1，比金盘字 2.42:1 更易读。
                    color = AILauncherColors.current.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = AILauncherType.Footnote.fontSize
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
 * 意图页优化（2026-10-30，Bob 已批 §二 B2）：理解预告条——规划落地后、
 * 执行前，把理解摊开给用户看。同一结果槽位、同一条语言（面板内透明、
 * 左轴与回复条一致），不新开容器、不另起列：
 * - 主文案一句话说清理解（“理解成：导航 · 机场”），用户一眼判对错；
 * - “就这样办”才真执行（协调器一次性守卫，连点不重复执行）；
 * - “改一下”回编辑态改原话（输入框同步回原句，见 LaunchedEffect(state)）；
 * - 纯查询（Chat）不走这里，直接出结果，不多一道确认；
 * - 中／高风险动作不走这里，仍走确认弹窗（原安全门）。
 * 常驻不自动收起；下方收起 pill 照常是退出入口。触控：两钮均为真
 * TextButton（与思考条取消钮同口径），另加 testTag 供冒烟定位。
 */
@Composable
private fun UnderstoodStrip(
    summary: String,
    onConfirm: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("understood_strip")
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "理解成：$summary",
            fontSize = AILauncherType.Footnote.fontSize,
            color = AILauncherColors.current.Title,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.testTag("understood_edit")
            ) {
                Text(
                    text = "改一下",
                    // 次要动作：Body 色，不抢“就这样办”。
                    color = AILauncherColors.current.Body,
                    fontSize = AILauncherType.Footnote.fontSize
                )
            }
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("understood_confirm")
            ) {
                Text(
                    text = "就这样办",
                    // A4：本条的主动作也不用金色——整页金色只留胶囊
                    // 提交盘；改 Title 色＋Medium 字重，仍是条内最显眼的钮。
                    color = AILauncherColors.current.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = AILauncherType.Footnote.fontSize
                )
            }
        }
    }
}

/**
 * 意图页优化（2026-10-30，Bob 已批 §二 B3）：正在执行条——与思考条
 * 同款 Row（面板内透明、TalkBack 礼貌播报），只呈现“正在办”，无钮：
 * 执行已交出，取消已无意义；同步深链交接下本条极短即被回复／失败
 * 取代，但思考／执行／完成在状态机与槽位呈现上就此分开。失败时
 * 走 Error 条明说原因，绝不报假成功（协调器原口径不动）。
 */
@Composable
private fun ExecutingStrip(summary: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = RESULT_BAR_MAX_HEIGHT)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("executing_strip")
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正在办：$summary",
            fontSize = AILauncherType.Footnote.fontSize,
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "思考中",
            fontSize = AILauncherType.Footnote.fontSize,
            // 2026-10-27 暗黑可读：深色 Body 在玻璃＋壁纸上发虚，改 Title；浅色不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
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
                // 意图页优化（2026-10-30，Bob 已批 §二 A4）：取消是次要
                // 动作，不再用金色抢眼，改 Body 色。
                color = AILauncherColors.current.Body,
                fontWeight = FontWeight.Medium,
                fontSize = AILauncherType.Footnote.fontSize
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
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = question,
            fontSize = AILauncherType.Footnote.fontSize,
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "需要开启一个权限",
            fontSize = AILauncherType.Callout.fontSize,
            fontWeight = FontWeight.SemiBold,
            color = AILauncherColors.current.Title
        )
        Text(
            text = message,
            fontSize = AILauncherType.Footnote.fontSize,
            // 2026-10-27 暗黑可读：深色 Body 在玻璃＋壁纸上发虚，改 Title；浅色不动。
            color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                // 2026-10-27 暗黑可读：深色 Hint 在深色玻璃上近隐形，改 Body；浅色不动。
                Text(text = "暂不", color = if (AILauncherColors.current.isDark) AILauncherColors.current.Body else AILauncherColors.current.Hint, fontSize = AILauncherType.Footnote.fontSize)
            }
            TextButton(onClick = onGoSettings) {
                Text(
                    text = "去开启",
                    // 意图页优化（2026-10-30，Bob 已批 §二 A4）：去开启
                    // 不再用金色——金色只留提交主操作；改 Title 色。
                    color = AILauncherColors.current.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = AILauncherType.Footnote.fontSize
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
