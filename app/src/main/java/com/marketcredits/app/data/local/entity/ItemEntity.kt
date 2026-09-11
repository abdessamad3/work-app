package com.marketcredits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemStatus {
    AVAILABLE,
    SOLD
}

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sellerId: Int,
    val title: String,
    val description: String,
    val price: Int,
    val status: String = ItemStatus.AVAILABLE.name,
    val createdAt: Long = System.currentTimeMillis()
)
