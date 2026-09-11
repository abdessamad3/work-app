package com.marketcredits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.marketcredits.app.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items WHERE status = 'AVAILABLE' AND sellerId != :excludingSellerId ORDER BY createdAt DESC")
    fun getAvailableItemsExcludingSeller(excludingSellerId: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE sellerId = :sellerId ORDER BY createdAt DESC")
    fun getItemsBySeller(sellerId: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    fun getItemById(itemId: Int): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemByIdOnce(itemId: Int): ItemEntity?
}
