package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.PetState
import com.bobot.ailauncher.ui.pet.PetView
import com.bobot.ailauncher.ui.pet.ThinkDots

/**
 * 输入态伴随七仔（v0.68.101·单宠物原则，Bob 08:08 拍板）：全屏唯一的
 * 七仔在输入态的呈现——胶囊左侧小只，承担反应姿态（倾听/思考/开心，
 * 沿用 PetRepository 的 intentPose 反应状态机，与 R-1 讲述者同一套，
 * 只是换了站位）。静态时它不在场（Dock 上沿坐姿七仔在场），输入态时
 * 坐姿七仔随 Dock 面板让位/隐藏——任何时刻屏幕上恰有一只七仔（冒烟
 * 断言 pet 节点数 ==1 在静态与输入态各验一次）。
 *
 * 本体不可点：输入态的退出走点外观察者（本节点在意图区 bounds 内，
 * 点它不退出也不吞手势语义，与 R-1 输入态点讲述者的防竞态口径一致）。
 */
@Composable
fun InputPetCompanion(modifier: Modifier = Modifier) {
    val mood by PetRepository.mood.collectAsState()
    val intentPose by PetRepository.intentPose.collectAsState()
    val intentThinking by PetRepository.intentThinking.collectAsState()
    val mouth by PetRepository.mouth.collectAsState()
    val petState by PetRepository.petState.collectAsState()
    Box(
        modifier = modifier
            .size(76.dp)
            .testTag("pet_qizai"),
        contentAlignment = Alignment.Center
    ) {
        PetView(
            mood = intentPose ?: mood,
            mouth = mouth,
            blinking = false,
            sleepy = petState == PetState.SLEEPY,
            modifier = Modifier.size(76.dp)
        )
        // 意图思考态：头顶思考点点（与原讲述者同一语言，深色小药丸保底）
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
