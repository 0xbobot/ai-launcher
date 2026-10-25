package com.bobot.ailauncher.ui.apps

import android.content.Context
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppSearchIndex

/**
 * 三处字母导航（应用中心竖屏、应用中心横屏、首页选择器）共用的项目表构造。
 *
 * 字母导航完全统一（Bob 2026-10-25 追问「为什么不改」）：332 只做到了块高等，
 * 行高＝块高／项目数仍不同——首页此前硬编码 31 项（房子＋分隔＋★＋分隔＋完整
 * A-Z＋分隔＋👁），应用中心则随本机应用变化。此后三处一律走本函数，项目数
 * 随本机应用严格一致，块高等后行距随之严格一致；再漂移只能是调用方传参不一，
 * 构造本身没有第二份实现可供漂移。
 *
 * 构造（与此前应用中心竖屏／横屏逐字一致）：
 * 房子；有 Dock 应用时分隔＋★；分隔；有应用字母；有隐藏应用时分隔＋👁。
 */
internal fun buildRailItems(
    hasDock: Boolean,
    letters: List<Char>,
    hasHidden: Boolean,
): List<RailItem> = buildList {
    add(RailItem.House)
    if (hasDock) {
        add(RailItem.Separator)
        add(RailItem.Star)
    }
    add(RailItem.Separator)
    letters.forEach { add(RailItem.Letter(it)) }
    if (hasHidden) {
        add(RailItem.Separator)
        add(RailItem.Eye)
    }
}

/**
 * 与应用中心 azGroups 同口径的字母集：AppCatalog.visibleApps（去本应用、去隐藏、
 * Collator(CHINA) 排序）按拼音首字母分组，'#' 排最后。首页选择器 letters 取此，
 * 与应用中心 letters 同源；此前首页硬编码完整 A-Z 时，选一个应用中心没有的
 * 字母会落到应用中心的前后最近字母兜底，统一后该兜底不再被 picker 触发。
 */
internal fun railLettersFromVisibleApps(
    visibleApps: List<AppInfo>,
    context: Context,
): List<Char> {
    val map = linkedMapOf<Char, MutableList<AppInfo>>()
    // 与 AppCenter.kt azGroups 逐字一致：字典优先，GB2312 区位码兜底。
    val pinyinOf: (Char) -> Char = { c ->
        val fromDict = AppSearchIndex.pinyinInitialOf(context, c)
        if (fromDict != '#') fromDict else pinyinInitial(c)
    }
    visibleApps.forEach { app ->
        map.getOrPut(groupKey(app.label, pinyinOf)) { mutableListOf() }.add(app)
    }
    return map.keys.sortedWith(compareBy({ if (it == '#') 1 else 0 }, { it }))
}
