package com.coffer.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.domain.totalRemainingForContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ContactRow(
    val contact: ContactEntity,
    val orderCount: Int,
    val totalRemainingCents: Long
)

data class ContactsUiState(
    val filterType: String = ContactType.SUPPLIER.name,
    val rows: List<ContactRow> = emptyList()
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    orderRepository: OrderRepository,
    paymentRepository: PaymentRepository
) : ViewModel() {

    private val filterType = MutableStateFlow(ContactType.SUPPLIER.name)

    val uiState: StateFlow<ContactsUiState> = combine(
        filterType,
        contactRepository.getAllContacts(),
        orderRepository.getAllOrders(),
        paymentRepository.getAllPayments()
    ) { type, contacts, orders, payments ->
        val rows = contacts
            .filter { it.type == type }
            .map { contact ->
                ContactRow(
                    contact = contact,
                    orderCount = orders.count { it.contactId == contact.id },
                    totalRemainingCents = totalRemainingForContact(contact.id, orders, payments)
                )
            }
        ContactsUiState(filterType = type, rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsUiState())

    fun setFilter(type: String) {
        filterType.value = type
    }
}
