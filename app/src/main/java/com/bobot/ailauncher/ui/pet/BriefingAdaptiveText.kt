package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.ui.glass.GlassPolicy

/**
 * 简报区自适应字色决策（Bob 2026-10-25 21:17 拍板：自适应字色、去纱）。
 *
 * 原则：简报区不再靠罩纱保可读性，改靠字色自适应——纱平时不可见，只在
 * 对比度不够／读不到壁纸时回退 347 纱。决策是纯函数（无 Android 依赖，
 * 可单测）；字色映射在调用方（PetZone）做，Compose Color 不进本文件。
 *
 * 亮度只看简报区那一带：调用方把 PetZone 高度对应的顶部带传进来，不用
 * 整张壁纸平均（整张平均会被画面下部的大面积区域带偏）。
 *
 * 迟滞带说明：两个字色档的严格达标区是不连通的——白字要求背景均值亮度
 * ≤ 约 0.157（对比度 4.5），墨字要求 ≥ 约 0.234，中间是天然死区；再加
 * 一级宽松保持阈值（已选档在对比度 3.5 以上就保持），形成施密特触发器：
 * 亮度在阈值附近徘徊时，决策只会在“当前档”和“回退纱”之间稳态切换，
 * 绝不来回翻色。死区内无历史时直接回退纱，不猜字色。
 *
 * 系统 WallpaperColors 的 hint 在此决策中没有可用位置：hint 有意义的
 * 平局区（两档对比度接近）正好落在上述死区内——两档都达不到 4.5，
 * 按对比度兜底规则必须回退纱，hint 不能推翻对比度保证。故 WallpaperColors
 * 只作壁纸变化的监听信号（见 SystemWallpaperLayer），不进颜色决策。
 */
enum class BriefingTextMode { DARK_TEXT, LIGHT_TEXT, FALLBACK_SCRIM }

object BriefingAdaptiveText {

    /** 深色字档标题（与现行浅色 CoverPalette 标题同值）。 */
    const val DARK_TITLE_ARGB: Long = 0xFF1F1D1A

    /** 深色字档正文／分隔线（与现行浅色 CoverPalette 同值）。 */
    const val DARK_BODY_ARGB: Long = 0xFF5C5852
    const val DARK_DIVIDER_ARGB: Long = 0xFFE8E2D9

    /** 深铜金：亮底上刊头金色的替代——白底对比度约 5.3:1，仍是金色系保品牌感。 */
    const val DARK_ACCENT_ARGB: Long = 0xFF7A5F0F

    /** 浅色字档标题（与现行深色 CoverPalette 标题同值）。 */
    const val LIGHT_TITLE_ARGB: Long = 0xFFF4F2EE

    /** 浅色字档正文／分隔线（与现行深色 CoverPalette 同值）。 */
    const val LIGHT_BODY_ARGB: Long = 0xFFB8B2A8
    const val LIGHT_DIVIDER_ARGB: Long = 0xFF3A362F

    /** 浅色字档刊头金（与现行 Accent 同值，深浅通用）。 */
    const val LIGHT_ACCENT_ARGB: Long = 0xFFC9A227

    /** 主对比度门槛：标题字 vs 简报区背景均值（WCAG 正文级）。 */
    const val CONTRAST_MEAN_MIN = 4.5

    /** 带内底线：标题字 vs 每一带的均值——带内明暗悬殊（如地平线横切简报区）
     * 时均值会撒谎，底线拦住它；过不了底线的档直接出局。 */
    const val CONTRAST_BAND_MIN = 3.0

    /** 已选档的宽松保持门槛（严格 4.5 的下一档）：高于它就不翻色、不回退。 */
    const val CONTRAST_KEEP_MIN = 3.5

    /**
     * 按简报区高度占比取顶部带：占比 0 → 空（= 未测到，回退纱）；
     * 占比覆盖不足一带时至少取首带；超界钳制到全部带。
     */
    fun bandsForZone(
        bands: List<GlassPolicy.BandLuminance>,
        fraction: Float,
    ): List<GlassPolicy.BandLuminance> {
        if (bands.isEmpty() || fraction <= 0f) return emptyList()
        val count = (fraction * bands.size).toInt().coerceIn(1, bands.size)
        return bands.take(count)
    }

    /**
     * 决策：previous 为上一次决策（null = 无历史）。
     * - 无带数据 → 回退纱。
     * - 已选档仍满足宽松保持（均值 ≥ 3.5 且各带 ≥ 3.0）→ 保持，不翻色。
     * - 否则严格决策：均值 ≥ 4.5 且各带 ≥ 3.0 的档中取对比度更高者；
     *   无档达标 → 回退纱。
     */
    fun decide(
        bands: List<GlassPolicy.BandLuminance>,
        previous: BriefingTextMode?,
    ): BriefingTextMode {
        if (bands.isEmpty()) return BriefingTextMode.FALLBACK_SCRIM
        val mean = bands.map { it.mean.toDouble() }.average()
        val darkLum = GlassPolicy.relativeLuminance(DARK_TITLE_ARGB.toInt())
        val lightLum = GlassPolicy.relativeLuminance(LIGHT_TITLE_ARGB.toInt())
        val darkMean = GlassPolicy.contrastRatio(darkLum, mean)
        val lightMean = GlassPolicy.contrastRatio(lightLum, mean)
        fun bandOk(lum: Double) =
            bands.all { GlassPolicy.contrastRatio(lum, it.mean.toDouble()) >= CONTRAST_BAND_MIN }
        val darkOk = darkMean >= CONTRAST_MEAN_MIN && bandOk(darkLum)
        val lightOk = lightMean >= CONTRAST_MEAN_MIN && bandOk(lightLum)

        // 宽松保持：已选档还没跌破保持线就留住（施密特触发器的回程）。
        if (previous != null && previous != BriefingTextMode.FALLBACK_SCRIM) {
            val prevMean = if (previous == BriefingTextMode.DARK_TEXT) darkMean else lightMean
            val prevLum = if (previous == BriefingTextMode.DARK_TEXT) darkLum else lightLum
            if (prevMean >= CONTRAST_KEEP_MIN && bandOk(prevLum)) return previous
        }

        if (!darkOk && !lightOk) return BriefingTextMode.FALLBACK_SCRIM
        return if (darkOk && lightOk) {
            if (darkMean >= lightMean) BriefingTextMode.DARK_TEXT else BriefingTextMode.LIGHT_TEXT
        } else if (darkOk) {
            BriefingTextMode.DARK_TEXT
        } else {
            BriefingTextMode.LIGHT_TEXT
        }
    }
}
