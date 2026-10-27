package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
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
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (AILauncherColors.current.isDark) AILauncherColors.current.Accent else AILauncherColors.current.Accent.copy(alpha = 0.6f))
                )
                Text(
                    text = t.exampleFillText(),
                    fontSize = 13.sp,
                    color = if (AILauncherColors.current.isDark) AILauncherColors.current.Body else AILauncherColors.current.Body.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
