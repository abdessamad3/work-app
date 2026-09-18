package com.coffer.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.coffer.app.domain.LocalCurrency
import com.coffer.app.ui.crash.CrashScreen
import com.coffer.app.ui.navigation.CofferNavGraph
import com.coffer.app.ui.onboarding.OnboardingScreen
import com.coffer.app.ui.onboarding.OnboardingViewModel
import com.coffer.app.ui.settings.SettingsViewModel
import com.coffer.app.ui.theme.CofferTheme
import com.coffer.app.work.OVERDUE_WORK_NAME
import com.coffer.app.work.OverdueCheckWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CofferTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val context = LocalContext.current
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "$OVERDUE_WORK_NAME-immediate",
                            ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequestBuilder<OverdueCheckWorker>().build()
                        )
                    }

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
                            true -> {
                                val settingsViewModel: SettingsViewModel = hiltViewModel()
                                val currency by settingsViewModel.currency.collectAsState()
                                CompositionLocalProvider(LocalCurrency provides currency) {
                                    CofferNavGraph()
                                }
                            }
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
