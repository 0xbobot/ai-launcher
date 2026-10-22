package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 输入甲板组件（v0.68.101·主页重构 R-2）：聚焦且未打字时，胶囊上方槽位
 * 的上区呈现——常用应用块 + 动态示例 chips；模板填入后，上区切换为当前
 * 槽位的快捷选择面板（选项 chips + 自由输入行），示例行留在面板下方。
 *
 * 甲板只活在输入态（与重构定案一致：建议撤出静态主页）；会议不在这里
 * 复刻——简报会行是会议的唯一呈现（.93 去重契约由「甲板根本不出会议
 * 行」天然满足）。与结果条同 Card 语言，高度守槽位纪律（见 IntentEntry
 * 的降级规则：先收示例行、再由宿主收应用量，绝不新开浮层）。
 */

/** 甲板·常用应用块：标题与应用项与旧建议面完全一致（点选直达打开）。 */
@Composable
fun DeckAppsBlock(
    apps: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "七仔猜你现在想用",
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
                ResultAppItem(app = app, onOpen = onOpenApp)
            }
        }
    }
}

/** 甲板·动态示例 chips（横向滚动）：点 = 把模板填进胶囊（不直接提交）。 */
@Composable
fun DeckExamplesRow(
    examples: List<SlotTemplate>,
    onPick: (SlotTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        examples.forEach { t ->
            Box(
                modifier = Modifier
                    .testTag("deck_example_${t.id}")
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(AILauncherColors.current.Card.copy(alpha = 0.88f))
                    .clickable { onPick(t) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t.chipText(),
                    fontSize = 13.sp,
                    color = AILauncherColors.current.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 胶囊内模板条（经 IntentCapsuleField 的 decorationBox 渲染）：固定文本
 * 段与槽位芯片同行排布、横向可滚。未填槽位 = 金色描边 + 槽位名；
 * 已填 = 金色淡底实心 + 值 + ▾（点它重开选择面板改值）。
 */
@Composable
fun TemplateStrip(
    fill: TemplateFill,
    onSlotTap: (SlotDef) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        fill.template.parts.forEach { part ->
            when (part) {
                is TemplatePart.Text -> Text(
                    text = part.text,
                    fontSize = 15.sp,
                    color = AILauncherColors.current.Title,
                    maxLines = 1
                )
                is TemplatePart.Slot -> SlotChip(
                    def = part.def,
                    value = fill.valueOf(part.def.param),
                    onClick = { onSlotTap(part.def) }
                )
            }
        }
    }
}

@Composable
private fun SlotChip(
    def: SlotDef,
    value: String?,
    onClick: () -> Unit
) {
    val accent = AILauncherColors.current.Accent
    val filled = value != null
    Box(
        modifier = Modifier
            .testTag("slot_chip_${def.param}")
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (filled) accent.copy(alpha = 0.18f)
                else accent.copy(alpha = 0.0f)
            )
            .border(
                1.dp,
                accent.copy(alpha = if (filled) 0.45f else 0.9f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (filled) "$value ▾" else def.label,
            fontSize = 14.sp,
            color = accent,
            fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 槽位快捷选择面板（甲板上区）：标题「槽位 · <label>」+ 选项 chips
 * （横向滚动，当前值高亮）+ 自由输入行（IME Done / 「确定」确认；
 * 清空确认 = 清除该槽位的值，面板保持打开）。
 *
 * [seedText] 非空时自由输入以它起头（模板态直接打字被宿主路由到这里）；
 * 否则以当前值起头（方便改字或清空重填）。
 */
@Composable
fun SlotPanel(
    def: SlotDef,
    options: List<String>,
    currentValue: String?,
    seedText: String,
    onPickOption: (String) -> Unit,
    onConfirmFree: (String) -> Unit,
    onFreeFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var freeText by remember(def.param, seedText) {
        mutableStateOf(seedText.ifEmpty { currentValue ?: "" })
    }
    Column(
        modifier = modifier
            .testTag("slot_panel")
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AILauncherColors.current.Card.copy(alpha = 0.95f))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "槽位 · ${def.label}",
            fontSize = 12.sp,
            color = AILauncherColors.current.Accent,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                lineHeight = 15.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        if (options.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { i, opt ->
                    SlotOptionChip(
                        text = opt,
                        selected = opt == currentValue,
                        testTag = "slot_option_$i",
                        onClick = { onPickOption(opt) }
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = freeText,
                onValueChange = { freeText = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("slot_free_input")
                    .onFocusChanged { onFreeFocusChanged(it.isFocused) },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = AILauncherColors.current.Title
                ),
                cursorBrush = SolidColor(AILauncherColors.current.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onConfirmFree(freeText) }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (freeText.isEmpty()) {
                            Text(
                                text = when (def.kind) {
                                    SlotKind.DESTINATION -> "自己输入目的地…"
                                    SlotKind.TIME -> "自己输入时间，如「明早九点」…"
                                },
                                fontSize = 14.sp,
                                color = AILauncherColors.current.Hint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            TextButton(
                onClick = { onConfirmFree(freeText) },
                modifier = Modifier.testTag("slot_free_confirm")
            ) {
                Text(
                    text = "确定",
                    fontSize = 13.sp,
                    color = AILauncherColors.current.Accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SlotOptionChip(
    text: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val accent = AILauncherColors.current.Accent
    Box(
        modifier = Modifier
            .testTag(testTag)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (selected) accent.copy(alpha = 0.22f)
                else AILauncherColors.current.Card.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                accent.copy(alpha = if (selected) 0.9f else 0.35f),
                RoundedCornerShape(99.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (selected) AILauncherColors.current.Title
            else AILauncherColors.current.Body,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}
