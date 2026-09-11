package com.marketcredits.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marketcredits.app.ui.itemdetail.ItemDetailScreen
import com.marketcredits.app.ui.marketplace.MarketplaceScreen
import com.marketcredits.app.ui.myprofile.MyProfileScreen
import com.marketcredits.app.ui.profile.ProfileSwitcherScreen
import com.marketcredits.app.ui.sell.SellItemScreen

@Composable
fun MarketCreditsNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.PROFILE_SWITCHER) {
        composable(Routes.PROFILE_SWITCHER) {
            ProfileSwitcherScreen(
                onProfileSelected = {
                    navController.navigate(Routes.MARKETPLACE) {
                        popUpTo(Routes.PROFILE_SWITCHER) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.MARKETPLACE) {
            MarketplaceScreen(
                onItemClick = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                onSellClick = { navController.navigate(Routes.SELL) },
                onMyProfileClick = { navController.navigate(Routes.MY_PROFILE) },
                onSwitchProfileClick = { navController.navigate(Routes.PROFILE_SWITCHER) }
            )
        }
        composable(
            route = Routes.ITEM_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.IntType })
        ) {
            ItemDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SELL) {
            SellItemScreen(onItemCreated = { navController.popBackStack() })
        }
        composable(Routes.MY_PROFILE) {
            MyProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
