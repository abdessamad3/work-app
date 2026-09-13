package com.coffer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coffer.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert
    suspend fun insert(payment: PaymentEntity)

    @Insert
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE orderId = :orderId")
    suspend fun deleteAllForOrder(orderId: Int)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY paidAt ASC")
    fun getPaymentsForOrder(orderId: Int): Flow<List<PaymentEntity>>
}
