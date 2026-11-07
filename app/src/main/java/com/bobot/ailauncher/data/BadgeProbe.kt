package com.bobot.ailauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 角标信号探针（Bob 2026-11-08 凌晨质疑“第三方桌面接不到中兴角标通道”只是推理、
 * 没实测过——他是对的；本文件只实测，不推断）。
 *
 * 两路实测，只读不写：
 * ① 角标广播：监听 ShortcutBadger 的两个广播 action
 *    - android.intent.action.BADGE_COUNT_UPDATE
 *    - me.leolin.shortcutbadger.BADGE_COUNT_UPDATE（Oreo 版，见
 *      /tmp/ShortcutBadger 的 IntentConstants）
 *    只收不发。收到后记下包名+数字，供角标第四路信号与诊断行展示。
 *    注意：ShortcutBadger 发广播前用 queryBroadcastReceivers 找接收方，
 *    只找得到 manifest 里声明的 receiver，所以除了服务里的运行时注册，
 *    AndroidManifest 里也声明了 BadgeBroadcastReceiver（exported=true）。
 * ② ZTE provider 读：content://com.android.launcher3.cornermark.unreadbadge
 *    先试 query()（看 cursor 有没有包名/数字列），再试 call("getAppUnreadCount")。
 *    只读，绝对不调 setAppUnreadCount（那是往系统桌面写数，会污染系统桌面角标）。
 *
 * 探针结果进角标第四路信号：广播数 + provider 读到的数，与现有三路取最大值。
 * 探针失败（没收到广播 / provider 读不到）时角标行为与原来一字不变。
 */
object BadgeProbe {
    private const val TAG = "BadgeProbe"

    const val ZTE_BADGE_URI = "content://com.android.launcher3.cornermark.unreadbadge"

    /** 只写在注释里警示：setAppUnreadCount 是往系统桌面写数，绝不调用。 */
    private const val ZTE_GET_METHOD = "getAppUnreadCount"

    const val BADGE_ACTION = "android.intent.action.BADGE_COUNT_UPDATE"
    const val BADGE_ACTION_OREO = "me.leolin.shortcutbadger.BADGE_COUNT_UPDATE"

    private const val EXTRA_COUNT = "badge_count"
    private const val EXTRA_PACKAGE = "badge_count_package_name"

    /** 飞书包名（诊断行“飞书数”用；优先按应用名“飞书”解析，解析不到才用它兜底）。 */
    const val FEISHU_PACKAGE = "com.ss.android.lark"

    // ---------- 广播状态 ----------

    data class LastBroadcast(val pkg: String, val count: Int, val at: Long)

    private val _broadcastCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val broadcastCounts: StateFlow<Map<String, Int>> = _broadcastCounts.asStateFlow()

    private val _lastBroadcast = MutableStateFlow<LastBroadcast?>(null)
    val lastBroadcast: StateFlow<LastBroadcast?> = _lastBroadcast.asStateFlow()

    // ---------- ZTE provider 探针结果 ----------

    data class ZteProbeResult(
        /** query() 是否返回了可用 cursor */
        val queryOk: Boolean,
        /** query 详情：行数/列名，或失败原因 */
        val queryDetail: String,
        /** call("getAppUnreadCount") 是否返回了带数的 Bundle */
        val callOk: Boolean,
        /** call 详情：拿到数的包，或失败原因 */
        val callDetail: String,
        /** 探针读到的各包角标数（query 行 + call 结果合并，取最大值） */
        val counts: Map<String, Int>,
        val probedAt: Long
    )

    private val _zteResult = MutableStateFlow<ZteProbeResult?>(null)
    val zteResult: StateFlow<ZteProbeResult?> = _zteResult.asStateFlow()

    /** 探针第四路信号：广播数 + ZTE provider 读到的数，每包取最大值。 */
    fun currentProbeCounts(): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        for ((pkg, n) in _broadcastCounts.value) out[pkg] = maxOf(out[pkg] ?: 0, n)
        val zte = _zteResult.value
        if (zte != null) {
            for ((pkg, n) in zte.counts) out[pkg] = maxOf(out[pkg] ?: 0, n)
        }
        return out
    }

    /**
     * 收到角标广播时调用（只收不发）。校验包名是已安装应用才记
     * （防伪造广播刷数）；返回 true 表示已记入。
     */
    fun onBroadcastReceived(context: Context, intent: Intent): Boolean {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotBlank() }
            ?: return false
        val count = intent.getIntExtra(EXTRA_COUNT, -1)
        if (count < 0) return false
        if (!isPackageInstalled(context, pkg)) return false
        val now = System.currentTimeMillis()
        _lastBroadcast.value = LastBroadcast(pkg, count, now)
        val cur = _broadcastCounts.value.toMutableMap()
        if (count > 0) cur[pkg] = count else cur.remove(pkg)
        _broadcastCounts.value = cur
        Log.d(TAG, "badge broadcast: $pkg=$count")
        return true
    }

    fun badgeIntentFilter(): IntentFilter = IntentFilter().apply {
        addAction(BADGE_ACTION)
        addAction(BADGE_ACTION_OREO)
    }

    /**
     * 试读 ZTE provider（阻塞调用，调用方必须在后台线程跑）。
     * 先 query() 全表，再对候选包逐个 call("getAppUnreadCount")。
     * 任何一步抛异常/返回空都记下来、不崩溃、不写任何东西。
     * 只试 getAppUnreadCount 这一个取数方法名，不对就记下来，不瞎试一堆。
     */
    fun probeZteProvider(
        context: Context,
        candidatePackages: Collection<String>
    ): ZteProbeResult {
        val uri = Uri.parse(ZTE_BADGE_URI)
        val counts = mutableMapOf<String, Int>()
        var queryOk = false
        var queryDetail = "未尝试"
        var callOk = false
        val callNotes = mutableListOf<String>()

        // ① query() 全表
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val cols = cursor.columnNames?.toList().orEmpty()
                queryDetail = "列=${cols} 行数=${cursor.count}"
                val pkgIdx = cols.indexOfFirst {
                    it.contains("package", ignoreCase = true) ||
                        it.contains("component", ignoreCase = true)
                }
                val countIdx = cols.indexOfFirst {
                    it.contains("count", ignoreCase = true) ||
                        it.contains("badge", ignoreCase = true) ||
                        it.contains("unread", ignoreCase = true)
                }
                if (pkgIdx >= 0 && countIdx >= 0) {
                    queryOk = true
                    while (cursor.moveToNext()) {
                        val pkg = cursor.getString(pkgIdx) ?: continue
                        val n = try {
                            cursor.getInt(countIdx)
                        } catch (_: Exception) {
                            continue
                        }
                        if (n > 0) counts[pkg] = maxOf(counts[pkg] ?: 0, n)
                    }
                } else {
                    queryDetail += "（无可识别的包名/数字列）"
                }
            } ?: run { queryDetail = "query 返回 null" }
        } catch (e: Exception) {
            queryDetail = "query 抛异常：${e.javaClass.simpleName}: ${e.message}"
            Log.d(TAG, "probeZteProvider query failed", e)
        }

        // ② 对候选包逐个 call getAppUnreadCount
        for (pkg in candidatePackages) {
            try {
                val component = resolveLaunchComponent(context, pkg)
                val extras = Bundle().apply {
                    putString("app_badge_component_name", component)
                }
                val out = context.contentResolver.call(uri, ZTE_GET_METHOD, null, extras)
                val n = out?.let {
                    when {
                        it.containsKey("app_badge_count") -> it.getInt("app_badge_count")
                        it.containsKey("count") -> it.getInt("count")
                        else -> null
                    }
                }
                if (n != null && n > 0) {
                    callOk = true
                    counts[pkg] = maxOf(counts[pkg] ?: 0, n)
                    callNotes += "$pkg=$n"
                } else if (out == null) {
                    callNotes += "$pkg: call 返回 null"
                } else {
                    callNotes += "$pkg: 返回无数字段（键=${out.keySet()?.joinToString()})"
                }
            } catch (e: Exception) {
                callNotes += "$pkg: 抛异常 ${e.javaClass.simpleName}"
                Log.d(TAG, "probeZteProvider call failed for $pkg", e)
            }
        }

        val result = ZteProbeResult(
            queryOk = queryOk,
            queryDetail = queryDetail,
            callOk = callOk,
            callDetail = if (callNotes.isEmpty()) "无候选包" else callNotes.joinToString("；"),
            counts = counts,
            probedAt = System.currentTimeMillis()
        )
        _zteResult.value = result
        Log.d(TAG, "probeZteProvider: $result")
        return result
    }

    /**
     * 飞书包名：先按应用名“飞书”解析（不写死包名），解析不到再用已知包名兜底
     * （且确实装了才认）。找不到返回 null（诊断行显示“—”）。
     */
    fun resolveFeishuPackage(context: Context): String? {
        val pm = context.packageManager
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent, PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val byLabel = apps.firstOrNull { it.loadLabel(pm)?.toString() == "飞书" }
                ?.activityInfo?.packageName
            if (byLabel != null) return byLabel
        } catch (_: Exception) {
            // 继续走兜底
        }
        return if (isPackageInstalled(context, FEISHU_PACKAGE)) FEISHU_PACKAGE else null
    }

    private fun resolveLaunchComponent(context: Context, pkg: String): String {
        return try {
            context.packageManager.getLaunchIntentForPackage(pkg)
                ?.component?.flattenToString() ?: pkg
        } catch (_: Exception) {
            pkg
        }
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    pkg, PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Manifest 声明的角标广播接收器（AndroidManifest 里 exported=true）。
 * ShortcutBadger 发广播前用 queryBroadcastReceivers 找接收方，只找得到
 * manifest 里声明的 receiver，所以必须有这一个；只收不发，只记数。
 * （运行时 receiver 另由 LauncherNotificationService 注册，收到的广播
 * 会立即触发一次角标重算；本 receiver 收到的是同一广播的另一份，
 * 只更新探针状态，下次通知事件时合入角标。）
 */
class BadgeBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BadgeProbe.onBroadcastReceived(context, intent)
    }
}
