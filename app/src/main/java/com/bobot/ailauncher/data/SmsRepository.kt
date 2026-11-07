package com.bobot.ailauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 短信读取（设计稿 §2.1 消息连接器主路径）：
 * 直接申请 READ_SMS（设备上显示为“读取消息”）后读近期短信。
 * 只读近期（默认 7 天）、只解析账单/预约类（见 SmartCardParser），
 * 解析完不长期存原文（设计稿 §4.5）。
 *
 * Play 版注意：Google Play 规定只有默认短信处理程序才能声明 READ_SMS，
 * 启动器不是默认短信应用——Play 版上架的同时不能声明本权限（见
 * AndroidManifest 注释与设计稿 §1、§6）。本权限只走测试版 / OTA 渠道。
 */
data class SmsMessage(
    /** 发件方地址（银行短信号码如 95558，或发件方号码） */
    val address: String,
    val body: String,
    /** 收到时间（毫秒） */
    val date: Long
)

object SmsRepository {
    private const val TAG = "SmsRepository"
    private const val DEFAULT_DAYS_BACK = 7L
    private const val DEFAULT_LIMIT = 200

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 读近期短信（最新在前）。任何异常（无权限、提供程序不可用）都吞成空表，
     * 调用方按“未接上”处理，不报错、不弹占位条。读到的条数只记 Log.d
     * （448：下次“卡片没出现”先看 logcat，读到几条一目了然）。
     */
    fun loadRecentSms(
        context: Context,
        daysBack: Long = DEFAULT_DAYS_BACK,
        limit: Int = DEFAULT_LIMIT
    ): List<SmsMessage> {
        if (!hasSmsPermission(context)) {
            Log.d(TAG, "loadRecentSms: no READ_SMS permission, 0 sms")
            return emptyList()
        }
        return try {
            val since = System.currentTimeMillis() - daysBack * 24L * 60 * 60 * 1000
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val out = mutableListOf<SmsMessage>()
            context.contentResolver.query(
                uri, projection,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && out.size < limit) {
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (body.isNotBlank()) out += SmsMessage(address, body, date)
                }
            }
            Log.d(TAG, "loadRecentSms: read=${out.size} sms")
            out
        } catch (e: Exception) {
            Log.d(TAG, "loadRecentSms failed, 0 sms", e)
            emptyList()
        }
    }
    /**
     * 短信库未读数（角标用，Bob 2026-11-07 晚：角标要的是应用的未读数据）。
     * 收件箱里 read=0 的短信条数——最准的短信未读数，不受通知栏清掉影响。
     * 只计数不读内容。无权限/异常返回 0（调用方按“本路无信号”处理）。
     */
    fun countUnreadSms(context: Context): Int {
        if (!hasSmsPermission(context)) {
            Log.d(TAG, "countUnreadSms: no READ_SMS permission, 0")
            return 0
        }
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = 0",
                null, null
            )?.use { cursor -> cursor.count } ?: 0
        } catch (e: Exception) {
            Log.d(TAG, "countUnreadSms failed, 0", e)
            0
        }
    }
}

/**
 * 联系人姓名查询（设计稿 §2.1 联系人连接器，可选增强）：
 * 只按号码查姓名（未接来电卡片显示人名而不是一串数字），核心回拨不用它也能做。
 * 未授权或查不到时返回 null，调用方只显示号码，不报错。
 */
object ContactNames {
    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    fun lookup(context: Context, number: String): String? {
        if (!hasContactsPermission(context)) return null
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
