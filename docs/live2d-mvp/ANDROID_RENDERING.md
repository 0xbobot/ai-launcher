# Live2D Android 渲染链路验证（live2d-mvp）

> 目标：**先用官方示例模型**，验证"SDK 集成 → Compose 嵌入 → 真机渲染"的技术链路，
> 与七仔专属模型的制作解耦。模型一到，换 `assets` 即可。

## 1. 集成了什么

| 部分 | 说明 |
|---|---|
| SDK 版本 | Cubism SDK for Java **5-r.5**（与官方 `CubismJavaSamples` 同步，2026-06 发布） |
| Framework | Live2D Open Software License（GitHub 开源）。**不复制进仓库**，`:live2d` 模块用 `srcDirs` 直接引用 `third_party/live2d/sdk/Framework` 源码，官方为唯一可信源 |
| Core | `Live2DCubismCore.aar`（Proprietary License）。**不进 git**，见 §4 |
| Demo 模型 | **Hiyori**（官方示例，Free Material License），`live2d/src/main/assets/Hiyori/` |
| Demo 逻辑 | 官方示例 LApp 全家桶（12 个文件，仅改包名）：`LAppDelegate` / `LAppView` / `LAppLive2DManager` / `LAppModel` / `GLRenderer` …，包 `com.bobot.ailauncher.live2d.demo` |
| Compose 嵌入 | `Live2DDemoActivity`（**debug only**）：`AndroidView` 包 `GLSurfaceView`，即架构文档里的"宿主 A（首页）"路线 |

### 模块与条件编译（关键设计）

```
settings.gradle.kts
  └─ 仅当 third_party/live2d/sdk/Core/android/Live2DCubismCore.aar 存在时
     才 include(":live2d")；否则自动跳过，CI 和没 SDK 的机器不受影响。

:live2d（android library，新增文件，无业务耦合）
  ├─ srcDirs → SDK Framework 源码 + shader assets（运行时动态加载，见"坑 2"）
  ├─ compileOnly(Core AAR)
  └─ assets/Hiyori（示例模型）

:app（仅加法修改）
  ├─ SDK 就位时：debugImplementation(:live2d) + debugImplementation(Core AAR)
  └─ app/src/debug/：Live2DDemoActivity + debug manifest（不碰主 manifest）
```

## 2. Demo 怎么跑

```bash
# 0. 先按 third_party/live2d/README.md 把官方 SDK 放好（Core AAR 是硬性前提）
# 1. 构建 debug 包（SDK 就位后 :live2d 自动纳入）
gradle :app:assembleDebug
# 2. 安装到真机，启动 demo
adb shell am start -n com.bobot.ailauncher/com.bobot.ailauncher.debug.Live2DDemoActivity
```

**验收标准**：
- Hiyori 在真机上渲染出来，无破面/黑块；
- 待机动作可见：呼吸、眨眼；
- 点按身体 → 切换随机动作；拖拽 → 脸部跟随；
- `adb logcat | grep -i cubism` 无 Core 报错。

## 3. 已知坑

1. **Core 必须从官网下**：`CubismJavaSamples` 仓库不含 Core；Framework 声明 `compileOnly`，
   App 侧必须 `implementation` 同一份 AAR，否则编译过、运行时 `NoClassDefFoundError`。
2. **Shader assets 必须打包**：Framework 的 GLSL 在
   `Framework/framework/src/main/assets/com/live2d/sdk/cubism/framework/shaders/`，
   运行时按文件名动态加载。`:live2d` 的 `assets.srcDirs` 已指向它；
   如果只引 java 源码不引 assets，编译全过、真机黑屏——这是最隐蔽的坑。
3. **官方示例要求 compileSdk 36 / JDK 17 toolchain**：本仓库是 34 / JDK 17，
   所以没直接 include 官方模块，而是自建 adapter（`:live2d`），只取它的源码与 assets。
4. **GLSurfaceView 与 Compose**：`AndroidView` 嵌 GLSurfaceView 是官方示例同款模式；
   浮窗二期要走 TextureView（render-to-texture），见架构文档 §3.2/§4，**本次不做**。
5. **模型自动发现**：`LAppLive2DManager.setUpModel()` 会爬 `assets` 根目录找
   `<目录名>.model3.json`；换七仔模型时把 `Hiyori/` 替换成 `Qizai/` 即可，无需改代码。
6. **ABI**：Core AAR 含 arm64-v8a（及 x86/x86_64 模拟器）；真机 arm64 为主。

## 4. Core 的获取与 License

- 下载：https://www.live2d.com/en/download/cubism-sdk/download-java/
  （阅读并同意许可协议后下载，下载即视为同意）。
- 落盘：解压到 `third_party/live2d/sdk/`，详见 `third_party/live2d/README.md`。
- **不要提交进 git**（`.gitignore` 已排除 `/third_party/live2d/sdk/`），不要单独再分发。
- Framework：Live2D Open Software License（开源）。
- Core：Live2D Proprietary Software License；个人与小规模（年营收 < 1000 万日元）免费。
  若未来进入沃尔玛商用，需与 Live2D 另谈授权。
- Hiyori 示例模型：Live2D Free Material License；小规模可用，需保留版权声明；
  七仔模型就绪后删除。

## 5. 当前验证状态（诚实版，2026-10-04）

- ✅ 代码：12 个 demo 文件对照官方 5-r.5 源码逐文件改写，import 全量交叉检查无悬空；
  Gradle 条件逻辑、Compose 嵌入代码已就绪。
- ⏳ **编译验证：待 Core AAR 就位**。live2d.com 下载页是 JS 同意流程且有反爬，
  本次未能自动获取 Core 二进制。SDK 放好后，`:app:assembleDebug` 即完成编译验证。
- ✅ CI 安全：无 SDK 时 `:live2d` 自动跳过，现有 `build-apk.yml` 不受影响。
- ⏳ **真机渲染验证：待真机 + Core**。验收标准见 §2。
