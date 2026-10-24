package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 输入甲板组件（v0.68.101 主页重构 R-2 建，F 批改素行）：聚焦且未打字时，
 * 一体化面板上区的呈现——常用应用块 + 动态示例素行。
 *
 * 甲板只活在输入态（与重构定案一致：建议撤出静态主页）；会议不在这里
 * 复刻——简报会行是会议的唯一呈现（.93 去重契约由「甲板根本不出会议
 * 行」天然满足）。高度守槽位纪律（见 IntentEntry 的降级规则：先收示例
 * 行、再由宿主收应用量，绝不新开浮层）。
 *
 * F 批变更：R-2 的槽位 UX（胶囊内 TemplateStrip、甲板 SlotPanel、槽位
 * 芯片）已整体退役——示例行从胶囊 chips 改为无容器素行，点选 = 把整句
 * 填入输入框（见 IntentEntry.pickExample），发送一律走自由文本路径。
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

/**
 * 甲板·动态示例素行（F 批）：无背景、无描边、无圆角容器——小琥珀点 +
 * 14sp 主体色文字，横向滚动保留；点 = 把模板整句填进胶囊输入框（不直接
 * 提交、不进任何模板态）。触控高 ≥48dp（heightIn 下限保证，行本身无
 * 视觉容器不等于热区缩小）。
 *
 * v6：行尾补 16dp 末尾 padding——横滚行最后一项此前在面板边缘硬截断
 * （317 真机反馈），留白后末项可完整滚入视野；v6 后调整（Bob
 * 2026-10-24 20:31：猜你想问行删除），tagPrefix 参数随之退役，主行
 * 固定用 deck_example_*。
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
                        .background(AILauncherColors.current.Accent)
                )
                Text(
                    text = t.exampleFillText(),
                    fontSize = 14.sp,
                    color = AILauncherColors.current.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
