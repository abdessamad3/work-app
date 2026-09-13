package com.coffer.app.domain

import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.ProductEntity

fun effectiveUnitPriceCents(listUnitPriceCents: Long, discountPercent: Int): Long =
    Math.round(listUnitPriceCents * (100 - discountPercent) / 100.0)

fun LineItemEntity.effectiveUnitPriceCents(): Long = effectiveUnitPriceCents(listUnitPriceCents, discountPercent)

fun LineItemEntity.lineTotalCents(): Long = effectiveUnitPriceCents() * quantity

/** Stock is never stored — it's just units bought from suppliers minus units sold to clients. */
fun computeStock(productId: Int, lineItems: List<LineItemEntity>, orders: List<OrderEntity>, contacts: List<ContactEntity>): Int {
    val orderById = orders.associateBy { it.id }
    val contactById = contacts.associateBy { it.id }
    var stock = 0
    lineItems.forEach { item ->
        if (item.productId != productId) return@forEach
        val order = orderById[item.orderId] ?: return@forEach
        val contact = contactById[order.contactId] ?: return@forEach
        stock += if (contact.type == ContactType.SUPPLIER.name) item.quantity else -item.quantity
    }
    return stock
}

data class SuggestedPrice(val listUnitPriceCents: Long, val discountPercent: Int)

/** What you last actually charged (or paid) this exact contact for this exact product, if anything. */
fun suggestPriceFor(productId: Int, contactId: Int, lineItems: List<LineItemEntity>, orders: List<OrderEntity>): SuggestedPrice? {
    val orderById = orders.associateBy { it.id }
    return lineItems
        .filter { it.productId == productId && orderById[it.orderId]?.contactId == contactId }
        .maxByOrNull { it.createdAt }
        ?.let { SuggestedPrice(it.listUnitPriceCents, it.discountPercent) }
}

/** The product's own buy or sell price, before checking for any per-client history. */
fun defaultPriceFor(productId: Int, products: List<ProductEntity>, isPurchase: Boolean): Long {
    val product = products.find { it.id == productId } ?: return 0
    return if (isPurchase) product.buyPriceCents else product.sellPriceCents
}
