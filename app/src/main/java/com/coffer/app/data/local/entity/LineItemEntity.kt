package com.coffer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "line_items")
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderId: Int,
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long
)
