package com.bobot.ailauncher.ui.glass

/**
 * 玻璃质感试点批（docs/GLASS_DESIGN.md v0.2 §4 分级 / §9 试点范围）的纯逻辑层：
 * 分级判定、参数标定、背景分类、饱和矩阵——全部无 Android 依赖，可单测。
 *
 * 参数口径以设计文档为准：模糊 24–28dp 等效、饱和 130–140%、叠白 12–15%、
 * 亮边以顶部高光为主；气质基线 = 原型 A 的克制「轻霜」：强玻璃只在照片/场景
 * 背景上全开，纯纸面背景自动降轻霜（强度随背景走）。
 */

/** 材质档位：STRONG = AGSL 折射强玻璃；MEDIUM = 真模糊中玻璃；SIMULATED = 现状半透明模拟。 */
enum class GlassTier { STRONG, MEDIUM, SIMULATED }

/** 表面按文档分级申请的档位意图：Dock/气泡申请强玻璃，TODAY 卡申请中玻璃。 */
enum class GlassSurfaceKind { STRONG, MEDIUM }

/** 背景类型：TEXTURED = 照片/场景壁纸；FLAT = 纯纸面（纯色/近纯色）。 */
enum class BackdropKind { TEXTURED, FLAT }

/** 一档材质的完整参数（dp 单位在绘制时按密度换算）。 */
data class GlassParams(
    /** 等效模糊半径（dp），文档标定 24–28。 */
    val blurRadiusDp: Float,
    /** 饱和系数，文档标定 1.30–1.40。 */
    val saturation: Float,
    /** 叠色不透明度，文档标定 0.12–0.15（纸面轻霜档除外，见 paramsFor）。 */
    val tintAlpha: Float,
    /** 边缘折射位移上限（dp）；中玻璃恒为 0（只模糊不变形）。 */
    val refractionDp: Float,
    /** 镜面高光强度 0..1（方向性，以顶部为主）。 */
    val specularStrength: Float,
    /** 1dp 亮边顶部不透明度（底部渐隐到近 0）。 */
    val edgeTopAlpha: Float,
    /** 折射边缘带深度（dp）。 */
    val bandDp: Float,
)

object GlassPolicy {

    /** RenderEffect 真模糊起点（Android 12）。 */
    const val API_RENDER_EFFECT = 31

    /** RuntimeShader（AGSL）起点（Android 13）。 */
    const val API_RUNTIME_SHADER = 33

    /** 低电量阈值：电量 ≤15% 且未充电时降级（省电原则）。 */
    const val LOW_BATTERY_PERCENT = 15

    /** 设备能力给出的自然档位（未计省电/低电/可见性）。 */
    fun naturalTier(sdkInt: Int, kind: GlassSurfaceKind): GlassTier = when {
        sdkInt < API_RENDER_EFFECT -> GlassTier.SIMULATED
        kind == GlassSurfaceKind.MEDIUM -> GlassTier.MEDIUM
        sdkInt >= API_RUNTIME_SHADER -> GlassTier.STRONG
        else -> GlassTier.MEDIUM // API 31/32：强玻璃面降中玻璃（无 RuntimeShader）
    }

    /** 档位下沉一格（降级链 STRONG → MEDIUM → SIMULATED）。 */
    fun GlassTier.stepDown(): GlassTier = when (this) {
        GlassTier.STRONG -> GlassTier.MEDIUM
        GlassTier.MEDIUM -> GlassTier.SIMULATED
        GlassTier.SIMULATED -> GlassTier.SIMULATED
    }

    /**
     * 最终档位：桌面不可见（active=false）一律回模拟档（只在桌面可见时启用）；
     * 低内存设备先降一格；省电模式或低电量再降一格（文档 §4 降级链）。
     */
    fun resolveTier(
        sdkInt: Int,
        kind: GlassSurfaceKind,
        active: Boolean,
        powerSave: Boolean,
        lowBattery: Boolean,
        lowRam: Boolean,
    ): GlassTier {
        if (!active) return GlassTier.SIMULATED
        var tier = naturalTier(sdkInt, kind)
        if (lowRam) tier = tier.stepDown()
        if (powerSave || lowBattery) tier = tier.stepDown()
        return tier
    }

    /**
     * 档位 × 背景 → 参数。SIMULATED 返回 null（调用方画现状半透明色）。
     * 纸面（FLAT）= 轻霜：折射/高光大幅收敛、饱和近 1、叠白加重保可读性——
     * 纸面上没有可折射的内容，强行全开只会发灰发脏（原型 A 的教训）。
     */
    fun paramsFor(tier: GlassTier, backdrop: BackdropKind): GlassParams? = when (tier) {
        GlassTier.SIMULATED -> null
        GlassTier.STRONG -> when (backdrop) {
            BackdropKind.TEXTURED -> GlassParams(
                blurRadiusDp = 26f, saturation = 1.35f, tintAlpha = 0.13f,
                refractionDp = 12f, specularStrength = 0.85f, edgeTopAlpha = 0.55f,
                bandDp = 20f,
            )
            BackdropKind.FLAT -> GlassParams(
                blurRadiusDp = 18f, saturation = 1.08f, tintAlpha = 0.38f,
                refractionDp = 3f, specularStrength = 0.30f, edgeTopAlpha = 0.40f,
                bandDp = 14f,
            )
        }
        GlassTier.MEDIUM -> when (backdrop) {
            BackdropKind.TEXTURED -> GlassParams(
                blurRadiusDp = 26f, saturation = 1.35f, tintAlpha = 0.14f,
                refractionDp = 0f, specularStrength = 0f, edgeTopAlpha = 0.50f,
                bandDp = 0f,
            )
            BackdropKind.FLAT -> GlassParams(
                blurRadiusDp = 16f, saturation = 1.05f, tintAlpha = 0.40f,
                refractionDp = 0f, specularStrength = 0f, edgeTopAlpha = 0.35f,
                bandDp = 0f,
            )
        }
    }

    /**
     * 饱和颜色矩阵（4×5 行主序，Compose ColorMatrix 布局）。
     * 亮度权重取 0.213/0.715/0.072（与 Android ColorMatrix.setSaturation 同口径）。
     * s=1 恒等；s=0 全灰；s=1.35 即文档的「增色 135%」。
     */
    fun saturationMatrix(s: Float): FloatArray {
        val inv = 1f - s
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** 背景像素统计：返回 (亮度标准差 0..255, 平均饱和度 0..1)，输入为 ARGB 像素。 */
    fun backdropStats(pixels: IntArray): Pair<Float, Float> {
        if (pixels.isEmpty()) return 0f to 0f
        var sumY = 0.0
        var sumSqY = 0.0
        var sumSat = 0.0
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val y = 0.213 * r + 0.715 * g + 0.072 * b
            sumY += y
            sumSqY += y * y
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            sumSat += if (max == 0) 0.0 else (max - min).toDouble() / max
        }
        val n = pixels.size.toDouble()
        val mean = sumY / n
        val variance = (sumSqY / n - mean * mean).coerceAtLeast(0.0)
        return kotlin.math.sqrt(variance).toFloat() to (sumSat / n).toFloat()
    }

    /**
     * 背景分类：亮度起伏与色彩都极低 → 纯纸面（轻霜）；否则按照片/场景处理。
     * 纯色壁纸的标准差为 0；纸面罩纱渐变（顶 75%→底 45% 的同色渐变）起伏也很小，
     * 同样归 FLAT——这正是想要的：渐变纸面不该触发全开折射。
     */
    fun classifyBackdrop(luminanceStdDev: Float, saturationMean: Float): BackdropKind =
        if (luminanceStdDev < 8f && saturationMean < 0.06f) BackdropKind.FLAT else BackdropKind.TEXTURED
}
