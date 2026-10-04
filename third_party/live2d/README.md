# third_party/live2d

Live2D 官方 SDK 落盘位置（**不进 git**，已被 `.gitignore` 排除）。

## 需要你手动做的唯一一件事

1. 去 https://www.live2d.com/en/download/cubism-sdk/download-java/
   阅读并同意许可协议，下载 **Cubism SDK for Java**（5-r.5，与本分支 Framework 版本对应）。
2. 解压，把整个 SDK 目录放到这里，变成：

```
third_party/live2d/
  README.md          ← 本文件（会进 git）
  sdk/               ← 解压后的官方 SDK（不进 git）
    Core/
      android/
        Live2DCubismCore.aar   ← 编译 :live2d 的硬性前提
    Framework/
      framework/
        src/main/java/...      ← Framework 源码（open 许可）
        src/main/assets/...    ← GLSL shader（运行时必须打包）
    Samples/                   ← 参考用
```

3. 放好后重新 sync Gradle，`:live2d` 模块会自动纳入构建
   （`settings.gradle.kts` 以 `Core/android/Live2DCubismCore.aar` 是否存在为判据）。

## 许可

- Framework：Live2D Open Software License（开源，可放心用）。
- Core（`Live2DCubismCore.aar`）：Live2D Proprietary Software License。
  个人与小规模（年营收 < 1000 万日元）免费；**不要提交进 git，不要单独再分发**。
  若未来进入沃尔玛商用，需与 Live2D 另谈授权。
- 示例模型 Hiyori（`live2d/src/main/assets/Hiyori`）：Live2D Free Material License
  （官方示例数据），小规模可商用，需保留版权声明；正式用七仔模型后删除。
