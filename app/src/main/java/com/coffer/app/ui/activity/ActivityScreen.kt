package com.coffer.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coffer.app.ui.components.ActivityEntryRow
import com.coffer.app.ui.components.CofferBottomBar
import com.coffer.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onNavigate: (String) -> Unit,
    onOpenOrder: (Int) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val options = listOf(ActivityFilter.ALL to "All", ActivityFilter.PAYMENTS to "Payments", ActivityFilter.ORDERS to "Orders")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Activity") }) },
        bottomBar = { CofferBottomBar(currentRoute = Routes.ACTIVITY, onNavigate = onNavigate) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = uiState.filter == value,
                        onClick = { viewModel.setFilter(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) { Text(label) }
                }
            }
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search by contact or description") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.entries.isEmpty()) {
                    item {
                        Text(
                            if (uiState.searchQuery.isBlank()) "Nothing here yet." else "No activity matches \"${uiState.searchQuery}\".",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(uiState.entries, key = { it.timestamp.toString() + it.orderId }) { entry ->
                        ActivityEntryRow(entry, onClick = { onOpenOrder(entry.orderId) })
                    }
                }
            }
        }
    }
}
