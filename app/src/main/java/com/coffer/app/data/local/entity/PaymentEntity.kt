package com.coffer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val orderId: Int,
    val amountCents: Long,
    val note: String?,
    val paidAt: Long = System.currentTimeMillis()
)
