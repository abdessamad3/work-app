package com.marketcredits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

const val STARTING_CREDIT_BALANCE = 100

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val colorHex: String,
    val creditBalance: Int = STARTING_CREDIT_BALANCE
)
