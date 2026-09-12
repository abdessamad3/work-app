package com.coffer.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.ActivityEntryRow
import com.coffer.app.ui.components.CofferBottomBar
import com.coffer.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    onOpenOrder: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Coffer")
                    Text(formatCents(uiState.cashBalanceCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            })
        },
        bottomBar = { CofferBottomBar(currentRoute = Routes.DASHBOARD, onNavigate = onNavigate) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.newOrder()) }) {
                Icon(Icons.Default.Add, contentDescription = "New order")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(label = "You owe suppliers", value = formatCents(uiState.owedToSuppliersCents))
                    StatCard(label = "Clients owe you", value = formatCents(uiState.owedByClientsCents))
                }
            }
            item { Text("Recent activity", style = MaterialTheme.typography.titleMedium) }
            if (uiState.recentActivity.isEmpty()) {
                item {
                    Text(
                        "Nothing yet — tap + to log your first order.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(uiState.recentActivity, key = { it.timestamp.toString() + it.orderId }) { entry ->
                    ActivityEntryRow(entry, onClick = { onOpenOrder(entry.orderId) })
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
