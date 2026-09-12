package com.coffer.app.ui.contacts

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.components.CofferBottomBar
import com.coffer.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigate: (String) -> Unit,
    onOpenContact: (Int) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val options = listOf(ContactType.SUPPLIER.name to "Suppliers", ContactType.CLIENT.name to "Clients")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        bottomBar = { CofferBottomBar(currentRoute = Routes.CONTACTS, onNavigate = onNavigate) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.newOrder()) }) {
                Icon(Icons.Default.Add, contentDescription = "New order")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                options.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = uiState.filterType == type,
                        onClick = { viewModel.setFilter(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) { Text(label) }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.rows.isEmpty()) {
                    item {
                        val kind = if (uiState.filterType == ContactType.SUPPLIER.name) "suppliers" else "clients"
                        Text("No $kind yet — add one from + New order.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(uiState.rows, key = { it.contact.id }) { row ->
                        ContactRowCard(row = row, isSupplier = uiState.filterType == ContactType.SUPPLIER.name, onClick = { onOpenContact(row.contact.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRowCard(row: ContactRow, isSupplier: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(row.contact.name, fontWeight = FontWeight.Bold)
                Text("${row.orderCount} order" + if (row.orderCount == 1) "" else "s", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (row.totalRemainingCents > 0) {
                    Text(formatCents(row.totalRemainingCents), fontWeight = FontWeight.Bold)
                    Text(if (isSupplier) "you owe" else "owes you", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Settled", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
