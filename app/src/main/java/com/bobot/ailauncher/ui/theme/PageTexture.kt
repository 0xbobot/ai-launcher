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
 * 页面背景三层，都淡，以不抢列表可读性为限：
 * ① 纵向暖灰渐变 ② 顶部柔和暖光晕 ③ 极淡颗粒（均匀 ±28、平铺、5%）。
 * 主题感知（跟随深/浅切换），只做背景层——调用点把原 `.background(...)` 换成
 * 本修饰符即可，布局/内容/交互/文字颜色一律不动。
 */
@Composable
fun Modifier.pageTextureBackground(): Modifier {
    val isDark = AILauncherColors.current.isDark
    // 深色按原型值（顶 #211E1A → 底 #0A0A0B，平均亮度与原 Background #1A1815 相当）；
    // 浅色同一结构换浅暖灰系（顶略暖亮 → 底主题暖灰，平均 ≈ 原 #F4F2EE，卡片/文字对比度不动）。
    val gradientTop = if (isDark) Color(0xFF211E1A) else Color(0xFFF8F5F0)
    val gradientBottom = if (isDark) Color(0xFF0A0A0B) else Color(0xFFECE7DC)
    // 暖光晕：原型暖琥珀 (92,58,26)；深色峰值约 0.22、浅色约 0.14（浅底上暖色更显）。
    val glowAlpha = if (isDark) 0.22f else 0.14f
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
        // ③ 极淡颗粒（平铺，约 5%——与原型 effect_noise σ28 @ 5% 同量级）。
        drawRect(brush = grainBrush, alpha = 0.05f)
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
