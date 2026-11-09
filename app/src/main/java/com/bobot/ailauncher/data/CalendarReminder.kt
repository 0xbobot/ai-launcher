package com.bobot.ailauncher.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * 预约卡“帮我建提醒”（第十一批，设计稿 §3）：
 * 在系统日历里建一个提醒事件（时间地点＋准备清单），到点由系统日历提醒。
 *
 * 时间与内容是纯函数（JVM 单测锁死 CalendarReminderTest）；写日历要
 * WRITE_CALENDAR 授权，由宿主先拿授权再调 [createReminder]。
 * 提醒写进系统日历：到点几点响、长什么样是系统日历定的，删改在日历
 * 应用里做，启动器不管。
 */

/** 体检类提醒提前量：前一晚 20:00（禁食截止点）＝当天 00:00 往前 4 小时 */
private const val CHECKUP_REMINDER_HOURS_BEFORE_MIDNIGHT = 4L

/** 非体检类提醒提前量：开始前 2 小时 */
private const val DEFAULT_REMINDER_HOURS_BEFORE_START = 2L

/**
 * 提醒该在几点响（纯函数）：体检类按禁食截止点——前一晚 20:00
 * （预约当天 00:00 往前 4 小时）；没有禁食这类截止点的预约，按开始前 2 小时。
 */
fun reminderTimeMs(startMs: Long, isCheckup: Boolean): Long {
    return if (isCheckup) {
        startOfDayMs(startMs) - CHECKUP_REMINDER_HOURS_BEFORE_MIDNIGHT * 60L * 60 * 1000
    } else {
        startMs - DEFAULT_REMINDER_HOURS_BEFORE_START * 60L * 60 * 1000
    }
}

/**
 * 提醒提前分钟数（纯函数）：事件开始前多少分钟响。提醒时间已过
 * （用户点得晚）时夹到 0——开始时响一次，总比不提醒强。
 */
fun reminderMinutesBefore(startMs: Long, isCheckup: Boolean, nowMs: Long): Long {
    val at = reminderTimeMs(startMs, isCheckup)
    if (at <= nowMs) return 0L
    return (startMs - at) / (60L * 1000)
}

/** 提醒事件标题（纯函数）：“体检预约 08:00–09:00” */
fun reminderEventTitle(kindWord: String, startMin: Int, endMin: Int?): String =
    "$kindWord ${formatTimeRange(startMin, endMin)}"

/**
 * 选一本可写的日历（返回 calendarId，选不到返回 null）：
 * 只看可见的日历（VISIBLE=1），优先标为主要的日历（IS_PRIMARY=1），
 * 其次按 CALENDAR_ACCESS_LEVEL 够写（OWNER 700 / EDITOR 600 /
 * CONTRIBUTOR 500；READ 100 只读不能写）。
 */
fun pickWritableCalendarId(context: Context): Long? {
    return try {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            ),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null, null
        ) ?: return null
        cursor.use {
            val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val accessCol =
                it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            var firstWritable: Long? = null
            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getLong(idCol) else continue
                val access = if (accessCol >= 0) it.getInt(accessCol) else 0
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (firstWritable == null) firstWritable = id
                val primary = if (primaryCol >= 0) it.getInt(primaryCol) == 1 else false
                if (primary) return id
            }
            firstWritable
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * 建提醒：往系统日历插一个事件（标题/起止/地点/备注＝准备清单）＋一条
 * 提前提醒（Reminders 表）。成功返回 true；没授权、没可写日历、插失败
 * 都返回 false（调用方不摘卡、只提示一句）。不抛异常。
 */
fun createCalendarReminder(
    context: Context,
    action: SmartCardAction.CreateReminder,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    return try {
        if (!CalendarRepository.hasWriteCalendarPermission(context)) return false
        val calendarId = pickWritableCalendarId(context) ?: return false
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, action.title)
            put(CalendarContract.Events.DTSTART, action.startMs)
            put(CalendarContract.Events.DTEND, action.endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            action.place?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            action.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val eventUri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI, values
        ) ?: return false
        val eventId = eventUri.lastPathSegment?.toLongOrNull() ?: return false
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(
                CalendarContract.Reminders.MINUTES,
                reminderMinutesBefore(action.startMs, action.isCheckup, nowMs)
            )
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI, reminderValues
        ) ?: return false
        true
    } catch (_: Exception) {
        false
    }
}
