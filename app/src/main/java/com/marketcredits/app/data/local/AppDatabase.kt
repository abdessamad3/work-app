package com.marketcredits.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marketcredits.app.data.local.dao.ItemDao
import com.marketcredits.app.data.local.dao.TransactionDao
import com.marketcredits.app.data.local.dao.UserDao
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.TransactionEntity
import com.marketcredits.app.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, ItemEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DATABASE_NAME = "market_credits.db"
    }
}
