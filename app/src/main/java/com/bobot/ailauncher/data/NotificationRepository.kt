package com.bobot.ailauncher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 桌面"正在进行时"信息流的一条卡片数据 */
data class SimpleNotification(
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
    val packageName: String
)

/** 通知仓库单例：由 LauncherNotificationService 写入，首页读取展示 */
object NotificationRepository {
    private val _notifications = MutableStateFlow<List<SimpleNotification>>(emptyList())
    val notifications: StateFlow<List<SimpleNotification>> = _notifications.asStateFlow()

    /** 监听服务是否已连接（授权发生在 App 运行中时，系统可能尚未回调 onListenerConnected） */
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun update(list: List<SimpleNotification>) {
        _notifications.value = list
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }
}
