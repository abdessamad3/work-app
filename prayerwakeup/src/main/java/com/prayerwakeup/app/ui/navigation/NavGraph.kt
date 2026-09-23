package com.prayerwakeup.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prayerwakeup.app.ui.home.HomeScreen
import com.prayerwakeup.app.ui.settings.SettingsScreen
import com.prayerwakeup.app.ui.stats.StatisticsScreen

@Composable
fun PrayerWakeupNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStatistics = { navController.navigate(Routes.STATISTICS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATISTICS) {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
