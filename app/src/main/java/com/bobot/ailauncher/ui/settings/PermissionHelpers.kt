package com.bobot.ailauncher.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.bobot.ailauncher.data.CalendarRepository

/** 检测本应用是否已被设为默认桌面 */
fun isDefaultLauncher(context: Context): Boolean {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return ri?.activityInfo?.packageName == context.packageName
}

/** 检测通知读取权限是否已开启 */
fun isNotificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** 检测日历读取权限是否已开启（CalendarRepository 的 UI 侧门面） */
fun isCalendarGranted(context: Context): Boolean =
    CalendarRepository.hasCalendarPermission(context)

/** 跳系统设置：设为默认桌面（App 内完成不了，只能带用户去选） */
fun openHomeSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** 跳系统设置：通知读取 */
fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** 跳系统设置：应用详情（日历被永久拒绝后，授权框已弹不出时的兜底） */
fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * v0.68.98（F-12）同款：随 ON_RESUME 刷新的权限状态——初值组合期读一次，
 * 从设置页返回即重读（此前状态行在组合期直读、返回后无重组触发就一直说
 * 「尚未…」，用户明明设好了还被状态行说谎）。
 * 与 F-12 的区别：返回 MutableState，调用方在运行时授权框回调里也可直接
 * 写 state.value——授权框弹在应用上方、不走 ON_RESUME，单靠重读刷不到。
 */
@Composable
fun rememberPermissionState(check: () -> Boolean): MutableState<Boolean> {
    val state = remember { mutableStateOf(check()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = check()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    return state
}

/**
 * 授权由未开→已开翻转时跑一次 onGranted（通知读取授权后强制 rebindListener
 * 用，让 onListenerConnected 立刻回调）。初值已开不跑，避免已绑定时多余重绑。
 */
@Composable
fun OnGrantedTransition(granted: Boolean, onGranted: () -> Unit) {
    var prev by remember { mutableStateOf(granted) }
    LaunchedEffect(granted) {
        if (granted && !prev) onGranted()
        prev = granted
    }
}

private const val PERMISSION_PREFS = "permission_prefs"
private const val KEY_CALENDAR_ASKED = "calendar_asked"

/** 日历授权框以前是否弹过（永久拒绝判定用：没弹过时 rationale 恒为 false） */
private fun wasCalendarAsked(context: Context): Boolean =
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_CALENDAR_ASKED, false)

private fun markCalendarAsked(context: Context) {
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_CALENDAR_ASKED, true).apply()
}

/**
 * 日历“去开启”操作：平时弹运行时授权框；用户此前已拒绝且勾过“不再询问”
 * 时授权框已弹不出，改跳应用详情设置页（否则点按无任何反应、用户无路可开）。
 * 调用方把 grantedState 传进来，授权框回调里直接写回状态（不走 ON_RESUME）。
 */
@Composable
fun rememberCalendarPermissionOpener(
    grantedState: MutableState<Boolean>
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        grantedState.value = granted
    }
    return {
        val activity = context as? Activity
        if (activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_CALENDAR
            ) && wasCalendarAsked(context)
        ) {
            openAppDetailsSettings(context)
        } else {
            markCalendarAsked(context)
            launcher.launch(Manifest.permission.READ_CALENDAR)
        }
    }
}
