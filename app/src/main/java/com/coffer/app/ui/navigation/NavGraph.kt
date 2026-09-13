package com.coffer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coffer.app.ui.activity.ActivityScreen
import com.coffer.app.ui.contactdetail.ContactDetailScreen
import com.coffer.app.ui.contacts.ContactsScreen
import com.coffer.app.ui.dashboard.DashboardScreen
import com.coffer.app.ui.neworder.NewOrderScreen
import com.coffer.app.ui.orderdetail.OrderDetailScreen
import com.coffer.app.ui.products.ProductsScreen
import com.coffer.app.ui.settings.SettingsScreen

@Composable
fun CofferNavGraph() {
    val navController = rememberNavController()

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigate = { route -> navigateToTab(route) },
                onOpenOrder = { orderId -> navController.navigate(Routes.orderDetail(orderId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onNavigate = { route -> navigateToTab(route) },
                onOpenContact = { contactId -> navController.navigate(Routes.contactDetail(contactId)) }
            )
        }
        composable(Routes.PRODUCTS) {
            ProductsScreen(onNavigate = { route -> navigateToTab(route) })
        }
        composable(Routes.ACTIVITY) {
            ActivityScreen(
                onNavigate = { route -> navigateToTab(route) },
                onOpenOrder = { orderId -> navController.navigate(Routes.orderDetail(orderId)) }
            )
        }
        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument("contactId") { type = NavType.IntType })
        ) {
            ContactDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { orderId -> navController.navigate(Routes.orderDetail(orderId)) },
                onNewOrder = { contactId -> navController.navigate(Routes.newOrder(contactId)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType })
        ) {
            OrderDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.NEW_ORDER,
            arguments = listOf(navArgument("contactId") { type = NavType.IntType; defaultValue = -1 })
        ) {
            NewOrderScreen(
                onBack = { navController.popBackStack() },
                onSaved = { contactId -> onOrderSaved(navController, contactId) }
            )
        }
    }
}

private fun onOrderSaved(navController: NavHostController, contactId: Int) {
    navController.popBackStack()
    navController.navigate(Routes.contactDetail(contactId)) { launchSingleTop = true }
}
