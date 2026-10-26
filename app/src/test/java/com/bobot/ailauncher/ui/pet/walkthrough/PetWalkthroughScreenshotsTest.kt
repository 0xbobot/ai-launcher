package com.bobot.ailauncher.ui.pet.walkthrough

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.bobot.ailauncher.ui.apps.DockShoulderShape
import com.bobot.ailauncher.ui.apps.appendShoulderArch
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.IntBuffer

/**
 * 宠物走查 Loop P0：JVM 截图采图（Paparazzi，layoutlib 渲染，无模拟器）。
 *
 * 主体选择原则（避开应用模块资源限制与 AGSL）：
 * - 肩线拱：直接画生产代码 DockShoulderShape／appendShoulderArch，
 *   不读资源、不走玻璃 AGSL，渲染即回归检测。
 * - 玻璃头盒：头图不走 painterResource（应用模块资源在 Paparazzi 下
 *   不可读），改从源码树直接读 drawable-nodpi/glass_head.png 原文件，
 *   用 BitmapFactory（android.jar 内可编译、layoutlib 运行时真实解码）
 *   量 alpha 包围盒（盒子尺寸 ≠ 可见尺寸的实测），再进 Compose 画进
 *   宠物盒——量的是真资产文件。javax.imageio 不可用（不在 android.jar
 *   编译类路径里），不要用它。
 * - 不碰 FloatingPet 本体（ExoPlayer／仓储／生命周期脚手架太重）：
 *   眨眼帧序列走几何测试的时序量测，眼睛本体渲染列入 P1。
 *
 * 普通 testDebugUnitTest 下 Paparazzi 快照只渲染不比对（不写文件、不失败），
 * 故主 CI 不受影响；走查工作流跑 recordPaparazziDebug 产图，报告脚本
 * 再对黄金图做像素 diff（通过／失败／新增 diff 三栏）。
 */
class PetWalkthroughScreenshotsTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private val tag = "screenshots"

    /**
     * 肩线拱主体：面板 stub（48dp 高）＋居中拱。
     * 轮廓顶缘在 y=0、拱峰在 y=−rise−peak，故整体下移 rise+peak+16dp，
     * 使拱峰落在画布内（上留 16dp、下留 16dp）。
     */
    @Composable
    private fun ShoulderArchSubject(chordDp: Float, riseDp: Float, peakDp: Float) {
        val chord = chordDp.dp
        val rise = riseDp.dp
        val peak = peakDp.dp
        Canvas(
            modifier = Modifier
                .size(chord + 32.dp, rise + peak + 64.dp)
                .background(Color.White),
        ) {
            val risePx = rise.toPx()
            val peakPx = peak.toPx()
            val chordPx = chord.toPx()
            val marginPx = 16.dp.toPx()
            val outline = DockShoulderShape(chord, rise, 28.dp, peak)
                .createOutline(Size(size.width, 48.dp.toPx()), LayoutDirection.Ltr, this)
            withTransform({ translate(top = risePx + peakPx + marginPx) }) {
                drawPath((outline as Outline.Generic).path, Color(0xFF2A2723))
                // dressing 拱高光：与生产 dressing 同一生产函数画法。
                val cx = size.width / 2f
                val half = chordPx / 2f
                val k = half * 0.5f
                val arch = Path().apply {
                    appendShoulderArch(cx - half, cx + half, cx, k, risePx, peakPx, 0f, moveToStart = true)
                }
                drawPath(arch, Color(0xFFC9A227), style = Stroke(width = 3f))
            }
        }
    }

    @Test
    fun shoulderArchD1() {
        // D1：panel 136dp → 宽 86.56dp → 弦 156.56dp、rise 18.06dp。
        paparazzi.snapshot { ShoulderArchSubject(156.56f, 18.06f, 0f) }
    }

    @Test
    fun shoulderArchD2() {
        // D2：panel 184dp → 宽 117.11dp → 弦 187.11dp、rise 21.59dp。
        paparazzi.snapshot { ShoulderArchSubject(187.11f, 21.59f, 0f) }
    }

    @Test
    fun shoulderArchCompactD1() {
        // 紧配置 D1：宠物 ×0.8 → 宽 69.25dp → 弦 139.25dp、rise 16.07dp。
        paparazzi.snapshot { ShoulderArchSubject(139.25f, 16.07f, 0f) }
    }

    @Test
    fun shoulderArchD1Peak() {
        // D1 弹性峰值 7dp：拱峰再隆起，弦端锚定不动。
        paparazzi.snapshot { ShoulderArchSubject(156.56f, 18.06f, 7f) }
    }

    // ---- 玻璃头盒 ----

    private data class HeadAsset(
        val bitmap: Bitmap,
        val visibleTopFrac: Double,
        val visibleBottomFrac: Double,
        val visibleLeftFrac: Double,
        val visibleRightFrac: Double,
    )

    private fun loadHeadAsset(): HeadAsset {
        val candidates = listOf(
            "src/main/res/drawable-nodpi/glass_head.png",
            "app/src/main/res/drawable-nodpi/glass_head.png",
        )
        val file = candidates
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: error(
                "glass_head.png not found; tried ${candidates.joinToString()} " +
                    "from working dir ${File(".").absolutePath}",
            )
        // 不用 javax.imageio（不在 android.jar 编译类路径里）：BitmapFactory
        // 在 android.jar 内可编译，在 Paparazzi layoutlib 运行时真实解码。
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("BitmapFactory could not decode $file")
        val w = bitmap.width
        val h = bitmap.height
        val buffer = IntBuffer.allocate(w * h)
        bitmap.copyPixelsToBuffer(buffer)
        val pixels = buffer.array()
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        // alpha > 16 视为可见内容（忽略抗锯齿尘点）。
        for (y in 0 until h) {
            for (x in 0 until w) {
                val alpha = (pixels[y * w + x] ushr 24) and 0xFF
                if (alpha > 16) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        require(bottom >= top && right >= left) { "glass_head.png has no visible pixels" }
        return HeadAsset(
            bitmap = bitmap,
            visibleTopFrac = top.toDouble() / h,
            visibleBottomFrac = (bottom + 1).toDouble() / h,
            visibleLeftFrac = left.toDouble() / w,
            visibleRightFrac = (right + 1).toDouble() / w,
        )
    }

    @Test
    fun headAssetBoxD1() {
        val head = loadHeadAsset()
        val heightFrac = head.visibleBottomFrac - head.visibleTopFrac
        val widthFrac = head.visibleRightFrac - head.visibleLeftFrac
        WalkthroughGoldens.check(tag, "head-visible-height-frac", heightFrac, 0.01, "frac")
        WalkthroughGoldens.check(tag, "head-visible-width-frac", widthFrac, 0.01, "frac")
        WalkthroughGoldens.note(
            tag,
            "head-visible-bounds",
            "top=${"%.3f".format(head.visibleTopFrac)} bottom=${"%.3f".format(head.visibleBottomFrac)} " +
                "left=${"%.3f".format(head.visibleLeftFrac)} right=${"%.3f".format(head.visibleRightFrac)} " +
                "(alpha>16, asset ${head.bitmap.width}x${head.bitmap.height})",
        )
        // D1 宠物盒：宽 86.56dp、高按 1318/1043；红框＝盒子，图＝可见内容。
        val petWidth = 86.56f
        val petHeight = petWidth * 1318f / 1043f
        paparazzi.snapshot {
            Box(
                modifier = Modifier
                    .size(petWidth.dp, petHeight.dp)
                    .background(Color.White)
                    .border(2.dp, Color.Red),
            ) {
                Image(
                    bitmap = head.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(petWidth.dp, petHeight.dp),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        // 快照名由 Paparazzi 按测试方法名生成（headAssetBoxD1）。
    }
}
