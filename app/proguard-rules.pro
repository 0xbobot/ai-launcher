# AI Launcher ProGuard 规则（v0.54.0 收紧：排除 Material2）

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

# Compose：按包精确保留，排除 Material2（androidx.compose.material）
# icons 子包保留（17 个图标），父包 Material2 组件剥离
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.animation.** { *; }
-dontwarn androidx.compose.**
