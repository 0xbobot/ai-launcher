pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ai-launcher"
include(":app")

// Live2D（七仔小世界渲染验证，live2d-mvp 分支）：官方 SDK 需手动下载到
// third_party/live2d/sdk（见 docs/live2d-mvp/ANDROID_RENDERING.md）。
// SDK 不存在时自动跳过，保证没 SDK 的机器（CI）也能正常构建。
// 注意：判据是 Core AAR 是否存在——它是编译 :live2d 的硬性前提。
if (file("third_party/live2d/sdk/Core/android/Live2DCubismCore.aar").exists()) {
    include(":live2d")
    project(":live2d").projectDir = file("live2d")
}
