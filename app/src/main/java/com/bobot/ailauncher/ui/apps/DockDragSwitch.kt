package com.bobot.ailauncher.ui.apps

import kotlin.math.abs

/**
 * D1/D2 拖拽切换纯逻辑（2026-10-27，拖拽代滑动批）。
 *
 * 设计（合理化方案）：
 * - 方向：水平拖拽，沿用全 App 手势签名（左滑＝多、右滑＝少）——
 *   D1 左拖 → D2（更多），D2 右拖 → D1（更少）。签名一字不动。
 * - 连续跟手：拖拽中内容随手指平移（阻尼 0.8、钳制 ±32dp），
 *   卡片本体与宠物栖坐不动（只动 AnimatedContent 的 translationX，
 *   不碰布局尺寸与肩线锚点）；松手时弹簧回落到 0。
 * - 松手确认／回落：松手时净位移 ≥ 阈值（沿用旧滑动阈值 48dp，
 *   纯距离判定、不引速度——与旧逻辑同一口径）才确认切换；
 *   低于阈值、或拖回原方向、或取消，一律弹簧回落、不切换。
 *   拖拽可中途取消（拖过阈值再拖回来松手＝不切换），这是拖拽
 *   相对滑动的核心收益；滑动是阈值一过即触发、不可取消。
 * - 与拉伸拖拽的区分：主靠落点、辅靠方向。拉伸拖拽的节点是
 *   宠物 Box（前面图层、后画），其 detector 消费位移（FloatingPet
 *   onDrag 内 change.consume()），面板 detector 根本收不到；
 *   面板拖拽只响应落在面板上的手势。方向上拉伸是上拉为主、
 *   档位拖拽是水平为主，双保险。
 * - 档位本身仍离散：拖拽只改触发方式，切换仍走原 AnimatedContent
 *   crossfade + scale，仍经 MainScreen.goDock（行数持久化不动）。
 * - 覆盖范围：拖拽只覆盖 D1↔D2。D2 左拖 → D3、D1 右拖 → 隐藏
 *   仍走旧的中途触发（非档位切换，不动）；上滑 → D3、下滑 →
 *   收一档亦不动；长按 → 隐藏保留（隐藏入口，不是切换触发）。
 */

/** 拖拽跟手阻尼（手指行程 × 0.8）。 */
const val DRAG_TIER_FOLLOW_DAMPING = 0.8f

/**
 * 拖拽松手确认目标：仅 D1 左拖 → D2、D2 右拖 → D1；
 * 低于阈值、D2 左拖、D1 右拖一律 null（后两者归旧中途触发管）。
 */
fun resolveDragTierTarget(
    state: DockState,
    accumXPx: Float,
    thresholdPx: Float,
): DockState? {
    if (abs(accumXPx) < thresholdPx) return null
    return when {
        state == DockState.D1 && accumXPx < 0 -> DockState.D2
        state == DockState.D2 && accumXPx > 0 -> DockState.D1
        else -> null
    }
}

/**
 * 拖拽跟手量（px）：仅可切换方向跟手（D1 左、D2 右），其余方向 0；
 * 阻尼 0.8、钳制 ±clampPx。
 */
fun dragTierFollowPx(
    state: DockState,
    accumXPx: Float,
    clampPx: Float,
): Float {
    val switchable = (state == DockState.D1 && accumXPx < 0) ||
        (state == DockState.D2 && accumXPx > 0)
    if (!switchable) return 0f
    return (accumXPx * DRAG_TIER_FOLLOW_DAMPING).coerceIn(-clampPx, clampPx)
}
