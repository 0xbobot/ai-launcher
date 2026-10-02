package com.bobot.ailauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
}

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
