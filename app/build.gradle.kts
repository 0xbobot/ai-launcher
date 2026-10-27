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
        versionCode = 379
        versionName = "0.68.171"
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

// R2：单测结果打到控制台日志，CI 里可直接核对每个用例的通过情况
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
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
    // pet-hybrid-float：视频表情高光（ExoPlayer + PlayerView，经 AndroidView 接入 Compose）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.squareup.okhttp)
    // v0.68.89：API Key 加密存储（EncryptedSharedPreferences）。
    // 并行线 B 起 Key 改存 Android Keystore AES/GCM 直连实现（零三方依赖），
    // 本依赖仅保留供一次性迁移读取 alpha06 旧格式数据；迁移随版本铺满后的
    // 下一批连同 LlmConfig.securePrefs 一起移除（本批不许删，删了旧 Key 读不出）。
    implementation(libs.androidx.security.crypto)
    // R2：IntentCoordinator 纯逻辑单测（JVM，不走 Android 运行时）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 体检批D ⑥：JVM 单测里解析 update.json 需要真 org.json 实现
    // （android.jar 的 org.json 是桩）；仅测试 classpath，不进 APK
    testImplementation(libs.org.json)
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
