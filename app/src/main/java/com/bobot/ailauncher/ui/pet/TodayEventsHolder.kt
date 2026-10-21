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
 *
 * v0.68.93（布局与交互 Loop 一批④）：同一信息只在一处呈现——Bob 截图实锤建议面
 * 会议行与 TODAY 的 NextUp 卡同屏重复同一场会。本 holder 再镜像一份
 * [presentedNextEventId]：NextUp 条当前正在呈现的事件 id（唯一写者仍是 PetZone，
 * 与封面条的渲染条件同源同拍）。IntentEntry 建议面的会议行按 eventId 与它去重：
 * NextUp 是会议的常驻主场，在场呈现同一事件时建议面不复刻，只补位（NextUp 没
 * 呈现该事件时才出会议行）。判定只用事件 id，不做文案比对。
 */
object TodayEventsHolder {
    private val _nextEvent = MutableStateFlow<CalEvent?>(null)
    val nextEvent: StateFlow<CalEvent?> = _nextEvent.asStateFlow()

    /** TODAY NextUp 条当前正在呈现的事件 id；没有呈现任何会议时为 null。 */
    private val _presentedNextEventId = MutableStateFlow<Long?>(null)
    val presentedNextEventId: StateFlow<Long?> = _presentedNextEventId.asStateFlow()

    fun update(event: CalEvent?) {
        _nextEvent.value = event
    }

    fun updatePresentedNextEventId(eventId: Long?) {
        _presentedNextEventId.value = eventId
    }
}
