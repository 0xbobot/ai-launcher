package com.bobot.ailauncher.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.pet.PetPresentedCard
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.util.rebindListener
import java.util.Calendar

/**
 * v0.27.0：重建的极简主页——只留宠物。
 * Bob 要求：和 Dock/宠物无关的全部清除。
 */
@Composable
fun HomeScreen(onOpenAppDrawer: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // v0.27.5：上滑检测改用 pointerInput（见下方 Column），删除 nestedScroll 方案

    // 通知监听重绑
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isNotificationAccessGranted(context) &&
                    !NotificationRepository.isConnected.value
                ) {
                    rebindListener(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 日历喂给宠物整理员（首页不展示）
    var calEvents by remember { mutableStateOf<List<CalEvent>?>(null) }
    LaunchedEffect(Unit) {
        if (hasCalendarPermission(context)) {
            calEvents = loadTodayEvents(context)
        }
    }
    LaunchedEffect(calEvents) {
        val t = System.currentTimeMillis()
        (calEvents ?: emptyList())
            .firstOrNull { !it.allDay && it.begin in (t + 1)..(t + 30 * 60 * 1000) }
            ?.let { PetRepository.handleIncomingCalendar(it.title, it.begin, it.location) }
    }

    // v0.27.5：上滑开 D3——直接 pointerInput 检测（nestedScroll 不可靠）
    // 非 Dock 区上滑，任何状态都进 D3
    val swipeUpHandler = rememberUpdatedState(onOpenAppDrawer)
    val swipeThresholdPx2 = with(density) { 48.dp.toPx() }

    // 极简：只有宠物（居中）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 可靠的上滑检测：累计上滑超过阈值就开 D3
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var accumY = 0f
                    var opened = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - change.previousPosition.y
                        // 只累计上滑（dy<0）
                        if (dy < 0) {
                            accumY += -dy
                            change.consume()
                            if (!opened && accumY >= swipeThresholdPx2) {
                                opened = true
                                swipeUpHandler.value()
                            }
                        } else {
                            // 下滑则重置（避免误触）
                            accumY = 0f
                        }
                        if (opened) {
                            // 已触发，消费掉剩余事件避免冲突
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PetZone()
        PetPresentedCard()
    }
}

private data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val allDay: Boolean
)

private fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

private fun loadTodayEvents(context: Context): List<CalEvent> {
    return try {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
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
                CalendarContract.Instances.ALL_DAY
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
            val ai = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (it.moveToNext() && list.size < 20) {
                list += CalEvent(
                    title = if (ti >= 0) it.getString(ti).orEmpty() else "",
                    begin = if (bi >= 0) it.getLong(bi) else 0L,
                    end = if (ei >= 0) it.getLong(ei) else 0L,
                    location = if (li >= 0) it.getString(li).orEmpty() else "",
                    allDay = if (ai >= 0) it.getInt(ai) == 1 else false
                )
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}
