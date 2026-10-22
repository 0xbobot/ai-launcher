package com.bobot.ailauncher.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 强玻璃 AGSL（docs/GLASS_DESIGN.md v0.2 §3 强玻璃路线）：
 * 按圆角矩形 SDF 只在边缘带内做位移折射 + 方向性镜面高光（光从上方来），
 * 中心区域权重为 0——采样不位移、高光不叠加，玻璃不是水滴。
 *
 * 输入 = 元素背后的一块「锐利背景」（比元素大一圈 margin 的 GraphicsLayer 截图），
 * 输出为预乘 alpha：边缘带内是折射后的背景，带外 alpha=0，露出下层的模糊层。
 * 思路参考 Kyant Backdrop 的 lens 实现（SDF + 法线方向位移），此处为自研最小实现，
 * 不引第三方库（体积 0、许可无负担）。
 *
 * 用平台 android.graphics.RuntimeShader + RenderEffect.createRuntimeShaderEffect，
 * 经 asComposeRenderEffect() 桥接到 Compose 图层——项目 Compose 1.7.5 未公开
 * compose 版 RuntimeShader/RuntimeShaderEffect 包装类，故走平台桥（Haze/Kyant 同法）。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object GlassShader {

    /** AGSL 源码。uContent 为输入纹理的固定 uniform 名（与 RuntimeShaderEffect 约定一致）。 */
    const val AGSL = """
uniform shader uContent;
uniform float2 uElemSize;
uniform float uInset;
uniform float uRadius;
uniform float uRefraction;
uniform float uBand;
uniform float uSpecular;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

float sdfAt(float2 elemCoord) {
    return sdRoundBox(elemCoord - uElemSize * 0.5, uElemSize * 0.5, uRadius);
}

half4 main(float2 coord) {
    float2 ec = coord - uInset;
    float d = sdfAt(ec);
    // 小元素（气泡）防呆：边缘带不得超过短边 45%，避免整面都是折射带
    float band = min(uBand, min(uElemSize.x, uElemSize.y) * 0.45);
    float depth = clamp(-d / max(band, 1.0), 0.0, 1.0);
    float w = pow(1.0 - depth, 2.0);
    float e = 1.25;
    float2 grad = float2(
        sdfAt(ec + float2(e, 0.0)) - sdfAt(ec - float2(e, 0.0)),
        sdfAt(ec + float2(0.0, e)) - sdfAt(ec - float2(0.0, e)));
    float gl = length(grad);
    grad = gl > 1e-4 ? grad / gl : float2(0.0, -1.0);
    // 向内采样：边缘把背后的内容往中心收（玻璃边缘的聚光感），中心 w=0 不位移
    float2 disp = -grad * (w * uRefraction);
    float4 c  = float4(uContent.eval(coord + disp));
    float4 cR = float4(uContent.eval(coord + disp * 1.06));
    float4 cB = float4(uContent.eval(coord + disp * 0.94));
    float3 rgb = float3(cR.r, c.g, cB.b);
    // 方向性镜面高光：法线朝上（屏幕坐标 y 向下，顶边外法线为 -y）最亮，侧面次之，底边最弱
    float topness = clamp(-grad.y, 0.0, 1.0);
    float spec = pow(topness, 2.0) * w * uSpecular;
    rgb += spec * 0.45;
    float a = clamp(w * 1.35, 0.0, 1.0);
    // 几何边缘抗锯齿：d=0 处 alpha 半开，配合外层 clip 不留硬边
    a *= clamp(-d * 0.5 + 0.5, 0.0, 1.0);
    return half4(rgb * a, a);
}
"""

    /** 创建 RuntimeShader；AGSL 编译失败（个别驱动）返回 null，调用方降中玻璃绘制。 */
    fun createOrNull(): RuntimeShader? = runCatching { RuntimeShader(AGSL) }.getOrNull()
}
