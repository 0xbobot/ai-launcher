package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType

/**
 * 未读组左滑清理行（Bob 2026-11-08 08:02 反馈①：未读组里的行支持左滑清理）。
 * 竖屏 AppCenter 与横屏 AppCenterLandscape 共用同一组件、同一手势。
 *
 * 行为：
 * - 左滑（EndToStart）→ onClear（取消该应用的在用通知，走通知监听服务）；
 *   松手后行弹回原位（不真正 dismiss——行靠角标归 0 离开未读组；若角标未归 0
 *   如短信库口径，行留在原位、角标已更新）。
 * - enabled=false（该应用无在用通知可取消，如短信库口径、飞书无通知）时，
 *   手势关闭、底色不画，行只剩点按/长按；用户去应用里读完自然清。
 * - 右滑不启用（只清通知，不做别的）。
 *
 * 手势隔离：行内 AppRow 的滑动浮层（★/眼睛）由调用方传 swipeActionsEnabled=false
 * 关掉，避免两套横滑抢事件；竖屏列表竖滑与右侧 rail 不受影响（横滑只消费横向拖动）。
 * 横屏注意：列是横向滚动的 LazyRow，左滑与列横滚同向——从未读行起手左滑会先触发
 * 清理（行内手势优先），要横滚列请从其他行或空白处起手（见收据）。
 */
@Composable
internal fun ClearableUnreadRow(
    enabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onClear()
                // 不真正 dismiss（见上），弹回原位。
                false
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        backgroundContent = {
            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = AILauncherColors.current.Alert,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "清除通知",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "清除",
                            fontSize = AILauncherType.Body.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        },
        content = content
    )
}
