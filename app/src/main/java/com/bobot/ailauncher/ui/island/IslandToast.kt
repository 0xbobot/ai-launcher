package com.bobot.ailauncher.ui.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * v0.68.48（Bob）：灵动岛式 Toast——黑胶囊置顶，替代系统白 Toast。
 * 所有 Toast.makeText 改走 IslandToasts.show，视觉统一。
 */
data class IslandToast(
    val text: String,
    val icon: ImageVector? = null
)

object IslandToasts {
    private val _current = MutableStateFlow<IslandToast?>(null)
    val current: StateFlow<IslandToast?> = _current.asStateFlow()

    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // v0.68.56：B 方案——系统通知通道需要 Context，MainActivity 初始化时注入
    @Volatile private var appContext: android.content.Context? = null
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    /**
     * 显示灵动岛 Toast。任意线程可调（data 层也能用，不需要 Context）。
     * v0.68.56（Bob）：B 方案——优先走系统通知（真·灵动岛），
     * 发不出（无权限/异常）才回落到应用内黑胶囊。
     * 新 Toast 会顶掉旧的，2.5 秒后自动收起。
     */
    fun show(text: String, icon: ImageVector? = null, durationMs: Long = 2500) {
        // 先试系统通道
        val ctx = appContext
        if (ctx != null && IslandNotification.show(ctx, text)) {
            return  // 系统接管了，不显示应用内 pill
        }
        // 回落：应用内黑胶囊
        dismissJob?.cancel()
        _current.value = IslandToast(text, icon)
        dismissJob = scope.launch {
            delay(durationMs)
            _current.value = null
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        _current.value = null
        appContext?.let { IslandNotification.cancel(it) }
    }
}

/**
 * 灵动岛 Toast 宿主——放在 MainScreen 最顶层。
 * 黑胶囊 + 白字，从顶部滑入。
 */
@Composable
fun IslandToastHost(
    modifier: Modifier = Modifier
) {
    val toast by IslandToasts.current.collectAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            // v0.68.56：回落到状态栏下方——B 方案走系统通道时这里不显示，
            // 只有无通知权限时才用应用内 pill，必须干净不重叠
            .statusBarsPadding()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            toast?.let { t ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.92f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    t.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = t.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
