package com.coffer.app.ui.orderdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var showPaymentDialog by remember { mutableStateOf(false) }
    val order = uiState.order

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.contact?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
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

            if (order.itemized && uiState.items.isNotEmpty()) {
                item { Text("Items", style = MaterialTheme.typography.titleMedium) }
                items(uiState.items, key = { it.id }) { lineItem ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(lineItem.name)
                            Text("×${lineItem.quantity} @ ${formatCents(lineItem.unitPriceCents)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatCents(lineItem.quantity * lineItem.unitPriceCents))
                    }
                }
            }

            item { Text("Payments", style = MaterialTheme.typography.titleMedium) }
            if (uiState.payments.isEmpty()) {
                item { Text("No payments recorded yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(uiState.payments, key = { it.id }) { payment ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(formatCents(payment.amountCents))
                            payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item {
                if (uiState.status == OrderStatus.PAID) {
                    Text("Fully paid", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    Button(onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add payment")
                    }
                }
            }
        }

        if (showPaymentDialog) {
            AddPaymentDialog(
                suggestedCents = uiState.remainingCents,
                onDismiss = { showPaymentDialog = false },
                onConfirm = { amountCents, note ->
                    viewModel.addPayment(amountCents, note)
                    showPaymentDialog = false
                }
            )
        }
    }
}

@Composable
private fun AddPaymentDialog(suggestedCents: Long, onDismiss: () -> Unit, onConfirm: (Long, String?) -> Unit) {
    var amountText by remember { mutableStateOf(String.format("%.2f", suggestedCents / 100.0)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add payment") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "Enter an amount greater than 0."
                } else {
                    onConfirm(Math.round(amount * 100), note.trim().ifBlank { null })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
