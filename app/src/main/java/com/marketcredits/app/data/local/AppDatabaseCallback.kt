package com.marketcredits.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider

/** Seeds a few demo profiles and listings the first time the database is created. */
class AppDatabaseCallback(
    private val databaseProvider: Provider<AppDatabase>,
    private val applicationScope: CoroutineScope
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            seedDatabase(databaseProvider.get())
        }
    }

    private suspend fun seedDatabase(database: AppDatabase) {
        val userDao = database.userDao()
        val itemDao = database.itemDao()

        val aliceId = userDao.insert(UserEntity(name = "Alice", colorHex = "#EF5350")).toInt()
        val bobId = userDao.insert(UserEntity(name = "Bob", colorHex = "#42A5F5")).toInt()
        val carolId = userDao.insert(UserEntity(name = "Carol", colorHex = "#66BB6A")).toInt()
        val daveId = userDao.insert(UserEntity(name = "Dave", colorHex = "#FFA726")).toInt()

        itemDao.insert(
            ItemEntity(
                sellerId = bobId,
                title = "Mountain Bike",
                description = "Barely used, 21-speed, great for trails.",
                price = 40
            )
        )
        itemDao.insert(
            ItemEntity(
                sellerId = carolId,
                title = "Acoustic Guitar",
                description = "Great condition, comes with a soft case.",
                price = 60
            )
        )
        itemDao.insert(
            ItemEntity(
                sellerId = daveId,
                title = "LED Desk Lamp",
                description = "Adjustable brightness and color temperature.",
                price = 15
            )
        )
        itemDao.insert(
            ItemEntity(
                sellerId = aliceId,
                title = "Board Game Bundle",
                description = "Three strategy games, all pieces included.",
                price = 25
            )
        )
    }
}
