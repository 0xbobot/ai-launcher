package com.bobot.ailauncher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 已归档消息（功能部分，非宠物角色）：
 * 原先由 PetRepository 持有、经宠物“取→分类→呈现”动画序列后才归档；
 * 删宠物后改为分类即归档，不再有取件/搬运/呈现卡片动画、宠物说话与瞥视落点。
 * 唯一消费者是简报标题的“需回复消息计数”（TodaySection 的 recentMsgs/replyMsgs）。
 * 分类类型 PetCat/PetItem 沿用 PetClassifier.kt 的数据定义（纯数据，无宠物行为）；
 * 改名是表面工作，留待主会话另定，不在此偷换。
 */
object FiledMessages {
    private val _filed = MutableStateFlow<Map<PetCat, List<PetItem>>>(emptyMap())
    val filed: StateFlow<Map<PetCat, List<PetItem>>> = _filed.asStateFlow()

    // 120 秒内同包名同标题同正文去重（原 handleIncoming 的 recent 口径）。
    private val recent = mutableMapOf<String, Long>()

    /** 通知分类并直接归档；自身应用的通知不归档（原口径）。 */
    fun classifyAndFile(n: SimpleNotification, ownPackage: String) {
        if (n.packageName == ownPackage) return
        val (cat, desc) = PetClassifier.classify(n.packageName, n.appName, n.title, n.text)
        val item = PetItem(
            id = "n${n.time}_${n.packageName.hashCode()}",
            cat = cat,
            sortDesc = desc,
            appName = n.appName,
            title = n.title,
            text = n.text,
            time = n.time,
            packageName = n.packageName,
            pendingIntent = n.contentIntent,
            notifKey = n.notifKey,
            phoneNumber = n.phoneNumber
        )
        file(item)
    }

    fun file(item: PetItem) {
        val key = "${item.packageName}|${item.title}|${item.text}"
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val last = recent[key]
            if (last != null && now - last < 120_000) return
            recent[key] = now
            if (recent.size > 200) recent.clear()
        }
        val map = _filed.value.toMutableMap()
        map[item.cat] = (map[item.cat] ?: emptyList()) + item
        _filed.value = map
    }

    /** 通知栏划掉后 Today 同步清除对应消息（原 removeFiledByPredicate 口径）。 */
    fun removeByPredicate(predicate: (PetItem) -> Boolean) {
        val map = _filed.value.toMutableMap()
        var changed = false
        for ((cat, list) in map) {
            val filtered = list.filterNot(predicate)
            if (filtered.size != list.size) {
                map[cat] = filtered
                changed = true
            }
        }
        if (changed) _filed.value = map
    }
}
