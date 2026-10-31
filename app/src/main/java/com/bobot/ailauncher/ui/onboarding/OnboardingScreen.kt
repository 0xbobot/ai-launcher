@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.onboarding

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.pet.HybridPet
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic

// 引导批（2026-10-31，Bob 改定）：只剩 1 屏——389 演示页作唯一一页，
// 第 2 屏权限组与第 3 屏完成＋默认二选一删掉（380 三页结构作废）；
// 权限不再引导里集中问：缺失由首页宠物气泡提示一次、设置→权限常驻页
// 随时补开（需要时再问）；低电量／无障碍弹窗时机一字不动。

/** 欢迎／完成屏卡片上沿的七仔宽度（与 Dock 上沿同一身体、同一 HybridPet） */
private val GuidePetWidth = 96.dp

/** 七仔压入卡片顶缘（Dock 同款 5dp，前面图层、不被卡片盖住下半身） */
private val PetPress = 5.dp

@Composable
fun OnboardingNav(onFinish: () -> Unit) {
    // 单页：只剩 389 演示页；“开始”与“跳过”均直接完成进桌面
    StepWelcome(onNext = onFinish, onSkip = onFinish)
}

@Composable
private fun StepScaffold(
    onSkip: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 刘海避让（Bob 2026-10-25 全页检查）：横屏下两侧挖孔区；两侧吃
            // displayCutout inset——竖屏侧边 inset 为 0，零变化；顶部 32dp 本就
            // 高于标准状态栏，不另加。
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Start + WindowInsetsSides.End
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // F-10：引导页点按补 tick（走 Haptics 两档门面，下同）
            TextButton(onClick = { view.tickHaptic(); onSkip() }) {
                Text("跳过", color = AILauncherColors.current.Hint)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val view = LocalView.current
    Button(
        // F-10：引导主按钮统一 confirm 档；金色底用深色字（Theme.kt 口径，
        // 白字压金底可读性不够）
        onClick = { view.confirmHaptic(); onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AILauncherColors.current.Accent)
    ) {
        Text(
            text = text,
            color = Color(0xFF1F1D1A),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
        )
    }
}

/**
 * 简报杂志风卡片（暖纸底 Card 色、宋体大标题、金色 accent、28dp 圆角）：
 * petWidth 非空时七仔坐卡片上沿——卡片下推（盒高 − 压入量），七仔后画、
 * 前面图层，脚底压进顶缘 5dp（Dock 同款“坐在上面”画法，不被盖住下半身）。
 * 引导语即卡片内容（七仔气泡样式，不另起一套视觉）；引导期的七仔只作
 * 向导、不接点按动作（语义清为空点可读“七仔”，不承诺点按行为）。
 */
@Composable
private fun GuideCard(
    petWidth: Dp?,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (petWidth != null) petWidth * (1318f / 1043f) - PetPress else 0.dp
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
        if (petWidth != null) {
            HybridPet(
                petWidth = petWidth,
                onPetTap = {},
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clearAndSetSemantics { contentDescription = "七仔" }
            )
        }
    }
}

/** 引导第 1 屏演示三态：初始／思考／结果（脚本化 mock，不调真实意图管线） */
private enum class DemoState { Idle, Thinking, Result }

/** Drawable → ImageBitmap（QuickActions 同款画法，不引 core-ktx） */
private fun Drawable.toDemoImageBitmap(): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp.asImageBitmap()
}

/** 第 1 屏：欢迎——可玩演示（Bob 2026-10-31 点选 B 方向，3 条好处行删掉换演示） */
@Composable
private fun StepWelcome(onNext: () -> Unit, onSkip: () -> Unit) {
    val view = LocalView.current
    var demo by remember { mutableStateOf(DemoState.Idle) }
    // 思考态约 900ms 后出结果；状态一切换或离屏，延时自动取消
    LaunchedEffect(demo) {
        if (demo == DemoState.Thinking) {
            delay(900)
            demo = DemoState.Result
        }
    }
    StepScaffold(onSkip = onSkip) {
        GuideCard(petWidth = GuidePetWidth) {
            Text(
                // 显式换行：两行断句，读着像设计不是断坏；字号／字体／颜色不动
                text = "说句话，\n手机就帮你干活",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AILauncherColors.current.Title,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 演示胶囊：简化版，预置文案＋首页同款金色提交盘（48dp 触控框＋
            // 40dp 金盘＋20dp 深色箭头）；点盘只推演示状态，不启动应用、
            // 不联网、不申请权限、不调真实意图／搜索代码
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(AILauncherColors.current.AccentSoft)
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "帮我打开微信",
                    fontSize = 16.sp,
                    color = AILauncherColors.current.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            // 思考中忽略点按（防重复计时）；结果态再点即重播
                            if (demo != DemoState.Thinking) {
                                view.tickHaptic()
                                demo = DemoState.Thinking
                            }
                        }
                        .semantics { contentDescription = "试试演示" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AILauncherColors.current.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AILauncherColors.Light.Title,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 演示结果区：下限 72dp，初始／思考态不跳布局；结果卡按内容长高
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                when (demo) {
                    DemoState.Idle -> Text(
                        text = "点一下右边的金色圆钮试试",
                        fontSize = 14.sp,
                        color = AILauncherColors.current.Hint
                    )
                    DemoState.Thinking -> Text(
                        text = "七仔正在找…",
                        fontSize = 14.sp,
                        color = AILauncherColors.current.Hint
                    )
                    DemoState.Result -> DemoResultCard()
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            // “开始”常在：演示不设门槛，不玩也能直接开始／跳过
            PrimaryButton(text = "开始", onClick = onNext)
        }
    }
}

/**
 * 演示结果卡（mock，到此为止）：微信图标 tile＋“微信”＋副文案＋右上角
 * “演示”角标。图标优先取 com.tencent.mm 真图标，取不到画绿底圆角方块＋
 * 白色“微”字，不引新图片资产；整卡不设点按动作，不假装能打开。
 */
@Composable
private fun DemoResultCard() {
    val context = LocalContext.current
    val iconBitmap = remember {
        try {
            context.packageManager.getApplicationIcon("com.tencent.mm")?.toDemoImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AILauncherColors.current.Card)
            .border(1.dp, AILauncherColors.current.Divider, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF07C160)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "微",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "微信",
                fontSize = 16.sp,
                color = AILauncherColors.current.Title
            )
            Text(
                text = "演示：正式用时点一下就打开",
                fontSize = 14.sp,
                color = AILauncherColors.current.Body
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 右上角“演示”角标（行尾顶对齐，不压文字；装饰语义）
        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .clip(RoundedCornerShape(8.dp))
                .background(AILauncherColors.current.AccentSoft)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { invisibleToUser() }
        ) {
            Text(
                text = "演示",
                fontSize = 11.sp,
                color = AILauncherColors.current.Accent
            )
        }
    }
}

