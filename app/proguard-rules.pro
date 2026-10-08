# AI Launcher ProGuard 规则（v0.52.0 瘦身启用混淆）

# 保持主 Activity 和 Application
-keep class com.bobot.ailauncher.MainActivity { *; }
-keep class com.bobot.ailauncher.** { *; }

# Compose Navigation
-keep class androidx.navigation.** { *; }

# Biometric（v0.41.9 隐藏应用入口生物识别）
-keep class androidx.biometric.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Compose 运行时
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
