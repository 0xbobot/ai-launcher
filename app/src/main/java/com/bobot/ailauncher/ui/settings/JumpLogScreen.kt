package com.bobot.ailauncher.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.UnreadJumpLog
import com.bobot.ailauncher.data.UnreadJumpLogLogic
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.pageTextureBackground
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
import com.bobot.ailauncher.ui.theme.tickHaptic

/**
 * 未读跳转日志（只读，Bob 2026-11-11 21:59：删质检、加日志）。
 * 入口是设置页“关于”区那一行；离开再进会重新读文件。
 * 每卡 = 一次点按：应用名 · 时间、一句话摘要、原始 JSON 事件行
 * （tap 一行、outcome 一行，若有）；没有内容字段可看——日志本身就
 * 不记通知标题/正文/聊天名/人名（见 UnreadJumpLog 的隐私边界）。
 */
@Composable
fun JumpLogScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val entries = remember {
        UnreadJumpLogLogic.groupByTap(UnreadJumpLog.readRecent(context))
    }
    val labels = remember(entries) {
        entries.map { it.pkg }.toSet().associateWith { pkg ->
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            }.getOrNull() ?: pkg
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pageTextureBackground()
            .statusBarsTopPadding()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Start + WindowInsetsSides.End
                )
            ),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "未读跳转日志",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = AILauncherType.Display.fontSize,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        view.tickHaptic()
                        val text = UnreadJumpLog.shareText(context)
                        if (text.isBlank()) {
                            IslandToasts.show("暂无日志可分享")
                            return@TextButton
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AI桌面未读跳转日志")
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, "分享跳转日志")
                            )
                        }.onFailure { IslandToasts.show("没有可用的分享方式") }
                    },
                    enabled = entries.isNotEmpty()
                ) {
                    Text(
                        text = "分享",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AILauncherColors.current.Card
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .shadow(
                            8.dp,
                            RoundedCornerShape(20.dp),
                            spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f)
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "暂无记录",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点一行未读行，就会记下来。",
                            fontSize = AILauncherType.Body.fontSize,
                            color = AILauncherColors.current.Body,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        } else {
            items(entries) { entry ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AILauncherColors.current.Card
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .shadow(
                            8.dp,
                            RoundedCornerShape(20.dp),
                            spotColor = AILauncherColors.current.Title.copy(alpha = 0.08f)
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "${labels[entry.pkg] ?: entry.pkg} · " +
                                UnreadJumpLogLogic.formatTime(entry.tapTs),
                            fontSize = AILauncherType.Body.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = UnreadJumpLogLogic.summarize(entry),
                            fontSize = AILauncherType.Body.fontSize,
                            color = AILauncherColors.current.Body,
                            lineHeight = 22.sp
                        )
                        entry.tap?.let { tap ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = UnreadJumpLogLogic.toJson(tap),
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp
                            )
                        }
                        entry.outcome?.let { outcome ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = UnreadJumpLogLogic.toJson(outcome),
                                fontSize = AILauncherType.Footnote.fontSize,
                                color = AILauncherColors.current.Hint,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
