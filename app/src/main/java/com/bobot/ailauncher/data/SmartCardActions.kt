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
 *   再找云闪付 / 支付宝 / 微信（按最近使用排），找不到才打开短信会话；
 *   接到还款页深链的先试深链直达（核到的才接，核不到的退回打开应用）。
 *   不用写死一家银行。
 * - 看预约：打开短信会话（导航去体检地点是下一步，先只看预约本身）。
 * - 回拨：ACTION_DIAL（打开拨号器并填好号码，用户再点一下拨出），
 *   不直接拨号、不读通话记录。
 * - 查看：点通知自带的 contentIntent，直达应用内页面。
 * - 帮我建提醒：不走这里（要先拿 WRITE_CALENDAR 授权），宿主拦截执行。
 * - 加入会议 / 看日程（第十二批，日历卡）：有会议链接直开链接，无链接
 *   打开日历应用（再兜底事件详情）。
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

    /** 本机已装应用的（包名，标签）对（单测不直接调，只供还款目标解析用） */
    private fun installedLabelPairs(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { app ->
                val label = try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    return@mapNotNull null
                }
                app.packageName to label
            }
    }

    /**
     * 支付应用候选（纯函数，单测锁死）：标签含云闪付 / 支付宝 / 微信的已装应用。
     * 还款目标的第二顺位（银行应用之后）：这三家都能还信用卡（云闪付/支付宝
     * 0 手续费、微信钱包里也有信用卡还款），按最近使用排。
     */
    fun matchPaymentAppCandidates(
        installed: List<Pair<String, String>>
    ): List<String> {
        val names = listOf("云闪付", "支付宝", "微信")
        return installed.filter { (_, label) ->
            val norm = label.replace(" ", "")
            names.any { norm.contains(it) }
        }.map { it.first }
    }

    /**
     * 按最近使用挑一个（纯函数，单测锁死）：最近用过的那家；都没用过
     * （lastUsed 全是 0）时取列表第一个（声明/传入顺序），不随机。
     * 银行与支付两路共用同一条排序逻辑（第十一批：复用意图逻辑，
     * 不另写一套匹配器）。
     */
    fun selectByRecency(
        candidates: List<String>,
        lastUsed: (String) -> Long
    ): String? {
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { lastUsed(it) }
            ?.takeIf { lastUsed(it) > 0L }
            ?: candidates.firstOrNull()
    }

    /**
     * 还款目标应用（第十一批，设计稿 §3）：银行简称匹配的银行应用
     * （最常用那家）→ 云闪付 / 支付宝 / 微信（按最近使用排）→ null
     * （调用方退回短信会话）。银行与支付两路都走同一条标签匹配＋
     * 最近使用排序（matchBankAppCandidates / matchPaymentAppCandidates
     * ＋ selectByRecency），不另写一套匹配器。
     */
    fun resolveRepaymentTarget(context: Context, bankName: String): String? {
        return try {
            val installed = installedLabelPairs(context)
            selectRepaymentTarget(
                matchBankAppCandidates(installed, bankName),
                matchPaymentAppCandidates(installed)
            ) { AppUsageTracker.effectiveLastUsed(context, it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 还款目标顺序（纯函数，单测锁死）：银行候选先挑，挑不到再挑支付候选；
     * 两路都按最近使用排（selectByRecency）。银行与支付的相对顺序是
     * 写死的业务口径（银行应用最懂这张卡），不是按使用时间混排。
     */
    fun selectRepaymentTarget(
        bankCandidates: List<String>,
        paymentCandidates: List<String>,
        lastUsed: (String) -> Long
    ): String? {
        return selectByRecency(bankCandidates, lastUsed)
            ?: selectByRecency(paymentCandidates, lastUsed)
    }

    /**
     * 还款页深链（第十一批，设计稿 §3；纯函数，单测锁死）：
     * 只收社区多源核实过的还款页地址——非官方文档、未在真机上逐条验证，
     * 执行层先校验能不能接住（resolveActivity），接不住退回打开应用。
     * 核到的：
     * - 支付宝（com.eg.android.AlipayGphone）：信用卡还款 appId 09999999，
     *   两处独立社区列表同值（CSDN 2020、xydida.com）。
     * - 云闪付（com.unionpay）：信用卡还款 upwallet://rn/rncredit，
     *   GitHub gist 多条评论同值。
     * - 掌上生活（com.cmbchina.ccd.pluto.cmbActivity）：招商银行信用卡还款
     *   cmblife://cfp/BillRepayment?needLogin=1&bindCard=1000，
     *   xydida.com 列表；包名经小米/360 两家应用商店核对一致。
     * 核不到的（退回打开应用）：
     * - 微信：只找到钱包首页 weixin://dl/wallet、扫一扫等通用入口，
     *   没有信用卡还款页的深链。
     * - 中信银行：只有裸 scheme citicbank://（等于打开应用，无还款页路径）；
     *   动卡空间也没有公开的还款页深链。
     * - 其他银行：没有公开的还款页深链。
     */
    fun repaymentDeepLink(packageName: String): String? = when (packageName) {
        "com.eg.android.AlipayGphone" ->
            "alipays://platformapi/startapp?appId=09999999"
        "com.unionpay" -> "upwallet://rn/rncredit"
        "com.cmbchina.ccd.pluto.cmbActivity" ->
            "cmblife://cfp/BillRepayment?needLogin=1&bindCard=1000"
        else -> null
    }

    /**
     * 账单卡的动作（第十一批）：还款目标应用（银行 → 云闪付/支付宝/微信，
     * 核到深链的带深链直达还款页）→ 短信会话 → 默认短信应用。
     */
    fun repaymentAction(
        context: Context,
        address: String,
        bankName: String
    ): SmartCardAction {
        val target = resolveRepaymentTarget(context, bankName)
        if (target != null) {
            return SmartCardAction.LaunchApp(target, repaymentDeepLink(target))
        }
        return smsCardAction(context, address, null)
    }

    /**
     * 短信类卡片的动作：银行应用（去还款）→ 短信会话 → 默认短信应用。
     * 地址不是号码（如字母发件方）时 smsto: 打不开，直接走默认短信应用。
     * 账单（bankName 非空）走 repaymentAction：银行 → 云闪付/支付宝/微信
     * （带还款页深链）→ 短信会话；预约（bankName 为空）直接走短信会话。
     */
    fun smsCardAction(
        context: Context,
        address: String,
        bankName: String?
    ): SmartCardAction {
        if (bankName != null) {
            return repaymentAction(context, address, bankName)
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
                    // 带深链时先试深链直达（限定到该包、校验能接住），
                    // 接不住或起不来时退回打开应用本体——深链是社区核实的
                    // 地址，执行层不许只认深链、必须带退回兜底。
                    val deepLink = action.deepLinkUri
                    if (deepLink != null) {
                        val deepIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(deepLink)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .setPackage(action.packageName)
                        // 深链起不来（没接住、抛异常）都退回打开应用本体，
                        // 不直接判失败。
                        val deepOk = try {
                            launchIfResolvable(context, deepIntent)
                        } catch (_: Exception) {
                            false
                        }
                        if (deepOk) true else launchApp(context, action.packageName)
                    } else {
                        launchApp(context, action.packageName)
                    }
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
                // 预约卡的“帮我建提醒”由宿主拦截（要先拿 WRITE_CALENDAR
                // 授权）、走 CalendarReminderWriter 执行，永不进这里；
                // 万一漏网也不 markDone、不写日历。
                is SmartCardAction.CreateReminder -> false
                // 日历卡（第十二批）：有会议链接直开链接，无链接打开日历
                // 应用（再兜底事件详情），与 PetZone 的
                // joinCalendarEvent 同一条链（CalendarMeeting）。
                is SmartCardAction.CalendarEvent ->
                    openCalendarEvent(context, action.eventId, action.meetingUrl)
            }
        } catch (_: Exception) {
            false
        }
        if (opened) {
            SmartCardRepository.markDone(context, card)
        }
        return opened
    }

    /** 打开应用本体（getLaunchIntentForPackage 拿不到时返回 false） */
    private fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /** 默认短信应用包名（小标与短信会话兜底用；拿不到返回 null） */
    fun defaultSmsPackage(context: Context): String? {
        return try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
    }

    /** 包名 → 应用显示标签（小标用；读不到返回 null，调用方不画小标） */
    fun appLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun launchIfResolvable(context: Context, intent: Intent): Boolean {
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else false
    }
}
