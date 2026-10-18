package com.bobot.ailauncher.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v0.68.66：设计评审 P1 —— 字阶 token。
 * 全应用统一 7 档语义字阶，替代散落的 20+ 种字号。
 * 层级：Display（杂志刊头）> Title（区块）> Headline（行标题）> Body/Callout（正文）> Footnote/Caption（辅助）。
 *
 * 行高按中文阅读优化：标题紧凑、正文宽松。
 */
object AILauncherType {
    /** 杂志刊头：应用中心/设置页大标题（32sp 宋体 Bold，字距 2sp） */
    val Display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 2.sp,
    )
    /** 区块标题：TODAY 分区头（20sp Bold） */
    val Title = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )
    /** 行标题：列表项主标题/卡片标题（17sp SemiBold） */
    val Headline = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )
    /** 正文：默认阅读文本（15sp） */
    val Body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    )
    /** 次级正文：描述/说明（14sp） */
    val Callout = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    /** 脚注：辅助说明（13sp） */
    val Footnote = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    /** 最小字号：标签/时间戳（12sp）——低于此字号中文可读性不足，禁止使用 */
    val Caption = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}
