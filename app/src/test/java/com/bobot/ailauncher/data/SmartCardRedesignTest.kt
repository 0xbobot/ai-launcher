package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.glass.BackdropKind
import com.bobot.ailauncher.ui.smartcard.SmartCardActionRowMinHeight
import com.bobot.ailauncher.ui.smartcard.SmartCardContentPadding
import com.bobot.ailauncher.ui.smartcard.SmartCardHeight
import com.bobot.ailauncher.ui.smartcard.SmartCardRowGap
import com.bobot.ailauncher.ui.smartcard.SmartCardStackTopGap
import com.bobot.ailauncher.ui.smartcard.SmartCardSubtitleEndInset
import com.bobot.ailauncher.ui.smartcard.SmartCardTitleRowHeight
import com.bobot.ailauncher.ui.smartcard.smartCardBorderColors
import com.bobot.ailauncher.ui.smartcard.smartCardFactAreaHeight
import com.bobot.ailauncher.ui.smartcard.smartCardTintAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片样式重设计单测（Bob 2026-11-11 批准，设计稿 smart-card-redesign.md；
 * 第十六批跟进：卡高 192dp＋正文区至少 3 行＋小标搬到标题行右上角）。
 *
 * 布局本身 JVM 测不到（Compose 排版），锁死的是数值一级：
 * ①卡高固定 192dp（8dp 网格）；②正文区至少 3 行高（算式 192－28－20－48－16
 * ＝80dp ≥ 72dp 的 3 行下限）；③小标右端内收 16dp（右缘距卡右缘 30dp，
 * 让出右上角待办数角标）；④呼吸描边渐变色（暖金→柔玫→冷青→暖金闭环，
 * 玫/青低饱和）；⑤暖灰衬底透明度（有纹理壁纸 0.55、低对比环境 0.75）；
 * ⑥叠层与 hero 的间距 16dp（8dp 网格）。
 * 正文 15sp Medium、去截断、卡内滚动、露边无字、2dp 呼吸描边、小标换位
 * 本身是 Compose 排版／绘制行为，见收据的 diff 复核＋Bob 真机验收。
 */
class SmartCardRedesignTest {

    // ---------- 卡高固定 192dp ----------

    @Test
    fun cardHeight_is192dp() {
        assertEquals(192.dp, SmartCardHeight)
    }

    @Test
    fun cardHeight_isOn8dpGrid() {
        assertEquals(0, SmartCardHeight.value.toInt() % 8)
    }

    // ---------- 正文区至少 3 行高 ----------

    @Test
    fun factArea_isAtLeastThreeLines() {
        // 实卡算式：192（卡高）－28（上下内边距 14×2）－20（标题行）
        // －48（按钮行）－16（行距 8×2）＝80dp；Body 15sp／24sp 行高，
        // 3 行下限 72dp，80dp 是 3.3 行。
        assertEquals(80.dp, smartCardFactAreaHeight())
        assertTrue(smartCardFactAreaHeight() >= 72.dp)
    }

    @Test
    fun factArea_constantsMatchLayout() {
        // 算式与布局同源的常量：内边距 14dp、标题行 20dp、按钮行 48dp、
        // 行距 8dp——布局里用的就是这组值（改一处就要改算式）。
        assertEquals(14.dp, SmartCardContentPadding)
        assertEquals(20.dp, SmartCardTitleRowHeight)
        assertEquals(48.dp, SmartCardActionRowMinHeight)
        assertEquals(8.dp, SmartCardRowGap)
    }

    // ---------- 小标搬到标题行右上角 ----------

    @Test
    fun subtitleEndInset_is16dp() {
        // 小标右端再向内收 16dp，留出右上角待办数角标的位置。
        assertEquals(16.dp, SmartCardSubtitleEndInset)
    }

    @Test
    fun subtitleRightEdge_is30dpFromCardEdge() {
        // 小标右缘距卡右缘＝14dp 内边距＋16dp 内收＝30dp；2 位数角标
        // 左缘落在卡右缘内约 20dp，留约 10dp 空。
        assertEquals(30.dp, SmartCardContentPadding + SmartCardSubtitleEndInset)
    }

    @Test
    fun subtitleEndInset_isOn8dpGrid() {
        assertEquals(0, SmartCardSubtitleEndInset.value.toInt() % 8)
    }

    // ---------- 呼吸描边渐变色 ----------

    @Test
    fun borderColors_areGoldRoseCyanGoldLoop() {
        val colors = smartCardBorderColors()
        assertEquals(4, colors.size)
        // 首尾同为 Bob 点名的暖金，绕一圈不断色。
        assertEquals(Color(0xFFD4A24E), colors[0])
        assertEquals(Color(0xFFD4A24E), colors[3])
        assertEquals(Color(0xFFD9A5A0), colors[1])
        assertEquals(Color(0xFF8FB8B5), colors[2])
    }

    @Test
    fun borderColors_roseAndCyanAreLowSaturation() {
        // 低饱和、贴浅暖灰主题：玫／青的饱和度（(max-min)/max）都低于 0.35；
        // 金是 Bob 点名的锚点，不锁饱和度。
        val colors = smartCardBorderColors()
        listOf(colors[1], colors[2]).forEach { color ->
            val r = color.red
            val g = color.green
            val b = color.blue
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            assertTrue((max - min) / max < 0.35f)
        }
    }

    // ---------- 暖灰衬底透明度 ----------

    @Test
    fun tintAlpha_texturedBackdrop_is055() {
        assertEquals(0.55f, smartCardTintAlpha(BackdropKind.TEXTURED), 0.001f)
    }

    @Test
    fun tintAlpha_flatBackdrop_is075() {
        // 低对比环境（壁纸平）回到 0.75 兜底。
        assertEquals(0.75f, smartCardTintAlpha(BackdropKind.FLAT), 0.001f)
    }

    @Test
    fun tintAlpha_noBackdropState_is075() {
        // 玻璃状态没提供（无模糊可读）走安全档 0.75。
        assertEquals(0.75f, smartCardTintAlpha(null), 0.001f)
    }

    // ---------- 叠层与 hero 的间距 ----------

    @Test
    fun stackTopGap_is16dp() {
        assertEquals(16.dp, SmartCardStackTopGap)
    }

    @Test
    fun stackTopGap_isOn8dpGrid() {
        assertEquals(0, SmartCardStackTopGap.value.toInt() % 8)
    }
}
