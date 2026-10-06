package com.bobot.ailauncher.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * v0.41.9：生物识别（面部/指纹）——隐藏应用入口的身份验证。
 * 通过 → 显示已隐藏应用；不通过 → 保持隐藏。
 */
object BiometricAuth {
    /** 设备是否可用生物识别（强生物识别） */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 弹系统生物识别框。必须在 FragmentActivity（MainActivity 是 ComponentActivity）中调用。
     * onSuccess：认证通过；onFail：失败/取消/出错（调用方保持隐藏状态）。
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // 单次识别失败（手指没放好等），不关闭，允许重试；只在用户取消/出错时回调
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFail()
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("查看已隐藏应用")
            .setSubtitle("请验证身份")
            .setNegativeButtonText("取消")
            .build()
        prompt.authenticate(promptInfo)
    }
}
