package com.marketcredits.app.data.repository

import androidx.room.withTransaction
import com.marketcredits.app.data.local.AppDatabase
import com.marketcredits.app.data.local.dao.ItemDao
import com.marketcredits.app.data.local.dao.TransactionDao
import com.marketcredits.app.data.local.dao.UserDao
import com.marketcredits.app.data.local.entity.ItemStatus
import com.marketcredits.app.data.local.entity.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

sealed class BuyResult {
    data object Success : BuyResult()
    data object ItemNotFound : BuyResult()
    data object ItemAlreadySold : BuyResult()
    data object CannotBuyOwnItem : BuyResult()
    data object InsufficientCredits : BuyResult()
}

/** Owns the buy flow, since it must atomically touch users, items, and transactions together. */
@Singleton
class TransactionRepository @Inject constructor(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val itemDao: ItemDao,
    private val transactionDao: TransactionDao
) {
    fun getTransactionsForUser(userId: Int) = transactionDao.getTransactionsForUser(userId)

    suspend fun buyItem(itemId: Int, buyerId: Int): BuyResult = database.withTransaction {
        val item = itemDao.getItemByIdOnce(itemId) ?: return@withTransaction BuyResult.ItemNotFound
        if (item.status != ItemStatus.AVAILABLE.name) return@withTransaction BuyResult.ItemAlreadySold
        if (item.sellerId == buyerId) return@withTransaction BuyResult.CannotBuyOwnItem

        val buyer = userDao.getUserByIdOnce(buyerId) ?: return@withTransaction BuyResult.ItemNotFound
        if (buyer.creditBalance < item.price) return@withTransaction BuyResult.InsufficientCredits
        val seller = userDao.getUserByIdOnce(item.sellerId) ?: return@withTransaction BuyResult.ItemNotFound

        userDao.update(buyer.copy(creditBalance = buyer.creditBalance - item.price))
        userDao.update(seller.copy(creditBalance = seller.creditBalance + item.price))
        itemDao.update(item.copy(status = ItemStatus.SOLD.name))
        transactionDao.insert(
            TransactionEntity(
                itemId = item.id,
                itemTitle = item.title,
                buyerId = buyerId,
                sellerId = item.sellerId,
                amount = item.price
            )
        )
        BuyResult.Success
    }
}
