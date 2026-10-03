package com.bobot.ailauncher.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全 App 统一事件总线（PRD §四十二）。
 *
 * 设计要点：
 * - `emit` 永不挂起、永不抛异常：AI Brain 挂掉/没启动时，Launcher 照常工作
 *   （PRD §六十七：AI 故障不影响 Launcher）。
 * - 无 replay：新订阅者只收订阅之后的事件，不补历史。
 * - 发送方不在乎有没有消费者：没有 Brain 消费时事件自然丢弃 = 优雅退化。
 */
object EventBus {
    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    /** 非挂起发送，缓冲区满时丢弃最旧（保证调用方永不阻塞）。 */
    fun emit(event: LauncherEvent) {
        _events.tryEmit(event)
    }

    /** 挂起发送，调用方愿意等待时用。 */
    suspend fun publish(event: LauncherEvent) {
        _events.emit(event)
    }
}
