package com.coffer.app.ui.orderdetail

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.domain.OrderStatus
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
                items(uiState.items, key = { it.id }) { lineItem ->
                    Card(onClick = { itemDialogTarget = lineItem }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(lineItem.name)
                                Text("×${lineItem.quantity} @ ${formatCents(lineItem.unitPriceCents)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatCents(lineItem.quantity * lineItem.unitPriceCents))
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
                items(uiState.payments, key = { it.id }) { payment ->
                    Card(onClick = { paymentDialogTarget = payment }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(formatCents(payment.amountCents), fontWeight = FontWeight.Bold)
                            payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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

        if (showAddItemDialog) {
            LineItemDialog(
                title = "Add item",
                initialName = "",
                initialQty = "",
                initialPrice = "",
                onDismiss = { showAddItemDialog = false },
                onSave = { name, qty, priceCents ->
                    viewModel.addLineItem(name, qty, priceCents)
                    showAddItemDialog = false
                },
                onDelete = null
            )
        }

        itemDialogTarget?.let { item ->
            LineItemDialog(
                title = "Edit item",
                initialName = item.name,
                initialQty = item.quantity.toString(),
                initialPrice = String.format("%.2f", item.unitPriceCents / 100.0),
                onDismiss = { itemDialogTarget = null },
                onSave = { name, qty, priceCents ->
                    viewModel.updateLineItem(item, name, qty, priceCents)
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
    initialName: String,
    initialQty: String,
    initialPrice: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialName) }
    var qty by remember { mutableStateOf(initialQty) }
    var price by remember { mutableStateOf(initialPrice) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Unit price") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val quantity = qty.toIntOrNull()
                val unitPrice = price.toDoubleOrNull()
                if (name.isBlank() || quantity == null || quantity <= 0 || unitPrice == null || unitPrice <= 0) {
                    error = "Fill in a product, quantity and price greater than 0."
                } else {
                    onSave(name.trim(), quantity, Math.round(unitPrice * 100))
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
