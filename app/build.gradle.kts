plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bobot.ailauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bobot.ailauncher"
        minSdk = 29
        targetSdk = 34
        versionCode = 172
        versionName = "0.64.1"
    }

    // 固定 debug 签名：所有 CI 构建共用 app/debug.keystore（个人实验项目，
    // 保证各版本签名一致，手机上可直接覆盖安装；正式发布需换正式签名）
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // v0.53.0 瘦身验证：debug 上开 R8 测试（lint 已修），确认能过再切 release
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            // v0.53.0 瘦身重试：lint 已修，验证 R8 是否能过（资源压缩暂关）
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Live2D demo 的 debug Activity 引用 :live2d 模块；该模块只在 SDK（Core AAR）
    // 存在时才被 include（见 settings.gradle.kts）。把文件放在独立 source root，
    // SDK 不存在时（CI）不加入编译，保证无 SDK 机器也能正常构建；
    // 有 SDK 的机器（VPS）自动编入。debug manifest 声明不受影响（manifest
    // merger 不校验类是否存在）。
    sourceSets {
        getByName("debug") {
            if (file("../third_party/live2d/sdk/Core/android/Live2DCubismCore.aar").exists()) {
                java.srcDir("src/live2dDebug/java")
            }
        }
    }
}

dependencies {
    val bom = libs.androidx.compose.bom
    implementation(platform(bom))
    androidTestImplementation(platform(bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // lint 误报 Fragment 版本（MainActivity 通知权限）：显式声明压住
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.squareup.okhttp)
    // v0.41.9：隐藏应用入口的生物识别（面部/指纹）
    implementation("androidx.biometric:biometric:1.1.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Live2D demo（live2d-mvp 分支，debug only）：SDK 就位时才接入。
    // Core AAR 运行时由 app 提供（Framework 侧声明为 compileOnly）。
    if (file("../third_party/live2d/sdk/Core/android/Live2DCubismCore.aar").exists()) {
        debugImplementation(project(":live2d"))
        debugImplementation(files("../third_party/live2d/sdk/Core/android/Live2DCubismCore.aar"))
    }
}
