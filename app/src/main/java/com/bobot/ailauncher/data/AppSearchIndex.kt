package com.bobot.ailauncher.data

import net.sourceforge.pinyin4j.PinyinHelper

/**
 * 应用中心搜索索引（PRD §三十三）。
 *
 * 四档匹配（按优先级）：
 * 1. 名称原样包含（去空格、不分大小写）——"微信"
 * 2. 自然语言关键词 → 包名片段 ——"打车"→滴滴/高德，"记东西"→备忘录类
 * 3. 拼音全拼/首字母 ——"weixin"/"wx"→微信，"zhifubao"/"zfb"→支付宝
 * 4. 包名包含 ——"mm"→com.tencent.mm
 * 另有模糊：查询串是目标的子序列也算命中（低分），如"w信"→微信。
 *
 * 拼音索引按应用列表构建一次（remember 缓存），查询时只做字符串匹配。
 */
object AppSearchIndex {

    /** 自然语言 → 包名片段（命中任一片段即相关） */
    private val keywordPackages: Map<String, List<String>> = mapOf(
        "打车" to listOf("didi", "t3go", "caocao", "autonavi", "baidumap"),
        "叫车" to listOf("didi", "t3go", "caocao"),
        "导航" to listOf("autonavi", "baidumap", "tencent.map", "navi"),
        "地图" to listOf("autonavi", "baidumap", "tencent.map"),
        "外卖" to listOf("meituan", "ele.me", "eleme"),
        "点餐" to listOf("meituan", "ele.me", "eleme"),
        "购物" to listOf("taobao", "jingdong", "pinduoduo", "vipshop", "suning"),
        "网购" to listOf("taobao", "jingdong", "pinduoduo"),
        "买东西" to listOf("taobao", "jingdong", "pinduoduo"),
        "支付" to listOf("alipay", "unionpay", "tencent.mm"),
        "付款" to listOf("alipay", "unionpay"),
        "银行" to listOf("icbc", "ccb.start", "cmbchina", "bank"),
        "音乐" to listOf("cloudmusic", "qqmusic", "kugou", "kuwo"),
        "听歌" to listOf("cloudmusic", "qqmusic", "kugou"),
        "视频" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv", "bili"),
        "看剧" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv"),
        "拍照" to listOf("mtxx", "lemon", "camera"),
        "修图" to listOf("mtxx", "lemon"),
        "浏览器" to listOf("quark", "ucmobile", "chrome", "browser"),
        "上网" to listOf("quark", "ucmobile", "chrome", "browser"),
        "邮件" to listOf("mobimail", "qqmail", "mail", "email"),
        "邮箱" to listOf("mobimail", "qqmail", "mail", "email"),
        "日历" to listOf("calendar"),
        "备忘" to listOf("note", "notion", "weread"),
        "记东西" to listOf("note", "notion", "memo"),
        "笔记" to listOf("note", "notion", "youdao"),
        "记事" to listOf("note", "memo"),
        "办公" to listOf("rimet", "lark", "wps", "dingtalk"),
        "开会" to listOf("rimet", "lark", "tencentmeeting", "zoom"),
        "聊天" to listOf("tencent.mm", "mobileqq", "lark"),
        "社交" to listOf("tencent.mm", "xiaohongshu", "weibo", "douyin", "zhihu"),
        "刷视频" to listOf("aweme", "gifmaker", "bili"),
        "短视频" to listOf("aweme", "gifmaker"),
        "看书" to listOf("weread", "dragon.read", "qidian", "zhihu"),
        "阅读" to listOf("weread", "dragon.read", "kindle"),
        "小说" to listOf("dragon.read", "qidian"),
        "旅游" to listOf("ctrip", "qunar", "taobao.trip"),
        "订票" to listOf("ctrip", "mobileticket", "qunar"),
        "火车票" to listOf("mobileticket", "ctrip"),
        "酒店" to listOf("ctrip", "qunar", "meituan"),
        "运动" to listOf("keep", "codoon", "gps"),
        "跑步" to listOf("keep", "codoon"),
        "输入法" to listOf("inputmethod", "sogou", "iflytek"),
        "天气" to listOf("weather"),
        "新闻" to listOf("news", "toutiao", "tencent.news"),
        "资讯" to listOf("news", "toutiao"),
    )

    data class Entry(
        val app: AppInfo,
        /** 名称拼音全拼（小写无调），如微信→weixin */
        val pinyinFull: String,
        /** 拼音首字母，如微信→wx */
        val pinyinInitials: String,
    )

    /** 关键词的拼音（懒加载）：输"dache"也能命中"打车" */
    private val keywordPinyin: Map<String, String> by lazy {
        keywordPackages.keys.associateWith { pinyinOf(it).first }
    }

    /** 为应用列表建索引（重操作，调用方 remember 缓存） */
    fun build(apps: List<AppInfo>): List<Entry> =
        apps.map { app ->
            val (full, initials) = pinyinOf(app.label.toString())
            Entry(app, full, initials)
        }

    /**
     * 搜索，返回按相关度排序的应用。空查询返回全部（保持原序）。
     */
    fun search(query: String, index: List<Entry>): List<AppInfo> {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isBlank()) return index.map { it.app }
        data class Hit(val app: AppInfo, val score: Int)
        val hits = mutableListOf<Hit>()
        // 自然语言关键词：查询包含关键词（或关键词拼音）→ 相关包名
        val keywordPkgs: Set<String> = buildSet {
            for ((k, pkgs) in keywordPackages) {
                if (q.contains(k) || k.contains(q)) {
                    addAll(pkgs)
                    continue
                }
                // 拼音关键词：q 至少 2 个字符才参与，避免单字母噪音
                if (q.length >= 2) {
                    val kp = keywordPinyin[k].orEmpty()
                    if (kp.isNotEmpty() && (kp.contains(q) || q.contains(kp))) addAll(pkgs)
                }
            }
        }
        for (e in index) {
            val name = e.app.label.toString().lowercase().replace(" ", "")
            val pkg = e.app.packageName.lowercase()
            val score = when {
                name.contains(q) -> 100
                keywordPkgs.isNotEmpty() && keywordPkgs.any { pkg.contains(it) } -> 80
                e.pinyinFull.contains(q) -> 70
                e.pinyinInitials.contains(q) && q.length >= 2 -> 60
                pkg.contains(q) -> 40
                isSubsequence(q, name) -> 30
                else -> 0
            }
            if (score > 0) hits += Hit(e.app, score)
        }
        return hits.sortedByDescending { it.score }.map { it.app }
    }

    /** 子序列模糊：q 的字符按序出现在目标里即可 */
    private fun isSubsequence(q: String, target: String): Boolean {
        if (q.length < 2 || q.length > target.length) return false
        var i = 0
        for (c in target) {
            if (c == q[i]) {
                i++
                if (i == q.length) return true
            }
        }
        return false
    }

    /** 中文→拼音全拼/首字母（pinyin4j，多音字取第一个读音） */
    private fun pinyinOf(label: String): Pair<String, String> {
        val full = StringBuilder()
        val initials = StringBuilder()
        for (ch in label) {
            val arr = try {
                PinyinHelper.toHanyuPinyinStringArray(ch)
            } catch (_: Exception) {
                null
            }
            if (arr != null && arr.isNotEmpty()) {
                // 默认格式如 "wei4"：去声调数字转小写
                val p = arr[0].lowercase().filter { it in 'a'..'z' }
                if (p.isNotEmpty()) {
                    full.append(p)
                    initials.append(p[0])
                }
            } else if (ch.isLetterOrDigit()) {
                // 英文/数字原样保留（英文应用名可直接搜）
                full.append(ch.lowercaseChar())
                initials.append(ch.lowercaseChar())
            }
            // 其他符号忽略
        }
        return full.toString() to initials.toString()
    }
}
