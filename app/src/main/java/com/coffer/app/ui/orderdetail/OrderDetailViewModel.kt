package com.coffer.app.ui.orderdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.data.local.entity.ProductEntity
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.data.repository.ProductRepository
import com.coffer.app.domain.OrderStatus
import com.coffer.app.domain.SuggestedPrice
import com.coffer.app.domain.computeOrder
import com.coffer.app.domain.suggestPriceFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderDetailUiState(
    val order: OrderEntity? = null,
    val contact: ContactEntity? = null,
    val items: List<LineItemEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val paidCents: Long = 0,
    val remainingCents: Long = 0,
    val status: OrderStatus = OrderStatus.UNPAID
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    contactRepository: ContactRepository
) : ViewModel() {

    private val orderId: Int = checkNotNull(savedStateHandle.get<Int>("orderId"))

    val uiState: StateFlow<OrderDetailUiState> = orderRepository.getOrderById(orderId)
        .flatMapLatest { order ->
            if (order == null) {
                flowOf(OrderDetailUiState())
            } else {
                combine(
                    orderRepository.getItemsForOrder(orderId),
                    paymentRepository.getPaymentsForOrder(orderId),
                    contactRepository.getContactById(order.contactId)
                ) { items, payments, contact ->
                    val computed = computeOrder(order, payments)
                    OrderDetailUiState(
                        order = order,
                        contact = contact,
                        items = items,
                        payments = payments.sortedByDescending { it.paidAt },
                        paidCents = computed.paidCents,
                        remainingCents = computed.remainingCents,
                        status = computed.status
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrderDetailUiState())

    val products: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allLineItems: StateFlow<List<LineItemEntity>> = orderRepository.getAllLineItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allOrders: StateFlow<List<OrderEntity>> = orderRepository.getAllOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun suggestedPriceFor(productId: Int): SuggestedPrice {
        val fallback = SuggestedPrice(products.value.find { it.id == productId }?.defaultUnitPriceCents ?: 0, 0)
        val contactId = uiState.value.order?.contactId ?: return fallback
        return suggestPriceFor(productId, contactId, allLineItems.value, allOrders.value) ?: fallback
    }

    fun createProduct(name: String, defaultPriceCents: Long, onCreated: (Int) -> Unit) {
        if (name.isBlank() || defaultPriceCents <= 0) return
        viewModelScope.launch {
            val id = productRepository.createProduct(name.trim(), defaultPriceCents)
            onCreated(id)
        }
    }

    fun addPayment(amountCents: Long, note: String?) {
        viewModelScope.launch {
            paymentRepository.addPayment(orderId, amountCents, note)
        }
    }

    fun updatePayment(payment: PaymentEntity, amountCents: Long, note: String?) {
        viewModelScope.launch {
            paymentRepository.updatePayment(payment, amountCents, note)
        }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch {
            paymentRepository.deletePayment(payment)
        }
    }

    fun updateFlatOrder(totalAmountCents: Long, description: String?) {
        viewModelScope.launch {
            orderRepository.updateFlatOrder(orderId, totalAmountCents, description)
        }
    }

    fun addLineItem(productId: Int, quantity: Int, listUnitPriceCents: Long, discountPercent: Int) {
        val name = products.value.find { it.id == productId }?.name ?: return
        viewModelScope.launch {
            orderRepository.addLineItem(orderId, productId, name, quantity, listUnitPriceCents, discountPercent)
        }
    }

    fun updateLineItem(item: LineItemEntity, productId: Int, quantity: Int, listUnitPriceCents: Long, discountPercent: Int) {
        val name = products.value.find { it.id == productId }?.name ?: item.name
        viewModelScope.launch {
            orderRepository.updateLineItem(item, productId, name, quantity, listUnitPriceCents, discountPercent)
        }
    }

    fun deleteLineItem(item: LineItemEntity) {
        viewModelScope.launch {
            orderRepository.deleteLineItem(item)
        }
    }

    fun deleteOrder(onDeleted: () -> Unit) {
        viewModelScope.launch {
            orderRepository.deleteOrder(orderId)
            onDeleted()
        }
    }
}
