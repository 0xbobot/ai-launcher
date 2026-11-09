package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探针数存盘纯函数单测（Bob 2026-11-09 12:20 ①：探针数改持久化，升级重启不丢）。
 * 只测纯函数 prune/merge；SharedPreferences I/O 是薄壳（与 BadgeStickyStore
 * 同款），验收走 Bob 真机升级测试。
 */
class BadgeProbeStoreTest {

    private val now = 1_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `max age is 7 days`() {
        assertEquals(7L * day, PROBE_MAX_AGE_MILLIS)
    }

    @Test
    fun `prune keeps fresh entries`() {
        val entries = mapOf(
            "com.a" to (2 to now),
            "com.b" to (1 to (now - 6 * day))
        )
        assertEquals(entries, pruneProbeEntries(entries, now))
    }

    @Test
    fun `prune drops entries older than 7 days`() {
        val entries = mapOf(
            "com.a" to (2 to now),
            "com.old" to (5 to (now - 8 * day))
        )
        assertEquals(mapOf("com.a" to (2 to now)), pruneProbeEntries(entries, now))
    }

    @Test
    fun `prune boundary exactly 7 days is kept`() {
        val entries = mapOf("com.a" to (2 to (now - 7 * day)))
        assertEquals(entries, pruneProbeEntries(entries, now))
    }

    @Test
    fun `prune drops non-positive timestamps`() {
        val entries = mapOf(
            "com.a" to (2 to now),
            "com.dirty" to (3 to 0L)
        )
        assertEquals(mapOf("com.a" to (2 to now)), pruneProbeEntries(entries, now))
    }

    @Test
    fun `merge keeps newer timestamp per package`() {
        val current = mapOf("com.a" to (2 to (now - day)))
        val incoming = mapOf("com.a" to (1 to now))
        assertEquals(mapOf("com.a" to (1 to now)), mergeProbeEntries(current, incoming))
    }

    @Test
    fun `merge keeps current when it is newer`() {
        val current = mapOf("com.a" to (2 to now))
        val incoming = mapOf("com.a" to (1 to (now - day)))
        assertEquals(mapOf("com.a" to (2 to now)), mergeProbeEntries(current, incoming))
    }

    @Test
    fun `merge tie keeps incoming`() {
        val current = mapOf("com.a" to (2 to now))
        val incoming = mapOf("com.a" to (1 to now))
        assertEquals(mapOf("com.a" to (1 to now)), mergeProbeEntries(current, incoming))
    }

    @Test
    fun `merge unions disjoint packages`() {
        val current = mapOf("com.a" to (2 to now))
        val incoming = mapOf("com.b" to (3 to now))
        assertEquals(
            mapOf("com.a" to (2 to now), "com.b" to (3 to now)),
            mergeProbeEntries(current, incoming)
        )
    }

    @Test
    fun `merge with empty incoming returns current`() {
        val current = mapOf("com.a" to (2 to now))
        assertEquals(current, mergeProbeEntries(current, emptyMap()))
    }

    @Test
    fun `merge with empty current returns incoming`() {
        val incoming = mapOf("com.a" to (2 to now))
        assertEquals(incoming, mergeProbeEntries(emptyMap(), incoming))
    }
}
