package com.coffer.app.ui.neworder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.ProductEntity
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.NewLineItem
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.ProductRepository
import com.coffer.app.domain.SuggestedPrice
import com.coffer.app.domain.defaultPriceFor
import com.coffer.app.domain.effectiveUnitPriceCents
import com.coffer.app.domain.suggestPriceFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemEntry(
    val productId: Int,
    val quantity: String,
    val listPrice: String,
    val discountPercent: String
)

@HiltViewModel
class NewOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    val presetContactId: Int? = savedStateHandle.get<Int>("contactId")?.takeIf { it > 0 }

    val presetContact: StateFlow<ContactEntity?> = presetContactId
        ?.let { contactRepository.getContactById(it) }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        ?: MutableStateFlow(null)

    val suppliers: StateFlow<List<ContactEntity>> = contactRepository.getContactsByType(ContactType.SUPPLIER.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val clients: StateFlow<List<ContactEntity>> = contactRepository.getContactsByType(ContactType.CLIENT.name)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allLineItems: StateFlow<List<LineItemEntity>> = orderRepository.getAllLineItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allOrders: StateFlow<List<OrderEntity>> = orderRepository.getAllOrders()
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

    fun createProduct(name: String, buyPriceCents: Long, sellPriceCents: Long, onCreated: (Int) -> Unit) {
        if (name.isBlank() || buyPriceCents <= 0 || sellPriceCents <= 0) return
        viewModelScope.launch {
            val id = productRepository.createProduct(name.trim(), buyPriceCents, sellPriceCents)
            onCreated(id)
        }
    }

    /** What you last charged (or paid) this contact for this product, if anything; else the product's buy/sell price. */
    fun suggestedPriceFor(productId: Int, contactId: Int?, isPurchase: Boolean): SuggestedPrice {
        val fallback = SuggestedPrice(defaultPriceFor(productId, products.value, isPurchase), 0)
        if (contactId == null) return fallback
        return suggestPriceFor(productId, contactId, allLineItems.value, allOrders.value) ?: fallback
    }

    fun submit(
        isPurchase: Boolean,
        existingContactId: Int?,
        newContactName: String,
        itemized: Boolean,
        totalAmountText: String,
        description: String,
        items: List<ItemEntry>,
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
                cleanedItems = items.mapNotNull { entry ->
                    val qty = entry.quantity.toIntOrNull() ?: 0
                    val listPrice = entry.listPrice.toDoubleOrNull() ?: 0.0
                    val discount = entry.discountPercent.toIntOrNull()?.coerceIn(0, 100) ?: 0
                    val product = products.value.find { it.id == entry.productId }
                    if (product != null && qty > 0 && listPrice > 0) {
                        NewLineItem(product.id, product.name, qty, Math.round(listPrice * 100), discount)
                    } else null
                }
                if (cleanedItems.isEmpty()) {
                    onError("Add at least one item with a product, quantity and price.")
                    return@launch
                }
                totalCents = cleanedItems.sumOf {
                    effectiveUnitPriceCents(it.listUnitPriceCents, it.discountPercent) * it.quantity
                }
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
