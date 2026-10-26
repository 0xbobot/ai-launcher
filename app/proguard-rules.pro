# AI Launcher ProGuard 规则（v0.68.154 瘦身：收紧 keep，R8 按引用剥离）
#
# 收紧依据（2026-10-27 全仓审计，基底 13d466ec）：
# - 应用代码无 Class.forName / ServiceLoader / getIdentifier / kotlin-reflect / 序列化；
# - 导航路由为字符串字面量直连 composable（"home"/"settings"），无 typed route；
# - 无 ViewModel / navArgument / rememberSaveable 自定义类型；应用无布局 XML、无 XML 自定义 View；
# - manifest 组件（LauncherApp / MainActivity / PanelAccessibilityService /
#   LauncherNotificationService / BatteryOverlayService / BatteryGuardActivity /
#   FileProvider）由 R8 按 manifest 自动 keep；
# - 各库自带 consumer 规则继续生效（Compose / Navigation / Biometric / OkHttp /
#   Okio / Tink），本文件不再整包 keep；
# - EncryptedSharedPreferences 迁移读路径仍在（LlmRouter.kt:110 调 securePrefs），
#   Tink 经直接引用可达，不受本次收紧影响（security-crypto 依赖本身不动）。
#
# 保持主 Activity（显式；manifest 也会自动 keep）
-keep class com.bobot.ailauncher.MainActivity { *; }

# Compose：icons 由 R8 按引用自动保留（v0.55 做法），不整包 keep
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$* { *; }
-dontwarn androidx.compose.**

# EncryptedSharedPreferences / Tink（v0.68.89 API Key 加密存储）：
# Tink 类引用了不在 classpath 的 errorprone 注解（仅编译期注解），R8 忽略即可
-dontwarn com.google.errorprone.annotations.**
