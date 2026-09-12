package com.coffer.app.ui.contactdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.domain.OrderComputed
import com.coffer.app.domain.computeOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ContactDetailUiState(
    val contact: ContactEntity? = null,
    val orders: List<OrderComputed> = emptyList(),
    val totalRemainingCents: Long = 0
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    contactRepository: ContactRepository,
    orderRepository: OrderRepository,
    paymentRepository: PaymentRepository
) : ViewModel() {

    val contactId: Int = checkNotNull(savedStateHandle.get<Int>("contactId"))

    val uiState: StateFlow<ContactDetailUiState> = combine(
        contactRepository.getContactById(contactId),
        orderRepository.getOrdersForContact(contactId),
        paymentRepository.getAllPayments()
    ) { contact, orders, payments ->
        val computed = orders.map { computeOrder(it, payments) }.sortedByDescending { it.order.createdAt }
        ContactDetailUiState(
            contact = contact,
            orders = computed,
            totalRemainingCents = computed.sumOf { it.remainingCents }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactDetailUiState())
}
