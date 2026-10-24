package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.core.pet.PetState
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.ui.pet.PetView
import com.bobot.ailauncher.ui.pet.ThinkDots
import com.bobot.ailauncher.ui.theme.AILauncherColors

/**
 * 输入态七仔（v0.68.101 单宠物原则；v6 栖坐胶囊上沿）：全屏唯一的七仔
 * 在输入态的呈现——站在胶囊顶缘极左（原型定稿 input-sit-v6 图 1，
 * Bob 2026-10-24 18:45 拍板，带三处改）。承担反应姿态（倾听/思考/
 * 开心，沿用 PetRepository 的 intentPose 反应状态机）。静态时它不在场
 * （Dock 上沿栖坐七仔在场），输入态时 Dock 栖坐随 Dock 让位——任何时刻
 * 屏幕上恰有一只七仔（layout_check 的 one_pet 断言两态各验）。
 *
 * 本体不可点：输入态的退出走点外观察者（本节点在意图区 bounds 内，
 * 点它不退出也不吞手势语义，与 R-1 输入态点讲述者的防竞态口径一致）。
 *
 * v6 活感（Bob 2026-10-24 18:13：坐还是站关系不大、重点是看上去像、
 * 不要只是一个摆件）：本体微倾 -5°（朝向右侧推荐问一侧）+ 头边一颗
 * 静态琥珀星光——倾斜与星光只做「它在场、在看推荐」的读法，不做动画
 * （省电口径不变）；尺寸由宿主按胶囊高 ×1.3 传入，[size] 不再定死 92dp。
 */
@Composable
fun InputPetCompanion(modifier: Modifier = Modifier, size: Dp = 92.dp) {
    // v0.68.102（310 冻结修复·入场削峰）：本体延迟 300ms 再挂载——进态
    // 首帧已有 IME resize + 槽位展开 + hero 紧凑在抢主线程，PetView 的
    // 大图首次解码与多路无限动画同帧启动是压垮点之一（CI 实证进态后
    // 进程死亡/楔死）。延迟窗内以同尺寸同 testTag 占位（布局与「恰一只
    // 七仔」计数口径不变），七仔在胶囊落定后随即就位，呈现语义不变。
    // v0.68.102（310 冻结续修·probe-freeze H1/H2/H3 对照实证）：300ms 仍
    // 落在入场爆发窗内——深色+无聚焦自动进态（问七仔路径）在本体挂载
    // 瞬间确定性点火整机图形栈楔死（与 IME/焦点无关：H1/H2 本体不挂载
    // 即活、H3 落定后 1500ms 挂载即活）。延迟提到入场动画全落定之后
    // （1500ms，真机帧成本约为 CI 软渲染的十分之一、余量充足），占位
    // 语义不变，本体姿态/反应语义不变。F 批改站位、v4 改紧凑行后沿用
    // 此削峰，不许删。
    var settled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        settled = true
    }
    if (!settled) {
        Box(
            modifier = modifier
                // requiredSize：坐沿几何是定稿尺寸，不许被宿主剩余高度钳制
                // （CI 首轮实测节点高被钳到 68dp、遮挡归零）
                .requiredSize(size)
                .testTag("pet_qizai")
        )
        return
    }
    val mood by PetRepository.mood.collectAsState()
    val intentPose by PetRepository.intentPose.collectAsState()
    val intentThinking by PetRepository.intentThinking.collectAsState()
    val mouth by PetRepository.mouth.collectAsState()
    val petState by PetRepository.petState.collectAsState()
    Box(
        modifier = modifier
            .requiredSize(size)
            .testTag("pet_qizai")
            // v6：本体微倾 -5°（顶向右侧推荐问倾，读出活感不是贴纸；
            // graphicsLayer 只改渲染不改布局 bounds，layout_check 的
            // 几何断言口径不受影响）
            .graphicsLayer { rotationZ = -5f },
        contentAlignment = Alignment.Center
    ) {
        // v6：头边星光（输入态 only、静态琥珀色、低调 14dp）——「它在看」
        // 的点缀，与思考点点（intentThinking 时）不冲突（各居一角）。
        // 注意：Canvas draw 块内不能调 @Composable（AILauncherColors.current）
        // 且 size 参数会遮住 DrawScope.size——颜色先在组合期读出、画布
        // 尺寸用 this.size 显式取 DrawScope 的。
        val sparkleColor = AILauncherColors.current.Accent
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 2.dp)
                .size(14.dp)
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = this.size.width / 2f
            val star = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(star, color = sparkleColor)
        }
        PetView(
            mood = intentPose ?: mood,
            mouth = mouth,
            blinking = false,
            sleepy = petState == PetState.SLEEPY,
            // v4：紧凑行语境抑制 FETCH 上浮（-52px 会把七仔掀离与推荐问
            // 的居中对齐，读成上飘）；其他语境不传，保持原姿态语言
            suppressPoseLift = true,
            modifier = Modifier.requiredSize(size)
        )
        // 意图思考态：头顶思考点点（随新站位落在头顶，与原讲述者同一语言，
        // 深色小药丸保底）
        if (intentThinking) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(
                        Color(0xFF141428).copy(alpha = 0.72f),
                        RoundedCornerShape(99.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                ThinkDots()
            }
        }
    }
}
