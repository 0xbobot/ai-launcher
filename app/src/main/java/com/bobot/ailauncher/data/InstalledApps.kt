package com.bobot.ailauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 已装应用变化信号（v0.68.88·保持更新）。
 *
 * Bob 定的原则：数据要基于已装应用初始化、并保持更新。此前首页本地搜索
 * 索引懒建后永不失效——新装一个应用，首页永远搜不到它（要等进程重启）；
 * 能力绑定也只在每次调用时现查、没有失效信号可供缓存层使用。
 *
 * 这里只做一件事：监听 PACKAGE_ADDED / PACKAGE_REMOVED / PACKAGE_REPLACED，
 * 变化时把 [epoch] +1。各个缓存（能力解析、首页索引）以 epoch 为失效键，
 * **只在变化时刷新，绝不轮询**（电量纪律）。注册幂等：进程内只注册一次，
 * 用 applicationContext 常驻（桌面进程与用户同寿命，正是需要监听的范围）。
 */
object InstalledApps {

    private val _epoch = MutableStateFlow(0)

    /** 已装集合变化代次（从 0 起，每次装/卸/替换 +1） */
    val epoch: StateFlow<Int> = _epoch

    @Volatile
    private var registered = false

    fun ensureRegistered(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val appContext = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    _epoch.value += 1
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
            registered = true
        }
    }

    /** 这些包名里本机「可启动」的子集（与注册表 isInstalled 同口径：有 launcher 入口） */
    fun installedAmong(context: Context, packageNames: Collection<String>): Set<String> {
        val pm = context.packageManager
        return packageNames.filterTo(mutableSetOf()) { pkg ->
            try {
                pm.getLaunchIntentForPackage(pkg) != null
            } catch (_: Exception) {
                false
            }
        }
    }
}
