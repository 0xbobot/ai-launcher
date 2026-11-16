package com.bobot.ailauncher.core.brain

/**
 * AI Brain 的决策输出（PRD §四十三）。
 *
 * - [Nothing]：合法的一等结果——AI 判断现在不需要做任何事（PRD §四十三/四十四）。
 */
sealed interface Decision {
    /** 什么都不做 */
    data object Nothing : Decision
}
