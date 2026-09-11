package com.marketcredits.app.data.repository

import com.marketcredits.app.data.local.dao.UserDao
import com.marketcredits.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllUsers()

    fun getUserById(userId: Int): Flow<UserEntity?> = userDao.getUserById(userId)

    suspend fun createUser(name: String, colorHex: String): Long =
        userDao.insert(UserEntity(name = name, colorHex = colorHex))
}
