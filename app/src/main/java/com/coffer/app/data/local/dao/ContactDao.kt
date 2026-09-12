package com.coffer.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.coffer.app.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE type = :type ORDER BY name ASC")
    fun getContactsByType(type: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    fun getContactById(contactId: Int): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactByIdOnce(contactId: Int): ContactEntity?
}
