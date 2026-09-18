package com.coffer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** [description] holds the free-text note when the order is a flat total, and is null when it's itemized. */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactId: Int,
    val totalAmountCents: Long,
    val itemized: Boolean,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null
)
