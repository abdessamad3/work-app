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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
            initialPrice = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, priceCents ->
                viewModel.createProduct(name, priceCents)
                showAddDialog = false
            },
            onDelete = null
        )
    }

    editTarget?.let { row ->
        ProductDialog(
            title = "Edit product",
            initialName = row.product.name,
            initialPrice = String.format("%.2f", row.product.defaultUnitPriceCents / 100.0),
            deleteBlockedMessage = if (!row.canDelete) "This product is used in an order and can't be deleted." else null,
            onDismiss = { editTarget = null },
            onSave = { name, priceCents ->
                viewModel.updateProduct(row.product, name, priceCents)
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
                Text(formatCents(row.product.defaultUnitPriceCents) + " default", style = MaterialTheme.typography.bodySmall)
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
    initialPrice: String,
    deleteBlockedMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Default price") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val priceValue = price.toDoubleOrNull()
                if (name.isBlank() || priceValue == null || priceValue <= 0) {
                    error = "Enter a name and a price greater than 0."
                } else {
                    onSave(name.trim(), Math.round(priceValue * 100))
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
}
