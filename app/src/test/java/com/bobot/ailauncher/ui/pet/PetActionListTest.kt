package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.PetRepository.PetManualAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 宠物动作清单内容锁定：可爱化只换呈现，内容与顺序一字不动。 */
class PetActionListTest {

    @Test
    fun `action items keep eight in fixed order`() {
        assertEquals(8, petActionItems.size)
        assertEquals(
            listOf(
                PetManualAction.GREET,
                PetManualAction.STRETCH,
                PetManualAction.TILT,
                PetManualAction.SHY,
                PetManualAction.SURPRISED,
                PetManualAction.DIZZY,
                PetManualAction.HAPPY,
                PetManualAction.DEEP_BREATH,
            ),
            petActionItems.map { it.kind },
        )
    }

    @Test
    fun `action items labels match kinds`() {
        assertEquals(
            listOf("打招呼", "伸懒腰", "歪头好奇", "害羞", "惊讶", "晕乎乎", "开心", "深呼吸"),
            petActionItems.map { it.label },
        )
    }

    @Test
    fun `action items icons are distinct per item`() {
        assertEquals(8, petActionItems.map { it.icon }.toSet().size)
        assertTrue(petActionItems.all { it.label.isNotBlank() })
    }
}
