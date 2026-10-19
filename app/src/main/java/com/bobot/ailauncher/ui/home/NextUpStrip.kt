package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.pet.CalEvent
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.CoverPalette
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * v0.68.34（Bob）：TODAY 紧凑条——封面模式下只露"接下来"一条。
 * 有会 → 显示会议 + 点按加入/提醒；无会 → 显示简报副行。
 * 人体工学：整卡 88dp 高可点，拇指区。
 */
@Composable
fun NextUpStrip(
    summary: String,
    nextEvent: CalEvent?,
    palette: CoverPalette,
    onMeetingTap: (CalEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMeeting = nextEvent != null
    val kicker = remember(nextEvent) {
        if (nextEvent != null) {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(nextEvent.begin)
            "接下来 · $t"
        } else "TODAY · 七仔简报"
    }
    val title = remember(nextEvent, summary) {
        if (nextEvent != null) "${nextEvent.title} · 点一下加入"
        else summary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = palette.title.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.AILauncherColors.Card.copy(alpha = 0.88f))
            .then(if (hasMeeting) Modifier.clickable { nextEvent?.let(onMeetingTap) } else Modifier)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Column {
            Row {
                TrackedText(
                    text = kicker,
                    fontSize = 12.sp,
                    tracking = 3.dp,
                    color = AILauncherColors.Accent
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 27.sp,
                color = palette.title,
                maxLines = 2
            )
        }
    }
}
