package com.marketcredits.app.ui.myprofile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.ItemStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    onBack: () -> Unit,
    viewModel: MyProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(uiState.user?.name ?: "My profile") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Balance", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${uiState.user?.creditBalance ?: 0} credits",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { Text("My listings", style = MaterialTheme.typography.titleMedium) }
            if (uiState.myListings.isEmpty()) {
                item { Text("You haven't listed anything yet.") }
            } else {
                items(uiState.myListings, key = { "listing-${it.id}" }) { listing -> ListingRow(listing) }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Transaction history", style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.history.isEmpty()) {
                item { Text("No transactions yet.") }
            } else {
                items(uiState.history, key = { "tx-${it.transaction.id}" }) { entry -> HistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun ListingRow(listing: ItemEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(listing.title, fontWeight = FontWeight.Bold)
                val statusLabel = if (listing.status == ItemStatus.SOLD.name) "Sold" else "Available"
                Text(statusLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text("${listing.price} credits")
        }
    }
}

@Composable
private fun HistoryRow(entry: TransactionDisplay) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(entry.transaction.itemTitle, fontWeight = FontWeight.Bold)
                val label = if (entry.wasSale) "Sold to ${entry.counterpartyName}" else "Bought from ${entry.counterpartyName}"
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (entry.wasSale) "+${entry.transaction.amount}" else "-${entry.transaction.amount}")
        }
    }
}
