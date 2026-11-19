package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 文案审计第 2 批：首进 peek 图标的行——按展示顺序（程序坞 → 未读 → A-Z）
 * 取前 3 个包名；用过一次行动作后调用方传空集，图标撤下。
 * （UiPrefs 的记／读按仓库口径不进 JVM 单测，靠真机验收。）
 */
class AppCenterPeekTest {

    @Test
    fun firstPeekPkgs_takesFirstThreeInDisplayOrder() {
        assertEquals(
            setOf("a", "b", "c"),
            firstPeekPkgs(
                dockPkgs = listOf("a", "b"),
                unreadPkgs = listOf("c", "d"),
                azPkgs = listOf("e", "f")
            )
        )
    }

    @Test
    fun firstPeekPkgs_dedupesAcrossSections() {
        // 未去重时前 3 会是 a、a、b（只剩 {a, b}）；去重后 a、b、c 都是前 3。
        assertEquals(
            setOf("a", "b", "c"),
            firstPeekPkgs(
                dockPkgs = listOf("a"),
                unreadPkgs = listOf("a", "b"),
                azPkgs = listOf("a", "b", "c")
            )
        )
    }

    @Test
    fun firstPeekPkgs_fewerThanThree() {
        assertEquals(
            setOf("a"),
            firstPeekPkgs(
                dockPkgs = emptyList(),
                unreadPkgs = emptyList(),
                azPkgs = listOf("a")
            )
        )
    }

    @Test
    fun firstPeekPkgs_empty() {
        assertEquals(
            emptySet<String>(),
            firstPeekPkgs(
                dockPkgs = emptyList(),
                unreadPkgs = emptyList(),
                azPkgs = emptyList()
            )
        )
    }
}
