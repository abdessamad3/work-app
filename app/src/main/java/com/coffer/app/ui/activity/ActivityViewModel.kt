package com.coffer.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.ui.dashboard.ActivityEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ActivityFilter { ALL, PAYMENTS, ORDERS }

data class ActivityUiState(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val searchQuery: String = "",
    val entries: List<ActivityEntry> = emptyList()
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    orderRepository: OrderRepository,
    paymentRepository: PaymentRepository,
    contactRepository: ContactRepository
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ALL)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ActivityUiState> = combine(
        filter,
        searchQuery,
        orderRepository.getAllOrders(),
        paymentRepository.getAllPayments(),
        contactRepository.getAllContacts()
    ) { currentFilter, query, orders, payments, contacts ->
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

        val all = (orderEvents + paymentEvents).sortedByDescending { it.timestamp }
        val byType = when (currentFilter) {
            ActivityFilter.ALL -> all
            ActivityFilter.PAYMENTS -> all.filterIsInstance<ActivityEntry.PaymentMade>()
            ActivityFilter.ORDERS -> all.filterIsInstance<ActivityEntry.OrderCreated>()
        }
        val filtered = if (query.isBlank()) {
            byType
        } else {
            byType.filter { entry ->
                val (contactName, label) = when (entry) {
                    is ActivityEntry.OrderCreated -> entry.contactName to entry.label
                    is ActivityEntry.PaymentMade -> entry.contactName to entry.label
                }
                contactName.contains(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
            }
        }
        ActivityUiState(filter = currentFilter, searchQuery = query, entries = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityUiState())

    fun setFilter(newFilter: ActivityFilter) {
        filter.value = newFilter
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
}
