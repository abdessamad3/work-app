package com.marketcredits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.marketcredits.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE buyerId = :userId OR sellerId = :userId ORDER BY timestamp DESC")
    fun getTransactionsForUser(userId: Int): Flow<List<TransactionEntity>>
}
