package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 会议链接 → 会议应用包名映射单测（第十七批）：
 * 先走会议应用、接不住才走浏览器。映射是纯函数（字符串处理，
 * 不依赖 android.net.Uri，JVM 可测）；执行层的 resolveActivity
 * 校验与落回浏览器（Context 才能测）见 openCalendarEvent 的 diff
 * 复核＋Bob 真机验收——飞书已装时直接进飞书入会页、不进浏览器，
 * 飞书未装时仍走浏览器。
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
}
