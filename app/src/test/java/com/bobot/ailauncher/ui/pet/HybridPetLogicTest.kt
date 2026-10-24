package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pet-hybrid-float：mood → 高光片映射与片源清单（纯逻辑，JVM 单测）。
 * 映射改动必须同步改 HybridPet 的状态联动注释与压制脚本的段落表。
 */
class HybridPetLogicTest {

    @Test
    fun moodToClipMapping() {
        assertEquals(HighlightClip.HAPPY, clipForMood(PetMood.HAPPY))
        assertEquals(HighlightClip.SURPRISED, clipForMood(PetMood.SURPRISED))
        assertEquals(HighlightClip.THINKING, clipForMood(PetMood.FETCH))
    }

    @Test
    fun codeLayerMoodsPlayNoVideo() {
        // idle / listening / glance 与其他情绪不播视频，走代码层。
        assertNull(clipForMood(PetMood.IDLE))
        assertNull(clipForMood(PetMood.SORTING))
        assertNull(clipForMood(PetMood.FILE))
        assertNull(clipForMood(PetMood.SHY))
        assertNull(clipForMood(PetMood.DIZZY))
    }

    @Test
    fun clipAssetsAndLoopFlags() {
        // 片源全部在 assets/clips/ 下；只有循环态（思考、播报）循环。
        val expected = mapOf(
            HighlightClip.GREET to ("clips/greet.mp4" to false),
            HighlightClip.HAPPY to ("clips/happy.mp4" to false),
            HighlightClip.THINKING to ("clips/thinking.mp4" to true),
            HighlightClip.SPEAKING to ("clips/speaking.mp4" to true),
            HighlightClip.SURPRISED to ("clips/surprised.mp4" to false),
        )
        assertEquals(5, HighlightClip.values().size)
        for ((clip, assetAndLoop) in expected) {
            assertEquals(assetAndLoop.first, clip.assetFile)
            assertEquals(assetAndLoop.second, clip.loop)
            assertTrue(clip.assetFile.startsWith("clips/"))
            assertTrue(clip.assetFile.endsWith(".mp4"))
        }
    }
}
