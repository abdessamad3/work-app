package com.coffer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coffer.app.data.local.entity.LineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LineItemDao {

    @Insert
    suspend fun insertAll(items: List<LineItemEntity>)

    @Update
    suspend fun update(item: LineItemEntity)

    @Delete
    suspend fun delete(item: LineItemEntity)

    @Query("DELETE FROM line_items WHERE orderId = :orderId")
    suspend fun deleteAllForOrder(orderId: Int)

    @Query("DELETE FROM line_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM line_items WHERE orderId = :orderId")
    fun getItemsForOrder(orderId: Int): Flow<List<LineItemEntity>>

    @Query("SELECT * FROM line_items WHERE orderId = :orderId")
    suspend fun getItemsForOrderOnce(orderId: Int): List<LineItemEntity>

    @Query("SELECT * FROM line_items")
    fun getAllLineItems(): Flow<List<LineItemEntity>>
}
