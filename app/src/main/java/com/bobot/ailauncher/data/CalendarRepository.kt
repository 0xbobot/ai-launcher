package com.bobot.ailauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * 日历事件（v0.48.0：日程从 HomeScreen 迁回、由七仔统一呈现；v0.58.0 加 description
 * 用于提取在线会议链接；v0.67.2 加 eventId 用于日历 fallback 直接打开事件详情）。
 * 批B ②：随日历查询从 ui/pet/PetZone 一起下沉 data 层，字段与语义不变。
 */
data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val description: String = "",
    val allDay: Boolean,
    val eventId: Long = 0L
)

/**
 * 批B ②：日历查询收口——原 PetZone.kt 文件级函数直接查 CalendarContract、
 * 校验 READ_CALENDAR 权限，违反「UI 不直接调系统」铁律。查询与权限判断下沉到此，
 * UI 只消费结果（PetZone 照常在 IO 线程调用，TodayEventsHolder 写入路径不变）。
 */
object CalendarRepository {

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    /** 今日 [本地 00:00, 次日 00:00) 窗口——从查询里抽出的纯函数，便于单测。 */
    internal fun todayWindow(nowMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    /** 今日全部日历事件（按开始时间升序，最多 20 条）；任何异常吞成空表（与原行为一致）。 */
    fun loadTodayEvents(context: Context): List<CalEvent> {
        return try {
            val (start, end) = todayWindow(System.currentTimeMillis())
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, start)
            ContentUris.appendId(builder, end)
            val cursor = context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_ID
                ),
                null, null,
                CalendarContract.Instances.BEGIN + " ASC"
            )
            val list = mutableListOf<CalEvent>()
            cursor?.use {
                val ti = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val bi = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val ei = it.getColumnIndex(CalendarContract.Instances.END)
                val li = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val di = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val ai = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val eidi = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                while (it.moveToNext() && list.size < 20) {
                    list += CalEvent(
                        title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                        begin = if (bi >= 0) it.getLong(bi) else 0L,
                        end = if (ei >= 0) it.getLong(ei) else 0L,
                        location = if (li >= 0) it.getString(li).orEmpty() else "",
                        description = if (di >= 0) it.getString(di).orEmpty() else "",
                        allDay = if (ai >= 0) it.getInt(ai) == 1 else false,
                        eventId = if (eidi >= 0) it.getLong(eidi) else 0L
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
