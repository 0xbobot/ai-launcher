package com.bobot.ailauncher.ui.pet

import android.graphics.BlurMaskFilter
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bobot.ailauncher.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 首页七仔场景动画：Mii 式程序化（canvas-demo v4 小样移植）。
 *
 * 建造方式 = 真渲染图（meteor_*，形象不动）+ 代码算变换：
 * - 姿态：main / wave / hold 三张按权重混合
 * - 眨眼：眼部包围盒纵向压缩（BlurMaskFilter 羽化，和 Python 版一致）
 * - 呼吸/跳跃：整体缩放位移
 * - 看消息：hold 姿态 + 卡片辉光 + 红点徽标 + 气泡
 * - 工匠联动：矢量小机器人 NPC + 齿轮 + 进度条 + 对勾
 *
 * 点按七仔可在场景间循环演示；新通知到达自动进 READING。
 */
enum class QizaiScene { NONE, WAVE, READING, WORKING }

fun QizaiScene.durationSec(): Float = when (this) {
    QizaiScene.WAVE -> 2.6f
    QizaiScene.READING -> 4.8f
    QizaiScene.WORKING -> 6.4f
    QizaiScene.NONE -> 0f
}

private data class FRect(val l: Float, val t: Float, val r: Float, val b: Float)

/** 眼部包围盒（相对位图坐标，Python 离线检测） */
private val EYE_BOXES = mapOf(
    "main" to listOf(
        FRect(0.1766f, 0.2687f, 0.5469f, 0.4198f),
        FRect(0.4531f, 0.2677f, 0.7000f, 0.4198f)
    ),
    "wave" to listOf(
        FRect(0.1781f, 0.2812f, 0.5469f, 0.4510f),
        FRect(0.4531f, 0.2531f, 0.6891f, 0.4281f)
    ),
    "hold" to listOf(
        FRect(0.2462f, 0.3600f, 0.4025f, 0.4200f),
        FRect(0.4775f, 0.3387f, 0.6775f, 0.4200f)
    )
)

/** hold 姿态卡片区域（相对坐标，Python 测量） */
private val CARD_RECT = FRect(0.3812f, 0.6875f, 0.5188f, 0.8062f)

private fun ss(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun easeOutBack(p: Float): Float {
    val c = 1.70158f
    val q = p.coerceIn(0f, 1f) - 1f
    return 1 + (c + 1) * q * q * q + c * q * q
}

@Composable
fun QizaiScenePlayer(
    scene: QizaiScene,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scene == QizaiScene.NONE) return
    val bmMain = ImageBitmap.imageResource(R.drawable.meteor_main)
    val bmWave = ImageBitmap.imageResource(R.drawable.meteor_wave)
    val bmHold = ImageBitmap.imageResource(R.drawable.meteor_hold)
    val bitmaps = mapOf("main" to bmMain, "wave" to bmWave, "hold" to bmHold)

    var t by remember(scene) { mutableFloatStateOf(0f) }
    LaunchedEffect(scene) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            val el = (SystemClock.uptimeMillis() - start) / 1000f
            if (el >= scene.durationSec()) {
                onDone()
                break
            }
            t = el
            delay(33) // ~30fps，和小样一致
        }
    }
    // 眨眼羽化（对应 Python 的 GaussianBlur mask）
    val blinkPaint = remember {
        Paint().apply {
            asFrameworkPaint().maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawQizaiScene(scene, t, bitmaps, blinkPaint)
    }
}

private fun DrawScope.drawQizaiScene(
    scene: QizaiScene,
    t: Float,
    bitmaps: Map<String, ImageBitmap>,
    blinkPaint: Paint
) {
    val W = size.width
    val H = size.height
    val bm0 = bitmaps.getValue("main")
    // 绘制高度取画布 75%，和常态 100dp 宠物视觉一致（场景盒更高）
    val fit = H * 0.75f / bm0.height

    // ---- 时间轴（和 v4 小样同一套数学） ----
    val poses: List<Pair<String, Float>>
    var sx = 1f
    var sy = 1f
    var yFrac = 0f
    var xFrac = 0.5f
    var blink = 1f
    var badge = 0f
    var bubble = 0f
    var glow = 0f
    var craftVis = 0f
    var prog = 0f
    var working = 0f
    var check = 0f
    val br = sin(2 * Math.PI.toFloat() * t / 2.4f)
    when (scene) {
        QizaiScene.WAVE -> {
            val w = ss(0f, 0.3f, t) * (1 - ss(2.2f, 2.6f, t))
            poses = listOf("main" to 1 - w, "wave" to w)
            yFrac = -0.035f * abs(sin(2 * Math.PI.toFloat() * 1.5f * t)) * w
            xFrac = 0.5f + 0.015f * sin(2 * Math.PI.toFloat() * 1.5f * t) * w
        }
        QizaiScene.READING -> {
            val w = ss(0f, 0.5f, t) * (1 - ss(4.2f, 4.8f, t))
            poses = listOf("main" to 1 - w, "hold" to w)
            xFrac = 0.5f + 0.012f * sin(2 * Math.PI.toFloat() * 0.8f * t) * w
            glow = w * (0.55f + 0.25f * sin(2 * Math.PI.toFloat() * 1.2f * t))
            if (t >= 1.0f) badge = easeOutBack((t - 1.0f) / 0.35f)
            if (t >= 1.4f) bubble = easeOutBack((t - 1.4f) / 0.35f)
            val dt = t - 2.2f
            blink = 1 - 0.92f * exp(-((dt / 0.06f) * (dt / 0.06f)))
        }
        QizaiScene.WORKING -> {
            poses = listOf("main" to 1f)
            val vis = ss(0f, 0.7f, t) * (1 - ss(5.6f, 6.4f, t))
            xFrac = 0.5f - 0.14f * vis // 七仔让到左边
            craftVis = vis
            prog = ss(0.8f, 3.2f, t)
            working = ss(0.7f, 1.0f, t) * (1 - ss(3.4f, 3.7f, t))
            if (t >= 3.5f) check = easeOutBack((t - 3.5f) / 0.3f)
            if (t in 3.5f..4.1f) yFrac = -0.07f * sin(Math.PI.toFloat() * (t - 3.5f) / 0.6f)
            val dt = t - 2.0f
            blink = 1 - 0.92f * exp(-((dt / 0.06f) * (dt / 0.06f)))
        }
        QizaiScene.NONE -> {
            poses = listOf("main" to 1f)
        }
    }
    // 呼吸（细微，和常态 PetView 的呼吸同量级）
    sx *= 1 - 0.008f * br
    sy *= 1 + 0.012f * br

    // ---- 画姿态 ----
    data class Placed(val name: String, val wt: Float, val left: Float, val top: Float, val dw: Float, val dh: Float)
    val placed = mutableListOf<Placed>()
    for ((name, wt) in poses) {
        if (wt <= 0.01f) continue
        val bm = bitmaps.getValue(name)
        val dw = bm.width * fit * sx
        val dh = bm.height * fit * sy
        val left = xFrac * W - dw / 2
        val top = (H - dh) / 2 + yFrac * dh
        drawImage(
            image = bm,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt()),
            alpha = wt
        )
        placed.add(Placed(name, wt, left, top, dw, dh))
        // 眨眼：眼部盒纵向压缩
        if (blink < 0.985f) {
            val fwPaint = blinkPaint.asFrameworkPaint()
            val prevAlpha = fwPaint.alpha
            fwPaint.alpha = (wt * 255).toInt().coerceIn(0, 255)
            for (b in EYE_BOXES.getValue(name)) {
                val bl = left + b.l * dw
                val bt = top + b.t * dh
                val br2 = left + b.r * dw
                val bb = top + b.b * dh
                val bw = br2 - bl
                val bh = maxOf(2f, (bb - bt) * blink)
                val cy = (bt + bb) / 2
                drawIntoCanvas { c ->
                    val src = android.graphics.Rect(bl.toInt(), bt.toInt(), br2.toInt(), bb.toInt())
                    val dst = android.graphics.RectF(bl, cy - bh / 2, br2, cy + bh / 2)
                    c.nativeCanvas.drawBitmap(bm.asAndroidBitmap(), src, dst, fwPaint)
                }
            }
            fwPaint.alpha = prevAlpha
        }
    }

    // ---- 看消息 FX ----
    if (scene == QizaiScene.READING) {
        val hold = placed.find { it.name == "hold" }
        if (hold != null && hold.wt > 0.01f) {
            val alpha = hold.wt
            val cl = hold.left + CARD_RECT.l * hold.dw
            val ct = hold.top + CARD_RECT.t * hold.dh
            val cr = hold.left + CARD_RECT.r * hold.dw
            val cb = hold.top + CARD_RECT.b * hold.dh
            val cardW = cr - cl
            val ccx = (cl + cr) / 2
            val ccy = (ct + cb) / 2
            // 辉光（三层圆叠出柔边）
            if (glow > 0.01f) {
                val gCols = listOf(0.55f to 20, 0.73f to 14, 0.91f to 8)
                for ((rr, aa) in gCols) {
                    drawCircle(
                        color = Color(255, 214, 130, (aa * glow * alpha).toInt().coerceIn(0, 255)),
                        radius = cardW * rr,
                        center = Offset(ccx, ccy)
                    )
                }
            }
            // 红点徽标
            if (badge > 0.01f) {
                val br2 = cardW * 0.11f * maxOf(badge, 0.01f)
                val bx = cr - cardW * 0.06f
                val by = ct + cardW * 0.06f
                drawCircle(Color(235, 80, 70, (255 * alpha).toInt()), br2, Offset(bx, by))
                drawCircle(
                    color = Color.White,
                    radius = br2,
                    center = Offset(bx, by),
                    style = Stroke(width = 3f)
                )
            }
            // 气泡 + 省略号
            if (bubble > 0.01f) {
                val bw2 = cardW * 0.78f * bubble
                val bh2 = cardW * 0.50f * bubble
                val mx = ccx - cardW * 0.55f
                val my = ct - cardW * 0.90f
                val bubbleAlpha = (0.94f * alpha * bubble).coerceIn(0f, 1f)
                drawRoundRect(
                    color = Color.White.copy(alpha = bubbleAlpha),
                    topLeft = Offset(mx - bw2 / 2, my - bh2 / 2),
                    size = Size(bw2, bh2),
                    cornerRadius = CornerRadius(cardW * 0.16f, cardW * 0.16f)
                )
                val tailPath = Path().apply {
                    moveTo(mx - cardW * 0.10f, my + bh2 / 2 - 2f)
                    lineTo(mx - cardW * 0.22f, my + bh2 / 2 + cardW * 0.16f)
                    lineTo(mx + cardW * 0.04f, my + bh2 / 2 + 2f)
                    close()
                }
                drawPath(tailPath, Color.White.copy(alpha = bubbleAlpha))
                val ndots = minOf(3, 1 + ((t - 1.4f) * 2.5f).toInt())
                for (i in 0 until ndots) {
                    drawCircle(
                        color = Color(150, 150, 150, (255 * bubbleAlpha).toInt()),
                        radius = cardW * 0.05f,
                        center = Offset(mx + (i - 1) * cardW * 0.18f, my)
                    )
                }
            }
        }
    }

    // ---- 工匠 NPC ----
    if (scene == QizaiScene.WORKING && craftVis > 0.01f) {
        val a = craftVis
        val slide = (1 - ss(0f, 0.7f, t)) * W * 0.4f + ss(5.6f, 6.4f, t) * W * 0.4f
        val rx = W * 0.80f + slide
        var hop = 0f
        if (t in 3.55f..4.05f) hop = -H * 0.03f * sin(Math.PI.toFloat() * (t - 3.55f) / 0.5f)
        val gy = H * 0.985f + hop
        val S = H * 0.30f // 机器人总高（含齿轮）
        fun col(r: Int, g: Int, b: Int) = Color(r, g, b, (255 * a).toInt())
        // 身体
        drawRoundRect(
            color = col(174, 185, 190),
            topLeft = Offset(rx - 0.143f * S, gy - 0.363f * S),
            size = Size(0.286f * S, 0.337f * S),
            cornerRadius = CornerRadius(0.09f * S, 0.09f * S)
        )
        // 脸
        drawCircle(col(60, 70, 75), 0.043f * S, Offset(rx - 0.073f * S, gy - 0.273f * S))
        drawCircle(col(60, 70, 75), 0.043f * S, Offset(rx + 0.073f * S, gy - 0.273f * S))
        drawLine(
            color = col(60, 70, 75),
            start = Offset(rx - 0.033f * S, gy - 0.180f * S),
            end = Offset(rx + 0.033f * S, gy - 0.180f * S),
            strokeWidth = 0.013f * S
        )
        // 手臂
        drawRoundRect(
            color = col(174, 185, 190),
            topLeft = Offset(rx - 0.197f * S, gy - 0.287f * S),
            size = Size(0.040f * S, 0.147f * S),
            cornerRadius = CornerRadius(0.020f * S, 0.020f * S)
        )
        drawRoundRect(
            color = col(174, 185, 190),
            topLeft = Offset(rx + 0.157f * S, gy - 0.287f * S),
            size = Size(0.040f * S, 0.147f * S),
            cornerRadius = CornerRadius(0.020f * S, 0.020f * S)
        )
        // 齿轮（头顶，转）
        drawGear(rx, gy - 0.467f * S, 0.090f * S, t * 300f * working, col(216, 201, 143), a)
        // 进度条
        val bx0 = rx - 0.200f * S
        val bx1 = rx + 0.200f * S
        val by = gy - 0.660f * S
        drawRoundRect(
            color = col(224, 216, 198),
            topLeft = Offset(bx0, by),
            size = Size(bx1 - bx0, 0.053f * S),
            cornerRadius = CornerRadius(0.027f * S, 0.027f * S)
        )
        val fw = (bx1 - bx0) * prog
        if (fw > 4f) {
            drawRoundRect(
                color = col(232, 184, 75),
                topLeft = Offset(bx0, by),
                size = Size(fw, 0.053f * S),
                cornerRadius = CornerRadius(0.027f * S, 0.027f * S)
            )
        }
        // 对勾
        if (check > 0.01f) {
            val cr = 0.070f * S * maxOf(check, 0.01f)
            val ccx = rx
            val ccy = by - 0.120f * S
            val cc = col(110, 170, 120)
            drawLine(cc, Offset(ccx - cr, ccy), Offset(ccx - cr * 0.25f, ccy + cr * 0.7f), 0.027f * S)
            drawLine(cc, Offset(ccx - cr * 0.25f, ccy + cr * 0.7f), Offset(ccx + cr, ccy - cr * 0.8f), 0.027f * S)
        }
    }
}

private fun DrawScope.drawGear(
    cx: Float,
    cy: Float,
    rad: Float,
    angleDeg: Float,
    color: Color,
    alpha: Float
) {
    val path = Path()
    val n = 12
    for (i in 0 until n * 2) {
        val a = Math.toRadians((angleDeg + i * 360f / (n * 2)).toDouble())
        val rr = if (i % 2 == 0) rad else rad * 0.78f
        val x = cx + rr * cos(a).toFloat()
        val y = cy + rr * sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    drawCircle(color, rad * 0.55f, Offset(cx, cy))
    drawCircle(Color(232, 226, 217, (255 * alpha).toInt()), rad * 0.20f, Offset(cx, cy))
}
