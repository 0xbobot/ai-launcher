package com.bobot.ailauncher.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
* 上下文包拼装纯逻辑单测（意图改进二批）：喂假快照断言输出，
* 不碰 Android / 日历 / 天气等真实数据源。
*/
class IntentContextPackTest {

@Test
fun `full snapshot renders every section`() {
val text = IntentContextPack.build(
IntentContextSnapshot(
nowText = "2026年10月21日 星期三 22:20（晚上）",
nextMeeting = "23:00 展台方案评审 · 3F 会议室",
weather = "深圳 晴 28°（今日 24~30°）",
topApps = listOf("微信", "飞书", "高德地图"),
installedApps = listOf("微信", "飞书", "高德地图", "日历"),
pendingReplies = 3
)
)
assertTrue(text.contains("【现在】2026年10月21日 星期三 22:20（晚上）"))
assertTrue(text.contains("【下一会议】23:00 展台方案评审 · 3F 会议室"))
assertTrue(text.contains("【天气】深圳 晴 28°（今日 24~30°）"))
assertTrue(text.contains("【常用应用】微信、飞书、高德地图"))
assertTrue(text.contains("【已装应用】微信、飞书、高德地图、日历"))
assertTrue(text.contains("【待回消息】3 条"))
// 全量塞得下时不许出现截断尾巴
assertFalse(text.contains("其余略"))
}

@Test
fun `missing optionals are omitted, never fabricated`() {
val text = IntentContextPack.build(
IntentContextSnapshot(nowText = "2026年10月21日 星期三 09:00（上午）")
)
assertEquals("【现在】2026年10月21日 星期三 09:00（上午）", text)
}

@Test
fun `zero pending replies are omitted`() {
val text = IntentContextPack.build(
IntentContextSnapshot(nowText = "T", pendingReplies = 0)
)
assertFalse(text.contains("【待回消息】"))
}

@Test
fun `installed apps are truncated to the char budget with total noted`() {
// 200 个较长名字总长远超 700 字预算，必须触发截断
//（首版用 120 个短名总长没到上限，拼装器无从截——数据问题，非实现问题）
val many = (1..200).map { "应用软件$it"}
val text = IntentContextPack.build(
IntentContextSnapshot(
nowText = "2026年10月21日 星期三 22:20（晚上）",
installedApps = many
)
)
assertTrue(
"总长 ${text.length} 超过上限 ${IntentContextPack.MAX_CHARS}",
text.length <= IntentContextPack.MAX_CHARS
)
assertTrue(text.contains("…（共200个，其余略）"))
// 时间这种高优先项不能被名录挤掉
assertTrue(text.startsWith("【现在】"))
}
}
