package com.coffer.app.data.repository

import androidx.room.withTransaction
import com.coffer.app.data.local.AppDatabase
import com.coffer.app.data.local.dao.ContactDao
import com.coffer.app.data.local.dao.LineItemDao
import com.coffer.app.data.local.dao.OrderDao
import com.coffer.app.data.local.dao.PaymentDao
import com.coffer.app.data.local.dao.ProductDao
import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.PaymentEntity
import com.coffer.app.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKUP_VERSION = 1

@Singleton
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val contactDao: ContactDao,
    private val orderDao: OrderDao,
    private val lineItemDao: LineItemDao,
    private val paymentDao: PaymentDao,
    private val productDao: ProductDao
) {
    suspend fun exportToJson(): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("contacts", JSONArray().apply {
            contactDao.getAllContacts().first().forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("type", c.type))
            }
        })

        root.put("products", JSONArray().apply {
            productDao.getAllProducts().first().forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("name", p.name)
                        .put("buyPriceCents", p.buyPriceCents)
                        .put("sellPriceCents", p.sellPriceCents)
                        .put("barcode", p.barcode ?: JSONObject.NULL)
                        .put("photoPath", p.photoPath ?: JSONObject.NULL)
                )
            }
        })

        root.put("orders", JSONArray().apply {
            orderDao.getAllOrders().first().forEach { o ->
                put(
                    JSONObject().put("id", o.id).put("contactId", o.contactId)
                        .put("totalAmountCents", o.totalAmountCents)
                        .put("itemized", o.itemized)
                        .put("description", o.description ?: JSONObject.NULL)
                        .put("createdAt", o.createdAt)
                        .put("dueDate", o.dueDate ?: JSONObject.NULL)
                )
            }
        })

        root.put("lineItems", JSONArray().apply {
            lineItemDao.getAllLineItems().first().forEach { li ->
                put(
                    JSONObject().put("id", li.id).put("orderId", li.orderId)
                        .put("productId", li.productId)
                        .put("name", li.name)
                        .put("quantity", li.quantity)
                        .put("listUnitPriceCents", li.listUnitPriceCents)
                        .put("discountPercent", li.discountPercent)
                        .put("createdAt", li.createdAt)
                )
            }
        })

        root.put("payments", JSONArray().apply {
            paymentDao.getAllPayments().first().forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("orderId", p.orderId)
                        .put("amountCents", p.amountCents)
                        .put("note", p.note ?: JSONObject.NULL)
                        .put("paidAt", p.paidAt)
                )
            }
        })

        return root.toString(2)
    }

    /** Wipes every table and replaces it with the contents of [json]. Throws if [json] isn't a valid Coffer backup. */
    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)

        val contacts = root.getJSONArray("contacts").mapObjects { o ->
            ContactEntity(id = o.getInt("id"), name = o.getString("name"), type = o.getString("type"))
        }
        val products = root.getJSONArray("products").mapObjects { o ->
            ProductEntity(
                id = o.getInt("id"),
                name = o.getString("name"),
                buyPriceCents = o.getLong("buyPriceCents"),
                sellPriceCents = o.getLong("sellPriceCents"),
                barcode = if (o.isNull("barcode")) null else o.getString("barcode"),
                photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath")
            )
        }
        val orders = root.getJSONArray("orders").mapObjects { o ->
            OrderEntity(
                id = o.getInt("id"),
                contactId = o.getInt("contactId"),
                totalAmountCents = o.getLong("totalAmountCents"),
                itemized = o.getBoolean("itemized"),
                description = if (o.isNull("description")) null else o.getString("description"),
                createdAt = o.getLong("createdAt"),
                dueDate = if (o.isNull("dueDate")) null else o.getLong("dueDate")
            )
        }
        val lineItems = root.getJSONArray("lineItems").mapObjects { o ->
            LineItemEntity(
                id = o.getInt("id"),
                orderId = o.getInt("orderId"),
                productId = o.getInt("productId"),
                name = o.getString("name"),
                quantity = o.getInt("quantity"),
                listUnitPriceCents = o.getLong("listUnitPriceCents"),
                discountPercent = o.getInt("discountPercent"),
                createdAt = o.getLong("createdAt")
            )
        }
        val payments = root.getJSONArray("payments").mapObjects { o ->
            PaymentEntity(
                id = o.getInt("id"),
                orderId = o.getInt("orderId"),
                amountCents = o.getLong("amountCents"),
                note = if (o.isNull("note")) null else o.getString("note"),
                paidAt = o.getLong("paidAt")
            )
        }

        database.withTransaction {
            paymentDao.deleteAll()
            lineItemDao.deleteAll()
            orderDao.deleteAll()
            productDao.deleteAll()
            contactDao.deleteAll()

            contactDao.insertAll(contacts)
            productDao.insertAll(products)
            orderDao.insertAll(orders)
            lineItemDao.insertAll(lineItems)
            paymentDao.insertAll(payments)
        }
    }
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
