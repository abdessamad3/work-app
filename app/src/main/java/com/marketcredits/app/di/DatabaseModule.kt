package com.marketcredits.app.di

import android.content.Context
import androidx.room.Room
import com.marketcredits.app.data.local.AppDatabase
import com.marketcredits.app.data.local.AppDatabaseCallback
import com.marketcredits.app.data.local.dao.ItemDao
import com.marketcredits.app.data.local.dao.TransactionDao
import com.marketcredits.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>,
        @ApplicationScope applicationScope: CoroutineScope
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .addCallback(AppDatabaseCallback(databaseProvider, applicationScope))
        .build()

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()
}
