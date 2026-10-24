package com.bobot.ailauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
    /**
     * 一套调色板（浅/深各一份实例）。
     * 批B ③：原全局 `var isDark`（组合期写全局 State）已废除——调色板改由
     * [LocalAILauncherColors] 在组合根（[AILauncherTheme]）按系统主题提供，
     * 全部读点走 [current]；颜色值与原 getter 逐一对应的字面值，零变化。
     */
    class Palette(val isDark: Boolean) {
        val Background: Color = if (isDark) Color(0xFF1A1815) else Color(0xFFF4F2EE)
        val Card: Color = if (isDark) Color(0xFF2A2723) else Color(0xFFFFFFFF)
        val Title: Color = if (isDark) Color(0xFFF4F2EE) else Color(0xFF1F1D1A)
        val Body: Color = if (isDark) Color(0xFFB8B2A8) else Color(0xFF5C5852)
        // 应用中心升级：浅色 Hint 加深（暖白底上对比度约 2.2:1 → 约 3.2:1），
        // 12sp 辅助文案可读；深色沿旧
        val Hint: Color = if (isDark) Color(0xFF6B6560) else Color(0xFF8A8378)
        val Accent: Color = Color(0xFFC9A227)      // sparkle 金（深浅通用）
        val AccentSoft: Color = if (isDark) Color(0xFF3A3226) else Color(0xFFF6ECD4)
        val Success: Color = Color(0xFF4C9A52)     // 成功绿（深浅通用）
        val Divider: Color = if (isDark) Color(0xFF3A362F) else Color(0xFFE8E2D9)

        // ---- 玻璃拟态（v0.11）：半透明底色 + 系统壁纸透出；API 31+ 叠加 Window 真实背景模糊 ----
        val GlassCard: Color = if (isDark) Color(0x8C1A1815) else Color(0x8CFFFFFF)
        val GlassCardStrong: Color = if (isDark) Color(0xE01A1815) else Color(0xE0FFFFFF)
        val GlassBorder: Color = if (isDark) Color(0x801A1815) else Color(0x80FFFFFF)
        val Grabber: Color = (if (isDark) Color(0xFFF4F2EE) else Color(0xFF1F1D1A)).copy(alpha = 0.28f)
    }

    val Light = Palette(isDark = false)
    val Dark = Palette(isDark = true)

    fun forDark(dark: Boolean): Palette = if (dark) Dark else Light

    /** 当前组合的调色板——只能在 @Composable 上下文读取（经 [LocalAILauncherColors]）。 */
    val current: Palette
        @Composable
        @ReadOnlyComposable
        get() = LocalAILauncherColors.current
}

/**
 * 批B ③：调色板 CompositionLocal。[AILauncherTheme] 在组合根提供；
 * 无提供者时回退浅色（与原全局 var 初始值 false 一致，如独立 Activity 冷启）。
 * 主题切换是整树级事件，故用 staticCompositionLocalOf（全量失效，语义与原全局 State 一致）。
 */
val LocalAILauncherColors: androidx.compose.runtime.ProvidableCompositionLocal<AILauncherColors.Palette> =
    staticCompositionLocalOf { AILauncherColors.Light }

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

fun coverPaletteFor(hour: Int, isDark: Boolean): CoverPalette {
    // v0.68.50：深色另走一套；批B ③：isDark 由调用方从 CompositionLocal 读后传入
    // （本函数是非组合纯函数，不再读全局状态）
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

// 批B ③：浅色 scheme 直接取 Light 调色板的字面值（与原 getter 在 isDark=false 时的
// 取值逐一相同；顶层 val 初始化时无组合上下文，不能走 CompositionLocal）。
private val AILauncherColorScheme = lightColorScheme(
    background = AILauncherColors.Light.Background,
    surface = AILauncherColors.Light.Card,
    surfaceVariant = AILauncherColors.Light.Background,
    onBackground = AILauncherColors.Light.Title,
    onSurface = AILauncherColors.Light.Title,
    onSurfaceVariant = AILauncherColors.Light.Body,
    primary = AILauncherColors.Light.Accent,
    onPrimary = Color.White,
    secondary = AILauncherColors.Light.Body,
    outline = AILauncherColors.Light.Divider
)

// R1（2026-10-20）：Material 暗色 scheme——取 AILauncherColors 的暗色 token 值。
// 注意不能在这里读 AILauncherColors 的 getter：scheme 是顶层 val，初始化时 isDark
// 还没被置 true，getter 会取到浅色值并被冻结。故直接写暗色字面值，与 token 保持一致：
// Background #1A1815 / Card #2A2723 / Title #F4F2EE / Body #B8B2A8 / Divider #3A362F。
// primary 沿用暖金 Accent #C9A227；金色底用深色字（#1F1D1A）保证 onPrimary 可读。
private val AILauncherDarkColorScheme = darkColorScheme(
    background = Color(0xFF1A1815),
    surface = Color(0xFF2A2723),
    surfaceVariant = Color(0xFF1A1815),
    onBackground = Color(0xFFF4F2EE),
    onSurface = Color(0xFFF4F2EE),
    onSurfaceVariant = Color(0xFFB8B2A8),
    primary = Color(0xFFC9A227),
    onPrimary = Color(0xFF1F1D1A),
    secondary = Color(0xFFB8B2A8),
    outline = Color(0xFF3A362F)
)

private val AILauncherShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun AILauncherTheme(content: @Composable () -> Unit) {
    // R1：按系统深色选 Material scheme；批B ③：调色板改经 CompositionLocal 在此提供
    // （原 Theme/MainScreen 两处组合期写全局 isDark 一并取消）
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalAILauncherColors provides AILauncherColors.forDark(dark)) {
        MaterialTheme(
            colorScheme = if (dark) AILauncherDarkColorScheme else AILauncherColorScheme,
            shapes = AILauncherShapes,
            content = content
        )
    }
}

/**
 * v0.68.50（Bob）：统一动效语言——三档时长 + 统一 easing。
 * 快：微交互反馈（按压、切换）；中：常规转场（卡片、面板）；慢：强调入场（Hero）。
 */
object MotionTokens {
    val Fast = 150
    // 应用中心升级：Short 120ms——波浪等跟手反馈统一走此档
    val Short = 120
    val Medium = 250
    val Slow = 400
    val Easing = androidx.compose.animation.core.EaseOutCubic
    val SpringStiff = androidx.compose.animation.core.Spring.StiffnessMedium
    val SpringDamp = 0.85f
}

/**
 * R3-D：系统减动效——读 ANIMATOR_DURATION_SCALE（无障碍"移除动画"开启或
 * 开发者选项把动画缩放关到 0 时为 0）。Compose 动画默认不读这个系统设置，
 * 装饰性循环动画（宠物浮动/呼吸/摆动等）须经此判断后自行静止；
 * 状态切换类动画（弹簧/淡入）不在此列——状态不依赖动画完成事件，关掉循环即可。
 * 注意：CI 模拟器以 disable-animations 运行，本判断在冒烟里恒为 true。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * v0.68.59（Bob）：六档字阶——严格收敛，全 App 只用这 6 档。
 * Caption 12 / Body 14 / Subtitle 16 / Title 20 / Headline 32 / Display 104
 */
object TypeScale {
    val Caption = 12
    val Body = 14
    val Subtitle = 16
    val Title = 20
    val Headline = 32
    val Display = 104
}
