package com.bobot.ailauncher.ui.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
/** 系统壁纸一次加载的全部产物：位图（玻璃内绘）＋带分类（玻璃档位）＋带亮度（简报自适应字色）。 */
internal data class LoadedWallpaper(
    val bitmap: Bitmap,
    val kinds: List<BackdropKind>,
    val luminance: List<GlassPolicy.BandLuminance>,
)

@Composable
internal fun SystemWallpaperLayer(state: GlassBackdropState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 壁纸更换跟上（简报自适应字色用）：系统壁纸颜色变化时 bump tick 触发重载。
    // API 27+（minSdk 29 可用）；回调走主线程 Handler，状态写安全。只跟系统壁纸，
    // 锁屏壁纸变化不触发（FLAG_SYSTEM 过滤）。
    DisposableEffect(context) {
        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) state.wallpaperTick++
        }
        runCatching {
            wm?.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }
        onDispose {
            runCatching { wm?.removeOnColorsChangedListener(listener) }
        }
    }
    LaunchedEffect(state.wallpaperTick) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadSystemWallpaper(context) }.getOrNull()
        }
        if (loaded != null) {
            bitmap = loaded.bitmap
            state.backdropBands = loaded.kinds
            state.backdropLuminance = loaded.luminance
        } else {
            // 读不到（动态壁纸 getDrawable 返回 null、个别厂商限制、异常）：
            // 亮度清空 → 简报回退 347 纱；玻璃旧位图保持既有降级行为（不破相）。
            state.backdropLuminance = emptyList()
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

private fun loadSystemWallpaper(context: Context): LoadedWallpaper? {
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
    // 背景分类（v0.68.96 区域版）：48×48 缩略图按水平带统计（亮度起伏/饱和/
    // 局部细节），逐带分类——整图均值会被大面积低纹理区域（如浅蓝天）稀释，
    // 曾把蓝天+远山照片误判成轻霜（叠白 0.38 吃掉通透感）。位图即屏幕尺寸
    // （cover 绘制），缩略图的带与画面纵向位置一一对应。
    val thumb = 48
    val small = Bitmap.createScaledBitmap(bmp, thumb, thumb, true)
    val pixels = IntArray(thumb * thumb)
    small.getPixels(pixels, 0, thumb, 0, 0, thumb, thumb)
    // 带分类与带亮度同批缩略图、同带划分，两份数据一一对应；简报只取顶部带。
    return LoadedWallpaper(
        bitmap = bmp,
        kinds = GlassPolicy.classifyBands(pixels, thumb, thumb),
        luminance = GlassPolicy.bandLuminance(pixels, thumb, thumb),
    )
}
