package com.coffer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
