package com.bobot.ailauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * 智能卡片的下一步动作执行（第一阶段，447/v1.2.0）：
 * 点动作即算处理完一张——成功打开才 markDone，失败不摘卡、只提示一句。
 *
 * 动作落点（设计稿 §3：用量统计决定动作落到用户真正用的那个应用上）：
 * - 去还款：先找本机已装的银行应用（标签含银行简称、按最近使用排），
 *   找不到才打开短信会话；不用写死一家银行。
 * - 看预约：打开短信会话（导航去体检地点是下一步，先只看预约本身）。
 * - 回拨：ACTION_DIAL（打开拨号器并填好号码，用户再点一下拨出），
 *   不直接拨号、不读通话记录。
 * - 查看：点通知自带的 contentIntent，直达应用内页面。
 */
object SmartCardActions {

    /** 纯函数：短信会话 URI（单测锁死） */
    fun smsUri(address: String): String = "smsto:$address"

    /** 纯函数：拨号 URI（单测锁死） */
    fun telUri(number: String): String = "tel:$number"

    /**
     * 标签匹配（纯函数，单测锁死）：已装应用里标签含银行简称的候选。
     * 输入是（包名，标签）对；返回包名（调用方再按最近使用排）。
     */
    fun matchBankAppCandidates(
        installed: List<Pair<String, String>>,
        bankName: String
    ): List<String> {
        val short = bankName
            .replace("银行", "")
            .replace("信用卡", "")
            .trim()
        if (short.isEmpty()) return emptyList()
        val normShort = short.replace(" ", "").lowercase()
        return installed.filter { (_, label) ->
            label.replace(" ", "").lowercase().contains(normShort)
        }.map { it.first }
    }

    /**
     * 去还款落到哪家银行应用：标签含银行简称的已装应用里，最近用过的那家
     * （用量统计 effectiveLastUsed：系统最后前台时间优先，未授权退回自记）。
     * 找不到返回 null，调用方退回短信会话。
     */
    fun resolveBankApp(context: Context, bankName: String): String? {
        return try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .mapNotNull { app ->
                    val label = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                    app.packageName to label
                }
            val candidates = matchBankAppCandidates(installed, bankName)
            candidates.maxByOrNull { AppUsageTracker.effectiveLastUsed(context, it) }
                ?.takeIf { AppUsageTracker.effectiveLastUsed(context, it) > 0L }
                ?: candidates.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 短信类卡片的动作：银行应用（去还款）→ 短信会话 → 默认短信应用。
     * 地址不是号码（如字母发件方）时 smsto: 打不开，直接走默认短信应用。
     */
    fun smsCardAction(
        context: Context,
        address: String,
        bankName: String?
    ): SmartCardAction {
        if (bankName != null) {
            val bankApp = resolveBankApp(context, bankName)
            if (bankApp != null) return SmartCardAction.LaunchApp(bankApp)
        }
        val dialable = address.isNotBlank() &&
            address.all { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (dialable) return SmartCardAction.SmsThread(address)
        return try {
            val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSms != null) SmartCardAction.LaunchApp(defaultSms)
            else SmartCardAction.SmsThread(address)
        } catch (_: Exception) {
            SmartCardAction.SmsThread(address)
        }
    }

    /** 通知类卡片的动作：contentIntent 直达应用内页面，没有就打开该应用 */
    fun notificationAction(n: SimpleNotification): SmartCardAction {
        val intent = n.contentIntent
        return if (intent != null) SmartCardAction.OpenNotification(intent)
        else SmartCardAction.LaunchApp(n.packageName)
    }

    /**
     * 执行动作并在成功时 markDone。返回 true 表已打开（卡片可摘），
     * false 表没打开（卡片保留；提示由调用方 UI 做，不在这里弹）。
     */
    fun performAction(context: Context, card: SmartCard): Boolean {
        val opened = try {
            when (val action = card.action) {
                is SmartCardAction.SmsThread -> {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(smsUri(action.address))
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIfResolvable(context, intent)
                }
                is SmartCardAction.Dial -> {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse(telUri(action.number))
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIfResolvable(context, intent)
                }
                is SmartCardAction.LaunchApp -> {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(action.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent != null) {
                        context.startActivity(intent)
                        true
                    } else false
                }
                is SmartCardAction.OpenNotification -> {
                    try {
                        action.pendingIntent?.send()
                        action.pendingIntent != null
                    } catch (_: Exception) {
                        false
                    }
                }
                // 引导卡的“连接”由宿主拦截、走 opener 执行，永不进这里；
                // 万一漏网也不 markDone、不打开任何东西。
                is SmartCardAction.Connect -> false
            }
        } catch (_: Exception) {
            false
        }
        if (opened) {
            SmartCardRepository.markDone(context, card)
        }
        return opened
    }

    private fun launchIfResolvable(context: Context, intent: Intent): Boolean {
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }
}
