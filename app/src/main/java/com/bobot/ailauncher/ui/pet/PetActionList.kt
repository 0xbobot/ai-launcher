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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.PetRepository.PetManualAction
import com.bobot.ailauncher.data.WeatherState
import com.bobot.ailauncher.ui.glass.GlassSurfaceKind
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.delay

/**
 * 宠物动作清单（2026-10-25，Bob 拍板）：点宠物弹出，非模态气泡——
 * 复用宠物说话气泡的形态（强玻璃 + 18dp 圆角 + 柔和阴影），不新开模态浮层。
 * 位置由调用方定（Dock 宠物上方，尾巴朝下指向宠物）；1 级直接列出，
 * 2 级点“更多”展开，意图搜索在 2 级；1 级表演动作点后不关清单（可连试），
 * 2 级功能动作点后关清单再执行。
 *
 * 注意：说话气泡与胶囊卡片在首页此前没有渲染面（唯一调用点随 F-01 退役），
 * 本清单即为它们的渲染面——点 2 级动作后气泡内容切到对应展示，不另起面。
 */
private enum class PetListMode { LIST, SPEECH, CAPSULE }

@Composable
fun PetActionList(
    onIntentSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var level2 by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(PetListMode.LIST) }
    var speechText by remember { mutableStateOf("") }
    val meetingSoon by PetRepository.meetingSoon.collectAsState()
    val weatherDesc by WeatherState.desc.collectAsState()
    val capsule by PetRepository.capsule.collectAsState()

    // 说话展示到期自动收起（SpeechBubble 同口径时长）。
    LaunchedEffect(mode, speechText) {
        if (mode == PetListMode.SPEECH) {
            delay(speechDisplayMs(speechText))
            onDismiss()
        }
    }

    // 打字机（SpeechBubble 同款：一字 40ms 蹦出）。
    var shownChars by remember(speechText) { mutableIntStateOf(0) }
    LaunchedEffect(speechText) {
        if (mode != PetListMode.SPEECH) return@LaunchedEffect
        shownChars = 0
        for (i in 1..speechText.length) {
            delay(40)
            shownChars = i
        }
    }

    val level1 = listOf(
        "打招呼" to PetManualAction.GREET,
        "眨眼" to PetManualAction.BLINK,
        "转头" to PetManualAction.TURN,
        "点头" to PetManualAction.NOD,
        "摇头" to PetManualAction.HEAD_SHAKE,
        "伸懒腰" to PetManualAction.STRETCH,
        "歪头好奇" to PetManualAction.TILT,
        "害羞" to PetManualAction.SHY,
        "惊讶" to PetManualAction.SURPRISED,
        "晕乎乎" to PetManualAction.DIZZY,
        "开心" to PetManualAction.HAPPY,
        "巡视" to PetManualAction.PATROL,
        "瞥视" to PetManualAction.GLANCE,
        "注视" to PetManualAction.GAZE_HOLD,
        "深呼吸" to PetManualAction.DEEP_BREATH,
        "重心微移" to PetManualAction.SWAY,
        "晃头" to PetManualAction.SHAKE,
        "摸头" to PetManualAction.PETTED,
        "拖拽反应" to PetManualAction.DRAG_REACT,
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
                    when (mode) {
                        PetListMode.LIST -> {
                            if (!level2) {
                                for ((label, kind) in level1) {
                                    ActionRow(
                                        label = label,
                                        testTag = "pet_action_" + kind.name.lowercase(),
                                        onClick = { PetRepository.requestPetAction(kind) },
                                    )
                                }
                                ActionRow(
                                    label = "更多 ▾",
                                    testTag = "pet_action_more",
                                    onClick = { level2 = true },
                                )
                            } else {
                                ActionRow(
                                    label = "意图搜索",
                                    testTag = "pet_action_intent_search",
                                    onClick = onIntentSearch,
                                )
                                ActionRow(
                                    label = "说话气泡",
                                    testTag = "pet_action_speech",
                                    onClick = {
                                        speechText = when {
                                            meetingSoon != null -> "「$meetingSoon」快开始了"
                                            weatherDesc != null -> "今天天气$weatherDesc"
                                            else -> "今天有什么想做的？"
                                        }
                                        PetRepository.say(speechText, GazeTarget.TODAY_CARD)
                                        mode = PetListMode.SPEECH
                                    },
                                )
                                ActionRow(
                                    label = "胶囊卡片",
                                    testTag = "pet_action_capsule",
                                    onClick = { mode = PetListMode.CAPSULE },
                                )
                                ActionRow(
                                    label = "收起 ▴",
                                    testTag = "pet_action_less",
                                    onClick = { level2 = false },
                                )
                            }
                        }
                        PetListMode.SPEECH -> {
                            Text(
                                text = speechText.take(shownChars),
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = bodyColor,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            ActionRow(
                                label = "返回",
                                testTag = "pet_action_back",
                                onClick = { mode = PetListMode.LIST },
                            )
                        }
                        PetListMode.CAPSULE -> {
                            val c = capsule
                            if (c != null) {
                                Text(
                                    text = c.timeLabel,
                                    fontSize = 12.sp,
                                    color = hintColor,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                                )
                                Text(
                                    text = c.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bodyColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                Text(
                                    text = c.body,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    color = bodyColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            } else {
                                Text(
                                    text = "暂无新的胶囊",
                                    fontSize = 15.sp,
                                    color = bodyColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                            ActionRow(
                                label = "返回",
                                testTag = "pet_action_back",
                                onClick = { mode = PetListMode.LIST },
                            )
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
