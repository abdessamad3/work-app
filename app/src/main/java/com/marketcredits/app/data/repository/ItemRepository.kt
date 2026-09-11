package com.marketcredits.app.data.repository

import com.marketcredits.app.data.local.dao.ItemDao
import com.marketcredits.app.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {
    fun getAvailableItemsExcludingSeller(sellerId: Int): Flow<List<ItemEntity>> =
        itemDao.getAvailableItemsExcludingSeller(sellerId)

    fun getItemsBySeller(sellerId: Int): Flow<List<ItemEntity>> =
        itemDao.getItemsBySeller(sellerId)

    fun getItemById(itemId: Int): Flow<ItemEntity?> = itemDao.getItemById(itemId)

    suspend fun createItem(sellerId: Int, title: String, description: String, price: Int): Long =
        itemDao.insert(
            ItemEntity(
                sellerId = sellerId,
                title = title,
                description = description,
                price = price
            )
        )
}
