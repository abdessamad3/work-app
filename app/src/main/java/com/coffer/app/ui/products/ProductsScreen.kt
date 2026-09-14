package com.coffer.app.ui.products

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.BarcodeScannerDialog
import com.coffer.app.ui.components.CofferBottomBar
import com.coffer.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onNavigate: (String) -> Unit,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ProductRow?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Products") }) },
        bottomBar = { CofferBottomBar(currentRoute = Routes.PRODUCTS, onNavigate = onNavigate) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New product")
            }
        }
    ) { padding ->
        if (uiState.rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No products yet — tap + to add one, or create one while itemizing an order.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.rows, key = { it.product.id }) { row ->
                    ProductRowCard(row = row, onClick = { editTarget = row })
                }
            }
        }
    }

    if (showAddDialog) {
        ProductDialog(
            title = "New product",
            initialName = "",
            initialBuyPrice = "",
            initialSellPrice = "",
            initialBarcode = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, buyCents, sellCents, barcode ->
                viewModel.createProduct(name, buyCents, sellCents, barcode)
                showAddDialog = false
            },
            onDelete = null
        )
    }

    editTarget?.let { row ->
        ProductDialog(
            title = "Edit product",
            initialName = row.product.name,
            initialBuyPrice = String.format("%.2f", row.product.buyPriceCents / 100.0),
            initialSellPrice = String.format("%.2f", row.product.sellPriceCents / 100.0),
            initialBarcode = row.product.barcode ?: "",
            deleteBlockedMessage = if (!row.canDelete) "This product is used in an order and can't be deleted." else null,
            onDismiss = { editTarget = null },
            onSave = { name, buyCents, sellCents, barcode ->
                viewModel.updateProduct(row.product, name, buyCents, sellCents, barcode)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteProduct(row)
                editTarget = null
            }
        )
    }
}

@Composable
private fun ProductRowCard(row: ProductRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(row.product.name, fontWeight = FontWeight.Bold)
                Text(
                    "Buy ${formatCents(row.product.buyPriceCents)} · Sell ${formatCents(row.product.sellPriceCents)}",
                    style = MaterialTheme.typography.bodySmall
                )
                row.product.barcode?.let { barcode ->
                    Text(barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${row.stockQuantity} in stock",
                    fontWeight = FontWeight.Bold,
                    color = if (row.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ProductDialog(
    title: String,
    initialName: String,
    initialBuyPrice: String,
    initialSellPrice: String,
    initialBarcode: String,
    deleteBlockedMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialName) }
    var buyPrice by remember { mutableStateOf(initialBuyPrice) }
    var sellPrice by remember { mutableStateOf(initialSellPrice) }
    var barcode by remember { mutableStateOf(initialBarcode) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = buyPrice, onValueChange = { buyPrice = it }, label = { Text("Buy price (from supplier)") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sellPrice, onValueChange = { sellPrice = it }, label = { Text("Sell price (to client)") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val buyValue = buyPrice.toDoubleOrNull()
                val sellValue = sellPrice.toDoubleOrNull()
                if (name.isBlank() || buyValue == null || buyValue <= 0 || sellValue == null || sellValue <= 0) {
                    error = "Enter a name and buy/sell prices greater than 0."
                } else {
                    onSave(name.trim(), Math.round(buyValue * 100), Math.round(sellValue * 100), barcode.trim().ifBlank { null })
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (deleteBlockedMessage == null) onDelete() else error = deleteBlockedMessage }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { value ->
                barcode = value
                showScanner = false
            }
        )
    }
}
