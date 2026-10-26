package com.bobot.ailauncher.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * 路径：每一行只调用统一动作触发入口 [PetRepository.requestPetAction]，
 * 动作本身是 [PetManualAction] 可触发单元（FloatingPet 的全身动作引擎
 * 与 PullUpDock 的肩线协同都只订阅 [PetRepository.actionTick] 事件）。
 * 未来自动触发点（现在不接，只保证架构不焊死在点选指挥上）：
 * 意图结果到达时可触发 HAPPY、打招呼（回主页 GREET）时可触发 GREET、
 * 任务完成时可触发 HAPPY / STRETCH——接线时直接调 requestPetAction，
 * 不必改清单、不必为自动触发另建一套执行路径。
 * 点行后不关清单，可连试；关闭走 ✕、点外关闭层（PullUpDock 侧）、
 * 进 D3 或进输入态（状态回落）。
 *
 * 注意：2 级意图搜索行已删除，意图入口改走点宠物直进输入；
 * 仅以默认值保留——意图侧 IntentEntry.kt 仍以旧签名调用本组件（意图语境
 * 下点宠物开关清单是 perch 子项的地盘，本子项不碰 IntentEntry.kt），
 * 待意图侧清单用法统一后由合入方删除该参数；Dock 侧已不再传它。
 */
@Composable
fun PetActionList(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ≤8 个不重复表演（评审 §6 口径，Bob 定稿的 8 行）：
    // 去重落点：摸头与害羞是同一套下压动画，只留害羞；打招呼 / 惊讶
    // 共用拉伸 0.6 骨架、伸懒腰 / 开心共用拉伸骨架，但 mood 与语义不同
    // （惊讶带 SURPRISED + 嘴 O、开心带 HAPPY），故两对各留两行、
    // 不再另行合并——评审砍的是纯重复行与复读行，不是共享骨架。
    // 删除：瞥视 / 巡视 / 注视（自主巡看的手动复读）、眨眼 / 转头
    // （自主行为的手动复读）、摸头（害羞的纯重复）、拖拽反应
    // （按钮模拟手势——拖拽反应归拖拽手势本身，见 FloatingPet 拖拽），
    // 以及点头 / 摇头 / 重心微移 / 晃头（同质小动作，验证清单不留）。
    val actions = listOf(
        "打招呼" to PetManualAction.GREET,
        "伸懒腰" to PetManualAction.STRETCH,
        "歪头好奇" to PetManualAction.TILT,
        "害羞" to PetManualAction.SHY,
        "惊讶" to PetManualAction.SURPRISED,
        "晕乎乎" to PetManualAction.DIZZY,
        "开心" to PetManualAction.HAPPY,
        "深呼吸" to PetManualAction.DEEP_BREATH,
    )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    for ((label, kind) in actions) {
                        ActionRow(
                            label = label,
                            testTag = "pet_action_" + kind.name.lowercase(),
                            // 统一触发入口（见文件头解耦说明）：清单只触发，
                            // 不执行、不关清单，可连试。
                            onClick = { PetRepository.requestPetAction(kind) },
                        )
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

@Composable
private fun ActionRow(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = AILauncherColors.current.Body,
        )
    }
}
