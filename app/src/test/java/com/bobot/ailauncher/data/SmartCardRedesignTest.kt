package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.glass.BackdropKind
import com.bobot.ailauncher.ui.smartcard.SmartCardFactMaxHeight
import com.bobot.ailauncher.ui.smartcard.SmartCardHeight
import com.bobot.ailauncher.ui.smartcard.SmartCardStackTopGap
import com.bobot.ailauncher.ui.smartcard.smartCardBorderColors
import com.bobot.ailauncher.ui.smartcard.smartCardTintAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片样式重设计单测（Bob 2026-11-11 批准，设计稿 smart-card-redesign.md）。
 *
 * 布局本身 JVM 测不到（Compose 排版），锁死的是数值一级：
 * ①卡高固定 168dp（8dp 网格）；②正文区封顶 3 行高（Body 15sp/24sp 行高
 * ×3＝72dp）；③呼吸描边渐变色（暖金→柔玫→冷青→暖金闭环，玫/青低饱和）；
 * ④暖灰衬底透明度（有纹理壁纸 0.55、低对比环境 0.75）；⑤叠层与 hero 的
 * 间距 16dp（8dp 网格）。
 * 正文 15sp Medium、去截断、卡内滚动、露边无字、2dp 呼吸描边本身是
 * Compose 排版／绘制行为，见收据的 diff 复核＋Bob 真机验收。
 */
class SmartCardRedesignTest {

    // ---------- 卡高固定 168dp ----------

    @Test
    fun cardHeight_is168dp() {
        assertEquals(168.dp, SmartCardHeight)
    }

    @Test
    fun cardHeight_isOn8dpGrid() {
        assertEquals(0, SmartCardHeight.value.toInt() % 8)
    }

    // ---------- 正文区封顶 3 行高 ----------

    @Test
    fun factMaxHeight_isThreeLinesOfBody() {
        // Body 15sp／行高 24sp × 3 行＝72dp；超了在卡内滚动，不是截断。
        assertEquals(72.dp, SmartCardFactMaxHeight)
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
