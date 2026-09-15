package com.coffer.app.ui.products

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coffer.app.data.local.entity.ProductEntity
import com.coffer.app.data.photo.ProductPhotoStore
import com.coffer.app.data.repository.ContactRepository
import com.coffer.app.data.repository.OrderRepository
import com.coffer.app.data.repository.ProductRepository
import com.coffer.app.domain.computeImageHash
import com.coffer.app.domain.computeStock
import com.coffer.app.domain.hammingDistance
import com.coffer.app.domain.normalizeBarcode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProductRow(
    val product: ProductEntity,
    val stockQuantity: Int,
    val canDelete: Boolean,
    val photoDistance: Int? = null
)

data class ProductsUiState(
    val rows: List<ProductRow> = emptyList(),
    val photoFilterActive: Boolean = false,
    val photoFilterOmittedCount: Int = 0
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    orderRepository: OrderRepository,
    contactRepository: ContactRepository
) : ViewModel() {

    private val photoQueryHash = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ProductsUiState> = combine(
        productRepository.getAllProducts(),
        orderRepository.getAllLineItems(),
        orderRepository.getAllOrders(),
        contactRepository.getAllContacts(),
        photoQueryHash
    ) { products, lineItems, orders, contacts, queryHash ->
        val baseRows = products.map { product ->
            ProductRow(
                product = product,
                stockQuantity = computeStock(product.id, lineItems, orders, contacts),
                canDelete = lineItems.none { it.productId == product.id }
            )
        }

        if (queryHash == null) {
            ProductsUiState(rows = baseRows)
        } else {
            val (withPhoto, withoutPhoto) = baseRows.partition { it.product.photoPath != null }
            val ranked = withPhoto
                .mapNotNull { row ->
                    val bitmap = ProductPhotoStore.loadBitmap(row.product.photoPath!!) ?: return@mapNotNull null
                    row.copy(photoDistance = hammingDistance(queryHash, computeImageHash(bitmap)))
                }
                .sortedBy { it.photoDistance }
            ProductsUiState(rows = ranked, photoFilterActive = true, photoFilterOmittedCount = withoutPhoto.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductsUiState())

    fun searchByPhoto(context: Context, uri: Uri, tempFileToDelete: File? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = ProductPhotoStore.loadBitmap(context, uri)
            tempFileToDelete?.delete()
            if (bitmap != null) photoQueryHash.value = computeImageHash(bitmap)
        }
    }

    fun clearPhotoFilter() {
        photoQueryHash.value = null
    }

    fun createProduct(name: String, buyPriceCents: Long, sellPriceCents: Long, barcode: String?, photoPath: String?) {
        if (name.isBlank() || buyPriceCents <= 0 || sellPriceCents <= 0) return
        viewModelScope.launch { productRepository.createProduct(name.trim(), buyPriceCents, sellPriceCents, normalizeBarcode(barcode), photoPath) }
    }

    fun updateProduct(product: ProductEntity, name: String, buyPriceCents: Long, sellPriceCents: Long, barcode: String?, photoPath: String?) {
        if (name.isBlank() || buyPriceCents <= 0 || sellPriceCents <= 0) return
        viewModelScope.launch { productRepository.updateProduct(product, name.trim(), buyPriceCents, sellPriceCents, normalizeBarcode(barcode), photoPath) }
    }

    /** No-op if the product is still used by a line item — callers should gate this on [ProductRow.canDelete]. */
    fun deleteProduct(row: ProductRow) {
        if (!row.canDelete) return
        viewModelScope.launch { productRepository.deleteProduct(row.product) }
    }
}
