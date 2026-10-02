package com.bobot.ailauncher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap

/** Drawable → ImageBitmap（用于展示 PackageManager 拿到的真实应用图标） */
@Composable
fun rememberDrawableBitmap(drawable: Drawable): ImageBitmap =
    remember(drawable) { drawable.toBitmap().asImageBitmap() }

@Composable
fun AppIconImage(
    drawable: Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        bitmap = rememberDrawableBitmap(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
