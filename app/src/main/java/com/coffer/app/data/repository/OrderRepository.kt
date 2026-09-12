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
}
