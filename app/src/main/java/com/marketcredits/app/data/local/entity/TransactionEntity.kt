package com.marketcredits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val itemId: Int,
    val itemTitle: String,
    val buyerId: Int,
    val sellerId: Int,
    val amount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
