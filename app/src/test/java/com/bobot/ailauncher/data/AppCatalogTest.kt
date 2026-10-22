package com.bobot.ailauncher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.95（批二-A·F-03/F-04）：应用目录仓库的失效判定纯逻辑单测。
 * 覆盖：无快照必建、同代不建、epoch 变必建（且不复用旧枚举）、
 * 仅 hiddenVersion 变也建但复用原始枚举（省整表 PackageManager 查询）。
 * 真正的枚举/索引构建走 Android API，不在单测覆盖内，经冒烟装卸桩验证。
 */
class AppCatalogTest {

    // ---------- catalogNeedsBuild ----------

    @Test
    fun `no snapshot always needs build`() {
        assertTrue(catalogNeedsBuild(null, null, 0, 0))
        assertTrue(catalogNeedsBuild(null, null, 5, 2))
    }

    @Test
    fun `same generation does not rebuild`() {
        assertFalse(catalogNeedsBuild(3, 1, 3, 1))
        assertFalse(catalogNeedsBuild(0, 0, 0, 0))
    }

    @Test
    fun `epoch change needs build`() {
        assertTrue(catalogNeedsBuild(3, 1, 4, 1))
    }

    @Test
    fun `hidden version change needs build`() {
        assertTrue(catalogNeedsBuild(3, 1, 3, 2))
    }

    // ---------- catalogReusesRaw ----------

    @Test
    fun `raw reused only within same epoch`() {
        assertTrue(catalogReusesRaw(3, 3))
        assertFalse(catalogReusesRaw(3, 4))
        assertFalse(catalogReusesRaw(null, 0))
    }
}
