package com.coffer.app.data.repository

import androidx.room.withTransaction
import com.coffer.app.data.local.AppDatabase
import com.coffer.app.data.local.dao.LineItemDao
import com.coffer.app.data.local.dao.OrderDao
import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.domain.effectiveUnitPriceCents
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class NewLineItem(
    val productId: Int,
    val name: String,
    val quantity: Int,
    val listUnitPriceCents: Long,
    val discountPercent: Int
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

    fun getAllLineItems(): Flow<List<LineItemEntity>> = lineItemDao.getAllLineItems()

    /** Creates the order (with its line items, if itemized) and an optional initial payment as one atomic write. */
    suspend fun createOrder(
        contactId: Int,
        totalAmountCents: Long,
        itemized: Boolean,
        description: String?,
        items: List<NewLineItem>,
        initialPaymentCents: Long?,
        dueDate: Long? = null
    ): Int = database.withTransaction {
        val orderId = orderDao.insert(
            OrderEntity(contactId = contactId, totalAmountCents = totalAmountCents, itemized = itemized, description = description, dueDate = dueDate)
        ).toInt()

        if (itemized && items.isNotEmpty()) {
            lineItemDao.insertAll(
                items.map {
                    LineItemEntity(
                        orderId = orderId,
                        productId = it.productId,
                        name = it.name,
                        quantity = it.quantity,
                        listUnitPriceCents = it.listUnitPriceCents,
                        discountPercent = it.discountPercent
                    )
                }
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

    /** Works for both flat and itemized orders since a due date isn't tied to how the total is computed. */
    suspend fun updateDueDate(orderId: Int, dueDate: Long?) {
        val order = orderDao.getOrderByIdOnce(orderId) ?: return
        orderDao.update(order.copy(dueDate = dueDate))
    }

    suspend fun addLineItem(orderId: Int, productId: Int, name: String, quantity: Int, listUnitPriceCents: Long, discountPercent: Int) =
        database.withTransaction {
            lineItemDao.insertAll(
                listOf(
                    LineItemEntity(
                        orderId = orderId,
                        productId = productId,
                        name = name,
                        quantity = quantity,
                        listUnitPriceCents = listUnitPriceCents,
                        discountPercent = discountPercent
                    )
                )
            )
            recalculateItemizedTotal(orderId)
        }

    suspend fun updateLineItem(item: LineItemEntity, productId: Int, name: String, quantity: Int, listUnitPriceCents: Long, discountPercent: Int) =
        database.withTransaction {
            lineItemDao.update(
                item.copy(
                    productId = productId,
                    name = name,
                    quantity = quantity,
                    listUnitPriceCents = listUnitPriceCents,
                    discountPercent = discountPercent
                )
            )
            recalculateItemizedTotal(item.orderId)
        }

    suspend fun deleteLineItem(item: LineItemEntity) = database.withTransaction {
        lineItemDao.delete(item)
        recalculateItemizedTotal(item.orderId)
    }

    private suspend fun recalculateItemizedTotal(orderId: Int) {
        val items = lineItemDao.getItemsForOrderOnce(orderId)
        val order = orderDao.getOrderByIdOnce(orderId) ?: return
        val total = items.sumOf { effectiveUnitPriceCents(it.listUnitPriceCents, it.discountPercent) * it.quantity }
        orderDao.update(order.copy(totalAmountCents = total))
    }

    /** Deletes the order along with its line items and payments. */
    suspend fun deleteOrder(orderId: Int) = database.withTransaction {
        lineItemDao.deleteAllForOrder(orderId)
        paymentDao.deleteAllForOrder(orderId)
        orderDao.getOrderByIdOnce(orderId)?.let { orderDao.delete(it) }
    }
}
