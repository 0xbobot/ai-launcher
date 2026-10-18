package com.bobot.ailauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp

/**
 * AI桌面 视觉规范（与原型图严格一致）：
 * 浅色主题 / 背景 #F4F2EE 暖灰 / 白卡片 + 柔和阴影 / 圆角 20dp /
 * 深色中文排版 / sparkle AI 点缀
 */
object AILauncherColors {
    val Background = Color(0xFFF4F2EE) // 暖灰底
    val Card = Color(0xFFFFFFFF)        // 白卡片
    val Title = Color(0xFF1F1D1A)       // 深色标题
    val Body = Color(0xFF5C5852)        // 正文灰
    val Hint = Color(0xFFA8A29A)        // 提示灰
    val Accent = Color(0xFFC9A227)      // sparkle 金
    val AccentSoft = Color(0xFFF6ECD4)   // 浅金底
    val Success = Color(0xFF4C9A52)     // 成功绿
    val Divider = Color(0xFFE8E2D9)      // 分隔线

    // ---- 玻璃拟态（v0.11）：半透明底色 + 系统壁纸透出；API 31+ 叠加 Window 真实背景模糊 ----
    val GlassCard = Color(0x8CFFFFFF)        // 通用卡片：白 55%
    val GlassCardStrong = Color(0xE0FFFFFF)  // 抽屉/全屏：白 88%（列表可读性）
    val GlassBorder = Color(0x80FFFFFF)      // 玻璃描边：白 50%
    val Grabber = Color(0xFF1F1D1A).copy(alpha = 0.28f) // 玻璃上的拖动手柄
}

/**
 * v0.68.34（Bob）：杂志封面时段配色——暖纸底不动，色温随晨/午/夜微调。
 * 晨版偏亮、午版标准、夜版压暗偏暖。一天内也有变化，封面不腻。
 */
data class CoverPalette(
    val background: Color,
    val title: Color,
    val body: Color,
    val divider: Color
)

fun coverPaletteFor(hour: Int): CoverPalette = when (hour) {
    in 6..11 -> CoverPalette( // 晨版：偏亮
        background = Color(0xFFF7F5F1),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFE8E2D9)
    )
    in 12..17 -> CoverPalette( // 午版：标准
        background = Color(0xFFF4F2EE),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFE8E2D9)
    )
    else -> CoverPalette( // 夜版：压暗偏暖
        background = Color(0xFFE9E0D2),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFDCD2C0)
    )
}

/** 壁纸上悬浮文字的投影，保证在任意壁纸上可读（配合 TextStyle(color = White, shadow = …) 使用） */
val GlassTextShadow = Shadow(
    offset = Offset(0f, 3f),
    blurRadius = 12f,
    color = Color(0x66000000)
)

private val AILauncherColorScheme = lightColorScheme(
    background = AILauncherColors.Background,
    surface = AILauncherColors.Card,
    surfaceVariant = AILauncherColors.Background,
    onBackground = AILauncherColors.Title,
    onSurface = AILauncherColors.Title,
    onSurfaceVariant = AILauncherColors.Body,
    primary = AILauncherColors.Accent,
    onPrimary = Color.White,
    secondary = AILauncherColors.Body,
    outline = AILauncherColors.Divider
)

private val AILauncherShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun AILauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AILauncherColorScheme,
        shapes = AILauncherShapes,
        content = content
    )
}
