package com.coffer.app.data.repository

import androidx.room.withTransaction
import com.coffer.app.data.local.AppDatabase
import com.coffer.app.data.local.dao.LineItemDao
import com.coffer.app.data.local.dao.OrderDao
import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class NewLineItem(
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long
)

@Singleton
class OrderRepository @Inject constructor(
    private val database: AppDatabase,
    private val orderDao: OrderDao,
    private val lineItemDao: LineItemDao,
    private val paymentDao: PaymentDao
) {
    fun getOrdersForContact(contactId: Int): Flow<List<OrderEntity>> = orderDao.getOrdersForContact(contactId)

    fun getOrderById(orderId: Int): Flow<OrderEntity?> = orderDao.getOrderById(orderId)

    fun getAllOrders(): Flow<List<OrderEntity>> = orderDao.getAllOrders()

    fun getItemsForOrder(orderId: Int): Flow<List<LineItemEntity>> = lineItemDao.getItemsForOrder(orderId)

    /** Creates the order (with its line items, if itemized) and an optional initial payment as one atomic write. */
    suspend fun createOrder(
        contactId: Int,
        totalAmountCents: Long,
        itemized: Boolean,
        description: String?,
        items: List<NewLineItem>,
        initialPaymentCents: Long?
    ): Int = database.withTransaction {
        val orderId = orderDao.insert(
            OrderEntity(contactId = contactId, totalAmountCents = totalAmountCents, itemized = itemized, description = description)
        ).toInt()

        if (itemized && items.isNotEmpty()) {
            lineItemDao.insertAll(
                items.map { LineItemEntity(orderId = orderId, name = it.name, quantity = it.quantity, unitPriceCents = it.unitPriceCents) }
            )
        }

        if (initialPaymentCents != null && initialPaymentCents > 0) {
            paymentDao.insert(PaymentEntity(orderId = orderId, amountCents = initialPaymentCents, note = null))
        }

        orderId
    }

    /** Only meaningful for a flat-total order; an itemized order's total always follows its line items. */
    suspend fun updateFlatOrder(orderId: Int, totalAmountCents: Long, description: String?) {
        val order = orderDao.getOrderByIdOnce(orderId) ?: return
        orderDao.update(order.copy(totalAmountCents = totalAmountCents, description = description))
    }

    suspend fun addLineItem(orderId: Int, name: String, quantity: Int, unitPriceCents: Long) = database.withTransaction {
        lineItemDao.insertAll(listOf(LineItemEntity(orderId = orderId, name = name, quantity = quantity, unitPriceCents = unitPriceCents)))
        recalculateItemizedTotal(orderId)
    }

    suspend fun updateLineItem(item: LineItemEntity, name: String, quantity: Int, unitPriceCents: Long) = database.withTransaction {
        lineItemDao.update(item.copy(name = name, quantity = quantity, unitPriceCents = unitPriceCents))
        recalculateItemizedTotal(item.orderId)
    }

    suspend fun deleteLineItem(item: LineItemEntity) = database.withTransaction {
        lineItemDao.delete(item)
        recalculateItemizedTotal(item.orderId)
    }

    private suspend fun recalculateItemizedTotal(orderId: Int) {
        val items = lineItemDao.getItemsForOrderOnce(orderId)
        val order = orderDao.getOrderByIdOnce(orderId) ?: return
        orderDao.update(order.copy(totalAmountCents = items.sumOf { it.quantity * it.unitPriceCents }))
    }

    /** Deletes the order along with its line items and payments. */
    suspend fun deleteOrder(orderId: Int) = database.withTransaction {
        lineItemDao.deleteAllForOrder(orderId)
        paymentDao.deleteAllForOrder(orderId)
        orderDao.getOrderByIdOnce(orderId)?.let { orderDao.delete(it) }
    }
}
