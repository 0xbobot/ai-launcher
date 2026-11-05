package com.bobot.ailauncher.ui.theme

import android.graphics.Bitmap
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.random.Random

/**
 * 430：已批纹理方向（Bob 2026-11-05 晚亲批“就按这个，设置页面也参考这个”；
 * 原型 texture_v1.png，生成脚本 ~/workspace/texture_prototype.py）。
 * 浅色加深（Bob 2026-11-05 晚“纹理在浅色模式下看不出来”；原型
 * texture_light_v1.png，生成脚本 ~/workspace/texture_light_prototype.py）：
 * 浅色渐变底 #ECE7DC → #E2D8C4、光晕峰值 0.14 → 0.20、颗粒 0.05 → 0.08，
 * 以“浅色下清楚可见、仍是背景”为限；深色值已批，一字不动。
 * 页面背景三层，都淡，以不抢列表可读性为限：
 * ① 纵向暖灰渐变 ② 顶部柔和暖光晕 ③ 极淡颗粒（均匀 ±28、平铺、深色 5% / 浅色 8%）。
 * 主题感知（跟随深/浅切换），只做背景层——调用点把原 `.background(...)` 换成
 * 本修饰符即可，布局/内容/交互/文字颜色一律不动。
 */
@Composable
fun Modifier.pageTextureBackground(): Modifier {
    val isDark = AILauncherColors.current.isDark
    // 深色按原型值（顶 #211E1A → 底 #0A0A0B，平均亮度与原 Background #1A1815 相当）；
    // 浅色同一结构换浅暖灰系（顶略暖亮 → 底主题暖灰，平均 ≈ 原 #F4F2EE，卡片/文字对比度不动）。
    // 浅色加深：底 #ECE7DC → #E2D8C4（两端拉开，暖灰更显；最深底上主文字对比度仍 >10:1）。
    val gradientTop = if (isDark) Color(0xFF211E1A) else Color(0xFFF8F5F0)
    val gradientBottom = if (isDark) Color(0xFF0A0A0B) else Color(0xFFE2D8C4)
    // 暖光晕：原型暖琥珀 (92,58,26)；深色峰值约 0.22（已批，不动）、浅色 0.14 → 0.20
    // （往深色靠但不过；浅底上暖色更显，顶部暖意清楚可见）。
    val glowAlpha = if (isDark) 0.22f else 0.20f
    // 颗粒：深色 0.05（已批，不动）、浅色 0.05 → 0.08（细颗粒看得见，仍是背景）。
    val grainAlpha = if (isDark) 0.05f else 0.08f
    val grainBitmap = remember { makeGrainBitmap() }
    val grainBrush = remember(grainBitmap) {
        ShaderBrush(
            android.graphics.BitmapShader(
                grainBitmap,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
            )
        )
    }
    return this.drawBehind {
        // ① 纵向暖灰渐变（不透明底，盖住旧实底色）。
        drawRect(brush = Brush.verticalGradient(listOf(gradientTop, gradientBottom)))
        // ② 顶部暖光晕（径向，中心在顶部中点，半径约 0.8 倍宽，向下淡出）。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    PageTextureGlow.copy(alpha = glowAlpha),
                    PageTextureGlow.copy(alpha = 0f),
                ),
                center = Offset(size.width * 0.5f, 0f),
                radius = size.width * 0.8f,
            )
        )
        // ③ 颗粒（平铺；深色约 5%、浅色约 8%——与原型同量级，浅色下细颗粒看得见）。
        drawRect(brush = grainBrush, alpha = grainAlpha)
    }
}

/** 暖光晕色：原型暖琥珀 (92, 58, 26)，深浅通用（只调峰值 alpha）。 */
private val PageTextureGlow = Color(0xFF5C3A1A)

/**
 * 颗粒贴图（256×256，确定性种子，灰度均匀噪声 ±28，均值 128）。
 * 一次生成、remember 复用；绘制时靠 Shader 平铺 + 5% alpha 压到极淡。
 */
private fun makeGrainBitmap(): Bitmap {
    val size = 256
    val random = Random(20261105)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        // 均匀噪声 ±28（kotlin.random.Random 没有 nextGaussian，用均匀分布；
        // 5% alpha 下与高斯无可见差别）。
        val v = (128 + random.nextInt(-28, 29)).coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
