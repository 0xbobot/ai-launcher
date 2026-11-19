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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.CrashLog
import com.bobot.ailauncher.data.CrashRecord
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.pageTextureBackground
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
import com.bobot.ailauncher.ui.theme.tickHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录（只读列表 + 系统分享 + 清空，正式版审计 ②-1）。
 * 入口是设置页“关于”区“崩溃记录”那一行；打开即标记已读，设置页行上
 * 的未读数归零。每卡 = 一份记录：时间 · 标题、全文（等宽，复用恢复屏
 * 的呈现）。冷启动 toast 说“崩溃记录在「设置 · 崩溃记录」里”，这个
 * 入口让它成真。
 */
@Composable
fun CrashLogScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    var records by remember { mutableStateOf(CrashLog.list(context)) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 打开即标记已读：冷启动 toast 的“没看过”状态清掉
    LaunchedEffect(Unit) { CrashLog.markAllRead(context) }

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
                    text = "崩溃记录",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = AILauncherType.Display.fontSize,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { view.tickHaptic(); showClearConfirm = true },
                    enabled = records.isNotEmpty()
                ) {
                    Text(
                        text = "清空",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
                TextButton(
                    onClick = {
                        view.tickHaptic()
                        val text = crashLogShareText(records)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AI 桌面崩溃记录")
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(send, "分享这份记录")
                            )
                        }.onFailure { IslandToasts.show("没有能分享的应用，换个方式试试吧") }
                    },
                    enabled = records.isNotEmpty()
                ) {
                    Text(
                        text = "分享",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
            }
        }

        if (records.isEmpty()) {
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
                            text = "这里还是空的",
                            fontSize = AILauncherType.BodyLarge.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "还没闪退过，有记录了会记在这里。",
                            fontSize = AILauncherType.Body.fontSize,
                            color = AILauncherColors.current.Body,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        } else {
            items(records) { record ->
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
                            text = "${formatCrashTime(record.epochMillis)} · ${record.title}",
                            fontSize = AILauncherType.Body.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = AILauncherColors.current.Title,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = record.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = AILauncherType.Footnote.fontSize,
                            color = AILauncherColors.current.Body,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = "清空崩溃记录",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AILauncherColors.current.Title
                )
            },
            text = {
                Text(
                    text = "全部记录都会删掉，删掉就找不回来了。",
                    fontSize = AILauncherType.Body.fontSize,
                    color = AILauncherColors.current.Body
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CrashLog.deleteAll(context)
                    records = CrashLog.list(context)
                    showClearConfirm = false
                    IslandToasts.show("已经清空了")
                }) {
                    Text(
                        text = "清空",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(
                        text = "取消",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Body
                    )
                }
            }
        )
    }
}

/** 分享用纯文本（纯函数，单测逐字锁死）：每条 = 时间 + 标题 + 全文；空列表返回 ""。 */
fun crashLogShareText(records: List<CrashRecord>): String =
    records.joinToString("\n\n---\n\n") { record ->
        "${formatCrashTime(record.epochMillis)}\n${record.title}\n${record.content}"
    }

private fun formatCrashTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
