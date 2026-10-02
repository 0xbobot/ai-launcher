package com.bobot.ailauncher.data

import androidx.compose.ui.graphics.Color

/** 宠物整理员的四个文件夹 */
enum class PetCat(
    val cnName: String,
    val color: Color,
    val tabLabel: String,
    val tabEmoji: String
) {
    IMP("重要", Color(0xFFE03131), "重要", "🔴"),
    WORK("工作", Color(0xFF3B7DE0), "工作", "💼"),
    FUN("娱乐", Color(0xFF2F9E44), "娱乐", "🎮"),
    PRIV("隐私", Color(0xFF868E96), "隐私", "🔒");
}

/** 宠物要整理的一件东西：通知或日程 */
data class PetItem(
    val id: String,
    val cat: PetCat,
    val sortDesc: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
    val packageName: String,
    val isCalendar: Boolean = false
)

/**
 * 宠物分类器 v1：纯规则（发件包名 + 标题/正文关键词 + 时间）。
 * 大模型版以后再接：把 classify() 换成走 LlmRouter 的实现即可，调用方不动。
 */
object PetClassifier {

    // ---- 关键词表 ----
    private val privKeywords = listOf(
        "验证码", "校验码", "动态码", "动态密码", "短信码",
        "银行", "支付", "付款", "转账", "账单", "信用卡", "借记卡",
        "花呗", "借呗", "余额", "扣款", "退款", "密码"
    )
    private val impKeywords = listOf(
        "老板", "领导", "总监", "经理",
        "妈妈", "爸爸", "老婆", "老公", "爸", "妈",
        "紧急", "截止", "马上", "立刻", "尽快", "加急",
        "重要", "deadline", "十万火急"
    )
    private val workKeywords = listOf(
        "会议", "评审", "prd", "需求", "上线", "排期", "周报", "日报",
        "打卡", "审批", "报销", "oa", "项目", "迭代", "版本",
        "@所有人", "@全体成员", "例会", "站会"
    )
    private val funKeywords = listOf(
        "点赞", "评论", "粉丝", "直播", "开播", "更新了", "发布了",
        "视频", "动态", "热搜", "上新", "优惠", "秒杀"
    )

    // ---- 包名表 ----
    private val workPackages = listOf(
        "dingtalk", "lark", "feishu", "wechatwork", "wxwork",
        "mail", "exchange", "outlook", "gmail",
        "calendar", "dingding"
    )
    private val funPackages = listOf(
        "douyin", "aweme", "xiaohongshu", "weibo", "bilibili",
        "zhihu", "taobao", "tmall", "jd", "pinduoduo",
        "music", "netease", "kugou", "qqmusic", "youtube",
        "tiktok", "instagram", "twitter"
    )
    private val bankPackages = listOf("bank", "icbc", "ccb", "abcbank", "cmb", "alipay", "wallet", "pay")

    /**
     * 返回 (分类, 一句话描述)，描述用于 sort-tag 展示决策，
     * 例：("这是老板的消息", PetCat.IMP)。
     */
    fun classify(
        packageName: String,
        appName: String,
        title: String,
        text: String
    ): Pair<PetCat, String> {
        val pkg = packageName.lowercase()
        val body = "$title $text"
        val bodyLow = body.lowercase()

        fun hit(list: List<String>) = list.any { body.contains(it) || bodyLow.contains(it) }

        // 1. 隐私：验证码 / 银行 / 支付 —— 打码存放
        if (hit(privKeywords) || bankPackages.any { pkg.contains(it) }) {
            return PetCat.PRIV to "这是私密内容"
        }
        // 2. 重要：老板/家人/紧急关键词
        if (hit(impKeywords)) {
            val who = impKeywords.firstOrNull { body.contains(it) } ?: "重要"
            return PetCat.IMP to "这是${who}的消息"
        }
        // 3. 工作：工作类应用或工作关键词
        if (workPackages.any { pkg.contains(it) } || hit(workKeywords)) {
            return PetCat.WORK to "这是工作消息"
        }
        // 4. 娱乐：社交娱乐应用或娱乐关键词
        if (funPackages.any { pkg.contains(it) } || hit(funKeywords)) {
            return PetCat.FUN to "这是娱乐内容"
        }
        // 5. 默认：工作（消息类多半与人相关，宁可放工作不放娱乐）
        return PetCat.WORK to "这是条新消息"
    }
}
