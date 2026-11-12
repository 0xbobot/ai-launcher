package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会议链接 → 会议应用包名映射单测（第十七批）：
 * 先走会议应用、接不住才走浏览器。映射是纯函数（字符串处理，
 * 不依赖 android.net.Uri，JVM 可测）；执行层的 resolveActivity
 * 校验与落回浏览器（Context 才能测）见 openCalendarEvent 的 diff
 * 复核＋Bob 真机验收——飞书已装时直接进飞书入会页、不进浏览器，
 * 飞书未装时仍走浏览器。
 *
 * 第十八批：feishuJoinDeepLink 单测（下半部分）——飞书先试官方入会
 * 深链 feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=<会议号>，
 * 会议号即 vc.feishu.cn/j/<9 位数字> 里的数字（已从飞书 APK v7.70.12
 * 的 manifest＋dex 反汇编核实）；提不到会议号时返回 null 落回 489 链。
 *
 * 第二十批：深链必须带 source=widget——飞书 VideoChatHandler 按 source
 * 分支，带号的入会分支 h.S 只在 source=="widget" 时才到；无 source 时
 * 落到无号的手动页 h.R（Bob 2026-11-12 录屏：ID 栏空）。旧格式
 * （无 source、只有 action+num）不再出现。
 */
class SmartCardMeetingAppTest {

    // ---------- 飞书 ----------

    @Test
    fun feishu_vcSubdomain() {
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://vc.feishu.cn/j/483456789")
        )
    }

    @Test
    fun feishu_meetingSubdomain() {
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://meeting.feishu.cn/xxx")
        )
    }

    @Test
    fun feishu_apexDomain() {
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://feishu.cn/xxx")
        )
    }

    @Test
    fun feishu_deepSubdomain() {
        // 任意 *.feishu.cn 子域都走飞书（endsWith .feishu.cn）。
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://a.b.feishu.cn/x?y=1")
        )
    }

    @Test
    fun feishu_caseInsensitive() {
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://VC.FEISHU.CN/j/483456789")
        )
    }

    // ---------- 腾讯会议 / VooV ----------

    @Test
    fun tencentMeeting_domain() {
        assertEquals(
            "com.tencent.wemeet.app",
            meetingAppPackage("https://meeting.tencent.com/dm/abc123")
        )
    }

    @Test
    fun tencentMeeting_wemeetHost() {
        // MEETING_DOMAINS 里的 wemeet：host 含 wemeet 走腾讯会议。
        assertEquals(
            "com.tencent.wemeet.app",
            meetingAppPackage("https://wemeet.qq.com/xxx")
        )
    }

    @Test
    fun voovMeeting_domain() {
        // voovmeeting.com 是国际版 VooV 的域，走 VooV，不走腾讯会议。
        assertEquals(
            "com.tencent.voov",
            meetingAppPackage("https://voovmeeting.com/xxx")
        )
    }

    // ---------- Zoom / Teams / 钉钉 / Webex ----------

    @Test
    fun zoom_usDomain() {
        assertEquals(
            "us.zoom.videomeetings",
            meetingAppPackage("https://zoom.us/j/123456789")
        )
    }

    @Test
    fun zoom_comDomain() {
        assertEquals(
            "us.zoom.videomeetings",
            meetingAppPackage("https://zoom.com/j/123456789")
        )
    }

    @Test
    fun teams_microsoftDomain() {
        assertEquals(
            "com.microsoft.teams",
            meetingAppPackage("https://teams.microsoft.com/l/meetup-join/abc")
        )
    }

    @Test
    fun teams_liveDomain() {
        assertEquals(
            "com.microsoft.teams",
            meetingAppPackage("https://teams.live.com/meet/abc")
        )
    }

    @Test
    fun dingtalk_domain() {
        assertEquals(
            "com.alibaba.android.rimet",
            meetingAppPackage("https://dingtalk.com/xxx")
        )
    }

    @Test
    fun webex_domain() {
        assertEquals(
            "com.cisco.webex.meetings",
            meetingAppPackage("https://xxx.webex.com/xxx")
        )
    }

    // ---------- 接不住：不在白名单／不是链接 ----------

    @Test
    fun unknownDomain_returnsNull() {
        // 不在 MEETING_DOMAINS 里的域：不定向任何应用，落回浏览器。
        assertNull(meetingAppPackage("https://meet.google.com/abc-defg-hij"))
        assertNull(meetingAppPackage("https://example.com/meeting"))
    }

    @Test
    fun lookalikeDomain_returnsNull() {
        // 仿冒域名防线：feishu.cn.evil.com 不是 feishu.cn 的子域，
        // notfeishu.cn 也不是——都不能定向到飞书。
        assertNull(meetingAppPackage("https://feishu.cn.evil.com/j/1"))
        assertNull(meetingAppPackage("https://notfeishu.cn/j/1"))
        assertNull(meetingAppPackage("https://fakefeishu.cn/j/1"))
    }

    @Test
    fun nonHttpUrl_returnsNull() {
        // 提取到的会议链接只会是 https?://；裸文本／自定义 scheme 不定向。
        assertNull(meetingAppPackage("not a url"))
        assertNull(meetingAppPackage("wemeet://join/123"))
        assertNull(meetingAppPackage(""))
    }

    @Test
    fun hostWithPortAndQuery_stillMaps() {
        // host 带端口／query／fragment 仍按 host 定向。
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://vc.feishu.cn:443/j/483456789?x=1#frag")
        )
    }

    @Test
    fun trailingDotHost_stillMaps() {
        // FQDN 末尾的点不影响判定。
        assertEquals(
            "com.ss.android.lark",
            meetingAppPackage("https://vc.feishu.cn./j/1")
        )
    }

    // ---------- 飞书入会深链（第十八批） ----------

    @Test
    fun feishuDeepLink_vcUrl() {
        //  canonical 形态：vc.feishu.cn/j/<9 位会议号> →
        // feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=<会议号>。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://vc.feishu.cn/j/483456789")
        )
    }

    @Test
    fun feishuDeepLink_hasSourceWidget() {
        // 第二十批：深链必须带 source=widget——飞书 VideoChatHandler
        // 按 source 分支，带号的入会分支 h.S 只在 source=="widget" 时才到；
        // 无 source 时落到无号的手动页 h.R（Bob 2026-11-12 录屏：点
        // 「加入会议」后飞书打开手动「会议 ID」输入页、ID 栏空，链接
        // https://vc.feishu.cn/j/972810207）。用 Bob 那场会的真号验。
        val link = feishuJoinDeepLink("https://vc.feishu.cn/j/972810207")
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=972810207",
            link
        )
        // 无 source 的旧格式（query 以 action=join 开头）不再出现。
        assertTrue(link!!.startsWith("feishu://applink.feishu.cn/client/videochat/open?source=widget&"))
    }

    @Test
    fun feishuDeepLink_caseInsensitive() {
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://VC.FEISHU.CN/J/483456789")
        )
    }

    @Test
    fun feishuDeepLink_withQueryAndFragment() {
        // query/fragment 不进会议号。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://vc.feishu.cn/j/483456789?x=1#frag")
        )
    }

    @Test
    fun feishuDeepLink_dashedNumber() {
        // 数字段里夹非数字字符时只留数字（与应用侧 g() 同口径）。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://vc.feishu.cn/j/48345-6789")
        )
    }

    @Test
    fun feishuDeepLink_longNumberTruncated() {
        // 超过 12 位截到 12 位（与应用侧 g() 同口径）。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=123456789012",
            feishuJoinDeepLink("https://vc.feishu.cn/j/12345678901234")
        )
    }

    @Test
    fun feishuDeepLink_lastSegmentFallback() {
        // 没有 /j/ 时取最后一段（meeting.feishu.cn 等其它形态的兜底）。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://meeting.feishu.cn/483456789")
        )
    }

    @Test
    fun feishuDeepLink_trailingDotHost() {
        // FQDN 末尾的点不影响判定。
        assertEquals(
            "feishu://applink.feishu.cn/client/videochat/open?source=widget&action=join&num=483456789",
            feishuJoinDeepLink("https://vc.feishu.cn./j/483456789")
        )
    }

    @Test
    fun feishuDeepLink_noNumber_returnsNull() {
        // 提不到会议号：不编深链，返回 null 落回 489 链（显式包名 https → 浏览器）。
        assertNull(feishuJoinDeepLink("https://meeting.feishu.cn/xxx"))
        assertNull(feishuJoinDeepLink("https://feishu.cn/xxx"))
        assertNull(feishuJoinDeepLink("https://vc.feishu.cn/j/"))
        assertNull(feishuJoinDeepLink("https://vc.feishu.cn/"))
    }

    @Test
    fun feishuDeepLink_shortNumber_returnsNull() {
        // 会议号是 9 位数字；少于 6 位的大多是其它 id，不认。
        assertNull(feishuJoinDeepLink("https://vc.feishu.cn/j/123"))
        assertNull(feishuJoinDeepLink("https://vc.feishu.cn/j/2024"))
    }

    @Test
    fun feishuDeepLink_nonFeishu_returnsNull() {
        // 其它会议应用不走飞书深链（各走 489 的显式包名 https 意图）。
        assertNull(feishuJoinDeepLink("https://zoom.us/j/123456789"))
        assertNull(feishuJoinDeepLink("https://meeting.tencent.com/dm/abc123"))
        assertNull(feishuJoinDeepLink("https://meet.google.com/abc-defg-hij"))
    }

    // ---------- 钉钉（第二十一批） ----------

    @Test
    fun dingtalkDeepLink_roomCodeAndConfid() {
        // 两参数都取到就都带（原样透传，顺序 roomCode 在前、confid 在后）。
        assertEquals(
            "dingtalk://dingtalkclient/action/join_conf?roomCode=ABC123xyz&confid=12345678901",
            dingtalkJoinDeepLink(
                "https://meeting.dingtalk.com?visitorindt&showmenu=false&roomCode=ABC123xyz&confid=12345678901"
            )
        )
    }

    @Test
    fun dingtalkDeepLink_roomCodeOnly() {
        // 只有 roomCode → 只带 roomCode（已登录/未登录都能走通，主推形态）。
        assertEquals(
            "dingtalk://dingtalkclient/action/join_conf?roomCode=ABC123xyz",
            dingtalkJoinDeepLink(
                "https://meeting.dingtalk.com?visitorindt&showmenu=false&roomCode=ABC123xyz"
            )
        )
    }

    @Test
    fun dingtalkDeepLink_confidOnly_returnsNull() {
        // 只有 confid → 返回 null：已登录分支完全不读 confid，只拿（空的）
        // roomCode 走，会落到通用登录/入会流程带不上这场会——不要只传它。
        assertNull(
            dingtalkJoinDeepLink("https://meeting.dingtalk.com?confid=12345678901")
        )
    }

    @Test
    fun dingtalkDeepLink_neitherParam_returnsNull() {
        // 两个都没有 → 返回 null，落回 489 链，不要编造。
        assertNull(dingtalkJoinDeepLink("https://meeting.dingtalk.com?visitorindt&showmenu=false"))
        assertNull(dingtalkJoinDeepLink("https://meeting.dingtalk.com"))
    }

    @Test
    fun dingtalkDeepLink_nonDingtalk_returnsNull() {
        // 非钉钉链接 → null（各走自己的链，不误进钉钉深链）。
        assertNull(dingtalkJoinDeepLink("https://vc.feishu.cn/j/972810207"))
        assertNull(dingtalkJoinDeepLink("https://meeting.tencent.com/dm/abc123"))
    }

    @Test
    fun dingtalkDeepLink_otherApps_returnsNull() {
        // 其余 5 家各 1 项：dingtalkJoinDeepLink 对它们的链接返回 null，
        // 锁死它们走 489 链/浏览器兜底，不误进钉钉深链。
        // 腾讯会议 (c)：meeting.tencent.com，无 roomCode/confid。
        assertNull(dingtalkJoinDeepLink("https://meeting.tencent.com/dm/abc123"))
        // VooV (c)：voovmeeting.com。
        assertNull(dingtalkJoinDeepLink("https://voovmeeting.com/dm/abc123"))
        // Webex (a)：489 显式包名 https 意图已能直接进应用。
        assertNull(dingtalkJoinDeepLink("https://example.webex.com/meet/join/123456789"))
        // Zoom (a)：489 已能直接进 Zoom。
        assertNull(dingtalkJoinDeepLink("https://zoom.us/j/123456789"))
        // Teams (a)：489 已能直接进 Teams。
        assertNull(dingtalkJoinDeepLink("https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc"))
    }
}
