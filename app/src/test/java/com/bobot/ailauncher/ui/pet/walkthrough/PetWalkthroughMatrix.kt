package com.bobot.ailauncher.ui.pet.walkthrough

/**
 * 宠物走查 Loop P0：矩阵项定义（测试侧）。
 *
 * 全量矩阵见 docs/pet-walkthrough-matrix.md（姿态 × perch × 尺寸档 × 横竖屏 ×
 * 壁纸 × 字号）。此处只列 P0 已实现的子集；id 稳定，供报告、子集过滤
 *（环境变量 PETWALKTHROUGH_SUBSET，逗号分隔的 id 前缀）与黄金基线引用。
 *
 * P0 语义（非阻塞报告，发布门禁是 P1）：
 * - 数值项对黄金基线（src/test/resources/pet-walkthrough-goldens.json）做
 *   漂移检测；无基线时记为 new，不失败。
 * - 只有走查工作流传 PETWALKTHROUGH_ENFORCE=true 时漂移才使测试失败；
 *   主 CI（build-apk.yml 的 testDebugUnitTest）不传该变量，永不因此变红。
 */
object PetWalkthroughMatrix {

    /** P0 数值／快照项 id（与矩阵文档 P0 表一一对应）。 */
    val P0_NUMERIC_IDS = listOf(
        "shoulder-d1-chord",
        "shoulder-d1-rise",
        "shoulder-d2-chord",
        "shoulder-d2-rise",
        "shoulder-compact-d1-chord",
        "shoulder-compact-d1-rise",
        "shoulder-compact-d2-chord",
        "shoulder-compact-d2-rise",
        "petbox-d1-height",
        "petbox-d2-height",
        "petbox-compact-d1-height",
        "petbox-compact-d2-height",
        "petbox-intent-height",
        "bubble-height",
        "dock-clearance-700dp",
        "dock-clearance-800dp",
        "dock-clearance-900dp",
        "blink-full-close-ms",
        "blink-full-hold-ms",
        "blink-full-open-ms",
        "blink-half-close-ms",
        "blink-half-hold-ms",
        "blink-half-open-ms",
        "blink-slow-half-close-ms",
        "blink-slow-half-hold-ms",
        "blink-slow-half-open-ms",
        "blink-double-gap-ms",
        "blink-full-depth",
        "blink-half-depth",
    )

    /** P0 快照项 id（Paparazzi record 产物名与之对应，见快照测试类）。 */
    val P0_SNAPSHOT_IDS = listOf(
        "shoulder-arch-d1",
        "shoulder-arch-d2",
        "shoulder-arch-compact-d1",
        "shoulder-arch-d1-peak",
        "head-asset-box-d1",
    )
}
