package com.coffer.app.domain

import com.coffer.app.data.local.entity.ContactEntity
import com.coffer.app.data.local.entity.ContactType
import com.coffer.app.data.local.entity.LineItemEntity
import com.coffer.app.data.local.entity.OrderEntity
import com.coffer.app.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricingTest {

    private fun contact(id: Int, type: ContactType) = ContactEntity(id = id, name = "Contact $id", type = type.name)
    private fun order(id: Int, contactId: Int) =
        OrderEntity(id = id, contactId = contactId, totalAmountCents = 0, itemized = true, description = null)
    private fun lineItem(id: Int, orderId: Int, productId: Int, quantity: Int, listPriceCents: Long, discount: Int = 0, createdAt: Long = 0) =
        LineItemEntity(
            id = id,
            orderId = orderId,
            productId = productId,
            name = "Product $productId",
            quantity = quantity,
            listUnitPriceCents = listPriceCents,
            discountPercent = discount,
            createdAt = createdAt
        )

    @Test
    fun `effectiveUnitPriceCents applies the discount`() {
        assertEquals(900L, effectiveUnitPriceCents(listUnitPriceCents = 1000, discountPercent = 10))
    }

    @Test
    fun `effectiveUnitPriceCents rounds to the nearest cent`() {
        // 999 * 0.67 = 669.33 -> rounds down to 669
        assertEquals(669L, effectiveUnitPriceCents(listUnitPriceCents = 999, discountPercent = 33))
    }

    @Test
    fun `lineTotalCents multiplies the discounted price by quantity`() {
        val item = lineItem(id = 1, orderId = 1, productId = 1, quantity = 3, listPriceCents = 1000, discount = 10)
        assertEquals(2700L, item.lineTotalCents())
    }

    @Test
    fun `computeStock adds supplier purchases and subtracts client sales`() {
        val contacts = listOf(contact(1, ContactType.SUPPLIER), contact(2, ContactType.CLIENT))
        val orders = listOf(order(id = 1, contactId = 1), order(id = 2, contactId = 2))
        val lineItems = listOf(
            lineItem(id = 1, orderId = 1, productId = 100, quantity = 10, listPriceCents = 500), // bought 10
            lineItem(id = 2, orderId = 2, productId = 100, quantity = 4, listPriceCents = 800),  // sold 4
            lineItem(id = 3, orderId = 1, productId = 999, quantity = 50, listPriceCents = 100)  // different product — ignored
        )

        assertEquals(6, computeStock(productId = 100, lineItems = lineItems, orders = orders, contacts = contacts))
    }

    @Test
    fun `suggestPriceFor returns the most recent line item for that product and contact`() {
        val orders = listOf(order(id = 1, contactId = 1), order(id = 2, contactId = 1))
        val lineItems = listOf(
            lineItem(id = 1, orderId = 1, productId = 100, quantity = 1, listPriceCents = 500, discount = 0, createdAt = 1000),
            lineItem(id = 2, orderId = 2, productId = 100, quantity = 1, listPriceCents = 600, discount = 5, createdAt = 2000)
        )

        val suggestion = suggestPriceFor(productId = 100, contactId = 1, lineItems = lineItems, orders = orders)

        assertEquals(SuggestedPrice(listUnitPriceCents = 600, discountPercent = 5), suggestion)
    }

    @Test
    fun `suggestPriceFor returns null when this contact has no history for the product`() {
        val orders = listOf(order(id = 1, contactId = 1))
        val lineItems = listOf(lineItem(id = 1, orderId = 1, productId = 100, quantity = 1, listPriceCents = 500))

        assertNull(suggestPriceFor(productId = 100, contactId = 2, lineItems = lineItems, orders = orders))
    }

    @Test
    fun `defaultPriceFor picks buy price for a purchase and sell price otherwise`() {
        val products = listOf(ProductEntity(id = 1, name = "Widget", buyPriceCents = 300, sellPriceCents = 500))

        assertEquals(300L, defaultPriceFor(productId = 1, products = products, isPurchase = true))
        assertEquals(500L, defaultPriceFor(productId = 1, products = products, isPurchase = false))
    }

    @Test
    fun `defaultPriceFor is zero for an unknown product`() {
        assertEquals(0L, defaultPriceFor(productId = 999, products = emptyList(), isPurchase = true))
    }
}
