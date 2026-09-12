package com.coffer.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.domain.cashBalanceCents
import com.coffer.app.domain.totalOwedByType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A fresh install starts at zero cash — every dollar from here on comes from a real logged payment. */
private const val OPENING_BALANCE_CENTS = 0L

sealed interface ActivityEntry {
    val timestamp: Long
    val orderId: Int

    data class OrderCreated(
        override val timestamp: Long,
        override val orderId: Int,
        val contactName: String,
        val label: String,
        val totalCents: Long
    ) : ActivityEntry

    data class PaymentMade(
        override val timestamp: Long,
        override val orderId: Int,
        val contactName: String,
        val label: String,
        val amountCents: Long,
        val isIncoming: Boolean
    ) : ActivityEntry
}

data class DashboardUiState(
    val cashBalanceCents: Long = 0,
    val owedToSuppliersCents: Long = 0,
    val owedByClientsCents: Long = 0,
    val recentActivity: List<ActivityEntry> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    orderRepository: OrderRepository,
    paymentRepository: PaymentRepository,
    contactRepository: ContactRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        orderRepository.getAllOrders(),
        paymentRepository.getAllPayments(),
        contactRepository.getAllContacts()
    ) { orders, payments, contacts ->
        val contactById = contacts.associateBy { it.id }
        val orderById = orders.associateBy { it.id }

        val orderEvents = orders.map { order ->
            val contact = contactById[order.contactId]
            ActivityEntry.OrderCreated(
                timestamp = order.createdAt,
                orderId = order.id,
                contactName = contact?.name ?: "Unknown",
                label = order.description ?: "Order",
                totalCents = order.totalAmountCents
            )
        }
        val paymentEvents = payments.map { payment ->
            val order = orderById[payment.orderId]
            val contact = order?.let { contactById[it.contactId] }
            ActivityEntry.PaymentMade(
                timestamp = payment.paidAt,
                orderId = payment.orderId,
                contactName = contact?.name ?: "Unknown",
                label = order?.description ?: "Order",
                amountCents = payment.amountCents,
                isIncoming = contact?.type == ContactType.CLIENT.name
            )
        }

        DashboardUiState(
            cashBalanceCents = cashBalanceCents(OPENING_BALANCE_CENTS, orders, payments, contacts),
            owedToSuppliersCents = totalOwedByType(ContactType.SUPPLIER.name, orders, payments, contacts),
            owedByClientsCents = totalOwedByType(ContactType.CLIENT.name, orders, payments, contacts),
            recentActivity = (orderEvents + paymentEvents).sortedByDescending { it.timestamp }.take(6)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
