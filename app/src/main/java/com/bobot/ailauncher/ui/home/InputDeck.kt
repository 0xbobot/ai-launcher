package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier,
    // 意图页优化（2026-10-30，Bob 已批 §二 A3/B1）：
    // - 行首固定用途标签（不跟横滚走，永远可见）：用户一眼看懂这行是
    //   “可以这样问的例子”，不是系统猜他想问什么。标签长在行内、
    //   与示例同一行同一槽位——不是已删的“猜你想问”独立标签行，
    //   不恢复它；点示例仍是整句填入输入框、不直接发送（pickExample
    //   原口径不动），匹配逻辑不动（仍是常驻发现行）。
    // - dimmed（B1）：本地结果在场时整行弱化——胶囊与结果是主角，
    //   示例不抢空间；只降 alpha，不隐藏、不压高、不动 48dp 热区。
    dimmed: Boolean = false
) {
    // 弱化只降视觉权重：示例是发现行、不承载关键信息（C2 口径），
    // 结果在场时它本就不是主角；无结果时全 alpha，与改前一致。
    val exampleAlpha = if (dimmed) 0.55f else 1f
    val exampleTextColor = (if (AILauncherColors.current.isDark) AILauncherColors.current.Title else AILauncherColors.current.Body).copy(alpha = exampleAlpha)
    val exampleDotColor = exampleTextColor
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
    // 意图页优化（2026-10-30，Bob 已批 §二 A3）：行首用途标签固定在
    // 横滚区之外——标签不滚动、不消失，示例在它右侧横滚；行高仍由
    // 48dp 项高决定，标签只居中对齐、不撑高；渐隐仍盖横滚区右缘
    // （改对齐到内层横滚盒，位置与改前一致），终点色、高度一字不动。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "可以这样问：",
            fontSize = 13.sp,
            color = exampleTextColor,
            maxLines = 1,
            // 对齐修复（2026-10-30，385 实拍）：标签左缘须与胶囊文字／
            // 结果标题同一左轴（面板内容左缘＋46dp）；385 时为 32dp，
            // 实拍量测标签左缘比轴线左偏约 14dp。
            modifier = Modifier.padding(start = 46.dp, end = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            // 2026-10-29 意图页设计评审第 4 轮 P1-5（示例行内局部几何，只动间距与渐隐宽度）：
            // 383 实拍第三项圆点恰落在渐隐起点上、露出仅约 1–4dp，读成硬切。
            // 项距 16dp→8dp 使第三项左移 16dp（两道间隙），渐隐宽 24dp→12dp 使
            // 渐隐起点右移 12dp；合计第三项在渐隐起点前的露出从约 4dp 增至约 32dp
            // （360dp 屏、实拍前两项文案量测：圆点＋首字完整在渐隐起点左侧，
            // 第二字起进渐隐透明段，仍可辨认；行高、热区、终点色一字不动）。
            // 本批只把行左内边 46dp 让给固定标签（横滚区左内边归零；385
            // 时为 32dp，实拍量测标签左缘比胶囊／结果轴线左偏约 14dp，本批
            // 对齐到 46dp 轴线），项距、渐隐宽、行尾留白一字不动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        // 2026-10-29 意图页设计评审第 4 轮 P1-2（只改本圆点颜色值）：
                        // 琥珀点改与示例字同色（深色 Title／浅色 Body），不再用金色；
                        // 提交箭头保留唯一金色 accent（IntentEntry.kt 提交圆盘不动）。
                        // 本批：圆点随 dimmed 同降 alpha（与字同色同 alpha）。
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(exampleDotColor)
                        )
                        Text(
                            text = t.exampleFillText(),
                            fontSize = 13.sp,
                            color = exampleTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            // 右缘渐隐（不拦截点按：本 Box 无 clickable，事件落到下层横滚行）：
            // 第 4 轮 P1-5：渐隐宽 24dp→12dp（起点右移 12dp，与项距收窄合计
            // 第三项露出约 32dp）；终点色仍为 Card 实色，不动；高度仍为 48dp
            // 定高，严禁改回 fillMaxHeight／fillMaxSize（第 3 轮根因禁令仍有效）。
            // 12dp 内从透明过渡到卡片实色，溢出项呈现为“字＋渐隐”而非半字硬切；
            // 内容未溢出时渐隐区只盖空衬底（同色系，无可见变化）。
            // 2026-10-29 意图页设计评审第 3 轮（撑高根因修复）：此处此前用
            // fillMaxHeight，父 Box 经面板 Column、PetZone 槽位一路向上吃到窗口高
            // 约束，渐隐盒把行高撑到窗口高、面板跟随撑满（382 实拍面板 736dp、
            // 面板顶压到 32dp、宠物截断 85% 皆由此来）。改行高约束：行高恒为
            // 48dp（项 heightIn 下限 48dp、单行 13sp 字高在任何字号档下均小于
            // 48dp），渐隐盒定高 48dp 即与行同高，不再向上传播窗口高约束；
            // 此后任何子树改动都不得再给渐隐盒加 fillMaxHeight／fillMaxSize。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(12.dp)
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, AILauncherColors.current.Card)
                        )
                    )
            )
        }
    }
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

