package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashEscapeLogicTest {

    private val version = 460

    @Test
    fun `no crash records keeps counter and shows nothing`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = null,
            lastSeenCrashEpoch = 0L,
            consecutive = 0,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(0, state.consecutive)
        assertEquals(0L, state.lastSeenCrashEpoch)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `first new crash raises counter to one without recovery`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 1000L,
            lastSeenCrashEpoch = 0L,
            consecutive = 0,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(1, state.consecutive)
        assertEquals(1000L, state.lastSeenCrashEpoch)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `second consecutive crash reaches threshold and shows recovery`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 2000L,
            lastSeenCrashEpoch = 1000L,
            consecutive = 1,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(2, state.consecutive)
        assertEquals(2000L, state.lastSeenCrashEpoch)
        assertTrue(state.shouldShowRecovery)
    }

    @Test
    fun `no new crash keeps recovery showing while counter stays at threshold`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 2000L,
            lastSeenCrashEpoch = 2000L,
            consecutive = 2,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(2, state.consecutive)
        assertTrue(state.shouldShowRecovery)
    }

    @Test
    fun `older crash records do not count as new`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 1000L,
            lastSeenCrashEpoch = 2000L,
            consecutive = 1,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(1, state.consecutive)
        assertEquals(2000L, state.lastSeenCrashEpoch)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `version change resets counter and marks old crashes seen`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 2000L,
            lastSeenCrashEpoch = 1000L,
            consecutive = 2,
            storedVersionCode = version - 1,
            currentVersionCode = version
        )
        assertEquals(0, state.consecutive)
        assertEquals(2000L, state.lastSeenCrashEpoch)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `first run with no stored version resets and shows nothing`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = null,
            lastSeenCrashEpoch = 0L,
            consecutive = 0,
            storedVersionCode = 0,
            currentVersionCode = version
        )
        assertEquals(0, state.consecutive)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `bad values are sanitized instead of crashing`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 1000L,
            lastSeenCrashEpoch = -500L,
            consecutive = -3,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(1, state.consecutive)
        assertEquals(1000L, state.lastSeenCrashEpoch)
        assertFalse(state.shouldShowRecovery)
    }

    @Test
    fun `counter is capped at max`() {
        val state = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 3000L,
            lastSeenCrashEpoch = 2000L,
            consecutive = CrashEscapeLogic.MAX_CONSECUTIVE,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(CrashEscapeLogic.MAX_CONSECUTIVE, state.consecutive)
        assertTrue(state.shouldShowRecovery)
    }

    @Test
    fun `retry reset then one more crash does not yet show recovery`() {
        // 点“重试”把计数清零后，再崩一次只到 1，不出恢复屏；
        // 再崩一次到 2 才出——与“计数清零”口径一致。
        val afterRetry = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 2000L,
            lastSeenCrashEpoch = 2000L,
            consecutive = 0,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(0, afterRetry.consecutive)
        assertFalse(afterRetry.shouldShowRecovery)
        val oneMore = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 3000L,
            lastSeenCrashEpoch = 2000L,
            consecutive = 0,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(1, oneMore.consecutive)
        assertFalse(oneMore.shouldShowRecovery)
        val secondMore = CrashEscapeLogic.onStartup(
            maxCrashEpoch = 4000L,
            lastSeenCrashEpoch = 3000L,
            consecutive = 1,
            storedVersionCode = version,
            currentVersionCode = version
        )
        assertEquals(2, secondMore.consecutive)
        assertTrue(secondMore.shouldShowRecovery)
    }
}
