plugins {
    id("com.android.library")
}

// 七仔小世界 Live2D 渲染验证模块（live2d-mvp 分支专用）。
// 仅当官方 SDK 就位（third_party/live2d/sdk）时才会被 settings.gradle.kts 纳入构建。

android {
    namespace = "com.bobot.ailauncher.live2d"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    sourceSets {
        getByName("main") {
            val sdkDir = rootProject.file("third_party/live2d/sdk")
            // Framework 源码：Live2D Open Software License（GitHub 开源部分）。
            // 直接引用 SDK 内的源码目录，保持官方为唯一可信源，本仓库不复制。
            java.srcDirs(
                "src/main/java",
                "$sdkDir/Framework/framework/src/main/java",
            )
            // 关键坑：Framework 的 GLSL shader 在 assets 里，运行时按文件名动态加载，
            // 打包时必须带上，否则编译能过、真机渲染黑屏。
            assets.srcDirs(
                "src/main/assets",
                "$sdkDir/Framework/framework/src/main/assets",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Core（Live2D Proprietary Software License，不进 git）：编译期需要；
    // 运行时 AAR 由 :app 以 debugImplementation 形式提供（见 app/build.gradle.kts）。
    compileOnly(files(rootProject.file("third_party/live2d/sdk/Core/android/Live2DCubismCore.aar")))
}
