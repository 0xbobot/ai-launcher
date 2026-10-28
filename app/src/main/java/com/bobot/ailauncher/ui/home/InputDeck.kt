package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 输入甲板组件（v0.68.101 主页重构 R-2 建，F 批改素行）：聚焦且未打字时，
 * 一体化面板内的呈现——动态示例素行（能力问题，做事型推荐）。
 *
 * 2026-10-25 意图卡片统一：甲板常用应用块（DeckAppsBlock）整段删除——
 * Dock 里已有应用推荐，意图初始状态再推一遍多为重复（Bob 原话；
 * 2026-10-22「同一信息只在一处呈现」原则）。甲板只活在输入态（与重构
 * 定案一致：建议撤出静态主页）；会议不在这里复刻——简报会行是会议的
 * 唯一呈现（.93 去重契约由「甲板根本不出会议行」天然满足）。
 *
 * F 批变更：R-2 的槽位 UX（胶囊内 TemplateStrip、甲板 SlotPanel、槽位
 * 芯片）已整体退役——示例行从胶囊 chips 改为无容器素行，点选 = 把整句
 * 填入输入框（见 IntentEntry.pickExample），发送一律走自由文本路径。
 */

/**
 * 甲板·动态示例素行（F 批）：无背景、无描边、无圆角容器——小琥珀点 +
 * 13sp 弱化文字，横向滚动保留；点 = 把模板整句填进胶囊输入框（不直接
 * 提交、不进任何模板态）。触控高 ≥48dp（heightIn 下限保证，行本身无
 * 视觉容器不等于热区缩小）。
 *
 * v6：行尾补 16dp 末尾 padding——横滚行最后一项此前在面板边缘硬截断
 * （317 真机反馈），留白后末项可完整滚入视野；v6 后调整（Bob
 * 2026-10-24 20:31：猜你想问行删除），tagPrefix 参数随之退役，主行
 * 固定用 deck_example_*。
 *
 * 2026-10-27：卡片角 ✕ 已移到底部收起，行尾留白回到 v6 的 16dp
 * （末项完整滚入视野，不再为 ✕ 覆盖区留禁区）。
 * 2026-10-27 暗黑可读：深色下示例文字与琥珀点不再降 alpha（玻璃＋
 * 壁纸上发虚），浅色仍 0.65／0.6。
 *
 * 2026-10-25 示例弱化（Bob 23:18：推荐问法还是需要的、只是弱化一点
 * 不要抢戏）：行保留原位原功能，只降视觉权重——字号 14sp→13sp、
 * 文字 Body 降 alpha 至 0.65、琥珀点降 alpha 至 0.6；不删行、不改结构、
 * 不换文案，应用推荐行（346 已删）不恢复。
 */
@Composable
fun DeckExamplesRow(
    examples: List<SlotTemplate>,
    onPick: (SlotTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    // 2026-10-28 意图页设计评审第 1 轮（P1-1／P1-2／P1-5／P1-7／P1-8／P2-2）：
    // 衬底降到 0.50 后深色 Body 在亮蓝壁纸取样上复测仅 3.76:1、不过 4.5，
    // 故深色示例文字改 Title（同衬底上 7.08:1）；浅色文字 alpha 0.65→0.90、
    // 琥珀点 alpha 0.6→1.0（浅色衬底上推导 5.28:1）；琥珀点径 6dp→8dp；
    // 2026-10-28 意图页设计评审第 2 轮 P1-8 重开：浅色实拍蜜桃色底上
    // 0.90 推导不成立（实测 3.76～3.93:1），浅色示例文字改全 alpha Body
    // （仍弱于 Title，弱化层级不动），与浅色衬底 0.75 合起来约 5.66:1。
    // 行内边 12dp→32dp（点轴 48dp、文字轴 62dp，与胶囊／结果轴一线）；
    // 项距 20dp→16dp；行右缘加 24dp 渐隐，行尾 16dp Spacer 保留。
    // 2026-10-27 设计精致化 P0-1：推荐行文字坐稳局部底色上（不只给字加亮）——
    // 素行本身无容器，但整行套一层与胶囊同色系的弱衬底：玻璃＋花纹壁纸下
    // 对比度不再随壁纸走；圆角裁剪、卡片边缘仍透玻璃，整卡不改黑。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(intentContentScrim())
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        examples.forEach { t ->
            Row(
                modifier = Modifier
                    .testTag("deck_example_${t.id}")
                    .heightIn(min = 48.dp)
                    .clickable { onPick(t) }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AILauncherColors.current.Accent)
                )
                Text(
                    text = t.exampleFillText(),
                    fontSize = 13.sp,
                    color = if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
    // 右缘渐隐（不拦截点按：本 Box 无 clickable，事件落到下层横滚行）：
    // 24dp 内从透明过渡到卡片实色，溢出项呈现为“字＋渐隐”而非半字硬切；
    // 内容未溢出时渐隐区只盖空衬底（同色系，无可见变化）。
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

/**
 * 意图卡片内容区弱衬底（2026-10-27 设计精致化 P0-1）：推荐行、结果条、
 * 查找态文字共用的局部底色——卡片玻璃＋壁纸透过来时文字对比度不再随壁纸
 * 走；只盖文字区、卡片边缘仍透玻璃，整卡不改黑。
 * 2026-10-28 意图页设计评审第 1 轮 P1-1：三层深色条重量倒置，衬底
 * 深色 0.65／浅色 0.60 → 两档同值 0.50（胶囊平底另取 0.55，高一档）；
 * 衬底降后深色 Body 不过线，故同衬底上的深色正文一律改 Title（见
 * DeckExamplesRow、结果标签、SlotStatusLine），不把衬底加回去。
 * 2026-10-28 意图页设计评审第 2 轮 P1-8 重开：浅色实拍（蜜桃色壁纸）
 * 上示例 3.76～3.93:1＜4.5，第 1 轮推导 5.28:1 的近白底前提不成立；
 * 浅色衬底 0.50→0.75（深色档不动，仍 0.50——深色 Title 在其上已 7.08:1），
 * 并与浅色示例文字全 alpha（见 DeckExamplesRow）合起来复测：同类蜜桃色
 * 底上示例 Body 约 5.66:1、结果标签／状态行 Body 约 5.66:1，均过 4.5。
 */
@Composable
internal fun intentContentScrim(): Color =
    if (AILauncherColors.current.isDark) AILauncherColors.current.Card.copy(alpha = 0.50f)
    else AILauncherColors.current.Card.copy(alpha = 0.75f)

