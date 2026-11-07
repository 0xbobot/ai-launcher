package com.bobot.ailauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.formatBadgeCount
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType

/**
 * 未读角标（Bob 2026-11-07 晚：Dock + 应用中心显示消息数量，参考原版桌面的角标）。
 *
 * 样式（我们桌面语言）：红色圆角 pill、白字加粗，贴在图标右上角、微微压住图标边缘；
 * 1 位数是圆形，多位数横向拉长成 pill；1–99 显示数字，≥100 显示 "99+"，0 条不画。
 * 纯视觉覆盖层：不加点击、不消费触摸（点按仍走图标/行本身；Dock 长按菜单不受影响）。
 * 高度由调用方按图标尺寸传（Dock 56dp 图标→20dp，46/48/44dp 图标→18dp），
 * 字号走 Caption token；颜色走 Palette 的 Alert token，不写死色值。
 * Dock 与应用中心（竖屏/横屏）共用这一个组件，颜色、字号、圆角、99+ 口径一致。
 */
@Composable
fun AppBadge(
    count: Int,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val text = formatBadgeCount(count) ?: return
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = height)
            .height(height)
            .background(
                color = AILauncherColors.current.Alert,
                shape = RoundedCornerShape(height / 2)
            )
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = AILauncherType.Caption.fontSize,
            lineHeight = AILauncherType.Caption.lineHeight,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
