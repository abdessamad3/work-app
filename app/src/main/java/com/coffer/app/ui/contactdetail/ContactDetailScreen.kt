package com.coffer.app.ui.contactdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.domain.OrderComputed
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onOpenOrder: (Int) -> Unit,
    onNewOrder: (Int) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val contact = uiState.contact
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteBlockedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; showRenameDialog = true })
                        DropdownMenuItem(
                            text = { Text("Delete contact") },
                            onClick = {
                                showMenu = false
                                if (uiState.canDelete) showDeleteConfirm = true else showDeleteBlockedDialog = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewOrder(viewModel.contactId) }) {
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val label = if (contact?.type == ContactType.SUPPLIER.name) "You owe" else "Owes you"
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (uiState.totalRemainingCents > 0) formatCents(uiState.totalRemainingCents) else "All settled",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item { Text("Orders", style = MaterialTheme.typography.titleMedium) }
            items(uiState.orders, key = { it.order.id }) { computed ->
                OrderRow(computed, onClick = { onOpenOrder(computed.order.id) })
            }
        }
    }

    if (showRenameDialog && contact != null) {
        RenameDialog(
            currentName = contact.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameContact(newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBlockedDialog = false },
            title = { Text("Can't delete yet") },
            text = { Text("This contact still has orders. Delete their orders first, then you can delete the contact.") },
            confirmButton = { TextButton(onClick = { showDeleteBlockedDialog = false }) { Text("Got it") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete contact?") },
            text = { Text("This removes ${contact?.name ?: "this contact"} for good. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteContact(onDeleted = onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename contact") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OrderRow(computed: OrderComputed, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(computed.order.description ?: "Itemized order", fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(computed.status)
                Text(formatCents(computed.order.totalAmountCents), style = MaterialTheme.typography.bodyMedium)
                if (computed.remainingCents > 0) {
                    Text("${formatCents(computed.remainingCents)} left", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
