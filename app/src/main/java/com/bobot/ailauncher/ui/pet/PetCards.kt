package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.glass.glassSurface
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import kotlinx.coroutines.delay

/**
 * 体检批 C 从 PetZone.kt 拆出：七仔说话气泡。
 *
 * v0.68.96（F-01 退役）：同文件的宠物呈现卡（PetPresentedCard 及其操作行）在
 * 本批前已无任何调用点，随退役一并删除。本文件只保留 SpeechBubble——它在
 * .94 玻璃试点批刚换上强玻璃，是七仔说话的统一呈现面；其唯一调用点位于
 * 已删除的非封面分支，当前暂无调用方，是否在封面形态补回气泡属产品决策，
 * 留待后续拍板，不在本批删除范围。
 */

/**
 * v0.47.0：七仔说话气泡——Mii 风（干净圆润白气泡+小尾巴）+ 游戏化（弹性弹出+打字机）。
 * 七仔有什么话要说，都走这里统一呈现。
 */
@Composable
internal fun SpeechBubble(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    // v0.64.5：编辑模式下点气泡退出编辑模式
    onClick: (() -> Unit)? = null
) {
    // 打字机：一字一字蹦出来（RPG 对话感）
    var shownChars by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shownChars = 0
        for (i in 1..text.length) {
            delay(40)
            shownChars = i
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f)
        ) + fadeIn(),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // v0.65.4：小尾巴指向上方七仔（气泡在七仔下方）
            // 玻璃试点批：尾巴填充与气泡体同色（浅色下同为白，与原 Color.White 一致；
            // 颜色在组合作用域先读好，Canvas 绘制 lambda 内不能读 CompositionLocal）
            val tailColor = AILauncherColors.current.Card
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(18.dp, 10.dp)
            ) {
                val tailPath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, 0f)
                    close()
                }
                drawPath(tailPath, tailColor)
            }
            // 玻璃试点批：气泡体改强玻璃——Box + 手动柔和阴影（与 TODAY 卡同手法，
            // v0.63.1 口径不用 Material3 Card）；模拟档 fallback 回原 Card 实色。
            // 形状（18dp）、最大宽 240dp、内边距与文字一律不动，只换材质。
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .glassSurface(
                        kind = com.bobot.ailauncher.ui.glass.GlassSurfaceKind.STRONG,
                        cornerRadius = 18.dp,
                        tintColor = AILauncherColors.current.Card,
                        fallbackColor = AILauncherColors.current.Card
                    )
                    .then(if (onClick != null) Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() } else Modifier)
            ) {
                Text(
                    text = text.take(shownChars),
                    fontSize = AILauncherType.Footnote.fontSize,
                    lineHeight = 18.sp,
                    color = Color(0xFF3A3A3A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
