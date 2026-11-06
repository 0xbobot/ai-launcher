package com.bobot.ailauncher.data

import android.content.Context
import org.json.JSONObject

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

    /**
     * 查询侧拼音流换算（并行线 D 拼音流版）：已上屏文本 → 拼音全拼流。
     * 与索引构建共用同一字典与 [pinyinOf] 口径（汉字逐字取字典主读音、
     * 拉丁/数字小写原样、字典缺字原样占位、符号略过），保证查询流与
     * 索引侧 Entry.pinyinFull 的编码方式逐字一致。只做换算，不改变
     * search 的匹配与排序行为。
     */
    fun pinyinStreamOf(context: Context, text: String): String =
        pinyinOf(text, loadDict(context)).first

    /**
     * 单个汉字的拼音首字母（大写），查不到返回 '#'。
     * 供应用中心 A-Z 分组使用，比 GB2312 区位码更准（覆盖 20924 字）。
     */
    fun pinyinInitialOf(context: Context, c: Char): Char {
        val pinyin = loadDict(context)[c.toString()] ?: return '#'
        val first = pinyin.firstOrNull() ?: return '#'
        return if (first in 'a'..'z') first.uppercaseChar() else '#'
    }

    /** 自然语言 → 包名片段（命中任一片段即相关） */
    private val keywordPackages: Map<String, List<String>> = mapOf(
        "打车" to listOf("didi", "t3go", "caocao", "autonavi", "baidumap"),
        "叫车" to listOf("didi", "t3go", "caocao"),
        "导航" to listOf("autonavi", "baidumap", "tencent.map", "navi"),
        "地图" to listOf("autonavi", "baidumap", "tencent.map"),
        // 429（Bob 2026-11-05：应用中心搜“查去机场的地铁”应出地图应用）：
        // 能力池关键字补进映射——地铁走地图（高德/百度/腾讯地图，另含
        // 专用地铁应用的 metro 片段）。
        "地铁" to listOf("autonavi", "baidumap", "tencent.map", "metro"),
        "外卖" to listOf("meituan", "ele.me", "eleme"),
        "点餐" to listOf("meituan", "ele.me", "eleme"),
        "购物" to listOf("taobao", "jingdong", "pinduoduo", "vipshop", "suning"),
        "网购" to listOf("taobao", "jingdong", "pinduoduo"),
        "买东西" to listOf("taobao", "jingdong", "pinduoduo"),
        "支付" to listOf("alipay", "unionpay", "tencent.mm"),
        "付款" to listOf("alipay", "unionpay"),
        "银行" to listOf("icbc", "ccb.start", "cmbchina", "bank"),
        // 能力问题批（Bob 2026-10-25）：英文音乐应用包名里都有 music，
        // 不补这一片段时「听歌」永远搜不到 Spotify/Apple Music/
        // YouTube Music；「听歌」再补 kuwo 与「音乐」对齐。
        "音乐" to listOf("cloudmusic", "qqmusic", "kugou", "kuwo", "music"),
        "听歌" to listOf("cloudmusic", "qqmusic", "kugou", "kuwo", "music"),
        // 搜索同义词批（Bob 2026-11-05：搜“我想听周杰伦的歌”得 0 款，
        // 改成“音乐”才出 5 款——“歌”没进映射；与内容抽取 parseContent
        // “听X的歌”口径对齐）。“歌曲”由“歌”子串覆盖，单独立项便于查。
        "歌" to listOf("cloudmusic", "qqmusic", "kugou", "kuwo", "music"),
        "歌曲" to listOf("cloudmusic", "qqmusic", "kugou", "kuwo", "music"),
        "视频" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv", "bili", "youtube"),
        "看剧" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv"),
        // 搜索同义词批（Bob 2026-11-05）：同类缺口——“追剧/看电影/订机票”
        // 等常见说法里的能力词不在映射里，整句搜不到。
        // “剧”走“看剧”同款（剧集应用）；“电影”走“视频”同款。
        "剧" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv"),
        "电影" to listOf("qiyi.video", "qqlive", "youku", "mgtv.tv", "bili", "youtube"),
        "拍照" to listOf("mtxx", "lemon", "camera"),
        "修图" to listOf("mtxx", "lemon"),
        "浏览器" to listOf("quark", "ucmobile", "chrome", "browser"),
        "上网" to listOf("quark", "ucmobile", "chrome", "browser"),
        "邮件" to listOf("mobimail", "qqmail", "mail", "email"),
        "邮箱" to listOf("mobimail", "qqmail", "mail", "email"),
        "日历" to listOf("calendar"),
        // 429：能力池关键字——日程走日历（与“日历”同口径）。
        "日程" to listOf("calendar"),
        "备忘" to listOf("note", "notion", "weread"),
        "记东西" to listOf("note", "notion", "memo"),
        "笔记" to listOf("note", "notion", "youdao"),
        "记事" to listOf("note", "memo"),
        "办公" to listOf("rimet", "lark", "wps", "dingtalk"),
        "开会" to listOf("rimet", "lark", "tencentmeeting", "zoom"),
        "聊天" to listOf("tencent.mm", "mobileqq", "lark"),
        "社交" to listOf("tencent.mm", "xiaohongshu", "weibo", "douyin", "zhihu"),
        "刷视频" to listOf("aweme", "gifmaker", "bili", "youtube"),
        "短视频" to listOf("aweme", "gifmaker"),
        "看书" to listOf("weread", "dragon.read", "qidian", "zhihu"),
        "阅读" to listOf("weread", "dragon.read", "kindle"),
        "小说" to listOf("dragon.read", "qidian"),
        "旅游" to listOf("ctrip", "qunar", "taobao.trip"),
        "订票" to listOf("ctrip", "mobileticket", "qunar"),
        "火车票" to listOf("mobileticket", "ctrip"),
        // 搜索同义词批：常见说法“高铁”不含“火车票”，整句搜不到。
        "高铁" to listOf("mobileticket", "ctrip"),
        // 429：能力池关键字——航班走航旅应用（携程/去哪儿/飞猪/航旅纵横/飞常准，
        // 另含 flight 通用片段）。
        "航班" to listOf("ctrip", "qunar", "taobao.trip", "flight", "umetrip", "variflight"),
        // 搜索同义词批：常见说法“订机票”不含“航班/订票”，整句搜不到。
        "机票" to listOf("ctrip", "qunar", "taobao.trip", "flight", "umetrip", "variflight"),
        "酒店" to listOf("ctrip", "qunar", "meituan"),
        "运动" to listOf("keep", "codoon", "gps"),
        // 搜索同义词批：常见说法“健身”不含“运动/跑步”，整句搜不到。
        "健身" to listOf("keep", "codoon", "gps"),
        "跑步" to listOf("keep", "codoon"),
        "输入法" to listOf("inputmethod", "sogou", "iflytek"),
        "天气" to listOf("weather"),
        "新闻" to listOf("news", "toutiao", "tencent.news"),
        "资讯" to listOf("news", "toutiao"),
    )

    /** 拼音字典（assets/pinyin_dict.json，20924 字，懒加载一次） */
    @Volatile
    private var dict: Map<String, String>? = null

    private fun loadDict(context: Context): Map<String, String> {
        dict?.let { return it }
        synchronized(this) {
            dict?.let { return it }
            val map = mutableMapOf<String, String>()
            try {
                val json = context.assets.open("pinyin_dict.json")
                    .bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
            } catch (_: Exception) {
                // 字典缺失时拼音匹配自动降级（按名称/包名照常搜）
            }
            dict = map
            return map
        }
    }

    data class Entry(
        val app: AppInfo,
        /** 名称拼音全拼（小写无调），如微信→weixin */
        val pinyinFull: String,
        /** 拼音首字母，如微信→wx */
        val pinyinInitials: String,
    )

    /** 关键词的拼音（懒加载）：输"dache"也能命中"打车”；需先 ensureKeywordsLoaded */
    private var keywordPinyin: Map<String, List<String>> = emptyMap()

    /** 多音字别名：默认读音不是常用词读音时补上（如音乐→yinyue，银行→yinhang） */
    private val keywordPinyinAlias: Map<String, List<String>> = mapOf(
        "音乐" to listOf("yinyue"),
        "银行" to listOf("yinhang"),
    )

    private fun ensureKeywordsLoaded(context: Context) {
        if (keywordPinyin.isNotEmpty()) return
        val d = loadDict(context)
        keywordPinyin = keywordPackages.keys.associateWith { k ->
            listOf(pinyinOf(k, d).first) + (keywordPinyinAlias[k].orEmpty())
        }
    }

    /** 为应用列表建索引（重操作，调用方 remember 缓存；需 Context 读拼音字典） */
    fun build(context: Context, apps: List<AppInfo>): List<Entry> {
        val d = loadDict(context)
        return apps.map { app ->
            val (full, initials) = pinyinOf(app.label.toString(), d)
            Entry(app, full, initials)
        }
    }

    /**
     * 搜索，返回按相关度排序的应用。空查询返回全部（保持原序）。
     * 需 Context（读拼音字典），调用方 remember 索引后复用。
     */
    fun search(context: Context, query: String, index: List<Entry>): List<AppInfo> {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isBlank()) return index.map { it.app }
        ensureKeywordsLoaded(context)
        val keywordPkgs = keywordPackagesFor(q, keywordPinyin)
        val ranked = rank(index, q, keywordPkgs) { pkg ->
            AppUsageTracker.usageScoreForSearch(context, pkg)
        }
        // 反馈闭环（Bob 2026-11-06）：这句以前被用户在选择器里点名过，
        // 直接把那家提到最前面（查不到映射时原样返回）。
        return applyUserMapping(ranked, index, UserIntentMapping.lookup(context, q))
    }

    /**
     * 体检批D ⑥：纯逻辑接缝——由归一化查询算出命中的自然语言关键词包名片段集合。
     * 中文路径：查询包含关键词、或关键词包含查询（如只打了「打」）；
     * 拼音路径：需先经 [ensureKeywordsLoaded] 备好 [keywordPinyin]（测试可直接传合成表），
     * 且查询至少 2 个字符，避免单字母噪音。行为与原内联实现等价。
     */
    internal fun keywordPackagesFor(
        q: String,
        keywordPinyin: Map<String, List<String>>
    ): Set<String> = buildSet {
        for ((k, pkgs) in keywordPackages) {
            if (q.contains(k) || k.contains(q)) {
                addAll(pkgs)
                continue
            }
            // 拼音关键词：q 至少 2 个字符才参与，避免单字母噪音
            if (q.length >= 2) {
                val kps = keywordPinyin[k].orEmpty()
                if (kps.any { it.isNotEmpty() && (it.contains(q) || q.contains(it)) }) {
                    addAll(pkgs)
                }
            }
        }
    }

    // ---------- 反馈闭环（Bob 2026-11-06：识别不到时，让用户重试/选应用） ----------

    /**
     * 意图句标记：查询含任一标记或书名号时，像一句意图（而不是单纯的应用名）。
     * 覆盖本批已见的句式（“我想看《狂飙》”含 想/看/《》；“打开微信”含 开；
     * “帮我订一张去北京的机票”含 帮/订/去），也兜底将来类似说法。
     */
    private val INTENT_MARKERS = listOf(
        "想", "要", "帮", "去", "听", "看", "订", "查",
        "找", "给", "开", "播", "搜", "买", "点", "发", "做", "办"
    )

    /** 查询是否像一句意图（纯逻辑，可单测）：含意图标记或书名号。 */
    fun looksLikeIntent(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (q.contains("《") || q.contains("》")) return true
        return INTENT_MARKERS.any { q.contains(it) }
    }

    /** 查询是否命中任一自然语言关键词（生产入口，需 Context 读拼音字典）。 */
    fun hasKeywordMatch(context: Context, query: String): Boolean {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return false
        ensureKeywordsLoaded(context)
        return hasKeywordMatch(q, keywordPinyin)
    }

    /** 可测入口：显式传拼音字典（测试直接传合成表）。 */
    internal fun hasKeywordMatch(
        q: String,
        keywordPinyin: Map<String, List<String>>
    ): Boolean = q.isNotEmpty() && keywordPackagesFor(q, keywordPinyin).isNotEmpty()

    /**
     * 识别不到时的重试提示是否出现（纯逻辑，可单测）：像一句意图、且本地
     * 关键词没命中。调用方另行要求“0 结果且索引已就绪”（空态本身的条件）。
     * “不存在的应用”/“asdfgh”没有意图标记，不出现；“帮我订一张去北京的
     * 机票”有标记但“机票”是关键词（439），不出现。
     */
    fun shouldShowRetryPrompt(query: String, hasKeywordMatch: Boolean): Boolean =
        looksLikeIntent(query) && !hasKeywordMatch

    /**
     * 选择器排序（反馈闭环·用户选应用）：把已装应用按与查询的相关程度排，
     * 相关度高的在前。打分复用 [scoreFor]（名称/关键词/拼音/子序列同口径），
     * 同分按使用频次破同分（[usageOf]，生产传 AppUsageTracker 口径），
     * 再并列按名称稳定排序；不过滤——用户要找的应用哪怕 0 分也要能选到。
     */
    fun rankChooserApps(
        context: Context,
        query: String,
        index: List<Entry>,
        usageOf: (String) -> Double
    ): List<AppInfo> {
        val q = query.trim().lowercase().replace(" ", "")
        ensureKeywordsLoaded(context)
        return rankChooserApps(q, keywordPackagesFor(q, keywordPinyin), index, usageOf)
    }

    /** 可测入口：显式传归一化查询与关键词包集合。 */
    internal fun rankChooserApps(
        q: String,
        keywordPkgs: Set<String>,
        index: List<Entry>,
        usageOf: (String) -> Double
    ): List<AppInfo> {
        // 空查询不走 scoreFor（"".contains("") 恒真会把全员打成 100 分）：
        // 直接按使用频次排，名称作稳定底。
        if (q.isBlank()) {
            return index.map { it.app }
                .sortedWith(
                    compareByDescending<AppInfo> { usageOf(it.packageName) }
                        .thenBy { it.label.toString() }
                )
        }
        data class Hit(val app: AppInfo, val score: Int, val usage: Double)
        val hits = index.map { e ->
            val name = e.app.label.toString().lowercase().replace(" ", "")
            val pkg = e.app.packageName.lowercase()
            Hit(
                e.app,
                scoreFor(q, name, pkg, e.pinyinFull, e.pinyinInitials, keywordPkgs),
                usageOf(e.app.packageName)
            )
        }
        return hits.sortedWith(
            compareByDescending<Hit> { it.score }
                .thenByDescending { it.usage }
                .thenBy { it.app.label.toString() }
        ).map { it.app }
    }

    /**
     * 用户教的映射生效（纯逻辑，可单测）：被映射的应用提到最前面；
     * 没映射、或被映射的应用不在索引里时，原样返回。空结果时把被映射的
     * 应用补到最前面（下次同句搜到它）。
     */
    internal fun applyUserMapping(
        results: List<AppInfo>,
        index: List<Entry>,
        mappedPkg: String?
    ): List<AppInfo> {
        if (mappedPkg.isNullOrBlank()) return results
        val mapped = index.firstOrNull { it.app.packageName == mappedPkg }?.app
            ?: return results
        return listOf(mapped) + results.filter { it.packageName != mappedPkg }
    }

    /** 体检批D ⑥：纯逻辑接缝——单个条目的六档命中分（0 表示未命中），档位口径见类头注释。 */
    internal fun scoreFor(
        q: String,
        name: String,
        pkg: String,
        pinyinFull: String,
        pinyinInitials: String,
        keywordPkgs: Set<String>
    ): Int = when {
        name.contains(q) -> 100
        keywordPkgs.isNotEmpty() && keywordPkgs.any { pkg.contains(it) } -> 80
        pinyinFull.contains(q) -> 70
        pinyinInitials.contains(q) && q.length >= 2 -> 60
        pkg.contains(q) -> 40
        isSubsequence(q, name) -> 30
        else -> 0
    }

    /**
     * 体检批D ⑥：纯逻辑接缝——打分 + 排序。空查询返回全部（保持原序）；
     * 排序键 = 档分降序、同档内使用频次降序破同分（频次经 [usageOf] 注入，
     * 生产传 AppUsageTracker 口径、测试传假表）；频次也并列时稳定排序保持索引原序。
     */
    internal fun rank(
        index: List<Entry>,
        q: String,
        keywordPkgs: Set<String>,
        usageOf: (String) -> Double
    ): List<AppInfo> {
        if (q.isBlank()) return index.map { it.app }
        data class Hit(val app: AppInfo, val score: Int, val usage: Double)
        val hits = mutableListOf<Hit>()
        for (e in index) {
            val name = e.app.label.toString().lowercase().replace(" ", "")
            val pkg = e.app.packageName.lowercase()
            val score = scoreFor(q, name, pkg, e.pinyinFull, e.pinyinInitials, keywordPkgs)
            if (score > 0) hits += Hit(e.app, score, usageOf(e.app.packageName))
        }
        // 意图改进一批：同档分内按使用频次破同分（只破同分——排序键档分在前，
        // 频次再高也压不过更高档的精确命中）；频次也并列时 sortedWith 稳定排序
        // 保持索引原序。首页本地结果与应用中心搜索共用本函数，同享此排序。
        return hits.sortedWith(
            compareByDescending<Hit> { it.score }.thenByDescending { it.usage }
        ).map { it.app }
    }

    /** 子序列模糊：q 的字符按序出现在目标里即可 */
    internal fun isSubsequence(q: String, target: String): Boolean {
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

    /** 中文→拼音全拼/首字母（内置字典，多音字取第一读音；缺字自动降级） */
    internal fun pinyinOf(label: String, dict: Map<String, String>): Pair<String, String> {
        val full = StringBuilder()
        val initials = StringBuilder()
        for (ch in label) {
            val py = dict[ch.toString()]
            if (py != null && py.isNotEmpty()) {
                full.append(py)
                initials.append(py[0])
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
