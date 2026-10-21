# AI桌面（AI Launcher）

AI 时代的 Android 启动桌面：不是图标货架，而是 **"意图入口 + 正在进行时"** 的系统级 AI 空间。
吉祥物是宠物「七仔」（毛绒 3D 小流星），它负责讲述信息、承接意图、给出下一步动作。

当前版本见 [CHANGELOG.md](CHANGELOG.md)（版本档案，v0.68.84 起）与线上
`update.json`；架构与专题文档在 `docs/`（`TECHNICAL_DESIGN.md`、
`INTERACTION_GUIDELINES.md`、`PET_WORLD_ARCHITECTURE.md`、`live2d-mvp/`）。

> **分发方式：仅限侧载。** 本应用使用较重的系统权限（见下「安装与分发」），
> 不以 Google Play 上架为目标，只通过 GitHub Releases + OTA 直分发。

## 现在的功能

- **首页**：每日封面（3 模板轮换）+ 时钟/天气/一句话 + 七仔 + 意图胶囊 + TODAY 信息流
  （下一会议、待回消息、待办等，每条信息都带用户最可能的下一步动作）。
- **意图入口**：点开胶囊先出「此刻常用 Top + 下一会议」（零打字第一屏）；
  打字时本地搜索秒出应用直达（名称/拼音/首字母 + 使用频次排序）；
  整句提交走大模型规划——模型带情境（时间、下一场日程、天气、常用与已装应用），
  选能力执行；缺必填参数时七仔追问补参，不缺参假执行；高风险动作先弹窗确认。
  首页胶囊与应用中心搜索是同一个入口的两层（应用中心顶部同款胶囊 + 「问七仔」）。
- **能力注册表**：`app/src/main/assets/capabilities.json` 定义能力（见下），
  运行时按本机**实际已装应用**动态绑定提供方；没装的能力不向模型许诺；
  装/卸应用后自动刷新。深链模板参数未填全不会被打开。
- **应用中心**：全部应用 A–Z 字母索引 + 搜索；Dock 与扇形菜单。
- **外观**：浅色暖纸面主题 + 深色模式（跟随系统），字阶/颜色 token 在 `ui/theme/`。
- **OTA 升级**：App 内检查更新、分片下载、断点续传、完整性校验后弹安装（见下）。

## 技术栈

Kotlin 2.0.20 + Jetpack Compose (BOM 2024.10.01) + Material3 + navigation-compose，
AGP 8.5.2，minSdk 29 / targetSdk 34，Gradle Kotlin DSL。

## 冷启动流程（第一印象）

首次打开走引导（`ui/onboarding/OnboardingScreen.kt`），每步都可跳过：
欢迎 → 设为默认桌面（`Settings.ACTION_HOME_SETTINGS`，实时检测）→
通知读取授权（「正在进行时」的数据来源）→ 写
`SharedPreferences("ai_launcher").onboarded=true` 进入主界面。

## 能力注册表（Capability Registry）

`app/src/main/assets/capabilities.json` 定义全部分组与能力，
`data/CapabilityRegistry.kt` 负责加载、解析与跳转。单个能力示意（导航）：

```json
{
  "id": "daohang",
  "label": "导航",
  "packageNames": ["com.autonavi.minimap", "com.baidu.BaiduMap", "com.tencent.map"],
  "providers": [
    {"name": "高德地图", "packageNames": ["com.autonavi.minimap"],
     "deeplink": "amap://route?sourceApplication=ai-launcher&dname={to}&dev=0&t=1"},
    {"name": "百度地图", "packageNames": ["com.baidu.BaiduMap"],
     "deeplink": "baidumap://map/direction?destination={to}&mode=driving&src=ai-launcher"},
    {"name": "腾讯地图", "packageNames": ["com.tencent.map"], "deeplink": null}
  ],
  "geoFallback": "name",
  "fallbackToLauncher": true,
  "params": [{"name": "to", "label": "目的地", "required": true, "ask": "导航去哪儿？"}]
}
```

解析策略：按 `providers` 候选链顺序，选第一个本机已安装的提供方拼深链直达；
深链打不开换下一家，再不行走 geo 兜底 / 打开应用主页；全部落空如实报错。
`params` 声明必填参数与缺参追问句，协调器在执行前校验，缺参先追问、不执行。

## CI 出包

`.github/workflows/build-apk.yml`：push / PR 自动跑
`gradle :app:assembleDebug` + 单元测试，产物 `app-debug.apk` 以 artifact
`ai-launcher-debug-apk` 上传。仓库不提交 gradle wrapper，CI 用
`gradle/actions/setup-gradle` 提供 Gradle。另有冒烟（`smoke-test.yml`，
GitHub Actions 模拟器）与五配置回归矩阵（`smoke-matrix.yml`，发版前手动跑）。

## OTA 自动升级

- manifest：`https://bobot.is-a.dev/ai-launcher/update.json`（GitHub Pages 只托管
  这一个文件），字段含 `versionCode / versionName / apkUrl / sha256 / changelog /
  forceUpdate / prerelease`；`sha256` 是发布时对最终 APK 计算的摘要
  （v0.68.89 起），App 下载后校验，不符不安装；无此字段的旧 manifest 自动跳过该项。
- 安装包：GitHub Releases 直链（`releases/download/v<版本>/ai-launcher-v<版本>.apk`）。
- App 侧（`data/OtaUpdater.kt` + `data/OtaDownloader.kt` + `data/ChunkedDownloader.kt`）：
  启动每天最多自动检查一次，设置页可手动触发，测试版（prerelease）只推给开启
  「接收测试版」开关的用户；下载为 App 内 OkHttp **分片并行 + 片级断点续传**
  （服务端不支持 Range 时自动退回单流），进度带实时速度；拼装后依次校验
  长度 → APK 可解析 → 包内 versionCode 与 manifest 一致 → SHA-256（有字段时），
  全部通过才标记完成并弹安装。
- 发布流程：手动触发 `Publish OTA` workflow（填 changelog，应与 `CHANGELOG.md`
  顶部条目一致），它取最近一次构建成功的 APK，从 APK 本体解析版本号，
  生成 update.json 推到 `clawbobot.github.io` 仓库的 `ai-launcher/` 目录，
  APK 上传到 GitHub Releases。需在仓库 Settings > Secrets 配
  `PAGES_DEPLOY_TOKEN`（对该 Pages 仓库 Contents 读写权限的 fine-grained PAT）。
- 签名：debug 包签名固定（`app/debug.keystore` 已入库），OTA 覆盖安装不报签名冲突；
  完整性由 SHA-256 + 托管账号安全兜底（换私有签名的影响见内部评估，未执行）。

## 安装与分发（仅限侧载）

从 GitHub Releases 下载 APK 侧载安装。主要权限（`AndroidManifest.xml`）：
INTERNET、QUERY_ALL_PACKAGES、PACKAGE_USAGE_STATS、READ_CALENDAR、
POST_NOTIFICATIONS、SYSTEM_ALERT_WINDOW、USE_FULL_SCREEN_INTENT、
FOREGROUND_SERVICE、REQUEST_INSTALL_PACKAGES、VIBRATE。
因权限面较重，本应用**仅限侧载分发，不上架 Google Play**。

## Live2D SDK（仅 demo，未进生产）

Live2D 只用于七仔渲染验证 demo（debug 接线），**生产宠物走静态 3D 渲染资源**。
SDK 是专有许可，**不随仓库分发**，需要时自行获取：

1. 从 Live2D 官网下载 **Cubism SDK**（本仓库验证基线：Cubism SDK for Java **5-r.5**，
   与官方 `CubismJavaSamples` 同步；原生示例流程见 `docs/live2d-mvp/RUNBOOK.md`）。
2. 把 `Live2DCubismCore.aar` 放到 `third_party/live2d/sdk/Core/android/`。
3. 仅当该 AAR 存在时，构建才纳入 `:live2d` 模块与 `live2dDebug` 源码
   （`settings.gradle.kts` / `app/build.gradle.kts` 条件接线）；不存在时
   正常构建、CI 与本机产物在 Live2D 部分的差异是显式设计。
   细节见 `docs/live2d-mvp/ANDROID_RENDERING.md`。

## 已知待验证（真机清单）

- [ ] 三家地图深链真机实开（高德 route / 百度 direction / 腾讯走 geo 兜底或打开应用）
- [ ] 26 个能力的包名抽验（纯打开型能力，如滴滴、航旅纵横、12306、携程等）
- [ ] 意图追问态、TODAY 无障碍（TalkBack）在真机的最终验收
- [ ] 通知监听在各厂商 ROM（小米/华为/OPPO/vivo）上的存活与自启动

## 视觉规范

浅色主题以暖纸面为基调（背景 `#F4F2EE` 系）、白卡片 + 柔和阴影、圆角 20dp、
sparkle 金（`#C9A227`）点缀，并提供深色模式。定义在 `ui/theme/Theme.kt`
（`AILauncherColors` / `AILauncherTheme`）与 `ui/theme/Type.kt` 字阶 token，
原型图与实现都按此执行。
