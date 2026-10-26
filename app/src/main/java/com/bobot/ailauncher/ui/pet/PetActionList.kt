package com.bobot.ailauncher.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.PetRepository.PetManualAction
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 宠物表演清单（2026-10-26 宠物交互重构，入口子项）：
 * 长按 Dock 宠物打开的非模态气泡，只有一层表演、无 2 级、无“更多”、
 * 无说话气泡 / 胶囊卡片行（胶囊行随 2 级移除一并删除——胶囊生产方
 * 在主源码里零调用方、恒为空态；要做先接生产方，不接就删）。
 *
 * 点按主入口已还给“问”（点宠物直进意图输入，见 MainScreen
 * onPetCellTap），本清单不再挂点按；TalkBack“问曜仔”从此说真话，
 * 长按提示为“长按看动作”（见 FloatingPet combinedClickable）。
 *
 * —— 动作执行与点选 UI 解耦（Bob 2026-10-26 亲定长远口径）——
 * 现阶段本清单只是验证动作是否活灵活现的工具；长远动作应服务某些
 * 交互、在交互发生时自动发生，不是用户点选指挥。本清单不另造执行
 * 路径：每一项只调用统一动作触发入口 [PetRepository.requestPetAction]，
 * 动作本身是 [PetManualAction] 可触发单元（FloatingPet 的全身动作引擎
 * 与 PullUpDock 的肩线协同都只订阅 [PetRepository.actionTick] 事件）。
 * 未来自动触发点（现在不接，只保证架构不焊死在点选指挥上）：
 * 意图结果到达时可触发 HAPPY、打招呼（回主页 GREET）时可触发 GREET、
 * 任务完成时可触发 HAPPY / STRETCH——接线时直接调 requestPetAction，
 * 不必改清单、不必为自动触发另建一套执行路径。
 * 点项后不关清单，可连试；关闭走 ✕、点外关闭层（PullUpDock 侧）、
 * 进 D3 或进输入态（状态回落）。
 *
 * 注意：2 级意图搜索行已删除，意图入口改走点宠物直进输入；
 * 仅以默认值保留——意图侧 IntentEntry.kt 仍以旧签名调用本组件（意图语境
 * 下点宠物开关清单是 perch 子项的地盘，本子项不碰 IntentEntry.kt），
 * 待意图侧清单用法统一后由合入方删除该参数；Dock 侧已不再传它。
 *
 * 呈现（2026-10-27 可爱化，Bob“圆形加形象的图标”）：
 * 旧 8 行文字改成 4×2 圆形按钮＋形象图标＋文字标签；内容与顺序与旧
 * 8 行一字相同，只换呈现。图标全部取自 material-icons-extended 真实
 * 资产（app 已依赖该库），不重画占位：打招呼＝挥手、伸懒腰＝舒展人形、
 * 歪头好奇＝问号、害羞＝内收、惊讶＝闪电、晕乎乎＝星星、开心＝笑脸、
 * 深呼吸＝气流。圆底用主题 Divider 暖灰（浅/深各一份），图标用 Title
 * 色、标签用 Body 色，浅/深色同代码路径。
 * 2026-10-27 fan-quick-services 批 rebase 适配：快捷服务不另起文字节，
 * 直接并入上面同一套圆形格（真实应用图标＋标签、自动换行，有装备时
 * 才出现），与扇形同一套 QuickActionsPrefs 数据 + launchQuickAction
 * 启动入口，不另造第二套动作层；表演 8 项的语义、顺序、触发入口一字
 * 不动。点按仍是“问”（直进意图输入）、拖拽仍是拉伸物理——快捷服务
 * 只落长按清单这一处入口。
 */
internal data class PetActionItem(
    val label: String,
    val kind: PetManualAction,
    val icon: ImageVector,
)

/** 清单内容（顺序即呈现顺序；单测锁定 8 项、顺序、标签与图标互不重复）。 */
internal val petActionItems = listOf(
    PetActionItem("打招呼", PetManualAction.GREET, Icons.Filled.WavingHand),
    PetActionItem("伸懒腰", PetManualAction.STRETCH, Icons.Filled.AccessibilityNew),
    PetActionItem("歪头好奇", PetManualAction.TILT, Icons.Filled.HelpOutline),
    PetActionItem("害羞", PetManualAction.SHY, Icons.Filled.Compress),
    PetActionItem("惊讶", PetManualAction.SURPRISED, Icons.Filled.FlashOn),
    PetActionItem("晕乎乎", PetManualAction.DIZZY, Icons.Filled.Stars),
    PetActionItem("开心", PetManualAction.HAPPY, Icons.Filled.SentimentVerySatisfied),
    PetActionItem("深呼吸", PetManualAction.DEEP_BREATH, Icons.Filled.Air),
)

@Composable
fun PetActionList(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ≤8 个不重复表演（评审 §6 口径，Bob 定稿的 8 项）：
    // 去重落点：摸头与害羞是同一套下压动画，只留害羞；打招呼 / 惊讶
    // 共用拉伸 0.6 骨架、伸懒腰 / 开心共用拉伸骨架，但 mood 与语义不同
    // （惊讶带 SURPRISED + 嘴 O、开心带 HAPPY），故两对各留两项、
    // 不再另行合并——评审砍的是纯重复项与复读项，不是共享骨架。
    // 删除：瞥视 / 巡视 / 注视（自主巡看的手动复读）、眨眼 / 转头
    // （自主行为的手动复读）、摸头（害羞的纯重复）、拖拽反应
    // （按钮模拟手势——拖拽反应归拖拽手势本身，见 FloatingPet 拖拽），
    // 以及点头 / 摇头 / 重心微移 / 晃头（同质小动作，验证清单不留）。
    val context = LocalContext.current
    val bodyColor = AILauncherColors.current.Body
    val hintColor = AILauncherColors.current.Hint
    val tailColor = AILauncherColors.current.Card

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(RoundedCornerShape(18.dp))
                .glassSurface(
                    kind = GlassSurfaceKind.STRONG,
                    cornerRadius = 18.dp,
                    tintColor = AILauncherColors.current.Card,
                    fallbackColor = AILauncherColors.current.Card
                )
        ) {
            Column {
                // 标题行 + 关闭（触控 48dp）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "曜仔动作",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = bodyColor,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pet_action_close")
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "✕", fontSize = 16.sp, color = hintColor)
                    }
                }
                // 4×2 圆形按钮格（每行 4 项，多出项自动换行；面板上限
                // 340dp 不变，8 项约 250dp 高不滚动，未来加项才滚动）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    for (rowItems in petActionItems.chunked(4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            for (item in rowItems) {
                                ActionButton(
                                    item = item,
                                    // 统一触发入口（见文件头解耦说明）：清单只触发，
                                    // 不执行、不关清单，可连试。
                                    onClick = { PetRepository.requestPetAction(item.kind) },
                                )
                            }
                        }
                    }
                    // 2026-10-27 fan-quick-services 批 rebase 适配：宠物复用
                    // 快捷服务——与扇形同一套数据（QuickActionsPrefs）+ 同一个
                    // 启动入口（launchQuickAction），不另造第二套动作层。
                    // 快捷项不另起文字节，直接并入上面同一套圆形格（真实应用
                    // 图标＋标签；表演 8 项恰占 2 整行，快捷格自然从第 3 行起，
                    // chunked(4) 自动换行）；无装备时整批不出现。
                    // Dock perch 与意图 perch 共用本组件，两处同时获得快捷服务。
                    val quickItems = remember {
                        try {
                            QuickActionsPrefs.load(context)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    for (rowChunk in quickItems.withIndex().toList().chunked(4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            for ((qi, item) in rowChunk) {
                                QuickActionButton(
                                    item = item,
                                    testTag = "pet_quick_$qi",
                                    // 与扇形段提交同一入口：快捷方式走 LauncherApps，
                                    // 主应用走包启动，打不开时同一套七仔语音兜底。
                                    onClick = { launchQuickAction(context, item) },
                                )
                            }
                        }
                    }
                }
            }
        }
        // 小尾巴朝下指向宠物（气泡在宠物上方；SpeechBubble 的尾巴朝上是
        // 因为它画在宠物下方，方向正好相反）。
        Canvas(modifier = Modifier.size(18.dp, 10.dp)) {
            val tailPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tailPath, tailColor)
        }
    }
}

/**
 * 可爱动作钮：56dp 圆底＋28dp 形象图标＋12sp 文字标签。
 * 触控热区为整格（圆＋标签＋上下 8dp，共约 56×94dp，≥48dp 下限）；
 * 图标 contentDescription 置空，TalkBack 只读下方标签，不双报。
 */
@Composable
private fun ActionButton(
    item: PetActionItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag("pet_action_" + item.kind.name.lowercase())
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AILauncherColors.current.Divider),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = AILauncherColors.current.Title,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.label,
            fontSize = 12.sp,
            color = AILauncherColors.current.Body,
            maxLines = 1,
        )
    }
}

/**
 * 快捷服务格：同款 56dp 圆底＋12sp 标签，图标用真实应用图标
 * （快捷方式走 ShortcutIconDrawable，主应用走 AppIconDrawable；
 * 取不到图标时 helper 自带 AccentSoft 兜底块）。
 * 图标 contentDescription 置空（helper 内已标装饰语义），TalkBack 只读标签。
 */
@Composable
private fun QuickActionButton(
    item: QuickActionItem,
    testTag: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .testTag(testTag)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AILauncherColors.current.Divider),
            contentAlignment = Alignment.Center,
        ) {
            if (item.shortcutId != null) {
                ShortcutIconDrawable(context, item.packageName, item.shortcutId, 36.dp)
            } else {
                val drawable = remember(item.packageName) {
                    try {
                        context.packageManager.getApplicationIcon(item.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (drawable != null) {
                    AppIconDrawable(drawable, 36.dp)
                } else {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AILauncherColors.current.AccentSoft)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.label.takeIf { it.isNotBlank() } ?: "快捷",
            fontSize = 12.sp,
            color = AILauncherColors.current.Body,
            maxLines = 1,
        )
    }
}
