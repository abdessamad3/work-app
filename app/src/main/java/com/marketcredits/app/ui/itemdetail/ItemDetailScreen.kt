package com.marketcredits.app.ui.itemdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marketcredits.app.data.local.entity.ItemStatus
import com.marketcredits.app.data.repository.BuyResult
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.buyResult.collectLatest { result ->
            when (result) {
                BuyResult.Success -> {
                    snackbarHostState.showSnackbar("Purchase complete!")
                    onBack()
                }
                BuyResult.InsufficientCredits -> snackbarHostState.showSnackbar("Not enough credits.")
                BuyResult.ItemAlreadySold -> snackbarHostState.showSnackbar("This item was already sold.")
                BuyResult.CannotBuyOwnItem -> snackbarHostState.showSnackbar("You can't buy your own item.")
                BuyResult.ItemNotFound -> snackbarHostState.showSnackbar("Item not found.")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Item details") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val item = uiState.item
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(text = item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Sold by ${uiState.sellerName}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = item.description, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${item.price} credits",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                when {
                    item.status == ItemStatus.SOLD.name -> Text("This item has already been sold.")
                    uiState.isOwnItem -> Text("This is your own listing.")
                    else -> Button(
                        onClick = { viewModel.buy() },
                        enabled = uiState.canAfford,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.canAfford) "Buy for ${item.price} credits" else "Not enough credits")
                    }
                }
            }
        }
    }
}
