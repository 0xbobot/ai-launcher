package com.bobot.ailauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.ui.MainScreen
import com.bobot.ailauncher.ui.onboarding.OnboardingNav
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CapabilityRegistry.load(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        setContent {
            AILauncherTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AILauncherColors.Background
                ) {
                    if (onboarded) {
                        MainScreen()
                    } else {
                        OnboardingNav(onFinish = {
                            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                            onboarded = true
                        })
                    }
                }
            }
        }
    }

    companion object {
        private const val PREFS = "ai_launcher"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
