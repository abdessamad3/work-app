package com.coffer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.coffer.app.data.local.entity.LineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LineItemDao {

    @Insert
    suspend fun insertAll(items: List<LineItemEntity>)

    @Query("SELECT * FROM line_items WHERE orderId = :orderId")
    fun getItemsForOrder(orderId: Int): Flow<List<LineItemEntity>>
}
