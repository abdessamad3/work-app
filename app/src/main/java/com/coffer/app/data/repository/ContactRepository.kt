package com.coffer.app.data.repository

import com.coffer.app.data.local.dao.ContactDao
import com.coffer.app.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {
    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    fun getContactsByType(type: String): Flow<List<ContactEntity>> = contactDao.getContactsByType(type)

    fun getContactById(contactId: Int): Flow<ContactEntity?> = contactDao.getContactById(contactId)

    suspend fun createContact(name: String, type: String): Int = contactDao.insert(ContactEntity(name = name, type = type)).toInt()

    suspend fun renameContact(contactId: Int, newName: String) {
        val contact = contactDao.getContactByIdOnce(contactId) ?: return
        contactDao.update(contact.copy(name = newName))
    }

    suspend fun deleteContact(contactId: Int) {
        val contact = contactDao.getContactByIdOnce(contactId) ?: return
        contactDao.delete(contact)
    }
}
