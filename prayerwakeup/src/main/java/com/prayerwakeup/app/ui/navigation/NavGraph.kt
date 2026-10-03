package com.prayerwakeup.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prayerwakeup.app.ui.home.HomeScreen
import com.prayerwakeup.app.ui.qibla.QiblaScreen
import com.prayerwakeup.app.ui.settings.SettingsScreen
import com.prayerwakeup.app.ui.stats.StatisticsScreen
import com.prayerwakeup.app.ui.voice.RecordVoiceScreen

@Composable
fun PrayerWakeupNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                onOpenQibla = { navController.navigate(Routes.QIBLA) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRecordVoice = { navController.navigate(Routes.RECORD_VOICE) }
            )
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QIBLA) {
            QiblaScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECORD_VOICE) {
            RecordVoiceScreen(onBack = { navController.popBackStack() })
        }
    }
}
