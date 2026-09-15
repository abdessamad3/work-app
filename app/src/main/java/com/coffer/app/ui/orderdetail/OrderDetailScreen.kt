package com.coffer.app.ui.orderdetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.data.local.entity.ProductEntity
import com.coffer.app.domain.OrderStatus
import com.coffer.app.domain.SuggestedPrice
import com.coffer.app.domain.formatCents
import com.coffer.app.domain.formatDate
import com.coffer.app.domain.lineTotalCents
import com.coffer.app.domain.normalizeBarcode
import com.coffer.app.ui.components.BarcodeScannerDialog
import com.coffer.app.ui.components.ProductThumbnail
import com.coffer.app.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()
    val order = uiState.order

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteOrderConfirm by remember { mutableStateOf(false) }
    var showEditOrderDialog by remember { mutableStateOf(false) }
    var paymentDialogTarget by remember { mutableStateOf<PaymentEntity?>(null) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var itemDialogTarget by remember { mutableStateOf<LineItemEntity?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.contact?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (order != null && !order.itemized) {
                            DropdownMenuItem(text = { Text("Edit order") }, onClick = { showMenu = false; showEditOrderDialog = true })
                        }
                        DropdownMenuItem(text = { Text("Delete order") }, onClick = { showMenu = false; showDeleteOrderConfirm = true })
                    }
                }
            )
        }
    ) { padding ->
        if (order == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(formatCents(order.totalAmountCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(order.description ?: "Itemized order", style = MaterialTheme.typography.bodySmall)
                                Text(formatDate(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(uiState.status)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${formatCents(uiState.paidCents)} paid", style = MaterialTheme.typography.bodySmall)
                            Text("${formatCents(uiState.remainingCents)} remaining", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = if (order.totalAmountCents > 0) (uiState.paidCents.toFloat() / order.totalAmountCents.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (order.itemized) {
                item { Text("Items", style = MaterialTheme.typography.titleMedium) }
                items(uiState.items, key = { "item-${it.id}" }) { lineItem ->
                    Card(onClick = { itemDialogTarget = lineItem }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(lineItem.name)
                                val discountSuffix = if (lineItem.discountPercent > 0) " − ${lineItem.discountPercent}%" else ""
                                Text(
                                    "×${lineItem.quantity} @ ${formatCents(lineItem.listUnitPriceCents)}$discountSuffix",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(formatCents(lineItem.lineTotalCents()))
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { showAddItemDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add item")
                    }
                }
            }

            item { Text("Payments", style = MaterialTheme.typography.titleMedium) }
            if (uiState.payments.isEmpty()) {
                item { Text("No payments recorded yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(uiState.payments, key = { "payment-${it.id}" }) { payment ->
                    Card(onClick = { paymentDialogTarget = payment }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(formatCents(payment.amountCents), fontWeight = FontWeight.Bold)
                            payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(formatDate(payment.paidAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                if (uiState.status == OrderStatus.PAID) {
                    Text("Fully paid", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    Button(onClick = { showAddPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add payment")
                    }
                }
            }
        }

        if (showAddPaymentDialog) {
            PaymentDialog(
                title = "Add payment",
                initialAmountCents = uiState.remainingCents,
                initialNote = "",
                onDismiss = { showAddPaymentDialog = false },
                onSave = { amountCents, note ->
                    viewModel.addPayment(amountCents, note)
                    showAddPaymentDialog = false
                },
                onDelete = null
            )
        }

        paymentDialogTarget?.let { payment ->
            PaymentDialog(
                title = "Edit payment",
                initialAmountCents = payment.amountCents,
                initialNote = payment.note ?: "",
                onDismiss = { paymentDialogTarget = null },
                onSave = { amountCents, note ->
                    viewModel.updatePayment(payment, amountCents, note)
                    paymentDialogTarget = null
                },
                onDelete = {
                    viewModel.deletePayment(payment)
                    paymentDialogTarget = null
                }
            )
        }

        val isPurchase = uiState.contact?.type == ContactType.SUPPLIER.name

        if (showAddItemDialog) {
            LineItemDialog(
                title = "Add item",
                products = products,
                initialProductId = null,
                initialQty = "",
                initialListPrice = "",
                initialDiscountPercent = "0",
                isPurchase = isPurchase,
                onCreateProduct = { name, buyCents, sellCents, onCreated -> viewModel.createProduct(name, buyCents, sellCents, onCreated) },
                onSuggestPrice = { productId -> viewModel.suggestedPriceFor(productId) },
                onDismiss = { showAddItemDialog = false },
                onSave = { productId, qty, listPriceCents, discount ->
                    viewModel.addLineItem(productId, qty, listPriceCents, discount)
                    showAddItemDialog = false
                },
                onDelete = null
            )
        }

        itemDialogTarget?.let { item ->
            LineItemDialog(
                title = "Edit item",
                products = products,
                initialProductId = item.productId,
                initialQty = item.quantity.toString(),
                initialListPrice = String.format("%.2f", item.listUnitPriceCents / 100.0),
                initialDiscountPercent = item.discountPercent.toString(),
                isPurchase = isPurchase,
                onCreateProduct = { name, buyCents, sellCents, onCreated -> viewModel.createProduct(name, buyCents, sellCents, onCreated) },
                onSuggestPrice = { productId -> viewModel.suggestedPriceFor(productId) },
                onDismiss = { itemDialogTarget = null },
                onSave = { productId, qty, listPriceCents, discount ->
                    viewModel.updateLineItem(item, productId, qty, listPriceCents, discount)
                    itemDialogTarget = null
                },
                onDelete = {
                    viewModel.deleteLineItem(item)
                    itemDialogTarget = null
                }
            )
        }

        if (showEditOrderDialog) {
            EditOrderDialog(
                initialTotal = order.totalAmountCents,
                initialDescription = order.description ?: "",
                onDismiss = { showEditOrderDialog = false },
                onSave = { totalCents, description ->
                    viewModel.updateFlatOrder(totalCents, description)
                    showEditOrderDialog = false
                }
            )
        }

        if (showDeleteOrderConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteOrderConfirm = false },
                title = { Text("Delete this order?") },
                text = { Text("This removes the order and every payment recorded against it. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteOrder(onDeleted = onBack) }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { showDeleteOrderConfirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun PaymentDialog(
    title: String,
    initialAmountCents: Long,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (Long, String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var amountText by remember { mutableStateOf(String.format("%.2f", initialAmountCents / 100.0)) }
    var note by remember { mutableStateOf(initialNote) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "Enter an amount greater than 0."
                } else {
                    onSave(Math.round(amount * 100), note.trim().ifBlank { null })
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun LineItemDialog(
    title: String,
    products: List<ProductEntity>,
    initialProductId: Int?,
    initialQty: String,
    initialListPrice: String,
    initialDiscountPercent: String,
    isPurchase: Boolean,
    onCreateProduct: (String, Long, Long, (Int) -> Unit) -> Unit,
    onSuggestPrice: (Int) -> SuggestedPrice,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Long, Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var selectedProductId by remember { mutableStateOf(initialProductId) }
    var usingNewProduct by remember { mutableStateOf(false) }
    var newProductName by remember { mutableStateOf("") }
    var newProductBuyPrice by remember { mutableStateOf("") }
    var newProductSellPrice by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(initialQty) }
    var listPrice by remember { mutableStateOf(initialListPrice) }
    var discountPercent by remember { mutableStateOf(initialDiscountPercent) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var scanNotFound by remember { mutableStateOf(false) }

    val selectedProduct = products.find { it.id == selectedProductId }
    val canChangeProduct = initialProductId == null

    fun selectProductById(productId: Int) {
        selectedProductId = productId
        val suggestion = onSuggestPrice(productId)
        listPrice = String.format("%.2f", suggestion.listUnitPriceCents / 100.0)
        discountPercent = suggestion.discountPercent.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (canChangeProduct && selectedProduct == null && !usingNewProduct) {
                    Text("Product", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        products.forEach { product ->
                            FilterChip(
                                selected = false,
                                onClick = { selectProductById(product.id) },
                                label = { Text(product.name) },
                                leadingIcon = if (product.photoPath != null) {
                                    { ProductThumbnail(photoPath = product.photoPath, modifier = Modifier.size(20.dp)) }
                                } else null
                            )
                        }
                        FilterChip(selected = false, onClick = { usingNewProduct = true }, label = { Text("+ New product") })
                        FilterChip(
                            selected = false,
                            onClick = { scanNotFound = false; showScanner = true },
                            label = { Text("Scan") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                        )
                    }
                    if (scanNotFound) {
                        Text(
                            "No product with that barcode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (canChangeProduct && usingNewProduct) {
                    OutlinedTextField(
                        value = newProductName,
                        onValueChange = { newProductName = it },
                        label = { Text("Product name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newProductBuyPrice,
                            onValueChange = { newProductBuyPrice = it },
                            label = { Text("Buy price") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newProductSellPrice,
                            onValueChange = { newProductSellPrice = it },
                            label = { Text("Sell price") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        enabled = newProductName.isNotBlank() &&
                            (newProductBuyPrice.toDoubleOrNull() ?: 0.0) > 0 &&
                            (newProductSellPrice.toDoubleOrNull() ?: 0.0) > 0,
                        onClick = {
                            val buyCents = Math.round((newProductBuyPrice.toDoubleOrNull() ?: 0.0) * 100)
                            val sellCents = Math.round((newProductSellPrice.toDoubleOrNull() ?: 0.0) * 100)
                            onCreateProduct(newProductName, buyCents, sellCents) { newId ->
                                selectedProductId = newId
                                usingNewProduct = false
                                listPrice = if (isPurchase) newProductBuyPrice else newProductSellPrice
                                discountPercent = "0"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add") }
                } else if (selectedProduct != null) {
                    Text(selectedProduct.name, fontWeight = FontWeight.Bold)
                }

                if (selectedProduct != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = listPrice, onValueChange = { listPrice = it }, label = { Text("List price") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = discountPercent, onValueChange = { discountPercent = it }, label = { Text("Discount %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val productId = selectedProductId
                val quantity = qty.toIntOrNull()
                val price = listPrice.toDoubleOrNull()
                val discount = discountPercent.toIntOrNull()?.coerceIn(0, 100)
                if (productId == null || quantity == null || quantity <= 0 || price == null || price <= 0 || discount == null) {
                    error = "Choose a product, and fill in a quantity and price greater than 0."
                } else {
                    onSave(productId, quantity, Math.round(price * 100), discount)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { value ->
                val product = products.find { it.barcode == normalizeBarcode(value) }
                if (product != null) {
                    selectProductById(product.id)
                    scanNotFound = false
                } else {
                    scanNotFound = true
                }
                showScanner = false
            }
        )
    }
}

@Composable
private fun EditOrderDialog(
    initialTotal: Long,
    initialDescription: String,
    onDismiss: () -> Unit,
    onSave: (Long, String?) -> Unit
) {
    var totalText by remember { mutableStateOf(String.format("%.2f", initialTotal / 100.0)) }
    var description by remember { mutableStateOf(initialDescription) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit order") },
        text = {
            Column {
                OutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("Total amount") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = totalText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "Enter an amount greater than 0."
                } else {
                    onSave(Math.round(amount * 100), description.trim().ifBlank { null })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
