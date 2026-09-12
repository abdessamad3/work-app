package com.coffer.app.data.repository

import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val paymentDao: PaymentDao
) {
    fun getAllPayments(): Flow<List<PaymentEntity>> = paymentDao.getAllPayments()

    fun getPaymentsForOrder(orderId: Int): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForOrder(orderId)

    suspend fun addPayment(orderId: Int, amountCents: Long, note: String?) {
        paymentDao.insert(PaymentEntity(orderId = orderId, amountCents = amountCents, note = note))
    }
}
