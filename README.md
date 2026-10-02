# AI桌面（AI Launcher）

> **v0.5**：自动 OTA 升级——版本 manifest 与安装包托管在 GitHub Pages（bobot.is-a.dev/ai-launcher/）；App 启动每天自动检查一次 + 设置页可手动触发；DownloadManager 后台下载 + 下载完成自动弹安装（FileProvider）；发布走 `publish-ota` workflow 手动触发（需配 PAGES_DEPLOY_TOKEN）。
> **v0.4**：首页通知卡片可点（打开对应 App）；日历三层重设计（下一个日程大卡片+倒计时 / 全天小字 / 后续小行）；能力页分类行重设计（折叠态直接横排真实 App 图标可点，展开态动画铺 4 列网格）；大模型报错透出（HTTP 状态码+服务端 error.message，baseUrl 自动规范化）；全部应用 A-Z 快速索引（右侧纵条+中央悬浮大字母）；首页/能力页底部小圆点 pager 指示器。
> **v0.3**：修真机反馈 4 问题 + 能力分类动态化——`QUERY_ALL_PACKAGES` 取全量应用；通知授权后自动重绑监听；语音改系统 RecognizerIntent（国产机可用）；首页排版收紧；能力页分组只显示本机已安装的真实应用（空分组隐藏）。
> **v0.2**：真桌面的全屏手势导航（去掉底部 tab；首页左滑进能力页、上滑打开应用抽屉）；意图框接大模型路由（设置页配 Key，默认 DeepSeek）；首页新增今日日程（日历）与语音输入。

AI 时代的 Android 启动桌面：不是图标货架，而是 **"意图入口 + 正在进行时"** 的系统级 AI 空间。

- **首页**：问候 + 意图输入框 + 「正在进行时」信息流（通知监听驱动）
- **能力**：按意图组织应用（出行 / 支付 / 办公 / 生活 / 购物），App 是能力的供应商
- **应用**：全部应用列表（中文排序）+ 搜索

## 技术栈

Kotlin 2.0.20 + Jetpack Compose (BOM 2024.10.01) + Material3 + navigation-compose，
AGP 8.5.2，minSdk 29 / targetSdk 34，Gradle Kotlin DSL。

## 冷启动流程（第一印象）

首次打开走 3 步引导（`ui/onboarding/OnboardingScreen.kt`），每步都可跳过：

1. **欢迎**：「把手机变成会干活的空间 / 说出你想干嘛，不用找 App」
2. **设为默认桌面**：跳 `Settings.ACTION_HOME_SETTINGS`，用 `PackageManager.resolveActivity`
   检测是否已是默认（点"下一步"时实时检测）
3. **通知读取**：跳 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`，用
   `NotificationManagerCompat.getEnabledListenerPackages` 检测；这是「正在进行时」
   的数据来源
4. **一切就绪**：写 `SharedPreferences("ai_launcher").onboarded=true`，进入主界面

## 能力注册表（Capability Registry）

`app/src/main/assets/capabilities.json` 定义全部能力，`data/CapabilityRegistry.kt`
负责加载与跳转。Schema：

```json
{
  "groups": [
    {
      "id": "travel", "label": "出行", "icon": "directions_car",
      "capabilities": [
        {
          "id": "ditie",
          "label": "地铁",
          "icon": "directions_subway",
          "packageName": "com.autonavi.minimap",
          "deeplink": "amap://route?sourceApplication=ai-launcher&dlat={lat}&dlon={lon}&dev=0&t=1",
          "fallbackToLauncher": true,
          "note": "需真机验证"
        }
      ]
    }
  ]
}
```

跳转策略（`resolveAndLaunch`）：deep link 直达 App 内页面 → 失败则打开 App 主页
→ 都失败返回 false（调用方 toast 降级）。`deeplink` 支持 `{param}` 占位符，
调用时传入 `params` 替换，未传入的占位符会被清空。

首页意图框当前是演示版路由：关键词小写匹配（打车/叫车→滴滴、地铁→高德、
火车→12306、航班/飞机→航旅纵横、酒店→携程），后续可替换为大模型意图理解。

## CI 出包

`.github/workflows/build-apk.yml`：push / PR 自动跑
`gradle :app:assembleDebug`，产物 `app-debug.apk` 以 artifact
`ai-launcher-debug-apk` 上传。仓库不提交 gradle wrapper，CI 用
`gradle/actions/setup-gradle` 提供 Gradle。

## OTA 自动升级

版本信息与安装包托管在 Bob 的 GitHub Pages 站：

- manifest：`https://bobot.is-a.dev/ai-launcher/update.json`
  ```json
  {"versionCode":7,"versionName":"0.5.0",
   "apkUrl":"https://bobot.is-a.dev/ai-launcher/ai-launcher-latest.apk",
   "changelog":"…","forceUpdate":false}
  ```
- 安装包：`https://bobot.is-a.dev/ai-launcher/ai-launcher-latest.apk`

App 侧（`data/OtaUpdater.kt`）：启动时每天最多自动检查一次（`SharedPreferences("ota")`
记 lastCheck），设置页可手动触发；有新版弹更新对话框（changelog + 立即更新/稍后，
`forceUpdate=true` 时不给"稍后"）；下载走系统 DownloadManager（通知栏可见进度），
下载完成广播 + 每次 onResume 检查到包下好就弹安装（FileProvider 授权给系统安装器，
只弹一次）；无"安装未知应用"权限时先引导去系统设置开启。

发布流程：到仓库 Actions 页手动触发 `Publish OTA` workflow（可填 changelog），它会
取最近一次构建成功的 APK + 当前 versionCode/versionName 生成 update.json，一起推到
`clawbobot.github.io` 仓库的 `ai-launcher/` 目录。需要先在仓库 Settings > Secrets 配好
`PAGES_DEPLOY_TOKEN`：对 `clawbobot/clawbobot.github.io` 有 Contents 读写权限的
fine-grained PAT（只勾选该仓库）。注意 debug 包签名固定（`app/debug.keystore` 已入库），
OTA 覆盖安装不会报签名冲突。

## 已知待验证（真机清单）

- [ ] 高德 `amap://route?...&t=1` 地铁/公交规划 deep link 是否可用（高德有公开文档，优先级最高）
- [ ] 滴滴 `com.sdu.didi.psnger` 包名与叫车页 deep link
- [ ] 航旅纵横 `com.umetrip.android.msky` 包名与航班号查询 deep link
- [ ] 12306 `com.MobileTicket` 包名（无公开 deep link，走 App 主页兜底）
- [ ] 携程 `ctrip.android.view` 包名（租车/酒店暂走 App 主页兜底）
- [ ] 支付宝 / 微信 / 美团 / 饿了么 / 大众点评 / 京东 / 淘宝 / 拼多多 / 飞书包名
- [ ] 银行 / 日历 / 邮箱为占位项，需接入具体 App 包名
- [ ] 通知监听在各厂商 ROM（小米/华为/OPPO/vivo）上的存活与自启动
- [ ] UsageStats 在未授权时返回空列表，兜底逻辑已覆盖

## 视觉规范

浅色主题，背景 `#F4F2EE` 暖灰，白卡片 + 柔和阴影，圆角 20dp，
深色中文排版，sparkle 金（`#C9A227`）点缀。定义在 `ui/theme/Theme.kt`
（`AILauncherColors` / `AILauncherTheme`），后续原型图与实现都按此执行。
