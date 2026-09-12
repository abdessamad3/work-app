package com.coffer.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ContactType {
    SUPPLIER,
    CLIENT
}

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String
)
