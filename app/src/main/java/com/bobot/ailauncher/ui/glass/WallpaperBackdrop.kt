package com.bobot.ailauncher.ui.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统壁纸内绘层（玻璃捕获的前提）：
 *
 * 本应用的壁纸原本是「系统壁纸透出」（themes.xml windowShowWallpaper=true），
 * 应用内任何捕获（自研引擎或 Haze）都看不见它。这一层把当前系统壁纸读出来
 * 内绘到桌面最底层，与原罩纱一起录进背景图层，玻璃才有真实内容可折射。
 *
 * 读不到时（动态壁纸 getDrawable 返回 null、个别厂商限制、异常）不绘制任何东西：
 * 系统壁纸照常透出，背景分类保持 FLAT（轻霜档），玻璃降级但不破相。
 * 位图按屏幕尺寸中心裁剪绘制，近似系统壁纸的呈现方式。
 */
@Composable
internal fun SystemWallpaperLayer(state: GlassBackdropState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadSystemWallpaper(context) }.getOrNull()
        }
        if (loaded != null) {
            bitmap = loaded.first
            state.backdropKind = loaded.second
        }
    }
    val bmp = bitmap ?: return
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

private fun loadSystemWallpaper(context: Context): Pair<Bitmap, BackdropKind>? {
    val wm = WallpaperManager.getInstance(context) ?: return null
    val drawable = runCatching { wm.drawable }.getOrNull() ?: return null
    val dm = context.resources.displayMetrics
    val w = dm.widthPixels.coerceAtLeast(1)
    val h = dm.heightPixels.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // 中心裁剪：按 cover 方式把壁纸画进屏幕尺寸位图（与系统呈现近似）
    val iw = drawable.intrinsicWidth.coerceAtLeast(1)
    val ih = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = maxOf(w.toFloat() / iw, h.toFloat() / ih)
    val dw = (iw * scale).toInt().coerceAtLeast(1)
    val dh = (ih * scale).toInt().coerceAtLeast(1)
    val left = (w - dw) / 2
    val top = (h - dh) / 2
    drawable.setBounds(left, top, left + dw, top + dh)
    drawable.draw(canvas)
    // 背景分类：24×24 缩略图统计亮度起伏与色彩（纯逻辑在 GlassPolicy）
    val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
    val pixels = IntArray(24 * 24)
    small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    val (stdDev, satMean) = GlassPolicy.backdropStats(pixels)
    return bmp to GlassPolicy.classifyBackdrop(stdDev, satMean)
}
