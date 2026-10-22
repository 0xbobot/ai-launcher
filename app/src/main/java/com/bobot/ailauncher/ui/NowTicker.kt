package com.bobot.ailauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/**
 * 分钟级「现在」状态源（v0.68.95·F-05：时间派生状态收敛）。
 *
 * 此前各表面各自捕获时间且互不一致：封面三模板各跑一个分钟时钟循环，但
 * 日期/时段文案是无 key 的 remember{} 一次性冻结；封面色板同样冻结；
 * TODAY 消息 3 小时窗用组合期捕获的 now 过滤、remember(filed) 不重算——
 * 桌面长挂跨午夜/跨时段后出现「新模板 + 旧日期」、旧消息滞留超时窗。
 *
 * 本状态源统一口径：
 * - 粒度分钟级，对齐整分钟边界（+200ms 容差，与原封面时钟循环同口径），
 *   绝不秒级轮询；
 * - 只在桌面 RESUMED 时 tick（省电原则）：ON_PAUSE 即停，ON_RESUME 立即
 *   刷新一次再续 tick——息屏/切走期间零成本，回来第一眼就是当前时间；
 * - 派生方（日期/时段/色板/时间窗）一律 remember(本状态) 或按其 key 派生，
 *   不再各自 remember{} 冻结。
 */
@Composable
fun rememberNowMillis(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resumed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // 回到桌面先立即对齐当前时间，再开始整分钟 tick
                    now.longValue = System.currentTimeMillis()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            val t = System.currentTimeMillis()
            now.longValue = t
            delay(delayToNextMinute(t))
        }
    }
    return now
}

/** 距下一分钟整点（+200ms 容差）的毫秒数。纯函数，供单测。 */
internal fun delayToNextMinute(nowMs: Long): Long =
    60_000L - (nowMs % 60_000L) + 200L

/**
 * 时间窗判定（F-05：TODAY 消息 3 小时窗同口径）——严格小于，与原
 * `now - it.time < 3 * 60 * 60 * 1000` 语义一致；now 由 ticker 派生后
 * 窗口随分钟推进自动收紧，不再冻结在 filed 上次变化的那一刻。
 */
internal fun isWithinWindow(itemTimeMs: Long, nowMs: Long, windowMs: Long): Boolean =
    nowMs - itemTimeMs < windowMs
