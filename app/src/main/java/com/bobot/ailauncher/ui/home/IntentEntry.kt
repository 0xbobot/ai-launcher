@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.apps.appendShoulderArch
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.glass.DockLightParams
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.ui.pet.HybridPet
import com.bobot.ailauncher.ui.pet.PetActionList
import com.bobot.ailauncher.ui.pet.TodayEventsHolder
import com.bobot.ailauncher.ui.theme.AILauncherColors
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
 * 意图卡片宠物（2026-10-25 意图卡片统一，Bob 拍板）：与 Dock 宠物同一只、
 * 同一套组件——Dock D1 档宠物宽固定，不跟意图面板高度走（面板高随
 * 结果槽位伸缩，宠物跟高走会忽大忽小）；压入量 5dp 与 Dock 肩峰压入同值。
 * 盒高按玻璃头长宽比（1043×1318）算，与 FloatingPet 内部口径一致。
 * B2+A 定稿（2026-10-26，Bob 点选 B2+A mock 档）：与 Dock D1 同步 ×0.8
 * （原 108dp → 86.4dp，与 Dock D1 的 86.6dp 仅面板估算舍入差）；接触影
 * （宽 ×0.72）与盒高均由此常量派生，自动跟随。
 */
private val INTENT_PET_WIDTH = 108.dp * 0.8f
private val INTENT_PET_PRESS = 5.dp

/**
 * 意图卡片肩线（输入态专注，2026-10-26 设计 §2）：弦 156.4dp、rise 18.0dp、
 * 压入 5dp——与 Dock B2+A 同一套语言，数值按设计定稿字面取，不从 Dock
 * 公式二次推导（156.4 × 18/156 = 18.05 ≈ 18.0，舍入差不可见）。
 */
private val INTENT_SHOULDER_CHORD = 156.4.dp
private val INTENT_SHOULDER_RISE = 18.0.dp

/** 意图卡片底部圆角外延区高度（2026-10-26 Bob 点选圆角外延，设计附录）。 */
private val INTENT_FLARE_HEIGHT = 24.dp

/**
 * 意图卡片轮廓（输入态专注·圆角外延，2026-10-26 Bob 点选）：
 * 顶缘 = Dock 肩线——直接复用 PullUpDock 的 [appendShoulderArch]（两段
 * cubicTo，k = 弦长/4，弦端与峰顶切线水平），不为意图卡片另写肩线；
 * 底部 = [flareDp] 高的外延区内两侧贝塞尔外扩 [flareExtentDp] 到全宽；
 * 全程单一 contour：拱与外延都在同一 Path 里续画，不 addPath、不另起
 * contour（351 对角线教训：addPath 另起 contour 会让 close() 从轮廓末点
 * 向拱起点拉对角线）。
 *
 * 局部坐标：原点在布局左上，y 向下；布局宽 = 卡片主体宽（竖屏 320dp、
 * 横屏封顶 520dp），主体两侧边在 x=0 / x=w；外延到底边时各向外扩
 * [flareExtentDp]（竖屏 20dp = 外边距，底边恰为屏宽；横屏同理外扩 20dp，
 * 底边 560dp 居中——横屏外扩到屏边须 140dp/side，曲线比例全毁，不取）。
 * 肩峰在 y=-rise 处（同 Dock：轮廓可超出布局上沿，布局高本身不动，
 * 外层 Box 不 clip，探出部分由宠物覆盖层空间容纳）；底边 y=h 全宽直线。
 *
 * 外延贝塞尔口径（设计附录，屏坐标左档 P0=(20,kb−24)→C1=(20,kb−10.8)→
 * C2=(11,kb)→P1=(0,kb)，右镜像；此处换算到布局坐标，主体左边缘为 x=0）：
 * 左（自下而上画）：P1=(-ext,h)→C1=(-ext+11,h)→C2=(0,h−10.8)→P0=(0,h−24)；
 * 右（自上而下画）：P0=(w,h−24)→C1=(w,h−10.8)→C2=(w+ext−11,h)→P1=(w+ext,h)。
 * 切线核对：外延上端控制点与主体侧边共线（竖直相切）、下端控制点与底边
 * 共线（水平相切），G1 连续无 kink；底角不再是 28dp 圆角——外延曲线本身
 * 就是底角处理（设计附录底边档口径；顶部两角仍走 Dock 同款 28dp 圆角）。
 */
internal class IntentCardShape(
    private val chordDp: Dp,
    private val riseDp: Dp,
    private val cornerDp: Dp,
    private val flareDp: Dp,
    private val flareExtentDp: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val chord = with(density) { chordDp.toPx() }
        val rise = with(density) { riseDp.toPx() }
        val corner = with(density) { cornerDp.toPx() }
        val flare = with(density) { flareDp.toPx() }
        val ext = with(density) { flareExtentDp.toPx() }
        val c1Drop = with(density) { 10.8.dp.toPx() }
        val c2Run = with(density) { 11.dp.toPx() }
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // 与 Dock 同款弦长钳制：窄布局下弦不超出圆角内侧。
        val half = (chord / 2f).coerceAtMost((w / 2f - corner).coerceAtLeast(0f))
        val x0 = cx - half
        val x1 = cx + half
        val k = half * 0.5f
        val yFlare = h - flare
        return Outline.Generic(Path().apply {
            moveTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            lineTo(x0, 0f)
            // 单 contour 续画肩线拱（moveToStart=false，不另起 contour）。
            appendShoulderArch(x0, x1, cx, k, rise, 0f, 0f, moveToStart = false)
            lineTo(w - corner, 0f)
            quadraticTo(w, 0f, w, corner)
            lineTo(w, yFlare)
            // 右侧外延：上端竖直相切、下端水平相切。
            cubicTo(w, h - c1Drop, w + ext - c2Run, h, w + ext, h)
            // 底边：全宽直线，贴键盘顶边（0dp 缝由宿主锚定保证）。
            lineTo(-ext, h)
            // 左侧外延（反向画）：下端水平相切、上端竖直相切。
            cubicTo(-ext + c2Run, h, 0f, h - c1Drop, 0f, yFlare)
            // close() 只回到起点 (0, corner)，即左侧边竖直线，无对角线。
            close()
        })
    }
}

/**
 * perch 位移起点估算（只读引用 PullUpDock 的 D1 静态几何，不改动它）：
 * Dock 宠物脚底距屏底 = 面板高 136dp + 肩 rise 18.1dp（弦 156.6dp × 18/156，
 * B2+A 定稿）− 压入 5dp ≈ 149.1dp。横屏 Dock 卡宽 344dp、右留边 12dp，
 * 故横屏 Dock 宠物中心 X = 屏宽 − 12dp − 172dp。
 * 估算只决定位移起点的落点，不决定终态：终态恒为面板肩峰压入 5dp
 * （峰顶 = 面板顶缘 − 18dp rise，见 IntentCardShape）；
 * D2（面板 184dp / rise 21.6dp）时起点估高约 48dp——Dock 卡正在退场、
 * 宠物本体已随卡片滑出淡出，48dp 的起点偏差只影响起步那一帧的落点，
 * 不影响“从 Dock 方向连续滑上来”的读感；要像素级精确须把 DockState
 * 经 HomeScreen 透传进来（HomeScreen / PullUpDock 不在本批文件范围，
 * 故此处不做，见集成注意点）。
 * 兜底：首帧未量到面板位置时起点取面板下方 320dp（典型竖屏下与
 * 实测起点差一个面板高以内），宠物仍从 Dock 方向滑入，不留空窗。
 */
private val PERCH_TRAVEL_DOCK_FOOT_FROM_BOTTOM = 149.1.dp
private val PERCH_TRAVEL_DOCK_CENTER_END_LANDSCAPE = 184.dp
private val PERCH_TRAVEL_FALLBACK_Y = 320.dp

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
    // R3-C：焦点变化回调（首页用来驱动七仔倾听姿态；应用中心不传，保持纯过滤）
    onFocusChanged: (Boolean) -> Unit = {},
    // v0.68.99（主页重构 R-1）：挂载即聚焦——首页输入态由「点七仔格/讲述者」
    // 进入，用户点一次就应能打字（键盘随聚焦弹起）；应用中心不传，保持点按
    // 聚焦的原行为；「问七仔」自动带起的思考/回复呈现也不传（不弹键盘）。
    requestFocusOnMount: Boolean = false,
    // F 批：面板语境背景——首页胶囊坐在一体化面板里，0.88 半透明底在面板上
    // 显脏；首页传 true（应用中心不传，保持原半透明观感）。
    // 2026-10-25 意图胶囊去深色（Bob 23:18 真机反馈）：true 原先用不透明
    // Card 色——深色模式下 Card 是近黑 0xFF2A2723，实拍（40,39,35）证实
    // 实心黑 pill 压在半透明玻璃卡片上打架；现改走卡片同一套玻璃
    // （STRONG + dockLight，与面板逐项对齐），与卡片同材质同色调。
    solidBackground: Boolean = false,
    // 应用中心升级（Bob 2026-10-24 定稿②）：箭头 = 确认本地搜索——
    // 调用方传此回调时，箭头钮走它（仅收键盘 + 定格结果）；不传时箭头
    // 沿旧走 onSubmit（首页不动）；IME Done 恒走 onSubmit，不受此影响
    onArrowSubmit: (() -> Unit)? = null
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
            .then(
                if (solidBackground) {
                    // 首页变体：卡片同款玻璃（见 solidBackground 注释），不另套新材质；
                    // 与面板同一引擎同一参数同一背景源，渲染色调与卡片一致。
                    Modifier.glassSurface(
                        kind = GlassSurfaceKind.STRONG,
                        cornerRadius = 28.dp,
                        tintColor = AILauncherColors.current.Card,
                        fallbackColor = AILauncherColors.current.GlassCard,
                        tintBoost = 0f,
                        shape = RoundedCornerShape(28.dp),
                        dockLight = DockLightParams()
                    )
                } else {
                    Modifier.background(AILauncherColors.current.Card.copy(alpha = 0.88f))
                }
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
                            // 2026-10-25 胶囊去深色连带：玻璃胶囊是中间调（深色模式
                            // 下深色 Hint 中灰约 0xFF6B6560 在其上对比度仅约 1.1:1、
                            // 几乎看不见），占位改 Title 降 alpha——深浅两档在玻璃
                            // 上都可读；应用中心变体（实心底）沿用 Hint，一字不动。
                            color = if (solidBackground) AILauncherColors.current.Title.copy(alpha = 0.6f)
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
                .clickable(enabled = canSubmit) { (onArrowSubmit ?: onSubmit)() },
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
    // 输入态专注·进出过渡 C（2026-10-26，见 PetPerchTransition.kt）：
    // HomeScreen 持有的同一过渡状态与单一退出入口。传 perchTransition
    // 时宠物位移／alpha／影子走共享 Animatable（进出同一弹簧、退态复用
    // 进态冻结位移量）；不传时走本函数内旧的本地 Animatable（独立使用
    // 时行为与 355 一致）。onExitFocusRequest 是 D 的 ✕ 一键退出也要调
    // 的唯一入口：✕ 只调它，不自写退出动画；默认回落到 onRequestExitInput。
    perchTransition: PetPerchTransitionState? = null,
    onExitFocusRequest: () -> Unit = onRequestExitInput,
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

    // 2026-10-25 意图卡片统一（Bob 拍板：宠物事件动作全部复用同一套）：
    // 旧意图宠物（InputPetCompanion → PetView 姿态图）的 R3-C 反应管线
    // （倾听 FETCH / 思考 SORTING+头顶点点 / 呈现 HAPPY）随旧宠物块整段
    // 退役——意图卡片上的宠物就是 Dock 宠物本体（HybridPet → FloatingPet
    // 活感引擎：呼吸、眨眼、转头注视、手动表演动作），行为与 Dock 那只
    // 完全一致，不另维护一套姿态。会话状态的传达仍由槽位内容本身承担
    // （PlanningStrip「想想中」/ LocalResultsBar / StatusStrip），不经宠
    // 物姿态。PetRepository 的 intentPose 覆盖层随之删除（唯一消费者已无）。
    // 但思考标志保留：眼睛批（344）以 intentThinking 驱动专注眼形，故
    // Planning 态仍写入、离场清零——姿态退役不得砍掉眼睛引擎的输入。
    LaunchedEffect(planning) { PetRepository.setIntentThinking(planning) }
    DisposableEffect(Unit) {
        onDispose { PetRepository.setIntentThinking(false) }
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
    // 滤空兜底：空池时推荐行整行隐藏（见下 showDeck 条件），不留空白占位。
    val deckExamples = remember(deckExamplePool, installedEpoch) {
        val executableIds = CapabilityRegistry.executableCapabilities(context).map { it.id }.toSet()
        SlotTemplates.filterExecutable(deckExamplePool, executableIds)
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

    // ---- 意图卡片统一（2026-10-25，Bob 拍板） ----
    // 面板实测高：只用于宠物/清单的锚定偏移，不参与面板自身布局——
    // 首帧估 96dp（胶囊 56 + 间距 12 + 内边距 28），实测到即纠正；动作
    // 清单开关与 Dock 宠物同款语义（点宠物开关清单）；意图搜索在清单
    // 2 级，意图语境下点它仅收起（本来就在意图搜索里，不再重进）。
    var panelHeight by remember { mutableStateOf(96.dp) }
    var petActionsVisible by remember { mutableStateOf(false) }
    // ---- 同一只宠物走过去（宠物交互重构 P0-3，2026-10-26） ----
    // 1500ms 延迟挂载 + 占位盒已删除：意图宠物随 IntentEntry 挂载即在场，
    // 不再有 1.5 秒空窗。进态位移在本函数内完成，不碰 PullUpDock 的
    // Dock 宠物门禁与 HomeScreen 的 fade-only 槽位过渡：
    //  - 宠物本体：挂载即从 Dock perch 方向（下方）连续滑到面板顶缘，
    //    全程同一 HybridPet / 同一活感引擎，途中不停留、不消失；
    //    位移量 = 起点估算（Dock 脚底）− 终点（面板顶缘 + 压入），
    //    以附加 offset 叠加在终态锚定偏移之上，面板高变化（结果槽位
    //    伸缩）时终态锚定照常跟随，位移向量冻结在进态那一刻不重算；
    //  - 落回带回弹过冲：宠物位移走弹簧（damping 0.75，终点附近轻微
    //    越过再回落），不是线性滑到位；头先动、跟随延迟：接触影
    //    晚 90ms 才淡入归位（影子跟在身体后面，不是焊在脚底同步走）；
    //  - 交接淡入：宠物 alpha 在 180ms 内 0→1，与 Dock 卡片退场
    //    fadeOut（150ms）交叠为一次交接，不出现两只实体的静止并存；
    //  - 尺寸：位移全程保持 INTENT_PET_WIDTH 不变（Dock D1 86.6dp →
    //    意图 86.4dp 差 0.2dp，插值无意义；D2 117.1dp → 86.4dp 的台阶
    //    不猜 DockState 做假插值，见文件头常量注释与集成注意点）；
    //  - 减动效：位移直接 snap 到终态，不播弹簧；
    //  - 退态（退出输入态）反向位移：由 HomeScreen 的 PetPerchTransition
    //    funnel 承担（本函数传 perchTransition 时位移／alpha／影子走共享
    //    Animatable；退态 = travel 1→0、同一弹簧、进态冻结位移量取负，
    //    四步时序见 PetPerchTransition.kt）。旧口径“退场由 HomeScreen
    //    fadeOut＋Dock 卡片重进承担、无交接”就此废掉，不许再各自保留一套 fade。
    // 面板在根坐标的位置（外层 Box 回写，取整 dp 防抖动重组）。
    var petAnchorTop by remember { mutableStateOf<Int?>(null) }
    var petAnchorCenterX by remember { mutableStateOf<Int?>(null) }
    // 位移向量（dp）：进态那一刻冻结，之后面板位置变化不再重算。
    var travelDeltaX by remember { mutableStateOf(0.dp) }
    var travelDeltaY by remember { mutableStateOf(PERCH_TRAVEL_FALLBACK_Y) }
    // 过渡 C：共享状态在场时位移／alpha／影子走 HomeScreen 持有的同一组
    // Animatable（退态由 HomeScreen 的 funnel 反向驱动同一组）；否则走
    // 本地组（355 行为）。本地组恒创建（廉价），只在无共享状态时使用。
    val legacyTravel = remember { Animatable(0f) }
    val legacyTravelAlpha = remember { Animatable(0f) }
    val legacyShadowTravel = remember { Animatable(0f) }
    val petTravel = perchTransition?.travel ?: legacyTravel
    val petTravelAlpha = perchTransition?.travelAlpha ?: legacyTravelAlpha
    val shadowTravel = perchTransition?.shadowTravel ?: legacyShadowTravel
    val reduceMotion = rememberReduceMotion()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    LaunchedEffect(Unit) {
        // 等一小段让外层 Box 的 onGloballyPositioned 先回写锚点
        // （布局回调在首帧内到达；等不到则用兜底起点，不阻塞挂载）。
        var waited = 0
        while (petAnchorTop == null && waited < 6) {
            delay(16)
            waited++
        }
        val anchorTop = petAnchorTop?.dp
        val anchorCenterX = petAnchorCenterX?.dp
        if (anchorTop != null) {
            val dockFootY = screenHeightDp - PERCH_TRAVEL_DOCK_FOOT_FROM_BOTTOM
            // 脚底在肩峰上：峰顶 = 面板顶缘 − rise，脚底压进峰顶 5dp。
            val intentFootY = anchorTop - INTENT_SHOULDER_RISE + INTENT_PET_PRESS
            travelDeltaY = (dockFootY - intentFootY)
                .coerceIn(0.dp, screenHeightDp)
            travelDeltaX = if (screenWidthDp > screenHeightDp && anchorCenterX != null) {
                (screenWidthDp - PERCH_TRAVEL_DOCK_CENTER_END_LANDSCAPE - anchorCenterX)
                    .coerceIn(-screenWidthDp / 2, screenWidthDp / 2)
            } else {
                0.dp
            }
        }
        if (perchTransition != null) {
            // 过渡 C：上一轮退态未收尾时先等它收尾（封顶 ~1s），再冻结本轮
            // 位移量——避免新冻结值中途改写在途退态的附加 offset（退态渲染
            // 仍在读同一份冻结量）。随后进态走共享状态（同一弹簧／时序）。
            var waitedExit = 0
            while (perchTransition.isExiting && waitedExit < 60) {
                delay(16)
                waitedExit++
            }
            perchTransition.freezeDelta(travelDeltaX, travelDeltaY)
            perchTransition.animateEnter(reduceMotion)
        } else if (reduceMotion) {
            petTravel.snapTo(1f)
            petTravelAlpha.snapTo(1f)
            shadowTravel.snapTo(1f)
        } else {
            launch { petTravel.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
            launch { petTravelAlpha.animateTo(1f, tween(180)) }
            launch { shadowTravel.animateTo(1f, tween(550, delayMillis = 90)) }
        }
    }
    val intentPetHeight = INTENT_PET_WIDTH * (1318f / 1043f)
    // 意图卡片轮廓（输入态专注·圆角外延）：顶缘复用 Dock 肩线、底部外延，
    // 参数常量见文件头；remember 无 key——参数全是常量，不随状态变。
    val intentCardShape = remember {
        IntentCardShape(
            chordDp = INTENT_SHOULDER_CHORD,
            riseDp = INTENT_SHOULDER_RISE,
            cornerDp = 28.dp,
            flareDp = INTENT_FLARE_HEIGHT,
            flareExtentDp = 20.dp,
        )
    }
    // 卡片宽：竖屏 320dp（两侧各 20dp）、横屏封顶 520dp 居中——外边距取
    // 20dp 与 (屏宽 − 520dp)/2 的大值，屏宽 ≤ 560dp 时恒为 20dp。
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = maxOf(20.dp, (screenWidthDp - 520.dp) / 2))) {
      // ---- 外层 Box：面板 + 前面图层宠物 + 动作清单（Dock 外层 Box 同款结构） ----
      // 外层 Box 高 = 面板高（面板是唯一决定尺寸的孩子）；宠物与清单放在
      // matchParentSize 覆盖层里、用 BottomCenter + 负偏移锚到面板顶缘上方，
      // 不占布局、不推面板——面板在流中的位置与改前一致，只短了旧宠物带
      // （胶囊带高从胶囊+宠物回到胶囊本身）。
      // 锚点回写（供 perch 位移起点估算）：面板在根坐标的顶缘与中心 X，
      // 取整 dp、变化才写，避免布局抖动引发重组风暴。
      Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { coords ->
          // 本仓 Compose 版本无 positionInRoot（编译已证实），用 HomeScreen
          // 同款 boundsInRoot：top = 面板顶缘，center.x = 面板中心 X。
          val bounds = coords.boundsInRoot()
          with(densityNow) {
              val top = bounds.top.toDp().value.toInt()
              val centerX = bounds.center.x.toDp().value.toInt()
              if (petAnchorTop != top) petAnchorTop = top
              if (petAnchorCenterX != centerX) petAnchorCenterX = centerX
          }
      }) {
      // ---- F 批·一体化面板：输入区（胶囊 + 推荐行）+ 结果槽位同处一面 ----
      // 样式与 Dock 卡片同一套（2026-10-25 意图卡片统一）：玻璃引擎 STRONG +
      // 肩线＋圆角外延轮廓（IntentCardShape：顶缘复用 Dock 肩线、底部 24dp
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
                dockLight = DockLightParams()
            )
            .onGloballyPositioned { coords ->
                with(densityNow) { panelHeight = coords.size.height.toDp() }
            }
            // 底垫 14dp 在外延区之上：总底垫 = 14dp + 24dp 外延区，全部内容
            // （示例行、输入框、结果槽位）收在外延区之上，不压外延曲线。
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp + INTENT_FLARE_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // ---- 输入甲板已瘦身（2026-10-25）：面板上区应用推荐整段删除 ----
        // （Dock 里已有应用推荐，意图初始状态再推一遍多为重复）；初始状态
        // 剩下的只有下方推荐问主行（能力问题，做事型推荐，不是应用推荐，
        // 不动）+ 胶囊 + 结果槽位。

        // ---- 输入区：胶囊 + 推荐问主行（2026-10-25：宠物已移出胶囊） ----
        // 七仔不再栖坐胶囊上沿（旧 v6 坐法随 InputPetCompanion 退役），改栖
        // 坐卡片上沿（见面板外覆盖层，与 Dock 上沿同一套坐法）。带高回到胶
        // 囊本身；推荐行底距胶囊顶 8dp（底部对齐）不变，起横滚位置从七仔右
        // 缘回到面板内容左缘；胶囊高实测 onGloballyPositioned 回写（首帧
        // 默认 56dp = 胶囊 min 高，实测到即纠正）。七仔常驻（打字后示例让
        // 位、七仔仍在场）；胶囊本体行为不动。静态布局，无尺寸动画（310 教训）。
        var capsuleHeight by remember { mutableStateOf(56.dp) }
        // 2026-10-25 示例行回槽（346 回归修复，Bob 23:18“推荐问法还是需要”
        // 的前提）：346 把宠物移上卡片上沿后，带盒高从胶囊+宠物缩回只剩
        // 胶囊，示例行沿用 -(capsuleHeight+8) 的上偏移后落到面板裁剪区外、
        // 只剩约 6dp 被裁（与脚本 §25/a11y“示例在场可点”口径不符）。带盒
        // 在行在场时补回行高+间距（48+8=56dp），行底回到胶囊顶上 8dp、
        // 完整落在面板内且不与胶囊重叠；行隐藏（打字后）时带盒同步缩回，
        // 不留空带。行本身位置公式一字不动，胶囊尺寸位置不动。
        val showExamplesRow = showDeck && showDeckExamples && deckExamples.isNotEmpty()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_pet_band")
                .height(capsuleHeight + if (showExamplesRow) 56.dp else 0.dp)
        ) {
            // 胶囊本体（与应用中心同一组件；面板语境用卡片同款玻璃，见上）
            IntentCapsuleField(
                value = fieldValue,
                onValueChange = { nv -> onFieldValueChange(nv) },
                onSubmit = { submitIfReady() },
                planning = planning,
                requestFocusOnMount = requestFocusOnMount,
                onFocusChanged = { capsuleFocused = it },
                solidBackground = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coords ->
                        with(densityNow) {
                            capsuleHeight = coords.size.height.toDp()
                        }
                    }
            )
            // 推荐问主行：底部对齐（行底距胶囊顶 8dp），从面板内容左缘起横滚；
            // 滤空时整行隐藏（按已装能力过滤后池子可能为空，不留空白占位）
            if (showExamplesRow) {
                DeckExamplesRow(
                    examples = deckExamples,
                    onPick = { pickExample(it) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .offset(y = -(capsuleHeight + 8.dp))
                )
            }
        }

        // ---- v6 后调整（Bob 2026-10-24 20:31）：胶囊下方「猜你想问」行
        // 整行删除（标签 + 后续推荐素行）；面板 Column 的 spacedBy(12.dp)
        // 自动收拢间距，不留空带；结果槽位直接回到胶囊下方。 ----

        // ---- 结果条槽位（非模态）：本地结果 / 回复 / 错误 / 权限说明共用 ----
        // 各 Strip 在面板内已去自身卡片底（透明，保留内边距与排版）。
        // 优先序：权限说明（需用户处理）> 本地结果（用户已改文本继续打字）> 回复/错误（对应已提交文本）
        // 槽位上限与条内滚动（输入态专注·卡片侧）：全部结果条共用 180dp 上限
        // （RESULT_BAR_MAX_HEIGHT 原注释即“三者共用”），超限槽位内滚动、
        // 不把卡片顶出可用高；卡片总高上限（键盘顶边到状态栏避让区）由宿主
        // 锚定保证（见集成注意点，HomeScreen 不在本批文件范围）。
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
                            // 过渡 C：显式退出走单一入口（反向位移 funnel）。
                            onExitFocusRequest()
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
      // ---- 覆盖层：接触影 + 宠物 + 动作清单（面板之后画、前面图层，不 clip） ----
      // 图层顺序即绘制顺序：面板 → 接触影 → 宠物 → 动作清单。覆盖层用
      // matchParentSize，不改变外层 Box 尺寸（= 面板高），宠物与清单只向
      // 上探出、不推面板。单宠物保证：Dock 宠物由 !intentInputMode 门控
      // （PullUpDock.kt），意图宠物只活在 IntentEntry 内（仅输入态挂载），
      // 静止态下屏幕上恰有一只；进态交接窗内（HomeScreen 的
      // AnimatedVisibility fade + Dock 卡片退场 fadeOut）意图宠物以
      // 180ms alpha 交接滑入，属一次交接、不出现两只实体的静止并存。
      Box(modifier = Modifier.matchParentSize()) {
        // 接触影：落在肩峰面上、宠物脚底正下方（面板之后、宠物之前画；
        // 口径沿用旧胶囊接触影：黑 0.14、宽为宠物宽 72%、高 10dp、落点
        // 改到肩峰面下 4dp，即面板顶缘上 rise−4dp 处）。位移跟随：影子不跟宠物同步走全程——
        // 晚 90ms 才淡入归位（travel 0→1），落点带 8dp 上滑收束，
        // 读作“身体先到、影子后跟上”，连接处不焊死。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 4.dp - INTENT_SHOULDER_RISE + 8.dp * (1f - shadowTravel.value))
                .size(width = INTENT_PET_WIDTH * 0.72f, height = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.14f * shadowTravel.value))
        )
        // 宠物：Dock 宠物本体（HybridPet，同一组件、同一活感引擎、同一动
        // 作状态机），只是换了个 perch——坐法与 Dock 上沿同一套：前面图
        // 层、居中、脚底压进卡片肩峰 INTENT_PET_PRESS（峰顶 = 面板顶缘 − rise）、接触不断；手势
        // 语义与 Dock 宠物同一套（集成时统一，协调方决策）：点按在意
        // 图态已无“问”可进，故为空操作（不与胶囊聚焦、点外退出打
        // 架）；动作清单只挂长按，与 Dock 侧一致，不许点按再开清单。
        // 位移：终态锚定偏移（面板高 ＋ rise − 压入，脚底压进肩峰 5dp）之上叠加位移附加量
        // travelDelta × (1 − travel)，travel 弹簧到 1（轻微过冲回落
        // 即落回回弹）；减动效时 travel 已 snap 到 1，附加量为零。
        // 真机待验：卡片顶缘坐姿、压入量、接触影在真实壁纸上的观感，
        // 以及位移全程（起步落点、弹簧过冲、影子跟随）是否自然。
        HybridPet(
            petWidth = INTENT_PET_WIDTH,
            onPetTap = { /* 意图态已在问，点按空操作；清单只挂长按 */ },
            onPetLongPress = { petActionsVisible = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(panelHeight + INTENT_SHOULDER_RISE - INTENT_PET_PRESS))
                .offset(
                    x = travelDeltaX * (1f - petTravel.value),
                    y = travelDeltaY * (1f - petTravel.value)
                )
                .graphicsLayer { alpha = petTravelAlpha.value }
                .testTag("pet_qizai")
        )
        // 动作清单：与 Dock 宠物上方同款（PetActionList 同一组件、同一清单、
        // 同一状态机；气泡底距宠物顶 8dp、尾巴朝下指向宠物）。单层 8 行，
        // 无 2 级；关闭靠气泡内 ✕，退出输入态时随面板一并消失。
        // 真机待验：意图面板上方空间有限（紧凑简报 + 状态栏），气泡顶部
        // 在小屏/横屏可能探出屏顶——若实测被裁，另起一单定清单落点。
        if (petActionsVisible) {
            PetActionList(
                onDismiss = { petActionsVisible = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(panelHeight + INTENT_SHOULDER_RISE + intentPetHeight - INTENT_PET_PRESS + 8.dp))
                    .testTag("pet_action_list")
            )
        }
      }
      } // 外层 Box（面板 + 前面图层宠物 + 动作清单）
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
