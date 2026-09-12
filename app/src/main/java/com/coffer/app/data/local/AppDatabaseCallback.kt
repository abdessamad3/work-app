package com.coffer.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider

/** Seeds a few demo suppliers, clients, orders and payments the first time the database is created. */
class AppDatabaseCallback(
    private val databaseProvider: Provider<AppDatabase>,
    private val applicationScope: CoroutineScope
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            seedDatabase(databaseProvider.get())
        }
    }

    private suspend fun seedDatabase(database: AppDatabase) {
        val contactDao = database.contactDao()
        val orderDao = database.orderDao()
        val lineItemDao = database.lineItemDao()
        val paymentDao = database.paymentDao()

        val northlineId = contactDao.insert(ContactEntity(name = "Northline Distributors", type = ContactType.SUPPLIER.name)).toInt()
        val primeToolsId = contactDao.insert(ContactEntity(name = "PrimeTools Supply", type = ContactType.SUPPLIER.name)).toInt()
        val aminaId = contactDao.insert(ContactEntity(name = "Amina Haddad", type = ContactType.CLIENT.name)).toInt()
        val riveraId = contactDao.insert(ContactEntity(name = "Rivera Construction", type = ContactType.CLIENT.name)).toInt()
        val youssefId = contactDao.insert(ContactEntity(name = "Youssef K.", type = ContactType.CLIENT.name)).toInt()

        // Northline: $2,000 itemized purchase, paid off in three installments.
        val stockOrderId = orderDao.insert(
            OrderEntity(contactId = northlineId, totalAmountCents = 200_000, itemized = true, description = null)
        ).toInt()
        lineItemDao.insertAll(
            listOf(
                LineItemEntity(orderId = stockOrderId, name = "Paint", quantity = 40, unitPriceCents = 2_000),
                LineItemEntity(orderId = stockOrderId, name = "Tools", quantity = 30, unitPriceCents = 4_000)
            )
        )
        paymentDao.insert(PaymentEntity(orderId = stockOrderId, amountCents = 80_000, note = null))
        paymentDao.insert(PaymentEntity(orderId = stockOrderId, amountCents = 60_000, note = null))
        paymentDao.insert(PaymentEntity(orderId = stockOrderId, amountCents = 60_000, note = null))

        // Northline: partially paid restock.
        val restockOrderId = orderDao.insert(
            OrderEntity(contactId = northlineId, totalAmountCents = 95_000, itemized = false, description = "Hardware restock")
        ).toInt()
        paymentDao.insert(PaymentEntity(orderId = restockOrderId, amountCents = 40_000, note = null))

        // PrimeTools: unpaid.
        orderDao.insert(
            OrderEntity(contactId = primeToolsId, totalAmountCents = 120_000, itemized = false, description = "Power tools batch")
        )

        // Amina: itemized sale, partially paid across two payments.
        val aminaOrderId = orderDao.insert(
            OrderEntity(contactId = aminaId, totalAmountCents = 50_000, itemized = true, description = null)
        ).toInt()
        lineItemDao.insertAll(
            listOf(
                LineItemEntity(orderId = aminaOrderId, name = "Paint", quantity = 10, unitPriceCents = 3_000),
                LineItemEntity(orderId = aminaOrderId, name = "Brushes", quantity = 20, unitPriceCents = 1_000)
            )
        )
        paymentDao.insert(PaymentEntity(orderId = aminaOrderId, amountCents = 20_000, note = null))
        paymentDao.insert(PaymentEntity(orderId = aminaOrderId, amountCents = 15_000, note = null))

        // Rivera: paid in full immediately.
        val riveraOrderId = orderDao.insert(
            OrderEntity(contactId = riveraId, totalAmountCents = 89_000, itemized = false, description = "Bulk order — mixed hardware")
        ).toInt()
        paymentDao.insert(PaymentEntity(orderId = riveraOrderId, amountCents = 89_000, note = null))

        // Youssef: one unpaid order, one partially paid.
        orderDao.insert(
            OrderEntity(contactId = youssefId, totalAmountCents = 54_000, itemized = false, description = "Assorted hardware")
        )
        val fastenersOrderId = orderDao.insert(
            OrderEntity(contactId = youssefId, totalAmountCents = 31_000, itemized = false, description = "Fasteners & sealant")
        ).toInt()
        paymentDao.insert(PaymentEntity(orderId = fastenersOrderId, amountCents = 10_000, note = null))
    }
}
