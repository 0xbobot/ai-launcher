package com.bobot.ailauncher.service

import android.service.notification.StatusBarNotification
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在用通知列表空态守卫单测（Bob 2026-11-08 22:15：角标全空调查——
 * 未连接时 activeNotifications 为 null，直接 filter 会 NPE，整推被
 * 外层 try/catch 吞掉、角标冻住）。只测纯函数 filterClearableNotifications；
 * 服务实例的 Binder 行为验收走 Bob 真机。
 */
class LauncherNotificationServiceTest {

    @Test
    fun `null active list becomes empty list`() {
        assertTrue(filterClearableNotifications(null).isEmpty())
    }

    @Test
    fun `empty active list stays empty`() {
        assertTrue(
            filterClearableNotifications(emptyArray<StatusBarNotification>()).isEmpty()
        )
    }
}
