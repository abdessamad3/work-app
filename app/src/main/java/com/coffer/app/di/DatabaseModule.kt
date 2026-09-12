package com.coffer.app.di

import android.content.Context
import androidx.room.Room
import com.coffer.app.data.local.AppDatabase
import com.coffer.app.data.local.dao.ContactDao
import com.coffer.app.data.local.dao.LineItemDao
import com.coffer.app.data.local.dao.OrderDao
import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.dao.ProductDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideOrderDao(database: AppDatabase): OrderDao = database.orderDao()

    @Provides
    fun provideLineItemDao(database: AppDatabase): LineItemDao = database.lineItemDao()

    @Provides
    fun providePaymentDao(database: AppDatabase): PaymentDao = database.paymentDao()

    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao = database.productDao()
}
