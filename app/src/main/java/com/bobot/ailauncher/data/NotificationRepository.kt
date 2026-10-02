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

    fun update(list: List<SimpleNotification>) {
        _notifications.value = list
    }
}
