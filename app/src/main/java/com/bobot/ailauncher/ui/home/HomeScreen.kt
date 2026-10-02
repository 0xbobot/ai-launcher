package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SimpleNotification
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val notifications by NotificationRepository.notifications.collectAsState()

    fun submit() {
        val text = input.trim()
        if (text.isBlank()) return
        val capId = routeKeyword(text)
        val ok = capId?.let { CapabilityRegistry.resolveAndLaunch(context, it) } ?: false
        if (!ok) {
            Toast.makeText(context, "演示版：已收到意图\"$text\"", Toast.LENGTH_SHORT).show()
        }
        input = ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 问候
        item {
            Column {
                Text(
                    text = greeting(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AILauncherColors.Title
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = todayText(),
                    fontSize = 14.sp,
                    color = AILauncherColors.Hint
                )
            }
        }
        // 意图输入框
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AILauncherColors.Accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("想做什么，直接告诉我…", color = AILauncherColors.Hint) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "语音输入（演示版暂为装饰）",
                        tint = AILauncherColors.Hint
                    )
                }
            }
        }
        // 正在进行
        item {
            Text(
                text = "正在进行",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AILauncherColors.Title
            )
        }
        if (!isNotificationAccessGranted(context)) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "开启通知读取后，「正在进行时」才能把会议、快递、消息主动浮上来。",
                            fontSize = 14.sp,
                            color = AILauncherColors.Body
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) {
                            Text("去开启", color = AILauncherColors.Accent)
                        }
                    }
                }
            }
        } else if (notifications.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "暂无进行中的事项",
                        fontSize = 14.sp,
                        color = AILauncherColors.Hint,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(notifications, key = { it.packageName + it.time }) { n ->
                NotificationCard(n)
            }
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

/** 演示版意图路由：关键词 → capabilityId */
private fun routeKeyword(raw: String): String? {
    val q = raw.lowercase(Locale.ROOT)
    return when {
        q.contains("打车") || q.contains("叫车") -> "dache"
        q.contains("地铁") -> "ditie"
        q.contains("火车") -> "huoche"
        q.contains("航班") || q.contains("飞机") -> "hangban"
        q.contains("酒店") -> "jiudian"
        else -> null
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
}

private fun todayText(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())

private fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(time))

@Composable
private fun NotificationCard(n: SimpleNotification) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = n.appName,
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(n.time),
                    fontSize = 12.sp,
                    color = AILauncherColors.Hint
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (n.title.isNotBlank()) {
                Text(
                    text = n.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.Title
                )
            }
            if (n.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = n.text,
                    fontSize = 14.sp,
                    color = AILauncherColors.Body,
                    maxLines = 2
                )
            }
        }
    }
}
