package com.coffer.app.ui.orderdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.PaymentRepository
import com.coffer.app.domain.OrderStatus
import com.coffer.app.domain.computeOrder
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

    fun addPayment(amountCents: Long, note: String?) {
        viewModelScope.launch {
            paymentRepository.addPayment(orderId, amountCents, note)
        }
    }
}
