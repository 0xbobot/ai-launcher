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
    // v0.68.50：深色自适应——所有颜色按系统深色模式切换
    val Background: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1A1815) else Color(0xFFF4F2EE)
    val Card: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF2A2723) else Color(0xFFFFFFFF)
    val Title: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFF4F2EE) else Color(0xFF1F1D1A)
    val Body: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFB8B2A8) else Color(0xFF5C5852)
    val Hint: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF6B6560) else Color(0xFFA8A29A)
    val Accent: Color = Color(0xFFC9A227)      // sparkle 金（深浅通用）
    val AccentSoft: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF3A3226) else Color(0xFFF6ECD4)
    val Success: Color = Color(0xFF4C9A52)     // 成功绿（深浅通用）
    val Divider: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF3A362F) else Color(0xFFE8E2D9)

    // ---- 玻璃拟态（v0.11）：半透明底色 + 系统壁纸透出；API 31+ 叠加 Window 真实背景模糊 ----
    val GlassCard: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0x8C1A1815) else Color(0x8CFFFFFF)
    val GlassCardStrong: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xE01A1815) else Color(0xE0FFFFFF)
    val GlassBorder: Color
        @Composable get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0x801A1815) else Color(0x80FFFFFF)
    val Grabber: Color
        @Composable get() = (if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFF4F2EE) else Color(0xFF1F1D1A)).copy(alpha = 0.28f)
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

@Composable
fun coverPaletteFor(hour: Int): CoverPalette {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    if (isDark) {
        // 深色：暖黑底 + 米白字，时段只做轻微明暗变化
        return when (hour) {
            in 6..11 -> CoverPalette(
                background = Color(0xFF1E1B16),
                title = Color(0xFFF4F2EE),
                body = Color(0xFFB8B2A8),
                divider = Color(0xFF3A362F)
            )
            in 12..17 -> CoverPalette(
                background = Color(0xFF1A1815),
                title = Color(0xFFF4F2EE),
                body = Color(0xFFB8B2A8),
                divider = Color(0xFF3A362F)
            )
            else -> CoverPalette(
                background = Color(0xFF141210),
                title = Color(0xFFF4F2EE),
                body = Color(0xFFA8A29A),
                divider = Color(0xFF2E2A24)
            )
        }
    }
    return when (hour) {
    in 6..11 -> CoverPalette(
        background = Color(0xFFF7F5F1),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFE8E2D9)
    )
    in 12..17 -> CoverPalette(
        background = Color(0xFFF4F2EE),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFE8E2D9)
    )
    else -> CoverPalette(
        background = Color(0xFFE9E0D2),
        title = Color(0xFF1F1D1A),
        body = Color(0xFF5C5852),
        divider = Color(0xFFDCD2C0)
    )
    }
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

/**
 * v0.68.50（Bob）：统一动效语言——三档时长 + 统一 easing。
 * 快：微交互反馈（按压、切换）；中：常规转场（卡片、面板）；慢：强调入场（Hero）。
 */
object MotionTokens {
    val Fast = 150
    val Medium = 250
    val Slow = 400
    val Easing = androidx.compose.animation.core.EaseOutCubic
    val SpringStiff = androidx.compose.animation.core.Spring.StiffnessMedium
    val SpringDamp = 0.85f
}
