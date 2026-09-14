package com.coffer.app.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ProductEntity
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.ProductRepository
import com.coffer.app.domain.computeStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductRow(
    val product: ProductEntity,
    val stockQuantity: Int,
    val canDelete: Boolean
)

data class ProductsUiState(val rows: List<ProductRow> = emptyList())

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    orderRepository: OrderRepository,
    contactRepository: ContactRepository
) : ViewModel() {

    val uiState: StateFlow<ProductsUiState> = combine(
        productRepository.getAllProducts(),
        orderRepository.getAllLineItems(),
        orderRepository.getAllOrders(),
        contactRepository.getAllContacts()
    ) { products, lineItems, orders, contacts ->
        val rows = products.map { product ->
            ProductRow(
                product = product,
                stockQuantity = computeStock(product.id, lineItems, orders, contacts),
                canDelete = lineItems.none { it.productId == product.id }
            )
        }
        ProductsUiState(rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductsUiState())

    fun createProduct(name: String, buyPriceCents: Long, sellPriceCents: Long, barcode: String?) {
        if (name.isBlank() || buyPriceCents <= 0 || sellPriceCents <= 0) return
        viewModelScope.launch { productRepository.createProduct(name.trim(), buyPriceCents, sellPriceCents, barcode?.trim()?.ifBlank { null }) }
    }

    fun updateProduct(product: ProductEntity, name: String, buyPriceCents: Long, sellPriceCents: Long, barcode: String?) {
        if (name.isBlank() || buyPriceCents <= 0 || sellPriceCents <= 0) return
        viewModelScope.launch { productRepository.updateProduct(product, name.trim(), buyPriceCents, sellPriceCents, barcode?.trim()?.ifBlank { null }) }
    }

    /** No-op if the product is still used by a line item — callers should gate this on [ProductRow.canDelete]. */
    fun deleteProduct(row: ProductRow) {
        if (!row.canDelete) return
        viewModelScope.launch { productRepository.deleteProduct(row.product) }
    }
}
