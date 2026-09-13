package com.coffer.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.ui.crash.CrashScreen
import com.coffer.app.ui.navigation.CofferNavGraph
import com.coffer.app.ui.onboarding.OnboardingScreen
import com.coffer.app.ui.onboarding.OnboardingViewModel
import com.coffer.app.ui.theme.CofferTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CofferTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var crashStackTrace by remember { mutableStateOf(readLastCrash()) }

                    if (crashStackTrace != null) {
                        CrashScreen(
                            stackTrace = crashStackTrace.orEmpty(),
                            onDismiss = {
                                clearLastCrash()
                                crashStackTrace = null
                            }
                        )
                    } else {
                        val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                        val hasSeenOnboarding by onboardingViewModel.hasSeenOnboarding.collectAsState()

                        when (hasSeenOnboarding) {
                            null -> Unit
                            false -> OnboardingScreen(onGetStarted = onboardingViewModel::markSeen)
                            true -> CofferNavGraph()
                        }
                    }
                }
            }
        }
    }

    private fun readLastCrash(): String? =
        getSharedPreferences(CofferApp.CRASH_PREFS, Context.MODE_PRIVATE).getString(CofferApp.CRASH_KEY, null)

    private fun clearLastCrash() {
        getSharedPreferences(CofferApp.CRASH_PREFS, Context.MODE_PRIVATE).edit().remove(CofferApp.CRASH_KEY).apply()
    }
}
