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
    /** 饱和系数。试点标定 1.35；v0.68.96 按 Bob 真机反馈上调到 1.45（见 paramsFor）。 */
    val saturation: Float,
    /** 叠色不透明度。试点标定 0.12–0.15；v0.68.96 照片档降到 0.07 量级（见 paramsFor）。 */
    val tintAlpha: Float,
    /** 边缘折射位移上限（dp）；中玻璃恒为 0（只模糊不变形）。 */
    val refractionDp: Float,
    /** 镜面高光强度 0..1（方向性，以顶部为主）。 */
    val specularStrength: Float,
    /** 1dp 亮边顶部不透明度（底部渐隐到近 0）。 */
    val edgeTopAlpha: Float,
    /** 折射边缘带深度（dp）。 */
    val bandDp: Float,
    /** 外侧暗描边不透明度（v0.68.96 新增）：亮背景上给玻璃一个可辨的切面轮廓。 */
    val rimAlpha: Float,
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
     *
     * v0.68.96 照片档再标定（Bob .94 真机反馈「完全没有玻璃的感觉」）：试点参数
     * 四样里只有模糊在干活——叠白 0.13 把通透感吃掉、饱和 1.35 对雾感远山不够、
     * 折射带 20dp 太窄太弱、亮边在亮背景上立不住。本次：叠白降到 0.07 量级、
     * 饱和升到 1.45、折射位移 12→16dp 且带宽 20→30dp、顶边亮度 0.55→0.75，
     * 并新增外侧暗描边（rimAlpha）在亮背景上衬出切面轮廓。FLAT 轻霜档不动。
     */
    fun paramsFor(tier: GlassTier, backdrop: BackdropKind): GlassParams? = when (tier) {
        GlassTier.SIMULATED -> null
        GlassTier.STRONG -> when (backdrop) {
            BackdropKind.TEXTURED -> GlassParams(
                blurRadiusDp = 26f, saturation = 1.45f, tintAlpha = 0.07f,
                refractionDp = 16f, specularStrength = 0.95f, edgeTopAlpha = 0.75f,
                bandDp = 30f, rimAlpha = 0.14f,
            )
            BackdropKind.FLAT -> GlassParams(
                blurRadiusDp = 18f, saturation = 1.08f, tintAlpha = 0.38f,
                refractionDp = 3f, specularStrength = 0.30f, edgeTopAlpha = 0.40f,
                bandDp = 14f, rimAlpha = 0.06f,
            )
        }
        GlassTier.MEDIUM -> when (backdrop) {
            BackdropKind.TEXTURED -> GlassParams(
                blurRadiusDp = 26f, saturation = 1.45f, tintAlpha = 0.08f,
                refractionDp = 0f, specularStrength = 0f, edgeTopAlpha = 0.65f,
                bandDp = 0f, rimAlpha = 0.10f,
            )
            BackdropKind.FLAT -> GlassParams(
                blurRadiusDp = 16f, saturation = 1.05f, tintAlpha = 0.40f,
                refractionDp = 0f, specularStrength = 0f, edgeTopAlpha = 0.35f,
                bandDp = 0f, rimAlpha = 0.05f,
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
     * 背景分类（整图版，保留兼容）：亮度起伏与色彩都极低 → 纯纸面（轻霜）；
     * 否则按照片/场景处理。纯色壁纸的标准差为 0；纸面罩纱渐变（顶 75%→底 45%
     * 的同色渐变）起伏也很小，同样归 FLAT——这正是想要的：渐变纸面不该触发
     * 全开折射。
     *
     * v0.68.96 起实际分类走 [classifyBands]（区域版）：整图统计会被大面积低
     * 纹理区域（如浅蓝天）稀释，误判 FLAT 后叠白 0.38 会吃掉照片的通透感。
     */
    fun classifyBackdrop(luminanceStdDev: Float, saturationMean: Float): BackdropKind =
        if (luminanceStdDev < 8f && saturationMean < 0.06f) BackdropKind.FLAT else BackdropKind.TEXTURED

    /** 局部细节阈值（相邻像素平均亮度差，0..255）：≥ 此值视为有真实位图纹理。 */
    const val DETAIL_TEXTURED_MIN = 1.5f

    /** 背景分类的水平带数（按画面高度均分，表面按自身位置取所在带）。 */
    const val BACKDROP_BANDS = 6

    private fun luminanceOf(px: Int): Double {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return 0.213 * r + 0.715 * g + 0.072 * b
    }

    /**
     * 局部细节：相邻像素（右/下）亮度差的平均值（0..255）。
     * 平滑渐变（纸面）每步差 <1；照片纹理（山脊/树冠/建筑边缘）在缩略图上
     * 仍有数个量级的步差——这是「有真实位图纹理」的判别信号，不受整图均值稀释。
     * pixels 为 width×height 行主序 ARGB。
     */
    fun localDetail(pixels: IntArray, width: Int, height: Int): Float {
        if (width < 1 || height < 1 || pixels.size < width * height) return 0f
        var sum = 0.0
        var count = 0L
        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                val y0 = luminanceOf(pixels[rowOff + x])
                if (x + 1 < width) {
                    sum += kotlin.math.abs(luminanceOf(pixels[rowOff + x + 1]) - y0)
                    count++
                }
                if (y + 1 < height) {
                    sum += kotlin.math.abs(luminanceOf(pixels[rowOff + width + x]) - y0)
                    count++
                }
            }
        }
        return if (count == 0L) 0f else (sum / count).toFloat()
    }

    /** 区域统计：指定行带内的 (亮度标准差， 平均饱和度， 局部细节)。 */
    fun regionStats(
        pixels: IntArray,
        width: Int,
        height: Int,
        rowFrom: Int,
        rowTo: Int,
    ): Triple<Float, Float, Float> {
        val y0 = rowFrom.coerceIn(0, height)
        val y1 = rowTo.coerceIn(y0, height)
        if (width < 1 || y1 <= y0 || pixels.size < width * height) return Triple(0f, 0f, 0f)
        var sumY = 0.0
        var sumSqY = 0.0
        var sumSat = 0.0
        var n = 0.0
        for (y in y0 until y1) {
            val rowOff = y * width
            for (x in 0 until width) {
                val px = pixels[rowOff + x]
                val lum = luminanceOf(px)
                sumY += lum
                sumSqY += lum * lum
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                sumSat += if (max == 0) 0.0 else (max - min).toDouble() / max
                n += 1.0
            }
        }
        if (n == 0.0) return Triple(0f, 0f, 0f)
        val mean = sumY / n
        val variance = (sumSqY / n - mean * mean).coerceAtLeast(0.0)
        // 局部细节只在带内取样（跨带边界不计）
        var dSum = 0.0
        var dCount = 0L
        for (y in y0 until y1) {
            val rowOff = y * width
            for (x in 0 until width) {
                val lum = luminanceOf(pixels[rowOff + x])
                if (x + 1 < width) {
                    dSum += kotlin.math.abs(luminanceOf(pixels[rowOff + x + 1]) - lum)
                    dCount++
                }
                if (y + 1 < y1) {
                    dSum += kotlin.math.abs(luminanceOf(pixels[rowOff + width + x]) - lum)
                    dCount++
                }
            }
        }
        val detail = if (dCount == 0L) 0f else (dSum / dCount).toFloat()
        return Triple(kotlin.math.sqrt(variance).toFloat(), (sumSat / n).toFloat(), detail)
    }

    /**
     * 带级分类：有真实位图纹理（局部细节达标）→ TEXTURED，照片永不进 FLAT；
     * 无纹理且亮度/色彩都极低 → FLAT（纯色/平滑纸面渐变）；其余按 TEXTURED。
     */
    fun classifyRegion(luminanceStdDev: Float, saturationMean: Float, localDetail: Float): BackdropKind =
        when {
            localDetail >= DETAIL_TEXTURED_MIN -> BackdropKind.TEXTURED
            luminanceStdDev < 8f && saturationMean < 0.06f -> BackdropKind.FLAT
            else -> BackdropKind.TEXTURED
        }

    /**
     * 区域分类：把缩略图按高度均分为 [BACKDROP_BANDS] 个水平带，逐带分类。
     * 玻璃面按自身在画面中的纵向位置取所在带的档位——Dock 带（画面下部）
     * 看的是远山而非整图被蓝天稀释后的均值。
     */
    fun classifyBands(pixels: IntArray, width: Int, height: Int): List<BackdropKind> {
        if (width < 1 || height < 1 || pixels.size < width * height) {
            return List(BACKDROP_BANDS) { BackdropKind.FLAT }
        }
        return (0 until BACKDROP_BANDS).map { band ->
            val rowFrom = band * height / BACKDROP_BANDS
            val rowTo = (band + 1) * height / BACKDROP_BANDS
            val (std, sat, detail) = regionStats(pixels, width, height, rowFrom, rowTo)
            classifyRegion(std, sat, detail)
        }
    }

    /** 整图概览（bands 的汇总）：任一带为照片即整图按照片对待（无位置信息时的兜底）。 */
    fun overallKind(bands: List<BackdropKind>): BackdropKind =
        if (bands.any { it == BackdropKind.TEXTURED }) BackdropKind.TEXTURED else BackdropKind.FLAT

    /** 按纵向比例（0=顶， 1=底）取所在带的分类；比例越界自动钳制。 */
    fun kindAtFraction(bands: List<BackdropKind>, fraction: Float): BackdropKind {
        if (bands.isEmpty()) return BackdropKind.FLAT
        val idx = (fraction.coerceIn(0f, 0.999f) * bands.size).toInt()
        return bands[idx.coerceIn(0, bands.size - 1)]
    }

    /**
     * 单带亮度（线性光 0..1）：均值 + 标准差，供简报自适应字色决策。
     * 注意与 [luminanceOf] 区分：后者是 gamma 编码加权和（玻璃分类用），
     * 线性光才能算 WCAG 对比度——gamma 值会扭曲中段对比，不可混用。
     * 与 [classifyBands] 同批缩略图、同带划分，两份带数据一一对应。
     */
    data class BandLuminance(val mean: Float, val stdDev: Float)

    fun bandLuminance(pixels: IntArray, width: Int, height: Int): List<BandLuminance> {
        if (width < 1 || height < 1 || pixels.size < width * height) {
            return List(BACKDROP_BANDS) { BandLuminance(0f, 0f) }
        }
        return (0 until BACKDROP_BANDS).map { band ->
            val y0 = band * height / BACKDROP_BANDS
            val y1 = (band + 1) * height / BACKDROP_BANDS
            var sum = 0.0
            var sumSq = 0.0
            var n = 0L
            for (y in y0 until y1) {
                val rowOff = y * width
                for (x in 0 until width) {
                    val lum = relativeLuminance(pixels[rowOff + x])
                    sum += lum
                    sumSq += lum * lum
                    n++
                }
            }
            if (n == 0L) {
                BandLuminance(0f, 0f)
            } else {
                val mean = sum / n
                val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
                BandLuminance(mean.toFloat(), kotlin.math.sqrt(variance).toFloat())
            }
        }
    }

    /** sRGB 通道 → 线性光（WCAG 相对亮度定义）。 */
    private fun linearChannel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    /** WCAG 相对亮度（线性光 0..1）：0xFF000000 → 0，0xFFFFFFFF → 1。 */
    fun relativeLuminance(px: Int): Double {
        val r = linearChannel((px shr 16) and 0xFF)
        val g = linearChannel((px shr 8) and 0xFF)
        val b = linearChannel(px and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG 对比度：(亮方 + 0.05) / (暗方 + 0.05)，黑白 = 21。 */
    fun contrastRatio(lumA: Double, lumB: Double): Double {
        val (hi, lo) = if (lumA >= lumB) lumA to lumB else lumB to lumA
        return (hi + 0.05) / (lo + 0.05)
    }
}
