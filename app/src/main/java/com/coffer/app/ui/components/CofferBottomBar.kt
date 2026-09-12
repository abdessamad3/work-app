package com.coffer.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.coffer.app.ui.navigation.Routes

@Composable
fun CofferBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.DASHBOARD,
            onClick = { onNavigate(Routes.DASHBOARD) },
            icon = { Icon(Icons.Default.Wallet, contentDescription = null) },
            label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.CONTACTS,
            onClick = { onNavigate(Routes.CONTACTS) },
            icon = { Icon(Icons.Default.People, contentDescription = null) },
            label = { Text("Contacts") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ACTIVITY,
            onClick = { onNavigate(Routes.ACTIVITY) },
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            label = { Text("Activity") }
        )
    }
}
