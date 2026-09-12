package com.coffer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [listUnitPriceCents] and [discountPercent] are a snapshot at the time of this order —
 * changing a product's default price later never rewrites past line items.
 */
@Entity(tableName = "line_items")
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderId: Int,
    val productId: Int,
    val name: String,
    val quantity: Int,
    val listUnitPriceCents: Long,
    val discountPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
