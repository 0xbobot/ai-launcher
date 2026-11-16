package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.CalEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「下一会议」轻量共享 holder（意图改进一批）。
 *
 * 背景：下一会议原本只活在 PetZone 的 Composable 状态里（TodaySection 回调写入），
 * 首页意图入口的零打字建议面需要读它（有下一会议时出一行会议条），但够不着那份状态。
 * 这里只做一份只读镜像：PetZone 照常算、照常写（[update]），IntentEntry 订阅
 * [nextEvent] 读取；TODAY 的数据流本身完全不改。PetZone 离场时写 null 防陈旧。
 */
object TodayEventsHolder {
    private val _nextEvent = MutableStateFlow<CalEvent?>(null)
    val nextEvent: StateFlow<CalEvent?> = _nextEvent.asStateFlow()

    fun update(event: CalEvent?) {
        _nextEvent.value = event
    }
}
