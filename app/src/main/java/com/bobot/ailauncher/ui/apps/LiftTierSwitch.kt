package com.bobot.ailauncher.ui.apps

import androidx.compose.animation.core.animate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobot.ailauncher.ui.theme.PetMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * D1/D2 提拉换档纯逻辑＋会话状态（2026-10-27，Bob 07:50 确认，
 * 取代 365 面板横拖；横屏仍走横拖，见 PullUpDock 面板手势）。
 *
 * 方向：从宠物身上起拖的竖向提拉（宠物 Box 前面图层先消费、与拉伸
 * 同仲裁，面板收不到）：往上提 = 身体膨胀 → D2（展开、多图标），
 * 往下压 = 身体收缩 → D1（收缩、少图标）。
 *
 * 语义（Bob 原话：边提边变、到阈值才切换）：
 * - 视觉连续跟手：morph 进度随拖动量连续走（上提 0→1、下压 1→0），
 *   面板高在两档实测高之间插值、图标从 4 大向 10 小连续 morph。
 * - 档位跟拖动量走阈值翻转：过阈值即切、松手前拖回阈值以下即翻回
 *   （可中途取消），松手以家族弹簧落定到当前档位静止态；未过阈值
 *   松手弹回原档（即旧拉伸行为）。
 *
 * 阈值 LIFT_TIER_THRESHOLD_DP = 48dp（手指净行程，非头升）：
 * - 沿用旧切换阈值（365 横拖与更早滑动同为 48dp），手感记忆不换；
 * - 48dp 行程处头升约 31dp（近 32dp 封顶），翻转时身体已 visibly
 *   拉伸，不会无感切换；又小于上滑进 D3 的 72dp，不与进 D3 混淆。
 * - 头升 32dp 封顶只管头、不限制面板膨胀量（面板高差 48dp 全程跟手）。
 *
 * 边界：D1 再往下压、D2 再往上提只拉伸回弹，不经此手势进 D3／隐藏。
 * 一个身体：面板高与宠物偏移同量变化（见 PullUpDock panelHGeo），
 * 相对几何一字不动，不加带子／桥／贴片；无脚下阴影；松手弹簧走
 * 家族 PetMotion.DragRelease（与拉伸同一支）。
 */

/** 提拉换档阈值（dp，手指净行程）。 */
const val LIFT_TIER_THRESHOLD_DP = 48f

/**
 * 提拉换档目标：仅 D1 上提 → D2、D2 下压 → D1；其余（D1 下压、
 * D2 上提、未过阈值、D3／Hidden）一律 null = 留原档。
 * upDp／downDp 为手指净行程（dp，FloatingPet dragTravelPx 换算，
 * 非 tanh 后的头升）。
 */
fun resolveLiftTierTarget(
    base: DockState,
    upDp: Float,
    downDp: Float,
    thresholdDp: Float,
): DockState? {
    return when {
        base == DockState.D1 && upDp >= thresholdDp -> DockState.D2
        base == DockState.D2 && downDp >= thresholdDp -> DockState.D1
        else -> null
    }
}

/**
 * morph 进度（0 = D1 静止、1 = D2 静止）：D1 基底随上提 0→1、
 * D2 基底随下压 1→0；反向行程不反向超调（D1 下压恒 0、D2 上提恒 1）；
 * 超阈值钳制在目标档（翻转后继续提／压只拉伸，不再胀）。
 */
fun liftMorphProgress(
    base: DockState,
    upDp: Float,
    downDp: Float,
    thresholdDp: Float,
): Float {
    return when (base) {
        DockState.D1 -> (upDp / thresholdDp).coerceIn(0f, 1f)
        DockState.D2 -> 1f - (downDp / thresholdDp).coerceIn(0f, 1f)
        else -> liftRestProgress(base)
    }
}

/** 档位静止态 morph 进度：D2 = 1，其余 = 0。 */
fun liftRestProgress(tier: DockState): Float = if (tier == DockState.D2) 1f else 0f

/**
 * 提拉换档会话：由 PullUpDock remember、经 DragBodyState 的 lift 观察
 * 字段接线（意图侧实例不设观察字段 = 纯拉伸）。
 * 会话只在 D1／D2 基底启动；横屏由接线方门禁（本类不管横竖屏）。
 */
class LiftTierState(private val scope: CoroutineScope) {
    /** 会话在场（拖拽中或松手落定期）：morph 覆盖层渲染门。 */
    var sessionActive by mutableStateOf(false)
        private set

    /** 拖拽进行中。 */
    var dragging by mutableStateOf(false)
        private set

    /** morph 进度：拖拽中直接写、松手由落定 Job 弹簧写到静止态。 */
    var progress by mutableFloatStateOf(0f)
        private set

    /** 本会话基底档。 */
    var baseTier: DockState = DockState.D1
        private set

    private var lastUpDp = 0f
    private var lastDownDp = 0f
    private var settleJob: Job? = null

    /**
     * 新一手势：取消在途落定、基底取当前档、进度 snap 到基底静止态
     * （与拉伸新一手势取消释放弹簧同款口径；落定未走完即被抓取时
     * 进度直接定到已提交档的静止态，不残留半程）。
     */
    fun onDragStart(base: DockState) {
        settleJob?.cancel()
        settleJob = null
        baseTier = base
        dragging = true
        sessionActive = base == DockState.D1 || base == DockState.D2
        progress = liftRestProgress(base)
        lastUpDp = 0f
        lastDownDp = 0f
    }

    /** 拖拽跟手：进度直接写（映射本身即阈值曲线，不再二次平滑）。 */
    fun onDragMove(upDp: Float, downDp: Float) {
        if (!sessionActive) return
        lastUpDp = upDp
        lastDownDp = downDp
        progress = liftMorphProgress(baseTier, upDp, downDp, LIFT_TIER_THRESHOLD_DP)
    }

    /**
     * 松手：当前有效档 = 阈值翻转结果（拖回阈值以下即翻回原档）；
     * 档位变了先提交（onCommit，落定覆盖层在场、AnimatedContent
     * 未上场故无二次 crossfade），再以家族弹簧落定到静止态；
     * 落定完先调 onSettled（接线方把面板实测高写到目标档静止高，
     * 防覆盖层撤下当帧几何跳变），再关会话。
     * 会话未启动（横屏门禁／D3／Hidden）时纯 no-op。
     */
    fun onDragEnd(
        onCommit: (DockState) -> Unit,
        onSettled: (DockState) -> Unit,
    ) {
        if (!sessionActive) return
        dragging = false
        val target = resolveLiftTierTarget(baseTier, lastUpDp, lastDownDp, LIFT_TIER_THRESHOLD_DP)
            ?: baseTier
        if (target != baseTier) onCommit(target)
        val rest = liftRestProgress(target)
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(progress, rest, animationSpec = PetMotion.DragRelease) { v, _ ->
                progress = v
            }
            onSettled(target)
            sessionActive = false
        }
    }

    /** 会话 hygiene：宠物因进输入态等原因中途卸载时关会话（不残留覆盖层）。 */
    fun cancelSession() {
        settleJob?.cancel()
        settleJob = null
        dragging = false
        sessionActive = false
    }
}
