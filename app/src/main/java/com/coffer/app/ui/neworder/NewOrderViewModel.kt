package com.coffer.app.ui.neworder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.NewLineItem
import com.coffer.app.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    val presetContactId: Int? = savedStateHandle.get<Int>("contactId")?.takeIf { it > 0 }

    val presetContact: StateFlow<ContactEntity?> = presetContactId
        ?.let { contactRepository.getContactById(it) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)

    val suppliers: StateFlow<List<ContactEntity>> = contactRepository.getContactsByType(ContactType.SUPPLIER.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val clients: StateFlow<List<ContactEntity>> = contactRepository.getContactsByType(ContactType.CLIENT.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _created = MutableSharedFlow<Int>()
    val created: SharedFlow<Int> = _created.asSharedFlow()

    fun createContact(name: String, isPurchase: Boolean, onCreated: (Int) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = contactRepository.createContact(
                name.trim(),
                if (isPurchase) ContactType.SUPPLIER.name else ContactType.CLIENT.name
            )
            onCreated(id)
        }
    }

    fun submit(
        isPurchase: Boolean,
        existingContactId: Int?,
        newContactName: String,
        itemized: Boolean,
        totalAmountText: String,
        description: String,
        items: List<Triple<String, String, String>>,
        paymentNowText: String,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            var contactId = existingContactId
            if (contactId == null) {
                if (newContactName.isBlank()) {
                    onError("Choose or add a contact.")
                    return@launch
                }
                contactId = contactRepository.createContact(
                    newContactName.trim(),
                    if (isPurchase) ContactType.SUPPLIER.name else ContactType.CLIENT.name
                )
            }

            val totalCents: Long
            val cleanedItems: List<NewLineItem>
            val desc: String?

            if (itemized) {
                cleanedItems = items.mapNotNull { (name, qtyText, priceText) ->
                    val qty = qtyText.toIntOrNull() ?: 0
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && qty > 0 && price > 0) {
                        NewLineItem(name.trim(), qty, Math.round(price * 100))
                    } else null
                }
                if (cleanedItems.isEmpty()) {
                    onError("Add at least one item with a name, quantity and price.")
                    return@launch
                }
                totalCents = cleanedItems.sumOf { it.quantity * it.unitPriceCents }
                desc = null
            } else {
                val amount = totalAmountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    onError("Enter an amount greater than 0.")
                    return@launch
                }
                totalCents = Math.round(amount * 100)
                cleanedItems = emptyList()
                desc = description.trim().ifBlank { "Order" }
            }

            val paymentNowCents = paymentNowText.toDoubleOrNull()?.takeIf { it > 0 }?.let { Math.round(it * 100) }

            orderRepository.createOrder(contactId, totalCents, itemized, desc, cleanedItems, paymentNowCents)
            _created.emit(contactId)
        }
    }
}
