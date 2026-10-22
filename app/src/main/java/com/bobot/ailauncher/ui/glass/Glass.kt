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
import androidx.compose.ui.graphics.Path
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

    /** 背景是照片/场景还是纯纸面（强度随背景走，见 GlassPolicy.paramsFor）。 */
    var backdropKind by mutableStateOf(BackdropKind.FLAT)

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
 * 玻璃面修饰（放在原 `.background(...)` 的位置调用，链的其余部分不动）：
 * SIMULATED 档或无玻璃环境时就地退化为原半透明色背景，像素级等同现状；
 * 玻璃档绘制：模糊+增色补丁（中/强共用）→（强玻璃）AGSL 折射层 → 叠色 → 顶部亮边。
 * 不改变任何尺寸/位置/层级，只换材质渲染（布局纪律）。
 *
 * @param tintBoost 0..1 的临时提亮（如 Dock 按压反馈），只调叠色/高光强度。
 */
@Composable
fun Modifier.glassSurface(
    kind: GlassSurfaceKind,
    cornerRadius: Dp,
    tintColor: Color,
    fallbackColor: Color,
    tintBoost: Float = 0f,
): Modifier {
    val state = LocalGlassBackdrop.current
        ?: return this.background(fallbackColor, RoundedCornerShape(cornerRadius))
    val tier = GlassPolicy.resolveTier(
        sdkInt = state.sdkInt,
        kind = kind,
        active = state.active,
        powerSave = state.powerSave,
        lowBattery = state.lowBattery,
        lowRam = state.lowRam,
    )
    val params = GlassPolicy.paramsFor(tier, state.backdropKind)
    if (tier == GlassTier.SIMULATED || params == null) {
        return this.background(fallbackColor, RoundedCornerShape(cornerRadius))
    }
    return this.then(
        glassDraw(state, tier, params, cornerRadius, tintColor, fallbackColor, tintBoost)
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
    params: GlassParams,
    cornerRadius: Dp,
    tintColor: Color,
    fallbackColor: Color,
    tintBoost: Float,
): Modifier {
    val composeDensity = LocalDensity.current.density
    val patchBlur = rememberGraphicsLayer()
    val patchSharp = rememberGraphicsLayer()
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
    var posInWindow by remember { mutableStateOf(Offset.Zero) }
    val cache = remember { PatchCache() }
    val boost = tintBoost.coerceIn(0f, 1f)

    return Modifier
        .onGloballyPositioned { posInWindow = it.positionInWindow() }
        .drawWithContent {
            val backdrop = state.layer
            val tick = state.recordTick // 订阅背景重录
            val radiusPx = cornerRadius.toPx()
            if (backdrop == null) {
                drawRoundRect(fallbackColor, cornerRadius = CornerRadius(radiusPx))
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
            val shapePath = Path().apply {
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
                    patchSharp.renderEffect = shaderEffect
                    patchSharp.topLeft = IntOffset(-cache.margin, -cache.margin)
                    drawLayer(patchSharp)
                }
                // 叠色（纸面轻霜档自动加重，见参数表）
                val tintAlpha = (params.tintAlpha + boost * 0.08f).coerceIn(0f, 0.9f)
                drawRoundRect(
                    color = tintColor.copy(alpha = tintAlpha),
                    cornerRadius = CornerRadius(radiusPx),
                )
                // 1dp 亮边：顶部最亮、向下渐隐（内缩半线宽整线落在面内）
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
            drawContent()
        }
}
