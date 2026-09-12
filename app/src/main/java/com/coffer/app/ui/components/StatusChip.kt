package com.coffer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coffer.app.domain.OrderStatus

@Composable
fun StatusChip(status: OrderStatus) {
    val (bg, fg, label) = when (status) {
        OrderStatus.PAID -> Triple(Color(0xFFDCEAE3), Color(0xFF1F6F5C), "Paid")
        OrderStatus.PARTIAL -> Triple(Color(0xFFF5E6C8), Color(0xFFB8791A), "Partial")
        OrderStatus.UNPAID -> Triple(Color(0xFFF4DFD8), Color(0xFFB0462E), "Unpaid")
    }
    Text(
        text = label,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
