package com.coffer.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coffer.app.domain.formatCents
import com.coffer.app.ui.dashboard.ActivityEntry

@Composable
fun ActivityEntryRow(entry: ActivityEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (entry) {
                is ActivityEntry.OrderCreated -> {
                    Text("New order — ${entry.contactName}", fontWeight = FontWeight.Bold)
                    Text("${entry.label} · ${formatCents(entry.totalCents)} total", style = MaterialTheme.typography.bodySmall)
                }
                is ActivityEntry.PaymentMade -> {
                    val sign = if (entry.isIncoming) "+" else "−"
                    Text("Payment — ${entry.contactName}", fontWeight = FontWeight.Bold)
                    Text("${entry.label} · $sign${formatCents(entry.amountCents)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
