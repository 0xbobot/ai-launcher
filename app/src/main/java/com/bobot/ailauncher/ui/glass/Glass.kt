package com.bobot.ailauncher.ui.glass

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader as PlatformShader
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.RenderEffect as PlatformRenderEffect
import kotlin.math.roundToInt

/**
 * 玻璃引擎（自研统一实现，强/中两档共用一套捕获，参数见 GlassPolicy）：
 *
 * 为什么不用 Haze（docs/GLASS_DESIGN.md v0.2 §2 的中玻璃原案）——落地时核实两点：
 * 1) 本应用的壁纸是「系统壁纸透出」（themes.xml windowShowWallpaper），不是内绘，
 *    Haze 与任何捕获方案都看不见它；必须先把壁纸内绘化（见 WallpaperBackdrop），
 *    这层做完后捕获问题对所有路线都已解决，Haze 的核心价值（捕获基建）不再独有。
 * 2) Haze 的样式只有模糊/叠色/噪点，没有饱和度控制，而文档标定增色 130–140%
 *    是中玻璃四件套之一；自研引擎用 RenderEffect 链（饱和矩阵 → 模糊）一次到位。
 * 强玻璃本来就必须自研 AGSL，统一引擎后两档共用捕获与降级，零新增依赖。
 *
 * 捕获方式：桌面背景层（内绘壁纸 + 罩纱）整体录进一个 GraphicsLayer（背景源），
 * 每个玻璃面按自身窗口位置从中裁出「比自己大一圈」的补丁，再施加效果绘制。
 */
class GlassBackdropState internal constructor() {
    /** 背景源图层（由宿主 rememberGraphicsLayer 后写入；null = 还没就绪，画模拟档）。 */
    var layer: GraphicsLayer? = null

    /** 背景每重录一次 +1；玻璃面在绘制时读取它来订阅背景更新。 */
    var recordTick by mutableIntStateOf(0)

    /** 背景容器左上角的窗口坐标（玻璃面据此换算自身相对位置）。 */
    var originInWindow by mutableStateOf(IntOffset.Zero)

    /** 背景源高度（窗口 px），玻璃面按自身纵向位置取所在水平带的分类用。 */
    var backdropHeightPx by mutableIntStateOf(0)

    /**
     * 背景的水平带分类（自上而下，见 GlassPolicy.classifyBands）：强度随背景走
     * 且随位置走——同一张壁纸，Dock 带（画面下部）与气泡带可不同档。
     * v0.68.96 前是整图单一分类，被大面积低纹理区域稀释后会误判轻霜。
     */
    var backdropBands by mutableStateOf<List<BackdropKind>>(listOf(BackdropKind.FLAT))

    /**
     * 背景的水平带亮度（线性光 0..1，与 backdropBands 同批缩略图、同带划分，
     * 见 GlassPolicy.bandLuminance）：整幅宽带，简报已不再消费（字区外深色块
     * 会带偏均值），保留供其他调用方／兼容；简报取样见 briefingLuminance。
     * 空 = 尚未加载成功（动态壁纸／读取失败）。
     */
    var backdropLuminance by mutableStateOf<List<GlassPolicy.BandLuminance>>(emptyList())

    /**
     * 简报字区（左 40% 横向范围，见 GlassPolicy.BRIEFING_TEXT_RIGHT_FRAC）
     * 的水平带亮度：简报自适应字色只看它顶部若干带。
     * 空 = 尚未加载成功（动态壁纸／读取失败），简报调用方沿用上次字色档（见 PetZone）。
     */
    var briefingLuminance by mutableStateOf<List<GlassPolicy.BandLuminance>>(emptyList())

    /** 壁纸重采样 tick：壁纸变化监听回调时 +1，SystemWallpaperLayer 据此重载。 */
    var wallpaperTick by mutableIntStateOf(0)

    /** 整图概览（无位置信息时的兜底）：任一带为照片即按照片对待。 */
    val backdropKind: BackdropKind get() = GlassPolicy.overallKind(backdropBands)

    /** 桌面可见且未被全屏层（D3）遮挡时才为 true；false → 全面回模拟档（省电）。 */
    var active by mutableStateOf(false)

    var powerSave by mutableStateOf(false)
    var lowBattery by mutableStateOf(false)
    var lowRam by mutableStateOf(false)

    val sdkInt: Int get() = Build.VERSION.SDK_INT
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/** 背景源修饰：把被修饰容器的内容（壁纸 + 罩纱）录进背景图层，同时照常绘制。 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = this
    .onGloballyPositioned {
        val p = it.positionInWindow()
        state.originInWindow = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        state.backdropHeightPx = it.size.height
    }
    .drawWithContent {
        val layer = state.layer
        if (layer != null && size.width >= 1f && size.height >= 1f) {
            layer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                this@drawWithContent.drawContent()
            }
            state.recordTick = if (state.recordTick == Int.MAX_VALUE) 0 else state.recordTick + 1
        }
        drawContent()
    }

/** 刷新降级信号（在桌面 onResume 时调用）：省电模式 / 低内存 / 低电量。 */
internal fun refreshGlassSignals(context: Context, state: GlassBackdropState) {
    state.powerSave =
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    state.lowRam =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    val battery = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    if (battery != null) {
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        state.lowBattery = scale > 0 && level >= 0 &&
            level * 100 / scale <= GlassPolicy.LOW_BATTERY_PERCENT && !charging
    }
}

/**
 * Dock 专用光影参数（玻璃质感 v2，2026-10-25）：只给 Dock 面板用。
 * 默认值即 v2 mock 定值按引擎等效幅度翻译的结果（非硬抄 PIL 像素值）：
 *  mock 对角跨度 28 级 → diagonalSpan 28/255 ≈ 0.11（白/黑各半）；
 *  mock 高光峰值 +58 → 58/255 ≈ 0.23；出口峰值 +60 → 0.235；
 *  mock 暖核峰值 80 → 80/255 ≈ 0.31；亮带 alpha 30/60/95 → 0.12/0.235/0.37。
 * 基底（模糊/叠色）不在此改、沿用 GlassPolicy：任务明确透明度不再是杠杆，
 * mock 的 8px PIL 半径无法等效换算为引擎 dp 模糊，硬搬等于为“更透”降模糊。
 * 深色模式暂无单独参数（mock 未覆盖），两档共用同一套值。
 */
data class DockLightParams(
    /** 对角曲面明暗总跨度（右上亮→左下暗，白/黑各占一半）。 */
    val diagonalSpan: Float = 0.11f,
    /** 高光面中心（相对宽/高）与峰值 alpha、半径（相对宽）。 */
    val highlightCenterX: Float = 0.72f,
    val highlightCenterY: Float = 0.30f,
    val highlightPeakAlpha: Float = 0.23f,
    val highlightRadiusFraction: Float = 0.35f,
    /** 左下出口中心（相对宽/高）、峰值 alpha、半径（相对宽）、椭圆拉伸。 */
    val exitCenterX: Float = 0.13f,
    val exitCenterY: Float = 0.90f,
    val exitPeakAlpha: Float = 0.235f,
    val exitRadiusFraction: Float = 0.30f,
    val exitStretchX: Float = 1.6f,
    val exitStretchY: Float = 0.55f,
    /** 沉底暖核中心（相对宽/高）、峰值 alpha、半径（相对宽）、暖色。 */
    val coreCenterX: Float = 0.57f,
    val coreCenterY: Float = 0.20f,
    val corePeakAlpha: Float = 0.31f,
    val coreRadiusFraction: Float = 0.35f,
    val coreColor: Color = Color(0xFFFFE1B4),
    /** 方向性亮带：窄环全周 alpha 范围与线宽、宽环 alpha 与线宽。 */
    val rimNarrowMinAlpha: Float = 0.12f,
    val rimNarrowMaxAlpha: Float = 0.235f,
    val rimNarrowWidth: Dp = 2.dp,
    val rimWideAlpha: Float = 0.37f,
    val rimWideWidth: Dp = 11.dp,
)

/**
 * 强玻璃 shader 外延补全参数（2026-10-26 本批，意图卡片圆角外延专用）：
 * 外延翅膀超出布局边界与平圆角 SDF，shader 锐利层须以外延矩形 SDF
 * 取 min 补全覆盖，否则翅膀只有模糊层、本体有锐利层，交界现色阶台阶。
 * 其他玻璃面一律不传（null → 两 uniform 置 0，shader 与旧版一字相同）。
 */
data class FlareShaderParams(
    /** 外延带高（意图卡片 INTENT_FLARE_HEIGHT = 24dp）。 */
    val height: Dp,
    /** 外延单侧外扩（意图卡片竖屏 = 20dp 外边距，底边恰为屏宽）。 */
    val extent: Dp,
)

/**
 * 径向渐变半径防线（357 横屏闪退根因，2026-10-26）：
 * android.graphics.RadialGradient 要求 ending radius 严格 > 0；
 * 玻璃面宽为 0 时 fraction × width = 0，主线程直接炸
 * IllegalArgumentException（崩溃记录 Glass.kt:378，nubia P0110）。
 * 0 宽的来源：357 横屏左区垫高按屏宽算、在 PetZone 左区槽内二次扣减，
 * 屏宽 ≤ 720dp 时卡片宽塌到 0（屏宽 720 恰好为 0、更小恒为 0）；
 * 布局侧已由 IntentEntry 容器相对口径修掉 0 宽条件，本防线守另一端——
 * 任何路径递来 0 宽都不许再炸主线程。钳制到 1px：0 宽面上的 1px
 * 渐变不可见；正常尺寸下 fraction × width 恒远大于 1px，渲染一字不动。
 * 三处径向渐变（暖核／高光／左下出口）一律走本函数，不许再裸乘。
 */
internal fun glassRadialRadius(fraction: Float, extentPx: Float): Float =
    (fraction * extentPx).coerceAtLeast(1f)

/**
 * 玻璃面修饰（放在原 `.background(...)` 的位置调用，链的其余部分不动）：
 * SIMULATED 档或无玻璃环境时就地退化为原半透明色背景，像素级等同现状；
 * 玻璃档绘制：模糊+增色补丁（中/强共用）→（强玻璃）AGSL 折射层 → 叠色 → 顶部亮边。
 * 不改变任何尺寸/位置/层级，只换材质渲染（布局纪律）。
 *
 * @param tintBoost 0..1 的临时提亮（如 Dock 按压反馈），只调叠色/高光强度。
 * @param shape 非圆角矩形轮廓（如 Dock 圆肩）时传自定义 Shape；默认 null
 *   即原圆角矩形口径，其他玻璃面行为一字不动。轮廓可超出布局上沿
 *   （肩线 rise），玻璃裁剪/叠色/亮边一律沿该轮廓走。
 * @param dockLight Dock 专用光影（玻璃质感 v2）；默认 null 时 glassDraw
 *   一字不动，其他玻璃面不受影响。SIMULATED 档不进 glassDraw，故该档无 v2 光影。
 * @param noRim true 时跳过全部轮廓描边（外侧暗描边、v2 方向性亮带、
 *   1dp 顶部亮边）：Dock 面板专用（Bob 2026-10-25 22:31：玻璃边框效果不要），
 *   默认 false，其他玻璃面一字不动。
 * @param flareShader 强玻璃 shader 外延补全（意图卡片圆角外延专用）；
 *   默认 null 不补全，其他玻璃面一字不动。
 */
@Composable
fun Modifier.glassSurface(
    kind: GlassSurfaceKind,
    cornerRadius: Dp,
    tintColor: Color,
    fallbackColor: Color,
    tintBoost: Float = 0f,
    shape: Shape? = null,
    dockLight: DockLightParams? = null,
    noRim: Boolean = false,
    flareShader: FlareShaderParams? = null,
): Modifier {
    val fallbackShape = shape ?: RoundedCornerShape(cornerRadius)
    val state = LocalGlassBackdrop.current
        ?: return this.background(fallbackColor, fallbackShape)
    val tier = GlassPolicy.resolveTier(
        sdkInt = state.sdkInt,
        kind = kind,
        active = state.active,
        powerSave = state.powerSave,
        lowBattery = state.lowBattery,
        lowRam = state.lowRam,
    )
    if (tier == GlassTier.SIMULATED) {
        return this.background(fallbackColor, fallbackShape)
    }
    return this.then(
        glassDraw(state, tier, cornerRadius, tintColor, fallbackColor, tintBoost, shape, dockLight, noRim, flareShader)
    )
}

/** 中玻璃效果链（平台 RenderEffect）：先饱和（色彩矩阵）后模糊，桥接为 Compose 效果。 */
@RequiresApi(GlassPolicy.API_RENDER_EFFECT)
private fun createBlurSaturationEffect(blurPx: Float, saturation: Float): RenderEffect {
    val satEffect = PlatformRenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(GlassPolicy.saturationMatrix(saturation))
    )
    val blurEffect = PlatformRenderEffect.createBlurEffect(
        blurPx, blurPx, PlatformShader.TileMode.DECAL
    )
    return PlatformRenderEffect.createChainEffect(blurEffect, satEffect).asComposeRenderEffect()
}

/** 强玻璃 AGSL 效果（平台 RuntimeShader）：uContent 为输入纹理 uniform 名。 */
@RequiresApi(GlassPolicy.API_RUNTIME_SHADER)
private fun createShaderEffect(shader: android.graphics.RuntimeShader): RenderEffect =
    PlatformRenderEffect.createRuntimeShaderEffect(shader, "uContent").asComposeRenderEffect()

/** 补丁重录缓存键：输入没变就不重录（静态面绝大多数帧直接命中）。 */
private class PatchCache {
    var key: Long = Long.MIN_VALUE
    var margin: Int = 0
    var width: Int = 0
    var height: Int = 0
}

@Composable
private fun glassDraw(
    state: GlassBackdropState,
    tier: GlassTier,
    cornerRadius: Dp,
    tintColor: Color,
    fallbackColor: Color,
    tintBoost: Float,
    shape: Shape? = null,
    dockLight: DockLightParams? = null,
    noRim: Boolean = false,
    flareShader: FlareShaderParams? = null,
): Modifier {
    val composeDensity = LocalDensity.current.density
    val patchBlur = rememberGraphicsLayer()
    val patchSharp = rememberGraphicsLayer()
    var posInWindow by remember { mutableStateOf(Offset.Zero) }
    var elemSize by remember { mutableStateOf(IntSize.Zero) }
    // v0.68.96：参数按「表面所在水平带」的背景分类取——同一张壁纸上，
    // Dock 带（下部远山）走照片全开、顶部天空带可走轻霜，不再被整图均值绑架。
    val bandKind = if (state.backdropHeightPx > 0 && elemSize.height > 0) {
        val centerFraction =
            (posInWindow.y - state.originInWindow.y + elemSize.height / 2f) /
                state.backdropHeightPx
        GlassPolicy.kindAtFraction(state.backdropBands, centerFraction)
    } else {
        state.backdropKind
    }
    val params = GlassPolicy.paramsFor(tier, bandKind)
    if (params == null) {
        return Modifier.background(fallbackColor, RoundedCornerShape(cornerRadius))
    }
    // AGSL 只在 API 33+ 创建；创建失败（个别驱动编译不过）→ 强玻璃自动降中玻璃绘制
    val shader = remember {
        if (Build.VERSION.SDK_INT >= GlassPolicy.API_RUNTIME_SHADER) {
            GlassShader.createOrNull()
        } else {
            null
        }
    }
    // 效果链走平台 RenderEffect + asComposeRenderEffect 桥（Compose 1.7.5 未公开
    // compose 版 ChainEffect/RuntimeShaderEffect 包装类）；本函数只在玻璃档组合，
    // 档位判定已保证 SDK ≥ 31（强档 ≥ 33），平台调用点另有显式 SDK 守卫。
    val blurSatEffect = remember(params, composeDensity) {
        if (Build.VERSION.SDK_INT >= GlassPolicy.API_RENDER_EFFECT) {
            createBlurSaturationEffect(params.blurRadiusDp * composeDensity, params.saturation)
        } else {
            null
        }
    }
    val shaderEffect = remember(shader) {
        if (shader != null && Build.VERSION.SDK_INT >= GlassPolicy.API_RUNTIME_SHADER) {
            createShaderEffect(shader)
        } else {
            null
        }
    }
    val strong = tier == GlassTier.STRONG && shaderEffect != null
    val cache = remember { PatchCache() }
    val boost = tintBoost.coerceIn(0f, 1f)

    return Modifier
        .onGloballyPositioned {
            posInWindow = it.positionInWindow()
            elemSize = it.size
        }
        .drawWithContent {
            val backdrop = state.layer
            val tick = state.recordTick // 订阅背景重录
            val radiusPx = cornerRadius.toPx()
            // 自定义轮廓（如 Dock 圆肩）：Shape 在绘制期展开为 Path；非
            // Generic 轮廓退回圆角矩形。null 时下面所有分支走原口径。
            val outlinePath: Path? = shape?.let {
                when (val o = it.createOutline(size, layoutDirection, this)) {
                    is Outline.Generic -> o.path
                    else -> null
                }
            }
            if (backdrop == null) {
                if (outlinePath != null) {
                    drawPath(path = outlinePath, color = fallbackColor)
                } else {
                    drawRoundRect(fallbackColor, cornerRadius = CornerRadius(radiusPx))
                }
                drawContent()
                return@drawWithContent
            }
            val blurPx = params.blurRadiusDp * density
            val refrPx = params.refractionDp * density
            // 补丁外扩量：覆盖模糊采样半径 + 折射位移 + 安全余量
            val margin = (blurPx + refrPx + 8.dp.toPx()).roundToInt().coerceAtLeast(1)
            val pw = (size.width.roundToInt() + 2 * margin).coerceAtLeast(1)
            val ph = (size.height.roundToInt() + 2 * margin).coerceAtLeast(1)
            val relX = (posInWindow.x - state.originInWindow.x).roundToInt()
            val relY = (posInWindow.y - state.originInWindow.y).roundToInt()
            // 元素自身尺寸：record 块内 size 是补丁尺寸，此处先 capture 供暖核定位用。
            val elemW = size.width
            val elemH = size.height
            val key = ((pw.toLong() * 31 + ph) * 31 + relX) * 31 + relY xor
                (tick.toLong() * 1_000_003L) xor params.hashCode().toLong()
            if (cache.key != key) {
                cache.key = key
                cache.margin = margin
                cache.width = pw
                cache.height = ph
                val recordSize = IntSize(pw, ph)
                patchBlur.record(recordSize) {
                    withTransform({ translate(margin - relX.toFloat(), margin - relY.toFloat()) }) {
                        drawLayer(backdrop)
                    }
                    // 玻璃质感 v2 暖核沉底：画进模糊补丁、随补丁一起被模糊 =
                    // 透过毛玻璃看的体内暖光（mock：中心 57% 宽、20% 高处、暖色、
                    // 峰值 80、强模糊后合成到模糊壁纸上）。只进模糊补丁，不进锐利补丁。
                    if (dockLight != null) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    dockLight.coreColor.copy(alpha = dockLight.corePeakAlpha),
                                    dockLight.coreColor.copy(alpha = 0f),
                                ),
                                center = Offset(
                                    margin + dockLight.coreCenterX * elemW,
                                    margin + dockLight.coreCenterY * elemH,
                                ),
                                radius = glassRadialRadius(dockLight.coreRadiusFraction, elemW),
                            ),
                        )
                    }
                }
                if (strong) {
                    patchSharp.record(recordSize) {
                        withTransform({ translate(margin - relX.toFloat(), margin - relY.toFloat()) }) {
                            drawLayer(backdrop)
                        }
                    }
                }
            }
            if (blurSatEffect != null) patchBlur.renderEffect = blurSatEffect
            patchBlur.topLeft = IntOffset(-cache.margin, -cache.margin)
            // v0.68.96：外侧暗描边——线心压在几何边缘上，外半线落在玻璃之外，
            // 在亮背景上衬出切面轮廓（亮边在浅色场景里单独不够辨认）。
            if (!noRim && params.rimAlpha > 0f) {
                if (outlinePath != null) {
                    // 自定义轮廓：描边直接压在轮廓线上（线心在边上，与原
                    // 外扩圆角矩形同一口径：外半线落在玻璃之外）。
                    drawPath(
                        path = outlinePath,
                        color = Color.Black.copy(alpha = params.rimAlpha),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                } else {
                    val rimHalf = 0.5.dp.toPx()
                    val rimPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                -rimHalf, -rimHalf,
                                size.width + rimHalf, size.height + rimHalf,
                                CornerRadius(radiusPx + rimHalf),
                            )
                        )
                    }
                    drawPath(
                        path = rimPath,
                        color = Color.Black.copy(alpha = params.rimAlpha),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            val shapePath = outlinePath ?: Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx)))
            }
            clipPath(shapePath) {
                drawLayer(patchBlur)
                if (strong && shader != null && shaderEffect != null &&
                    Build.VERSION.SDK_INT >= GlassPolicy.API_RUNTIME_SHADER
                ) {
                    shader.setFloatUniform("uElemSize", size.width, size.height)
                    shader.setFloatUniform("uInset", cache.margin.toFloat())
                    shader.setFloatUniform("uRadius", radiusPx)
                    shader.setFloatUniform("uRefraction", refrPx)
                    shader.setFloatUniform("uBand", params.bandDp * density)
                    shader.setFloatUniform("uSpecular", params.specularStrength * (1f + boost * 0.25f))
                    // 外延补全（意图卡片）：无外延时两值皆 0，shader 门关闭。
                    shader.setFloatUniform("uFlareH", flareShader?.height?.toPx() ?: 0f)
                    shader.setFloatUniform("uFlareExt", flareShader?.extent?.toPx() ?: 0f)
                    patchSharp.renderEffect = shaderEffect
                    patchSharp.topLeft = IntOffset(-cache.margin, -cache.margin)
                    drawLayer(patchSharp)
                }
                // 叠色（纸面轻霜档自动加重，见参数表）
                val tintAlpha = (params.tintAlpha + boost * 0.08f).coerceIn(0f, 0.9f)
                if (outlinePath != null) {
                    drawPath(
                        path = outlinePath,
                        color = tintColor.copy(alpha = tintAlpha),
                    )
                } else {
                    drawRoundRect(
                        color = tintColor.copy(alpha = tintAlpha),
                        cornerRadius = CornerRadius(radiusPx),
                    )
                }
                // 玻璃质感 v2（Dock 专用光影，dockLight 非空时）：叠色之上、
                // 图标之下。339 dressing 在链下游继续画（肩线描边线、内晕一字不动）。
                if (dockLight != null) {
                    // ① 对角曲面明暗：右上亮→左下暗，总跨度 diagonalSpan（白/黑各半）。
                    val halfSpan = dockLight.diagonalSpan / 2f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = halfSpan),
                                Color.Transparent,
                                Color.White.copy(alpha = halfSpan),
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        ),
                    )
                    // ② 高光面：右上区域一块柔和光面（肩线描边线保留，见 339 dressing）。
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = dockLight.highlightPeakAlpha),
                                Color.White.copy(alpha = 0f),
                            ),
                            center = Offset(
                                dockLight.highlightCenterX * size.width,
                                dockLight.highlightCenterY * size.height,
                            ),
                            radius = glassRadialRadius(dockLight.highlightRadiusFraction, size.width),
                        ),
                    )
                    // ③ 左下出口：沿底边横向拉长的暖白亮边（各向异性缩放
                    // 把圆形光斑压成沿底边的椭圆）。
                    val exitCenter = Offset(
                        dockLight.exitCenterX * size.width,
                        dockLight.exitCenterY * size.height,
                    )
                    withTransform({
                        scale(dockLight.exitStretchX, dockLight.exitStretchY, pivot = exitCenter)
                    }) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFF6E8).copy(alpha = dockLight.exitPeakAlpha),
                                    Color(0xFFFFF6E8).copy(alpha = 0f),
                                ),
                                center = exitCenter,
                                radius = glassRadialRadius(dockLight.exitRadiusFraction, size.width),
                            ),
                        )
                    }
                    // ⑤ 方向性亮带：窄环全周（对角线向右上递增）+ 宽环只在右上
                    //（对角线向左下衰减到 0）。描边线心压在轮廓线上，外半线被
                    // clip 截掉，与引擎暗描边同一口径；宽环 11dp 描边约 5.5dp 落在面内。
                    // noRim 时整段跳过（Dock 边框效果不要，Bob 2026-10-25 22:31）。
                    if (!noRim) {
                    drawPath(
                        path = shapePath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = dockLight.rimNarrowMinAlpha),
                                Color.White.copy(alpha = dockLight.rimNarrowMaxAlpha),
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        ),
                        style = Stroke(width = dockLight.rimNarrowWidth.toPx()),
                    )
                    drawPath(
                        path = shapePath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = dockLight.rimWideAlpha),
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        ),
                        style = Stroke(width = dockLight.rimWideWidth.toPx()),
                    )
                    }
                }
                // 1dp 亮边：顶部最亮、向下渐隐（内缩半线宽整线落在面内）；
                // noRim 时整段跳过（Dock 边框效果不要，Bob 2026-10-25 22:31）。
                if (!noRim) if (outlinePath != null) {
                    // 自定义轮廓：描边压在轮廓线上，渐变起止取轮廓包围盒
                    // （肩线峰顶即最亮处，与平顶缘口径一致）。
                    val top = outlinePath.getBounds().top
                    drawPath(
                        path = outlinePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = params.edgeTopAlpha),
                                Color.White.copy(alpha = 0.05f),
                            ),
                            startY = top,
                            endY = size.height,
                        ),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                } else {
                    val halfStroke = 0.5.dp.toPx()
                    val edgePath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                halfStroke, halfStroke,
                                size.width - halfStroke, size.height - halfStroke,
                                CornerRadius((radiusPx - halfStroke).coerceAtLeast(0f)),
                            )
                        )
                    }
                    drawPath(
                        path = edgePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = params.edgeTopAlpha),
                                Color.White.copy(alpha = 0.05f),
                            ),
                            startY = 0f,
                            endY = size.height,
                        ),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            drawContent()
        }
}
