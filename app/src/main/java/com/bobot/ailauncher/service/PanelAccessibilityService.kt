package com.bobot.ailauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 系统面板服务：用无障碍全局动作打开通知中心 / 控制中心（快捷设置）。
 * 需要用户在系统设置 → 无障碍中手动开启。
 */
class PanelAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile
        private var instance: PanelAccessibilityService? = null

        /** 是否已在系统中启用 */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) == 1
            if (!enabled) return false
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return services.contains(context.packageName)
        }

        /** 打开通知中心（通知栏）；返回是否成功触发 */
        fun openNotificationCenter(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        }

        /** 打开控制中心（快捷设置面板）；返回是否成功触发 */
        fun openControlCenter(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true
        }
    }
}
