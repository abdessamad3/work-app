package com.coffer.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.coffer.app.data.local.dao.ContactDao
import com.coffer.app.data.local.dao.LineItemDao
import com.coffer.app.data.local.dao.OrderDao
import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity

@Database(
    entities = [ContactEntity::class, OrderEntity::class, LineItemEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun orderDao(): OrderDao
    abstract fun lineItemDao(): LineItemDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        const val DATABASE_NAME = "coffer.db"
    }
}
